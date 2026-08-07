package com.miniandmore.app.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.config.ApplicationProperties;
import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.LedgerlyWebhookEvent;
import com.miniandmore.app.domain.Product;
import com.miniandmore.app.domain.enumeration.LedgerlySyncStatus;
import com.miniandmore.app.domain.enumeration.PaymentStatus;
import com.miniandmore.app.repository.CustomerOrderRepository;
import com.miniandmore.app.repository.LedgerlyWebhookEventRepository;
import com.miniandmore.app.repository.ProductRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class LedgerlyWebhookHandlerService {

    private static final Logger LOG = LoggerFactory.getLogger(LedgerlyWebhookHandlerService.class);

    private final LedgerlyCredentialsProvider credentialsProvider;
    private final LedgerlyWebhookEventRepository webhookEventRepository;
    private final ProductRepository productRepository;
    private final CustomerOrderRepository customerOrderRepository;
    private final ObjectMapper objectMapper;
    private final LedgerlyProductMergeService productMergeService;
    private final LedgerlyCategoryResolver categoryResolver;
    private final ApplicationProperties applicationProperties;

    public LedgerlyWebhookHandlerService(
        LedgerlyCredentialsProvider credentialsProvider,
        LedgerlyWebhookEventRepository webhookEventRepository,
        ProductRepository productRepository,
        CustomerOrderRepository customerOrderRepository,
        ObjectMapper objectMapper,
        LedgerlyProductMergeService productMergeService,
        LedgerlyCategoryResolver categoryResolver,
        ApplicationProperties applicationProperties
    ) {
        this.credentialsProvider = credentialsProvider;
        this.webhookEventRepository = webhookEventRepository;
        this.productRepository = productRepository;
        this.customerOrderRepository = customerOrderRepository;
        this.objectMapper = objectMapper;
        this.productMergeService = productMergeService;
        this.categoryResolver = categoryResolver;
        this.applicationProperties = applicationProperties;
    }

    public Map<String, Object> handle(String rawBody, String signature, String eventHeader) {
        verifySignature(rawBody, signature);
        Map<String, Object> payload = parsePayload(rawBody);
        String eventId = stringValue(payload.get("id"));
        if (eventId == null || eventId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing event id");
        }
        if (webhookEventRepository.existsByEventId(eventId)) {
            return Map.of("ok", true, "duplicate", true);
        }

        String event = eventHeader != null && !eventHeader.isBlank() ? eventHeader : stringValue(payload.get("event"));
        if (event == null || event.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing event type");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) payload.getOrDefault("data", Map.of());
        switch (event) {
            case "item.updated", "item.created" -> {
                if (!applicationProperties.getLedgerly().isAutoPullEnabled()) {
                    LOG.info("Ignoring Ledgerly {} — auto-pull disabled", event);
                } else {
                    handleItemUpdated(data);
                }
            }
            case "invoice.paid" -> {
                if (!applicationProperties.getLedgerly().isAutoSyncEnabled()) {
                    LOG.info("Ignoring Ledgerly invoice.paid — auto-sync disabled");
                } else {
                    handleInvoicePaid(data);
                }
            }
            case "order.fulfilled", "order.delivered", "salesorder.fulfilled", "shipment.updated", "shipment.delivered" -> {
                if (!applicationProperties.getLedgerly().isAutoSyncEnabled()) {
                    LOG.info("Ignoring Ledgerly {} — auto-sync disabled", event);
                } else {
                    handleOrderTrackingUpdated(data);
                }
            }
            default -> LOG.debug("Ignoring unhandled Ledgerly webhook event {}", event);
        }

        LedgerlyWebhookEvent record = new LedgerlyWebhookEvent();
        record.setEventId(eventId);
        record.setEventType(event);
        record.setReceivedAt(Instant.now());
        webhookEventRepository.save(record);
        return Map.of("ok", true, "event", event);
    }

    private void handleItemUpdated(Map<String, Object> data) {
        String sku = stringValue(data.get("sku"));
        if (sku == null || sku.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "item.updated requires sku");
        }
        Product product = productRepository
            .findOneBySku(sku)
            .or(() -> {
                String ledgerlyItemId = stringValue(data.get("ledgerlyItemId"));
                return ledgerlyItemId != null ? productRepository.findOneByLedgerlyItemId(ledgerlyItemId) : java.util.Optional.empty();
            })
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No product for SKU " + sku));

        productMergeService.mergeLedgerlyItem(product, data, categoryResolver::resolve, null);
        productRepository.save(product);
    }

    private void handleInvoicePaid(Map<String, Object> data) {
        String externalRef = stringValue(data.get("externalRef"));
        if (externalRef == null || externalRef.isBlank()) {
            externalRef = stringValue(data.get("orderNumber"));
        }
        if (externalRef == null || externalRef.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invoice.paid requires externalRef");
        }
        final String orderNumber = externalRef;
        CustomerOrder order = customerOrderRepository
            .findOneByOrderNumber(orderNumber)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No order for " + orderNumber));

        if (order.getPaymentStatus() != PaymentStatus.PAID) {
            order.setPaymentStatus(PaymentStatus.PAID);
        }
        String ledgerlyInvoiceId = stringValue(data.get("ledgerlyInvoiceId"));
        if (ledgerlyInvoiceId != null && !ledgerlyInvoiceId.isBlank()) {
            order.setLedgerlyInvoiceId(ledgerlyInvoiceId);
        }
        if (LedgerlyOrderStatus.shouldReplace(order.getLedgerlyOrderStatus(), "Paid")) {
            order.setLedgerlyOrderStatus("Paid");
        }
        order.setLedgerlySyncStatus(LedgerlySyncStatus.SYNCED);
        order.setLedgerlyLastSyncedAt(Instant.now());
        order.setLedgerlySyncError(null);
        customerOrderRepository.save(order);
    }

    private void handleOrderTrackingUpdated(Map<String, Object> data) {
        String externalRef = stringValue(data.get("externalRef"));
        if (externalRef == null || externalRef.isBlank()) {
            externalRef = stringValue(data.get("orderNumber"));
        }
        if (externalRef == null || externalRef.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "tracking webhook requires externalRef");
        }
        final String orderNumber = externalRef;
        CustomerOrder order = customerOrderRepository
            .findOneByOrderNumber(orderNumber)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No order for " + orderNumber));

        String incoming = LedgerlyOrderStatus.resolveFromPullResponse(data);
        if (incoming != null && LedgerlyOrderStatus.shouldReplace(order.getLedgerlyOrderStatus(), incoming)) {
            order.setLedgerlyOrderStatus(incoming);
        }
        LedgerlyOrderStatus.applyTrackingFields(order, data);
        order.setLedgerlyTrackingLastPulledAt(Instant.now());
        customerOrderRepository.save(order);
    }

    private void verifySignature(String rawBody, String signature) {
        String secret = credentialsProvider.webhookSecret();
        if (secret == null || secret.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Ledgerly webhook secret not configured");
        }
        if (signature == null || signature.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing X-Ledgerly-Signature");
        }
        String expected = sign(rawBody, secret);
        String provided = signature.trim();
        if (provided.regionMatches(true, 0, "sha256=", 0, 7)) {
            provided = provided.substring(7);
        }
        if (!expected.equalsIgnoreCase(provided)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid webhook signature");
        }
    }

    private String sign(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to verify webhook signature", e);
        }
    }

    private Map<String, Object> parsePayload(String rawBody) {
        try {
            return objectMapper.readValue(rawBody, new TypeReference<>() {});
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid JSON payload");
        }
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString();
    }
}
