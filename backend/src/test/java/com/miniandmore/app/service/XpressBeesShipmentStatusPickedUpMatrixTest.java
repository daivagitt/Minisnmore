package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class XpressBeesShipmentStatusPickedUpMatrixTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { "   ", "ofd", "out_for_delivery", "delivered", "undelivered", "delivery_failed", "pending_pickup" })
    void isPickedUpOrInTransit_falseForExclusiveBuckets(String status) {
        assertThat(XpressBeesShipmentStatus.isPickedUpOrInTransit(status)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = { "picked_up", "in_transit", "reached_hub", "dispatched", "shipped", "manifested", "rto_in_transit" })
    void isPickedUpOrInTransit_trueForTransitTokens(String status) {
        assertThat(XpressBeesShipmentStatus.isPickedUpOrInTransit(status)).isTrue();
    }

    @ParameterizedTest
    @CsvSource({ "unknown,false", "bagged,false", "in_transit,true" })
    void isPickedUpOrInTransit_unknownVsInTransitAlias(String status, boolean expected) {
        assertThat(XpressBeesShipmentStatus.isPickedUpOrInTransit(status)).isEqualTo(expected);
    }
}
