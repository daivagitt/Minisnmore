package com.miniandmore.app.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.config.ApplicationProperties;
import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.XpressBeesShipmentWebhookEvent;
import com.miniandmore.app.repository.CustomerOrderRepository;
import com.miniandmore.app.repository.XpressBeesShipmentWebhookEventRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class XpressBeesShipmentWebhookHandlerService {

    private final ApplicationProperties applicationProperties;
    private final XpressBeesShipmentWebhookEventRepository eventRepository;
    private final CustomerOrderRepository orderRepository;
    private final OrderShipmentService orderShipmentService;
    private final ObjectMapper objectMapper;

    public XpressBeesShipmentWebhookHandlerService(
        ApplicationProperties applicationProperties,
        XpressBeesShipmentWebhookEventRepository eventRepository,
        CustomerOrderRepository orderRepository,
        OrderShipmentService orderShipmentService,
        ObjectMapper objectMapper
    ) {
        this.applicationProperties = applicationProperties;
        this.eventRepository = eventRepository;
        this.orderRepository = orderRepository;
        this.orderShipmentService = orderShipmentService;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> handle(String rawBody, String signature, String sharedSecret) {
        verifyAuth(rawBody, signature, sharedSecret);
        Map<String, Object> payload = WebhookPayloadSupport.parseJsonMap(objectMapper, rawBody);
        Map<String, Object> data = ZohoWebhookSupport.mergeNestedPayload(ZohoWebhookSupport.eventData(payload), "shipment", "data");
        String orderNumber = ZohoWebhookSupport.firstNonBlank(
            value(data, "order_number"),
            value(data, "order_no"),
            value(data, "OrderNo"),
            value(data, "reference")
        );
        String awb = ZohoWebhookSupport.firstNonBlank(
            value(data, "awb"),
            value(data, "awb_number"),
            value(data, "AWBNumber"),
            value(data, "AirWayBillNO")
        );
        String providerStatus = ZohoWebhookSupport.firstNonBlank(
            value(data, "status"),
            value(data, "shipment_status"),
            value(data, "event"),
            value(data, "milestone")
        );
        if (providerStatus == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing carrier status");
        }
        CustomerOrder order = findOrder(orderNumber, awb);
        String normalized = normalize(providerStatus);
        String eventId = ZohoWebhookSupport.explicitEventIdIncludingLegacyId(payload, data);
        if (eventId == null) {
            eventId = "xb-shipment-" + order.getOrderNumber() + "-" + normalized + "-" + StringUtils.defaultString(awb);
        }
        if (eventRepository.existsByEventId(eventId)) {
            return ZohoWebhookSupport.duplicateEventResult(eventId);
        }

        XpressBeesShipmentWebhookEvent event = new XpressBeesShipmentWebhookEvent();
        event.setEventId(eventId);
        event.setOrderNumber(order.getOrderNumber());
        event.setAwb(awb);
        event.setProviderStatus(providerStatus);
        event.setReceivedAt(Instant.now());
        try {
            eventRepository.saveAndFlush(event);
        } catch (DataIntegrityViolationException ex) {
            return ZohoWebhookSupport.duplicateEventResult(eventId);
        }

        CustomerOrder saved = orderShipmentService.applyProviderStatus(order, providerStatus, OrderDeliverySupport.deliveredAt(data));
        String localStatus = eventStatus(XpressBeesShipmentStatus.normalize(providerStatus));
        event.setLocalStatus(localStatus);
        eventRepository.save(event);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        result.put("eventId", eventId);
        result.put("order_number", saved.getOrderNumber());
        result.put("provider_status", providerStatus);
        result.put("local_status", localStatus);
        return result;
    }

    private void verifyAuth(String rawBody, String signature, String sharedSecret) {
        ZohoWebhookSupport.verifySecretOrSignature(
            applicationProperties.getXpressbees().getWebhookSecret(),
            rawBody,
            signature,
            sharedSecret,
            "ExpressBees shipment webhook is not configured",
            "Missing ExpressBees webhook authentication",
            "Invalid ExpressBees webhook signature"
        );
    }

    private CustomerOrder findOrder(String orderNumber, String awb) {
        if (StringUtils.isNotBlank(orderNumber)) {
            return orderRepository
                .findOneByOrderNumber(orderNumber.trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        }
        if (StringUtils.isNotBlank(awb)) {
            return orderRepository
                .findFirstByTrackingNumber(awb.trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found for AWB"));
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provide order_number or AWB");
    }

    private static String value(Map<String, Object> data, String key) {
        return ZohoWebhookSupport.stringValue(data.get(key));
    }

    private static String eventStatus(String status) {
        return XpressBeesShipmentStatus.isDelivered(status) ? "delivered" : status;
    }

    private static String normalize(String status) {
        return StringUtils.defaultString(status)
            .trim()
            .toLowerCase(Locale.ROOT)
            .replace('-', '_')
            .replace(' ', '_');
    }
}
