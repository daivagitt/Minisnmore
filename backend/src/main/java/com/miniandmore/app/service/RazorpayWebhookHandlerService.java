package com.miniandmore.app.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.OrderCancellationRequest;
import com.miniandmore.app.domain.OrderReturn;
import com.miniandmore.app.domain.RazorpayWebhookEvent;
import com.miniandmore.app.domain.enumeration.ExchangeSettlementStatus;
import com.miniandmore.app.domain.enumeration.OrderReturnStatus;
import com.miniandmore.app.domain.enumeration.OrderStatus;
import com.miniandmore.app.domain.enumeration.PaymentStatus;
import com.miniandmore.app.repository.CustomerOrderRepository;
import com.miniandmore.app.repository.OrderCancellationRequestRepository;
import com.miniandmore.app.repository.OrderReturnRepository;
import com.miniandmore.app.repository.RazorpayWebhookEventRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class RazorpayWebhookHandlerService {

    private static final Logger LOG = LoggerFactory.getLogger(RazorpayWebhookHandlerService.class);

    private final ObjectMapper objectMapper;
    private final RazorpayWebhookEventRepository webhookEventRepository;
    private final CustomerOrderRepository customerOrderRepository;
    private final OrderReturnRepository orderReturnRepository;
    private final OrderCancellationRequestRepository cancellationRequestRepository;
    private final OrderEventService orderEventService;
    private final PackageShortShipService packageShortShipService;
    private final String webhookSecret;
    private final TransactionTemplate eventInsertTransaction;

    @Autowired
    public RazorpayWebhookHandlerService(
        ObjectMapper objectMapper,
        RazorpayWebhookEventRepository webhookEventRepository,
        CustomerOrderRepository customerOrderRepository,
        OrderReturnRepository orderReturnRepository,
        OrderCancellationRequestRepository cancellationRequestRepository,
        OrderEventService orderEventService,
        PackageShortShipService packageShortShipService,
        @Value("${razorpay.webhook-secret:}") String webhookSecret,
        PlatformTransactionManager transactionManager
    ) {
        this(
            objectMapper,
            webhookEventRepository,
            customerOrderRepository,
            orderReturnRepository,
            cancellationRequestRepository,
            orderEventService,
            packageShortShipService,
            webhookSecret
        );
        this.eventInsertTransaction.setTransactionManager(transactionManager);
        this.eventInsertTransaction.setPropagationBehaviorName("PROPAGATION_REQUIRES_NEW");
    }

    RazorpayWebhookHandlerService(
        ObjectMapper objectMapper,
        RazorpayWebhookEventRepository webhookEventRepository,
        CustomerOrderRepository customerOrderRepository,
        OrderReturnRepository orderReturnRepository,
        OrderCancellationRequestRepository cancellationRequestRepository,
        OrderEventService orderEventService,
        PackageShortShipService packageShortShipService,
        String webhookSecret
    ) {
        this.objectMapper = objectMapper;
        this.webhookEventRepository = webhookEventRepository;
        this.customerOrderRepository = customerOrderRepository;
        this.orderReturnRepository = orderReturnRepository;
        this.cancellationRequestRepository = cancellationRequestRepository;
        this.orderEventService = orderEventService;
        this.packageShortShipService = packageShortShipService;
        this.webhookSecret = webhookSecret;
        this.eventInsertTransaction = new TransactionTemplate();
    }

    public Map<String, Object> handle(String rawBody, String signature) {
        verifySignature(rawBody, signature);
        JsonNode root = parseBody(rawBody);
        String event = text(root, "event");
        if (StringUtils.isBlank(event)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing Razorpay event type");
        }

        JsonNode refundEntity = root.path("payload").path("refund").path("entity");
        JsonNode paymentEntity = root.path("payload").path("payment").path("entity");
        String refundId = firstNonBlank(text(refundEntity, "id"), text(refundEntity, "refund_id"));
        String paymentId = firstNonBlank(text(refundEntity, "payment_id"), text(paymentEntity, "id"));
        String razorpayOrderId = firstNonBlank(text(paymentEntity, "order_id"), text(refundEntity, "order_id"));
        String orderNumber = firstNonBlank(
            text(refundEntity.path("notes"), "order_number"),
            text(paymentEntity.path("notes"), "order_number")
        );
        String eventId = firstNonBlank(text(root, "id"), event + ":" + firstNonBlank(refundId, paymentId, razorpayOrderId, orderNumber));
        if (StringUtils.isBlank(eventId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing Razorpay event id");
        }
        eventId = StringUtils.left(eventId, 160);

        if (webhookEventRepository.existsByEventId(eventId)) {
            return result(event, eventId, null, refundId, true, false, "duplicate");
        }

        boolean refundProcessed = isRefundProcessed(event);
        Optional<CustomerOrder> matched = resolveOrder(refundId, paymentId, razorpayOrderId, orderNumber);
        if (matched.isEmpty()) {
            LOG.info(
                "Razorpay webhook {} did not match a local order (refundId={}, paymentId={}, razorpayOrderId={}, orderNumber={})",
                event,
                refundId,
                paymentId,
                razorpayOrderId,
                orderNumber
            );
            return result(event, eventId, null, refundId, false, false, "unmatched");
        }

        CustomerOrder order = matched.orElseThrow();
        String matchedOrderNumber = order.getOrderNumber();
        RazorpayWebhookEvent record = new RazorpayWebhookEvent();
        record.setEventId(eventId);
        record.setEventType(event);
        record.setOrderNumber(matchedOrderNumber);
        record.setRefundId(refundId);
        record.setLocalStatus(event);
        record.setReceivedAt(Instant.now());
        if (!insertEvent(record)) {
            return result(event, eventId, matchedOrderNumber, refundId, true, false, "duplicate");
        }

        boolean updated = false;
        String outcome = "ignored";
        if (refundProcessed) {
            updated = markRefunded(order, refundEntity, refundId);
            if (updated) {
                customerOrderRepository.save(order);
                orderEventService.syncLifecycleEvents(order);
                outcome = "refunded";
            }
        } else if (isRefundFailed(event)) {
            updated = markRefundFailed(order, refundEntity, refundId);
            if (updated) {
                customerOrderRepository.save(order);
                orderEventService.syncLifecycleEvents(order);
                outcome = "refund_failed";
            }
        }

        record.setLocalStatus(order.getPaymentStatus() != null ? order.getPaymentStatus().name().toLowerCase() : event);
        webhookEventRepository.save(record);

        return result(event, eventId, matchedOrderNumber, refundId, false, updated, outcome);
    }

    private boolean insertEvent(RazorpayWebhookEvent event) {
        try {
            if (eventInsertTransaction.getTransactionManager() == null) {
                webhookEventRepository.saveAndFlush(event);
            } else {
                eventInsertTransaction.executeWithoutResult(status -> webhookEventRepository.saveAndFlush(event));
            }
            return true;
        } catch (DataIntegrityViolationException ex) {
            return false;
        }
    }

    private boolean markRefunded(CustomerOrder order, JsonNode refundEntity, String refundId) {
        boolean caseUpdated = applyProcessedRefundToCases(refundId);
        PaymentStatus current = order.getPaymentStatus();
        if (
            current != PaymentStatus.REFUNDED &&
            current != PaymentStatus.REFUND_INITIATED &&
            current != PaymentStatus.PARTIALLY_REFUNDED &&
            current != PaymentStatus.PAID &&
            current != PaymentStatus.REFUND_FAILED
        ) {
            return caseUpdated;
        }
        PaymentStatus next = nextPaymentStatus(order, refundEntity, refundId);
        if (next == current) {
            if (caseUpdated) {
                maybeNotifyPackageShortRefundProcessed(order, refundEntity, refundId);
            }
            return caseUpdated;
        }
        order.setPaymentStatus(next);
        maybeNotifyPackageShortRefundProcessed(order, refundEntity, refundId);
        return true;
    }

    private void maybeNotifyPackageShortRefundProcessed(CustomerOrder order, JsonNode refundEntity, String refundId) {
        if (order == null || StringUtils.isBlank(refundId)) {
            return;
        }
        BigDecimal amount = refundAmount(refundEntity);
        if (refundId.equals(order.getPackageShortRazorpayRefundId())) {
            packageShortShipService.notifyPackageShortRefundProcessed(order, amount, refundId);
            return;
        }
        orderReturnRepository.findFirstByPackageShortRazorpayRefundIdOrderByIdDesc(refundId).ifPresent(orderReturn -> {
            CustomerOrder linked = orderReturn.getOrder();
            if (linked != null) {
                packageShortShipService.notifyPackageShortRefundProcessed(linked, amount, refundId);
            }
        });
    }

    private boolean applyProcessedRefundToCases(String refundId) {
        if (StringUtils.isBlank(refundId)) {
            return false;
        }
        boolean updated = false;
        Optional<OrderReturn> byReturnRefund = orderReturnRepository.findFirstByRazorpayRefundIdOrderByIdDesc(refundId);
        if (byReturnRefund.isPresent()) {
            OrderReturn orderReturn = byReturnRefund.orElseThrow();
            if (orderReturn.getStatus() == OrderReturnStatus.FAILED) {
                orderReturn.setStatus(OrderReturnStatus.REFUNDED);
                orderReturnRepository.save(orderReturn);
                updated = true;
            }
        }
        Optional<OrderReturn> bySettlementRefund = orderReturnRepository.findFirstBySettlementReferenceOrderByIdDesc(refundId);
        if (bySettlementRefund.isPresent()) {
            OrderReturn orderReturn = bySettlementRefund.orElseThrow();
            if (orderReturn.getSettlementStatus() == ExchangeSettlementStatus.PENDING) {
                orderReturn.setSettlementStatus(ExchangeSettlementStatus.REFUNDED);
                orderReturn.setSettledAt(Instant.now());
                orderReturnRepository.save(orderReturn);
                updated = true;
            }
        }
        return updated;
    }

    private PaymentStatus nextPaymentStatus(CustomerOrder order, JsonNode refundEntity, String refundId) {
        if (order.getStatus() == OrderStatus.CANCELLED) {
            return PaymentStatus.REFUNDED;
        }
        BigDecimal settled = settledRefundAmount(order, refundEntity, refundId);
        if (settled.signum() <= 0) {
            return order.getPaymentStatus();
        }
        BigDecimal orderTotal = money(order.getTotal());
        if (orderTotal.signum() > 0 && settled.compareTo(orderTotal) < 0) {
            return PaymentStatus.PARTIALLY_REFUNDED;
        }
        return PaymentStatus.REFUNDED;
    }

    private BigDecimal settledRefundAmount(CustomerOrder order, JsonNode refundEntity, String refundId) {
        BigDecimal thisRefund = refundAmount(refundEntity);
        if (order.getId() == null) {
            return thisRefund;
        }
        var returns = orderReturnRepository.findAllByOrder_IdOrderByRequestedAtAsc(order.getId());
        BigDecimal booked = ExchangeSettlementAmounts.prepaidRefundsAlreadyIssued(order, returns);
        if (ExchangeSettlementAmounts.isRefundBookedOnOrder(order, returns, refundId)) {
            return booked.max(thisRefund);
        }
        if (booked.signum() <= 0) {
            return thisRefund;
        }
        return booked.add(thisRefund);
    }

    private Optional<CustomerOrder> resolveOrder(String refundId, String paymentId, String razorpayOrderId, String orderNumber) {
        if (StringUtils.isNotBlank(refundId)) {
            Optional<OrderReturn> byReturnRefund = orderReturnRepository.findFirstByRazorpayRefundIdOrderByIdDesc(refundId);
            if (byReturnRefund.isPresent() && byReturnRefund.orElseThrow().getOrder() != null) {
                return Optional.of(byReturnRefund.orElseThrow().getOrder());
            }
            Optional<OrderReturn> bySettlementRefund = orderReturnRepository.findFirstBySettlementReferenceOrderByIdDesc(refundId);
            if (bySettlementRefund.isPresent() && bySettlementRefund.orElseThrow().getOrder() != null) {
                return Optional.of(bySettlementRefund.orElseThrow().getOrder());
            }
            Optional<OrderCancellationRequest> byCancelRefund = cancellationRequestRepository.findFirstByRazorpayRefundIdOrderByIdDesc(
                refundId
            );
            if (byCancelRefund.isPresent() && byCancelRefund.orElseThrow().getOrder() != null) {
                return Optional.of(byCancelRefund.orElseThrow().getOrder());
            }
            Optional<OrderReturn> byPackageShortReturn = orderReturnRepository.findFirstByPackageShortRazorpayRefundIdOrderByIdDesc(
                refundId
            );
            if (byPackageShortReturn.isPresent() && byPackageShortReturn.orElseThrow().getOrder() != null) {
                return Optional.of(byPackageShortReturn.orElseThrow().getOrder());
            }
            Optional<CustomerOrder> byPackageShortOrder = customerOrderRepository.findFirstByPackageShortRazorpayRefundIdOrderByIdDesc(
                refundId
            );
            if (byPackageShortOrder.isPresent()) {
                return byPackageShortOrder;
            }
        }
        if (StringUtils.isNotBlank(paymentId)) {
            Optional<CustomerOrder> byPayment = customerOrderRepository.findFirstByRazorpayPaymentId(paymentId);
            if (byPayment.isPresent()) {
                return byPayment;
            }
        }
        if (StringUtils.isNotBlank(razorpayOrderId)) {
            Optional<CustomerOrder> byRazorpayOrder = customerOrderRepository.findFirstByRazorpayOrderId(razorpayOrderId);
            if (byRazorpayOrder.isPresent()) {
                return byRazorpayOrder;
            }
        }
        if (StringUtils.isNotBlank(orderNumber)) {
            return customerOrderRepository.findOneByOrderNumber(orderNumber);
        }
        return Optional.empty();
    }

    private boolean markRefundFailed(CustomerOrder order, JsonNode refundEntity, String refundId) {
        PaymentStatus current = order.getPaymentStatus();
        if (current == PaymentStatus.FAILED || current == PaymentStatus.PENDING) {
            return false;
        }
        String reason = refundFailureReason(refundEntity, refundId);
        boolean caseUpdated = applyFailedRefundToCases(order, refundId, reason);
        if (!caseUpdated) {
            return false;
        }
        PaymentStatus next = paymentStatusAfterFailedRefund(order);
        if (next != current) {
            order.setPaymentStatus(next);
        }
        return true;
    }

    private boolean applyFailedRefundToCases(CustomerOrder order, String refundId, String reason) {
        boolean updated = false;
        if (StringUtils.isBlank(refundId)) {
            return false;
        }
        Optional<OrderReturn> byReturnRefund = orderReturnRepository.findFirstByRazorpayRefundIdOrderByIdDesc(refundId);
        if (byReturnRefund.isPresent()) {
            updated = markReturnRefundFailed(byReturnRefund.orElseThrow(), refundId, reason) || updated;
        }
        Optional<OrderReturn> bySettlementRefund = orderReturnRepository.findFirstBySettlementReferenceOrderByIdDesc(refundId);
        if (bySettlementRefund.isPresent()) {
            updated = markSettlementRefundFailed(bySettlementRefund.orElseThrow(), refundId, reason) || updated;
        }
        Optional<OrderCancellationRequest> byCancelRefund = cancellationRequestRepository.findFirstByRazorpayRefundIdOrderByIdDesc(
            refundId
        );
        if (byCancelRefund.isPresent()) {
            updated = markCancellationRefundFailed(byCancelRefund.orElseThrow(), refundId, reason) || updated;
        }
        Optional<OrderReturn> byPackageShortReturn = orderReturnRepository.findFirstByPackageShortRazorpayRefundIdOrderByIdDesc(
            refundId
        );
        if (byPackageShortReturn.isPresent()) {
            updated =
                markPackageShortRefundFailedOnReturn(byPackageShortReturn.orElseThrow(), refundId, reason) || updated;
        }
        if (order != null && refundId.equals(order.getPackageShortRazorpayRefundId())) {
            updated = markPackageShortRefundFailedOnOrder(order, refundId, reason) || updated;
        }
        return updated;
    }

    private boolean markPackageShortRefundFailedOnOrder(CustomerOrder order, String refundId, String reason) {
        if (order == null || !refundId.equals(order.getPackageShortRazorpayRefundId())) {
            return false;
        }
        order.setPackageShortRefundAmount(null);
        order.setPackageShortRazorpayRefundId(null);
        order.setNotes(mergeNotes(order.getNotes(), reason + " (" + refundId + ")"));
        return true;
    }

    private boolean markPackageShortRefundFailedOnReturn(OrderReturn orderReturn, String refundId, String reason) {
        if (!refundId.equals(orderReturn.getPackageShortRazorpayRefundId())) {
            return false;
        }
        orderReturn.setPackageShortRefundAmount(null);
        orderReturn.setPackageShortRazorpayRefundId(null);
        orderReturn.setAdminNotes(mergeNotes(orderReturn.getAdminNotes(), reason + " (" + refundId + ")"));
        orderReturnRepository.save(orderReturn);
        return true;
    }

    private boolean markReturnRefundFailed(OrderReturn orderReturn, String refundId, String reason) {
        if (orderReturn.getStatus() != OrderReturnStatus.REFUNDED && orderReturn.getStatus() != OrderReturnStatus.FAILED) {
            return false;
        }
        orderReturn.setStatus(OrderReturnStatus.FAILED);
        orderReturn.setAdminNotes(mergeNotes(orderReturn.getAdminNotes(), reason + " (" + refundId + ")"));
        orderReturnRepository.save(orderReturn);
        return true;
    }

    private boolean markSettlementRefundFailed(OrderReturn orderReturn, String refundId, String reason) {
        if (orderReturn.getSettlementStatus() != ExchangeSettlementStatus.REFUNDED) {
            return false;
        }
        orderReturn.setSettlementStatus(ExchangeSettlementStatus.PENDING);
        orderReturn.setSettledAt(null);
        orderReturn.setSettledBy(null);
        orderReturn.setAdminNotes(mergeNotes(orderReturn.getAdminNotes(), reason + " (" + refundId + ")"));
        orderReturnRepository.save(orderReturn);
        return true;
    }

    private boolean markCancellationRefundFailed(OrderCancellationRequest request, String refundId, String reason) {
        request.setAdminNotes(mergeNotes(request.getAdminNotes(), reason + " (" + refundId + ")"));
        cancellationRequestRepository.save(request);
        return true;
    }

    private PaymentStatus paymentStatusAfterFailedRefund(CustomerOrder order) {
        if (order.getId() == null) {
            return PaymentStatus.REFUND_FAILED;
        }
        var returns = orderReturnRepository.findAllByOrder_IdOrderByRequestedAtAsc(order.getId());
        BigDecimal remaining = ExchangeSettlementAmounts.prepaidRefundsAlreadyIssued(order, returns);
        BigDecimal orderTotal = money(order.getTotal());
        if (orderTotal.signum() > 0 && remaining.compareTo(orderTotal) >= 0) {
            return PaymentStatus.REFUNDED;
        }
        if (remaining.signum() > 0) {
            return PaymentStatus.PARTIALLY_REFUNDED;
        }
        return PaymentStatus.REFUND_FAILED;
    }

    private static String refundFailureReason(JsonNode refundEntity, String refundId) {
        String detail = firstNonBlank(
            text(refundEntity, "error_description"),
            text(refundEntity.path("error"), "description"),
            text(refundEntity.path("notes"), "error")
        );
        if (StringUtils.isNotBlank(detail)) {
            return "Razorpay refund failed: " + StringUtils.left(detail, 400);
        }
        return "Razorpay refund failed";
    }

    private static String mergeNotes(String existing, String addition) {
        String left = StringUtils.trimToEmpty(existing);
        String right = StringUtils.trimToEmpty(addition);
        if (left.isEmpty()) {
            return StringUtils.left(right, 2000);
        }
        if (right.isEmpty()) {
            return StringUtils.left(left, 2000);
        }
        return StringUtils.left(left + "\n" + right, 2000);
    }

    private static boolean isRefundProcessed(String event) {
        return "refund.processed".equals(event) || "payment.refunded".equals(event);
    }

    private static boolean isRefundFailed(String event) {
        return "refund.failed".equals(event);
    }

    private void verifySignature(String rawBody, String signature) {
        if (StringUtils.isBlank(webhookSecret)) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Razorpay webhook secret is not configured");
        }
        if (StringUtils.isBlank(signature) || !WebhookHmacSupport.signaturesMatch(webhookSecret, rawBody, signature)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Razorpay webhook signature");
        }
    }

    private JsonNode parseBody(String rawBody) {
        try {
            return objectMapper.readTree(StringUtils.defaultString(rawBody));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Razorpay webhook payload");
        }
    }

    private static BigDecimal refundAmount(JsonNode refundEntity) {
        if (refundEntity == null || refundEntity.isMissingNode() || refundEntity.isNull()) {
            return BigDecimal.ZERO;
        }
        long paise = refundEntity.path("amount").asLong(-1);
        if (paise >= 0) {
            return BigDecimal.valueOf(paise).movePointLeft(2).setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO;
    }

    private static BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value.setScale(2, RoundingMode.HALF_UP);
    }

    private static String text(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        JsonNode child = node.path(field);
        if (child.isMissingNode() || child.isNull() || !child.isValueNode()) {
            return null;
        }
        return StringUtils.trimToNull(child.asText());
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.isNotBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private static Map<String, Object> result(
        String event,
        String eventId,
        String orderNumber,
        String refundId,
        boolean duplicate,
        boolean updated,
        String outcome
    ) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ok", true);
        data.put("event", event);
        data.put("event_id", eventId);
        data.put("order_number", orderNumber);
        data.put("refund_id", refundId);
        data.put("duplicate", duplicate);
        data.put("updated", updated);
        data.put("outcome", outcome);
        return data;
    }
}
