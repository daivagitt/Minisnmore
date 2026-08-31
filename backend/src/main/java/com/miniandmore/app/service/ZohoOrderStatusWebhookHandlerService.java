package com.miniandmore.app.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.config.ApplicationProperties;
import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.ZohoOrderStatusWebhookEvent;
import com.miniandmore.app.domain.enumeration.OrderStatus;
import com.miniandmore.app.repository.CustomerOrderRepository;
import com.miniandmore.app.repository.ZohoOrderStatusWebhookEventRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class ZohoOrderStatusWebhookHandlerService {

    private final ApplicationProperties applicationProperties;
    private final ZohoOrderStatusWebhookEventRepository webhookEventRepository;
    private final CustomerOrderRepository customerOrderRepository;
    private final OrderEventService orderEventService;
    private final ObjectMapper objectMapper;

    public ZohoOrderStatusWebhookHandlerService(
        ApplicationProperties applicationProperties,
        ZohoOrderStatusWebhookEventRepository webhookEventRepository,
        CustomerOrderRepository customerOrderRepository,
        OrderEventService orderEventService,
        ObjectMapper objectMapper
    ) {
        this.applicationProperties = applicationProperties;
        this.webhookEventRepository = webhookEventRepository;
        this.customerOrderRepository = customerOrderRepository;
        this.orderEventService = orderEventService;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> handle(String rawBody, String signature, String sharedSecret) {
        verifySignature(rawBody, signature, sharedSecret);
        Map<String, Object> payload = WebhookPayloadSupport.parseJsonMap(objectMapper, rawBody);
        Map<String, Object> salesOrder = normalizeSalesOrderPayload(eventData(payload));

        String zohoSalesOrderId = firstNonBlank(
            stringValue(salesOrder.get("salesorder_id")),
            stringValue(salesOrder.get("salesOrderId")),
            stringValue(salesOrder.get("zohoSalesOrderId")),
            stringValue(salesOrder.get("zoho_sales_order_id"))
        );
        String salesorderNumber = firstNonBlank(
            stringValue(salesOrder.get("salesorder_number")),
            stringValue(salesOrder.get("salesorderNumber")),
            stringValue(salesOrder.get("orderNumber")),
            stringValue(salesOrder.get("order_number"))
        );
        if (zohoSalesOrderId == null && salesorderNumber == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provide zohoSalesOrderId or salesorder_number");
        }

        String status = firstNonBlank(stringValue(salesOrder.get("status")), stringValue(salesOrder.get("shipped_status")));
        if (status == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing sales order status");
        }
        String eventId = eventId(payload, salesOrder, zohoSalesOrderId, salesorderNumber, status);
        if (eventId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing eventId");
        }
        if (webhookEventRepository.existsByEventId(eventId)) {
            return Map.of("ok", true, "duplicate", true, "eventId", eventId);
        }

        CustomerOrder order = findOrder(zohoSalesOrderId, salesorderNumber);
        OrderStatus statusBefore = order.getStatus();
        String trackingStatusBefore = OrderTrackingStatusResolver.resolve(order);

        ZohoOrderStatusWebhookEvent record = new ZohoOrderStatusWebhookEvent();
        record.setEventId(eventId);
        record.setZohoSalesOrderId(zohoSalesOrderId);
        record.setSalesorderNumber(salesorderNumber);
        record.setStatus(status);
        record.setReceivedAt(Instant.now());
        try {
            webhookEventRepository.saveAndFlush(record);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            return Map.of("ok", true, "duplicate", true, "eventId", eventId);
        }

        try {
            ZohoOrderStatus.applySalesOrderTracking(order, salesOrder);
            if (order.getStatus() == OrderStatus.CANCELLED && StringUtils.isBlank(order.getCancellationReason())) {
                order.setCancellationReason(firstNonBlank(stringValue(salesOrder.get("reason")), "Cancelled in Zoho"));
            }
            order.setZohoTrackingLastPulledAt(Instant.now());
            CustomerOrder saved = customerOrderRepository.save(order);
            if (saved.getStatus() != statusBefore || !OrderTrackingStatusResolver.resolve(saved).equals(trackingStatusBefore)) {
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
                saved.getStatus().name()
            );
        } catch (RuntimeException e) {
            webhookEventRepository.delete(record);
            throw e;
        }
    }

    private CustomerOrder findOrder(String zohoSalesOrderId, String salesorderNumber) {
        if (StringUtils.isNotBlank(zohoSalesOrderId)) {
            return customerOrderRepository.findOneByZohoSalesOrderId(zohoSalesOrderId.trim()).or(() -> findByOrderNumber(salesorderNumber)).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No order for Zoho sales order " + zohoSalesOrderId)
            );
        }
        return findByOrderNumber(salesorderNumber)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No order for " + salesorderNumber));
    }

    private java.util.Optional<CustomerOrder> findByOrderNumber(String salesorderNumber) {
        String trimmed = StringUtils.trimToNull(salesorderNumber);
        if (trimmed == null) {
            return java.util.Optional.empty();
        }
        return customerOrderRepository.findOneByOrderNumber(trimmed);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> eventData(Map<String, Object> payload) {
        Object data = payload.get("data");
        if (data instanceof Map<?, ?> map) {
            Map<String, Object> merged = new LinkedHashMap<>(payload);
            merged.putAll((Map<String, Object>) map);
            return merged;
        }
        return payload;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> normalizeSalesOrderPayload(Map<String, Object> data) {
        for (String key : List.of("salesorder", "sales_order", "order")) {
            Object nested = data.get(key);
            if (nested instanceof Map<?, ?> map) {
                Map<String, Object> merged = new LinkedHashMap<>(data);
                merged.putAll((Map<String, Object>) map);
                return merged;
            }
        }
        return data;
    }

    private void verifySignature(String rawBody, String signature, String sharedSecret) {
        String secret = StringUtils.trimToNull(applicationProperties.getZoho().getOrderStatusWebhookSecret());
        if (secret == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Order status webhook secret is not configured");
        }
        if (StringUtils.equals(secret, StringUtils.trimToNull(sharedSecret))) {
            return;
        }
        if (StringUtils.isBlank(signature)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing order status webhook authentication");
        }
        if (!WebhookHmacSupport.signaturesMatch(secret, rawBody, signature)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid order status webhook signature");
        }
    }

    private static String eventId(
        Map<String, Object> payload,
        Map<String, Object> salesOrder,
        String zohoSalesOrderId,
        String salesorderNumber,
        String status
    ) {
        String explicit = firstNonBlank(
            stringValue(payload.get("eventId")),
            stringValue(payload.get("event_id")),
            stringValue(payload.get("id")),
            stringValue(salesOrder.get("eventId")),
            stringValue(salesOrder.get("event_id"))
        );
        if (explicit != null) {
            return explicit;
        }
        String modified = firstNonBlank(
            stringValue(salesOrder.get("last_modified_time")),
            stringValue(salesOrder.get("updated_time")),
            stringValue(salesOrder.get("date"))
        );
        String identity = firstNonBlank(zohoSalesOrderId, salesorderNumber);
        return identity == null || modified == null ? null : "zoho-salesorder:" + identity + ":" + status + ":" + modified;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            String trimmed = StringUtils.trimToNull(value);
            if (trimmed != null) {
                return trimmed;
            }
        }
        return null;
    }

    private static String stringValue(Object value) {
        return value == null ? null : value.toString();
    }
}
