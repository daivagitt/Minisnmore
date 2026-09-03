package com.miniandmore.app.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.config.ApplicationProperties;
import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.ZohoInvoiceWebhookEvent;
import com.miniandmore.app.domain.enumeration.OrderStatus;
import com.miniandmore.app.domain.enumeration.ZohoSyncStatus;
import com.miniandmore.app.repository.CustomerOrderRepository;
import com.miniandmore.app.repository.ZohoInvoiceWebhookEventRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class ZohoInvoiceWebhookHandlerService {

    private final ApplicationProperties applicationProperties;
    private final ZohoInvoiceWebhookEventRepository webhookEventRepository;
    private final CustomerOrderRepository customerOrderRepository;
    private final OrderEventService orderEventService;
    private final ZohoSyncService zohoSyncService;
    private final ObjectMapper objectMapper;

    public ZohoInvoiceWebhookHandlerService(
        ApplicationProperties applicationProperties,
        ZohoInvoiceWebhookEventRepository webhookEventRepository,
        CustomerOrderRepository customerOrderRepository,
        OrderEventService orderEventService,
        ZohoSyncService zohoSyncService,
        ObjectMapper objectMapper
    ) {
        this.applicationProperties = applicationProperties;
        this.webhookEventRepository = webhookEventRepository;
        this.customerOrderRepository = customerOrderRepository;
        this.orderEventService = orderEventService;
        this.zohoSyncService = zohoSyncService;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> handle(String rawBody, String signature, String sharedSecret) {
        verifySignature(rawBody, signature, sharedSecret);
        Map<String, Object> payload = WebhookPayloadSupport.parseJsonMap(objectMapper, rawBody);
        Map<String, Object> data = ZohoWebhookSupport.eventData(payload);
        Map<String, Object> invoice = normalizeInvoicePayload(data);

        String eventId = eventId(payload, data, invoice);
        if (eventId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing eventId");
        }
        if (webhookEventRepository.existsByEventId(eventId)) {
            return ZohoWebhookSupport.duplicateEventResult(eventId);
        }

        String zohoInvoiceId = ZohoWebhookSupport.firstNonBlank(
            ZohoWebhookSupport.stringValue(invoice.get("invoice_id")),
            ZohoWebhookSupport.stringValue(invoice.get("invoiceId")),
            ZohoWebhookSupport.stringValue(invoice.get("zohoInvoiceId"))
        );
        String zohoSalesOrderId = ZohoWebhookSupport.firstNonBlank(
            ZohoWebhookSupport.stringValue(invoice.get("salesorder_id")),
            ZohoWebhookSupport.stringValue(invoice.get("salesorderId")),
            ZohoWebhookSupport.stringValue(data.get("salesorder_id")),
            ZohoWebhookSupport.stringValue(data.get("zohoSalesOrderId"))
        );
        String referenceNumber = ZohoWebhookSupport.firstNonBlank(
            ZohoWebhookSupport.stringValue(invoice.get("reference_number")),
            ZohoWebhookSupport.stringValue(invoice.get("referenceNumber"))
        );
        if (zohoInvoiceId == null && zohoSalesOrderId == null && referenceNumber == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provide invoice_id and salesorder_id or reference_number");
        }

        CustomerOrder order = findOrder(zohoSalesOrderId, referenceNumber);
        OrderStatus statusBefore = order.getStatus();

        ZohoInvoiceWebhookEvent record = new ZohoInvoiceWebhookEvent();
        record.setEventId(eventId);
        record.setZohoInvoiceId(zohoInvoiceId);
        record.setZohoSalesOrderId(zohoSalesOrderId);
        record.setInvoiceNumber(ZohoWebhookSupport.stringValue(invoice.get("invoice_number")));
        record.setStatus(ZohoWebhookSupport.stringValue(invoice.get("status")));
        record.setReceivedAt(Instant.now());
        try {
            webhookEventRepository.saveAndFlush(record);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            return ZohoWebhookSupport.duplicateEventResult(eventId);
        }

        try {
            ZohoInvoiceStatus.applyInvoice(order, invoice);
            order.setZohoSyncStatus(ZohoSyncStatus.SYNCED);
            order.setZohoSyncError(null);
            order.setZohoLastSyncedAt(Instant.now());
            CustomerOrder saved = customerOrderRepository.save(order);
            String status = ZohoWebhookSupport.stringValue(invoice.get("status"));
            if (ZohoInvoiceStatus.shouldCachePdf(status)) {
                zohoSyncService.ensureInvoicePdfCached(saved.getId());
            }
            if (saved.getStatus() != statusBefore) {
                orderEventService.syncLifecycleEvents(saved);
            }
            return Map.of(
                "ok",
                true,
                "eventId",
                eventId,
                "orderNumber",
                saved.getOrderNumber(),
                "status",
                StringUtils.defaultString(status)
            );
        } catch (RuntimeException e) {
            webhookEventRepository.delete(record);
            throw e;
        }
    }

    private CustomerOrder findOrder(String zohoSalesOrderId, String referenceNumber) {
        if (StringUtils.isNotBlank(zohoSalesOrderId)) {
            return customerOrderRepository
                .findOneByZohoSalesOrderId(zohoSalesOrderId.trim())
                .or(() -> findByStoreOrderNumber(referenceNumber))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No order for Zoho sales order " + zohoSalesOrderId));
        }
        return findByStoreOrderNumber(referenceNumber)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No order for " + referenceNumber));
    }

    private Optional<CustomerOrder> findByStoreOrderNumber(String referenceNumber) {
        String trimmed = StringUtils.trimToNull(referenceNumber);
        if (trimmed == null || !trimmed.startsWith("MNM-")) {
            return Optional.empty();
        }
        return customerOrderRepository.findOneByOrderNumber(trimmed);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> normalizeInvoicePayload(Map<String, Object> data) {
        for (String key : List.of("invoice")) {
            Object nested = data.get(key);
            if (nested instanceof Map<?, ?> map) {
                Map<String, Object> normalized = new LinkedHashMap<>(data);
                normalized.putAll((Map<String, Object>) map);
                return normalized;
            }
        }
        return data;
    }

    private void verifySignature(String rawBody, String signature, String sharedSecret) {
        ZohoWebhookSupport.verifySecretOrSignature(
            applicationProperties.getZoho().getInvoiceWebhookSecret(),
            rawBody,
            signature,
            sharedSecret,
            "Invoice webhook secret is not configured",
            "Missing invoice webhook authentication",
            "Invalid invoice webhook signature"
        );
    }

    private static String eventId(Map<String, Object> payload, Map<String, Object> data, Map<String, Object> invoice) {
        String explicit = ZohoWebhookSupport.explicitEventIdIncludingLegacyId(payload, data, invoice);
        if (explicit != null) {
            return explicit;
        }
        String invoiceId = ZohoWebhookSupport.firstNonBlank(
            ZohoWebhookSupport.stringValue(invoice.get("invoice_id")),
            ZohoWebhookSupport.stringValue(invoice.get("invoiceId"))
        );
        String status = ZohoWebhookSupport.stringValue(invoice.get("status"));
        String modified = ZohoWebhookSupport.firstNonBlank(
            ZohoWebhookSupport.stringValue(invoice.get("last_modified_time")),
            ZohoWebhookSupport.stringValue(invoice.get("updated_time")),
            ZohoWebhookSupport.stringValue(invoice.get("created_time"))
        );
        if (invoiceId == null) {
            return null;
        }
        return "zoho-invoice:" + invoiceId + ":" + ZohoWebhookSupport.firstNonBlank(status, "unknown") + ":" + ZohoWebhookSupport.firstNonBlank(modified, "unknown");
    }
}
