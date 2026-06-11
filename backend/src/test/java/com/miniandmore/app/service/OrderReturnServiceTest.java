package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.miniandmore.app.client.ZohoBooksIntegrationClient;
import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.OrderItem;
import com.miniandmore.app.domain.OrderReturn;
import com.miniandmore.app.domain.Product;
import com.miniandmore.app.domain.enumeration.OrderReturnStatus;
import com.miniandmore.app.domain.enumeration.OrderStatus;
import com.miniandmore.app.domain.enumeration.PaymentStatus;
import com.miniandmore.app.repository.CustomerOrderRepository;
import com.miniandmore.app.repository.OrderItemRepository;
import com.miniandmore.app.repository.OrderReturnRepository;
import com.miniandmore.app.repository.ProductRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class OrderReturnServiceTest {

    private static final String USER = "customer1";
    private static final String ORDER_NUMBER = "ORD-9001";

    @Mock
    private OrderReturnRepository orderReturnRepository;

    @Mock
    private CustomerOrderRepository customerOrderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private RazorpayRefundService razorpayRefundService;

    @Mock
    private ZohoBooksIntegrationClient zohoBooksIntegrationClient;

    @InjectMocks
    private OrderReturnService orderReturnService;

    @Test
    void requestReturn_shortReason_throwsBadRequest() {
        assertThatThrownBy(() -> orderReturnService.requestReturn(USER, ORDER_NUMBER, "too short"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("at least 10 characters");
    }

    @Test
    void requestReturn_orderNotFound_throwsNotFound() {
        when(customerOrderRepository.findOneByOrderNumberAndUserLogin(ORDER_NUMBER, USER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderReturnService.requestReturn(USER, ORDER_NUMBER, "Product arrived damaged"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Order not found");
    }

    @Test
    void requestReturn_eligibleOrder_persistsReturn() {
        CustomerOrder order = eligibleOrder();
        when(customerOrderRepository.findOneByOrderNumberAndUserLogin(ORDER_NUMBER, USER)).thenReturn(Optional.of(order));
        when(orderReturnRepository.existsByOrderIdAndStatusIn(eq(order.getId()), any())).thenReturn(false);
        when(orderReturnRepository.save(any(OrderReturn.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderReturn result = orderReturnService.requestReturn(USER, ORDER_NUMBER, "Product arrived damaged in transit");

        assertThat(result.getStatus()).isEqualTo(OrderReturnStatus.REQUESTED);
        assertThat(result.getUserLogin()).isEqualTo(USER);
        assertThat(result.getOrder()).isSameAs(order);
        assertThat(result.getReturnNumber()).startsWith("RET-" + ORDER_NUMBER);
        verify(orderReturnRepository).save(any(OrderReturn.class));
    }

    @Test
    void cancelByCustomer_onlyRequestedStatusAllowed() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.APPROVED);
        orderReturn.setUserLogin(USER);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));

        assertThatThrownBy(() -> orderReturnService.cancelByCustomer(USER, "RET-1"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Only pending return requests");
    }

    @Test
    void cancelByCustomer_requestedStatus_cancelsReturn() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.REQUESTED);
        orderReturn.setUserLogin(USER);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);

        OrderReturn result = orderReturnService.cancelByCustomer(USER, "RET-1");

        assertThat(result.getStatus()).isEqualTo(OrderReturnStatus.CANCELLED);
        assertThat(result.getResolvedAt()).isNotNull();
    }

    @Test
    void approve_requestedReturn_movesToApproved() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.REQUESTED);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);

        OrderReturn result = orderReturnService.approve("RET-1", "Approved by admin");

        assertThat(result.getStatus()).isEqualTo(OrderReturnStatus.APPROVED);
        assertThat(result.getAdminNotes()).isEqualTo("Approved by admin");
    }

    @Test
    void eligibilityForUser_orderNotFound() {
        when(customerOrderRepository.findOneByOrderNumberAndUserLogin(ORDER_NUMBER, USER)).thenReturn(Optional.empty());

        Map<String, Object> result = orderReturnService.eligibilityForUser(USER, ORDER_NUMBER);

        assertThat(result.get("eligible")).isEqualTo(false);
        assertThat(result.get("message")).isEqualTo("Order not found");
    }

    @Test
    void eligibilityForUser_paidDeliveredOrder_isEligible() {
        CustomerOrder order = eligibleOrder();
        when(customerOrderRepository.findOneByOrderNumberAndUserLogin(ORDER_NUMBER, USER)).thenReturn(Optional.of(order));

        Map<String, Object> result = orderReturnService.eligibilityForUser(USER, ORDER_NUMBER);

        assertThat(result.get("eligible")).isEqualTo(true);
    }

    @Test
    void resolveDeliveryAnchor_usesShipmentDate() {
        CustomerOrder order = eligibleOrder().shipmentDate(LocalDate.now().toString());

        Instant anchor = orderReturnService.resolveDeliveryAnchor(order);

        assertThat(anchor).isNotNull();
    }

    @Test
    void listForAdmin_withoutFilter_returnsAllStatuses() {
        List<OrderReturn> returns = List.of(returnWithStatus(OrderReturnStatus.REQUESTED));
        when(orderReturnRepository.findAllByStatusInOrderByRequestedAtDesc(any())).thenReturn(returns);

        List<OrderReturn> result = orderReturnService.listForAdmin(null);

        assertThat(result).isEqualTo(returns);
        verify(orderReturnRepository).findAllByStatusInOrderByRequestedAtDesc(List.of(OrderReturnStatus.values()));
    }

    @Test
    void listForAdmin_withStatusFilter() {
        List<OrderReturn> returns = List.of(returnWithStatus(OrderReturnStatus.APPROVED));
        when(orderReturnRepository.findAllByStatusInOrderByRequestedAtDesc(List.of(OrderReturnStatus.APPROVED))).thenReturn(returns);

        List<OrderReturn> result = orderReturnService.listForAdmin(OrderReturnStatus.APPROVED);

        assertThat(result).isEqualTo(returns);
    }

    @Test
    void reject_requestedReturn_movesToRejected() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.REQUESTED);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);

        OrderReturn result = orderReturnService.reject("RET-1", "Item not eligible", "Admin note");

        assertThat(result.getStatus()).isEqualTo(OrderReturnStatus.REJECTED);
        assertThat(result.getRejectionReason()).isEqualTo("Item not eligible");
    }

    @Test
    void markReceived_approvedReturn_movesToReceived() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.APPROVED);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);

        OrderReturn result = orderReturnService.markReceived("RET-1", "Received at warehouse");

        assertThat(result.getStatus()).isEqualTo(OrderReturnStatus.RECEIVED);
    }

    @Test
    void processRefund_demoMode_marksRefunded() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.RECEIVED);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(razorpayRefundService.refundPayment(any(), any())).thenReturn("rfnd_demo_ORD-9001");
        when(orderItemRepository.findAllByOrderId(10L)).thenReturn(List.of());
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);
        when(customerOrderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderReturn result = orderReturnService.processRefund("RET-1", "Refunded");

        assertThat(result.getStatus()).isEqualTo(OrderReturnStatus.REFUNDED);
        assertThat(result.getRazorpayRefundId()).isEqualTo("rfnd_demo_ORD-9001");
    }

    @Test
    void assertReturnEligible_unpaidOrder_throwsBadRequest() {
        CustomerOrder order = eligibleOrder().paymentStatus(PaymentStatus.PENDING);

        assertThatThrownBy(() -> orderReturnService.assertReturnEligible(order))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Only paid orders");
    }

    @Test
    void listForUser_returnsUserReturns() {
        List<OrderReturn> returns = List.of(returnWithStatus(OrderReturnStatus.REQUESTED));
        when(orderReturnRepository.findAllByUserLoginOrderByRequestedAtDesc(USER)).thenReturn(returns);

        assertThat(orderReturnService.listForUser(USER)).isEqualTo(returns);
    }

    @Test
    void requestReturn_openReturnExists_throwsConflict() {
        CustomerOrder order = eligibleOrder();
        when(customerOrderRepository.findOneByOrderNumberAndUserLogin(ORDER_NUMBER, USER)).thenReturn(java.util.Optional.of(order));
        when(orderReturnRepository.existsByOrderIdAndStatusIn(eq(order.getId()), any())).thenReturn(true);

        assertThatThrownBy(() -> orderReturnService.requestReturn(USER, ORDER_NUMBER, "Product arrived damaged in transit"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("already open");
    }

    @Test
    void reject_shortReason_throwsBadRequest() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.REQUESTED);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(java.util.Optional.of(orderReturn));

        assertThatThrownBy(() -> orderReturnService.reject("RET-1", "no", null))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Rejection reason");
    }

    @Test
    void processRefund_zohoFailure_stillRefunds() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.RECEIVED);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(java.util.Optional.of(orderReturn));
        when(razorpayRefundService.refundPayment(any(), any())).thenReturn("rfnd_demo_ORD-9001");
        when(orderItemRepository.findAllByOrderId(10L)).thenReturn(List.of());
        when(zohoBooksIntegrationClient.createCreditNote(any(), any())).thenThrow(new RuntimeException("zoho down"));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);
        when(customerOrderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderReturn result = orderReturnService.processRefund("RET-1", null);

        assertThat(result.getStatus()).isEqualTo(OrderReturnStatus.REFUNDED);
    }

    @Test
    void processRefund_existingRefundId_skipsRazorpay() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.FAILED);
        orderReturn.setRazorpayRefundId("rfnd_existing");
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(orderItemRepository.findAllByOrderId(10L)).thenReturn(List.of());
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);
        when(customerOrderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderReturn result = orderReturnService.processRefund("RET-1", "Retry");

        assertThat(result.getStatus()).isEqualTo(OrderReturnStatus.REFUNDED);
    }

    @Test
    void restoreInventory_incrementsProductStock() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.RECEIVED);
        OrderItem item = new OrderItem().productId("1").qty(2);
        Product product = new Product().id(1L).stock(5);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(razorpayRefundService.refundPayment(any(), any())).thenReturn("rfnd_demo");
        when(orderItemRepository.findAllByOrderId(10L)).thenReturn(List.of(item));
        when(productRepository.findOneForInventoryUpdate(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(product)).thenReturn(product);
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);
        when(customerOrderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        orderReturnService.processRefund("RET-1", null);

        assertThat(product.getStock()).isEqualTo(7);
    }

    @Test
    void approve_wrongStatus_throwsBadRequest() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.APPROVED);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));

        assertThatThrownBy(() -> orderReturnService.approve("RET-1", null))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("not awaiting approval");
    }

    @Test
    void markReceived_wrongStatus_throwsBadRequest() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.REQUESTED);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));

        assertThatThrownBy(() -> orderReturnService.markReceived("RET-1", null))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("approved before marking received");
    }

    @Test
    void eligibilityForUser_notDeliveredYet_notEligible() {
        CustomerOrder order = eligibleOrder().shipmentDate(null).status(OrderStatus.CONFIRMED);
        when(customerOrderRepository.findOneByOrderNumberAndUserLogin(ORDER_NUMBER, USER)).thenReturn(Optional.of(order));

        Map<String, Object> result = orderReturnService.eligibilityForUser(USER, ORDER_NUMBER);

        assertThat(result.get("eligible")).isEqualTo(false);
    }

    @Test
    void processRefund_wrongStatus_throwsBadRequest() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.REQUESTED);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));

        assertThatThrownBy(() -> orderReturnService.processRefund("RET-1", null))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("received");
    }

    @Test
    void resolveDeliveryAnchor_withoutShipmentDate_usesZohoTrackingPull() {
        CustomerOrder order = eligibleOrder()
            .shipmentDate(null)
            .status(OrderStatus.FULFILLED)
            .zohoTrackingLastPulledAt(Instant.parse("2026-01-05T00:00:00Z"));

        Instant anchor = orderReturnService.resolveDeliveryAnchor(order);

        assertThat(anchor).isEqualTo(Instant.parse("2026-01-05T00:00:00Z"));
    }

    @Test
    void processRefund_razorpayFailure_marksFailedAndRethrows() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.RECEIVED);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(razorpayRefundService.refundPayment(any(), any())).thenThrow(new RuntimeException("gateway down"));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);

        assertThatThrownBy(() -> orderReturnService.processRefund("RET-1", "note"))
            .isInstanceOf(RuntimeException.class);

        assertThat(orderReturn.getStatus()).isEqualTo(OrderReturnStatus.FAILED);
    }

    @Test
    void markReceived_withExistingNotes_mergesNotes() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.APPROVED);
        orderReturn.setAdminNotes("Existing");
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);

        OrderReturn result = orderReturnService.markReceived("RET-1", "Received");

        assertThat(result.getAdminNotes()).contains("Existing").contains("Received");
    }

    @Test
    void reject_longReason_truncatesToMaxLength() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.REQUESTED);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);

        String reason = "R".repeat(2000);
        OrderReturn result = orderReturnService.reject("RET-1", reason, null);

        assertThat(result.getRejectionReason()).hasSize(1000);
    }

    @Test
    void cancelByCustomer_wrongUser_throwsNotFound() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.REQUESTED);
        orderReturn.setUserLogin("other");
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));

        assertThatThrownBy(() -> orderReturnService.cancelByCustomer(USER, "RET-1"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("not found");
    }

    @Test
    void assertReturnEligible_expiredWindow_throwsBadRequest() {
        CustomerOrder order = eligibleOrder().shipmentDate(LocalDate.now().minusDays(30).toString());

        assertThatThrownBy(() -> orderReturnService.assertReturnEligible(order))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Return window has expired");
    }

    @Test
    void assertReturnEligible_cancelledOrder_throwsBadRequest() {
        CustomerOrder order = eligibleOrder().status(OrderStatus.CANCELLED);

        assertThatThrownBy(() -> orderReturnService.assertReturnEligible(order))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Cancelled orders");
    }

    @Test
    void requestReturn_veryLongReason_truncatesReason() {
        CustomerOrder order = eligibleOrder();
        when(customerOrderRepository.findOneByOrderNumberAndUserLogin(ORDER_NUMBER, USER)).thenReturn(Optional.of(order));
        when(orderReturnRepository.existsByOrderIdAndStatusIn(eq(order.getId()), any())).thenReturn(false);
        when(orderReturnRepository.save(any(OrderReturn.class))).thenAnswer(inv -> inv.getArgument(0));

        String longReason = "x".repeat(5000);
        OrderReturn result = orderReturnService.requestReturn(USER, ORDER_NUMBER, longReason);

        assertThat(result.getReason()).hasSize(4000);
    }

    @Test
    void processRefund_orderAlreadyRefunded_skipsInventoryRestore() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.RECEIVED);
        orderReturn.getOrder().setPaymentStatus(PaymentStatus.REFUNDED);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(razorpayRefundService.refundPayment(any(), any())).thenReturn("rfnd_demo");
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);

        orderReturnService.processRefund("RET-1", "done");

        verify(productRepository, org.mockito.Mockito.never()).save(any());
    }

    private CustomerOrder eligibleOrder() {
        return new CustomerOrder()
            .id(10L)
            .userLogin(USER)
            .orderNumber(ORDER_NUMBER)
            .status(OrderStatus.FULFILLED)
            .paymentStatus(PaymentStatus.PAID)
            .subtotal(BigDecimal.TEN)
            .discount(BigDecimal.ZERO)
            .shipping(BigDecimal.ZERO)
            .tax(BigDecimal.ZERO)
            .donation(BigDecimal.ZERO)
            .total(BigDecimal.TEN)
            .shippingAddressJson("{}")
            .shipmentDate(LocalDate.now().toString());
    }

    private OrderReturn returnWithStatus(OrderReturnStatus status) {
        OrderReturn orderReturn = new OrderReturn();
        orderReturn.setReturnNumber("RET-1");
        orderReturn.setStatus(status);
        orderReturn.setReason("Product arrived damaged in transit");
        orderReturn.setRequestedAt(Instant.now());
        orderReturn.setOrder(eligibleOrder());
        return orderReturn;
    }
}
