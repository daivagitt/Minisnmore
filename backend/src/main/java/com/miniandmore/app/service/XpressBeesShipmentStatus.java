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

    public static final String AWAITING_PICKUP_MILESTONE = "awaiting_pickup";
    public static final String IN_TRANSIT = "in_transit";
    public static final String OUT_FOR_DELIVERY = "out_for_delivery";
    public static final String DELIVERY_FAILED = "delivery_failed";
    public static final String DELIVERED_MILESTONE = "delivered";

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
        "scheduled",
        AWAITING_PICKUP_MILESTONE
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

    public static boolean isOutForDelivery(String status) {
        String normalized = normalize(status);
        return normalized.equals("ofd") || normalized.contains("out_for_delivery");
    }

    public static boolean isUndelivered(String status) {
        String normalized = normalize(status);
        return normalized.contains("undelivered")
            || normalized.contains("not_delivered")
            || normalized.contains("delivery_failed")
            || normalized.contains("failed_delivery");
    }

    public static boolean isPickedUpOrInTransit(String status) {
        String normalized = normalize(status);
        // Exclusive buckets: OFD / undelivered / delivered are never "in transit".
        if (
            normalized.isBlank() ||
            isAwaitingPickup(normalized) ||
            isDelivered(normalized) ||
            isOutForDelivery(normalized) ||
            isUndelivered(normalized)
        ) {
            return false;
        }
        return (
            normalized.contains("picked") ||
            normalized.contains("transit") ||
            normalized.contains("hub") ||
            normalized.contains("dispatched") ||
            normalized.contains("shipped") ||
            normalized.contains("manifested") ||
            normalized.contains("rto")
        );
    }

    /** Canonical forward milestone, or null when the scan is not a known ExpressBees status. */
    public static String milestone(String raw) {
        if (StringUtils.isBlank(raw)) {
            return null;
        }
        if (isDelivered(raw)) {
            return DELIVERED_MILESTONE;
        }
        if (isUndelivered(raw)) {
            return DELIVERY_FAILED;
        }
        if (isOutForDelivery(raw)) {
            return OUT_FOR_DELIVERY;
        }
        if (isPickedUpOrInTransit(raw)) {
            return IN_TRANSIT;
        }
        if (isAwaitingPickup(raw)) {
            return AWAITING_PICKUP_MILESTONE;
        }
        return null;
    }

    /**
     * Same stickiness for customer orders and exchange replacements: out for delivery is not
     * overwritten by a hub scan, a failed attempt can return to out for delivery, and delivered is terminal.
     */
    public static String preferForwardMilestone(String current, String incoming) {
        if (StringUtils.isBlank(incoming)) {
            return canonicalOrNormalized(current);
        }
        String currentMilestone = canonicalOrNormalized(current);
        if (StringUtils.isBlank(currentMilestone)) {
            return incoming;
        }
        if (DELIVERED_MILESTONE.equals(currentMilestone) || DELIVERED_MILESTONE.equals(incoming)) {
            return DELIVERED_MILESTONE.equals(incoming) ? incoming : currentMilestone;
        }
        if (OUT_FOR_DELIVERY.equals(currentMilestone)) {
            if (OUT_FOR_DELIVERY.equals(incoming) || DELIVERY_FAILED.equals(incoming)) {
                return incoming;
            }
            return currentMilestone;
        }
        if (
            DELIVERY_FAILED.equals(currentMilestone) &&
            !OUT_FOR_DELIVERY.equals(incoming) &&
            forwardRank(incoming) < forwardRank(currentMilestone)
        ) {
            return currentMilestone;
        }
        if (forwardRank(incoming) < forwardRank(currentMilestone)) {
            return currentMilestone;
        }
        return incoming;
    }

    public static boolean sameForwardMilestone(String left, String right) {
        String leftMilestone = milestone(left);
        String rightMilestone = milestone(right);
        if (leftMilestone != null && rightMilestone != null) {
            return leftMilestone.equals(rightMilestone);
        }
        return normalize(left).equals(normalize(right));
    }

    private static String canonicalOrNormalized(String status) {
        String canonical = milestone(status);
        return canonical != null ? canonical : normalize(status);
    }

    private static int forwardRank(String milestone) {
        return switch (StringUtils.defaultString(milestone)) {
            case AWAITING_PICKUP_MILESTONE -> 1;
            case IN_TRANSIT -> 2;
            case OUT_FOR_DELIVERY, DELIVERY_FAILED -> 3;
            case DELIVERED_MILESTONE -> 4;
            default -> 0;
        };
    }
}
