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
import java.util.Optional;
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
        Map<String, Object> data = ZohoWebhookSupport.eventData(payload);
        Map<String, Object> salesOrder = normalizeSalesOrderPayload(data);

        String eventId = eventId(payload, data, salesOrder);
        if (eventId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing eventId");
        }
        String zohoSalesOrderId = ZohoWebhookSupport.firstNonBlank(
            ZohoWebhookSupport.stringValue(salesOrder.get("salesorder_id")),
            ZohoWebhookSupport.stringValue(salesOrder.get("salesorderId")),
            ZohoWebhookSupport.stringValue(data.get("zohoSalesOrderId")),
            ZohoWebhookSupport.stringValue(data.get("zoho_sales_order_id"))
        );
        String salesorderNumber = ZohoWebhookSupport.firstNonBlank(
            ZohoWebhookSupport.stringValue(salesOrder.get("salesorder_number")),
            ZohoWebhookSupport.stringValue(salesOrder.get("salesorderNumber"))
        );
        String referenceNumber = ZohoWebhookSupport.firstNonBlank(
            ZohoWebhookSupport.stringValue(salesOrder.get("reference_number")),
            ZohoWebhookSupport.stringValue(salesOrder.get("referenceNumber"))
        );
        if (zohoSalesOrderId == null && salesorderNumber == null && referenceNumber == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provide salesorder_id, salesorder_number, or reference_number");
        }

        CustomerOrder order = findOrder(zohoSalesOrderId, referenceNumber, salesorderNumber);
        OrderStatus statusBefore = order.getStatus();
        String trackingStatusBefore = OrderTrackingStatusResolver.resolve(order);
        boolean duplicateEvent = webhookEventRepository.existsByEventId(eventId);
        if (duplicateEvent && !shouldReapplyDeliveryUpdate(order, salesOrder)) {
            return ZohoWebhookSupport.duplicateEventResult(eventId);
        }

        ZohoOrderStatusWebhookEvent record = null;
        if (!duplicateEvent) {
            record = new ZohoOrderStatusWebhookEvent();
            record.setEventId(eventId);
            record.setZohoSalesOrderId(zohoSalesOrderId);
            record.setSalesorderNumber(salesorderNumber);
            record.setStatus(ZohoWebhookSupport.stringValue(salesOrder.get("status")));
            record.setReceivedAt(Instant.now());
            try {
                webhookEventRepository.saveAndFlush(record);
            } catch (org.springframework.dao.DataIntegrityViolationException e) {
                if (!shouldReapplyDeliveryUpdate(order, salesOrder)) {
                    return ZohoWebhookSupport.duplicateEventResult(eventId);
                }
            }
        }

        try {
            ZohoOrderStatus.applySalesOrderTracking(order, salesOrder);
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

    private CustomerOrder findOrder(String zohoSalesOrderId, String referenceNumber, String salesorderNumber) {
        if (StringUtils.isNotBlank(zohoSalesOrderId)) {
            return customerOrderRepository
                .findOneByZohoSalesOrderId(zohoSalesOrderId.trim())
                .or(() -> findByStoreOrderNumber(referenceNumber))
                .or(() -> findByStoreOrderNumber(salesorderNumber))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No order for Zoho sales order " + zohoSalesOrderId));
        }
        return findByStoreOrderNumber(referenceNumber)
            .or(() -> findByStoreOrderNumber(salesorderNumber))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No order for " + StringUtils.defaultString(referenceNumber, salesorderNumber)));
    }

    private Optional<CustomerOrder> findByStoreOrderNumber(String orderNumber) {
        String trimmed = StringUtils.trimToNull(orderNumber);
        if (trimmed == null || !trimmed.startsWith("MNM-")) {
            return Optional.empty();
        }
        return customerOrderRepository.findOneByOrderNumber(trimmed);
    }

    private static boolean shouldReapplyDeliveryUpdate(CustomerOrder order, Map<String, Object> salesOrder) {
        if (order.getStatus() == OrderStatus.FULFILLED || order.getStatus() == OrderStatus.CANCELLED) {
            return false;
        }
        return ZohoOrderStatus.indicatesDelivery(salesOrder);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> normalizeSalesOrderPayload(Map<String, Object> data) {
        for (String key : List.of("salesorder", "sales_order")) {
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
            applicationProperties.getZoho().getOrderStatusWebhookSecret(),
            rawBody,
            signature,
            sharedSecret,
            "Order status webhook secret is not configured",
            "Missing order status webhook authentication",
            "Invalid order status webhook signature"
        );
    }

    private static String eventId(Map<String, Object> payload, Map<String, Object> data, Map<String, Object> salesOrder) {
        String explicit = ZohoWebhookSupport.explicitEventIdIncludingLegacyId(payload, data, salesOrder);
        if (explicit != null) {
            return explicit;
        }
        String salesOrderId = ZohoWebhookSupport.firstNonBlank(
            ZohoWebhookSupport.stringValue(salesOrder.get("salesorder_id")),
            ZohoWebhookSupport.stringValue(salesOrder.get("salesorderId"))
        );
        String status = ZohoWebhookSupport.stringValue(salesOrder.get("status"));
        String modified = ZohoWebhookSupport.firstNonBlank(
            ZohoWebhookSupport.stringValue(salesOrder.get("last_modified_time")),
            ZohoWebhookSupport.stringValue(salesOrder.get("updated_time")),
            ZohoWebhookSupport.stringValue(salesOrder.get("created_time"))
        );
        if (salesOrderId == null) {
            return null;
        }
        return "zoho-salesorder:" + salesOrderId + ":" + ZohoWebhookSupport.firstNonBlank(status, "unknown") + ":" + ZohoWebhookSupport.firstNonBlank(modified, "unknown");
    }
}
