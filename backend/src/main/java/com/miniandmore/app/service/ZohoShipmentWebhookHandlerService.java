package com.miniandmore.app.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.config.ApplicationProperties;
import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.ZohoShipmentWebhookEvent;
import com.miniandmore.app.domain.enumeration.OrderStatus;
import com.miniandmore.app.repository.CustomerOrderRepository;
import com.miniandmore.app.repository.ZohoShipmentWebhookEventRepository;
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
public class ZohoShipmentWebhookHandlerService {

    private final ApplicationProperties applicationProperties;
    private final ZohoShipmentWebhookEventRepository webhookEventRepository;
    private final CustomerOrderRepository customerOrderRepository;
    private final OrderEventService orderEventService;
    private final ObjectMapper objectMapper;

    public ZohoShipmentWebhookHandlerService(
        ApplicationProperties applicationProperties,
        ZohoShipmentWebhookEventRepository webhookEventRepository,
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

    public Map<String, Object> handle(String rawBody, String signature) {
        return handle(rawBody, signature, null);
    }

    public Map<String, Object> handle(String rawBody, String signature, String sharedSecret) {
        verifySignature(rawBody, signature, sharedSecret);
        Map<String, Object> payload = WebhookPayloadSupport.parseJsonMap(objectMapper, rawBody);

        Map<String, Object> data = eventData(payload);
        Map<String, Object> packageLike = normalizePackagePayload(data);
        if (!hasShipmentSignal(packageLike)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing shipment/package fields");
        }
        String eventId = eventId(payload, data, packageLike);
        if (eventId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing eventId");
        }
        if (webhookEventRepository.existsByEventId(eventId)) {
            return Map.of("ok", true, "duplicate", true, "eventId", eventId);
        }
        String zohoSalesOrderId = firstNonBlank(
            stringValue(packageLike.get("salesorder_id")),
            stringValue(data.get("zohoSalesOrderId")),
            stringValue(data.get("zoho_sales_order_id"))
        );
        String salesorderNumber = firstNonBlank(
            stringValue(packageLike.get("salesorder_number")),
            stringValue(data.get("salesorderNumber")),
            stringValue(data.get("orderNumber")),
            stringValue(data.get("order_number"))
        );
        if (zohoSalesOrderId == null && salesorderNumber == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provide zohoSalesOrderId or salesorder_number");
        }

        CustomerOrder order = findOrder(zohoSalesOrderId, salesorderNumber);
        OrderStatus statusBefore = order.getStatus();

        ZohoShipmentWebhookEvent record = new ZohoShipmentWebhookEvent();
        record.setEventId(eventId);
        record.setZohoSalesOrderId(zohoSalesOrderId);
        record.setSalesorderNumber(salesorderNumber);
        record.setPackageId(stringValue(packageLike.get("package_id")));
        record.setShipmentId(stringValue(packageLike.get("shipment_id")));
        record.setStatus(stringValue(packageLike.get("status")));
        record.setReceivedAt(Instant.now());
        try {
            webhookEventRepository.saveAndFlush(record);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            return Map.of("ok", true, "duplicate", true, "eventId", eventId);
        }

        try {
            ZohoOrderStatus.applyPackages(order, List.of(packageLike));
            order.setZohoTrackingLastPulledAt(Instant.now());
            CustomerOrder saved = customerOrderRepository.save(order);
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
    private static Map<String, Object> normalizePackagePayload(Map<String, Object> data) {
        Map<String, Object> source = data;
        for (String key : List.of("package", "shipment", "shipment_order", "shipmentOrder")) {
            Object nested = data.get(key);
            if (nested instanceof Map<?, ?> map) {
                source = (Map<String, Object>) map;
                break;
            }
        }

        List<Map<String, Object>> sources = source == data ? List.of(source) : List.of(source, data);
        Map<String, Object> normalized = new LinkedHashMap<>(source);
        copyFirst(normalized, "salesorder_id", sources, "salesorder_id", "zohoSalesOrderId", "zoho_sales_order_id");
        copyFirst(normalized, "salesorder_number", sources, "salesorder_number", "salesorderNumber", "orderNumber", "order_number");
        copyFirst(normalized, "package_id", sources, "package_id", "packageId");
        copyFirst(normalized, "shipment_id", sources, "shipment_id", "shipmentId", "shipmentorder_id", "shipmentOrderId");
        copyFirst(normalized, "tracking_number", sources, "tracking_number", "trackingNumber", "reference_number");
        copyFirst(normalized, "carrier", sources, "carrier", "delivery_method", "deliveryMethod");
        copyFirst(normalized, "delivery_method", sources, "delivery_method", "deliveryMethod", "carrier");
        copyFirst(normalized, "shipment_date", sources, "shipment_date", "shipmentDate", "date");
        copyFirst(normalized, "status", sources, "status", "shipment_status", "shipmentStatus");
        copyFirst(normalized, "detailed_status", sources, "detailed_status", "detailedStatus");
        copyFirst(normalized, "status_message", sources, "status_message", "statusMessage");
        return normalized;
    }

    private void verifySignature(String rawBody, String signature, String sharedSecret) {
        String secret = StringUtils.trimToNull(applicationProperties.getZoho().getShipmentWebhookSecret());
        if (secret == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Shipment webhook secret is not configured");
        }
        if (StringUtils.equals(secret, StringUtils.trimToNull(sharedSecret))) {
            return;
        }
        if (StringUtils.isBlank(signature)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing shipment webhook authentication");
        }
        if (!WebhookHmacSupport.signaturesMatch(secret, rawBody, signature)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid shipment webhook signature");
        }
    }

    private java.util.Optional<CustomerOrder> findByOrderNumber(String salesorderNumber) {
        String trimmed = StringUtils.trimToNull(salesorderNumber);
        if (trimmed == null) {
            return java.util.Optional.empty();
        }
        return customerOrderRepository.findOneByOrderNumber(trimmed);
    }

    private static boolean hasShipmentSignal(Map<String, Object> packageLike) {
        return firstNonBlank(
                stringValue(packageLike.get("package_id")),
                stringValue(packageLike.get("shipment_id")),
                stringValue(packageLike.get("tracking_number")),
                stringValue(packageLike.get("carrier")),
                stringValue(packageLike.get("delivery_method")),
                stringValue(packageLike.get("shipment_date")),
                stringValue(packageLike.get("status")),
                stringValue(packageLike.get("detailed_status")),
                stringValue(packageLike.get("status_message"))
            ) !=
            null;
    }

    private static String eventId(Map<String, Object> payload, Map<String, Object> data, Map<String, Object> packageLike) {
        String explicit = firstNonBlank(
            stringValue(payload.get("eventId")),
            stringValue(payload.get("event_id")),
            stringValue(payload.get("id")),
            stringValue(data.get("eventId")),
            stringValue(data.get("event_id")),
            stringValue(data.get("id")),
            stringValue(packageLike.get("eventId")),
            stringValue(packageLike.get("event_id")),
            stringValue(packageLike.get("id"))
        );
        if (explicit != null) {
            return explicit;
        }
        String entityId = firstNonBlank(stringValue(packageLike.get("shipment_id")), stringValue(packageLike.get("package_id")));
        String status = firstNonBlank(stringValue(packageLike.get("status")), stringValue(packageLike.get("detailed_status")));
        String modified = firstNonBlank(
            stringValue(packageLike.get("last_modified_time")),
            stringValue(packageLike.get("updated_time")),
            stringValue(packageLike.get("created_time")),
            stringValue(packageLike.get("shipment_date"))
        );
        if (entityId == null || (status == null && modified == null)) {
            return null;
        }
        return "zoho-shipment:" + entityId + ":" + firstNonBlank(status, "unknown") + ":" + firstNonBlank(modified, "unknown");
    }

    private static void copyFirst(Map<String, Object> target, String targetKey, List<Map<String, Object>> sources, String... sourceKeys) {
        for (Map<String, Object> source : sources) {
            for (String sourceKey : sourceKeys) {
                Object value = source.get(sourceKey);
                if (StringUtils.isNotBlank(stringValue(value))) {
                    target.put(targetKey, value);
                    return;
                }
            }
        }
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
