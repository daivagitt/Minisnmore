package com.miniandmore.app.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.client.ZohoBooksClient;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class ZohoShipmentWebhookHandlerService {

    private static final Logger LOG = LoggerFactory.getLogger(ZohoShipmentWebhookHandlerService.class);

    private final ApplicationProperties applicationProperties;
    private final ZohoShipmentWebhookEventRepository webhookEventRepository;
    private final CustomerOrderRepository customerOrderRepository;
    private final OrderEventService orderEventService;
    private final ZohoBooksClient booksClient;
    private final ObjectMapper objectMapper;

    public ZohoShipmentWebhookHandlerService(
        ApplicationProperties applicationProperties,
        ZohoShipmentWebhookEventRepository webhookEventRepository,
        CustomerOrderRepository customerOrderRepository,
        OrderEventService orderEventService,
        ZohoBooksClient booksClient,
        ObjectMapper objectMapper
    ) {
        this.applicationProperties = applicationProperties;
        this.webhookEventRepository = webhookEventRepository;
        this.customerOrderRepository = customerOrderRepository;
        this.orderEventService = orderEventService;
        this.booksClient = booksClient;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> handle(String rawBody, String signature) {
        return handle(rawBody, signature, null);
    }

    public Map<String, Object> handle(String rawBody, String signature, String sharedSecret) {
        verifySignature(rawBody, signature, sharedSecret);
        Map<String, Object> payload = WebhookPayloadSupport.parseJsonMap(objectMapper, rawBody);

        Map<String, Object> data = ZohoWebhookSupport.eventData(payload);
        Map<String, Object> packageLike = normalizePackagePayload(data);
        String explicitEventId = ZohoWebhookSupport.explicitEventIdIncludingLegacyId(payload, data, packageLike);
        enrichShipmentStatus(data, packageLike, explicitEventId);
        String zohoSalesOrderId = ZohoWebhookSupport.firstNonBlank(
            ZohoWebhookSupport.stringValue(packageLike.get("salesorder_id")),
            ZohoWebhookSupport.stringValue(data.get("zohoSalesOrderId")),
            ZohoWebhookSupport.stringValue(data.get("zoho_sales_order_id"))
        );
        String salesorderNumber = ZohoWebhookSupport.firstNonBlank(
            ZohoWebhookSupport.stringValue(packageLike.get("salesorder_number")),
            ZohoWebhookSupport.stringValue(data.get("salesorderNumber")),
            ZohoWebhookSupport.stringValue(data.get("orderNumber")),
            ZohoWebhookSupport.stringValue(data.get("order_number"))
        );
        if (zohoSalesOrderId == null && salesorderNumber == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provide zohoSalesOrderId or salesorder_number");
        }
        enrichFromZohoSalesOrder(packageLike, zohoSalesOrderId);
        backfillMissingShipmentStatus(packageLike, zohoSalesOrderId, salesorderNumber);
        if (!hasShipmentSignal(packageLike)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing shipment/package fields");
        }
        String eventId = eventId(payload, data, packageLike, explicitEventId, zohoSalesOrderId, salesorderNumber);
        if (eventId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing eventId");
        }

        CustomerOrder order = findOrder(zohoSalesOrderId, salesorderNumber);
        OrderStatus statusBefore = order.getStatus();
        String trackingStatusBefore = OrderTrackingStatusResolver.resolve(order);
        boolean duplicateEvent = webhookEventRepository.existsByEventId(eventId);
        if (duplicateEvent && !shouldReapplyDeliveryUpdate(order, packageLike)) {
            return ZohoWebhookSupport.duplicateEventResult(eventId);
        }

        ZohoShipmentWebhookEvent record = null;
        if (!duplicateEvent) {
            record = new ZohoShipmentWebhookEvent();
            record.setEventId(eventId);
            record.setZohoSalesOrderId(zohoSalesOrderId);
            record.setSalesorderNumber(salesorderNumber);
            record.setPackageId(ZohoWebhookSupport.stringValue(packageLike.get("package_id")));
            record.setShipmentId(
                ZohoWebhookSupport.firstNonBlank(
                    ZohoWebhookSupport.stringValue(packageLike.get("shipment_id")),
                    ZohoWebhookSupport.stringValue(packageLike.get("shipment_order_id"))
                )
            );
            record.setStatus(ZohoWebhookSupport.stringValue(packageLike.get("status")));
            record.setReceivedAt(Instant.now());
            try {
                webhookEventRepository.saveAndFlush(record);
            } catch (org.springframework.dao.DataIntegrityViolationException e) {
                if (!shouldReapplyDeliveryUpdate(order, packageLike)) {
                    return ZohoWebhookSupport.duplicateEventResult(eventId);
                }
            }
        }

        try {
            return applyShipmentUpdate(order, packageLike, eventId, statusBefore, trackingStatusBefore);
        } catch (RuntimeException e) {
            if (record != null) {
                webhookEventRepository.delete(record);
            }
            throw e;
        }
    }

    private Map<String, Object> applyShipmentUpdate(
        CustomerOrder order,
        Map<String, Object> packageLike,
        String eventId,
        OrderStatus statusBefore,
        String trackingStatusBefore
    ) {
        ZohoOrderStatus.applyPackages(order, List.of(packageLike));
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
    }

    private static boolean shouldReapplyDeliveryUpdate(CustomerOrder order, Map<String, Object> packageLike) {
        if (order.getStatus() == OrderStatus.FULFILLED || order.getStatus() == OrderStatus.CANCELLED) {
            return false;
        }
        return ZohoOrderStatus.indicatesShipmentDelivery(packageLike);
    }

    private static void enrichShipmentStatus(Map<String, Object> data, Map<String, Object> packageLike, String explicitEventId) {
        if (StringUtils.isNotBlank(ZohoWebhookSupport.stringValue(packageLike.get("status")))) {
            return;
        }
        String inferred = ZohoWebhookSupport.firstNonBlank(
            ZohoWebhookSupport.stringValue(packageLike.get("shipment_status")),
            ZohoWebhookSupport.stringValue(packageLike.get("current_status")),
            ZohoWebhookSupport.stringValue(packageLike.get("delivery_status")),
            ZohoWebhookSupport.stringValue(packageLike.get("detailed_status")),
            ZohoWebhookSupport.stringValue(packageLike.get("status_message")),
            ZohoWebhookSupport.stringValue(data.get("status")),
            ZohoWebhookSupport.stringValue(data.get("shipment_status")),
            ZohoWebhookSupport.stringValue(data.get("current_status")),
            ZohoWebhookSupport.stringValue(data.get("delivery_status")),
            inferStatusFromEventId(explicitEventId)
        );
        if (StringUtils.isNotBlank(inferred)) {
            packageLike.put("status", inferred);
        }
    }

    private static String inferStatusFromEventId(String eventId) {
        if (StringUtils.isBlank(eventId)) {
            return null;
        }
        String lower = eventId.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("delivered")) {
            return "delivered";
        }
        if (lower.contains("fulfilled")) {
            return "fulfilled";
        }
        if (lower.contains("out_for_delivery")) {
            return "out_for_delivery";
        }
        if (lower.contains("in_transit")) {
            return "in_transit";
        }
        if (lower.contains("shipped")) {
            return "shipped";
        }
        return null;
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
        ZohoWebhookSupport.blankUnresolvedPlaceholders(normalized);
        copyFirst(normalized, "salesorder_id", sources, "salesorder_id", "zohoSalesOrderId", "zoho_sales_order_id");
        copyFirst(normalized, "salesorder_number", sources, "salesorder_number", "salesorderNumber", "orderNumber", "order_number");
        copyFirst(normalized, "package_id", sources, "package_id", "packageId");
        copyFirst(normalized, "shipment_order_id", sources, "shipment_order_id", "shipmentorder_id", "shipmentOrderId");
        copyFirst(normalized, "shipment_id", sources, "shipment_id", "shipmentId");
        copyFirst(normalized, "tracking_number", sources, "tracking_number", "trackingNumber");
        copyFirst(normalized, "reference_number", sources, "reference_number", "referenceNumber");
        copyReferenceNumberAsTrackingIfSafe(normalized);
        copyFirst(normalized, "carrier", sources, "carrier", "delivery_method", "deliveryMethod");
        copyFirst(normalized, "delivery_method", sources, "delivery_method", "deliveryMethod", "carrier");
        copyFirst(normalized, "shipment_date", sources, "shipment_date", "shipmentDate", "date", "delivered_date", "deliveredDate", "delivery_date", "deliveryDate");
        copyFirst(
            normalized,
            "status",
            sources,
            "status",
            "shipment_status",
            "shipmentStatus",
            "status_formatted",
            "statusFormatted",
            "shipment_status_formatted",
            "shipmentStatusFormatted",
            "current_status",
            "currentStatus"
        );
        copyFirst(normalized, "current_status", sources, "current_status", "currentStatus");
        copyFirst(normalized, "shipment_status", sources, "shipment_status", "shipmentStatus");
        copyFirst(
            normalized,
            "shipment_delivered_date",
            sources,
            "shipment_delivered_date",
            "shipmentDeliveredDate",
            "shipment_delivered_date_formatted",
            "shipmentDeliveredDateFormatted"
        );
        copyFirst(
            normalized,
            "delivery_status",
            sources,
            "delivery_status",
            "deliveryStatus",
            "cf_minims_delivery_status",
            "cfMinimsDeliveryStatus"
        );
        copyFirst(
            normalized,
            "cf_minims_delivery_status",
            sources,
            "cf_minims_delivery_status",
            "cfMinimsDeliveryStatus",
            "delivery_status",
            "deliveryStatus"
        );
        copyFirst(normalized, "detailed_status", sources, "detailed_status", "detailedStatus");
        copyFirst(normalized, "status_message", sources, "status_message", "statusMessage");
        copyFirst(normalized, "last_modified_time", sources, "last_modified_time", "lastModifiedTime", "updated_time", "updatedTime");
        ZohoWebhookSupport.blankUnresolvedPlaceholders(normalized);
        return normalized;
    }

    private void verifySignature(String rawBody, String signature, String sharedSecret) {
        ZohoWebhookSupport.verifySecretOrSignature(
            applicationProperties.getZoho().getShipmentWebhookSecret(),
            rawBody,
            signature,
            sharedSecret,
            "Shipment webhook secret is not configured",
            "Missing shipment webhook authentication",
            "Invalid shipment webhook signature"
        );
    }

    private java.util.Optional<CustomerOrder> findByOrderNumber(String salesorderNumber) {
        String trimmed = StringUtils.trimToNull(salesorderNumber);
        if (trimmed == null) {
            return java.util.Optional.empty();
        }
        return customerOrderRepository.findOneByOrderNumber(trimmed);
    }

    @SuppressWarnings("unchecked")
    private void enrichFromZohoSalesOrder(Map<String, Object> packageLike, String zohoSalesOrderId) {
        if (hasExplicitShipmentStatus(packageLike) || StringUtils.isBlank(zohoSalesOrderId) || booksClient == null) {
            return;
        }
        try {
            Map<String, Object> response = booksClient.getSalesOrder(zohoSalesOrderId.trim());
            if (response == null || response.isEmpty()) {
                return;
            }
            Map<String, Object> salesOrder = (Map<String, Object>) response.getOrDefault("salesorder", response);
            if (salesOrder == null) {
                return;
            }
            Map<String, Object> pkg = pickBestZohoPackage(salesOrder.get("packages"));
            if (pkg == null) {
                return;
            }
            mergeZohoPackageIntoWebhook(packageLike, pkg);
        } catch (RuntimeException e) {
            LOG.debug("Could not enrich shipment webhook from Zoho sales order {}: {}", zohoSalesOrderId, e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> pickBestZohoPackage(Object rawPackages) {
        if (!(rawPackages instanceof List<?> packages) || packages.isEmpty()) {
            return null;
        }
        Map<String, Object> best = null;
        int bestRank = -1;
        for (Object raw : packages) {
            if (!(raw instanceof Map<?, ?> map)) {
                continue;
            }
            Map<String, Object> pkg = (Map<String, Object>) map;
            int rank = 0;
            if (ZohoOrderStatus.indicatesShipmentDelivery(pkg) || ZohoOrderStatus.indicatesShipmentDelivery(nestedShipmentOrder(pkg))) {
                rank = 2;
            } else if (
                StringUtils.isNotBlank(ZohoWebhookSupport.stringValue(pkg.get("shipment_id"))) ||
                nestedShipmentOrder(pkg) != null
            ) {
                rank = 1;
            }
            if (rank > bestRank) {
                best = pkg;
                bestRank = rank;
            }
        }
        return best;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> nestedShipmentOrder(Map<String, Object> pkg) {
        Object nested = pkg.get("shipment_order");
        if (nested instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return null;
    }

    private static void mergeZohoPackageIntoWebhook(Map<String, Object> target, Map<String, Object> pkg) {
        List<Map<String, Object>> sources = nestedShipmentOrder(pkg) != null ? List.of(pkg, nestedShipmentOrder(pkg)) : List.of(pkg);
        copyFirst(target, "status", sources, "status", "shipment_status", "status_formatted", "shipment_status_formatted");
        copyFirst(target, "shipment_status", sources, "shipment_status", "shipmentStatus");
        copyFirst(target, "package_id", sources, "package_id", "packageId");
        copyFirst(target, "shipment_id", sources, "shipment_id", "shipmentId");
        copyFirst(target, "carrier", sources, "carrier");
        copyFirst(target, "delivery_method", sources, "delivery_method", "deliveryMethod", "carrier");
        copyFirst(target, "shipment_date", sources, "shipment_date", "shipmentDate");
        copyFirst(
            target,
            "shipment_delivered_date",
            sources,
            "shipment_delivered_date",
            "shipmentDeliveredDate",
            "shipment_delivered_date_formatted"
        );
    }

    private static boolean hasShipmentSignal(Map<String, Object> packageLike) {
        return ZohoWebhookSupport.firstNonBlank(
                ZohoWebhookSupport.stringValue(packageLike.get("package_id")),
                ZohoWebhookSupport.stringValue(packageLike.get("shipment_id")),
                ZohoWebhookSupport.stringValue(packageLike.get("shipment_order_id")),
                ZohoWebhookSupport.stringValue(packageLike.get("tracking_number")),
                ZohoWebhookSupport.stringValue(packageLike.get("carrier")),
                ZohoWebhookSupport.stringValue(packageLike.get("delivery_method")),
                ZohoWebhookSupport.stringValue(packageLike.get("shipment_date")),
                ZohoWebhookSupport.stringValue(packageLike.get("status")),
                ZohoWebhookSupport.stringValue(packageLike.get("current_status")),
                ZohoWebhookSupport.stringValue(packageLike.get("delivery_status")),
                ZohoWebhookSupport.stringValue(packageLike.get("detailed_status")),
                ZohoWebhookSupport.stringValue(packageLike.get("status_message"))
            ) !=
            null;
    }

    private static String eventId(
        Map<String, Object> payload,
        Map<String, Object> data,
        Map<String, Object> packageLike,
        String explicitEventId,
        String zohoSalesOrderId,
        String salesorderNumber
    ) {
        if (explicitEventId != null) {
            return explicitEventId;
        }
        String entityId = ZohoWebhookSupport.firstNonBlank(
            ZohoWebhookSupport.stringValue(packageLike.get("shipment_id")),
            ZohoWebhookSupport.stringValue(packageLike.get("package_id")),
            ZohoWebhookSupport.stringValue(packageLike.get("shipment_order_id")),
            zohoSalesOrderId,
            salesorderNumber
        );
        String status = ZohoWebhookSupport.firstNonBlank(
            ZohoWebhookSupport.stringValue(packageLike.get("status")),
            ZohoWebhookSupport.stringValue(packageLike.get("detailed_status")),
            ZohoWebhookSupport.stringValue(packageLike.get("shipment_status")),
            ZohoWebhookSupport.stringValue(packageLike.get("current_status")),
            ZohoWebhookSupport.stringValue(packageLike.get("delivery_status"))
        );
        String modified = ZohoWebhookSupport.firstNonBlank(
            ZohoWebhookSupport.stringValue(packageLike.get("last_modified_time")),
            ZohoWebhookSupport.stringValue(packageLike.get("updated_time")),
            ZohoWebhookSupport.stringValue(packageLike.get("created_time")),
            ZohoWebhookSupport.stringValue(packageLike.get("shipment_date"))
        );
        if (entityId == null || (status == null && modified == null)) {
            return null;
        }
        return "zoho-shipment:" + entityId + ":" + ZohoWebhookSupport.firstNonBlank(status, "unknown") + ":" + ZohoWebhookSupport.firstNonBlank(modified, "unknown");
    }

    private static boolean hasExplicitShipmentStatus(Map<String, Object> packageLike) {
        return ZohoWebhookSupport.firstNonBlank(
            ZohoWebhookSupport.stringValue(packageLike.get("status")),
            ZohoWebhookSupport.stringValue(packageLike.get("current_status")),
            ZohoWebhookSupport.stringValue(packageLike.get("delivery_status")),
            ZohoWebhookSupport.stringValue(packageLike.get("detailed_status")),
            ZohoWebhookSupport.stringValue(packageLike.get("status_message")),
            ZohoWebhookSupport.stringValue(packageLike.get("cf_minims_delivery_status")),
            ZohoWebhookSupport.stringValue(packageLike.get("shipment_status")),
            ZohoWebhookSupport.stringValue(packageLike.get("shipment_delivered_date"))
        ) !=
        null;
    }

    private static boolean hasTransitFields(Map<String, Object> packageLike) {
        return ZohoWebhookSupport.firstNonBlank(
            ZohoWebhookSupport.stringValue(packageLike.get("shipment_date")),
            ZohoWebhookSupport.stringValue(packageLike.get("tracking_number")),
            ZohoWebhookSupport.stringValue(packageLike.get("carrier")),
            ZohoWebhookSupport.stringValue(packageLike.get("delivery_method"))
        ) !=
        null;
    }

    /**
     * Date/carrier without a status is the live "on the way" payload. Do not treat it as delivered.
     * Only a payload with no transit fields at all (sales-order id only) is backfilled to delivered.
     */
    private static void backfillMissingShipmentStatus(
        Map<String, Object> packageLike,
        String zohoSalesOrderId,
        String salesorderNumber
    ) {
        if (hasExplicitShipmentStatus(packageLike) || hasTransitFields(packageLike)) {
            return;
        }
        if (StringUtils.isBlank(zohoSalesOrderId) && StringUtils.isBlank(salesorderNumber)) {
            return;
        }
        if (!hasShipmentSignal(packageLike)) {
            packageLike.put("status", "delivered");
            packageLike.put("status_message", "delivered");
        }
    }

    private static void copyReferenceNumberAsTrackingIfSafe(Map<String, Object> normalized) {
        if (StringUtils.isNotBlank(ZohoWebhookSupport.stringValue(normalized.get("tracking_number")))) {
            return;
        }
        String reference = ZohoWebhookSupport.stringValue(normalized.get("reference_number"));
        if (reference == null) {
            return;
        }
        String salesorderNumber = ZohoWebhookSupport.stringValue(normalized.get("salesorder_number"));
        String salesorderId = ZohoWebhookSupport.stringValue(normalized.get("salesorder_id"));
        if (
            reference.equalsIgnoreCase(StringUtils.defaultString(salesorderNumber)) ||
            reference.equalsIgnoreCase(StringUtils.defaultString(salesorderId))
        ) {
            return;
        }
        normalized.put("tracking_number", reference);
    }

    private static void copyFirst(Map<String, Object> target, String targetKey, List<Map<String, Object>> sources, String... sourceKeys) {
        for (Map<String, Object> source : sources) {
            for (String sourceKey : sourceKeys) {
                Object value = source.get(sourceKey);
                if (StringUtils.isNotBlank(ZohoWebhookSupport.stringValue(value))) {
                    target.put(targetKey, value);
                    return;
                }
            }
        }
    }
}
