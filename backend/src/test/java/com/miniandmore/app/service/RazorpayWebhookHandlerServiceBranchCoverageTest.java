package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RazorpayWebhookHandlerServiceBranchCoverageTest {

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
            "secret"
        );
    }

    @Test
    void resolveOrder_prefersReturnRefundThenSettlementThenCancelThenPayment() {
        CustomerOrder byPayment = new CustomerOrder().id(1L).orderNumber("MNM-PAY");
        CustomerOrder byReturn = new CustomerOrder().id(2L).orderNumber("MNM-RET");
        OrderReturn returnCase = new OrderReturn();
        returnCase.setRazorpayRefundId("rfnd_1");
        returnCase.setOrder(byReturn);
        when(orderReturnRepository.findFirstByRazorpayRefundIdOrderByIdDesc("rfnd_1")).thenReturn(Optional.of(returnCase));

        Optional<CustomerOrder> resolved = ReflectionTestUtils.invokeMethod(
            handler,
            "resolveOrder",
            "rfnd_1",
            "pay_1",
            "order_1",
            "MNM-X"
        );
        assertThat(resolved).contains(byReturn);

        when(orderReturnRepository.findFirstByRazorpayRefundIdOrderByIdDesc("rfnd_2")).thenReturn(Optional.empty());
        OrderReturn settlement = new OrderReturn();
        settlement.setSettlementReference("rfnd_2");
        settlement.setOrder(byReturn);
        when(orderReturnRepository.findFirstBySettlementReferenceOrderByIdDesc("rfnd_2")).thenReturn(Optional.of(settlement));
        assertThat(
            (Optional<CustomerOrder>) ReflectionTestUtils.invokeMethod(handler, "resolveOrder", "rfnd_2", null, null, null)
        ).contains(byReturn);

        when(orderReturnRepository.findFirstBySettlementReferenceOrderByIdDesc("rfnd_3")).thenReturn(Optional.empty());
        OrderCancellationRequest cancel = new OrderCancellationRequest();
        cancel.setRazorpayRefundId("rfnd_3");
        cancel.setOrder(byPayment);
        when(cancellationRequestRepository.findFirstByRazorpayRefundIdOrderByIdDesc("rfnd_3")).thenReturn(Optional.of(cancel));
        assertThat(
            (Optional<CustomerOrder>) ReflectionTestUtils.invokeMethod(handler, "resolveOrder", "rfnd_3", null, null, null)
        ).contains(byPayment);

        when(cancellationRequestRepository.findFirstByRazorpayRefundIdOrderByIdDesc("rfnd_4")).thenReturn(Optional.empty());
        when(customerOrderRepository.findFirstByRazorpayPaymentId("pay_9")).thenReturn(Optional.of(byPayment));
        assertThat(
            (Optional<CustomerOrder>) ReflectionTestUtils.invokeMethod(handler, "resolveOrder", "rfnd_4", "pay_9", null, null)
        ).contains(byPayment);
    }

    @Test
    void applyFailedRefundToCases_updatesReturnSettlementAndCancellation() {
        OrderReturn refunded = new OrderReturn();
        refunded.setStatus(OrderReturnStatus.REFUNDED);
        refunded.setRazorpayRefundId("rfnd_fail");
        refunded.setSettlementReference("rfnd_fail");
        OrderReturn settlementOnly = new OrderReturn();
        settlementOnly.setStatus(OrderReturnStatus.DELIVERED);
        settlementOnly.setSettlementStatus(ExchangeSettlementStatus.REFUNDED);
        settlementOnly.setSettlementReference("rfnd_settle");
        OrderCancellationRequest cancel = new OrderCancellationRequest();
        cancel.setRazorpayRefundId("rfnd_cancel");
        when(orderReturnRepository.findFirstByRazorpayRefundIdOrderByIdDesc("rfnd_fail")).thenReturn(Optional.of(refunded));
        when(orderReturnRepository.findFirstBySettlementReferenceOrderByIdDesc("rfnd_fail")).thenReturn(Optional.of(settlementOnly));
        when(cancellationRequestRepository.findFirstByRazorpayRefundIdOrderByIdDesc("rfnd_fail")).thenReturn(Optional.of(cancel));
        when(orderReturnRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Boolean updated = ReflectionTestUtils.invokeMethod(handler, "applyFailedRefundToCases", new CustomerOrder(), "rfnd_fail", "bank declined");

        assertThat(updated).isTrue();
        assertThat(refunded.getStatus()).isEqualTo(OrderReturnStatus.FAILED);
        verify(orderReturnRepository).save(refunded);
    }

    @Test
    void applyFailedRefundToCases_blankRefundId_returnsFalse() {
        Boolean updated = ReflectionTestUtils.invokeMethod(
            handler,
            "applyFailedRefundToCases",
            new CustomerOrder().paymentStatus(PaymentStatus.PAID),
            "  ",
            "reason"
        );
        assertThat(updated).isFalse();
    }
}
