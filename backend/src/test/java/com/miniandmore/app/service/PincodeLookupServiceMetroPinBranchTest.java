package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Covers metro pin-prefix branches in {@link PincodeLookupService#deliveryEta}. */
class PincodeLookupServiceMetroPinBranchTest {

    @ParameterizedTest
    @MethodSource("metroPinPrefixes")
    void deliveryEta_metroPinPrefixWithoutMetroName_returnsFastEta(String pincode) {
        assertThat(PincodeLookupService.deliveryEta(pincode, "Rajasthan", "Rural Town")).isEqualTo("2–3 days");
    }

    private static Stream<Arguments> metroPinPrefixes() {
        return Stream.of(
            Arguments.of("560099"),
            Arguments.of("561001"),
            Arguments.of("400001"),
            Arguments.of("401105"),
            Arguments.of("110001"),
            Arguments.of("122001"),
            Arguments.of("600001"),
            Arguments.of("601203"),
            Arguments.of("700001"),
            Arguments.of("711101"),
            Arguments.of("500001"),
            Arguments.of("501218"),
            Arguments.of("411001"),
            Arguments.of("412207")
        );
    }

    @ParameterizedTest
    @MethodSource("nonMetroPins")
    void deliveryEta_nonMetroPin_returnsStandardEta(String pincode) {
        assertThat(PincodeLookupService.deliveryEta(pincode, "Rajasthan", "Rural Town")).isEqualTo("4–6 days");
    }

    private static Stream<String> nonMetroPins() {
        return Stream.of("302001", "800001", "160001");
    }
}
