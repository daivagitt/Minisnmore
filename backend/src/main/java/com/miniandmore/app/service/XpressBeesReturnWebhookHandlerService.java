package com.miniandmore.app.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.config.ApplicationProperties;
import com.miniandmore.app.domain.OrderReturn;
import com.miniandmore.app.domain.XpressBeesReturnWebhookEvent;
import com.miniandmore.app.domain.enumeration.OrderRequestType;
import com.miniandmore.app.domain.enumeration.OrderReturnStatus;
import com.miniandmore.app.repository.OrderReturnRepository;
import com.miniandmore.app.repository.XpressBeesReturnWebhookEventRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
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
public class XpressBeesReturnWebhookHandlerService {

    private static final Logger LOG = LoggerFactory.getLogger(XpressBeesReturnWebhookHandlerService.class);

    private final ApplicationProperties applicationProperties;
    private final XpressBeesReturnWebhookEventRepository webhookEventRepository;
    private final OrderReturnRepository orderReturnRepository;
    private final OrderReturnService orderReturnService;
    private final ObjectMapper objectMapper;

    public XpressBeesReturnWebhookHandlerService(
        ApplicationProperties applicationProperties,
        XpressBeesReturnWebhookEventRepository webhookEventRepository,
        OrderReturnRepository orderReturnRepository,
        OrderReturnService orderReturnService,
        ObjectMapper objectMapper
    ) {
        this.applicationProperties = applicationProperties;
        this.webhookEventRepository = webhookEventRepository;
        this.orderReturnRepository = orderReturnRepository;
        this.orderReturnService = orderReturnService;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> handle(String rawBody, String signature, String sharedSecret) {
        verifyAuth(rawBody, signature, sharedSecret);
        Map<String, Object> payload = WebhookPayloadSupport.parseJsonMap(objectMapper, rawBody);
        Map<String, Object> data = ZohoWebhookSupport.mergeNestedPayload(ZohoWebhookSupport.eventData(payload), "shipment", "data");

        String returnNumber = ZohoWebhookSupport.firstNonBlank(
            ZohoWebhookSupport.stringValue(data.get("return_number")),
            ZohoWebhookSupport.stringValue(data.get("returnNumber")),
            ZohoWebhookSupport.stringValue(data.get("order_no")),
            ZohoWebhookSupport.stringValue(data.get("orderNo")),
            ZohoWebhookSupport.stringValue(data.get("OrderNo")),
            ZohoWebhookSupport.stringValue(data.get("case_reference")),
            ZohoWebhookSupport.stringValue(data.get("reference"))
        );
        String reverseAwb = ZohoWebhookSupport.firstNonBlank(
            ZohoWebhookSupport.stringValue(data.get("reverse_awb")),
            ZohoWebhookSupport.stringValue(data.get("awb")),
            ZohoWebhookSupport.stringValue(data.get("awb_number")),
            ZohoWebhookSupport.stringValue(data.get("AWBNumber")),
            ZohoWebhookSupport.stringValue(data.get("AirWayBillNO"))
        );
        String providerStatus = ZohoWebhookSupport.firstNonBlank(
            ZohoWebhookSupport.stringValue(data.get("status")),
            ZohoWebhookSupport.stringValue(data.get("shipment_status")),
            ZohoWebhookSupport.stringValue(data.get("event")),
            ZohoWebhookSupport.stringValue(data.get("milestone"))
        );
        if (providerStatus == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing carrier status");
        }

        OrderReturn orderReturn = findReturn(returnNumber, reverseAwb);
        String eventId = resolveEventId(payload, data, orderReturn, providerStatus, reverseAwb);
        if (webhookEventRepository.existsByEventId(eventId)) {
            return ZohoWebhookSupport.duplicateEventResult(eventId);
        }

        XpressBeesReturnWebhookEvent record = new XpressBeesReturnWebhookEvent();
        record.setEventId(eventId);
        record.setReturnNumber(orderReturn.getReturnNumber());
        record.setReverseAwb(reverseAwb);
        record.setProviderStatus(providerStatus);
        record.setReceivedAt(Instant.now());
        try {
            webhookEventRepository.saveAndFlush(record);
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            return ZohoWebhookSupport.duplicateEventResult(eventId);
        }

        String failureReason = ZohoWebhookSupport.firstNonBlank(
            ZohoWebhookSupport.stringValue(data.get("failure_reason")),
            ZohoWebhookSupport.stringValue(data.get("reason")),
            ZohoWebhookSupport.stringValue(data.get("remarks"))
        );
        String localStatus = applyCarrierStatus(orderReturn, providerStatus, failureReason, reverseAwb);
        record.setLocalStatus(localStatus);
        webhookEventRepository.save(record);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        result.put("eventId", eventId);
        result.put("return_number", orderReturn.getReturnNumber());
        result.put("provider_status", providerStatus);
        result.put("local_status", localStatus);
        return result;
    }

    private void verifyAuth(String rawBody, String signature, String sharedSecret) {
        String configured = applicationProperties.getXpressbees().getWebhookSecret();
        if (StringUtils.isBlank(configured)) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "ExpressBees return webhook is not configured");
        }
        ZohoWebhookSupport.verifySecretOrSignature(
            configured,
            rawBody,
            signature,
            sharedSecret,
            "ExpressBees return webhook is not configured",
            "Missing ExpressBees webhook authentication",
            "Invalid ExpressBees webhook signature"
        );
    }

    private OrderReturn findReturn(String returnNumber, String awb) {
        if (StringUtils.isNotBlank(returnNumber)) {
            return orderReturnRepository
                .findOneByReturnNumber(returnNumber.trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Return request not found"));
        }
        if (StringUtils.isNotBlank(awb)) {
            String normalizedAwb = awb.trim();
            Optional<OrderReturn> byReverse = orderReturnRepository.findFirstByReverseAwbOrderByRequestedAtDesc(normalizedAwb);
            if (byReverse.isPresent()) {
                return byReverse.orElseThrow();
            }
            return orderReturnRepository
                .findFirstByOutboundAwbOrderByRequestedAtDesc(normalizedAwb)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Return request not found for AWB"));
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provide return_number or AWB");
    }

    private String resolveEventId(
        Map<String, Object> payload,
        Map<String, Object> data,
        OrderReturn orderReturn,
        String providerStatus,
        String reverseAwb
    ) {
        String explicit = ZohoWebhookSupport.explicitEventIdIncludingLegacyId(payload, data);
        if (explicit != null) {
            return explicit;
        }
        return "xb-return-" + orderReturn.getReturnNumber() + "-" + normalizeStatus(providerStatus) + "-" + StringUtils.defaultString(reverseAwb);
    }

    private String applyCarrierStatus(OrderReturn orderReturn, String providerStatus, String failureReason, String awb) {
        if (isOutboundEvent(orderReturn, awb)) {
            return applyOutboundCarrierStatus(orderReturn, providerStatus);
        }
        return applyReverseCarrierStatus(orderReturn, providerStatus, failureReason);
    }

    private boolean isOutboundEvent(OrderReturn orderReturn, String awb) {
        if (StringUtils.isBlank(orderReturn.getOutboundAwb())) {
            return false;
        }
        if (StringUtils.isBlank(awb)) {
            return orderReturn.getStatus() == OrderReturnStatus.READY_TO_SHIP || orderReturn.getStatus() == OrderReturnStatus.SHIPPED;
        }
        return StringUtils.equalsIgnoreCase(orderReturn.getOutboundAwb(), awb.trim());
    }

    private String applyOutboundCarrierStatus(OrderReturn orderReturn, String providerStatus) {
        String normalized = normalizeStatus(providerStatus);
        OrderReturnStatus current = orderReturn.getStatus();
        return switch (normalized) {
            case "shipped", "in_transit", "dispatched", "picked", "manifested" -> {
                if (current == OrderReturnStatus.READY_TO_SHIP) {
                    orderReturnService.markShipped(orderReturn.getReturnNumber(), "ExpressBees: " + providerStatus);
                }
                yield OrderReturnStatus.SHIPPED.name().toLowerCase(Locale.ROOT);
            }
            case "out_for_delivery", "ofd" -> {
                if (current == OrderReturnStatus.READY_TO_SHIP) {
                    orderReturnService.markShipped(orderReturn.getReturnNumber(), "ExpressBees: " + providerStatus);
                }
                // Once-only: prior completed OFD webhooks already notified (mirrors order_event insertIfAbsent).
                if (!webhookEventRepository.existsCompletedOutForDeliveryEvent(orderReturn.getReturnNumber())) {
                    orderReturnService.notifyExchangeOutForDelivery(orderReturn.getReturnNumber());
                }
                yield OrderReturnStatus.SHIPPED.name().toLowerCase(Locale.ROOT);
            }
            case "delivered", "delivery_done", "delivered_to_customer" -> {
                if (current == OrderReturnStatus.READY_TO_SHIP) {
                    orderReturnService.markShipped(orderReturn.getReturnNumber(), "ExpressBees: " + providerStatus);
                    current = OrderReturnStatus.SHIPPED;
                }
                if (current == OrderReturnStatus.SHIPPED) {
                    orderReturnService.markDelivered(orderReturn.getReturnNumber(), "ExpressBees: " + providerStatus);
                    current = OrderReturnStatus.DELIVERED;
                }
                if (current == OrderReturnStatus.DELIVERED && orderReturn.getRequestType() == OrderRequestType.EXCHANGE) {
                    orderReturnService.completeExchange(orderReturn.getReturnNumber(), "ExpressBees: " + providerStatus);
                    yield OrderReturnStatus.COMPLETED.name().toLowerCase(Locale.ROOT);
                }
                yield OrderReturnStatus.DELIVERED.name().toLowerCase(Locale.ROOT);
            }
            default -> {
                LOG.debug("Ignoring unmapped ExpressBees outbound status {} for {}", providerStatus, orderReturn.getReturnNumber());
                yield current.name().toLowerCase(Locale.ROOT);
            }
        };
    }

    private String applyReverseCarrierStatus(OrderReturn orderReturn, String providerStatus, String failureReason) {
        String normalized = normalizeStatus(providerStatus);
        OrderReturnStatus current = orderReturn.getStatus();
        return switch (normalized) {
            case "pickup_scheduled", "out_for_pickup", "scheduled" -> {
                if (current == OrderReturnStatus.APPROVED || current == OrderReturnStatus.PICKUP_FAILED) {
                    orderReturnService.ensurePickupScheduled(orderReturn.getReturnNumber(), "ExpressBees: " + providerStatus);
                }
                yield OrderReturnStatus.PICKUP_SCHEDULED.name().toLowerCase(Locale.ROOT);
            }
            case "picked_up", "pickdone", "pick_done" -> {
                if (
                    current == OrderReturnStatus.APPROVED ||
                    current == OrderReturnStatus.PICKUP_REQUESTED ||
                    current == OrderReturnStatus.PICKUP_SCHEDULED
                ) {
                    orderReturnService.markPickedUp(orderReturn.getReturnNumber(), "ExpressBees: " + providerStatus);
                }
                yield OrderReturnStatus.PICKED_UP.name().toLowerCase(Locale.ROOT);
            }
            case "received", "rto_delivered", "warehouse_received" -> {
                if (
                    current == OrderReturnStatus.APPROVED ||
                    current == OrderReturnStatus.PICKED_UP ||
                    current == OrderReturnStatus.PICKUP_SCHEDULED ||
                    current == OrderReturnStatus.PICKUP_REQUESTED
                ) {
                    orderReturnService.markReceived(orderReturn.getReturnNumber(), "ExpressBees: " + providerStatus);
                }
                yield OrderReturnStatus.RECEIVED.name().toLowerCase(Locale.ROOT);
            }
            case "pickup_failed", "failed", "cancelled", "rpcancel", "customer_cancelled", "customer_na", "no_product" -> {
                if (
                    current == OrderReturnStatus.PICKUP_SCHEDULED ||
                    current == OrderReturnStatus.PICKUP_REQUESTED ||
                    current == OrderReturnStatus.APPROVED
                ) {
                    orderReturnService.markPickupFailed(
                        orderReturn.getReturnNumber(),
                        StringUtils.defaultIfBlank(failureReason, providerStatus),
                        "ExpressBees webhook"
                    );
                }
                yield OrderReturnStatus.PICKUP_FAILED.name().toLowerCase(Locale.ROOT);
            }
            default -> {
                LOG.debug("Ignoring unmapped ExpressBees return status {} for {}", providerStatus, orderReturn.getReturnNumber());
                yield current.name().toLowerCase(Locale.ROOT);
            }
        };
    }

    private static String normalizeStatus(String status) {
        return StringUtils.defaultString(status)
            .trim()
            .toLowerCase(Locale.ROOT)
            .replace('-', '_')
            .replace(' ', '_');
    }
}
