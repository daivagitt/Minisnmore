package com.miniandmore.app.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.config.ApplicationProperties;
import com.miniandmore.app.domain.ZohoStockWebhookEvent;
import com.miniandmore.app.repository.ZohoStockWebhookEventRepository;
import java.time.Instant;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Idempotent stock-only webhook receiver. Reconciles Zoho {@code stock_on_hand} to the absolute
 * store quantity so automatic events, admin retries, and Zoho document-driven movements converge
 * instead of stacking duplicate deductions.
 */
@Service
public class ZohoStockWebhookHandlerService {

    private static final Logger LOG = LoggerFactory.getLogger(ZohoStockWebhookHandlerService.class);

    private final ApplicationProperties applicationProperties;
    private final ZohoStockWebhookEventRepository webhookEventRepository;
    private final ZohoStockReconcileService stockReconcileService;
    private final ObjectMapper objectMapper;

    public ZohoStockWebhookHandlerService(
        ApplicationProperties applicationProperties,
        ZohoStockWebhookEventRepository webhookEventRepository,
        ZohoStockReconcileService stockReconcileService,
        ObjectMapper objectMapper
    ) {
        this.applicationProperties = applicationProperties;
        this.webhookEventRepository = webhookEventRepository;
        this.stockReconcileService = stockReconcileService;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> handle(String rawBody, String signature) {
        return handle(rawBody, signature, null);
    }

    public Map<String, Object> handle(String rawBody, String signature, String sharedSecret) {
        verifySignature(rawBody, signature, sharedSecret);
        Map<String, Object> payload = WebhookPayloadSupport.parseJsonMap(objectMapper, rawBody);

        String eventId = StringUtils.trimToNull(ZohoWebhookSupport.stringValue(payload.get("eventId")));
        if (eventId == null) {
            eventId = StringUtils.trimToNull(ZohoWebhookSupport.stringValue(payload.get("event_id")));
        }
        if (eventId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing eventId");
        }
        if (webhookEventRepository.existsByEventId(eventId)) {
            return ZohoWebhookSupport.duplicateEventResult(eventId);
        }

        String sku = StringUtils.trimToNull(ZohoWebhookSupport.stringValue(payload.get("sku")));
        Long productId = parseProductId(payload.get("productId"), payload.get("product_id"));
        Integer stock = parseStock(payload.get("stock"));
        if (stock == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing or invalid stock");
        }
        if (sku == null && productId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provide sku or productId");
        }

        ZohoStockWebhookEvent record = new ZohoStockWebhookEvent();
        record.setEventId(eventId);
        record.setSku(sku);
        record.setProductId(productId);
        record.setReceivedAt(Instant.now());
        try {
            webhookEventRepository.saveAndFlush(record);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            return ZohoWebhookSupport.duplicateEventResult(eventId);
        }

        try {
            Map<String, Object> result = stockReconcileService.reconcileStockFromWebhook(sku, productId, stock);
            if (!Boolean.TRUE.equals(result.get("ok"))) {
                String reason = String.valueOf(result.getOrDefault("error", result.getOrDefault("reason", "Stock reconcile failed")));
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, reason);
            }
            LOG.debug("Processed Zoho stock webhook {} for sku={} productId={}", eventId, sku, productId);
            return result;
        } catch (RuntimeException e) {
            webhookEventRepository.delete(record);
            throw e;
        }
    }

    private void verifySignature(String rawBody, String signature, String sharedSecret) {
        ZohoWebhookSupport.verifySecretOrSignature(
            applicationProperties.getZoho().getStockWebhookSecret(),
            rawBody,
            signature,
            sharedSecret,
            "Stock webhook secret is not configured",
            "Missing stock webhook authentication",
            "Invalid stock webhook signature"
        );
    }

    private static Long parseProductId(Object camel, Object snake) {
        Object raw = camel != null ? camel : snake;
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number number) {
            return number.longValue();
        }
        String text = StringUtils.trimToNull(raw.toString());
        if (text == null) {
            return null;
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid productId");
        }
    }

    private static Integer parseStock(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number number) {
            return Math.max(0, number.intValue());
        }
        String text = StringUtils.trimToNull(raw.toString());
        if (text == null) {
            return null;
        }
        try {
            return Math.max(0, Integer.parseInt(text));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
