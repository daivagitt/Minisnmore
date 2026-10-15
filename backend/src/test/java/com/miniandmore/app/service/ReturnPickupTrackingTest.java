package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.miniandmore.app.domain.enumeration.ReturnLogisticsMode;
import org.junit.jupiter.api.Test;

class ReturnPickupTrackingTest {

    @Test
    void prefersStoredTrackingUrl() {
        assertThat(
            ReturnPickupTracking.url("https://track.example/REV", "XB1", ReturnLogisticsMode.MANUAL, "Delhivery")
        ).isEqualTo("https://track.example/REV");
    }

    @Test
    void buildsExpressBeesUrlFromReverseAwb() {
        assertThat(ReturnPickupTracking.url(null, " XB1 ", ReturnLogisticsMode.XPRESSBEES, null))
            .isEqualTo("https://shipmentv2.xpressbees.com/orders/tracking/XB1");
    }

    @Test
    void doesNotInventExpressBeesUrlForOtherCarriers() {
        assertThat(ReturnPickupTracking.url(null, "DLV-1", ReturnLogisticsMode.MANUAL, "Delhivery")).isNull();
    }

    @Test
    void encodesAwbForPathUse() {
        assertThat(ReturnPickupTracking.url(null, "XB 1", ReturnLogisticsMode.XPRESSBEES, null))
            .isEqualTo("https://shipmentv2.xpressbees.com/orders/tracking/XB%201");
    }

    @Test
    void returnsNullWhenNeitherUrlNorAwbPresent() {
        assertThat(ReturnPickupTracking.url("  ", null, ReturnLogisticsMode.XPRESSBEES, "XpressBees")).isNull();
    }
}
