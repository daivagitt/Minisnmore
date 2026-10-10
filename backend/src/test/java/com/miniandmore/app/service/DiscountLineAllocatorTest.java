package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.miniandmore.app.domain.enumeration.CouponEligibilityScope;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DiscountLineAllocatorTest {

    @Test
    void allocateProportionally_sharesByGrossAndSumsToDiscount() {
        List<BogoDiscountCalculator.Line> lines = List.of(
            new BogoDiscountCalculator.Line(1, BigDecimal.valueOf(1000), 0, "1", null, null),
            new BogoDiscountCalculator.Line(1, BigDecimal.valueOf(500), 1, "2", null, null)
        );

        Map<Integer, BigDecimal> allocations = DiscountLineAllocator.allocateProportionally(
            BigDecimal.valueOf(150),
            lines,
            CouponEligibilityScope.ALL,
            null
        );

        assertThat(allocations.get(0)).isEqualByComparingTo("100.00");
        assertThat(allocations.get(1)).isEqualByComparingTo("50.00");
        assertThat(allocations.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("150.00");
    }

    @Test
    void allocateProportionally_categoryScope_ignoresOtherLines() {
        List<BogoDiscountCalculator.Line> lines = List.of(
            new BogoDiscountCalculator.Line(1, BigDecimal.valueOf(1000), 0, "1", null, 10L),
            new BogoDiscountCalculator.Line(1, BigDecimal.valueOf(1000), 1, "2", null, 99L)
        );

        Map<Integer, BigDecimal> allocations = DiscountLineAllocator.allocateProportionally(
            BigDecimal.valueOf(100),
            lines,
            CouponEligibilityScope.CATEGORY,
            10L
        );

        assertThat(allocations).containsOnlyKeys(0);
        assertThat(allocations.get(0)).isEqualByComparingTo("100.00");
    }
}
