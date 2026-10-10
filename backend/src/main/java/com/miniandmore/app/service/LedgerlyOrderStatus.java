package com.miniandmore.app.service;

import com.miniandmore.app.domain.CustomerOrder;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;

/**
 * Normalizes Ledgerly commerce order / shipment statuses for storefront tracking.
 * Ledgerly may return payment status ({@code Paid}) separately from fulfillment ({@code Delivered}).
 */
public final class LedgerlyOrderStatus {

    private LedgerlyOrderStatus() {}

    public static String resolveFromPullResponse(Map<String, Object> response) {
        for (String key : new String[] { "fulfillmentStatus", "deliveryStatus", "shipmentStatus", "status" }) {
            String value = normalize(stringValue(response.get(key)));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    public static void applyTrackingFields(CustomerOrder order, Map<String, Object> response) {
        String trackingNumber = firstNonBlank(
            stringValue(response.get("trackingNumber")),
            stringValue(response.get("tracking_number"))
        );
        if (trackingNumber != null) {
            order.setTrackingNumber(trackingNumber);
        }
        String trackingCarrier = firstNonBlank(
            stringValue(response.get("trackingCarrier")),
            stringValue(response.get("tracking_carrier"))
        );
        if (trackingCarrier != null) {
            order.setTrackingCarrier(trackingCarrier);
        }
        String shipmentDate = firstNonBlank(
            stringValue(response.get("shipmentDate")),
            stringValue(response.get("shipment_date"))
        );
        if (shipmentDate != null) {
            order.setShipmentDate(shipmentDate);
        }
        if (isDelivered(resolveFromPullResponse(response))) {
            OrderDeliverySupport.markDelivered(order, response);
        }
    }

    public static boolean shouldReplace(String current, String incoming) {
        String normalizedCurrent = normalize(current);
        String normalizedIncoming = normalize(incoming);
        if (normalizedIncoming == null) {
            return false;
        }
        if (normalizedCurrent == null) {
            return true;
        }
        return rank(normalizedIncoming) >= rank(normalizedCurrent);
    }

    public static boolean isCancelled(String status) {
        String normalized = normalize(status);
        return normalized != null && (normalized.equals("void") || normalized.equals("cancelled") || normalized.equals("canceled"));
    }

    public static boolean isDelivered(String status) {
        String normalized = normalize(status);
        return normalized != null
            && (normalized.equals("delivered")
                || normalized.equals("closed")
                || normalized.equals("fulfilled")
                || normalized.equals("complete")
                || normalized.equals("completed"));
    }

    public static boolean isShipped(String status) {
        String normalized = normalize(status);
        return normalized != null
            && (normalized.equals("shipped")
                || normalized.equals("in_transit")
                || normalized.equals("in-transit")
                || normalized.equals("dispatched")
                || normalized.equals("on_the_way")
                || normalized.equals("on-the-way"));
    }

    static int rank(String status) {
        if (status == null) {
            return 0;
        }
        if (isDelivered(status)) {
            return 40;
        }
        if (isShipped(status)) {
            return 30;
        }
        if (status.equals("paid") || status.equals("processing") || status.equals("confirmed") || status.equals("open")) {
            return 20;
        }
        return 10;
    }

    private static String normalize(String status) {
        if (status == null) {
            return null;
        }
        String trimmed = status.trim().toLowerCase().replace('-', '_');
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.isNotBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }
}
