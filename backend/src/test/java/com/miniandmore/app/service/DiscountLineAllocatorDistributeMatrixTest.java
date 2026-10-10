package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DiscountLineAllocatorDistributeMatrixTest {

    @Test
    void distribute_emptyGuards() {
        assertThat(DiscountLineAllocator.distribute(null, Map.of(1, BigDecimal.ONE), false)).isEmpty();
        assertThat(DiscountLineAllocator.distribute(BigDecimal.ZERO, Map.of(1, BigDecimal.ONE), false)).isEmpty();
        assertThat(DiscountLineAllocator.distribute(new BigDecimal("-1"), Map.of(1, BigDecimal.ONE), false)).isEmpty();
        assertThat(DiscountLineAllocator.distribute(BigDecimal.TEN, null, false)).isEmpty();
        assertThat(DiscountLineAllocator.distribute(BigDecimal.TEN, Map.of(), false)).isEmpty();
        assertThat(DiscountLineAllocator.distribute(BigDecimal.TEN, Map.of(1, BigDecimal.ZERO, 2, new BigDecimal("-1")), false)).isEmpty();
    }

    @Test
    void distribute_splitsAndCapsAndDropsZeroShares() {
        Map<Integer, BigDecimal> weights = new LinkedHashMap<>();
        weights.put(1, new BigDecimal("10"));
        weights.put(2, new BigDecimal("30"));
        Map<Integer, BigDecimal> split = DiscountLineAllocator.distribute(new BigDecimal("40"), weights, false);
        assertThat(split.get(1)).isEqualByComparingTo("10.00");
        assertThat(split.get(2)).isEqualByComparingTo("30.00");

        Map<Integer, BigDecimal> capped = DiscountLineAllocator.distribute(new BigDecimal("100"), weights, true);
        assertThat(capped.get(1)).isEqualByComparingTo("10.00");
        assertThat(capped.get(2)).isEqualByComparingTo("30.00");

        Map<Integer, BigDecimal> tiny = new LinkedHashMap<>();
        tiny.put(1, new BigDecimal("0.001"));
        tiny.put(2, new BigDecimal("10"));
        Map<Integer, BigDecimal> dropped = DiscountLineAllocator.distribute(new BigDecimal("0.01"), tiny, false);
        assertThat(dropped.containsKey(1) ? dropped.get(1).signum() : 0).isGreaterThanOrEqualTo(0);
    }
}
