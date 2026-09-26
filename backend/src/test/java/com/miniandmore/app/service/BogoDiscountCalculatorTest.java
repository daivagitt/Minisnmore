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

    @Test
    void buyOneGetOneFree_greedyDescendingPairing_givesFairPairing() {
        // Basket: [1200, 1000, 500, 400]
        // Pair 1: (1200, 1000) -> 1000 free
        // Pair 2: (500, 400) -> 400 free
        // Total discount: 1400 (not naive 500+400=900)
        BogoDiscountCalculator.Result result = BogoDiscountCalculator.calculate(
            1,
            1,
            BigDecimal.valueOf(100),
            null,
            BigDecimal.valueOf(3100),
            List.of(
                new BogoDiscountCalculator.Line(1, BigDecimal.valueOf(1200)),
                new BogoDiscountCalculator.Line(1, BigDecimal.valueOf(1000)),
                new BogoDiscountCalculator.Line(1, BigDecimal.valueOf(500)),
                new BogoDiscountCalculator.Line(1, BigDecimal.valueOf(400))
            )
        );

        assertThat(result.eligible()).isTrue();
        assertThat(result.discount()).isEqualByComparingTo("1400.00");
        assertThat(result.appliedGroups()).isEqualTo(2);
        assertThat(result.nextTierMissing()).isEqualTo(0);
    }

    @Test
    void buyOneGetOneFree_addingCheapAccessory_neverReducesDiscount_monotonicity() {
        // Without accessory: [1200, 1000] -> 1000 discount
        BogoDiscountCalculator.Result withoutAccessory = BogoDiscountCalculator.calculate(
            1,
            1,
            BigDecimal.valueOf(100),
            null,
            BigDecimal.valueOf(2200),
            List.of(
                new BogoDiscountCalculator.Line(1, BigDecimal.valueOf(1200)),
                new BogoDiscountCalculator.Line(1, BigDecimal.valueOf(1000))
            )
        );
        assertThat(withoutAccessory.discount()).isEqualByComparingTo("1000.00");
        assertThat(withoutAccessory.nextTierMissing()).isEqualTo(0);

        // With accessory: [1200, 1000, 200] -> discount must stay 1000, NOT drop to 200!
        BogoDiscountCalculator.Result withAccessory = BogoDiscountCalculator.calculate(
            1,
            1,
            BigDecimal.valueOf(100),
            null,
            BigDecimal.valueOf(2400),
            List.of(
                new BogoDiscountCalculator.Line(1, BigDecimal.valueOf(1200)),
                new BogoDiscountCalculator.Line(1, BigDecimal.valueOf(1000)),
                new BogoDiscountCalculator.Line(1, BigDecimal.valueOf(200))
            )
        );
        assertThat(withAccessory.discount()).isEqualByComparingTo("1000.00");
        assertThat(withAccessory.appliedGroups()).isEqualTo(1);
        assertThat(withAccessory.nextTierMissing()).isEqualTo(1);
    }

    @Test
    void buyTwoGetOneFree_greedyDescendingPairing_reportsNextTier() {
        // Basket: [1000, 900, 800, 300]
        // Group 1 (3 items): Buy 1000, Buy 900, Get 800 free
        // Remainder: 1 item (300). Needs 2 more items to get another free item.
        BogoDiscountCalculator.Result result = BogoDiscountCalculator.calculate(
            2,
            1,
            BigDecimal.valueOf(100),
            null,
            BigDecimal.valueOf(3000),
            List.of(
                new BogoDiscountCalculator.Line(1, BigDecimal.valueOf(1000)),
                new BogoDiscountCalculator.Line(1, BigDecimal.valueOf(900)),
                new BogoDiscountCalculator.Line(1, BigDecimal.valueOf(800)),
                new BogoDiscountCalculator.Line(1, BigDecimal.valueOf(300))
            )
        );

        assertThat(result.eligible()).isTrue();
        assertThat(result.discount()).isEqualByComparingTo("800.00");
        assertThat(result.appliedGroups()).isEqualTo(1);
        assertThat(result.nextTierMissing()).isEqualTo(2);
    }

    @Test
    void bogo_withNullOrEmptyOrNegativeLines_handlesGracefully() {
        BogoDiscountCalculator.Result nullLines = BogoDiscountCalculator.calculate(1, 1, null, null, BigDecimal.ZERO, null);
        assertThat(nullLines.eligible()).isFalse();
        assertThat(nullLines.missingItems()).isEqualTo(2);

        BogoDiscountCalculator.Result emptyLines = BogoDiscountCalculator.calculate(1, 1, null, null, BigDecimal.ZERO, List.of());
        assertThat(emptyLines.eligible()).isFalse();

        BogoDiscountCalculator.Result invalidLines = BogoDiscountCalculator.calculate(
            1,
            1,
            null,
            null,
            BigDecimal.ZERO,
            List.of(new BogoDiscountCalculator.Line(-1, BigDecimal.valueOf(100)), new BogoDiscountCalculator.Line(1, null))
        );
        assertThat(invalidLines.eligible()).isFalse();
    }

    @Test
    void negativeMaxDiscount_clampsToZero_neverProducesNegativeDiscount() {
        BogoDiscountCalculator.Result result = BogoDiscountCalculator.calculate(
            1,
            1,
            BigDecimal.valueOf(100),
            BigDecimal.valueOf(-50),
            BigDecimal.valueOf(1500),
            List.of(new BogoDiscountCalculator.Line(1, BigDecimal.valueOf(1000)), new BogoDiscountCalculator.Line(1, BigDecimal.valueOf(500)))
        );
        assertThat(result.discount()).isEqualByComparingTo("0.00");
    }

    @Test
    void massiveUnitExpansion_isCappedAtMaxTotalUnits() {
        // 10 lines of 500 units each = 5000 units. Should cap at MAX_TOTAL_UNITS (2000).
        List<BogoDiscountCalculator.Line> largeCart = List.of(
            new BogoDiscountCalculator.Line(500, BigDecimal.valueOf(100)),
            new BogoDiscountCalculator.Line(500, BigDecimal.valueOf(100)),
            new BogoDiscountCalculator.Line(500, BigDecimal.valueOf(100)),
            new BogoDiscountCalculator.Line(500, BigDecimal.valueOf(100))
        );
        BogoDiscountCalculator.Result result = BogoDiscountCalculator.calculate(
            1,
            1,
            BigDecimal.valueOf(100),
            null,
            BigDecimal.valueOf(200000),
            largeCart
        );
        assertThat(result.eligible()).isTrue();
        assertThat(result.appliedGroups()).isEqualTo(1000); // 2000 / 2 = 1000 groups
    }
}
