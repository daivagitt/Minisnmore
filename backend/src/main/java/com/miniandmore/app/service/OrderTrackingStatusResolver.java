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
        if (isExpressBeesCarrierCancelled(order)) {
            return XpressBeesShipmentStatus.CARRIER_CANCELLED;
        }
        if (isExpressBeesAwaitingPickup(order)) {
            return "awaiting_pickup";
        }
        boolean localShipment = OrderShipmentService.hasLocalForwardShipment(order);
        if (
            order.getStatus() == OrderStatus.FULFILLED ||
            (!localShipment && LedgerlyOrderStatus.isDelivered(ledgerlyStatus)) ||
            (!localShipment && ZohoOrderStatus.isDelivered(order.getZohoOrderStatus()))
        ) {
            return "delivered";
        }
        if (localShipment && XpressBeesShipmentStatus.isOutForDelivery(order.getShipmentCarrierStatus())) {
            return "out_for_delivery";
        }
        if (localShipment && XpressBeesShipmentStatus.isUndelivered(order.getShipmentCarrierStatus())) {
            return "delivery_failed";
        }
        if (
            XpressBeesShipmentStatus.isPickedUpOrInTransit(order.getShipmentCarrierStatus()) ||
            StringUtils.isNotBlank(order.getTrackingNumber()) ||
            StringUtils.isNotBlank(order.getShipmentDate()) ||
            (!localShipment && LedgerlyOrderStatus.isShipped(ledgerlyStatus)) ||
            (!localShipment && ZohoOrderStatus.isShipped(order.getZohoOrderStatus()))
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

    /** True once the parcel has left the warehouse (shipped, out for delivery, delivery failed, or delivered). */
    public static boolean isDispatched(String trackingStatus) {
        return "shipped".equals(trackingStatus) || "out_for_delivery".equals(trackingStatus) || "delivery_failed".equals(trackingStatus) || "delivered".equals(trackingStatus);
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
        String carrierStatus = order.getShipmentCarrierStatus();
        return !XpressBeesShipmentStatus.isCancelled(carrierStatus) &&
            !XpressBeesShipmentStatus.isPickedUpOrInTransit(carrierStatus) &&
            !XpressBeesShipmentStatus.isOutForDelivery(carrierStatus) &&
            !XpressBeesShipmentStatus.isUndelivered(carrierStatus) &&
            !XpressBeesShipmentStatus.isDelivered(carrierStatus);
    }

    /**
     * ExpressBees voided the AWB before pickup. The customer order stays open so Admin
     * can rebook a shipment or cancel the order — same fork as exchange pickup-failed.
     */
    static boolean isExpressBeesCarrierCancelled(CustomerOrder order) {
        if (order == null || order.getShipmentLogisticsMode() != ReturnLogisticsMode.XPRESSBEES) {
            return false;
        }
        if (StringUtils.isBlank(order.getTrackingNumber()) || order.getShipmentCancelledAt() != null) {
            return false;
        }
        if (order.getStatus() == OrderStatus.CANCELLED || order.getStatus() == OrderStatus.FULFILLED) {
            return false;
        }
        return XpressBeesShipmentStatus.isCancelled(order.getShipmentCarrierStatus());
    }
}
