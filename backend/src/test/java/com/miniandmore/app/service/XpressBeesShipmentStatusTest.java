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
        assertThat(XpressBeesShipmentStatus.isPickedUpOrInTransit("out for delivery")).isTrue();
        assertThat(XpressBeesShipmentStatus.isAwaitingPickup("picked_up")).isFalse();
    }

    @Test
    void deliveredIsTerminal() {
        assertThat(XpressBeesShipmentStatus.isDelivered("delivered")).isTrue();
        assertThat(XpressBeesShipmentStatus.isPickedUpOrInTransit("delivered")).isFalse();
    }
}
