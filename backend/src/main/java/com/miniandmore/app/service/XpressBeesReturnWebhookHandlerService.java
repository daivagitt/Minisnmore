package com.miniandmore.app.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.config.ApplicationProperties;
import com.miniandmore.app.domain.OrderReturn;
import com.miniandmore.app.domain.XpressBeesReturnWebhookEvent;
import com.miniandmore.app.domain.enumeration.OrderRequestType;
import com.miniandmore.app.domain.enumeration.OrderReturnStatus;
import com.miniandmore.app.domain.enumeration.ReturnLogisticsMode;
import com.miniandmore.app.repository.OrderReturnRepository;
import com.miniandmore.app.repository.XpressBeesReturnWebhookEventRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class XpressBeesReturnWebhookHandlerService {

    private static final Logger LOG = LoggerFactory.getLogger(XpressBeesReturnWebhookHandlerService.class);
    private static final String EXPRESS_BEES_PREFIX = "ExpressBees: ";

    private final ApplicationProperties applicationProperties;
    private final XpressBeesReturnWebhookEventRepository webhookEventRepository;
    private final OrderReturnRepository orderReturnRepository;
    private final OrderReturnService orderReturnService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate eventInsertTransaction;

    @Autowired
    public XpressBeesReturnWebhookHandlerService(
        ApplicationProperties applicationProperties,
        XpressBeesReturnWebhookEventRepository webhookEventRepository,
        OrderReturnRepository orderReturnRepository,
        OrderReturnService orderReturnService,
        ObjectMapper objectMapper,
        PlatformTransactionManager transactionManager
    ) {
        this.applicationProperties = applicationProperties;
        this.webhookEventRepository = webhookEventRepository;
        this.orderReturnRepository = orderReturnRepository;
        this.orderReturnService = orderReturnService;
        this.objectMapper = objectMapper;
        this.eventInsertTransaction = new TransactionTemplate(transactionManager);
        this.eventInsertTransaction.setPropagationBehaviorName("PROPAGATION_REQUIRES_NEW");
    }

    XpressBeesReturnWebhookHandlerService(
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
        this.eventInsertTransaction = null;
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
        if (isCompletedWebhookEvent(eventId)) {
            return ZohoWebhookSupport.duplicateEventResult(eventId);
        }
        clearPendingWebhookEvent(eventId);

        XpressBeesReturnWebhookEvent webhookRecord = new XpressBeesReturnWebhookEvent();
        webhookRecord.setEventId(eventId);
        webhookRecord.setReturnNumber(orderReturn.getReturnNumber());
        webhookRecord.setReverseAwb(reverseAwb);
        webhookRecord.setProviderStatus(providerStatus);
        webhookRecord.setReceivedAt(Instant.now());
        if (!insertEvent(webhookRecord)) {
            if (isCompletedWebhookEvent(eventId)) {
                return ZohoWebhookSupport.duplicateEventResult(eventId);
            }
            clearPendingWebhookEvent(eventId);
            if (!insertEvent(webhookRecord)) {
                return ZohoWebhookSupport.duplicateEventResult(eventId);
            }
        }

        String failureReason = ZohoWebhookSupport.firstNonBlank(
            ZohoWebhookSupport.stringValue(data.get("failure_reason")),
            ZohoWebhookSupport.stringValue(data.get("reason")),
            ZohoWebhookSupport.stringValue(data.get("remarks"))
        );
        String localStatus;
        try {
            localStatus = applyCarrierStatus(orderReturn, providerStatus, failureReason, reverseAwb);
        } catch (RuntimeException ex) {
            clearPendingWebhookEvent(eventId);
            throw ex;
        }
        webhookRecord.setLocalStatus(localStatus);
        webhookEventRepository.save(webhookRecord);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        result.put("eventId", eventId);
        result.put("return_number", orderReturn.getReturnNumber());
        result.put("provider_status", providerStatus);
        result.put("local_status", localStatus);
        return result;
    }

    /**
     * The unique-key insert runs in its own transaction. If two carrier callbacks race, only the
     * inner transaction rolls back; the successful pickup-booking request remains committable.
     */
    private boolean insertEvent(XpressBeesReturnWebhookEvent event) {
        try {
            if (eventInsertTransaction == null) {
                webhookEventRepository.saveAndFlush(event);
            } else {
                eventInsertTransaction.executeWithoutResult(status -> webhookEventRepository.saveAndFlush(event));
            }
            return true;
        } catch (DataIntegrityViolationException ex) {
            return false;
        }
    }

    /** Completed events are deduped; pending rows (failed apply) are cleared so the carrier can retry. */
    private boolean isCompletedWebhookEvent(String eventId) {
        return webhookEventRepository
            .findOneByEventId(eventId)
            .map(row -> StringUtils.isNotBlank(row.getLocalStatus()))
            .orElse(false);
    }

    private void clearPendingWebhookEvent(String eventId) {
        if (eventInsertTransaction == null) {
            webhookEventRepository.deletePendingByEventId(eventId);
        } else {
            eventInsertTransaction.executeWithoutResult(status -> webhookEventRepository.deletePendingByEventId(eventId));
        }
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
        return "xb-return-" + orderReturn.getReturnNumber() + "-" + XpressBeesShipmentStatus.normalize(providerStatus) + "-" + StringUtils.defaultString(reverseAwb);
    }

    private String applyCarrierStatus(OrderReturn orderReturn, String providerStatus, String failureReason, String awb) {
        if (isOutboundEvent(orderReturn, awb)) {
            return applyOutboundCarrierStatus(orderReturn, providerStatus);
        }
        return applyReverseCarrierStatus(orderReturn, providerStatus, failureReason, awb);
    }

    private boolean isOutboundEvent(OrderReturn orderReturn, String awb) {
        if (StringUtils.isBlank(orderReturn.getOutboundAwb())) {
            return false;
        }
        if (StringUtils.isBlank(awb)) {
            return false;
        }
        return StringUtils.equalsIgnoreCase(orderReturn.getOutboundAwb(), awb.trim());
    }

    private String applyOutboundCarrierStatus(OrderReturn orderReturn, String providerStatus) {
        String incoming = XpressBeesReturnCarrierStatus.outboundMilestone(providerStatus);
        String preferred = XpressBeesReturnCarrierStatus.preferOutbound(orderReturn.getOutboundCarrierStatus(), incoming);
        if (StringUtils.isNotBlank(preferred)) {
            orderReturn.setOutboundCarrierStatus(preferred);
            orderReturnRepository.save(orderReturn);
        }
        OrderReturnStatus current = orderReturn.getStatus();
        if (incoming == null) {
            LOG.debug("Ignoring unmapped ExpressBees outbound status {} for {}", providerStatus, orderReturn.getReturnNumber());
            return current.name().toLowerCase(Locale.ROOT);
        }
        return switch (incoming) {
            case XpressBeesReturnCarrierStatus.IN_TRANSIT -> {
                if (current == OrderReturnStatus.READY_TO_SHIP) {
                    orderReturnService.markShipped(orderReturn.getReturnNumber(), EXPRESS_BEES_PREFIX + providerStatus);
                }
                yield OrderReturnStatus.SHIPPED.name().toLowerCase(Locale.ROOT);
            }
            case XpressBeesReturnCarrierStatus.OUT_FOR_DELIVERY, XpressBeesReturnCarrierStatus.DELIVERY_FAILED -> {
                if (current == OrderReturnStatus.READY_TO_SHIP) {
                    orderReturnService.markShipped(orderReturn.getReturnNumber(), EXPRESS_BEES_PREFIX + providerStatus);
                }
                // Once-only: prior completed OFD webhooks already notified (mirrors order_event insertIfAbsent).
                if (
                    XpressBeesReturnCarrierStatus.OUT_FOR_DELIVERY.equals(incoming) &&
                    !webhookEventRepository.existsCompletedOutForDeliveryEvent(orderReturn.getReturnNumber())
                ) {
                    orderReturnService.notifyExchangeOutForDelivery(orderReturn.getReturnNumber());
                }
                yield OrderReturnStatus.SHIPPED.name().toLowerCase(Locale.ROOT);
            }
            case XpressBeesReturnCarrierStatus.DELIVERED -> {
                if (current == OrderReturnStatus.READY_TO_SHIP) {
                    orderReturnService.markShipped(orderReturn.getReturnNumber(), EXPRESS_BEES_PREFIX + providerStatus);
                    current = OrderReturnStatus.SHIPPED;
                }
                if (current == OrderReturnStatus.SHIPPED) {
                    orderReturnService.markDelivered(orderReturn.getReturnNumber(), EXPRESS_BEES_PREFIX + providerStatus);
                    current = OrderReturnStatus.DELIVERED;
                }
                if (current == OrderReturnStatus.DELIVERED && orderReturn.getRequestType() == OrderRequestType.EXCHANGE) {
                    orderReturnService.completeExchange(orderReturn.getReturnNumber(), EXPRESS_BEES_PREFIX + providerStatus);
                    yield OrderReturnStatus.COMPLETED.name().toLowerCase(Locale.ROOT);
                }
                yield OrderReturnStatus.DELIVERED.name().toLowerCase(Locale.ROOT);
            }
            default -> current.name().toLowerCase(Locale.ROOT);
        };
    }

    private String applyReverseCarrierStatus(OrderReturn orderReturn, String providerStatus, String failureReason, String awb) {
        ensureReverseShipmentFromWebhook(orderReturn, awb);
        String incoming = XpressBeesReturnCarrierStatus.reverseMilestone(providerStatus);
        String preferred = XpressBeesReturnCarrierStatus.preferReverse(orderReturn.getReverseCarrierStatus(), incoming);
        if (StringUtils.isNotBlank(preferred)) {
            orderReturn.setReverseCarrierStatus(preferred);
            orderReturnRepository.save(orderReturn);
        }
        OrderReturnStatus current = orderReturn.getStatus();
        if (incoming == null) {
            LOG.debug("Ignoring unmapped ExpressBees return status {} for {}", providerStatus, orderReturn.getReturnNumber());
            return current.name().toLowerCase(Locale.ROOT);
        }
        return switch (incoming) {
            case XpressBeesReturnCarrierStatus.DATA_RECEIVED, XpressBeesReturnCarrierStatus.OUT_FOR_PICKUP -> {
                if (current == OrderReturnStatus.APPROVED || current == OrderReturnStatus.PICKUP_FAILED) {
                    orderReturnService.ensurePickupScheduled(orderReturn.getReturnNumber(), EXPRESS_BEES_PREFIX + providerStatus);
                }
                yield OrderReturnStatus.PICKUP_SCHEDULED.name().toLowerCase(Locale.ROOT);
            }
            case XpressBeesReturnCarrierStatus.PICKED_UP, XpressBeesReturnCarrierStatus.IN_TRANSIT -> {
                current = orderReturn.getStatus();
                if (current == OrderReturnStatus.APPROVED || current == OrderReturnStatus.PICKUP_FAILED) {
                    orderReturnService.ensurePickupScheduled(orderReturn.getReturnNumber(), EXPRESS_BEES_PREFIX + providerStatus);
                    current = reloadReturn(orderReturn.getReturnNumber()).getStatus();
                }
                if (current == OrderReturnStatus.PICKUP_REQUESTED || current == OrderReturnStatus.PICKUP_SCHEDULED) {
                    orderReturnService.markPickedUp(orderReturn.getReturnNumber(), EXPRESS_BEES_PREFIX + providerStatus);
                    current = OrderReturnStatus.PICKED_UP;
                }
                yield current.name().toLowerCase(Locale.ROOT);
            }
            case XpressBeesReturnCarrierStatus.DELIVERED_TO_WAREHOUSE -> {
                current = orderReturn.getStatus();
                if (current == OrderReturnStatus.APPROVED || current == OrderReturnStatus.PICKUP_FAILED) {
                    orderReturnService.ensurePickupScheduled(orderReturn.getReturnNumber(), EXPRESS_BEES_PREFIX + providerStatus);
                    current = reloadReturn(orderReturn.getReturnNumber()).getStatus();
                }
                if (
                    orderReturn.getRequestType() == OrderRequestType.EXCHANGE &&
                    (current == OrderReturnStatus.PICKUP_REQUESTED || current == OrderReturnStatus.PICKUP_SCHEDULED)
                ) {
                    orderReturnService.markPickedUp(orderReturn.getReturnNumber(), EXPRESS_BEES_PREFIX + providerStatus);
                    current = OrderReturnStatus.PICKED_UP;
                }
                if (
                    current == OrderReturnStatus.PICKUP_REQUESTED ||
                    current == OrderReturnStatus.PICKUP_SCHEDULED ||
                    current == OrderReturnStatus.PICKED_UP
                ) {
                    orderReturnService.markReceived(orderReturn.getReturnNumber(), EXPRESS_BEES_PREFIX + providerStatus);
                    current = OrderReturnStatus.RECEIVED;
                }
                yield current.name().toLowerCase(Locale.ROOT);
            }
            case XpressBeesReturnCarrierStatus.PICKUP_FAILED -> {
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
            default -> current.name().toLowerCase(Locale.ROOT);
        };
    }

    private void ensureReverseShipmentFromWebhook(OrderReturn orderReturn, String awb) {
        if (orderReturn.getLogisticsMode() == null) {
            orderReturn.setLogisticsMode(ReturnLogisticsMode.XPRESSBEES);
        }
        if (StringUtils.isBlank(orderReturn.getCarrierName())) {
            orderReturn.setCarrierName("XpressBees");
        }
        if (StringUtils.isNotBlank(awb)) {
            String normalizedAwb = awb.trim();
            if (StringUtils.isBlank(orderReturn.getReverseAwb())) {
                orderReturn.setReverseAwb(StringUtils.left(normalizedAwb, 80));
            }
        }
        orderReturnRepository.save(orderReturn);
    }

    private OrderReturn reloadReturn(String returnNumber) {
        return orderReturnRepository
            .findOneByReturnNumber(returnNumber)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Return request not found"));
    }
}
