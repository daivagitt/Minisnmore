package com.miniandmore.app.service;

import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.OrderReturn;
import com.miniandmore.app.domain.enumeration.ExchangeSettlementStatus;
import com.miniandmore.app.domain.enumeration.OrderRequestType;
import com.miniandmore.app.domain.enumeration.OrderReturnStatus;
import com.miniandmore.app.domain.enumeration.PaymentStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.apache.commons.lang3.StringUtils;

/**
 * Single policy for exchange cash movement: Razorpay eligibility, remaining item refund after
 * settlement, and webhook totals. Keep convert-to-refund and payment_status on the same math.
 */
public final class ExchangeSettlementAmounts {

    private ExchangeSettlementAmounts() {}

    public static BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value.setScale(2, RoundingMode.HALF_UP);
    }

    public static boolean alreadySettled(ExchangeSettlementStatus status) {
        return (
            status == ExchangeSettlementStatus.COLLECTED ||
            status == ExchangeSettlementStatus.REFUNDED ||
            status == ExchangeSettlementStatus.WAIVED
        );
    }

    public static boolean canRefundViaRazorpay(OrderReturn orderReturn) {
        if (orderReturn == null || orderReturn.getRequestType() != OrderRequestType.EXCHANGE) {
            return false;
        }
        if (money(orderReturn.getPriceDifference()).signum() >= 0) {
            return false;
        }
        ExchangeSettlementStatus status = orderReturn.getSettlementStatus();
        if (status != null && status != ExchangeSettlementStatus.PENDING) {
            return false;
        }
        return isPrepaidRazorpayEligible(orderReturn.getOrder());
    }

    public static boolean isPrepaidRazorpayEligible(CustomerOrder order) {
        if (order == null || "cod".equalsIgnoreCase(order.getPaymentMethod())) {
            return false;
        }
        if (StringUtils.isBlank(order.getRazorpayPaymentId())) {
            return false;
        }
        PaymentStatus paymentStatus = order.getPaymentStatus();
        return (
            paymentStatus == PaymentStatus.PAID ||
            paymentStatus == PaymentStatus.REFUND_INITIATED ||
            paymentStatus == PaymentStatus.REFUND_FAILED ||
            paymentStatus == PaymentStatus.PARTIALLY_REFUNDED
        );
    }

    public static BigDecimal remainingItemRefund(OrderReturn orderReturn, BigDecimal originalItemAmount) {
        BigDecimal itemAmount = money(originalItemAmount);
        ExchangeSettlementStatus status = orderReturn == null ? null : orderReturn.getSettlementStatus();
        if (status == ExchangeSettlementStatus.COLLECTED) {
            return itemAmount.add(money(orderReturn.getSettlementAmount()));
        }
        if (status == ExchangeSettlementStatus.REFUNDED) {
            return itemAmount.subtract(money(orderReturn.getSettlementAmount())).max(BigDecimal.ZERO);
        }
        return itemAmount;
    }

    public static BigDecimal cashAlreadyRefunded(OrderReturn orderReturn) {
        if (orderReturn == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal line = BigDecimal.ZERO;
        if (orderReturn.getStatus() == OrderReturnStatus.REFUNDED) {
            line = line.add(money(orderReturn.getRefundAmount()));
        }
        if (orderReturn.getSettlementStatus() == ExchangeSettlementStatus.REFUNDED) {
            line = line.add(money(orderReturn.getSettlementAmount()));
        }
        return line;
    }
}
