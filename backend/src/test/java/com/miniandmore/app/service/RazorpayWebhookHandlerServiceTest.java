package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RazorpayWebhookHandlerServiceTest {

    private static final String SECRET = "rzp_webhook_secret";

    @Mock
    private RazorpayWebhookEventRepository webhookEventRepository;

    @Mock
    private CustomerOrderRepository customerOrderRepository;

    @Mock
    private OrderReturnRepository orderReturnRepository;

    @Mock
    private OrderCancellationRequestRepository cancellationRequestRepository;

    @Mock
    private OrderEventService orderEventService;

    private RazorpayWebhookHandlerService handler;

    @BeforeEach
    void setUp() {
        handler = new RazorpayWebhookHandlerService(
            new ObjectMapper(),
            webhookEventRepository,
            customerOrderRepository,
            orderReturnRepository,
            cancellationRequestRepository,
            orderEventService,
            SECRET
        );
        when(webhookEventRepository.save(any(RazorpayWebhookEvent.class))).thenAnswer(inv -> inv.getArgument(0));
        when(webhookEventRepository.saveAndFlush(any(RazorpayWebhookEvent.class))).thenAnswer(inv -> inv.getArgument(0));
        when(customerOrderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderReturnRepository.findFirstByRazorpayRefundIdOrderByIdDesc(any())).thenReturn(Optional.empty());
        when(orderReturnRepository.findFirstBySettlementReferenceOrderByIdDesc(any())).thenReturn(Optional.empty());
        when(cancellationRequestRepository.findFirstByRazorpayRefundIdOrderByIdDesc(any())).thenReturn(Optional.empty());
        when(customerOrderRepository.findFirstByRazorpayPaymentId(any())).thenReturn(Optional.empty());
        when(customerOrderRepository.findFirstByRazorpayOrderId(any())).thenReturn(Optional.empty());
        when(customerOrderRepository.findOneByOrderNumber(any())).thenReturn(Optional.empty());
        when(orderReturnRepository.findAllByOrder_IdOrderByRequestedAtAsc(any())).thenReturn(java.util.List.of());
        when(orderReturnRepository.save(any(OrderReturn.class))).thenAnswer(inv -> inv.getArgument(0));
        when(cancellationRequestRepository.save(any(OrderCancellationRequest.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void handle_invalidSignature_unauthorized() {
        assertThatThrownBy(() -> handler.handle(processedBody("rfnd_1", "pay_1", "order_1", null), "deadbeef"))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
            .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void handle_missingSecret_serviceUnavailable() {
        handler = new RazorpayWebhookHandlerService(
            new ObjectMapper(),
            webhookEventRepository,
            customerOrderRepository,
            orderReturnRepository,
            cancellationRequestRepository,
            orderEventService,
            ""
        );
        assertThatThrownBy(() -> handler.handle("{}", "sig"))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
            .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void handle_duplicateEvent_skipsUpdate() {
        String body = processedBody("rfnd_dup", "pay_dup", "order_dup", "MNM-1");
        when(webhookEventRepository.existsByEventId("evt_dup")).thenReturn(true);

        Map<String, Object> result = handler.handle(body, sign(body));

        assertThat(result.get("duplicate")).isEqualTo(true);
        assertThat(result.get("updated")).isEqualTo(false);
        verify(customerOrderRepository, never()).save(any());
    }

    @Test
    void handle_refundProcessed_matchesRefundIdOnReturn() {
        CustomerOrder order = paidOrder();
        order.setPaymentStatus(PaymentStatus.REFUND_INITIATED);
        OrderReturn orderReturn = new OrderReturn();
        orderReturn.setOrder(order);
        when(orderReturnRepository.findFirstByRazorpayRefundIdOrderByIdDesc("rfnd_ret")).thenReturn(Optional.of(orderReturn));
        String body = processedBody("rfnd_ret", "pay_ret", "order_ret", null);

        Map<String, Object> result = handler.handle(body, sign(body));

        assertThat(result.get("updated")).isEqualTo(true);
        assertThat(result.get("outcome")).isEqualTo("refunded");
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
        verify(orderEventService).syncLifecycleEvents(order);
    }

    @Test
    void handle_refundProcessed_matchesSettlementReference() {
        CustomerOrder order = paidOrder();
        order.setPaymentStatus(PaymentStatus.REFUND_INITIATED);
        OrderReturn orderReturn = new OrderReturn();
        orderReturn.setOrder(order);
        orderReturn.setStatus(OrderReturnStatus.ACCEPTED);
        orderReturn.setSettlementStatus(ExchangeSettlementStatus.REFUNDED);
        orderReturn.setSettlementAmount(new BigDecimal("300.00"));
        when(orderReturnRepository.findFirstBySettlementReferenceOrderByIdDesc("rfnd_settle")).thenReturn(
            Optional.of(orderReturn)
        );
        when(orderReturnRepository.findAllByOrder_IdOrderByRequestedAtAsc(11L)).thenReturn(java.util.List.of(orderReturn));
        String body =
            "{\"id\":\"evt_settle\",\"event\":\"refund.processed\",\"payload\":{\"refund\":{\"entity\":{\"id\":\"rfnd_settle\",\"payment_id\":\"pay_settle\",\"amount\":30000,\"status\":\"processed\"}}}}";

        Map<String, Object> result = handler.handle(body, sign(body));

        assertThat(result.get("updated")).isEqualTo(true);
        assertThat(result.get("outcome")).isEqualTo("refunded");
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);
    }

    @Test
    void handle_refundProcessed_zeroRemainderConvertedDoesNotUseOrderTotal() {
        CustomerOrder order = paidOrder();
        order.setPaymentStatus(PaymentStatus.REFUND_INITIATED);
        order.setTotal(new BigDecimal("1299.00"));
        OrderReturn orderReturn = new OrderReturn();
        orderReturn.setOrder(order);
        orderReturn.setStatus(OrderReturnStatus.REFUNDED);
        orderReturn.setRefundAmount(BigDecimal.ZERO);
        orderReturn.setSettlementStatus(ExchangeSettlementStatus.REFUNDED);
        orderReturn.setSettlementAmount(new BigDecimal("120.00"));
        when(orderReturnRepository.findFirstBySettlementReferenceOrderByIdDesc("rfnd_zero")).thenReturn(Optional.of(orderReturn));
        when(orderReturnRepository.findAllByOrder_IdOrderByRequestedAtAsc(11L)).thenReturn(java.util.List.of(orderReturn));
        String body =
            "{\"id\":\"evt_zero\",\"event\":\"refund.processed\",\"payload\":{\"refund\":{\"entity\":{\"id\":\"rfnd_zero\",\"payment_id\":\"pay_zero\",\"amount\":12000,\"status\":\"processed\"}}}}";

        handler.handle(body, sign(body));

        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);
    }

    @Test
    void handle_refundProcessed_matchesRefundIdOnCancellation() {
        CustomerOrder order = paidOrder();
        order.setStatus(OrderStatus.CANCELLED);
        order.setPaymentStatus(PaymentStatus.REFUND_INITIATED);
        OrderCancellationRequest request = new OrderCancellationRequest();
        request.setOrder(order);
        when(cancellationRequestRepository.findFirstByRazorpayRefundIdOrderByIdDesc("rfnd_can")).thenReturn(Optional.of(request));
        String body = processedBody("rfnd_can", "pay_can", "order_can", null);

        handler.handle(body, sign(body));

        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    void handle_refundProcessed_matchesPaymentId() {
        CustomerOrder order = paidOrder();
        order.setPaymentStatus(PaymentStatus.REFUND_INITIATED);
        when(customerOrderRepository.findFirstByRazorpayPaymentId("pay_pay")).thenReturn(Optional.of(order));
        String body = processedBody("rfnd_pay", "pay_pay", "order_pay", null);

        handler.handle(body, sign(body));

        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    void handle_refundProcessed_matchesRazorpayOrderId() {
        CustomerOrder order = paidOrder();
        order.setPaymentStatus(PaymentStatus.REFUND_INITIATED);
        when(customerOrderRepository.findFirstByRazorpayOrderId("order_rzp")).thenReturn(Optional.of(order));
        String body = processedBody("rfnd_ord", "pay_ord", "order_rzp", null);

        handler.handle(body, sign(body));

        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    void handle_refundProcessed_matchesNotesOrderNumber() {
        CustomerOrder order = paidOrder();
        order.setPaymentStatus(PaymentStatus.REFUND_INITIATED);
        when(customerOrderRepository.findOneByOrderNumber("MNM-NOTE")).thenReturn(Optional.of(order));
        String body = processedBody("rfnd_note", "pay_note", "order_note", "MNM-NOTE");

        handler.handle(body, sign(body));

        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(order.getOrderNumber()).isEqualTo("MNM-1");
    }

    @Test
    void handle_partialRefund_marksPartiallyRefunded() {
        CustomerOrder order = paidOrder();
        order.setPaymentStatus(PaymentStatus.REFUND_INITIATED);
        order.setTotal(new BigDecimal("2000.00"));
        when(customerOrderRepository.findFirstByRazorpayPaymentId("pay_part")).thenReturn(Optional.of(order));
        String body = processedBody("rfnd_part", "pay_part", "order_part", null);

        handler.handle(body, sign(body));

        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);
        verify(orderEventService).syncLifecycleEvents(order);
    }

    @Test
    void handle_cumulativeReturnRefunds_marksRefunded() {
        CustomerOrder order = paidOrder();
        order.setPaymentStatus(PaymentStatus.PARTIALLY_REFUNDED);
        order.setTotal(new BigDecimal("2000.00"));
        OrderReturn first = new OrderReturn();
        first.setStatus(OrderReturnStatus.REFUNDED);
        first.setRefundAmount(new BigDecimal("700.00"));
        OrderReturn second = new OrderReturn();
        second.setStatus(OrderReturnStatus.REFUNDED);
        second.setRefundAmount(new BigDecimal("1300.00"));
        when(customerOrderRepository.findFirstByRazorpayPaymentId("pay_cum")).thenReturn(Optional.of(order));
        when(orderReturnRepository.findAllByOrder_IdOrderByRequestedAtAsc(11L)).thenReturn(java.util.List.of(first, second));
        String body = processedBody("rfnd_cum", "pay_cum", "order_cum", null);

        handler.handle(body, sign(body));

        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    void handle_alreadyRefunded_doesNotNotifyAgain() {
        CustomerOrder order = paidOrder();
        order.setPaymentStatus(PaymentStatus.REFUNDED);
        when(customerOrderRepository.findFirstByRazorpayPaymentId("pay_done")).thenReturn(Optional.of(order));
        String body = processedBody("rfnd_done", "pay_done", "order_done", null);

        Map<String, Object> result = handler.handle(body, sign(body));

        assertThat(result.get("updated")).isEqualTo(false);
        verify(orderEventService, never()).syncLifecycleEvents(any());
    }

    @Test
    void handle_unmatched_doesNotPersistSoRazorpayCanRetry() {
        String body = processedBody("rfnd_none", "pay_none", "order_none", "MISSING");
        when(customerOrderRepository.findOneByOrderNumber("MISSING")).thenReturn(Optional.empty());

        Map<String, Object> result = handler.handle(body, sign(body));

        assertThat(result.get("outcome")).isEqualTo("unmatched");
        assertThat(result.get("updated")).isEqualTo(false);
        verify(webhookEventRepository, never()).saveAndFlush(any());
        verify(webhookEventRepository, never()).save(any());
    }

    @Test
    void handle_duplicateInsert_skipsUpdate() {
        CustomerOrder order = paidOrder();
        order.setPaymentStatus(PaymentStatus.REFUND_INITIATED);
        when(customerOrderRepository.findFirstByRazorpayPaymentId("pay_race")).thenReturn(Optional.of(order));
        when(webhookEventRepository.saveAndFlush(any(RazorpayWebhookEvent.class))).thenThrow(
            new DataIntegrityViolationException("duplicate")
        );
        String body = processedBody("rfnd_race", "pay_race", "order_race", null);

        Map<String, Object> result = handler.handle(body, sign(body));

        assertThat(result.get("duplicate")).isEqualTo(true);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.REFUND_INITIATED);
        verify(orderEventService, never()).syncLifecycleEvents(any());
    }

    @Test
    void handle_cancelled_marksRefundedEvenWhenWebhookAmountIsPartial() {
        CustomerOrder order = paidOrder();
        order.setStatus(OrderStatus.CANCELLED);
        order.setPaymentStatus(PaymentStatus.REFUND_INITIATED);
        order.setTotal(new BigDecimal("2000.00"));
        when(customerOrderRepository.findFirstByRazorpayPaymentId("pay_can_full")).thenReturn(Optional.of(order));
        String body = processedBody("rfnd_can_full", "pay_can_full", "order_can_full", null);

        handler.handle(body, sign(body));

        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    void handle_payloadWithoutTopLevelId_usesEventAndRefundId() {
        CustomerOrder order = paidOrder();
        order.setPaymentStatus(PaymentStatus.REFUND_INITIATED);
        when(customerOrderRepository.findFirstByRazorpayPaymentId("pay_noid")).thenReturn(Optional.of(order));
        String body =
            "{\"event\":\"refund.processed\",\"payload\":{\"refund\":{\"entity\":{\"id\":\"rfnd_noid\",\"payment_id\":\"pay_noid\",\"amount\":129900,\"status\":\"processed\"}}}}";

        Map<String, Object> result = handler.handle(body, sign(body));

        assertThat(result.get("event_id")).isEqualTo("refund.processed:rfnd_noid");
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    void handle_refundFailed_marksRefundFailedAndReopensReturn() {
        CustomerOrder order = paidOrder();
        order.setPaymentStatus(PaymentStatus.REFUND_INITIATED);
        OrderReturn orderReturn = new OrderReturn();
        orderReturn.setOrder(order);
        orderReturn.setStatus(OrderReturnStatus.REFUNDED);
        orderReturn.setRefundAmount(new BigDecimal("1299.00"));
        when(orderReturnRepository.findFirstByRazorpayRefundIdOrderByIdDesc("rfnd_fail")).thenReturn(Optional.of(orderReturn));
        String body =
            "{\"id\":\"evt_fail\",\"event\":\"refund.failed\",\"payload\":{\"refund\":{\"entity\":{\"id\":\"rfnd_fail\",\"payment_id\":\"pay_fail\",\"amount\":129900,\"status\":\"failed\",\"error_description\":\"bank declined\"}}}}";

        Map<String, Object> result = handler.handle(body, sign(body));

        assertThat(result.get("updated")).isEqualTo(true);
        assertThat(result.get("outcome")).isEqualTo("refund_failed");
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.REFUND_FAILED);
        assertThat(orderReturn.getStatus()).isEqualTo(OrderReturnStatus.FAILED);
        assertThat(orderReturn.getAdminNotes()).contains("bank declined");
        verify(orderEventService).syncLifecycleEvents(order);
    }

    @Test
    void handle_refundFailed_keepsPartialWhenOtherRefundSucceeded() {
        CustomerOrder order = paidOrder();
        order.setPaymentStatus(PaymentStatus.REFUNDED);
        OrderReturn failed = new OrderReturn();
        failed.setOrder(order);
        failed.setStatus(OrderReturnStatus.REFUNDED);
        failed.setRefundAmount(new BigDecimal("200.00"));
        OrderReturn succeeded = new OrderReturn();
        succeeded.setOrder(order);
        succeeded.setStatus(OrderReturnStatus.REFUNDED);
        succeeded.setRefundAmount(new BigDecimal("500.00"));
        when(orderReturnRepository.findFirstByRazorpayRefundIdOrderByIdDesc("rfnd_fail")).thenReturn(Optional.of(failed));
        when(orderReturnRepository.findAllByOrder_IdOrderByRequestedAtAsc(11L)).thenReturn(java.util.List.of(failed, succeeded));
        String body =
            "{\"id\":\"evt_fail_partial\",\"event\":\"refund.failed\",\"payload\":{\"refund\":{\"entity\":{\"id\":\"rfnd_fail\",\"payment_id\":\"pay_fail\",\"amount\":20000,\"status\":\"failed\"}}}}";

        handler.handle(body, sign(body));

        assertThat(failed.getStatus()).isEqualTo(OrderReturnStatus.FAILED);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);
    }

    @Test
    void handle_refundFailed_reopensExchangeSettlement() {
        CustomerOrder order = paidOrder();
        order.setPaymentStatus(PaymentStatus.REFUND_INITIATED);
        OrderReturn orderReturn = new OrderReturn();
        orderReturn.setOrder(order);
        orderReturn.setStatus(OrderReturnStatus.ACCEPTED);
        orderReturn.setSettlementStatus(ExchangeSettlementStatus.REFUNDED);
        orderReturn.setSettlementAmount(new BigDecimal("300.00"));
        orderReturn.setSettlementReference("rfnd_settle_fail");
        when(orderReturnRepository.findFirstBySettlementReferenceOrderByIdDesc("rfnd_settle_fail")).thenReturn(
            Optional.of(orderReturn)
        );
        String body =
            "{\"id\":\"evt_settle_fail\",\"event\":\"refund.failed\",\"payload\":{\"refund\":{\"entity\":{\"id\":\"rfnd_settle_fail\",\"payment_id\":\"pay_settle\",\"amount\":30000,\"status\":\"failed\"}}}}";

        handler.handle(body, sign(body));

        assertThat(orderReturn.getSettlementStatus()).isEqualTo(ExchangeSettlementStatus.PENDING);
        assertThat(orderReturn.getSettlementReference()).isEqualTo("rfnd_settle_fail");
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.REFUND_FAILED);
    }

    @Test
    void handle_refundFailed_unmatchedRefundIdDoesNotRewritePayment() {
        CustomerOrder order = paidOrder();
        order.setPaymentStatus(PaymentStatus.REFUND_INITIATED);
        when(customerOrderRepository.findFirstByRazorpayPaymentId("pay_stale")).thenReturn(Optional.of(order));
        String body =
            "{\"id\":\"evt_stale_fail\",\"event\":\"refund.failed\",\"payload\":{\"refund\":{\"entity\":{\"id\":\"rfnd_old\",\"payment_id\":\"pay_stale\",\"amount\":129900,\"status\":\"failed\"}}}}";

        Map<String, Object> result = handler.handle(body, sign(body));

        assertThat(result.get("updated")).isEqualTo(false);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.REFUND_INITIATED);
        verify(orderEventService, never()).syncLifecycleEvents(any());
    }

    @Test
    void handle_refundProcessed_reopensFailedReturn() {
        CustomerOrder order = paidOrder();
        order.setPaymentStatus(PaymentStatus.REFUND_FAILED);
        OrderReturn orderReturn = new OrderReturn();
        orderReturn.setOrder(order);
        orderReturn.setStatus(OrderReturnStatus.FAILED);
        orderReturn.setRefundAmount(new BigDecimal("1299.00"));
        when(orderReturnRepository.findFirstByRazorpayRefundIdOrderByIdDesc("rfnd_late_ok")).thenReturn(Optional.of(orderReturn));
        when(orderReturnRepository.findAllByOrder_IdOrderByRequestedAtAsc(11L)).thenReturn(java.util.List.of(orderReturn));
        String body = processedBody("rfnd_late_ok", "pay_late", "order_late", "MNM-1");

        handler.handle(body, sign(body));

        assertThat(orderReturn.getStatus()).isEqualTo(OrderReturnStatus.REFUNDED);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    void handle_paymentRefundedEvent_withoutAmount_doesNotTreatAsOrderTotal() {
        CustomerOrder order = paidOrder();
        order.setPaymentStatus(PaymentStatus.REFUND_INITIATED);
        when(customerOrderRepository.findFirstByRazorpayPaymentId("pay_evt")).thenReturn(Optional.of(order));
        String body =
            "{\"id\":\"evt_pay\",\"event\":\"payment.refunded\",\"payload\":{\"payment\":{\"entity\":{\"id\":\"pay_evt\",\"order_id\":\"order_evt\",\"status\":\"refunded\"}}}}";

        Map<String, Object> result = handler.handle(body, sign(body));

        assertThat(result.get("updated")).isEqualTo(false);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.REFUND_INITIATED);
    }

    @Test
    void handle_paymentRefundedEvent_withoutAmount_usesCashAlreadyRefunded() {
        CustomerOrder order = paidOrder();
        order.setPaymentStatus(PaymentStatus.REFUND_INITIATED);
        OrderReturn orderReturn = new OrderReturn();
        orderReturn.setOrder(order);
        orderReturn.setStatus(OrderReturnStatus.REFUNDED);
        orderReturn.setRefundAmount(new BigDecimal("1299.00"));
        when(customerOrderRepository.findFirstByRazorpayPaymentId("pay_full")).thenReturn(Optional.of(order));
        when(orderReturnRepository.findAllByOrder_IdOrderByRequestedAtAsc(11L)).thenReturn(java.util.List.of(orderReturn));
        String body =
            "{\"id\":\"evt_full\",\"event\":\"payment.refunded\",\"payload\":{\"payment\":{\"entity\":{\"id\":\"pay_full\",\"order_id\":\"order_full\",\"status\":\"refunded\"}}}}";

        handler.handle(body, sign(body));

        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }

    private static CustomerOrder paidOrder() {
        CustomerOrder order = new CustomerOrder();
        order.setId(11L);
        order.setOrderNumber("MNM-1");
        order.setPaymentMethod("online");
        order.setPaymentStatus(PaymentStatus.PAID);
        order.setStatus(OrderStatus.CONFIRMED);
        order.setTotal(new BigDecimal("1299.00"));
        return order;
    }

    private static String processedBody(String refundId, String paymentId, String razorpayOrderId, String orderNumber) {
        String notes = orderNumber == null ? "{}" : "{\"order_number\":\"" + orderNumber + "\"}";
        return (
            "{\"id\":\"evt_dup\",\"event\":\"refund.processed\",\"payload\":{\"refund\":{\"entity\":{\"id\":\"" +
            refundId +
            "\",\"payment_id\":\"" +
            paymentId +
            "\",\"amount\":129900,\"status\":\"processed\",\"notes\":" +
            notes +
            "}},\"payment\":{\"entity\":{\"id\":\"" +
            paymentId +
            "\",\"order_id\":\"" +
            razorpayOrderId +
            "\"}}}}"
        );
    }

    private static String sign(String body) {
        return WebhookHmacSupport.hmacSha256Hex(SECRET, body);
    }
}
