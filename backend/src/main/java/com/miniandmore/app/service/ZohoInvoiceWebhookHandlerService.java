package com.miniandmore.app.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.config.ApplicationProperties;
import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.OrderReturn;
import com.miniandmore.app.domain.ZohoInvoiceWebhookEvent;
import com.miniandmore.app.domain.enumeration.OrderStatus;
import com.miniandmore.app.domain.enumeration.ZohoSyncStatus;
import com.miniandmore.app.repository.CustomerOrderRepository;
import com.miniandmore.app.repository.OrderReturnRepository;
import com.miniandmore.app.repository.ZohoInvoiceWebhookEventRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class ZohoInvoiceWebhookHandlerService {

    private static final Logger LOG = LoggerFactory.getLogger(ZohoInvoiceWebhookHandlerService.class);

    private final ApplicationProperties applicationProperties;
    private final ZohoInvoiceWebhookEventRepository webhookEventRepository;
    private final CustomerOrderRepository customerOrderRepository;
    private final OrderReturnRepository orderReturnRepository;
    private final OrderEventService orderEventService;
    private final ZohoSyncService zohoSyncService;
    private final ObjectMapper objectMapper;

    public ZohoInvoiceWebhookHandlerService(
        ApplicationProperties applicationProperties,
        ZohoInvoiceWebhookEventRepository webhookEventRepository,
        CustomerOrderRepository customerOrderRepository,
        OrderReturnRepository orderReturnRepository,
        OrderEventService orderEventService,
        ZohoSyncService zohoSyncService,
        ObjectMapper objectMapper
    ) {
        this.applicationProperties = applicationProperties;
        this.webhookEventRepository = webhookEventRepository;
        this.customerOrderRepository = customerOrderRepository;
        this.orderReturnRepository = orderReturnRepository;
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

        return findReplacementReturn(zohoInvoiceId, referenceNumber)
            .map(orderReturn -> acceptReplacementInvoice(orderReturn, eventId, zohoInvoiceId, zohoSalesOrderId, invoice))
            .orElseGet(() -> applyOrSkipCheckoutInvoice(eventId, zohoInvoiceId, zohoSalesOrderId, referenceNumber, invoice));
    }

    private Map<String, Object> applyOrSkipCheckoutInvoice(
        String eventId,
        String zohoInvoiceId,
        String zohoSalesOrderId,
        String referenceNumber,
        Map<String, Object> invoice
    ) {
        Optional<CustomerOrder> matchedOrder = findCheckoutOrder(zohoInvoiceId, zohoSalesOrderId, referenceNumber);
        if (matchedOrder.isEmpty()) {
            LOG.info(
                "Skipping Zoho invoice webhook {} — no MiniMS order for salesorder_id={} reference_number={}",
                eventId,
                zohoSalesOrderId,
                referenceNumber
            );
            return ZohoWebhookSupport.skippedResult("no matching store order", eventId);
        }
        return applyCheckoutInvoice(matchedOrder.orElseThrow(), eventId, zohoInvoiceId, zohoSalesOrderId, invoice);
    }

    private Map<String, Object> acceptReplacementInvoice(
        OrderReturn orderReturn,
        String eventId,
        String zohoInvoiceId,
        String zohoSalesOrderId,
        Map<String, Object> invoice
    ) {
        ZohoInvoiceWebhookEvent record = insertEvent(eventId, zohoInvoiceId, zohoSalesOrderId, invoice);
        if (record == null) {
            return ZohoWebhookSupport.duplicateEventResult(eventId);
        }
        try {
            String invoiceId = ZohoWebhookSupport.firstNonBlank(
                ZohoWebhookSupport.stringValue(invoice.get("invoice_id")),
                ZohoWebhookSupport.stringValue(invoice.get("invoiceId"))
            );
            if (StringUtils.isNotBlank(invoiceId) && StringUtils.isBlank(orderReturn.getZohoReplacementInvoiceId())) {
                orderReturn.setZohoReplacementInvoiceId(invoiceId.trim());
                orderReturnRepository.save(orderReturn);
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("ok", true);
            result.put("eventId", eventId);
            result.put("orderNumber", orderReturn.getOrder().getOrderNumber());
            result.put("status", StringUtils.defaultString(ZohoWebhookSupport.stringValue(invoice.get("status"))));
            result.put("replacement", true);
            return result;
        } catch (RuntimeException e) {
            webhookEventRepository.delete(record);
            throw e;
        }
    }

    private Map<String, Object> applyCheckoutInvoice(
        CustomerOrder order,
        String eventId,
        String zohoInvoiceId,
        String zohoSalesOrderId,
        Map<String, Object> invoice
    ) {
        OrderStatus statusBefore = order.getStatus();
        ZohoInvoiceWebhookEvent record = insertEvent(eventId, zohoInvoiceId, zohoSalesOrderId, invoice);
        if (record == null) {
            return ZohoWebhookSupport.duplicateEventResult(eventId);
        }
        try {
            String status = ZohoWebhookSupport.stringValue(invoice.get("status"));
            ZohoInvoiceStatus.applyInvoice(order, invoice);
            order.setZohoSyncStatus(ZohoSyncStatus.SYNCED);
            order.setZohoSyncError(null);
            order.setZohoLastSyncedAt(Instant.now());
            CustomerOrder saved = customerOrderRepository.save(order);
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

    private ZohoInvoiceWebhookEvent insertEvent(String eventId, String zohoInvoiceId, String zohoSalesOrderId, Map<String, Object> invoice) {
        ZohoInvoiceWebhookEvent record = new ZohoInvoiceWebhookEvent();
        record.setEventId(eventId);
        record.setZohoInvoiceId(zohoInvoiceId);
        record.setZohoSalesOrderId(zohoSalesOrderId);
        record.setInvoiceNumber(ZohoWebhookSupport.stringValue(invoice.get("invoice_number")));
        record.setStatus(ZohoWebhookSupport.stringValue(invoice.get("status")));
        record.setReceivedAt(Instant.now());
        try {
            webhookEventRepository.saveAndFlush(record);
            return record;
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            return null;
        }
    }

    private Optional<OrderReturn> findReplacementReturn(String zohoInvoiceId, String referenceNumber) {
        if (StringUtils.isNotBlank(zohoInvoiceId)) {
            Optional<OrderReturn> byInvoiceId = orderReturnRepository.findFirstByZohoReplacementInvoiceIdOrderByIdDesc(zohoInvoiceId.trim());
            if (byInvoiceId.filter(value -> value.getOrder() != null).isPresent()) {
                return byInvoiceId;
            }
        }
        String returnNumber = ZohoExchangeInvoiceService.returnNumberFromInvoiceReference(referenceNumber);
        if (returnNumber == null) {
            return Optional.empty();
        }
        return orderReturnRepository.findOneByReturnNumber(returnNumber).filter(value -> value.getOrder() != null);
    }

    private Optional<CustomerOrder> findCheckoutOrder(String zohoInvoiceId, String zohoSalesOrderId, String referenceNumber) {
        Optional<CustomerOrder> found = Optional.empty();
        if (StringUtils.isNotBlank(zohoInvoiceId)) {
            found = customerOrderRepository.findOneByZohoInvoiceId(zohoInvoiceId.trim());
        }
        if (found.isEmpty() && StringUtils.isNotBlank(zohoSalesOrderId)) {
            found = customerOrderRepository.findOneByZohoSalesOrderId(zohoSalesOrderId.trim());
        }
        if (found.isEmpty()) {
            found = findByStoreOrderNumber(referenceNumber);
        }
        if (found.isEmpty() && ZohoWebhookSupport.isStoreOrderNumber(referenceNumber)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No order for " + referenceNumber);
        }
        return found;
    }

    private Optional<CustomerOrder> findByStoreOrderNumber(String referenceNumber) {
        if (!ZohoWebhookSupport.isStoreOrderNumber(referenceNumber)) {
            return Optional.empty();
        }
        return customerOrderRepository.findOneByOrderNumber(referenceNumber.trim());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> normalizeInvoicePayload(Map<String, Object> data) {
        Map<String, Object> normalized = ZohoWebhookSupport.mergeNestedPayload(data, "invoice");
        String referenceNumber = ZohoWebhookSupport.firstNonBlank(
            ZohoWebhookSupport.stringValue(normalized.get("reference_number")),
            ZohoWebhookSupport.stringValue(normalized.get("referenceNumber"))
        );
        if (ZohoExchangeInvoiceService.returnNumberFromInvoiceReference(referenceNumber) != null) {
            return normalized;
        }
        if (StringUtils.isNotBlank(ZohoWebhookSupport.stringValue(normalized.get("salesorder_id")))) {
            return normalized;
        }
        Object salesorders = normalized.get("salesorders");
        if (!(salesorders instanceof List<?> list) || list.isEmpty() || !(list.getFirst() instanceof Map<?, ?> firstOrder)) {
            return normalized;
        }
        if (normalized == data) {
            normalized = new LinkedHashMap<>(data);
        }
        Map<String, Object> first = (Map<String, Object>) firstOrder;
        String salesOrderId = ZohoWebhookSupport.firstNonBlank(ZohoWebhookSupport.stringValue(first.get("salesorder_id")));
        if (salesOrderId != null) {
            normalized.put("salesorder_id", salesOrderId);
        }
        if (StringUtils.isBlank(ZohoWebhookSupport.stringValue(normalized.get("reference_number")))) {
            String nestedReference = ZohoWebhookSupport.firstNonBlank(
                ZohoWebhookSupport.stringValue(first.get("reference_number")),
                ZohoWebhookSupport.stringValue(first.get("salesorder_number"))
            );
            if (nestedReference != null) {
                normalized.put("reference_number", nestedReference);
            }
        }
        return normalized;
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
        return (
            "zoho-invoice:" +
            invoiceId +
            ":" +
            ZohoWebhookSupport.firstNonBlank(status, "unknown") +
            ":" +
            ZohoWebhookSupport.firstNonBlank(modified, "unknown")
        );
    }
}
