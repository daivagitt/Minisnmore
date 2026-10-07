package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class XpressBeesReturnCarrierStatusTest {

    @Test
    void reverseMilestone_mapsExpressBeesPickupActivities() {
        assertThat(XpressBeesReturnCarrierStatus.reverseMilestone("Data Received")).isEqualTo("data_received");
        assertThat(XpressBeesReturnCarrierStatus.reverseMilestone("Out for Pickup")).isEqualTo("out_for_pickup");
        assertThat(XpressBeesReturnCarrierStatus.reverseMilestone("PickDone")).isEqualTo("picked_up");
        assertThat(XpressBeesReturnCarrierStatus.reverseMilestone("InTransit")).isEqualTo("in_transit");
        assertThat(XpressBeesReturnCarrierStatus.reverseMilestone("warehouse_received")).isEqualTo("delivered_to_warehouse");
        assertThat(XpressBeesReturnCarrierStatus.reverseMilestone("RPCancel")).isEqualTo("pickup_failed");
        assertThat(XpressBeesReturnCarrierStatus.reverseMilestone("customer_na")).isEqualTo("pickup_failed");
        assertThat(XpressBeesReturnCarrierStatus.reverseMilestone("failed")).isNull();
        assertThat(XpressBeesReturnCarrierStatus.reverseMilestone("cancelled")).isNull();
        assertThat(XpressBeesReturnCarrierStatus.reverseMilestone("mystery")).isNull();
    }

    @Test
    void preferReverse_doesNotRegressPastPhysicalPickup() {
        assertThat(XpressBeesReturnCarrierStatus.preferReverse("in_transit", "out_for_pickup")).isEqualTo("in_transit");
        assertThat(XpressBeesReturnCarrierStatus.preferReverse("picked_up", "pickup_failed")).isEqualTo("picked_up");
        assertThat(XpressBeesReturnCarrierStatus.preferReverse("out_for_pickup", "pickup_failed")).isEqualTo("pickup_failed");
        assertThat(XpressBeesReturnCarrierStatus.preferReverse("pickup_failed", "out_for_pickup")).isEqualTo("out_for_pickup");
        assertThat(XpressBeesReturnCarrierStatus.preferReverse("delivered_to_warehouse", "in_transit")).isEqualTo(
            "delivered_to_warehouse"
        );
    }

    @Test
    void outboundMilestone_mapsForwardExpressBeesScans() {
        assertThat(XpressBeesReturnCarrierStatus.outboundMilestone("booked")).isEqualTo("awaiting_pickup");
        assertThat(XpressBeesReturnCarrierStatus.outboundMilestone("Picked")).isEqualTo("in_transit");
        assertThat(XpressBeesReturnCarrierStatus.outboundMilestone("OUT FOR DELIVERY")).isEqualTo("out_for_delivery");
        assertThat(XpressBeesReturnCarrierStatus.outboundMilestone("undelivered")).isEqualTo("delivery_failed");
        assertThat(XpressBeesReturnCarrierStatus.outboundMilestone("delivered")).isEqualTo("delivered");
    }

    @Test
    void preferReverse_doesNotRegressFromPickedUpToDataReceived() {
        assertThat(XpressBeesReturnCarrierStatus.preferReverse("picked_up", "data_received")).isEqualTo("picked_up");
    }

    @Test
    void preferOutbound_keepsDeliveryFailedOverInTransit() {
        assertThat(XpressBeesReturnCarrierStatus.preferOutbound("delivery_failed", "in_transit")).isEqualTo("delivery_failed");
        assertThat(XpressBeesReturnCarrierStatus.preferOutbound("out_for_delivery", "in_transit")).isEqualTo("out_for_delivery");
        assertThat(XpressBeesReturnCarrierStatus.preferOutbound("delivery_failed", "out_for_delivery")).isEqualTo("out_for_delivery");
        assertThat(XpressBeesReturnCarrierStatus.preferOutbound("delivered", "out_for_delivery")).isEqualTo("delivered");
    }
}
