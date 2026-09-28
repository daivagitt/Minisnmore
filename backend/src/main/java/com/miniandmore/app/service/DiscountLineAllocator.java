package com.miniandmore.app.service;

import com.miniandmore.app.domain.enumeration.CouponEligibilityScope;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Allocates a flat/percent coupon discount across qualifying cart lines by gross share.
 * Kept separate from {@link BogoDiscountCalculator} (which owns BOGO pairing only).
 */
public final class DiscountLineAllocator {

    private DiscountLineAllocator() {}

    public static Map<Integer, BigDecimal> allocateProportionally(
        BigDecimal discount,
        List<BogoDiscountCalculator.Line> lines,
        CouponEligibilityScope scope,
        Long eligibilityCategoryId
    ) {
        List<BogoDiscountCalculator.Line> qualifying = BogoDiscountCalculator.qualifyingLines(
            lines,
            scope,
            eligibilityCategoryId
        );
        BigDecimal amount = discount == null ? BigDecimal.ZERO : discount.max(BigDecimal.ZERO);
        if (amount.signum() == 0 || qualifying.isEmpty()) {
            return Map.of();
        }
        Map<Integer, BigDecimal> weights = new LinkedHashMap<>();
        for (BogoDiscountCalculator.Line line : qualifying) {
            BigDecimal gross = line.unitPrice().multiply(BigDecimal.valueOf(line.qty())).max(BigDecimal.ZERO);
            weights.merge(line.lineIndex(), gross, BigDecimal::add);
        }
        return distribute(amount, weights, true);
    }

    /**
     * Distribute {@code amount} across entries proportional to weights.
     * Last entry receives the remainder so the sum matches {@code amount}.
     * When {@code capAtWeight} is true, each share is also capped at its weight.
     */
    public static Map<Integer, BigDecimal> distribute(
        BigDecimal amount,
        Map<Integer, BigDecimal> weights,
        boolean capAtWeight
    ) {
        if (amount == null || amount.signum() <= 0 || weights == null || weights.isEmpty()) {
            return Map.of();
        }
        BigDecimal totalWeight = weights.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalWeight.signum() <= 0) {
            return Map.of();
        }
        Map<Integer, BigDecimal> out = new LinkedHashMap<>();
        BigDecimal allocated = BigDecimal.ZERO;
        List<Map.Entry<Integer, BigDecimal>> entries = new ArrayList<>(weights.entrySet());
        for (int i = 0; i < entries.size(); i++) {
            Map.Entry<Integer, BigDecimal> entry = entries.get(i);
            BigDecimal share;
            if (i == entries.size() - 1) {
                share = amount.subtract(allocated).max(BigDecimal.ZERO);
            } else {
                share = entry.getValue().multiply(amount).divide(totalWeight, 2, RoundingMode.HALF_UP);
                allocated = allocated.add(share);
            }
            if (capAtWeight) {
                share = share.min(entry.getValue());
            }
            share = share.setScale(2, RoundingMode.HALF_UP);
            if (share.signum() > 0) {
                out.put(entry.getKey(), share);
            }
        }
        return out;
    }
}
