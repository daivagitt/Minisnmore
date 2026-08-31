package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class IndiaPlaceOfSupplyCodesTest {

    @Test
    void lookup_knownState_returnsCode() {
        assertThat(IndiaPlaceOfSupplyCodes.lookup("maharashtra")).isEqualTo("MH");
        assertThat(IndiaPlaceOfSupplyCodes.lookup("karnataka")).isEqualTo("KA");
    }

    @Test
    void lookup_unknownState_returnsNull() {
        assertThat(IndiaPlaceOfSupplyCodes.lookup("atlantis")).isNull();
    }

    @Test
    void containsValue_knownCode_returnsTrue() {
        assertThat(IndiaPlaceOfSupplyCodes.containsValue("MH")).isTrue();
        assertThat(IndiaPlaceOfSupplyCodes.containsValue("ZZ")).isFalse();
    }
}
