package com.miniandmore.app.service;

import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.OrderReturn;
import com.miniandmore.app.domain.enumeration.ExchangeSettlementStatus;
import com.miniandmore.app.domain.enumeration.OrderRequestType;
import com.miniandmore.app.domain.enumeration.OrderReturnStatus;
import com.miniandmore.app.domain.enumeration.PaymentStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
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

    /**
     * Forward package-short cash counted against the original Razorpay capture (excludes failed online attempts).
     */
    public static BigDecimal forwardPackageShortCash(CustomerOrder order) {
        if (order == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal amount = money(order.getPackageShortRefundAmount());
        if (amount.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return amount;
    }

    public static BigDecimal packageShortCashOnReturn(OrderReturn orderReturn) {
        if (orderReturn == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal amount = money(orderReturn.getPackageShortRefundAmount());
        if (amount.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return amount;
    }

    public static BigDecimal prepaidRefundsAlreadyIssued(CustomerOrder order, Collection<OrderReturn> returns) {
        BigDecimal issued = forwardPackageShortCash(order);
        if (returns == null) {
            return issued;
        }
        for (OrderReturn orderReturn : returns) {
            issued = issued.add(cashAlreadyRefunded(orderReturn));
        }
        return issued;
    }

    public static BigDecimal remainingPrepaidRefundable(CustomerOrder order, Collection<OrderReturn> returns) {
        if (order == null) {
            return BigDecimal.ZERO;
        }
        return money(order.getTotal()).subtract(prepaidRefundsAlreadyIssued(order, returns)).max(BigDecimal.ZERO);
    }

    public static boolean isRefundBookedOnOrder(CustomerOrder order, Collection<OrderReturn> returns, String refundId) {
        if (StringUtils.isBlank(refundId)) {
            return false;
        }
        if (order != null && refundId.equals(order.getPackageShortRazorpayRefundId())) {
            return true;
        }
        if (returns == null) {
            return false;
        }
        for (OrderReturn orderReturn : returns) {
            if (
                refundId.equals(orderReturn.getPackageShortRazorpayRefundId()) ||
                refundId.equals(orderReturn.getRazorpayRefundId()) ||
                refundId.equals(orderReturn.getSettlementReference())
            ) {
                return true;
            }
        }
        return false;
    }

    public static BigDecimal remainingItemRefund(OrderReturn orderReturn, BigDecimal originalItemAmount) {
        BigDecimal itemAmount = money(originalItemAmount);
        ExchangeSettlementStatus status = orderReturn == null ? null : orderReturn.getSettlementStatus();
        if (status == ExchangeSettlementStatus.COLLECTED) {
            itemAmount = itemAmount.add(money(orderReturn.getSettlementAmount()));
        } else if (status == ExchangeSettlementStatus.REFUNDED) {
            itemAmount = itemAmount.subtract(money(orderReturn.getSettlementAmount())).max(BigDecimal.ZERO);
        }
        return itemAmount.subtract(money(orderReturn == null ? null : orderReturn.getPackageShortRefundAmount())).max(BigDecimal.ZERO);
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
        line = line.add(packageShortCashOnReturn(orderReturn));
        return line;
    }
}
