package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.OrderReturn;
import com.miniandmore.app.domain.enumeration.ExchangeSettlementStatus;
import com.miniandmore.app.domain.enumeration.OrderRequestType;
import com.miniandmore.app.domain.enumeration.OrderReturnStatus;
import com.miniandmore.app.domain.enumeration.PaymentStatus;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ExchangeSettlementAmountsTest {

    @Test
    void remainingItemRefund_subtractsAlreadyRefundedDifference() {
        OrderReturn orderReturn = exchange();
        orderReturn.setSettlementStatus(ExchangeSettlementStatus.REFUNDED);
        orderReturn.setSettlementAmount(new BigDecimal("120.00"));

        assertThat(ExchangeSettlementAmounts.remainingItemRefund(orderReturn, new BigDecimal("500.00")))
            .isEqualByComparingTo("380.00");
        assertThat(ExchangeSettlementAmounts.remainingItemRefund(orderReturn, new BigDecimal("120.00")))
            .isEqualByComparingTo("0.00");
        orderReturn.setPackageShortRefundAmount(new BigDecimal("50.00"));
        assertThat(ExchangeSettlementAmounts.remainingItemRefund(orderReturn, new BigDecimal("500.00")))
            .isEqualByComparingTo("330.00");
    }

    @Test
    void cashAlreadyRefunded_addsSettlementAndConvertedRefundWithoutOverlapOnZeroRemainder() {
        OrderReturn inFlight = exchange();
        inFlight.setSettlementStatus(ExchangeSettlementStatus.REFUNDED);
        inFlight.setSettlementAmount(new BigDecimal("120.00"));
        assertThat(ExchangeSettlementAmounts.cashAlreadyRefunded(inFlight)).isEqualByComparingTo("120.00");

        OrderReturn converted = exchange();
        converted.setStatus(OrderReturnStatus.REFUNDED);
        converted.setRefundAmount(new BigDecimal("380.00"));
        converted.setSettlementStatus(ExchangeSettlementStatus.REFUNDED);
        converted.setSettlementAmount(new BigDecimal("120.00"));
        assertThat(ExchangeSettlementAmounts.cashAlreadyRefunded(converted)).isEqualByComparingTo("500.00");

        OrderReturn fullySettledThenConverted = exchange();
        fullySettledThenConverted.setStatus(OrderReturnStatus.REFUNDED);
        fullySettledThenConverted.setRefundAmount(BigDecimal.ZERO);
        fullySettledThenConverted.setSettlementStatus(ExchangeSettlementStatus.REFUNDED);
        fullySettledThenConverted.setSettlementAmount(new BigDecimal("120.00"));
        assertThat(ExchangeSettlementAmounts.cashAlreadyRefunded(fullySettledThenConverted)).isEqualByComparingTo("120.00");
    }

    @Test
    void remainingPrepaidRefundable_subtractsForwardAndExchangePackageShort() {
        CustomerOrder order = new CustomerOrder().id(1L).total(new BigDecimal("1000.00"));
        order.setPackageShortRefundAmount(new BigDecimal("100.00"));
        order.setPackageShortRazorpayRefundId("rfnd_fwd");

        OrderReturn exchange = exchange();
        exchange.setPackageShortRefundAmount(new BigDecimal("200.00"));
        exchange.setPackageShortRazorpayRefundId("rfnd_ex");

        assertThat(ExchangeSettlementAmounts.remainingPrepaidRefundable(order, java.util.List.of(exchange)))
            .isEqualByComparingTo("700.00");
    }

    @Test
    void prepaidRefundsAlreadyIssued_ignoresClearedPackageShortAfterWebhookFailure() {
        CustomerOrder order = new CustomerOrder().id(1L).total(new BigDecimal("1000.00"));
        order.setPaymentStatus(PaymentStatus.REFUND_INITIATED);

        OrderReturn exchange = exchange();
        exchange.setOrder(order);

        assertThat(ExchangeSettlementAmounts.prepaidRefundsAlreadyIssued(order, java.util.List.of(exchange)))
            .isEqualByComparingTo("0.00");
        assertThat(ExchangeSettlementAmounts.remainingPrepaidRefundable(order, java.util.List.of(exchange)))
            .isEqualByComparingTo("1000.00");
    }

    @Test
    void prepaidRefundsAlreadyIssued_stillCountsSuccessfulPackageShortWhenOrderRefundFailed() {
        CustomerOrder order = new CustomerOrder().id(1L).total(new BigDecimal("1000.00"));
        order.setPaymentStatus(PaymentStatus.REFUND_FAILED);
        order.setPackageShortRefundAmount(new BigDecimal("300.00"));
        order.setPackageShortRazorpayRefundId("rfnd_ok");

        assertThat(ExchangeSettlementAmounts.remainingPrepaidRefundable(order, java.util.List.of()))
            .isEqualByComparingTo("700.00");
    }

    @Test
    void canRefundViaRazorpay_requiresPrepaidPaymentId() {
        OrderReturn orderReturn = exchange();
        orderReturn.setPriceDifference(new BigDecimal("-300.00"));
        orderReturn.setSettlementStatus(ExchangeSettlementStatus.PENDING);
        orderReturn.getOrder().paymentMethod("online").paymentStatus(PaymentStatus.PAID);

        assertThat(ExchangeSettlementAmounts.canRefundViaRazorpay(orderReturn)).isFalse();
        orderReturn.getOrder().razorpayPaymentId("pay_1");
        assertThat(ExchangeSettlementAmounts.canRefundViaRazorpay(orderReturn)).isTrue();
    }

    private static OrderReturn exchange() {
        CustomerOrder order = new CustomerOrder().id(1L).orderNumber("ORD-1");
        OrderReturn orderReturn = new OrderReturn();
        orderReturn.setRequestType(OrderRequestType.EXCHANGE);
        orderReturn.setStatus(OrderReturnStatus.ACCEPTED);
        orderReturn.setOrder(order);
        return orderReturn;
    }
}
