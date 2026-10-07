package com.miniandmore.app.service;

import java.util.Set;
import org.apache.commons.lang3.StringUtils;

/**
 * Reverse-pickup milestones. Replacement shipments use {@link XpressBeesShipmentStatus}.
 *
 * Reverse journey: Data Received, Out for Pickup, PickDone, InTransit, warehouse delivery.
 * A generated AWB is only a booking. Physical pickup is a later scan.
 */
public final class XpressBeesReturnCarrierStatus {

    public static final String DATA_RECEIVED = "data_received";
    public static final String OUT_FOR_PICKUP = "out_for_pickup";
    public static final String PICKUP_FAILED = "pickup_failed";
    public static final String PICKED_UP = "picked_up";
    public static final String IN_TRANSIT = XpressBeesShipmentStatus.IN_TRANSIT;
    public static final String DELIVERED_TO_WAREHOUSE = "delivered_to_warehouse";

    public static final String AWAITING_PICKUP = XpressBeesShipmentStatus.AWAITING_PICKUP_MILESTONE;
    public static final String OUT_FOR_DELIVERY = XpressBeesShipmentStatus.OUT_FOR_DELIVERY;
    public static final String DELIVERY_FAILED = XpressBeesShipmentStatus.DELIVERY_FAILED;
    public static final String DELIVERED = XpressBeesShipmentStatus.DELIVERED_MILESTONE;

    private static final Set<String> REVERSE_DATA_RECEIVED = Set.of(
        "data_received",
        "dr",
        "pending",
        "booked",
        "created",
        "shipment_created",
        "pickup_pending",
        "ppd",
        "manifested",
        "manifest_generated",
        "label_generated",
        "pickup_scheduled",
        "scheduled",
        "pending_pickup"
    );

    private static final Set<String> REVERSE_OUT_FOR_PICKUP = Set.of("out_for_pickup", "ofp", "outforpickup");

    private static final Set<String> REVERSE_PICKED_UP = Set.of(
        "picked_up",
        "picked",
        "pickdone",
        "pick_done",
        "pickup_done",
        "pud"
    );

    private static final Set<String> REVERSE_IN_TRANSIT = Set.of(
        "in_transit",
        "intransit",
        "it",
        "reached_at_destination",
        "rad",
        "reached_at_origin"
    );

    private static final Set<String> REVERSE_DELIVERED = Set.of(
        "delivered",
        "dto",
        "delivered_to_origin",
        "rto_delivered",
        "warehouse_received",
        "wh_handover",
        "received"
    );

    /** Reverse-only failure codes. Generic failed/cancelled scans must not flip pickup to failed. */
    private static final Set<String> REVERSE_PICKUP_FAILED = Set.of(
        "pickup_failed",
        "pickup_not_done",
        "pnd",
        "rpcancel",
        "rp_cancel",
        "customer_cancelled",
        "customer_na",
        "no_product",
        "pickup_cancelled"
    );

    private XpressBeesReturnCarrierStatus() {}

    public static String normalize(String status) {
        return XpressBeesShipmentStatus.normalize(status);
    }

    /** Canonical reverse milestone, or null when the scan is not a known ExpressBees reverse status. */
    public static String reverseMilestone(String raw) {
        String normalized = normalize(raw);
        if (normalized.isBlank()) {
            return null;
        }
        if (REVERSE_DELIVERED.contains(normalized)) {
            return DELIVERED_TO_WAREHOUSE;
        }
        if (REVERSE_IN_TRANSIT.contains(normalized)) {
            return IN_TRANSIT;
        }
        if (REVERSE_PICKED_UP.contains(normalized)) {
            return PICKED_UP;
        }
        if (REVERSE_PICKUP_FAILED.contains(normalized)) {
            return PICKUP_FAILED;
        }
        if (REVERSE_OUT_FOR_PICKUP.contains(normalized)) {
            return OUT_FOR_PICKUP;
        }
        if (REVERSE_DATA_RECEIVED.contains(normalized)) {
            return DATA_RECEIVED;
        }
        return null;
    }

    /**
     * Keep the further milestone. A pickup failure can replace a booking or an out-for-pickup
     * scan, and a later out-for-pickup scan can replace that failure on reattempt.
     * Scans after physical pickup are not rolled back by an earlier booking or a failed attempt.
     */
    public static String preferReverse(String current, String incoming) {
        if (StringUtils.isBlank(incoming)) {
            return normalize(current);
        }
        String currentMilestone = normalize(current);
        if (StringUtils.isBlank(currentMilestone)) {
            return incoming;
        }
        if (DELIVERED_TO_WAREHOUSE.equals(currentMilestone)) {
            return currentMilestone;
        }
        if (PICKUP_FAILED.equals(currentMilestone) && rank(incoming) <= rank(OUT_FOR_PICKUP)) {
            return incoming;
        }
        if (rank(incoming) < rank(currentMilestone)) {
            return currentMilestone;
        }
        return incoming;
    }

    public static String outboundMilestone(String raw) {
        return XpressBeesShipmentStatus.milestone(raw);
    }

    public static String preferOutbound(String current, String incoming) {
        return XpressBeesShipmentStatus.preferForwardMilestone(current, incoming);
    }

    private static int rank(String milestone) {
        return switch (StringUtils.defaultString(milestone)) {
            case DATA_RECEIVED -> 1;
            case OUT_FOR_PICKUP, PICKUP_FAILED -> 2;
            case PICKED_UP -> 3;
            case IN_TRANSIT -> 4;
            case DELIVERED_TO_WAREHOUSE -> 5;
            default -> 0;
        };
    }
}
