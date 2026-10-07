package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ExchangeRazorpaySettlementServiceTest {

    @Mock
    private OrderReturnRepository orderReturnRepository;

    @Mock
    private CustomerOrderRepository customerOrderRepository;

    @Mock
    private RazorpayRefundService razorpayRefundService;

    @Mock
    private OrderMailService orderMailService;

    @InjectMocks
    private ExchangeRazorpaySettlementService exchangeRazorpaySettlementService;

    @Test
    void recordRazorpayRefund_persistsSettlementAndInitiatesPaymentStatus() {
        OrderReturn orderReturn = pendingCheaperExchange();
        CustomerOrder order = orderReturn.getOrder();

        when(orderReturnRepository.findOneByReturnNumberForUpdate("RET-1")).thenReturn(Optional.of(orderReturn));
        when(razorpayRefundService.findExistingRefundForOrder(order, new BigDecimal("300.00"), RazorpayRefundService.PURPOSE_EXCHANGE_SETTLEMENT))
            .thenReturn(Optional.empty());
        when(razorpayRefundService.refundPayment(order, new BigDecimal("300.00"), RazorpayRefundService.PURPOSE_EXCHANGE_SETTLEMENT))
            .thenReturn("rfnd_diff_1");
        when(customerOrderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);

        OrderReturn result = exchangeRazorpaySettlementService.recordRazorpayRefund(
            "RET-1",
            new BigDecimal("300.00"),
            "admin",
            null
        );

        assertThat(result.getSettlementStatus()).isEqualTo(ExchangeSettlementStatus.REFUNDED);
        assertThat(result.getSettlementReference()).isEqualTo("rfnd_diff_1");
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.REFUND_INITIATED);
        verify(razorpayRefundService).refundPayment(
            eq(order),
            eq(new BigDecimal("300.00")),
            eq(RazorpayRefundService.PURPOSE_EXCHANGE_SETTLEMENT)
        );
    }

    @Test
    void recordRazorpayRefund_reusesListedRefundWithoutCallingRazorpayAgain() {
        OrderReturn orderReturn = pendingCheaperExchange();
        CustomerOrder order = orderReturn.getOrder();

        when(orderReturnRepository.findOneByReturnNumberForUpdate("RET-1")).thenReturn(Optional.of(orderReturn));
        when(
            razorpayRefundService.findExistingRefundForOrder(
                order,
                new BigDecimal("300.00"),
                RazorpayRefundService.PURPOSE_EXCHANGE_SETTLEMENT
            )
        )
            .thenReturn(Optional.of("rfnd_existing"));
        when(customerOrderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);

        OrderReturn result = exchangeRazorpaySettlementService.recordRazorpayRefund(
            "RET-1",
            new BigDecimal("300.00"),
            "admin",
            null
        );

        assertThat(result.getSettlementReference()).isEqualTo("rfnd_existing");
        assertThat(result.getSettlementStatus()).isEqualTo(ExchangeSettlementStatus.REFUNDED);
        verify(razorpayRefundService, never()).refundPayment(any(), any(), any());
    }

    @Test
    void recordRazorpayRefund_retriesAfterSaveFailure_reusesListedRefund() {
        OrderReturn firstAttempt = pendingCheaperExchange();
        OrderReturn retryAttempt = pendingCheaperExchange();

        when(orderReturnRepository.findOneByReturnNumberForUpdate("RET-1"))
            .thenReturn(Optional.of(firstAttempt))
            .thenReturn(Optional.of(retryAttempt));
        when(
            razorpayRefundService.findExistingRefundForOrder(
                any(),
                eq(new BigDecimal("300.00")),
                eq(RazorpayRefundService.PURPOSE_EXCHANGE_SETTLEMENT)
            )
        )
            .thenReturn(Optional.empty())
            .thenReturn(Optional.of("rfnd_diff_1"));
        when(
            razorpayRefundService.refundPayment(
                any(),
                eq(new BigDecimal("300.00")),
                eq(RazorpayRefundService.PURPOSE_EXCHANGE_SETTLEMENT)
            )
        )
            .thenReturn("rfnd_diff_1");
        when(customerOrderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderReturnRepository.save(any(OrderReturn.class))).thenThrow(new RuntimeException("db down")).thenAnswer(
            inv -> inv.getArgument(0)
        );

        assertThatThrownBy(() ->
            exchangeRazorpaySettlementService.recordRazorpayRefund("RET-1", new BigDecimal("300.00"), "admin", null)
        )
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("db down");

        OrderReturn result = exchangeRazorpaySettlementService.recordRazorpayRefund(
            "RET-1",
            new BigDecimal("300.00"),
            "admin",
            null
        );

        assertThat(result.getSettlementReference()).isEqualTo("rfnd_diff_1");
        assertThat(result.getSettlementStatus()).isEqualTo(ExchangeSettlementStatus.REFUNDED);
        verify(razorpayRefundService, times(1)).refundPayment(
            any(),
            eq(new BigDecimal("300.00")),
            eq(RazorpayRefundService.PURPOSE_EXCHANGE_SETTLEMENT)
        );
    }

    @Test
    void recordRazorpayRefund_pendingFailedRfndCreatesNewRefund() {
        OrderReturn orderReturn = pendingCheaperExchange();
        orderReturn.setSettlementReference("rfnd_failed");
        CustomerOrder order = orderReturn.getOrder();

        when(orderReturnRepository.findOneByReturnNumberForUpdate("RET-1")).thenReturn(Optional.of(orderReturn));
        when(
            razorpayRefundService.findExistingRefundForOrder(
                order,
                new BigDecimal("300.00"),
                RazorpayRefundService.PURPOSE_EXCHANGE_SETTLEMENT
            )
        )
            .thenReturn(Optional.empty());
        when(razorpayRefundService.refundPayment(order, new BigDecimal("300.00"), RazorpayRefundService.PURPOSE_EXCHANGE_SETTLEMENT))
            .thenReturn("rfnd_retry");
        when(customerOrderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);

        OrderReturn result = exchangeRazorpaySettlementService.recordRazorpayRefund(
            "RET-1",
            new BigDecimal("300.00"),
            "admin",
            null
        );

        assertThat(result.getSettlementReference()).isEqualTo("rfnd_retry");
        verify(razorpayRefundService).refundPayment(
            eq(order),
            eq(new BigDecimal("300.00")),
            eq(RazorpayRefundService.PURPOSE_EXCHANGE_SETTLEMENT)
        );
    }

    @Test
    void recordRazorpayRefund_alreadySettled_throwsConflict() {
        OrderReturn orderReturn = pendingCheaperExchange();
        orderReturn.setSettlementStatus(ExchangeSettlementStatus.REFUNDED);
        when(orderReturnRepository.findOneByReturnNumberForUpdate("RET-1")).thenReturn(Optional.of(orderReturn));

        assertThatThrownBy(() ->
            exchangeRazorpaySettlementService.recordRazorpayRefund("RET-1", new BigDecimal("300.00"), "admin", null)
        )
            .isInstanceOf(ResponseStatusException.class)
            .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
            .isEqualTo(HttpStatus.CONFLICT);
        verify(razorpayRefundService, never()).refundPayment(any(), any(), any());
    }

    private static OrderReturn pendingCheaperExchange() {
        CustomerOrder order = new CustomerOrder()
            .id(10L)
            .orderNumber("ORD-9001")
            .paymentMethod("online")
            .razorpayPaymentId("pay_live_1")
            .paymentStatus(PaymentStatus.PAID)
            .status(OrderStatus.FULFILLED)
            .total(new BigDecimal("1200.00"));
        OrderReturn orderReturn = new OrderReturn();
        orderReturn.setReturnNumber("RET-1");
        orderReturn.setRequestType(OrderRequestType.EXCHANGE);
        orderReturn.setStatus(OrderReturnStatus.ACCEPTED);
        orderReturn.setSettlementStatus(ExchangeSettlementStatus.PENDING);
        orderReturn.setPriceDifference(new BigDecimal("-300.00"));
        orderReturn.setOrder(order);
        return orderReturn;
    }
}
