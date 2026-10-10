package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class LedgerlyOrderStatusRankBranchTest {

    @ParameterizedTest
    @MethodSource("rankCases")
    void rank_coversEveryStatusFamily(String status, int rank) {
        assertThat(LedgerlyOrderStatus.rank(status)).isEqualTo(rank);
    }

    private static Stream<Arguments> rankCases() {
        return Stream.of(
            Arguments.of(null, 0),
            Arguments.of("delivered", 40),
            Arguments.of("completed", 40),
            Arguments.of("shipped", 30),
            Arguments.of("in-transit", 30),
            Arguments.of("paid", 20),
            Arguments.of("processing", 20),
            Arguments.of("confirmed", 20),
            Arguments.of("open", 20),
            Arguments.of("cancelled", 10),
            Arguments.of("unknown", 10)
        );
    }
}
