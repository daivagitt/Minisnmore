package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.miniandmore.app.client.LedgerlyIntegrationClient;
import com.miniandmore.app.client.XpressBeesClient;
import com.miniandmore.app.client.ZohoBooksIntegrationClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.config.ApplicationProperties;
import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.OrderItem;
import com.miniandmore.app.domain.OrderReturn;
import com.miniandmore.app.domain.OrderReturnItem;
import com.miniandmore.app.domain.Product;
import com.miniandmore.app.domain.enumeration.ExchangeSettlementStatus;
import com.miniandmore.app.domain.enumeration.OrderRequestType;
import com.miniandmore.app.domain.enumeration.OrderReturnStatus;
import com.miniandmore.app.domain.enumeration.OrderStatus;
import com.miniandmore.app.domain.enumeration.PaymentStatus;
import com.miniandmore.app.domain.enumeration.ReturnDisposition;
import com.miniandmore.app.domain.enumeration.ReturnLogisticsMode;
import com.miniandmore.app.repository.CustomerOrderRepository;
import com.miniandmore.app.repository.OrderItemRepository;
import com.miniandmore.app.repository.OrderReturnItemRepository;
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
import org.mockito.Spy;
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
    private OrderReturnItemRepository orderReturnItemRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private RazorpayRefundService razorpayRefundService;

    @Mock
    private LedgerlyIntegrationClient ledgerlyIntegrationClient;

    @Mock
    private ZohoBooksIntegrationClient zohoBooksIntegrationClient;

    @Spy
    private ProductVariantSupport variantSupport = StorePricingTestSupport.variantSupport(new com.fasterxml.jackson.databind.ObjectMapper());

    @Mock
    private OrderMailService orderMailService;

    @Mock
    private OrderSmsService orderSmsService;

    @Mock
    private ApplicationProperties applicationProperties;

    @Mock
    private XpressBeesClient xpressBeesClient;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private OrderReturnService orderReturnService;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        lenient().when(applicationProperties.getZoho()).thenReturn(new ApplicationProperties.Zoho());
        lenient().when(applicationProperties.getLedgerly()).thenReturn(new ApplicationProperties.Ledgerly());
        lenient().when(applicationProperties.getXpressbees()).thenReturn(new ApplicationProperties.XpressBees());
        lenient().when(objectMapper.createObjectNode()).thenReturn(new ObjectMapper().createObjectNode());
    }

    private void enableLedgerlyReturnsSync() {
        ApplicationProperties.Ledgerly ledgerly = new ApplicationProperties.Ledgerly();
        ledgerly.setSyncReturns(true);
        when(applicationProperties.getLedgerly()).thenReturn(ledgerly);
    }

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
        verify(orderMailService).sendReturnUpdate(eq(order), any(OrderReturn.class));
    }

    @Test
    void requestReturn_mailFailure_stillPersists() {
        CustomerOrder order = eligibleOrder();
        when(customerOrderRepository.findOneByOrderNumberAndUserLogin(ORDER_NUMBER, USER)).thenReturn(Optional.of(order));
        when(orderReturnRepository.existsByOrderIdAndStatusIn(eq(order.getId()), any())).thenReturn(false);
        when(orderReturnRepository.save(any(OrderReturn.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new RuntimeException("smtp down")).when(orderMailService).sendReturnUpdate(any(), any());

        OrderReturn result = orderReturnService.requestReturn(USER, ORDER_NUMBER, "Product arrived damaged in transit");

        assertThat(result.getStatus()).isEqualTo(OrderReturnStatus.REQUESTED);
    }

    @Test
    void cancelByCustomer_nullOrder_skipsEmail() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.REQUESTED);
        orderReturn.setUserLogin(USER);
        orderReturn.setOrder(null);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);

        orderReturnService.cancelByCustomer(USER, "RET-1");

        verify(orderMailService, never()).sendReturnUpdate(any(), any());
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
        verify(orderMailService).sendReturnUpdate(any(), eq(orderReturn));
    }

    @Test
    void approve_requestedReturn_movesToApproved() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.REQUESTED);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);

        OrderReturn result = orderReturnService.approve("RET-1", "Approved by admin");

        assertThat(result.getStatus()).isEqualTo(OrderReturnStatus.APPROVED);
        assertThat(result.getAdminNotes()).isEqualTo("Approved by admin");
        verify(orderMailService).sendReturnUpdate(any(), eq(orderReturn));
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
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.ACCEPTED);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(razorpayRefundService.refundPayment(any(), any())).thenReturn("rfnd_demo_ORD-9001");
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);
        when(customerOrderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderReturn result = orderReturnService.processRefund("RET-1", "Refunded");

        assertThat(result.getStatus()).isEqualTo(OrderReturnStatus.REFUNDED);
        assertThat(result.getRazorpayRefundId()).isEqualTo("rfnd_demo_ORD-9001");
    }

    @Test
    void processRefund_zohoSyncReturnsDisabledByDefault_neverCallsZoho() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.ACCEPTED);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(razorpayRefundService.refundPayment(any(), any())).thenReturn("rfnd_demo_ORD-9001");
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);
        when(customerOrderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        orderReturnService.processRefund("RET-1", "Refunded");

        verify(zohoBooksIntegrationClient, never()).createCreditNote(any(), any());
    }

    @Test
    void processRefund_ledgerlySyncReturnsDisabledByDefault_neverCallsLedgerly() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.ACCEPTED);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(razorpayRefundService.refundPayment(any(), any())).thenReturn("rfnd_demo_ORD-9001");
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);
        when(customerOrderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderReturn result = orderReturnService.processRefund("RET-1", "Refunded");

        verify(ledgerlyIntegrationClient, never()).cancelOrder(any(), any(), any());
        assertThat(result.getStatus()).isEqualTo(OrderReturnStatus.REFUNDED);
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
    void processRefund_ledgerlySuccess_setsCreditNoteId() {
        enableLedgerlyReturnsSync();
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.ACCEPTED);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(razorpayRefundService.refundPayment(any(), any())).thenReturn("rfnd_demo_ORD-9001");
        when(ledgerlyIntegrationClient.cancelOrder(eq(ORDER_NUMBER), eq("RET-1"), any())).thenReturn(Optional.of("cn-9001"));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);
        when(customerOrderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderReturn result = orderReturnService.processRefund("RET-1", null);

        assertThat(result.getStatus()).isEqualTo(OrderReturnStatus.REFUNDED);
        assertThat(result.getLedgerlyCreditNoteId()).isEqualTo("cn-9001");
        verify(ledgerlyIntegrationClient).cancelOrder(eq(ORDER_NUMBER), eq("RET-1"), any());
    }

    @Test
    void processRefund_existingCreditNoteId_skipsLedgerlyCall() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.ACCEPTED);
        orderReturn.setLedgerlyCreditNoteId("cn-existing");
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(razorpayRefundService.refundPayment(any(), any())).thenReturn("rfnd_demo");
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);
        when(customerOrderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        orderReturnService.processRefund("RET-1", null);

        verify(ledgerlyIntegrationClient, never()).cancelOrder(any(), any(), any());
        verify(ledgerlyIntegrationClient, never()).createCreditNote(any(), any(), any());
    }

    @Test
    void processRefund_ledgerlyFailure_stillRefunds() {
        enableLedgerlyReturnsSync();
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.ACCEPTED);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(java.util.Optional.of(orderReturn));
        when(razorpayRefundService.refundPayment(any(), any())).thenReturn("rfnd_demo_ORD-9001");
        when(ledgerlyIntegrationClient.cancelOrder(any(), any(), any())).thenThrow(new RuntimeException("ledgerly down"));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);
        when(customerOrderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderReturn result = orderReturnService.processRefund("RET-1", null);

        assertThat(result.getStatus()).isEqualTo(OrderReturnStatus.REFUNDED);
    }

    @Test
    void processRefund_ledgerlyEmptyResponse_demoOrder_stubsCreditNoteId() {
        enableLedgerlyReturnsSync();
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.ACCEPTED);
        CustomerOrder order = orderReturn.getOrder();
        order.setRazorpayPaymentId("pay_demo_scenario_123");
        order.setRazorpayOrderId("order_demo_ORD-9001");
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(razorpayRefundService.isDemoMode()).thenReturn(true);
        when(razorpayRefundService.refundPayment(any(), any())).thenReturn("rfnd_demo_ORD-9001");
        when(ledgerlyIntegrationClient.cancelOrder(any(), any(), any())).thenReturn(Optional.empty());
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);
        when(customerOrderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderReturn result = orderReturnService.processRefund("RET-1", null);

        assertThat(result.getLedgerlyCreditNoteId()).isEqualTo("cn_demo_" + ORDER_NUMBER);
    }

    @Test
    void processRefund_existingRefundId_skipsRazorpay() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.FAILED);
        orderReturn.setRazorpayRefundId("rfnd_existing");
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);
        when(customerOrderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderReturn result = orderReturnService.processRefund("RET-1", "Retry");

        assertThat(result.getStatus()).isEqualTo(OrderReturnStatus.REFUNDED);
    }

    @Test
    void acceptWithRestockableDisposition_incrementsProductStock() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.INSPECTED);
        OrderItem item = new OrderItem().productId("1").qty(2);
        Product product = new Product().id(1L).stock(5);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(orderItemRepository.findAllByOrderId(10L)).thenReturn(List.of(item));
        when(productRepository.findOneForInventoryUpdate(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(product)).thenReturn(product);
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);

        orderReturnService.acceptAfterInspection("RET-1", ReturnDisposition.RESTOCKABLE, null);

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
            .hasMessageContaining("before marking received");
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
            .hasMessageContaining("accepted");
    }

    @Test
    void resolveDeliveryAnchor_withoutShipmentDate_usesLedgerlyTrackingPull() {
        CustomerOrder order = eligibleOrder()
            .shipmentDate(null)
            .status(OrderStatus.FULFILLED)
            .ledgerlyTrackingLastPulledAt(Instant.parse("2026-01-05T00:00:00Z"));

        Instant anchor = orderReturnService.resolveDeliveryAnchor(order);

        assertThat(anchor).isEqualTo(Instant.parse("2026-01-05T00:00:00Z"));
    }

    @Test
    void processRefund_razorpayFailure_marksFailedAndRethrows() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.ACCEPTED);
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
    void acceptWithRestockable_variantProduct_incrementsVariantStock() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.INSPECTED);
        String variantsJson =
            "[{\"sku\":\"TEE-S-RED\",\"size\":\"S\",\"color\":\"Red\",\"rate\":500,\"stock\":1,\"active\":true}]";
        OrderItem item = new OrderItem().productId("2").qty(1).variantSku("TEE-S-RED").itemSize("S").color("Red");
        Product product = new Product().id(2L).stock(1).itemStructure("VARIANTS").variantsJson(variantsJson);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(orderItemRepository.findAllByOrderId(10L)).thenReturn(List.of(item));
        when(productRepository.findOneForInventoryUpdate(2L)).thenReturn(Optional.of(product));
        when(productRepository.save(product)).thenReturn(product);
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);

        orderReturnService.acceptAfterInspection("RET-1", ReturnDisposition.RESTOCKABLE, null);

        verify(productRepository).save(product);
        assertThat(product.getStock()).isEqualTo(2);
    }

    @Test
    void resolveDeliveryAnchor_trackingNumberWithShipmentDate() {
        CustomerOrder order = eligibleOrder()
            .shipmentDate(LocalDate.now().minusDays(1).toString())
            .status(OrderStatus.CONFIRMED)
            .trackingNumber("AWB-123");

        Instant anchor = orderReturnService.resolveDeliveryAnchor(order);

        assertThat(anchor).isNotNull();
    }

    @Test
    void resolveDeliveryAnchor_fulfilledUsesLedgerlyLastSyncedAt() {
        CustomerOrder order = eligibleOrder()
            .shipmentDate(null)
            .status(OrderStatus.FULFILLED)
            .ledgerlyLastSyncedAt(Instant.parse("2026-01-10T00:00:00Z"));

        Instant anchor = orderReturnService.resolveDeliveryAnchor(order);

        assertThat(anchor).isEqualTo(Instant.parse("2026-01-10T00:00:00Z"));
    }

    @Test
    void processRefund_ledgerlyFailure_recordsAdminNote() {
        enableLedgerlyReturnsSync();
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.ACCEPTED);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(razorpayRefundService.refundPayment(any(), any())).thenReturn("rfnd_live_1");
        when(ledgerlyIntegrationClient.cancelOrder(any(), any(), any())).thenThrow(new RuntimeException("ledgerly 500"));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);
        when(customerOrderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderReturn result = orderReturnService.processRefund("RET-1", "done");

        assertThat(result.getStatus()).isEqualTo(OrderReturnStatus.REFUNDED);
        assertThat(result.getAdminNotes()).contains("Ledgerly order cancel failed");
    }

    @Test
    void approve_returnNotFound_throwsNotFound() {
        when(orderReturnRepository.findOneByReturnNumber("MISSING")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderReturnService.approve("MISSING", null))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("not found");
    }

    // ---------------------------------------------------------------------
    // Exchange request creation
    // ---------------------------------------------------------------------

    @Test
    void requestExchange_unavailableReplacement_throwsBadRequest() {
        CustomerOrder order = eligibleOrder();
        OrderItem item = new OrderItem().id(5L).productId("2").qty(1).itemSize("S").color("Red");
        Product product = new Product()
            .id(2L)
            .itemStructure("VARIANTS")
            .variantsJson("[{\"sku\":\"TEE-S-RED\",\"size\":\"S\",\"color\":\"Red\",\"rate\":500,\"stock\":0,\"active\":false}]");
        when(customerOrderRepository.findOneByOrderNumberAndUserLogin(ORDER_NUMBER, USER)).thenReturn(Optional.of(order));
        when(orderItemRepository.findByIdAndOrderId(5L, 10L)).thenReturn(Optional.of(item));
        when(productRepository.findById(2L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() ->
            orderReturnService.requestExchange(USER, ORDER_NUMBER, 5L, "Wrong size ordered by mistake", "M", "Blue")
        )
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("unavailable for exchange");
    }

    @Test
    void requestExchange_sellableReplacement_persistsExchange() {
        CustomerOrder order = eligibleOrder();
        OrderItem item = new OrderItem().id(5L).productId("2").qty(1).itemSize("S").color("Red");
        Product product = new Product()
            .id(2L)
            .itemStructure("VARIANTS")
            .variantsJson("[{\"sku\":\"TEE-M-BLUE\",\"size\":\"M\",\"color\":\"Blue\",\"rate\":500,\"stock\":3,\"active\":true}]");
        when(customerOrderRepository.findOneByOrderNumberAndUserLogin(ORDER_NUMBER, USER)).thenReturn(Optional.of(order));
        when(orderItemRepository.findByIdAndOrderId(5L, 10L)).thenReturn(Optional.of(item));
        when(productRepository.findById(2L)).thenReturn(Optional.of(product));
        when(orderReturnRepository.save(any(OrderReturn.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderReturn result = orderReturnService.requestExchange(USER, ORDER_NUMBER, 5L, "Wrong size ordered by mistake", "M", "Blue");

        assertThat(result.getRequestType()).isEqualTo(OrderRequestType.EXCHANGE);
        assertThat(result.getReplacementVariantSku()).isEqualTo("TEE-M-BLUE");
        assertThat(result.getOrderItem()).isSameAs(item);
        verify(orderMailService).sendReturnUpdate(eq(order), any(OrderReturn.class));
    }

    @Test
    void requestExchange_openReturnOnOrder_throwsConflict() {
        CustomerOrder order = eligibleOrder();
        OrderItem item = new OrderItem().id(5L).productId("2").qty(1).itemSize("S").color("Red");
        Product product = new Product()
            .id(2L)
            .itemStructure("VARIANTS")
            .variantsJson("[{\"sku\":\"TEE-M-BLUE\",\"size\":\"M\",\"color\":\"Blue\",\"rate\":500,\"stock\":3,\"active\":true}]");
        when(customerOrderRepository.findOneByOrderNumberAndUserLogin(ORDER_NUMBER, USER)).thenReturn(Optional.of(order));
        when(orderItemRepository.findByIdAndOrderId(5L, 10L)).thenReturn(Optional.of(item));
        when(productRepository.findById(2L)).thenReturn(Optional.of(product));
        when(orderReturnRepository.existsByOrderIdAndRequestTypeAndStatusIn(eq(10L), eq(OrderRequestType.RETURN), any())).thenReturn(true);

        assertThatThrownBy(() ->
            orderReturnService.requestExchange(USER, ORDER_NUMBER, 5L, "Wrong size ordered by mistake", "M", "Blue")
        )
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("open return already exists");
    }

    @Test
    void requestExchange_openExchangeOnSameItem_throwsConflict() {
        CustomerOrder order = eligibleOrder();
        OrderItem item = new OrderItem().id(5L).productId("2").qty(1).itemSize("S").color("Red");
        Product product = new Product()
            .id(2L)
            .itemStructure("VARIANTS")
            .variantsJson("[{\"sku\":\"TEE-M-BLUE\",\"size\":\"M\",\"color\":\"Blue\",\"rate\":500,\"stock\":3,\"active\":true}]");
        when(customerOrderRepository.findOneByOrderNumberAndUserLogin(ORDER_NUMBER, USER)).thenReturn(Optional.of(order));
        when(orderItemRepository.findByIdAndOrderId(5L, 10L)).thenReturn(Optional.of(item));
        when(productRepository.findById(2L)).thenReturn(Optional.of(product));
        when(orderReturnRepository.existsByOrderIdAndOrderItemIdAndStatusIn(eq(10L), eq(5L), any())).thenReturn(true);

        assertThatThrownBy(() ->
            orderReturnService.requestExchange(USER, ORDER_NUMBER, 5L, "Wrong size ordered by mistake", "M", "Blue")
        )
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("already open for this item");
    }

    @Test
    void isReplacementSellable_matchingActiveVariant_returnsTrue() {
        CustomerOrder order = eligibleOrder();
        OrderItem item = new OrderItem().id(5L).productId("2");
        item.setOrder(order);
        Product product = new Product()
            .id(2L)
            .itemStructure("VARIANTS")
            .variantsJson("[{\"sku\":\"TEE-M-BLUE\",\"size\":\"M\",\"color\":\"Blue\",\"rate\":500,\"stock\":3,\"active\":true}]");
        when(orderItemRepository.findById(5L)).thenReturn(Optional.of(item));
        when(productRepository.findById(2L)).thenReturn(Optional.of(product));

        assertThat(orderReturnService.isReplacementSellable(USER, 5L, "M", "Blue")).isTrue();
    }

    // ---------------------------------------------------------------------
    // Granular fulfilment transitions
    // ---------------------------------------------------------------------

    @Test
    void markPickupRequested_approvedReturn_movesToPickupRequested() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.APPROVED);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);

        OrderReturn result = orderReturnService.markPickupRequested("RET-1", null);

        assertThat(result.getStatus()).isEqualTo(OrderReturnStatus.PICKUP_REQUESTED);
        verify(orderMailService).sendReturnUpdate(any(), eq(orderReturn));
    }

    @Test
    void markPickedUp_pickupRequestedReturn_movesToPickedUp() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.PICKUP_REQUESTED);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);

        OrderReturn result = orderReturnService.markPickedUp("RET-1", null);

        assertThat(result.getStatus()).isEqualTo(OrderReturnStatus.PICKED_UP);
    }

    @Test
    void markInspected_receivedReturn_movesToInspected() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.RECEIVED);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);

        OrderReturn result = orderReturnService.markInspected("RET-1", null);

        assertThat(result.getStatus()).isEqualTo(OrderReturnStatus.INSPECTED);
    }

    @Test
    void acceptAfterInspection_inspectedReturn_movesToAccepted() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.INSPECTED);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);

        OrderReturn result = orderReturnService.acceptAfterInspection("RET-1", ReturnDisposition.QUARANTINE, null);

        assertThat(result.getStatus()).isEqualTo(OrderReturnStatus.ACCEPTED);
    }

    @Test
    void rejectAtCurrentStage_requestedReturn_delegatesToReject() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.REQUESTED);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);

        OrderReturn result = orderReturnService.rejectAtCurrentStage("RET-1", "Not eligible", null);

        assertThat(result.getStatus()).isEqualTo(OrderReturnStatus.REJECTED);
    }

    @Test
    void rejectAtCurrentStage_wrongStatus_throwsBadRequest() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.APPROVED);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));

        assertThatThrownBy(() -> orderReturnService.rejectAtCurrentStage("RET-1", "Not eligible", null))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("cannot be rejected");
    }

    @Test
    void rejectAfterInspection_wrongStatus_throwsBadRequest() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.RECEIVED);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));

        assertThatThrownBy(() -> orderReturnService.rejectAfterInspection("RET-1", "Damaged beyond use", null))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("inspected");
    }

    @Test
    void rejectAfterInspection_inspectedReturn_rejectsWithoutStockChange() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.INSPECTED);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);

        OrderReturn result = orderReturnService.rejectAfterInspection("RET-1", "Item damaged by customer", null);

        assertThat(result.getStatus()).isEqualTo(OrderReturnStatus.REJECTED);
        verify(productRepository, never()).findOneForInventoryUpdate(any());
    }

    @Test
    void rejectAfterInspection_rejectsEvenWhenStockWasPreviouslyCredited() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.INSPECTED);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);

        OrderReturn result = orderReturnService.rejectAfterInspection("RET-1", "Item damaged by customer", null);

        assertThat(result.getStatus()).isEqualTo(OrderReturnStatus.REJECTED);
        assertThat(result.getAdminNotes()).isNull();
    }

    // ---------------------------------------------------------------------
    // Exchange-only fulfilment
    // ---------------------------------------------------------------------

    @Test
    void allocateReplacementItem_notAnExchange_throwsBadRequest() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.ACCEPTED);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));

        assertThatThrownBy(() -> orderReturnService.allocateReplacementItem("RET-1", null))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Not an exchange request");
    }

    @Test
    void allocateReplacementItem_insufficientStock_throwsConflictAndDoesNotTransition() {
        OrderReturn orderReturn = exchangeReturnWithStatus(OrderReturnStatus.ACCEPTED, "TEE-M-BLUE", "M", "Blue");
        Product product = new Product()
            .id(2L)
            .itemStructure("VARIANTS")
            .variantsJson("[{\"sku\":\"TEE-M-BLUE\",\"size\":\"M\",\"color\":\"Blue\",\"rate\":500,\"stock\":0,\"active\":true}]");
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(productRepository.findOneForInventoryUpdate(2L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> orderReturnService.allocateReplacementItem("RET-1", null))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("no longer in stock");

        assertThat(orderReturn.getStatus()).isEqualTo(OrderReturnStatus.ACCEPTED);
        verify(productRepository, never()).save(any());
    }

    @Test
    void allocateReplacementItem_success_decrementsStockAndTransitions() {
        OrderReturn orderReturn = exchangeReturnWithStatus(OrderReturnStatus.ACCEPTED, "TEE-M-BLUE", "M", "Blue");
        Product product = new Product()
            .id(2L)
            .itemStructure("VARIANTS")
            .variantsJson("[{\"sku\":\"TEE-M-BLUE\",\"size\":\"M\",\"color\":\"Blue\",\"rate\":500,\"stock\":3,\"active\":true}]");
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(productRepository.findOneForInventoryUpdate(2L)).thenReturn(Optional.of(product));
        when(productRepository.save(product)).thenReturn(product);
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);

        OrderReturn result = orderReturnService.allocateReplacementItem("RET-1", null);

        assertThat(result.getStatus()).isEqualTo(OrderReturnStatus.ITEM_ALLOCATED);
        assertThat(product.getStock()).isEqualTo(2);
    }

    @Test
    void convertExchangeToRefund_refundsItemAmountNotWholeOrder() {
        OrderReturn orderReturn = exchangeReturnWithStatus(OrderReturnStatus.ACCEPTED, "TEE-M-BLUE", "M", "Blue");
        orderReturn.getOrderItem().price(new BigDecimal("250.00")).qty(2);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(razorpayRefundService.refundPayment(any(), eq(new BigDecimal("500.00")))).thenReturn("rfnd_demo");
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);

        OrderReturn result = orderReturnService.convertExchangeToRefund("RET-1", null);

        assertThat(result.getStatus()).isEqualTo(OrderReturnStatus.REFUNDED);
        assertThat(result.getRefundAmount()).isEqualByComparingTo("500.00");
        verify(customerOrderRepository, never()).save(any());
    }

    @Test
    void processRefund_exchangeType_throwsBadRequest() {
        OrderReturn orderReturn = exchangeReturnWithStatus(OrderReturnStatus.ACCEPTED, "TEE-M-BLUE", "M", "Blue");
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));

        assertThatThrownBy(() -> orderReturnService.processRefund("RET-1", null))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("convert-to-refund");
    }

    @Test
    void exchangeFulfilment_walksThroughRemainingStates() {
        OrderReturn orderReturn = exchangeReturnWithStatus(OrderReturnStatus.ITEM_ALLOCATED, "TEE-M-BLUE", "M", "Blue");
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);

        assertThat(orderReturnService.markRepacking("RET-1", null).getStatus()).isEqualTo(OrderReturnStatus.REPACKING);
        assertThat(orderReturnService.markReadyToShip("RET-1", null).getStatus()).isEqualTo(OrderReturnStatus.READY_TO_SHIP);
        assertThat(orderReturnService.markShipped("RET-1", null).getStatus()).isEqualTo(OrderReturnStatus.SHIPPED);
        assertThat(orderReturnService.markDelivered("RET-1", null).getStatus()).isEqualTo(OrderReturnStatus.DELIVERED);
        assertThat(orderReturnService.completeExchange("RET-1", null).getStatus()).isEqualTo(OrderReturnStatus.COMPLETED);
    }

    @Test
    void publicConfig_exposesFeatureFlags() {
        Map<String, Object> config = orderReturnService.publicConfig();

        assertThat(config.get("enabled")).isEqualTo(true);
        assertThat(config.get("window_days")).isEqualTo(3);
    }

    @Test
    void rejectAtCurrentStage_inspectedReturn_delegatesToRejectAfterInspection() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.INSPECTED);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);

        OrderReturn result = orderReturnService.rejectAtCurrentStage("RET-1", "Damaged item", "note");

        assertThat(result.getStatus()).isEqualTo(OrderReturnStatus.REJECTED);
        verify(productRepository, never()).findOneForInventoryUpdate(any());
    }

    @Test
    void isReplacementSellable_wrongUser_returnsFalse() {
        CustomerOrder order = eligibleOrder();
        OrderItem item = new OrderItem().id(5L).productId("2");
        item.setOrder(order);
        when(orderItemRepository.findById(5L)).thenReturn(Optional.of(item));

        assertThat(orderReturnService.isReplacementSellable("other-user", 5L, "M", "Blue")).isFalse();
    }

    @Test
    void isReplacementSellable_nonVariantProduct_returnsFalse() {
        CustomerOrder order = eligibleOrder();
        OrderItem item = new OrderItem().id(5L).productId("2");
        item.setOrder(order);
        Product product = new Product().id(2L).itemStructure("SIMPLE");
        when(orderItemRepository.findById(5L)).thenReturn(Optional.of(item));
        when(productRepository.findById(2L)).thenReturn(Optional.of(product));

        assertThat(orderReturnService.isReplacementSellable(USER, 5L, "M", "Blue")).isFalse();
    }

    @Test
    void requestExchange_nonVariantProduct_throwsBadRequest() {
        CustomerOrder order = eligibleOrder();
        OrderItem item = new OrderItem().id(5L).productId("2").qty(1);
        Product product = new Product().id(2L).itemStructure("SIMPLE");
        when(customerOrderRepository.findOneByOrderNumberAndUserLogin(ORDER_NUMBER, USER)).thenReturn(Optional.of(order));
        when(orderItemRepository.findByIdAndOrderId(5L, 10L)).thenReturn(Optional.of(item));
        when(productRepository.findById(2L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() ->
            orderReturnService.requestExchange(USER, ORDER_NUMBER, 5L, "Wrong size ordered by mistake", "M", "Blue")
        )
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("does not support exchanges");
    }

    @Test
    void acceptWithRestockable_exchangeRestoresItemStock() {
        OrderReturn orderReturn = exchangeReturnWithStatus(OrderReturnStatus.INSPECTED, "TEE-M-BLUE", "M", "Blue");
        Product product = new Product().id(2L).stock(4);
        orderReturn.getOrderItem().productId("2").qty(1);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(productRepository.findOneForInventoryUpdate(2L)).thenReturn(Optional.of(product));
        when(productRepository.save(product)).thenReturn(product);
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);

        orderReturnService.acceptAfterInspection("RET-1", ReturnDisposition.RESTOCKABLE, null);

        assertThat(product.getStock()).isEqualTo(5);
        verify(orderItemRepository, never()).findAllByOrderId(any());
    }

    @Test
    void convertExchangeToRefund_fromFailedStatus_refundsItemAmount() {
        OrderReturn orderReturn = exchangeReturnWithStatus(OrderReturnStatus.FAILED, "TEE-M-BLUE", "M", "Blue");
        orderReturn.getOrderItem().price(new BigDecimal("150.00")).qty(1);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(razorpayRefundService.refundPayment(any(), eq(new BigDecimal("150.00")))).thenReturn("rfnd_demo");
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);

        OrderReturn result = orderReturnService.convertExchangeToRefund("RET-1", "Converted");

        assertThat(result.getStatus()).isEqualTo(OrderReturnStatus.REFUNDED);
    }

    @Test
    void processRefund_zohoSyncEnabled_createsCreditNote() {
        ApplicationProperties.Zoho zoho = new ApplicationProperties.Zoho();
        zoho.setSyncReturns(true);
        when(applicationProperties.getZoho()).thenReturn(zoho);
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.ACCEPTED);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(razorpayRefundService.refundPayment(any(), any())).thenReturn("rfnd_demo_ORD-9001");
        when(zohoBooksIntegrationClient.createCreditNote(10L, "RET-1")).thenReturn(Optional.of("cn-zoho-1"));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);
        when(customerOrderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderReturn result = orderReturnService.processRefund("RET-1", null);

        assertThat(result.getZohoCreditNoteId()).isEqualTo("cn-zoho-1");
    }

    @Test
    void processRefund_zohoFailure_recordsAdminNote() {
        ApplicationProperties.Zoho zoho = new ApplicationProperties.Zoho();
        zoho.setSyncReturns(true);
        when(applicationProperties.getZoho()).thenReturn(zoho);
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.ACCEPTED);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(razorpayRefundService.refundPayment(any(), any())).thenReturn("rfnd_live");
        when(zohoBooksIntegrationClient.createCreditNote(10L, "RET-1")).thenThrow(new RuntimeException("zoho 500"));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);
        when(customerOrderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderReturn result = orderReturnService.processRefund("RET-1", null);

        assertThat(result.getStatus()).isEqualTo(OrderReturnStatus.REFUNDED);
        assertThat(result.getAdminNotes()).contains("Zoho credit note failed");
    }

    @Test
    void resolveDeliveryAnchor_deliveredTrackingStatus_usesLedgerlyPullTime() {
        CustomerOrder order = eligibleOrder()
            .shipmentDate(null)
            .status(OrderStatus.CONFIRMED)
            .ledgerlyOrderStatus("delivered")
            .ledgerlyTrackingLastPulledAt(Instant.parse("2026-02-01T00:00:00Z"));

        Instant anchor = orderReturnService.resolveDeliveryAnchor(order);

        assertThat(anchor).isEqualTo(Instant.parse("2026-02-01T00:00:00Z"));
    }

    @Test
    void resolveDeliveryAnchor_trackingNumberWithoutShipment_usesLedgerlyPullTime() {
        CustomerOrder order = eligibleOrder()
            .shipmentDate(null)
            .status(OrderStatus.CONFIRMED)
            .trackingNumber("AWB-999")
            .ledgerlyTrackingLastPulledAt(Instant.parse("2026-02-15T00:00:00Z"));

        Instant anchor = orderReturnService.resolveDeliveryAnchor(order);

        assertThat(anchor).isEqualTo(Instant.parse("2026-02-15T00:00:00Z"));
    }

    @Test
    void markReceived_fromPickedUp_movesToReceived() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.PICKED_UP);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);

        OrderReturn result = orderReturnService.markReceived("RET-1", "At warehouse");

        assertThat(result.getStatus()).isEqualTo(OrderReturnStatus.RECEIVED);
    }

    @Test
    void reject_wrongStatus_throwsBadRequest() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.APPROVED);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));

        assertThatThrownBy(() -> orderReturnService.reject("RET-1", "Not eligible", null))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("not awaiting approval");
    }

    @Test
    void rejectAfterInspection_exchangeRejectsWithoutStockChange() {
        OrderReturn orderReturn = exchangeReturnWithStatus(OrderReturnStatus.INSPECTED, "TEE-M-BLUE", "M", "Blue");
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);

        OrderReturn result = orderReturnService.rejectAfterInspection("RET-1", "Customer damaged item", null);

        assertThat(result.getStatus()).isEqualTo(OrderReturnStatus.REJECTED);
        verify(productRepository, never()).findOneForInventoryUpdate(any());
    }

    @Test
    void allocateReplacementItem_wrongStatus_throwsBadRequest() {
        OrderReturn orderReturn = exchangeReturnWithStatus(OrderReturnStatus.REQUESTED, "TEE-M-BLUE", "M", "Blue");
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));

        assertThatThrownBy(() -> orderReturnService.allocateReplacementItem("RET-1", null))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("accepted before allocating");
    }

    @Test
    void allocateReplacementItem_missingProduct_throwsBadRequest() {
        OrderReturn orderReturn = exchangeReturnWithStatus(OrderReturnStatus.ACCEPTED, "TEE-M-BLUE", "M", "Blue");
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(productRepository.findOneForInventoryUpdate(2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderReturnService.allocateReplacementItem("RET-1", null))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("could not be resolved");
    }

    @Test
    void convertExchangeToRefund_wrongStatus_throwsBadRequest() {
        OrderReturn orderReturn = exchangeReturnWithStatus(OrderReturnStatus.REQUESTED, "TEE-M-BLUE", "M", "Blue");
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));

        assertThatThrownBy(() -> orderReturnService.convertExchangeToRefund("RET-1", null))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("accepted");
    }

    @Test
    void processRefund_ledgerlyException_demoOrder_stubsCreditNote() {
        enableLedgerlyReturnsSync();
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.ACCEPTED);
        CustomerOrder order = orderReturn.getOrder();
        order.setRazorpayPaymentId("pay_demo_abc");
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(razorpayRefundService.refundPayment(any(), any())).thenReturn("rfnd_demo");
        when(ledgerlyIntegrationClient.cancelOrder(any(), any(), any())).thenThrow(new RuntimeException("ledgerly down"));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);
        when(customerOrderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderReturn result = orderReturnService.processRefund("RET-1", null);

        assertThat(result.getLedgerlyCreditNoteId()).isEqualTo("cn_demo_" + ORDER_NUMBER);
    }

    @Test
    void processRefund_zohoEmptyResponse_demoOrder_stubsCreditNote() {
        ApplicationProperties.Zoho zoho = new ApplicationProperties.Zoho();
        zoho.setSyncReturns(true);
        when(applicationProperties.getZoho()).thenReturn(zoho);
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.ACCEPTED);
        orderReturn.getOrder().setRazorpayOrderId("order_demo_ORD-9001");
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(razorpayRefundService.isDemoMode()).thenReturn(true);
        when(razorpayRefundService.refundPayment(any(), any())).thenReturn("rfnd_demo");
        when(zohoBooksIntegrationClient.createCreditNote(10L, "RET-1")).thenReturn(Optional.empty());
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);
        when(customerOrderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderReturn result = orderReturnService.processRefund("RET-1", null);

        assertThat(result.getZohoCreditNoteId()).isEqualTo("cn_zoho_demo_" + ORDER_NUMBER);
    }

    @Test
    void processRefund_zohoException_demoOrder_stubsCreditNote() {
        ApplicationProperties.Zoho zoho = new ApplicationProperties.Zoho();
        zoho.setSyncReturns(true);
        when(applicationProperties.getZoho()).thenReturn(zoho);
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.ACCEPTED);
        orderReturn.getOrder().setRazorpayPaymentId("pay_demo_xyz");
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(razorpayRefundService.refundPayment(any(), any())).thenReturn("rfnd_demo");
        when(zohoBooksIntegrationClient.createCreditNote(10L, "RET-1")).thenThrow(new RuntimeException("zoho down"));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);
        when(customerOrderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderReturn result = orderReturnService.processRefund("RET-1", null);

        assertThat(result.getZohoCreditNoteId()).isEqualTo("cn_zoho_demo_" + ORDER_NUMBER);
    }

    @Test
    void acceptWithRestockable_invalidProductId_skipsInventoryRestore() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.INSPECTED);
        OrderItem item = new OrderItem().productId("not-a-number").qty(2);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(orderItemRepository.findAllByOrderId(10L)).thenReturn(List.of(item));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);

        orderReturnService.acceptAfterInspection("RET-1", ReturnDisposition.RESTOCKABLE, null);

        verify(productRepository, never()).findOneForInventoryUpdate(any());
    }

    @Test
    void acceptWithRestockable_zeroQuantityItem_skipsStockChange() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.INSPECTED);
        OrderItem item = new OrderItem().productId("1").qty(0);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(orderItemRepository.findAllByOrderId(10L)).thenReturn(List.of(item));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);

        orderReturnService.acceptAfterInspection("RET-1", ReturnDisposition.RESTOCKABLE, null);

        verify(productRepository, never()).save(any());
    }

    @Test
    void acceptWithRestockable_variantIncrementFails_fallsBackToProductStock() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.INSPECTED);
        OrderItem item = new OrderItem().productId("2").qty(1).variantSku("MISSING").itemSize("M").color("Blue");
        Product product = new Product().id(2L).stock(3).itemStructure("VARIANTS").variantsJson("[]");
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(orderItemRepository.findAllByOrderId(10L)).thenReturn(List.of(item));
        when(productRepository.findOneForInventoryUpdate(2L)).thenReturn(Optional.of(product));
        when(productRepository.save(product)).thenReturn(product);
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);

        orderReturnService.acceptAfterInspection("RET-1", ReturnDisposition.RESTOCKABLE, null);

        assertThat(product.getStock()).isEqualTo(4);
    }

    @Test
    void rejectAfterInspection_nullItem_stillRejects() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.INSPECTED);
        orderReturn.setRequestType(OrderRequestType.EXCHANGE);
        orderReturn.setOrderItem(null);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);

        OrderReturn result = orderReturnService.rejectAfterInspection("RET-1", "Not acceptable", null);

        assertThat(result.getStatus()).isEqualTo(OrderReturnStatus.REJECTED);
    }

    @Test
    void requestReturn_longOrderNumber_truncatesReturnNumber() {
        CustomerOrder order = eligibleOrder().orderNumber("ORD-" + "X".repeat(60));
        when(customerOrderRepository.findOneByOrderNumberAndUserLogin(order.getOrderNumber(), USER)).thenReturn(Optional.of(order));
        when(orderReturnRepository.existsByOrderIdAndStatusIn(eq(order.getId()), any())).thenReturn(false);
        when(orderReturnRepository.save(any(OrderReturn.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderReturn result = orderReturnService.requestReturn(USER, order.getOrderNumber(), "Product arrived damaged in transit");

        assertThat(result.getReturnNumber().length()).isLessThanOrEqualTo(48);
    }

    @Test
    void resolveDeliveryAnchor_invalidShipmentDate_returnsNull() {
        CustomerOrder order = eligibleOrder().shipmentDate("31/08/2026").status(OrderStatus.CONFIRMED);

        Instant anchor = orderReturnService.resolveDeliveryAnchor(order);

        assertThat(anchor).isNull();
    }

    @Test
    void isReplacementSellable_missingItem_returnsFalse() {
        when(orderItemRepository.findById(99L)).thenReturn(Optional.empty());

        assertThat(orderReturnService.isReplacementSellable(USER, 99L, "M", "Blue")).isFalse();
    }

    @Test
    void requestExchange_missingOrderItem_throwsNotFound() {
        CustomerOrder order = eligibleOrder();
        when(customerOrderRepository.findOneByOrderNumberAndUserLogin(ORDER_NUMBER, USER)).thenReturn(Optional.of(order));
        when(orderItemRepository.findByIdAndOrderId(99L, 10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
            orderReturnService.requestExchange(USER, ORDER_NUMBER, 99L, "Wrong size ordered by mistake", "M", "Blue")
        )
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Order item not found");
    }

    @Test
    void acceptWithRestockable_exchangeWithZeroQtyItem_doesNotSaveProduct() {
        OrderReturn orderReturn = exchangeReturnWithStatus(OrderReturnStatus.INSPECTED, "TEE-M-BLUE", "M", "Blue");
        orderReturn.getOrderItem().productId("2").qty(0);
        Product product = new Product().id(2L).stock(4);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(productRepository.findOneForInventoryUpdate(2L)).thenReturn(Optional.of(product));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);

        orderReturnService.acceptAfterInspection("RET-1", ReturnDisposition.RESTOCKABLE, null);

        verify(productRepository, never()).save(any());
        assertThat(product.getStock()).isEqualTo(4);
    }

    @Test
    void rejectAfterInspection_missingProduct_stillRejects() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.INSPECTED);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);

        OrderReturn result = orderReturnService.rejectAfterInspection("RET-1", "Item damaged by customer", null);

        assertThat(result.getStatus()).isEqualTo(OrderReturnStatus.REJECTED);
        assertThat(result.getAdminNotes()).isNull();
    }

    @Test
    void rejectAfterInspection_zeroQuantityItem_stillRejects() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.INSPECTED);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);

        OrderReturn result = orderReturnService.rejectAfterInspection("RET-1", "Item damaged by customer", null);

        assertThat(result.getStatus()).isEqualTo(OrderReturnStatus.REJECTED);
        verify(productRepository, never()).save(any());
    }

    @Test
    void rejectAfterInspection_invalidProductId_stillRejects() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.INSPECTED);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);

        OrderReturn result = orderReturnService.rejectAfterInspection("RET-1", "Item damaged by customer", null);

        assertThat(result.getStatus()).isEqualTo(OrderReturnStatus.REJECTED);
    }

    @Test
    void allocateReplacementItem_invalidProductId_throwsBadRequest() {
        OrderReturn orderReturn = exchangeReturnWithStatus(OrderReturnStatus.ACCEPTED, "TEE-M-BLUE", "M", "Blue");
        orderReturn.getOrderItem().productId("bad-id");
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));

        assertThatThrownBy(() -> orderReturnService.allocateReplacementItem("RET-1", null))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("could not be resolved");
    }

    @Test
    void bookPickup_manualWithoutAwb_throwsBadRequest() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.APPROVED);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));

        assertThatThrownBy(() ->
            orderReturnService.bookPickup("RET-1", ReturnLogisticsMode.MANUAL, "Delhivery", "", null, null, null)
        )
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Reverse AWB");
    }

    @Test
    void bookPickup_xpressBeesNotConfigured_throwsServiceUnavailable() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.APPROVED);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(xpressBeesClient.isReversePickupConfigured()).thenReturn(false);

        assertThatThrownBy(() ->
            orderReturnService.bookPickup("RET-1", ReturnLogisticsMode.XPRESSBEES, null, null, null, null, null)
        )
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("not configured");
    }

    @Test
    void bookPickup_xpressBeesSuccess_persistsAwbAndTracking() throws Exception {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.APPROVED);
        orderReturn.getOrder().shippingAddressJson(
            "{\"full_name\":\"Jane Doe\",\"phone\":\"9876543210\",\"line1\":\"12 MG Road\",\"city\":\"Bengaluru\",\"state\":\"Karnataka\",\"pincode\":\"560001\"}"
        );
        OrderItem item = new OrderItem().productName("Tee").variantSku("TEE-1").qty(1).price(BigDecimal.TEN);
        orderReturn.setOrderItem(item);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(xpressBeesClient.isReversePickupConfigured()).thenReturn(true);
        when(xpressBeesClient.createReversePickup(any())).thenReturn(
            new XpressBeesClient.ReversePickupResult("AWB123", "https://track.example/AWB123", "Tomorrow", "REQ-1", "Success")
        );
        when(xpressBeesClient.buildTrackingUrl("AWB123")).thenReturn("https://track.example/AWB123");
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);

        OrderReturn result = orderReturnService.bookPickup("RET-1", ReturnLogisticsMode.XPRESSBEES, null, null, null, null, "Book now");

        assertThat(result.getStatus()).isEqualTo(OrderReturnStatus.PICKUP_SCHEDULED);
        assertThat(result.getLogisticsMode()).isEqualTo(ReturnLogisticsMode.XPRESSBEES);
        assertThat(result.getReverseAwb()).isEqualTo("AWB123");
        assertThat(result.getTrackingUrl()).isEqualTo("https://track.example/AWB123");
        assertThat(result.getProviderRequestId()).isEqualTo("REQ-1");
    }

    @Test
    void bookOutbound_manualMode_movesToShippedWithAwb() {
        OrderReturn orderReturn = exchangeReturnWithStatus(OrderReturnStatus.READY_TO_SHIP, "TEE-M-BLUE", "M", "Blue");
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);

        OrderReturn result = orderReturnService.bookOutbound(
            "RET-1",
            ReturnLogisticsMode.MANUAL,
            "Delhivery",
            "OUT-AWB-1",
            "https://track/out",
            "https://label/out",
            "Manual outbound"
        );

        assertThat(result.getStatus()).isEqualTo(OrderReturnStatus.SHIPPED);
        assertThat(result.getOutboundLogisticsMode()).isEqualTo(ReturnLogisticsMode.MANUAL);
        assertThat(result.getOutboundAwb()).isEqualTo("OUT-AWB-1");
        assertThat(result.getOutboundTrackingUrl()).isEqualTo("https://track/out");
        assertThat(result.getOutboundLabelUrl()).isEqualTo("https://label/out");
    }

    @Test
    void bookOutbound_xpressBeesSuccess_persistsOutboundAwb() throws Exception {
        OrderReturn orderReturn = exchangeReturnWithStatus(OrderReturnStatus.READY_TO_SHIP, "TEE-M-BLUE", "M", "Blue");
        orderReturn
            .getOrder()
            .shippingAddressJson(
                "{\"full_name\":\"Jane Doe\",\"phone\":\"9876543210\",\"line1\":\"12 MG Road\",\"city\":\"Bengaluru\",\"state\":\"Karnataka\",\"pincode\":\"560001\"}"
            );
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(xpressBeesClient.isForwardShipmentConfigured()).thenReturn(true);
        when(xpressBeesClient.createForwardShipment(any())).thenReturn(
            new XpressBeesClient.ForwardShipmentResult("OUT123", "https://track.example/OUT123", "https://label/out", "OUT-REQ-1", "Success")
        );
        when(xpressBeesClient.buildTrackingUrl("OUT123")).thenReturn("https://track.example/OUT123");
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);

        OrderReturn result = orderReturnService.bookOutbound("RET-1", ReturnLogisticsMode.XPRESSBEES, null, null, null, null, "Book outbound");

        assertThat(result.getStatus()).isEqualTo(OrderReturnStatus.SHIPPED);
        assertThat(result.getOutboundLogisticsMode()).isEqualTo(ReturnLogisticsMode.XPRESSBEES);
        assertThat(result.getOutboundAwb()).isEqualTo("OUT123");
        assertThat(result.getOutboundTrackingUrl()).isEqualTo("https://track.example/OUT123");
        assertThat(result.getOutboundProviderRequestId()).isEqualTo("OUT-REQ-1");
    }

    @Test
    void requestReturn_selectedQuantityCreatesProratedRefundSnapshot() {
        CustomerOrder order = eligibleOrder().subtotal(new BigDecimal("1000.00")).discount(new BigDecimal("100.00")).total(new BigDecimal("900.00"));
        OrderItem item = new OrderItem().id(77L).order(order).productId("2").productName("Romper").qty(2).price(new BigDecimal("500.00"));
        when(customerOrderRepository.findOneByOrderNumberAndUserLogin(ORDER_NUMBER, USER)).thenReturn(Optional.of(order));
        when(orderReturnRepository.existsByOrderIdAndStatusIn(eq(10L), any())).thenReturn(false);
        when(orderReturnRepository.save(any(OrderReturn.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderItemRepository.findAllByOrderId(10L)).thenReturn(List.of(item));
        when(orderReturnItemRepository.save(any(OrderReturnItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderReturn result = orderReturnService.requestReturn(
            USER,
            ORDER_NUMBER,
            "The selected item is the wrong size",
            List.of(new OrderReturnService.ReturnLineSelection(77L, 1))
        );

        assertThat(result.getRefundAmount()).isEqualByComparingTo("450.00");
        verify(orderReturnItemRepository).save(
            org.mockito.ArgumentMatchers.argThat(line ->
                line.getRequestedQty() == 1 &&
                line.getGrossAmount().compareTo(new BigDecimal("500.00")) == 0 &&
                line.getDiscountAllocation().compareTo(new BigDecimal("50.00")) == 0 &&
                line.getRefundAmount().compareTo(new BigDecimal("450.00")) == 0
            )
        );
    }

    @Test
    void approveExchangeReservesReplacementAndSnapshotsPriceDifference() {
        OrderReturn orderReturn = exchangeReturnWithStatus(OrderReturnStatus.REQUESTED, "TEE-M-BLUE", "M", "Blue");
        orderReturn.getOrderItem().price(new BigDecimal("100.00"));
        Product product = new Product()
            .id(2L)
            .itemStructure("VARIANTS")
            .variantsJson("[{\"sku\":\"TEE-M-BLUE\",\"size\":\"M\",\"color\":\"Blue\",\"rate\":500,\"stock\":2,\"active\":true}]");
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(productRepository.findOneForInventoryUpdate(2L)).thenReturn(Optional.of(product));
        when(productRepository.save(product)).thenReturn(product);
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);

        OrderReturn result = orderReturnService.approve("RET-1", null);

        assertThat(result.getStatus()).isEqualTo(OrderReturnStatus.APPROVED);
        assertThat(result.getReplacementReservedAt()).isNotNull();
        assertThat(result.getPriceDifference()).isPositive();
        assertThat(result.getSettlementStatus()).isEqualTo(ExchangeSettlementStatus.PENDING);
        assertThat(product.getStock()).isEqualTo(1);
    }

    @Test
    void settlePriceDifferenceRecordsAuditedCollection() {
        OrderReturn orderReturn = exchangeReturnWithStatus(OrderReturnStatus.APPROVED, "TEE-M-BLUE", "M", "Blue");
        orderReturn.setPriceDifference(new BigDecimal("100.00"));
        orderReturn.setSettlementStatus(ExchangeSettlementStatus.PENDING);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);

        OrderReturn result = orderReturnService.settlePriceDifference(
            "RET-1",
            ExchangeSettlementStatus.COLLECTED,
            new BigDecimal("100.00"),
            "UPI",
            "UTR-123",
            "admin",
            null
        );

        assertThat(result.getSettlementStatus()).isEqualTo(ExchangeSettlementStatus.COLLECTED);
        assertThat(result.getSettlementReference()).isEqualTo("UTR-123");
        assertThat(result.getSettledBy()).isEqualTo("admin");
        assertThat(result.getSettledAt()).isNotNull();
    }

    private OrderReturn exchangeReturnWithStatus(OrderReturnStatus status, String replacementSku, String size, String color) {
        OrderReturn orderReturn = returnWithStatus(status);
        orderReturn.setRequestType(OrderRequestType.EXCHANGE);
        OrderItem item = new OrderItem().id(5L).productId("2").qty(1).itemSize("S").color("Red").price(BigDecimal.TEN);
        orderReturn.setOrderItem(item);
        orderReturn.setReplacementVariantSku(replacementSku);
        orderReturn.setReplacementSize(size);
        orderReturn.setReplacementColor(color);
        return orderReturn;
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
