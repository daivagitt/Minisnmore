package com.miniandmore.app.service;

import java.util.Locale;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;

/**
 * Maps ExpressBees forward-shipment milestones onto customer-facing tracking.
 *
 * AWB generation is only a label/booking. "Shipped" starts at the first physical scan
 * (pickup / in-transit / out for delivery).
 */
public final class XpressBeesShipmentStatus {

    private static final Set<String> AWAITING_PICKUP = Set.of(
        "booked",
        "pending",
        "created",
        "data_received",
        "shipment_created",
        "label_generated",
        "manifest_generated",
        "pickup_scheduled",
        "pending_pickup",
        "out_for_pickup",
        "scheduled"
    );

    private static final Set<String> DELIVERED = Set.of(
        "delivered",
        "delivery_done",
        "delivered_to_customer"
    );

    private XpressBeesShipmentStatus() {}

    public static String normalize(String status) {
        return StringUtils.defaultString(status)
            .trim()
            .toLowerCase(Locale.ROOT)
            .replace('-', '_')
            .replace(' ', '_');
    }

    public static boolean isAwaitingPickup(String status) {
        String normalized = normalize(status);
        return normalized.isBlank() || AWAITING_PICKUP.contains(normalized);
    }

    public static boolean isDelivered(String status) {
        return DELIVERED.contains(normalize(status));
    }

    public static boolean isPickedUpOrInTransit(String status) {
        String normalized = normalize(status);
        if (normalized.isBlank() || isAwaitingPickup(normalized) || isDelivered(normalized)) {
            return false;
        }
        if (normalized.contains("undelivered") || normalized.contains("failed")) {
            return true;
        }
        return (
            normalized.contains("picked") ||
            normalized.contains("transit") ||
            normalized.contains("hub") ||
            normalized.contains("dispatched") ||
            normalized.contains("shipped") ||
            normalized.contains("ofd") ||
            normalized.contains("out_for_delivery") ||
            normalized.contains("manifested") ||
            normalized.contains("rto")
        );
    }
}
