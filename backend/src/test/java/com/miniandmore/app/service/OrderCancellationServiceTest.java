package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.OrderCancellationRequest;
import com.miniandmore.app.domain.enumeration.CancellationRequestStatus;
import com.miniandmore.app.domain.enumeration.ManualRefundMethod;
import com.miniandmore.app.domain.enumeration.OrderStatus;
import com.miniandmore.app.domain.enumeration.PaymentStatus;
import com.miniandmore.app.repository.CustomerOrderRepository;
import com.miniandmore.app.repository.OrderCancellationRequestRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class OrderCancellationServiceTest {

    @Mock
    private OrderCancellationRequestRepository cancellationRequestRepository;

    @Mock
    private CustomerOrderRepository customerOrderRepository;

    @Mock
    private OrderCancellationExecutor cancellationExecutor;

    @Mock
    private OrderMailService orderMailService;

    @Mock
    private OrderSmsService orderSmsService;

    @Mock
    private OrderShipmentService orderShipmentService;

    private OrderCancellationService service;

    @BeforeEach
    void setUp() {
        service = new OrderCancellationService(
            cancellationRequestRepository,
            customerOrderRepository,
            cancellationExecutor,
            orderMailService,
            orderSmsService,
            orderShipmentService
        );
    }

    private CustomerOrder createOrder(String orderNumber, String userLogin) {
        CustomerOrder order = new CustomerOrder();
        order.setId(101L);
        order.setOrderNumber(orderNumber);
        order.setUserLogin(userLogin);
        order.setStatus(OrderStatus.CONFIRMED);
        order.setPaymentStatus(PaymentStatus.PAID);
        order.setPaymentMethod("online");
        order.setTotal(new BigDecimal("1500.00"));
        return order;
    }

    @Test
    void submitRequest_validRequest_success() {
        CustomerOrder order = createOrder("ORD-001", "testuser");
        when(customerOrderRepository.findOneByOrderNumberAndUserLogin("ORD-001", "testuser"))
            .thenReturn(Optional.of(order));
        when(cancellationRequestRepository.existsByOrderIdAndStatus(101L, CancellationRequestStatus.REQUESTED))
            .thenReturn(false);
        when(cancellationRequestRepository.save(any(OrderCancellationRequest.class)))
            .thenAnswer(inv -> inv.getArgument(0));

        OrderCancellationRequest request = service.submitRequest("testuser", "ORD-001", "Changed my mind about the purchase");

        assertThat(request).isNotNull();
        assertThat(request.getStatus()).isEqualTo(CancellationRequestStatus.REQUESTED);
        assertThat(request.getReason()).isEqualTo("Changed my mind about the purchase");
        assertThat(request.getUserLogin()).isEqualTo("testuser");
        assertThat(request.getOrder()).isEqualTo(order);
        verify(cancellationExecutor).assertCancelEligible(order);
        verify(orderMailService).sendCancellationRequested(eq(order), any(OrderCancellationRequest.class));
    }

    @Test
    void submitRequest_reasonTooShort_throwsBadRequest() {
        assertThatThrownBy(() -> service.submitRequest("testuser", "ORD-001", "no"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Cancellation reason must be at least 3 characters");
    }

    @Test
    void submitRequest_reasonTooLong_throwsBadRequest() {
        String longReason = "a".repeat(2001);
        assertThatThrownBy(() -> service.submitRequest("testuser", "ORD-001", longReason))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Cancellation reason is too long");
    }

    @Test
    void submitRequest_orderNotFound_throwsNotFound() {
        when(customerOrderRepository.findOneByOrderNumberAndUserLogin("ORD-001", "testuser"))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.submitRequest("testuser", "ORD-001", "Valid reason"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Order not found");
    }

    @Test
    void submitRequest_alreadyPending_throwsConflict() {
        CustomerOrder order = createOrder("ORD-001", "testuser");
        when(customerOrderRepository.findOneByOrderNumberAndUserLogin("ORD-001", "testuser"))
            .thenReturn(Optional.of(order));
        when(cancellationRequestRepository.existsByOrderIdAndStatus(101L, CancellationRequestStatus.REQUESTED))
            .thenReturn(true);

        assertThatThrownBy(() -> service.submitRequest("testuser", "ORD-001", "Valid reason"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("A cancellation request is already pending for this order");
    }

    @Test
    void submitRequest_afterHandWrap_success() {
        CustomerOrder order = createOrder("ORD-001", "testuser");
        order.setStatus(OrderStatus.HAND_WRAPPED);
        when(customerOrderRepository.findOneByOrderNumberAndUserLogin("ORD-001", "testuser"))
            .thenReturn(Optional.of(order));
        when(cancellationRequestRepository.existsByOrderIdAndStatus(101L, CancellationRequestStatus.REQUESTED))
            .thenReturn(false);
        when(cancellationRequestRepository.save(any(OrderCancellationRequest.class)))
            .thenAnswer(inv -> inv.getArgument(0));

        OrderCancellationRequest request = service.submitRequest("testuser", "ORD-001", "Valid reason here");

        assertThat(request.getStatus()).isEqualTo(CancellationRequestStatus.REQUESTED);
        assertThat(request.getOrder()).isEqualTo(order);
        verify(cancellationExecutor).assertCancelEligible(order);
    }

    @Test
    void submitRequest_afterPackedAt_success() {
        CustomerOrder order = createOrder("ORD-001", "testuser");
        order.setPackedAt(Instant.parse("2026-09-30T00:00:00Z"));
        when(customerOrderRepository.findOneByOrderNumberAndUserLogin("ORD-001", "testuser"))
            .thenReturn(Optional.of(order));
        when(cancellationRequestRepository.existsByOrderIdAndStatus(101L, CancellationRequestStatus.REQUESTED))
            .thenReturn(false);
        when(cancellationRequestRepository.save(any(OrderCancellationRequest.class)))
            .thenAnswer(inv -> inv.getArgument(0));

        OrderCancellationRequest request = service.submitRequest("testuser", "ORD-001", "Valid reason here");

        assertThat(request.getStatus()).isEqualTo(CancellationRequestStatus.REQUESTED);
        assertThat(request.getOrder()).isEqualTo(order);
        verify(cancellationExecutor).assertCancelEligible(order);
    }

    @Test
    void submitRequest_mailServiceFails_doesNotThrow() {
        CustomerOrder order = createOrder("ORD-001", "testuser");
        when(customerOrderRepository.findOneByOrderNumberAndUserLogin("ORD-001", "testuser"))
            .thenReturn(Optional.of(order));
        when(cancellationRequestRepository.existsByOrderIdAndStatus(101L, CancellationRequestStatus.REQUESTED))
            .thenReturn(false);
        when(cancellationRequestRepository.save(any(OrderCancellationRequest.class)))
            .thenAnswer(inv -> inv.getArgument(0));
        doThrow(new RuntimeException("Mail server down"))
            .when(orderMailService).sendCancellationRequested(any(), any());

        OrderCancellationRequest request = service.submitRequest("testuser", "ORD-001", "Valid reason here");
        assertThat(request).isNotNull();
    }

    @Test
    void getOpenForUser_found_returnsRequest() {
        CustomerOrder order = createOrder("ORD-001", "testuser");
        OrderCancellationRequest req = new OrderCancellationRequest();
        req.setRequestNumber("CAN-ORD001-123456");

        when(customerOrderRepository.findOneByOrderNumberAndUserLogin("ORD-001", "testuser"))
            .thenReturn(Optional.of(order));
        when(cancellationRequestRepository.findFirstByOrderIdAndStatusOrderByRequestedAtDesc(101L, CancellationRequestStatus.REQUESTED))
            .thenReturn(Optional.of(req));

        assertThat(service.getOpenForUser("testuser", "ORD-001")).isEqualTo(req);
    }

    @Test
    void getOpenForUser_notFound_returnsNull() {
        CustomerOrder order = createOrder("ORD-001", "testuser");
        when(customerOrderRepository.findOneByOrderNumberAndUserLogin("ORD-001", "testuser"))
            .thenReturn(Optional.of(order));
        when(cancellationRequestRepository.findFirstByOrderIdAndStatusOrderByRequestedAtDesc(101L, CancellationRequestStatus.REQUESTED))
            .thenReturn(Optional.empty());

        assertThat(service.getOpenForUser("testuser", "ORD-001")).isNull();
    }

    @Test
    void getOpenForUser_orderNotFound_throwsNotFound() {
        when(customerOrderRepository.findOneByOrderNumberAndUserLogin("ORD-001", "testuser"))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getOpenForUser("testuser", "ORD-001"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Order not found");
    }

    @Test
    void getVisibleForUser_prefersPendingRequest() {
        CustomerOrder order = createOrder("ORD-001", "testuser");
        OrderCancellationRequest pending = new OrderCancellationRequest();
        pending.setRequestNumber("CAN-OPEN");
        pending.setStatus(CancellationRequestStatus.REQUESTED);

        when(customerOrderRepository.findOneByOrderNumberAndUserLogin("ORD-001", "testuser"))
            .thenReturn(Optional.of(order));
        when(cancellationRequestRepository.findFirstByOrderIdAndStatusOrderByRequestedAtDesc(101L, CancellationRequestStatus.REQUESTED))
            .thenReturn(Optional.of(pending));

        assertThat(service.getVisibleForUser("testuser", "ORD-001")).isEqualTo(pending);
    }

    @Test
    void getVisibleForUser_returnsLatestRejectedWhenNonePending() {
        CustomerOrder order = createOrder("ORD-001", "testuser");
        OrderCancellationRequest rejected = new OrderCancellationRequest();
        rejected.setRequestNumber("CAN-REJ");
        rejected.setStatus(CancellationRequestStatus.REJECTED);
        rejected.setRejectionReason("Order is already packed");

        when(customerOrderRepository.findOneByOrderNumberAndUserLogin("ORD-001", "testuser"))
            .thenReturn(Optional.of(order));
        when(cancellationRequestRepository.findFirstByOrderIdAndStatusOrderByRequestedAtDesc(101L, CancellationRequestStatus.REQUESTED))
            .thenReturn(Optional.empty());
        when(cancellationRequestRepository.findFirstByOrderIdAndStatusOrderByRequestedAtDesc(101L, CancellationRequestStatus.REJECTED))
            .thenReturn(Optional.of(rejected));

        assertThat(service.getVisibleForUser("testuser", "ORD-001")).isEqualTo(rejected);
    }

    @Test
    void getVisibleForUser_stillReturnsRejectedAfterLaterWithdrawnRequest() {
        CustomerOrder order = createOrder("ORD-001", "testuser");
        OrderCancellationRequest rejected = new OrderCancellationRequest();
        rejected.setRequestNumber("CAN-REJ");
        rejected.setStatus(CancellationRequestStatus.REJECTED);
        rejected.setRejectionReason("Already shipped");

        when(customerOrderRepository.findOneByOrderNumberAndUserLogin("ORD-001", "testuser"))
            .thenReturn(Optional.of(order));
        when(cancellationRequestRepository.findFirstByOrderIdAndStatusOrderByRequestedAtDesc(101L, CancellationRequestStatus.REQUESTED))
            .thenReturn(Optional.empty());
        when(cancellationRequestRepository.findFirstByOrderIdAndStatusOrderByRequestedAtDesc(101L, CancellationRequestStatus.REJECTED))
            .thenReturn(Optional.of(rejected));

        assertThat(service.getVisibleForUser("testuser", "ORD-001")).isEqualTo(rejected);
    }

    @Test
    void getVisibleForUser_none_returnsNull() {
        CustomerOrder order = createOrder("ORD-001", "testuser");
        when(customerOrderRepository.findOneByOrderNumberAndUserLogin("ORD-001", "testuser"))
            .thenReturn(Optional.of(order));
        when(cancellationRequestRepository.findFirstByOrderIdAndStatusOrderByRequestedAtDesc(101L, CancellationRequestStatus.REQUESTED))
            .thenReturn(Optional.empty());
        when(cancellationRequestRepository.findFirstByOrderIdAndStatusOrderByRequestedAtDesc(101L, CancellationRequestStatus.REJECTED))
            .thenReturn(Optional.empty());

        assertThat(service.getVisibleForUser("testuser", "ORD-001")).isNull();
    }

    @Test
    void listForAdmin_nullFilter_returnsAll() {
        OrderCancellationRequest req = new OrderCancellationRequest();
        when(cancellationRequestRepository.findAllByStatusInOrderByRequestedAtDesc(anyList()))
            .thenReturn(List.of(req));

        List<OrderCancellationRequest> result = service.listForAdmin(null);
        assertThat(result).containsExactly(req);
    }

    @Test
    void listForAdmin_withFilter_returnsFiltered() {
        OrderCancellationRequest req = new OrderCancellationRequest();
        when(cancellationRequestRepository.findAllByStatusInOrderByRequestedAtDesc(List.of(CancellationRequestStatus.APPROVED)))
            .thenReturn(List.of(req));

        List<OrderCancellationRequest> result = service.listForAdmin(CancellationRequestStatus.APPROVED);
        assertThat(result).containsExactly(req);
    }

    @Test
    void approve_onlinePayment_success() {
        CustomerOrder order = createOrder("ORD-001", "testuser");
        OrderCancellationRequest req = new OrderCancellationRequest();
        req.setRequestNumber("CAN-ORD001-111111");
        req.setStatus(CancellationRequestStatus.REQUESTED);
        req.setReason("Item not needed");
        req.setOrder(order);

        when(cancellationRequestRepository.findOneByRequestNumber("CAN-ORD001-111111"))
            .thenReturn(Optional.of(req));
        when(cancellationExecutor.performCancellation(order, "Item not needed"))
            .thenReturn("rfnd_12345");
        when(cancellationRequestRepository.save(any(OrderCancellationRequest.class)))
            .thenAnswer(inv -> inv.getArgument(0));

        OrderCancellationRequest approved = service.approve("CAN-ORD001-111111", "Approved by admin");

        assertThat(approved.getStatus()).isEqualTo(CancellationRequestStatus.APPROVED);
        assertThat(approved.getRazorpayRefundId()).isEqualTo("rfnd_12345");
        assertThat(approved.getAdminNotes()).isEqualTo("Approved by admin");
        verify(orderMailService).sendCancellationAccepted(eq(order), eq(approved));
        verify(orderMailService).sendRefundInitiated(eq(order), eq(order.getTotal()), eq("rfnd_12345"), eq("online"));
        verify(orderMailService, never()).sendOrderCancelled(any());
        verify(orderSmsService, never()).sendOrderCancelled(any());
    }

    @Test
    void approve_collectedCod_setsRefundAmount() {
        CustomerOrder order = createOrder("ORD-002", "testuser");
        order.setPaymentMethod("cod");
        order.setPaymentStatus(PaymentStatus.PAID);
        order.setStatus(OrderStatus.CONFIRMED);

        OrderCancellationRequest req = new OrderCancellationRequest();
        req.setRequestNumber("CAN-ORD002-222222");
        req.setStatus(CancellationRequestStatus.REQUESTED);
        req.setReason("Item not needed");
        req.setOrder(order);

        when(cancellationRequestRepository.findOneByRequestNumber("CAN-ORD002-222222"))
            .thenReturn(Optional.of(req));
        when(cancellationRequestRepository.save(any(OrderCancellationRequest.class)))
            .thenAnswer(inv -> inv.getArgument(0));

        OrderCancellationRequest approved = service.approve("CAN-ORD002-222222", "Notes");

        assertThat(approved.getRefundAmount()).isEqualTo(order.getTotal());
    }

    @Test
    void approve_afterHandWrap_success() {
        CustomerOrder order = createOrder("ORD-001", "testuser");
        order.setStatus(OrderStatus.HAND_WRAPPED);
        OrderCancellationRequest req = new OrderCancellationRequest();
        req.setRequestNumber("CAN-ORD001-111111");
        req.setStatus(CancellationRequestStatus.REQUESTED);
        req.setReason("Changed my mind");
        req.setOrder(order);

        when(cancellationRequestRepository.findOneByRequestNumber("CAN-ORD001-111111"))
            .thenReturn(Optional.of(req));
        when(cancellationExecutor.performCancellation(order, "Changed my mind")).thenReturn("rfnd_packed");
        when(cancellationRequestRepository.save(any(OrderCancellationRequest.class)))
            .thenAnswer(inv -> inv.getArgument(0));

        OrderCancellationRequest approved = service.approve("CAN-ORD001-111111", "Approved");

        assertThat(approved.getStatus()).isEqualTo(CancellationRequestStatus.APPROVED);
        assertThat(approved.getRazorpayRefundId()).isEqualTo("rfnd_packed");
        verify(cancellationExecutor).assertCancelEligible(order);
        verify(cancellationExecutor).performCancellation(order, "Changed my mind");
    }

    @Test
    void approve_withBookedLabel_voidsAwbBeforeCancellingOrder() {
        CustomerOrder order = createOrder("ORD-001", "testuser");
        order.setStatus(OrderStatus.HAND_WRAPPED);
        order.setTrackingNumber("XB-1");
        order.setShipmentLogisticsMode(com.miniandmore.app.domain.enumeration.ReturnLogisticsMode.XPRESSBEES);
        CustomerOrder afterVoid = createOrder("ORD-001", "testuser");
        afterVoid.setStatus(OrderStatus.HAND_WRAPPED);
        OrderCancellationRequest req = new OrderCancellationRequest();
        req.setRequestNumber("CAN-ORD001-111111");
        req.setStatus(CancellationRequestStatus.REQUESTED);
        req.setReason("Changed my mind");
        req.setOrder(order);

        when(cancellationRequestRepository.findOneByRequestNumber("CAN-ORD001-111111"))
            .thenReturn(Optional.of(req));
        when(customerOrderRepository.findOneByOrderNumberForUpdate("ORD-001")).thenReturn(Optional.of(afterVoid));
        when(cancellationExecutor.performCancellation(afterVoid, "Changed my mind")).thenReturn("rfnd_packed");
        when(cancellationRequestRepository.save(any(OrderCancellationRequest.class)))
            .thenAnswer(inv -> inv.getArgument(0));

        service.approve("CAN-ORD001-111111", "Approved");

        InOrder inOrder = inOrder(orderShipmentService, cancellationExecutor);
        inOrder
            .verify(orderShipmentService)
            .cancelShipment(
                eq("ORD-001"),
                eq(com.miniandmore.app.domain.enumeration.ReturnLogisticsMode.XPRESSBEES),
                eq("Changed my mind"),
                eq("cancellation-approval")
            );
        inOrder.verify(cancellationExecutor).assertCancelEligible(afterVoid);
        inOrder.verify(cancellationExecutor).performCancellation(afterVoid, "Changed my mind");
    }

    @Test
    void approve_shortCustomerReason_combinesAdminNotesForShipmentVoid() {
        CustomerOrder order = createOrder("ORD-001", "testuser");
        order.setTrackingNumber("XB-1");
        order.setShipmentLogisticsMode(com.miniandmore.app.domain.enumeration.ReturnLogisticsMode.XPRESSBEES);
        CustomerOrder afterVoid = createOrder("ORD-001", "testuser");
        OrderCancellationRequest req = new OrderCancellationRequest();
        req.setRequestNumber("CAN-ORD001-111111");
        req.setStatus(CancellationRequestStatus.REQUESTED);
        req.setReason("Yes");
        req.setOrder(order);

        when(cancellationRequestRepository.findOneByRequestNumber("CAN-ORD001-111111"))
            .thenReturn(Optional.of(req));
        when(customerOrderRepository.findOneByOrderNumberForUpdate("ORD-001")).thenReturn(Optional.of(afterVoid));
        when(cancellationExecutor.performCancellation(afterVoid, "Yes")).thenReturn(null);
        when(cancellationRequestRepository.save(any(OrderCancellationRequest.class)))
            .thenAnswer(inv -> inv.getArgument(0));

        service.approve("CAN-ORD001-111111", "Approved by ops");

        verify(orderShipmentService).cancelShipment(
            eq("ORD-001"),
            eq(com.miniandmore.app.domain.enumeration.ReturnLogisticsMode.XPRESSBEES),
            eq("Yes Approved by ops"),
            eq("cancellation-approval")
        );
    }

    @Test
    void approve_notRequested_throwsBadRequest() {
        OrderCancellationRequest req = new OrderCancellationRequest();
        req.setRequestNumber("CAN-ORD001-111111");
        req.setStatus(CancellationRequestStatus.APPROVED);

        when(cancellationRequestRepository.findOneByRequestNumber("CAN-ORD001-111111"))
            .thenReturn(Optional.of(req));

        assertThatThrownBy(() -> service.approve("CAN-ORD001-111111", "Notes"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Cancellation request is not awaiting approval");
    }

    @Test
    void recordManualCodRefund_success() {
        CustomerOrder order = createOrder("ORD-002", "testuser");
        order.setPaymentMethod("cod");
        order.setPaymentStatus(PaymentStatus.PAID);
        order.setStatus(OrderStatus.CONFIRMED);

        OrderCancellationRequest req = new OrderCancellationRequest();
        req.setRequestNumber("CAN-ORD002-COD");
        req.setStatus(CancellationRequestStatus.APPROVED);
        req.setOrder(order);

        when(cancellationRequestRepository.findOneByRequestNumber("CAN-ORD002-COD"))
            .thenReturn(Optional.of(req));
        when(cancellationRequestRepository.save(any(OrderCancellationRequest.class)))
            .thenAnswer(inv -> inv.getArgument(0));
        when(customerOrderRepository.save(any(CustomerOrder.class)))
            .thenAnswer(inv -> inv.getArgument(0));

        OrderCancellationRequest refunded = service.recordManualCodRefund(
            "CAN-ORD002-COD",
            ManualRefundMethod.BANK_TRANSFER,
            "TXN-987654",
            "Refund sent via NEFT",
            "admin_agent"
        );

        assertThat(refunded.getManualRefundMethod()).isEqualTo(ManualRefundMethod.BANK_TRANSFER);
        assertThat(refunded.getManualRefundReference()).isEqualTo("TXN-987654");
        assertThat(refunded.getManualRefundRecordedBy()).isEqualTo("admin_agent");
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
        verify(orderMailService).sendRefundCompleted(eq(order), eq(order.getTotal()), eq("TXN-987654"), eq("cod"));
    }

    @Test
    void recordManualCodRefund_notApproved_throwsBadRequest() {
        OrderCancellationRequest req = new OrderCancellationRequest();
        req.setStatus(CancellationRequestStatus.REQUESTED);
        when(cancellationRequestRepository.findOneByRequestNumber("CAN-1"))
            .thenReturn(Optional.of(req));

        assertThatThrownBy(() -> service.recordManualCodRefund("CAN-1", ManualRefundMethod.UPI, "REF123", "", "admin"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Only approved cancellations can record COD refunds");
    }

    @Test
    void recordManualCodRefund_notCollectedCod_throwsBadRequest() {
        CustomerOrder order = createOrder("ORD-1", "user");
        order.setPaymentMethod("online");

        OrderCancellationRequest req = new OrderCancellationRequest();
        req.setStatus(CancellationRequestStatus.APPROVED);
        req.setOrder(order);
        when(cancellationRequestRepository.findOneByRequestNumber("CAN-1"))
            .thenReturn(Optional.of(req));

        assertThatThrownBy(() -> service.recordManualCodRefund("CAN-1", ManualRefundMethod.UPI, "REF123", "", "admin"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Manual COD refund recording applies only to collected COD orders");
    }

    @Test
    void recordManualCodRefund_alreadyRecorded_throwsConflict() {
        CustomerOrder order = createOrder("ORD-1", "user");
        order.setPaymentMethod("cod");
        order.setPaymentStatus(PaymentStatus.PAID);
        order.setStatus(OrderStatus.CONFIRMED);

        OrderCancellationRequest req = new OrderCancellationRequest();
        req.setStatus(CancellationRequestStatus.APPROVED);
        req.setOrder(order);
        req.setManualRefundRecordedAt(Instant.now());
        when(cancellationRequestRepository.findOneByRequestNumber("CAN-1"))
            .thenReturn(Optional.of(req));

        assertThatThrownBy(() -> service.recordManualCodRefund("CAN-1", ManualRefundMethod.UPI, "REF123", "", "admin"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("COD refund already recorded for this cancellation");
    }

    @Test
    void recordManualCodRefund_nullMethod_throwsBadRequest() {
        CustomerOrder order = createOrder("ORD-1", "user");
        order.setPaymentMethod("cod");
        order.setPaymentStatus(PaymentStatus.PAID);
        order.setStatus(OrderStatus.CONFIRMED);

        OrderCancellationRequest req = new OrderCancellationRequest();
        req.setStatus(CancellationRequestStatus.APPROVED);
        req.setOrder(order);
        when(cancellationRequestRepository.findOneByRequestNumber("CAN-1"))
            .thenReturn(Optional.of(req));

        assertThatThrownBy(() -> service.recordManualCodRefund("CAN-1", null, "REF123", "", "admin"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Refund method is required");
    }

    @Test
    void recordManualCodRefund_shortReference_throwsBadRequest() {
        CustomerOrder order = createOrder("ORD-1", "user");
        order.setPaymentMethod("cod");
        order.setPaymentStatus(PaymentStatus.PAID);
        order.setStatus(OrderStatus.CONFIRMED);

        OrderCancellationRequest req = new OrderCancellationRequest();
        req.setStatus(CancellationRequestStatus.APPROVED);
        req.setOrder(order);
        when(cancellationRequestRepository.findOneByRequestNumber("CAN-1"))
            .thenReturn(Optional.of(req));

        assertThatThrownBy(() -> service.recordManualCodRefund("CAN-1", ManualRefundMethod.UPI, "ab", "", "admin"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Refund reference must be at least 4 characters");
    }

    @Test
    void reject_success() {
        CustomerOrder order = createOrder("ORD-001", "testuser");
        OrderCancellationRequest req = new OrderCancellationRequest();
        req.setRequestNumber("CAN-REJ-01");
        req.setStatus(CancellationRequestStatus.REQUESTED);
        req.setOrder(order);

        when(cancellationRequestRepository.findOneByRequestNumber("CAN-REJ-01"))
            .thenReturn(Optional.of(req));
        when(cancellationRequestRepository.save(any(OrderCancellationRequest.class)))
            .thenAnswer(inv -> inv.getArgument(0));

        OrderCancellationRequest rejected = service.reject("CAN-REJ-01", "Order already processed for packing", "Admin note");

        assertThat(rejected.getStatus()).isEqualTo(CancellationRequestStatus.REJECTED);
        assertThat(rejected.getRejectionReason()).isEqualTo("Order already processed for packing");
        assertThat(rejected.getAdminNotes()).isEqualTo("Admin note");
        verify(orderMailService).sendCancellationRejected(eq(order), eq(rejected));
    }

    @Test
    void reject_shortReason_throwsBadRequest() {
        OrderCancellationRequest req = new OrderCancellationRequest();
        req.setStatus(CancellationRequestStatus.REQUESTED);
        when(cancellationRequestRepository.findOneByRequestNumber("CAN-1"))
            .thenReturn(Optional.of(req));

        assertThatThrownBy(() -> service.reject("CAN-1", "bad", "note"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Rejection reason must be at least 5 characters");
    }

    @Test
    void reject_notRequested_throwsBadRequest() {
        OrderCancellationRequest req = new OrderCancellationRequest();
        req.setStatus(CancellationRequestStatus.REJECTED);
        when(cancellationRequestRepository.findOneByRequestNumber("CAN-1"))
            .thenReturn(Optional.of(req));

        assertThatThrownBy(() -> service.reject("CAN-1", "Valid rejection reason", "note"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Cancellation request is not awaiting approval");
    }

    @Test
    void withdrawByCustomer_success() {
        CustomerOrder order = createOrder("ORD-001", "testuser");
        OrderCancellationRequest req = new OrderCancellationRequest();
        req.setRequestNumber("CAN-001");
        req.setUserLogin("testuser");
        req.setStatus(CancellationRequestStatus.REQUESTED);

        when(customerOrderRepository.findOneByOrderNumberAndUserLogin("ORD-001", "testuser"))
            .thenReturn(Optional.of(order));
        when(cancellationRequestRepository.findFirstByOrderIdAndStatusOrderByRequestedAtDesc(101L, CancellationRequestStatus.REQUESTED))
            .thenReturn(Optional.of(req));
        when(cancellationRequestRepository.save(any(OrderCancellationRequest.class)))
            .thenAnswer(inv -> inv.getArgument(0));

        OrderCancellationRequest withdrawn = service.withdrawByCustomer("testuser", "ORD-001");
        assertThat(withdrawn.getStatus()).isEqualTo(CancellationRequestStatus.WITHDRAWN);
    }

    @Test
    void withdrawByCustomer_loginMismatch_throwsNotFound() {
        CustomerOrder order = createOrder("ORD-001", "testuser");
        OrderCancellationRequest req = new OrderCancellationRequest();
        req.setUserLogin("otheruser");
        req.setStatus(CancellationRequestStatus.REQUESTED);

        when(customerOrderRepository.findOneByOrderNumberAndUserLogin("ORD-001", "testuser"))
            .thenReturn(Optional.of(order));
        when(cancellationRequestRepository.findFirstByOrderIdAndStatusOrderByRequestedAtDesc(101L, CancellationRequestStatus.REQUESTED))
            .thenReturn(Optional.of(req));

        assertThatThrownBy(() -> service.withdrawByCustomer("testuser", "ORD-001"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("No pending cancellation request found");
    }

    @Test
    void hasPendingRequest_delegatesToRepository() {
        when(cancellationRequestRepository.existsByOrderIdAndStatus(55L, CancellationRequestStatus.REQUESTED))
            .thenReturn(true);
        assertThat(service.hasPendingRequest(55L)).isTrue();
    }

    @Test
    void toDto_withOrderAndAllFields() {
        CustomerOrder order = createOrder("ORD-DTO", "user123");
        order.setPaymentMethod("cod");
        order.setPaymentStatus(PaymentStatus.PAID);
        order.setStatus(OrderStatus.CONFIRMED);

        OrderCancellationRequest req = new OrderCancellationRequest();
        req.setId(5L);
        req.setRequestNumber("CAN-DTO-001");
        req.setStatus(CancellationRequestStatus.APPROVED);
        req.setReason("Changed mind");
        req.setAdminNotes("Some notes");
        req.setRejectionReason(null);
        req.setRazorpayRefundId("rfnd_001");
        req.setRequestedAt(Instant.now());
        req.setResolvedAt(Instant.now());
        req.setOrder(order);
        req.setUserLogin("user123");
        req.setRefundAmount(new BigDecimal("1500.00"));
        req.setManualRefundMethod(ManualRefundMethod.UPI);
        req.setManualRefundReference("UPI-12345");
        req.setManualRefundRecordedAt(Instant.now());
        req.setManualRefundRecordedBy("admin");

        Map<String, Object> dto = service.toDto(req);
        assertThat(dto.get("id")).isEqualTo(5L);
        assertThat(dto.get("request_number")).isEqualTo("CAN-DTO-001");
        assertThat(dto.get("status")).isEqualTo("approved");
        assertThat(dto.get("order_number")).isEqualTo("ORD-DTO");
        assertThat(dto.get("customer_login")).isEqualTo("user123");
        assertThat(dto.get("cod_refund_pending")).isEqualTo(false);
        assertThat(dto.get("manual_refund_method")).isEqualTo("upi");
        assertThat(dto.get("manual_refund_reference")).isEqualTo("UPI-12345");
    }

    @Test
    void toDto_withOrderNull() {
        OrderCancellationRequest req = new OrderCancellationRequest();
        req.setId(10L);
        req.setRequestNumber("CAN-NULL");
        req.setStatus(CancellationRequestStatus.REQUESTED);
        req.setReason("Reason");

        Map<String, Object> dto = service.toDto(req);
        assertThat(dto.get("id")).isEqualTo(10L);
        assertThat(dto.get("order_id")).isNull();
        assertThat(dto.get("order_number")).isNull();
    }

    @Test
    void constructorWithoutMailService() {
        OrderCancellationService noMail = new OrderCancellationService(
            cancellationRequestRepository,
            customerOrderRepository,
            cancellationExecutor,
            null,
            null,
            null
        );
        assertThat(noMail).isNotNull();
    }

    @Test
    void mailService_exceptionsDuringApproveRejectWithdraw_areCaught() {
        CustomerOrder order = createOrder("ORD-MAIL", "user");
        order.setStatus(OrderStatus.CONFIRMED);

        OrderCancellationRequest req = new OrderCancellationRequest();
        req.setRequestNumber("CAN-MAIL");
        req.setStatus(CancellationRequestStatus.REQUESTED);
        req.setReason("Reason");
        req.setOrder(order);

        when(cancellationRequestRepository.findOneByRequestNumber("CAN-MAIL")).thenReturn(Optional.of(req));
        when(cancellationRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // 1. Approve with mail failure
        org.mockito.Mockito.doThrow(new RuntimeException("mail failed"))
            .when(orderMailService).sendCancellationAccepted(any(), any());
        OrderCancellationRequest approved = service.approve("CAN-MAIL", null);
        assertThat(approved.getStatus()).isEqualTo(CancellationRequestStatus.APPROVED);
        assertThat(approved.getAdminNotes()).isNull(); // tests trimNotes(null)

        // 2. Reject with mail failure
        req.setStatus(CancellationRequestStatus.REQUESTED);
        org.mockito.Mockito.doThrow(new RuntimeException("mail failed"))
            .when(orderMailService).sendCancellationRejected(any(), any());
        OrderCancellationRequest rejected = service.reject("CAN-MAIL", "Valid reason", null);
        assertThat(rejected.getStatus()).isEqualTo(CancellationRequestStatus.REJECTED);
    }

    @Test
    void helperMethods_coverage() throws Exception {
        java.lang.reflect.Method mMoney = OrderCancellationService.class.getDeclaredMethod("money", BigDecimal.class);
        mMoney.setAccessible(true);
        assertThat((BigDecimal) mMoney.invoke(null, (Object) null)).isEqualByComparingTo(BigDecimal.ZERO);

        java.lang.reflect.Method mGenReq = OrderCancellationService.class.getDeclaredMethod("generateRequestNumber", String.class);
        mGenReq.setAccessible(true);
        String reqNull = (String) mGenReq.invoke(null, (Object) null);
        assertThat(reqNull).startsWith("CAN-ORD-");
    }

    @Test
    void cancelOrderFromAdmin_createsApprovedRequestAndRefunds() {
        CustomerOrder order = createOrder("ORD-001", "testuser");
        when(customerOrderRepository.findOneByOrderNumberForUpdate("ORD-001")).thenReturn(Optional.of(order));
        when(customerOrderRepository.findById(101L)).thenReturn(Optional.of(order));
        when(cancellationExecutor.performCancellation(order, "Item is out of stock")).thenReturn("rfnd_admin");
        when(cancellationRequestRepository.findFirstByOrderIdAndStatusOrderByRequestedAtDesc(101L, CancellationRequestStatus.REQUESTED))
            .thenReturn(Optional.empty());
        when(cancellationRequestRepository.save(any(OrderCancellationRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        CustomerOrder cancelled = service.cancelOrderFromAdmin("ORD-001", "Item is out of stock", "admin.user", null);

        assertThat(cancelled).isEqualTo(order);
        verify(cancellationExecutor).assertCancelEligible(order);
        verify(cancellationExecutor).performCancellation(order, "Item is out of stock");
        verify(cancellationRequestRepository).save(argThat(req ->
            req.getStatus() == CancellationRequestStatus.APPROVED &&
            "Item is out of stock".equals(req.getReason()) &&
            "rfnd_admin".equals(req.getRazorpayRefundId()) &&
            req.getAdminNotes() != null &&
            req.getAdminNotes().contains("admin.user")
        ));
        verify(orderMailService).sendOrderCancelled(order);
        verify(orderSmsService).sendOrderCancelled(order);
        verify(orderMailService, never()).sendCancellationAccepted(any(), any());
        verify(orderMailService).sendRefundInitiated(eq(order), eq(order.getTotal()), eq("rfnd_admin"), eq("online"));
        verify(orderShipmentService, never()).cancelShipment(any(), any(), any(), any());
    }

    @Test
    void cancelOrderFromAdmin_mailAndSmsFailures_stillCancels() {
        CustomerOrder order = createOrder("ORD-001", "testuser");
        when(customerOrderRepository.findOneByOrderNumberForUpdate("ORD-001")).thenReturn(Optional.of(order));
        when(customerOrderRepository.findById(101L)).thenReturn(Optional.of(order));
        when(cancellationRequestRepository.findFirstByOrderIdAndStatusOrderByRequestedAtDesc(101L, CancellationRequestStatus.REQUESTED))
            .thenReturn(Optional.empty());
        when(cancellationRequestRepository.save(any(OrderCancellationRequest.class))).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new RuntimeException("smtp down")).when(orderMailService).sendOrderCancelled(any());
        doThrow(new RuntimeException("sms down")).when(orderSmsService).sendOrderCancelled(any());

        CustomerOrder cancelled = service.cancelOrderFromAdmin("ORD-001", "Unable to fulfil", "admin.user", null);

        assertThat(cancelled).isEqualTo(order);
        verify(cancellationRequestRepository).save(argThat(req -> req.getStatus() == CancellationRequestStatus.APPROVED));
    }

    @Test
    void cancelOrderFromAdmin_pendingRequestIsApproved() {
        CustomerOrder order = createOrder("ORD-001", "testuser");
        OrderCancellationRequest pending = new OrderCancellationRequest();
        pending.setRequestNumber("CAN-EXISTING");
        pending.setStatus(CancellationRequestStatus.REQUESTED);
        pending.setReason("Customer changed mind");
        pending.setOrder(order);
        pending.setUserLogin("testuser");

        when(customerOrderRepository.findOneByOrderNumberForUpdate("ORD-001")).thenReturn(Optional.of(order));
        when(customerOrderRepository.findById(101L)).thenReturn(Optional.of(order));
        when(cancellationRequestRepository.findFirstByOrderIdAndStatusOrderByRequestedAtDesc(101L, CancellationRequestStatus.REQUESTED))
            .thenReturn(Optional.of(pending));
        when(cancellationRequestRepository.save(any(OrderCancellationRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        service.cancelOrderFromAdmin("ORD-001", "Approved from orders screen", "ops.admin", null);

        assertThat(pending.getStatus()).isEqualTo(CancellationRequestStatus.APPROVED);
        assertThat(pending.getReason()).isEqualTo("Approved from orders screen");
        verify(cancellationRequestRepository).save(pending);
        verify(orderMailService).sendCancellationAccepted(eq(order), eq(pending));
        verify(orderMailService, never()).sendOrderCancelled(any());
        verify(orderSmsService, never()).sendOrderCancelled(any());
    }

    @Test
    void cancelOrderFromAdmin_orderNotFound_throwsNotFound() {
        when(customerOrderRepository.findOneByOrderNumberForUpdate("MISSING")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancelOrderFromAdmin("MISSING", "Valid reason here", "admin", null))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Order not found");
    }

    @Test
    void cancelOrderFromAdmin_withBookedLabel_voidsAwbBeforeCancellingOrder() {
        CustomerOrder order = createOrder("ORD-001", "testuser");
        order.setTrackingNumber("XB-1");
        order.setShipmentLogisticsMode(com.miniandmore.app.domain.enumeration.ReturnLogisticsMode.XPRESSBEES);
        CustomerOrder afterVoid = createOrder("ORD-001", "testuser");
        when(customerOrderRepository.findOneByOrderNumberForUpdate("ORD-001")).thenReturn(Optional.of(order), Optional.of(afterVoid));
        when(customerOrderRepository.findById(101L)).thenReturn(Optional.of(afterVoid));
        when(cancellationExecutor.performCancellation(eq(afterVoid), eq("Unable to fulfil"))).thenReturn(null);
        when(cancellationRequestRepository.findFirstByOrderIdAndStatusOrderByRequestedAtDesc(101L, CancellationRequestStatus.REQUESTED))
            .thenReturn(Optional.empty());
        when(cancellationRequestRepository.save(any(OrderCancellationRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        service.cancelOrderFromAdmin("ORD-001", "Unable to fulfil", "ops.admin", null);

        InOrder inOrder = inOrder(orderShipmentService, cancellationExecutor);
        inOrder
            .verify(orderShipmentService)
            .cancelShipment(
                eq("ORD-001"),
                eq(com.miniandmore.app.domain.enumeration.ReturnLogisticsMode.XPRESSBEES),
                eq("Unable to fulfil"),
                eq("ops.admin")
            );
        inOrder.verify(cancellationExecutor).assertCancelEligible(afterVoid);
        inOrder.verify(cancellationExecutor).performCancellation(eq(afterVoid), eq("Unable to fulfil"));
    }

    @Test
    void cancelOrderFromAdmin_carrierVoidFails_orderNotCancelled() {
        CustomerOrder order = createOrder("ORD-001", "testuser");
        order.setTrackingNumber("XB-1");
        order.setShipmentLogisticsMode(com.miniandmore.app.domain.enumeration.ReturnLogisticsMode.XPRESSBEES);
        when(customerOrderRepository.findOneByOrderNumberForUpdate("ORD-001")).thenReturn(Optional.of(order));
        when(orderShipmentService.cancelShipment(any(), any(), any(), any()))
            .thenThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Carrier timeout"));

        assertThatThrownBy(() -> service.cancelOrderFromAdmin("ORD-001", "Unable to fulfil", "ops.admin", null))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> {
                ResponseStatusException rse = (ResponseStatusException) ex;
                assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                assertThat(rse.getReason()).contains("was not cancelled");
            });
        verify(cancellationExecutor, never()).performCancellation(any(), any());
    }

    @Test
    void cancelOrderFromAdmin_carrierAlreadyCancelled_clearsLocallyWithoutXpressBeesMode() {
        CustomerOrder order = createOrder("ORD-001", "testuser");
        order.setTrackingNumber("XB-1");
        order.setShipmentLogisticsMode(com.miniandmore.app.domain.enumeration.ReturnLogisticsMode.XPRESSBEES);
        order.setShipmentCarrierStatus("carrier_cancelled");
        CustomerOrder afterVoid = createOrder("ORD-001", "testuser");
        when(customerOrderRepository.findOneByOrderNumberForUpdate("ORD-001")).thenReturn(Optional.of(order), Optional.of(afterVoid));
        when(customerOrderRepository.findById(101L)).thenReturn(Optional.of(afterVoid));
        when(cancellationExecutor.performCancellation(eq(afterVoid), eq("Courier voided AWB"))).thenReturn("rfnd_1");
        when(cancellationRequestRepository.findFirstByOrderIdAndStatusOrderByRequestedAtDesc(101L, CancellationRequestStatus.REQUESTED))
            .thenReturn(Optional.empty());
        when(cancellationRequestRepository.save(any(OrderCancellationRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        // Mode resolution must force MANUAL for carrier_cancelled even when client asks for xpressbees.
        service.cancelOrderFromAdmin("ORD-001", "Courier voided AWB", "ops.admin", "xpressbees");

        verify(orderShipmentService)
            .cancelShipment(
                eq("ORD-001"),
                eq(com.miniandmore.app.domain.enumeration.ReturnLogisticsMode.MANUAL),
                eq("Courier voided AWB"),
                eq("ops.admin")
            );
        verify(cancellationExecutor).performCancellation(eq(afterVoid), eq("Courier voided AWB"));
    }

    @Test
    void canCancel_delegatesToExecutor() {
        CustomerOrder order = createOrder("ORD-001", "testuser");
        when(cancellationExecutor.isCancelEligible(order)).thenReturn(true);

        assertThat(service.canCancel(order)).isTrue();
        verify(cancellationExecutor).isCancelEligible(order);
    }

    @Test
    void canCustomerRequestCancel_trueAfterHandWrapWhenEligible() {
        CustomerOrder packed = createOrder("ORD-001", "testuser");
        packed.setStatus(OrderStatus.HAND_WRAPPED);
        when(cancellationExecutor.isCancelEligible(packed)).thenReturn(true);

        assertThat(service.canCustomerRequestCancel(packed)).isTrue();
    }

    @Test
    void shouldContactCareToCancel_falseWhenPacked() {
        CustomerOrder packed = createOrder("ORD-001", "testuser");
        packed.setStatus(OrderStatus.HAND_WRAPPED);

        assertThat(service.shouldContactCareToCancel(packed)).isFalse();
    }

    @Test
    void shouldContactCareToCancel_alwaysFalse() {
        CustomerOrder order = createOrder("ORD-001", "testuser");

        assertThat(service.shouldContactCareToCancel(order)).isFalse();
    }

    @Test
    void canCustomerShowCancelAction_falseWhenPendingRequest() {
        CustomerOrder order = createOrder("ORD-001", "testuser");
        when(cancellationRequestRepository.existsByOrderIdAndStatus(101L, CancellationRequestStatus.REQUESTED)).thenReturn(true);

        assertThat(service.canCustomerShowCancelAction(order)).isFalse();
    }
}
