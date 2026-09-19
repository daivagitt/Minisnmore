package com.miniandmore.app.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Buy-X-get-Y discount on the cheapest qualifying units.
 * Buy 1 Get 1 Free is {@code buyQty=1, getQty=1, percentOff=100}.
 */
public final class BogoDiscountCalculator {

    public static final int MIN_QTY = 1;
    public static final int MAX_QTY = 10;

    public record Line(int qty, BigDecimal unitPrice) {}

    public record Result(BigDecimal discount, int missingItems) {
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
            return new Result(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), missing);
        }
        units.sort(Comparator.naturalOrder());
        int freeCount = (units.size() / groupSize) * get;
        BigDecimal percent = clampPercent(percentOff);
        BigDecimal discount = BigDecimal.ZERO;
        for (int i = 0; i < freeCount; i++) {
            discount = discount.add(units.get(i).multiply(percent).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
        }
        if (maxDiscount != null) {
            discount = discount.min(maxDiscount);
        }
        BigDecimal cap = subtotal == null ? discount : subtotal.max(BigDecimal.ZERO);
        discount = discount.max(BigDecimal.ZERO).min(cap).setScale(2, RoundingMode.HALF_UP);
        return new Result(discount, 0);
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
            int qty = Math.min(line.qty(), 50);
            for (int i = 0; i < qty; i++) {
                units.add(price);
            }
        }
        return units;
    }
}
