package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class XpressBeesShipmentStatusTest {

    @Test
    void bookedAndScheduledStayAwaitingPickup() {
        assertThat(XpressBeesShipmentStatus.isAwaitingPickup("booked")).isTrue();
        assertThat(XpressBeesShipmentStatus.isAwaitingPickup("pickup scheduled")).isTrue();
        assertThat(XpressBeesShipmentStatus.isAwaitingPickup(null)).isTrue();
        assertThat(XpressBeesShipmentStatus.isPickedUpOrInTransit("booked")).isFalse();
    }

    @Test
    void pickupAndHubScansAreInTransit() {
        assertThat(XpressBeesShipmentStatus.isPickedUpOrInTransit("Picked Up")).isTrue();
        assertThat(XpressBeesShipmentStatus.isPickedUpOrInTransit("in transit")).isTrue();
        assertThat(XpressBeesShipmentStatus.isPickedUpOrInTransit("Reached at nearest hub")).isTrue();
        assertThat(XpressBeesShipmentStatus.isPickedUpOrInTransit("out for delivery")).isFalse();
        assertThat(XpressBeesShipmentStatus.isAwaitingPickup("picked_up")).isFalse();
    }

    @Test
    void outForDeliveryIsDetected() {
        assertThat(XpressBeesShipmentStatus.isOutForDelivery("OUT FOR DELIVERY")).isTrue();
        assertThat(XpressBeesShipmentStatus.isOutForDelivery("Out-for-delivery")).isTrue();
        assertThat(XpressBeesShipmentStatus.isOutForDelivery("OFD")).isTrue();
        assertThat(XpressBeesShipmentStatus.isOutForDelivery("in transit")).isFalse();
        assertThat(XpressBeesShipmentStatus.isOutForDelivery("Delivered")).isFalse();
        assertThat(XpressBeesShipmentStatus.isPickedUpOrInTransit("OFD")).isFalse();
    }

    @Test
    void deliveredIsTerminal() {
        assertThat(XpressBeesShipmentStatus.isDelivered("delivered")).isTrue();
        assertThat(XpressBeesShipmentStatus.isPickedUpOrInTransit("delivered")).isFalse();
    }

    @Test
    void undeliveredIsDetected() {
        assertThat(XpressBeesShipmentStatus.isUndelivered("undelivered")).isTrue();
        assertThat(XpressBeesShipmentStatus.isUndelivered("delivery_failed")).isTrue();
        assertThat(XpressBeesShipmentStatus.isUndelivered("delivery failed")).isTrue();
        assertThat(XpressBeesShipmentStatus.isUndelivered("failed_delivery")).isTrue();
        assertThat(XpressBeesShipmentStatus.isUndelivered("not_delivered")).isTrue();
        assertThat(XpressBeesShipmentStatus.isUndelivered("out for delivery")).isFalse();
        assertThat(XpressBeesShipmentStatus.isUndelivered("delivered")).isFalse();
        assertThat(XpressBeesShipmentStatus.isUndelivered("in transit")).isFalse();
        assertThat(XpressBeesShipmentStatus.isPickedUpOrInTransit("undelivered")).isFalse();
        assertThat(XpressBeesShipmentStatus.isPickedUpOrInTransit("delivery_failed")).isFalse();
    }

    @Test
    void preferForwardMilestone_keepsOutForDeliveryAheadOfHubScans() {
        assertThat(XpressBeesShipmentStatus.preferForwardMilestone("out_for_delivery", "in_transit")).isEqualTo(
            "out_for_delivery"
        );
        assertThat(XpressBeesShipmentStatus.preferForwardMilestone("out_for_delivery", "delivery_failed")).isEqualTo(
            "delivery_failed"
        );
        assertThat(XpressBeesShipmentStatus.preferForwardMilestone("delivery_failed", "out_for_delivery")).isEqualTo(
            "out_for_delivery"
        );
        assertThat(XpressBeesShipmentStatus.preferForwardMilestone("delivered", "out_for_delivery")).isEqualTo("delivered");
        assertThat(XpressBeesShipmentStatus.milestone("Picked Up")).isEqualTo("in_transit");
        assertThat(XpressBeesShipmentStatus.milestone("booked")).isEqualTo("awaiting_pickup");
    }

    @Test
    void cancelledAwbIsDetectedBeforePickup() {
        assertThat(XpressBeesShipmentStatus.isCancelled("Cancelled")).isTrue();
        assertThat(XpressBeesShipmentStatus.isCancelled("shipment cancelled")).isTrue();
        assertThat(XpressBeesShipmentStatus.isCancelled("AWB-Cancelled")).isTrue();
        assertThat(XpressBeesShipmentStatus.milestone("cancelled")).isEqualTo("carrier_cancelled");
        assertThat(XpressBeesShipmentStatus.isAwaitingPickup("cancelled")).isFalse();
        assertThat(XpressBeesShipmentStatus.isPickedUpOrInTransit("cancelled")).isFalse();
        assertThat(XpressBeesShipmentStatus.isCancelled("in transit")).isFalse();
    }

    @Test
    void preferForwardMilestone_carrierCancelOnlyReplacesAwaitingPickup() {
        assertThat(XpressBeesShipmentStatus.preferForwardMilestone("awaiting_pickup", "carrier_cancelled")).isEqualTo(
            "carrier_cancelled"
        );
        assertThat(XpressBeesShipmentStatus.preferForwardMilestone("carrier_cancelled", "awaiting_pickup")).isEqualTo(
            "carrier_cancelled"
        );
        assertThat(XpressBeesShipmentStatus.preferForwardMilestone("in_transit", "cancelled")).isEqualTo("in_transit");
        assertThat(XpressBeesShipmentStatus.preferForwardMilestone("carrier_cancelled", "in_transit")).isEqualTo(
            "in_transit"
        );
        assertThat(XpressBeesShipmentStatus.preferForwardMilestone("delivered", "cancelled")).isEqualTo("delivered");
    }
}
