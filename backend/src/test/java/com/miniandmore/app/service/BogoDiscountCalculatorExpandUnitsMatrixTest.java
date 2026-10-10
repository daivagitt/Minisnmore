package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class BogoDiscountCalculatorExpandUnitsMatrixTest {

    @Test
    void expandUnits_skipsNullLinesAndCapsPerLineAndTotal() {
        assertThat((List<?>) ReflectionTestUtils.invokeMethod(BogoDiscountCalculator.class, "expandUnits", new Object[] { null })).isEmpty();

        List<BogoDiscountCalculator.Line> lines = new ArrayList<>();
        lines.add(null);
        lines.add(new BogoDiscountCalculator.Line(0, BigDecimal.ONE, 0, "p", "sku", 1L));
        lines.add(new BogoDiscountCalculator.Line(2, null, 1, "p", "sku", 1L));
        lines.add(new BogoDiscountCalculator.Line(2, BigDecimal.ONE, null, "p", "sku", 1L));
        lines.add(new BogoDiscountCalculator.Line(3, new BigDecimal("-5"), 2, "p", "sku", 1L));
        List<?> expanded = ReflectionTestUtils.invokeMethod(BogoDiscountCalculator.class, "expandUnits", lines);
        assertThat(expanded).hasSize(3);

        List<BogoDiscountCalculator.Line> capped = List.of(
            new BogoDiscountCalculator.Line(BogoDiscountCalculator.MAX_UNITS_PER_LINE + 10, BigDecimal.TEN, 0, "p", "s", 1L),
            new BogoDiscountCalculator.Line(BogoDiscountCalculator.MAX_UNITS_PER_LINE, BigDecimal.ONE, 1, "p", "s", 1L),
            new BogoDiscountCalculator.Line(BogoDiscountCalculator.MAX_UNITS_PER_LINE, BigDecimal.ONE, 2, "p", "s", 1L),
            new BogoDiscountCalculator.Line(BogoDiscountCalculator.MAX_UNITS_PER_LINE, BigDecimal.ONE, 3, "p", "s", 1L),
            new BogoDiscountCalculator.Line(BogoDiscountCalculator.MAX_UNITS_PER_LINE, BigDecimal.ONE, 4, "p", "s", 1L)
        );
        List<?> totalCapped = ReflectionTestUtils.invokeMethod(BogoDiscountCalculator.class, "expandUnits", capped);
        assertThat(totalCapped.size()).isEqualTo(BogoDiscountCalculator.MAX_TOTAL_UNITS);
    }
}
