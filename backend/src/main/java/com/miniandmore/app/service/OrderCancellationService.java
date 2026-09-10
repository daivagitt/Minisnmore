package com.miniandmore.app.service;

import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.OrderCancellationRequest;
import com.miniandmore.app.domain.enumeration.CancellationRequestStatus;
import com.miniandmore.app.repository.CustomerOrderRepository;
import com.miniandmore.app.repository.OrderCancellationRequestRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class OrderCancellationService {

    private final OrderCancellationRequestRepository cancellationRequestRepository;
    private final CustomerOrderRepository customerOrderRepository;
    private final OrderCancellationExecutor cancellationExecutor;

    public OrderCancellationService(
        OrderCancellationRequestRepository cancellationRequestRepository,
        CustomerOrderRepository customerOrderRepository,
        OrderCancellationExecutor cancellationExecutor
    ) {
        this.cancellationRequestRepository = cancellationRequestRepository;
        this.customerOrderRepository = customerOrderRepository;
        this.cancellationExecutor = cancellationExecutor;
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
        return cancellationRequestRepository.save(request);
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
        request.setResolvedAt(Instant.now());
        return cancellationRequestRepository.save(request);
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
        return cancellationRequestRepository.save(request);
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
            data.put("order_id", request.getOrder().getId());
            data.put("order_number", request.getOrder().getOrderNumber());
            data.put("order_total", request.getOrder().getTotal());
            data.put("customer_login", request.getUserLogin());
        }
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
}
