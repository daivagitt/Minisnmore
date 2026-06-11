package com.miniandmore.app.service;

import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.enumeration.OrderStatus;
import com.miniandmore.app.domain.enumeration.PaymentStatus;
import org.apache.commons.lang3.StringUtils;

public final class OrderTrackingStatusResolver {

    private OrderTrackingStatusResolver() {}

    public static String resolve(CustomerOrder order) {
        if (order.getStatus() == OrderStatus.CANCELLED || "void".equalsIgnoreCase(StringUtils.defaultString(order.getZohoOrderStatus()))) {
            return "cancelled";
        }
        if (order.getPaymentStatus() != PaymentStatus.PAID) {
            return "pending";
        }
        if (order.getStatus() == OrderStatus.FULFILLED || "closed".equalsIgnoreCase(StringUtils.defaultString(order.getZohoOrderStatus()))) {
            return "delivered";
        }
        if (StringUtils.isNotBlank(order.getTrackingNumber()) || StringUtils.isNotBlank(order.getShipmentDate())) {
            return "shipped";
        }
        if (order.getStatus() == OrderStatus.CONFIRMED || order.getStatus() == OrderStatus.PENDING) {
            return "processing";
        }
        return "pending";
    }
}
