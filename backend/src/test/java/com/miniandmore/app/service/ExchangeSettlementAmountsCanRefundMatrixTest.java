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

class ExchangeSettlementAmountsCanRefundMatrixTest {

    @Test
    void canRefundViaRazorpay_coversNullTypeDifferenceAndSettlement() {
        assertThat(ExchangeSettlementAmounts.canRefundViaRazorpay(null)).isFalse();

        OrderReturn notExchange = base();
        notExchange.setRequestType(OrderRequestType.RETURN);
        assertThat(ExchangeSettlementAmounts.canRefundViaRazorpay(notExchange)).isFalse();

        OrderReturn positive = base();
        positive.setPriceDifference(new BigDecimal("10.00"));
        assertThat(ExchangeSettlementAmounts.canRefundViaRazorpay(positive)).isFalse();

        OrderReturn settled = base();
        settled.setSettlementStatus(ExchangeSettlementStatus.REFUNDED);
        assertThat(ExchangeSettlementAmounts.canRefundViaRazorpay(settled)).isFalse();

        OrderReturn pendingNullStatus = base();
        pendingNullStatus.setSettlementStatus(null);
        assertThat(ExchangeSettlementAmounts.canRefundViaRazorpay(pendingNullStatus)).isTrue();

        OrderReturn eligible = base();
        assertThat(ExchangeSettlementAmounts.canRefundViaRazorpay(eligible)).isTrue();

        OrderReturn cod = base();
        cod.getOrder().setPaymentMethod("cod");
        assertThat(ExchangeSettlementAmounts.canRefundViaRazorpay(cod)).isFalse();
    }

    @Test
    void remainingItemRefundAndCashAlreadyRefunded() {
        assertThat(ExchangeSettlementAmounts.remainingItemRefund(null, new BigDecimal("10"))).isEqualByComparingTo("10.00");
        OrderReturn collected = base();
        collected.setSettlementStatus(ExchangeSettlementStatus.COLLECTED);
        collected.setSettlementAmount(new BigDecimal("2"));
        assertThat(ExchangeSettlementAmounts.remainingItemRefund(collected, new BigDecimal("10"))).isEqualByComparingTo("12.00");

        OrderReturn refunded = base();
        refunded.setSettlementStatus(ExchangeSettlementStatus.REFUNDED);
        refunded.setSettlementAmount(new BigDecimal("3"));
        refunded.setStatus(OrderReturnStatus.REFUNDED);
        assertThat(ExchangeSettlementAmounts.remainingItemRefund(refunded, new BigDecimal("10"))).isEqualByComparingTo("7.00");
        assertThat(ExchangeSettlementAmounts.cashAlreadyRefunded(null)).isEqualByComparingTo("0");
        assertThat(ExchangeSettlementAmounts.cashAlreadyRefunded(refunded)).isGreaterThanOrEqualTo(BigDecimal.ZERO);
    }

    private static OrderReturn base() {
        CustomerOrder order = new CustomerOrder()
            .paymentMethod("online")
            .razorpayPaymentId("pay_1")
            .paymentStatus(PaymentStatus.PAID);
        OrderReturn orderReturn = new OrderReturn();
        orderReturn.setRequestType(OrderRequestType.EXCHANGE);
        orderReturn.setPriceDifference(new BigDecimal("-40.00"));
        orderReturn.setSettlementStatus(ExchangeSettlementStatus.PENDING);
        orderReturn.setOrder(order);
        return orderReturn;
    }
}
