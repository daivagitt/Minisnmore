package com.miniandmore.integration.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ZohoIndiaGstSupportTest {

    @Test
    void placeOfSupplyFromState_byNameAndCode() {
        assertThat(ZohoIndiaGstSupport.placeOfSupplyFromState("Karnataka")).isEqualTo("KA");
        assertThat(ZohoIndiaGstSupport.placeOfSupplyFromState("ka")).isEqualTo("KA");
        assertThat(ZohoIndiaGstSupport.placeOfSupplyFromState("Tamil Nadu")).isEqualTo("TN");
        assertThat(ZohoIndiaGstSupport.placeOfSupplyFromState("")).isNull();
        assertThat(ZohoIndiaGstSupport.placeOfSupplyFromState("Unknown Region")).isNull();
    }

    @Test
    void placeOfSupplyFromState_partialMatch() {
        assertThat(ZohoIndiaGstSupport.placeOfSupplyFromState("North Karnataka district")).isEqualTo("KA");
    }

    @Test
    void isIndiaDataCenter() {
        assertThat(ZohoIndiaGstSupport.isIndiaDataCenter("in")).isTrue();
        assertThat(ZohoIndiaGstSupport.isIndiaDataCenter("IN")).isTrue();
        assertThat(ZohoIndiaGstSupport.isIndiaDataCenter("com")).isFalse();
    }
}
