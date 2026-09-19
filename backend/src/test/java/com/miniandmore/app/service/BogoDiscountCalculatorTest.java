package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class BogoDiscountCalculatorTest {

    @Test
    void buyOneGetOneFree_makesCheapestUnitFree() {
        BogoDiscountCalculator.Result result = BogoDiscountCalculator.calculate(
            1,
            1,
            BigDecimal.valueOf(100),
            null,
            BigDecimal.valueOf(1500),
            List.of(new BogoDiscountCalculator.Line(1, BigDecimal.valueOf(1000)), new BogoDiscountCalculator.Line(1, BigDecimal.valueOf(500)))
        );

        assertThat(result.eligible()).isTrue();
        assertThat(result.discount()).isEqualByComparingTo("500.00");
    }

    @Test
    void buyOneGetOneFree_twoPairs_freesTwoCheapest() {
        BogoDiscountCalculator.Result result = BogoDiscountCalculator.calculate(
            1,
            1,
            BigDecimal.valueOf(100),
            null,
            BigDecimal.valueOf(4000),
            List.of(new BogoDiscountCalculator.Line(4, BigDecimal.valueOf(1000)))
        );

        assertThat(result.discount()).isEqualByComparingTo("2000.00");
    }

    @Test
    void buyTwoGetOneFree_requiresThreeUnits() {
        BogoDiscountCalculator.Result shortCart = BogoDiscountCalculator.calculate(
            2,
            1,
            BigDecimal.valueOf(100),
            null,
            BigDecimal.valueOf(2000),
            List.of(new BogoDiscountCalculator.Line(2, BigDecimal.valueOf(1000)))
        );
        assertThat(shortCart.eligible()).isFalse();
        assertThat(shortCart.missingItems()).isEqualTo(1);

        BogoDiscountCalculator.Result complete = BogoDiscountCalculator.calculate(
            2,
            1,
            BigDecimal.valueOf(100),
            null,
            BigDecimal.valueOf(2400),
            List.of(
                new BogoDiscountCalculator.Line(1, BigDecimal.valueOf(1000)),
                new BogoDiscountCalculator.Line(1, BigDecimal.valueOf(800)),
                new BogoDiscountCalculator.Line(1, BigDecimal.valueOf(600))
            )
        );
        assertThat(complete.eligible()).isTrue();
        assertThat(complete.discount()).isEqualByComparingTo("600.00");
    }

    @Test
    void percentOffGetItem_appliesToCheapestUnits() {
        BogoDiscountCalculator.Result result = BogoDiscountCalculator.calculate(
            1,
            1,
            BigDecimal.valueOf(50),
            null,
            BigDecimal.valueOf(1500),
            List.of(new BogoDiscountCalculator.Line(1, BigDecimal.valueOf(1000)), new BogoDiscountCalculator.Line(1, BigDecimal.valueOf(500)))
        );

        assertThat(result.discount()).isEqualByComparingTo("250.00");
    }

    @Test
    void maxDiscount_capsBogoSaving() {
        BogoDiscountCalculator.Result result = BogoDiscountCalculator.calculate(
            1,
            1,
            BigDecimal.valueOf(100),
            BigDecimal.valueOf(200),
            BigDecimal.valueOf(1500),
            List.of(new BogoDiscountCalculator.Line(1, BigDecimal.valueOf(1000)), new BogoDiscountCalculator.Line(1, BigDecimal.valueOf(500)))
        );

        assertThat(result.discount()).isEqualByComparingTo("200.00");
    }

    @Test
    void buyTwoGetOneFree_singleLineQtyTwo_isNotEligible() {
        BogoDiscountCalculator.Result result = BogoDiscountCalculator.calculate(
            2,
            1,
            BigDecimal.valueOf(100),
            null,
            BigDecimal.valueOf(2000),
            List.of(new BogoDiscountCalculator.Line(2, BigDecimal.valueOf(1000)))
        );

        assertThat(result.eligible()).isFalse();
        assertThat(result.missingItems()).isEqualTo(1);
    }

    @Test
    void clampQty_defaultsAndCaps() {
        assertThat(BogoDiscountCalculator.clampQty(null)).isEqualTo(1);
        assertThat(BogoDiscountCalculator.clampQty(0)).isEqualTo(1);
        assertThat(BogoDiscountCalculator.clampQty(99)).isEqualTo(10);
    }
}
