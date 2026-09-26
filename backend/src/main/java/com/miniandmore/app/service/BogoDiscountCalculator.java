package com.miniandmore.app.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Buy-X-get-Y discount calculator modeled after standard e-commerce promotional engines
 * (e.g. Shopify, Amazon, Magento).
 *
 * <p>Standard pairing rules:
 * <ul>
 *   <li>Qualifying units are sorted in descending order of unit price.</li>
 *   <li>Greedy groups of size {@code buy + get} are formed starting from highest priced items.</li>
 *   <li>In each group, the top {@code buy} items are full price (qualifying trigger items),
 *       and the remaining {@code get} items receive the promotional discount (equal or lesser value).</li>
 *   <li>Monotonicity guarantee: adding extra items to the basket never reduces the discount.</li>
 *   <li>Upsell hints: tracks items needed to reach the next promotional tier.</li>
 * </ul>
 */
public final class BogoDiscountCalculator {

    public static final int MIN_QTY = 1;
    public static final int MAX_QTY = 10;
    public static final int MAX_UNITS_PER_LINE = 500;
    public static final int MAX_TOTAL_UNITS = 2000;

    public record Line(int qty, BigDecimal unitPrice) {}

    public record Result(
        BigDecimal discount,
        int missingItems,
        int appliedGroups,
        int nextTierMissing
    ) {
        public Result(BigDecimal discount, int missingItems) {
            this(discount, missingItems, 0, 0);
        }

        public boolean eligible() {
            return missingItems <= 0;
        }
    }

    private BogoDiscountCalculator() {}

    public static int clampQty(Integer value) {
        if (value == null || value < MIN_QTY) {
            return MIN_QTY;
        }
        return Math.min(value, MAX_QTY);
    }

    public static Result calculate(
        Integer buyQty,
        Integer getQty,
        BigDecimal percentOff,
        BigDecimal maxDiscount,
        BigDecimal subtotal,
        List<Line> lines
    ) {
        int buy = clampQty(buyQty);
        int get = clampQty(getQty);
        int groupSize = buy + get;
        List<BigDecimal> units = expandUnits(lines);
        int missing = Math.max(0, groupSize - units.size());
        if (missing > 0) {
            return new Result(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), missing, 0, missing);
        }

        // Standard e-commerce BOGO pairing:
        // 1. Sort qualifying units descending by price (highest first).
        // 2. Form greedy groups of size (buy + get) from highest to lowest.
        // 3. In each group, the top `buy` items are paid and the remaining `get` items
        //    receive the discount (percentOff % off each).
        units.sort(Comparator.reverseOrder());
        int numGroups = units.size() / groupSize;
        int remainder = units.size() % groupSize;
        int nextTierMissing = remainder == 0 ? 0 : (groupSize - remainder);

        BigDecimal percent = clampPercent(percentOff);
        BigDecimal discount = BigDecimal.ZERO;

        for (int g = 0; g < numGroups; g++) {
            int groupStart = g * groupSize;
            for (int i = 0; i < get; i++) {
                BigDecimal getItemPrice = units.get(groupStart + buy + i);
                discount = discount.add(
                    getItemPrice.multiply(percent).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                );
            }
        }

        if (maxDiscount != null) {
            discount = discount.min(maxDiscount.max(BigDecimal.ZERO));
        }
        BigDecimal cap = subtotal == null ? discount : subtotal.max(BigDecimal.ZERO);
        discount = discount.max(BigDecimal.ZERO).min(cap).setScale(2, RoundingMode.HALF_UP);
        return new Result(discount, 0, numGroups, nextTierMissing);
    }

    private static BigDecimal clampPercent(BigDecimal percentOff) {
        if (percentOff == null) {
            return BigDecimal.valueOf(100);
        }
        if (percentOff.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }
        if (percentOff.compareTo(BigDecimal.valueOf(100)) > 0) {
            return BigDecimal.valueOf(100);
        }
        return percentOff;
    }

    private static List<BigDecimal> expandUnits(List<Line> lines) {
        List<BigDecimal> units = new ArrayList<>();
        if (lines == null) {
            return units;
        }
        for (Line line : lines) {
            if (line == null || line.qty() <= 0 || line.unitPrice() == null) {
                continue;
            }
            BigDecimal price = line.unitPrice().max(BigDecimal.ZERO);
            int qty = Math.min(line.qty(), MAX_UNITS_PER_LINE);
            for (int i = 0; i < qty; i++) {
                if (units.size() >= MAX_TOTAL_UNITS) {
                    break;
                }
                units.add(price);
            }
            if (units.size() >= MAX_TOTAL_UNITS) {
                break;
            }
        }
        return units;
    }
}
