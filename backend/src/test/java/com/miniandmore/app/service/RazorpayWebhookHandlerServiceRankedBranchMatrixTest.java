package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.OrderCancellationRequest;
import com.miniandmore.app.domain.OrderReturn;
import com.miniandmore.app.domain.enumeration.ExchangeSettlementStatus;
import com.miniandmore.app.domain.enumeration.OrderReturnStatus;
import com.miniandmore.app.domain.enumeration.PaymentStatus;
import com.miniandmore.app.repository.CustomerOrderRepository;
import com.miniandmore.app.repository.OrderCancellationRequestRepository;
import com.miniandmore.app.repository.OrderReturnRepository;
import com.miniandmore.app.repository.RazorpayWebhookEventRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RazorpayWebhookHandlerServiceRankedBranchMatrixTest {

    @Mock
    private RazorpayWebhookEventRepository webhookEvents;

    @Mock
    private CustomerOrderRepository orders;

    @Mock
    private OrderReturnRepository returns;

    @Mock
    private OrderCancellationRequestRepository cancellations;

    @Mock
    private OrderEventService events;

    private RazorpayWebhookHandlerService service;

    @BeforeEach
    void setUp() {
        service = new RazorpayWebhookHandlerService(
            new ObjectMapper(),
            webhookEvents,
            orders,
            returns,
            cancellations,
            events,
            "secret"
        );
    }

    @Test
    void applyFailedRefundToCases_presentButIneligibleCasesRemainUnchanged() {
        OrderReturn returnCase = new OrderReturn();
        returnCase.setStatus(OrderReturnStatus.ACCEPTED);
        returnCase.setSettlementStatus(ExchangeSettlementStatus.PENDING);
        when(returns.findFirstByRazorpayRefundIdOrderByIdDesc("rfnd")).thenReturn(Optional.of(returnCase));
        when(returns.findFirstBySettlementReferenceOrderByIdDesc("rfnd")).thenReturn(Optional.of(returnCase));
        when(cancellations.findFirstByRazorpayRefundIdOrderByIdDesc("rfnd")).thenReturn(Optional.empty());

        Boolean changed = ReflectionTestUtils.invokeMethod(
            service,
            "applyFailedRefundToCases",
            new CustomerOrder(),
            "rfnd",
            "declined"
        );

        assertThat(changed).isFalse();
        verify(returns, never()).save(returnCase);
    }

    @Test
    void applyFailedRefundToCases_updatesOnlyCancellationWhenReturnCasesAbsent() {
        OrderCancellationRequest cancellation = new OrderCancellationRequest();
        cancellation.setAdminNotes("before");
        when(returns.findFirstByRazorpayRefundIdOrderByIdDesc("rfnd-c")).thenReturn(Optional.empty());
        when(returns.findFirstBySettlementReferenceOrderByIdDesc("rfnd-c")).thenReturn(Optional.empty());
        when(cancellations.findFirstByRazorpayRefundIdOrderByIdDesc("rfnd-c")).thenReturn(Optional.of(cancellation));

        Boolean changed = ReflectionTestUtils.invokeMethod(
            service,
            "applyFailedRefundToCases",
            new CustomerOrder(),
            "rfnd-c",
            "timeout"
        );

        assertThat(changed).isTrue();
        assertThat(cancellation.getAdminNotes()).isEqualTo("before\ntimeout (rfnd-c)");
        verify(cancellations).save(cancellation);
    }

    @Test
    void resolveOrder_skipsMatchedCasesWhoseOrderIsNullThenUsesLaterKeys() {
        OrderReturn noOrder = new OrderReturn();
        OrderCancellationRequest noCancelOrder = new OrderCancellationRequest();
        CustomerOrder byNumber = new CustomerOrder().orderNumber("MNM-LAST");
        when(returns.findFirstByRazorpayRefundIdOrderByIdDesc("rfnd")).thenReturn(Optional.of(noOrder));
        when(returns.findFirstBySettlementReferenceOrderByIdDesc("rfnd")).thenReturn(Optional.of(noOrder));
        when(cancellations.findFirstByRazorpayRefundIdOrderByIdDesc("rfnd")).thenReturn(Optional.of(noCancelOrder));
        when(orders.findFirstByRazorpayPaymentId("pay")).thenReturn(Optional.empty());
        when(orders.findFirstByRazorpayOrderId("order")).thenReturn(Optional.empty());
        when(orders.findOneByOrderNumber("MNM-LAST")).thenReturn(Optional.of(byNumber));

        Optional<CustomerOrder> resolved = ReflectionTestUtils.invokeMethod(
            service,
            "resolveOrder",
            "rfnd",
            "pay",
            "order",
            "MNM-LAST"
        );

        assertThat(resolved).contains(byNumber);
    }

    @Test
    void paymentStatusAfterFailedRefund_coversNullIdNonePartialAndFull() {
        CustomerOrder noId = new CustomerOrder();
        assertThat(
            (PaymentStatus) ReflectionTestUtils.invokeMethod(service, "paymentStatusAfterFailedRefund", noId)
        ).isEqualTo(PaymentStatus.REFUND_FAILED);

        CustomerOrder order = new CustomerOrder().id(4L).total(new BigDecimal("100.00"));
        when(returns.findAllByOrder_IdOrderByRequestedAtAsc(4L)).thenReturn(List.of());
        assertThat(
            (PaymentStatus) ReflectionTestUtils.invokeMethod(service, "paymentStatusAfterFailedRefund", order)
        ).isEqualTo(PaymentStatus.REFUND_FAILED);

        OrderReturn partial = refunded("40.00");
        when(returns.findAllByOrder_IdOrderByRequestedAtAsc(4L)).thenReturn(List.of(partial));
        assertThat(
            (PaymentStatus) ReflectionTestUtils.invokeMethod(service, "paymentStatusAfterFailedRefund", order)
        ).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);

        OrderReturn full = refunded("100.00");
        when(returns.findAllByOrder_IdOrderByRequestedAtAsc(4L)).thenReturn(List.of(full));
        assertThat(
            (PaymentStatus) ReflectionTestUtils.invokeMethod(service, "paymentStatusAfterFailedRefund", order)
        ).isEqualTo(PaymentStatus.REFUNDED);
    }

    private static OrderReturn refunded(String amount) {
        OrderReturn orderReturn = new OrderReturn();
        orderReturn.setStatus(OrderReturnStatus.REFUNDED);
        orderReturn.setRefundAmount(new BigDecimal(amount));
        return orderReturn;
    }
}
