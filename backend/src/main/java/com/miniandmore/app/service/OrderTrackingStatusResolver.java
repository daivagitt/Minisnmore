package com.miniandmore.app.service;

import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.enumeration.OrderStatus;
import com.miniandmore.app.domain.enumeration.PaymentStatus;
import org.apache.commons.lang3.StringUtils;

public final class OrderTrackingStatusResolver {

    private OrderTrackingStatusResolver() {}

    public static String resolve(CustomerOrder order) {
        String ledgerlyStatus = order.getLedgerlyOrderStatus();
        if (order.getStatus() == OrderStatus.CANCELLED || LedgerlyOrderStatus.isCancelled(ledgerlyStatus)) {
            return "cancelled";
        }
        // COD orders remain payment-pending until delivery, but their fulfillment still advances.
        if (order.getPaymentStatus() != PaymentStatus.PAID && order.getStatus() == OrderStatus.PENDING) {
            return "pending";
        }
        if (
            order.getStatus() == OrderStatus.FULFILLED ||
            LedgerlyOrderStatus.isDelivered(ledgerlyStatus) ||
            ZohoOrderStatus.isDelivered(order.getZohoOrderStatus())
        ) {
            return "delivered";
        }
        if (
            LedgerlyOrderStatus.isShipped(ledgerlyStatus) ||
            ZohoOrderStatus.isShipped(order.getZohoOrderStatus()) ||
            StringUtils.isNotBlank(order.getTrackingNumber()) ||
            StringUtils.isNotBlank(order.getShipmentDate())
        ) {
            return "shipped";
        }
        if (order.getStatus() == OrderStatus.CONFIRMED || order.getStatus() == OrderStatus.PENDING) {
            return "processing";
        }
        return "pending";
    }
}
