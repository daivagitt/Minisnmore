package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.OrderReturn;
import com.miniandmore.app.domain.enumeration.ExchangeSettlementStatus;
import com.miniandmore.app.domain.enumeration.OrderRequestType;
import com.miniandmore.app.domain.enumeration.OrderReturnStatus;
import com.miniandmore.app.domain.enumeration.OrderStatus;
import com.miniandmore.app.domain.enumeration.PaymentStatus;
import com.miniandmore.app.repository.CustomerOrderRepository;
import com.miniandmore.app.repository.OrderReturnRepository;
import java.math.BigDecimal;
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
class ExchangeRazorpaySettlementResidualMatrixTest {

    @Mock
    private OrderReturnRepository orderReturnRepository;

    @Mock
    private CustomerOrderRepository customerOrderRepository;

    @Mock
    private RazorpayRefundService razorpayRefundService;

    @Mock
    private OrderMailService orderMailService;

    @Mock
    private OrderEventService orderEventService;

    private ExchangeRazorpaySettlementService service;

    @BeforeEach
    void setUp() {
        service = new ExchangeRazorpaySettlementService(
            orderReturnRepository,
            customerOrderRepository,
            razorpayRefundService,
            orderMailService,
            orderEventService
        );
        when(orderReturnRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(razorpayRefundService.findExistingRefundForOrder(any(), any(), any())).thenReturn(Optional.empty());
        when(razorpayRefundService.refundPayment(any(), any(), any())).thenReturn("rfnd_new");
    }

    @Test
    void recordRazorpayRefund_zeroAmountUsesExpectedAndApprovedStatus() {
        OrderReturn orderReturn = base();
        orderReturn.setStatus(OrderReturnStatus.APPROVED);
        when(orderReturnRepository.findOneByReturnNumberForUpdate("RET-X")).thenReturn(Optional.of(orderReturn));

        OrderReturn saved = service.recordRazorpayRefund("RET-X", BigDecimal.ZERO, "admin", "note");

        assertThat(saved.getSettlementAmount()).isEqualByComparingTo("300.00");
        verify(razorpayRefundService).refundPayment(orderReturn.getOrder(), new BigDecimal("300.00"), RazorpayRefundService.PURPOSE_EXCHANGE_SETTLEMENT);
        verify(customerOrderRepository).save(orderReturn.getOrder());
        assertThat(orderReturn.getOrder().getPaymentStatus()).isEqualTo(PaymentStatus.REFUND_INITIATED);
    }

    @Test
    void recordRazorpayRefund_alreadyInitiatedStatusDoesNotRewritePayment() {
        OrderReturn orderReturn = base();
        orderReturn.getOrder().setPaymentStatus(PaymentStatus.REFUND_INITIATED);
        when(orderReturnRepository.findOneByReturnNumberForUpdate("RET-X")).thenReturn(Optional.of(orderReturn));

        service.recordRazorpayRefund("RET-X", new BigDecimal("300.00"), null, null);

        verify(customerOrderRepository, never()).save(any());
        assertThat(orderReturn.getOrder().getPaymentStatus()).isEqualTo(PaymentStatus.REFUND_INITIATED);
        assertThat(orderReturn.getSettledBy()).isEqualTo("system");
    }

    @Test
    void recordRazorpayRefund_refundFailedStatusIsUpdated_codLikeSkippedByEligibilityAlready() {
        OrderReturn orderReturn = base();
        orderReturn.getOrder().setPaymentStatus(PaymentStatus.REFUND_FAILED);
        when(orderReturnRepository.findOneByReturnNumberForUpdate("RET-X")).thenReturn(Optional.of(orderReturn));

        service.recordRazorpayRefund("RET-X", new BigDecimal("300.00"), "admin", "more");

        assertThat(orderReturn.getOrder().getPaymentStatus()).isEqualTo(PaymentStatus.REFUND_INITIATED);
        assertThat(orderReturn.getAdminNotes()).contains("more");
    }

    @Test
    void recordRazorpayRefund_pendingRfndPrefixCreatesNewRefund() {
        OrderReturn orderReturn = base();
        orderReturn.setSettlementReference("rfnd_old");
        orderReturn.setSettlementStatus(ExchangeSettlementStatus.PENDING);
        when(orderReturnRepository.findOneByReturnNumberForUpdate("RET-X")).thenReturn(Optional.of(orderReturn));

        OrderReturn saved = service.recordRazorpayRefund("RET-X", new BigDecimal("300.00"), "admin", null);

        assertThat(saved.getSettlementReference()).isEqualTo("rfnd_new");
        verify(razorpayRefundService).refundPayment(any(), any(), any());
    }

    @Test
    void mergeNotes_coversEmptyLeftRightAndBoth() {
        assertThat((String) ReflectionTestUtils.invokeMethod(ExchangeRazorpaySettlementService.class, "mergeNotes", null, " right "))
            .isEqualTo("right");
        assertThat((String) ReflectionTestUtils.invokeMethod(ExchangeRazorpaySettlementService.class, "mergeNotes", "left", "  "))
            .isEqualTo("left");
        assertThat((String) ReflectionTestUtils.invokeMethod(ExchangeRazorpaySettlementService.class, "mergeNotes", "left", "right"))
            .isEqualTo("left\nright");
        assertThat((String) ReflectionTestUtils.invokeMethod(ExchangeRazorpaySettlementService.class, "mergeNotes", "", "")).isEmpty();
    }

    private static OrderReturn base() {
        CustomerOrder order = new CustomerOrder()
            .id(1L)
            .orderNumber("ORD-1")
            .paymentMethod("online")
            .razorpayPaymentId("pay_1")
            .paymentStatus(PaymentStatus.PAID)
            .status(OrderStatus.FULFILLED);
        OrderReturn orderReturn = new OrderReturn();
        orderReturn.setReturnNumber("RET-X");
        orderReturn.setRequestType(OrderRequestType.EXCHANGE);
        orderReturn.setStatus(OrderReturnStatus.ACCEPTED);
        orderReturn.setSettlementStatus(ExchangeSettlementStatus.PENDING);
        orderReturn.setPriceDifference(new BigDecimal("-300.00"));
        orderReturn.setOrder(order);
        return orderReturn;
    }
}
