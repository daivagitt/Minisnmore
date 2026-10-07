package com.miniandmore.app.service;

import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.OrderCancellationRequest;
import com.miniandmore.app.domain.enumeration.CancellationRequestStatus;
import com.miniandmore.app.domain.enumeration.ManualRefundMethod;
import com.miniandmore.app.domain.enumeration.OrderStatus;
import com.miniandmore.app.domain.enumeration.PaymentStatus;
import com.miniandmore.app.domain.enumeration.ReturnLogisticsMode;
import com.miniandmore.app.repository.CustomerOrderRepository;
import com.miniandmore.app.repository.OrderCancellationRequestRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class OrderCancellationService {

    private static final Logger LOG = LoggerFactory.getLogger(OrderCancellationService.class);
    private static final int ADMIN_CANCEL_REASON_MIN_LENGTH = 5;

    private final OrderCancellationRequestRepository cancellationRequestRepository;
    private final CustomerOrderRepository customerOrderRepository;
    private final OrderCancellationExecutor cancellationExecutor;
    private final OrderMailService orderMailService;
    private final OrderSmsService orderSmsService;
    private final OrderShipmentService orderShipmentService;

    @Autowired
    public OrderCancellationService(
        OrderCancellationRequestRepository cancellationRequestRepository,
        CustomerOrderRepository customerOrderRepository,
        OrderCancellationExecutor cancellationExecutor,
        @Autowired(required = false) OrderMailService orderMailService,
        @Autowired(required = false) OrderSmsService orderSmsService,
        @Lazy @Autowired(required = false) OrderShipmentService orderShipmentService
    ) {
        this.cancellationRequestRepository = cancellationRequestRepository;
        this.customerOrderRepository = customerOrderRepository;
        this.cancellationExecutor = cancellationExecutor;
        this.orderMailService = orderMailService;
        this.orderSmsService = orderSmsService;
        this.orderShipmentService = orderShipmentService;
    }

    public OrderCancellationRequest submitRequest(String userLogin, String orderNumber, String reason) {
        String normalizedReason = normalizeReason(reason);
        CustomerOrder order = customerOrderRepository
            .findOneByOrderNumberAndUserLogin(orderNumber, userLogin)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        cancellationExecutor.assertCancelEligible(order);
        if (isAfterHandWrap(order)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Please contact Customer Care");
        }
        if (cancellationRequestRepository.existsByOrderIdAndStatus(order.getId(), CancellationRequestStatus.REQUESTED)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A cancellation request is already pending for this order");
        }
        OrderCancellationRequest request = new OrderCancellationRequest();
        request.setRequestNumber(generateRequestNumber(order.getOrderNumber()));
        request.setStatus(CancellationRequestStatus.REQUESTED);
        request.setReason(normalizedReason);
        request.setRequestedAt(Instant.now());
        request.setOrder(order);
        request.setUserLogin(userLogin);
        OrderCancellationRequest saved = cancellationRequestRepository.save(request);
        if (orderMailService != null) {
            AfterCommit.run(() -> {
                try {
                    orderMailService.sendCancellationRequested(order, saved);
                } catch (RuntimeException ex) {
                    LOG.warn("Cancellation requested email failed for {}", saved.getRequestNumber(), ex);
                }
            });
        }
        return saved;
    }

    public OrderCancellationRequest getOpenForUser(String userLogin, String orderNumber) {
        CustomerOrder order = requireOwnedOrder(userLogin, orderNumber);
        return cancellationRequestRepository
            .findFirstByOrderIdAndStatusOrderByRequestedAtDesc(order.getId(), CancellationRequestStatus.REQUESTED)
            .orElse(null);
    }

    /** Pending request if one exists; otherwise the most recent declined cancellation request. */
    public OrderCancellationRequest getVisibleForUser(String userLogin, String orderNumber) {
        CustomerOrder order = requireOwnedOrder(userLogin, orderNumber);
        return cancellationRequestRepository
            .findFirstByOrderIdAndStatusOrderByRequestedAtDesc(order.getId(), CancellationRequestStatus.REQUESTED)
            .or(() ->
                cancellationRequestRepository.findFirstByOrderIdAndStatusOrderByRequestedAtDesc(
                    order.getId(),
                    CancellationRequestStatus.REJECTED
                )
            )
            .orElse(null);
    }

    public List<OrderCancellationRequest> listForAdmin(CancellationRequestStatus statusFilter) {
        if (statusFilter == null) {
            return cancellationRequestRepository.findAllByStatusInOrderByRequestedAtDesc(List.of(CancellationRequestStatus.values()));
        }
        return cancellationRequestRepository.findAllByStatusInOrderByRequestedAtDesc(List.of(statusFilter));
    }

    public OrderCancellationRequest approve(String requestNumber, String adminNotes) {
        OrderCancellationRequest request = loadRequest(requestNumber);
        if (request.getStatus() != CancellationRequestStatus.REQUESTED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cancellation request is not awaiting approval");
        }
        CustomerOrder order = request.getOrder();
        cancellationExecutor.assertCancelEligible(order);
        if (isAfterHandWrap(order)) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "This order is already packed. Reject the cancellation request and ask the customer to contact Customer Care."
            );
        }
        String refundId = cancellationExecutor.performCancellation(order, request.getReason());
        OrderCancellationRequest saved = completeApprovedCancellation(
            request,
            order,
            refundId,
            trimNotes(adminNotes),
            Instant.now()
        );
        notifyRequestAccepted(order, saved, refundId);
        return saved;
    }

    public OrderCancellationRequest recordManualCodRefund(
        String requestNumber,
        ManualRefundMethod method,
        String reference,
        String adminNotes,
        String recordedBy
    ) {
        OrderCancellationRequest request = loadRequest(requestNumber);
        if (request.getStatus() != CancellationRequestStatus.APPROVED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only approved cancellations can record COD refunds");
        }
        CustomerOrder order = request.getOrder();
        if (!OrderReturnService.isCollectedCod(order)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Manual COD refund recording applies only to collected COD orders");
        }
        if (request.getManualRefundRecordedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "COD refund already recorded for this cancellation");
        }
        if (method == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Refund method is required");
        }
        request.setManualRefundMethod(method);
        request.setManualRefundReference(validateManualRefundReference(reference));
        request.setManualRefundRecordedAt(Instant.now());
        request.setManualRefundRecordedBy(StringUtils.left(StringUtils.trimToEmpty(recordedBy), 50));
        request.setRefundAmount(money(order.getTotal()));
        request.setAdminNotes(trimNotes(adminNotes));
        order.setPaymentStatus(PaymentStatus.REFUNDED);
        customerOrderRepository.save(order);
        OrderCancellationRequest saved = cancellationRequestRepository.save(request);
        if (orderMailService != null) {
            AfterCommit.run(() -> {
                try {
                    orderMailService.sendRefundCompleted(order, saved.getRefundAmount(), saved.getManualRefundReference(), "cod");
                } catch (RuntimeException ex) {
                    LOG.warn("Cancellation refund completed email failed for {}", saved.getRequestNumber(), ex);
                }
            });
        }
        return saved;
    }

    public OrderCancellationRequest retryOnlineRefund(String requestNumber) {
        OrderCancellationRequest request = loadRequest(requestNumber);
        if (request.getStatus() != CancellationRequestStatus.APPROVED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only approved cancellations can retry a refund");
        }
        CustomerOrder order = request.getOrder();
        if (order == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
        }
        String failedRefundId = StringUtils.trimToNull(request.getRazorpayRefundId());
        String refundId = cancellationExecutor.retryFailedOnlineRefund(order);
        request.setRazorpayRefundId(refundId);
        request.setAdminNotes(
            trimNotes(
                StringUtils.trimToEmpty(request.getAdminNotes()) +
                (failedRefundId != null ? "\nRetried after failed refund " + failedRefundId : "\nRetried online refund")
            )
        );
        OrderCancellationRequest saved = cancellationRequestRepository.save(request);
        if (orderMailService != null) {
            AfterCommit.run(() -> {
                try {
                    orderMailService.sendRefundInitiated(order, money(order.getTotal()), refundId, order.getPaymentMethod());
                } catch (RuntimeException ex) {
                    LOG.warn("Cancellation refund retry email failed for {}", saved.getRequestNumber(), ex);
                }
            });
        }
        return saved;
    }

    public OrderCancellationRequest reject(String requestNumber, String rejectionReason, String adminNotes) {
        OrderCancellationRequest request = loadRequest(requestNumber);
        if (request.getStatus() != CancellationRequestStatus.REQUESTED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cancellation request is not awaiting approval");
        }
        request.setStatus(CancellationRequestStatus.REJECTED);
        request.setRejectionReason(validateRejectionReason(rejectionReason));
        request.setAdminNotes(trimNotes(adminNotes));
        request.setResolvedAt(Instant.now());
        OrderCancellationRequest saved = cancellationRequestRepository.save(request);
        if (orderMailService != null) {
            CustomerOrder order = request.getOrder();
            AfterCommit.run(() -> {
                try {
                    orderMailService.sendCancellationRejected(order, saved);
                } catch (RuntimeException ex) {
                    LOG.warn("Cancellation rejected email failed for {}", saved.getRequestNumber(), ex);
                }
            });
        }
        return saved;
    }

    public boolean canCancel(CustomerOrder order) {
        return cancellationExecutor.isCancelEligible(order);
    }

    public boolean canCustomerRequestCancel(CustomerOrder order) {
        return canCancel(order) && !isAfterHandWrap(order);
    }

    /** Customer account UI: may show "Request cancellation" (not pending, eligible, not yet packed). */
    public boolean canCustomerShowCancelAction(CustomerOrder order) {
        if (order == null || order.getId() == null) {
            return false;
        }
        if (hasPendingRequest(order.getId())) {
            return false;
        }
        return canCustomerRequestCancel(order);
    }

    public boolean shouldContactCareToCancel(CustomerOrder order) {
        if (order == null || order.getId() == null) {
            return false;
        }
        if (order.getStatus() == OrderStatus.CANCELLED || order.getStatus() == OrderStatus.FULFILLED) {
            return false;
        }
        if (hasPendingRequest(order.getId())) {
            return false;
        }
        if (canCustomerRequestCancel(order)) {
            return false;
        }
        return isAfterHandWrap(order);
    }

    public static boolean isAfterHandWrap(CustomerOrder order) {
        if (order == null) {
            return false;
        }
        return order.getStatus() == OrderStatus.HAND_WRAPPED || order.getPackedAt() != null;
    }

    public CustomerOrder loadCancellable(String orderNumber) {
        CustomerOrder order = customerOrderRepository
            .findOneByOrderNumberForUpdate(orderNumber)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        cancellationExecutor.assertCancelEligible(order);
        return order;
    }

    /**
     * Admin-initiated cancel from the orders screen. When a label exists, void the AWB first so refunds
     * are not issued while the shipment is still live.
     */
    public CustomerOrder cancelOrderFromAdmin(String orderNumber, String reason, String cancelledBy, String shipmentMode) {
        String normalizedReason = normalizeAdminReason(reason);
        CustomerOrder order = loadCancellable(orderNumber);
        boolean hadBookedLabel = StringUtils.isNotBlank(order.getTrackingNumber());
        if (hadBookedLabel) {
            if (orderShipmentService == null) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Shipment cancellation is not configured");
            }
            ReturnLogisticsMode logisticsMode = resolveShipmentCancelMode(order, shipmentMode);
            try {
                orderShipmentService.cancelShipment(
                    orderNumber,
                    logisticsMode,
                    normalizedReason,
                    StringUtils.defaultIfBlank(cancelledBy, "system")
                );
            } catch (ResponseStatusException ex) {
                if (ex.getStatusCode().isSameCodeAs(HttpStatus.BAD_GATEWAY)) {
                    throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Could not void the AWB with the carrier: "
                            + StringUtils.defaultIfBlank(ex.getReason(), ex.getMessage())
                            + ". The order was not cancelled; void the label manually or retry after the carrier confirms."
                    );
                }
                throw ex;
            }
            order = customerOrderRepository
                .findOneByOrderNumberForUpdate(orderNumber)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
            cancellationExecutor.assertCancelEligible(order);
        }
        return finalizeAdminCancellation(order, normalizedReason, cancelledBy);
    }

    private CustomerOrder finalizeAdminCancellation(CustomerOrder order, String normalizedReason, String cancelledBy) {
        String refundId = cancellationExecutor.performCancellation(order, normalizedReason);
        Instant now = Instant.now();
        var pending = cancellationRequestRepository.findFirstByOrderIdAndStatusOrderByRequestedAtDesc(
            order.getId(),
            CancellationRequestStatus.REQUESTED
        );
        boolean customerRequested = pending.isPresent();
        OrderCancellationRequest request = pending.orElseGet(() -> newAdminCancellation(order, normalizedReason, now));
        request.setReason(normalizedReason);
        String adminNotes = "Cancelled by admin" + (StringUtils.isNotBlank(cancelledBy) ? " (" + cancelledBy + ")" : "");
        OrderCancellationRequest saved = completeApprovedCancellation(request, order, refundId, adminNotes, now);
        if (customerRequested) {
            notifyRequestAccepted(order, saved, refundId);
        } else {
            notifyStoreCancelled(order, saved, refundId);
        }
        return customerOrderRepository.findById(order.getId()).orElse(order);
    }

    private OrderCancellationRequest completeApprovedCancellation(
        OrderCancellationRequest request,
        CustomerOrder order,
        String refundId,
        String adminNotes,
        Instant resolvedAt
    ) {
        request.setStatus(CancellationRequestStatus.APPROVED);
        request.setAdminNotes(trimNotes(adminNotes));
        request.setRazorpayRefundId(refundId);
        if (OrderReturnService.isCollectedCod(order)) {
            request.setRefundAmount(money(order.getTotal()));
        }
        request.setResolvedAt(resolvedAt);
        return cancellationRequestRepository.save(request);
    }

    private void notifyRequestAccepted(CustomerOrder order, OrderCancellationRequest saved, String refundId) {
        AfterCommit.run(() -> {
            runSafely("Cancellation accepted email", saved.getRequestNumber(), () -> {
                if (orderMailService != null) {
                    orderMailService.sendCancellationAccepted(order, saved);
                }
            });
            notifyRefundInitiated(order, saved, refundId);
        });
    }

    private void notifyStoreCancelled(CustomerOrder order, OrderCancellationRequest saved, String refundId) {
        AfterCommit.run(() -> {
            runSafely("Store cancellation email", saved.getRequestNumber(), () -> {
                if (orderMailService != null) {
                    orderMailService.sendOrderCancelled(order);
                }
            });
            runSafely("Store cancellation SMS", saved.getRequestNumber(), () -> {
                if (orderSmsService != null) {
                    orderSmsService.sendOrderCancelled(order);
                }
            });
            notifyRefundInitiated(order, saved, refundId);
        });
    }

    private void notifyRefundInitiated(CustomerOrder order, OrderCancellationRequest saved, String refundId) {
        if (orderMailService == null || StringUtils.isBlank(refundId)) {
            return;
        }
        runSafely("Cancellation refund initiated email", saved.getRequestNumber(), () ->
            orderMailService.sendRefundInitiated(order, money(order.getTotal()), refundId, order.getPaymentMethod())
        );
    }

    private void runSafely(String label, String id, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException ex) {
            LOG.warn("{} failed for {}", label, id, ex);
        }
    }

    private static ReturnLogisticsMode resolveShipmentCancelMode(CustomerOrder order, String shipmentMode) {
        if (StringUtils.isNotBlank(shipmentMode)) {
            try {
                return ReturnLogisticsMode.valueOf(shipmentMode.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid shipment mode: " + shipmentMode);
            }
        }
        return order.getShipmentLogisticsMode() == ReturnLogisticsMode.XPRESSBEES
            ? ReturnLogisticsMode.XPRESSBEES
            : ReturnLogisticsMode.MANUAL;
    }

    private OrderCancellationRequest newAdminCancellation(CustomerOrder order, String reason, Instant requestedAt) {
        OrderCancellationRequest created = new OrderCancellationRequest();
        created.setRequestNumber(generateRequestNumber(order.getOrderNumber()));
        created.setRequestedAt(requestedAt);
        created.setOrder(order);
        created.setUserLogin(StringUtils.defaultIfBlank(StringUtils.left(order.getUserLogin(), 50), "admin"));
        created.setReason(reason);
        return created;
    }

    public OrderCancellationRequest withdrawByCustomer(String userLogin, String orderNumber) {
        CustomerOrder order = customerOrderRepository
            .findOneByOrderNumberAndUserLogin(orderNumber, userLogin)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        OrderCancellationRequest request = cancellationRequestRepository
            .findFirstByOrderIdAndStatusOrderByRequestedAtDesc(order.getId(), CancellationRequestStatus.REQUESTED)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No pending cancellation request found"));
        if (!userLogin.equals(request.getUserLogin())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No pending cancellation request found");
        }
        request.setStatus(CancellationRequestStatus.WITHDRAWN);
        request.setResolvedAt(Instant.now());
        return cancellationRequestRepository.save(request);
    }

    public boolean hasPendingRequest(Long orderId) {
        return cancellationRequestRepository.existsByOrderIdAndStatus(orderId, CancellationRequestStatus.REQUESTED);
    }

    public Map<String, Object> toDto(OrderCancellationRequest request) {
        var data = new LinkedHashMap<String, Object>();
        data.put("id", request.getId());
        data.put("request_number", request.getRequestNumber());
        data.put("status", request.getStatus().name().toLowerCase());
        data.put("reason", request.getReason());
        data.put("admin_notes", request.getAdminNotes());
        data.put("rejection_reason", request.getRejectionReason());
        data.put("razorpay_refund_id", request.getRazorpayRefundId());
        data.put("requested_at", request.getRequestedAt());
        data.put("resolved_at", request.getResolvedAt());
        if (request.getOrder() != null) {
            CustomerOrder order = request.getOrder();
            data.put("order_id", order.getId());
            data.put("order_number", order.getOrderNumber());
            data.put("order_total", order.getTotal());
            data.put("customer_login", request.getUserLogin());
            data.put("payment_method", order.getPaymentMethod());
            data.put("payment_status", order.getPaymentStatus() != null ? order.getPaymentStatus().name().toLowerCase() : null);
            data.put(
                "cod_refund_pending",
                request.getStatus() == CancellationRequestStatus.APPROVED &&
                OrderReturnService.isCollectedCod(order) &&
                request.getManualRefundRecordedAt() == null
            );
        }
        data.put("refund_amount", request.getRefundAmount());
        data.put(
            "manual_refund_method",
            request.getManualRefundMethod() != null ? request.getManualRefundMethod().name().toLowerCase() : null
        );
        data.put("manual_refund_reference", request.getManualRefundReference());
        data.put("manual_refund_recorded_at", request.getManualRefundRecordedAt());
        data.put("manual_refund_recorded_by", request.getManualRefundRecordedBy());
        return data;
    }

    /** Customer account APIs — no internal admin or ops fields. */
    public Map<String, Object> toCustomerDto(OrderCancellationRequest request) {
        var data = new LinkedHashMap<String, Object>();
        data.put("request_number", request.getRequestNumber());
        data.put("status", request.getStatus().name().toLowerCase());
        data.put("reason", request.getReason());
        data.put("rejection_reason", request.getRejectionReason());
        data.put("requested_at", request.getRequestedAt());
        data.put("resolved_at", request.getResolvedAt());
        if (request.getOrder() != null) {
            CustomerOrder order = request.getOrder();
            data.put(
                "cod_refund_pending",
                request.getStatus() == CancellationRequestStatus.APPROVED &&
                OrderReturnService.isCollectedCod(order) &&
                request.getManualRefundRecordedAt() == null
            );
        }
        return data;
    }

    private OrderCancellationRequest loadRequest(String requestNumber) {
        return cancellationRequestRepository
            .findOneByRequestNumber(requestNumber)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cancellation request not found"));
    }

    private CustomerOrder requireOwnedOrder(String userLogin, String orderNumber) {
        return customerOrderRepository
            .findOneByOrderNumberAndUserLogin(orderNumber, userLogin)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
    }

    private static String normalizeReason(String reason) {
        String normalized = StringUtils.normalizeSpace(StringUtils.trimToEmpty(reason));
        if (normalized.length() < 3) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cancellation reason must be at least 3 characters");
        }
        if (normalized.length() > 2000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cancellation reason is too long");
        }
        return normalized;
    }

    private static String normalizeAdminReason(String reason) {
        String normalized = StringUtils.normalizeSpace(StringUtils.trimToEmpty(reason));
        if (normalized.length() < ADMIN_CANCEL_REASON_MIN_LENGTH) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Cancellation reason must be at least " + ADMIN_CANCEL_REASON_MIN_LENGTH + " characters"
            );
        }
        if (normalized.length() > 2000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cancellation reason is too long");
        }
        return normalized;
    }

    private static String validateRejectionReason(String rejectionReason) {
        String normalized = StringUtils.normalizeSpace(StringUtils.trimToEmpty(rejectionReason));
        if (normalized.length() < ADMIN_CANCEL_REASON_MIN_LENGTH) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Rejection reason must be at least " + ADMIN_CANCEL_REASON_MIN_LENGTH + " characters"
            );
        }
        return normalized.length() <= 1000 ? normalized : normalized.substring(0, 1000);
    }

    private static String trimNotes(String adminNotes) {
        if (adminNotes == null) {
            return null;
        }
        String trimmed = StringUtils.normalizeSpace(adminNotes).trim();
        return trimmed.isEmpty() ? null : StringUtils.left(trimmed, 2000);
    }

    private static String generateRequestNumber(String orderNumber) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
        return "CAN-" + StringUtils.defaultIfBlank(orderNumber, "ORD").replaceAll("[^A-Za-z0-9]", "") + "-" + suffix;
    }

    private static BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String validateManualRefundReference(String reference) {
        String normalized = StringUtils.normalizeSpace(StringUtils.trimToEmpty(reference));
        if (normalized.length() < 4) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Refund reference must be at least 4 characters");
        }
        return StringUtils.left(normalized, 160);
    }
}
