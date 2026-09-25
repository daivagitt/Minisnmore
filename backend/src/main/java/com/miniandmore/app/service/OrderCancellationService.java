package com.miniandmore.app.service;

import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.OrderCancellationRequest;
import com.miniandmore.app.domain.enumeration.CancellationRequestStatus;
import com.miniandmore.app.domain.enumeration.ManualRefundMethod;
import com.miniandmore.app.domain.enumeration.PaymentStatus;
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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class OrderCancellationService {

    private static final Logger LOG = LoggerFactory.getLogger(OrderCancellationService.class);

    private final OrderCancellationRequestRepository cancellationRequestRepository;
    private final CustomerOrderRepository customerOrderRepository;
    private final OrderCancellationExecutor cancellationExecutor;
    private final OrderMailService orderMailService;

    public OrderCancellationService(
        OrderCancellationRequestRepository cancellationRequestRepository,
        CustomerOrderRepository customerOrderRepository,
        OrderCancellationExecutor cancellationExecutor
    ) {
        this(cancellationRequestRepository, customerOrderRepository, cancellationExecutor, null);
    }

    @Autowired
    public OrderCancellationService(
        OrderCancellationRequestRepository cancellationRequestRepository,
        CustomerOrderRepository customerOrderRepository,
        OrderCancellationExecutor cancellationExecutor,
        @Autowired(required = false) OrderMailService orderMailService
    ) {
        this.cancellationRequestRepository = cancellationRequestRepository;
        this.customerOrderRepository = customerOrderRepository;
        this.cancellationExecutor = cancellationExecutor;
        this.orderMailService = orderMailService;
    }

    public OrderCancellationRequest submitRequest(String userLogin, String orderNumber, String reason) {
        String normalizedReason = normalizeReason(reason);
        CustomerOrder order = customerOrderRepository
            .findOneByOrderNumberAndUserLogin(orderNumber, userLogin)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        cancellationExecutor.assertCancelEligible(order);
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
        CustomerOrder order = customerOrderRepository
            .findOneByOrderNumberAndUserLogin(orderNumber, userLogin)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        return cancellationRequestRepository
            .findFirstByOrderIdAndStatusOrderByRequestedAtDesc(order.getId(), CancellationRequestStatus.REQUESTED)
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
        String refundId = cancellationExecutor.performCancellation(order, request.getReason());
        request.setStatus(CancellationRequestStatus.APPROVED);
        request.setAdminNotes(trimNotes(adminNotes));
        request.setRazorpayRefundId(refundId);
        if (OrderReturnService.isCollectedCod(order)) {
            request.setRefundAmount(money(order.getTotal()));
        }
        request.setResolvedAt(Instant.now());
        OrderCancellationRequest saved = cancellationRequestRepository.save(request);
        if (orderMailService != null) {
            AfterCommit.run(() -> {
                try {
                    orderMailService.sendCancellationAccepted(order, saved);
                } catch (RuntimeException ex) {
                    LOG.warn("Cancellation accepted email failed for {}", saved.getRequestNumber(), ex);
                }
            });
        }
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

    private OrderCancellationRequest loadRequest(String requestNumber) {
        return cancellationRequestRepository
            .findOneByRequestNumber(requestNumber)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cancellation request not found"));
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

    private static String validateRejectionReason(String rejectionReason) {
        String normalized = StringUtils.normalizeSpace(StringUtils.trimToEmpty(rejectionReason));
        if (normalized.length() < 5) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rejection reason must be at least 5 characters");
        }
        return normalized;
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
