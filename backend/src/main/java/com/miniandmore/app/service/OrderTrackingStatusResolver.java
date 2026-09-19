package com.miniandmore.app.service;

import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.enumeration.OrderStatus;
import com.miniandmore.app.domain.enumeration.PaymentStatus;
import com.miniandmore.app.domain.enumeration.ReturnLogisticsMode;
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
        if (isExpressBeesAwaitingPickup(order)) {
            return "awaiting_pickup";
        }
        if (
            LedgerlyOrderStatus.isShipped(ledgerlyStatus) ||
            ZohoOrderStatus.isShipped(order.getZohoOrderStatus()) ||
            XpressBeesShipmentStatus.isPickedUpOrInTransit(order.getShipmentCarrierStatus()) ||
            StringUtils.isNotBlank(order.getTrackingNumber()) ||
            StringUtils.isNotBlank(order.getShipmentDate())
        ) {
            return "shipped";
        }
        if (order.getStatus() == OrderStatus.HAND_WRAPPED) {
            return "hand_wrapped";
        }
        if (order.getStatus() == OrderStatus.CONFIRMED || order.getStatus() == OrderStatus.PENDING) {
            // Payment alone must never imply that wrapping is complete. A paid PENDING row is an
            // inconsistent/transitional record, but "confirmed" is still safer than advancing it.
            return "confirmed";
        }
        return "pending";
    }

    /**
     * ExpressBees AWB generation only prints a label. Customer "shipped" waits for a pickup scan.
     */
    static boolean isExpressBeesAwaitingPickup(CustomerOrder order) {
        if (order.getShipmentLogisticsMode() != ReturnLogisticsMode.XPRESSBEES) {
            return false;
        }
        if (StringUtils.isBlank(order.getTrackingNumber()) || order.getShipmentCancelledAt() != null) {
            return false;
        }
        return !XpressBeesShipmentStatus.isPickedUpOrInTransit(order.getShipmentCarrierStatus()) &&
            !XpressBeesShipmentStatus.isDelivered(order.getShipmentCarrierStatus());
    }
}
