package com.miniandmore.app.service;

import com.miniandmore.app.domain.enumeration.CouponEligibilityScope;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;

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
 *   <li>Per-line {@code discountByLineIndex} records which cart lines received the free/discounted units
 *       so returns can refund fairly.</li>
 * </ul>
 *
 * <p>Flat/percent line allocation lives in {@link DiscountLineAllocator}. Eligibility filtering is shared
 * via {@link #qualifyingLines}.
 */
public final class BogoDiscountCalculator {

    public static final int MIN_QTY = 1;
    public static final int MAX_QTY = 10;
    public static final int MAX_UNITS_PER_LINE = 500;
    public static final int MAX_TOTAL_UNITS = 2000;

    public record Line(
        int qty,
        BigDecimal unitPrice,
        Integer lineIndex,
        String productId,
        String variantSku,
        Long categoryId
    ) {
        public Line(int qty, BigDecimal unitPrice) {
            this(qty, unitPrice, null, null, null, null);
        }

        public Line withIndex(int index) {
            return new Line(qty, unitPrice, index, productId, variantSku, categoryId);
        }
    }

    public record Result(
        BigDecimal discount,
        int missingItems,
        int appliedGroups,
        int nextTierMissing,
        Map<Integer, BigDecimal> discountByLineIndex
    ) {
        public Result(BigDecimal discount, int missingItems) {
            this(discount, missingItems, 0, 0, Map.of());
        }

        public Result(BigDecimal discount, int missingItems, int appliedGroups, int nextTierMissing) {
            this(discount, missingItems, appliedGroups, nextTierMissing, Map.of());
        }

        public boolean eligible() {
            return missingItems <= 0;
        }

        public BigDecimal allocationForLine(int lineIndex) {
            return discountByLineIndex.getOrDefault(lineIndex, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        }
    }

    private record PricedUnit(int lineIndex, BigDecimal price) {}

    private BogoDiscountCalculator() {}

    public static int clampQty(Integer value) {
        if (value == null || value < MIN_QTY) {
            return MIN_QTY;
        }
        return Math.min(value, MAX_QTY);
    }

    public static CouponEligibilityScope resolveScope(CouponEligibilityScope scope) {
        return scope == null ? CouponEligibilityScope.ALL : scope;
    }

    public static Result calculate(
        Integer buyQty,
        Integer getQty,
        BigDecimal percentOff,
        BigDecimal maxDiscount,
        BigDecimal subtotal,
        List<Line> lines
    ) {
        return calculate(buyQty, getQty, percentOff, maxDiscount, subtotal, lines, CouponEligibilityScope.ALL, null);
    }

    @SuppressWarnings("java:S107")
    public static Result calculate(
        Integer buyQty,
        Integer getQty,
        BigDecimal percentOff,
        BigDecimal maxDiscount,
        BigDecimal subtotal,
        List<Line> lines,
        CouponEligibilityScope scope,
        Long eligibilityCategoryId
    ) {
        int buy = clampQty(buyQty);
        int get = clampQty(getQty);
        int groupSize = buy + get;
        CouponEligibilityScope resolved = resolveScope(scope);
        List<Line> qualifying = qualifyingLines(lines, resolved, eligibilityCategoryId);

        Result raw = resolved == CouponEligibilityScope.SAME_SKU
            ? calculateSameSku(buy, get, groupSize, percentOff, qualifying)
            : calculateOnPool(buy, get, groupSize, percentOff, qualifying);

        return applyCaps(raw, maxDiscount, subtotal);
    }

    /** @deprecated Prefer {@link DiscountLineAllocator#allocateProportionally}. */
    @Deprecated
    public static Map<Integer, BigDecimal> allocateProportionally(
        BigDecimal discount,
        List<Line> lines,
        CouponEligibilityScope scope,
        Long eligibilityCategoryId
    ) {
        return DiscountLineAllocator.allocateProportionally(discount, lines, scope, eligibilityCategoryId);
    }

    public static List<Line> qualifyingLines(List<Line> lines, CouponEligibilityScope scope, Long eligibilityCategoryId) {
        List<Line> indexed = indexLines(lines);
        return switch (resolveScope(scope)) {
            case CATEGORY -> filterByCategory(indexed, eligibilityCategoryId);
            case SAME_SKU, ALL -> indexed;
        };
    }

    public static BigDecimal qualifyingSubtotal(List<Line> lines, CouponEligibilityScope scope, Long eligibilityCategoryId) {
        return qualifyingLines(lines, scope, eligibilityCategoryId)
            .stream()
            .map(line -> line.unitPrice().multiply(BigDecimal.valueOf(line.qty())).max(BigDecimal.ZERO))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static Result calculateSameSku(
        int buy,
        int get,
        int groupSize,
        BigDecimal percentOff,
        List<Line> lines
    ) {
        Map<String, List<Line>> bySku = new LinkedHashMap<>();
        for (Line line : lines) {
            bySku.computeIfAbsent(skuKey(line), key -> new ArrayList<>()).add(line);
        }
        BigDecimal discount = BigDecimal.ZERO;
        int appliedGroups = 0;
        int missingItems = Integer.MAX_VALUE;
        int nextTierMissing = Integer.MAX_VALUE;
        Map<Integer, BigDecimal> byLine = new LinkedHashMap<>();
        boolean anyEligible = false;

        for (List<Line> group : bySku.values()) {
            Result part = calculateOnPool(buy, get, groupSize, percentOff, group);
            if (part.eligible()) {
                anyEligible = true;
                discount = discount.add(part.discount());
                appliedGroups += part.appliedGroups();
                if (part.nextTierMissing() > 0) {
                    nextTierMissing = Math.min(nextTierMissing, part.nextTierMissing());
                }
                mergeAllocations(byLine, part.discountByLineIndex());
            } else if (part.missingItems() > 0) {
                missingItems = Math.min(missingItems, part.missingItems());
            }
        }

        if (!anyEligible) {
            int missing = missingItems == Integer.MAX_VALUE ? groupSize : missingItems;
            return new Result(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), missing, 0, missing, Map.of());
        }
        return new Result(
            discount.setScale(2, RoundingMode.HALF_UP),
            0,
            appliedGroups,
            nextTierMissing == Integer.MAX_VALUE ? 0 : nextTierMissing,
            scaleMap(byLine)
        );
    }

    private static Result calculateOnPool(
        int buy,
        int get,
        int groupSize,
        BigDecimal percentOff,
        List<Line> lines
    ) {
        List<PricedUnit> units = expandUnits(lines);
        int missing = Math.max(0, groupSize - units.size());
        if (missing > 0) {
            return new Result(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), missing, 0, missing, Map.of());
        }

        units.sort(Comparator.comparing(PricedUnit::price).reversed().thenComparingInt(PricedUnit::lineIndex));
        int numGroups = units.size() / groupSize;
        int remainder = units.size() % groupSize;
        int nextTierMissing = remainder == 0 ? 0 : (groupSize - remainder);

        BigDecimal percent = clampPercent(percentOff);
        BigDecimal discount = BigDecimal.ZERO;
        Map<Integer, BigDecimal> byLine = new LinkedHashMap<>();

        for (int g = 0; g < numGroups; g++) {
            int groupStart = g * groupSize;
            for (int i = 0; i < get; i++) {
                PricedUnit getUnit = units.get(groupStart + buy + i);
                BigDecimal unitDiscount = getUnit
                    .price()
                    .multiply(percent)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                discount = discount.add(unitDiscount);
                byLine.merge(getUnit.lineIndex(), unitDiscount, BigDecimal::add);
            }
        }

        return new Result(discount.setScale(2, RoundingMode.HALF_UP), 0, numGroups, nextTierMissing, scaleMap(byLine));
    }

    private static Result applyCaps(Result raw, BigDecimal maxDiscount, BigDecimal subtotal) {
        BigDecimal discount = raw.discount();
        Map<Integer, BigDecimal> byLine = new LinkedHashMap<>(raw.discountByLineIndex());
        if (maxDiscount != null) {
            BigDecimal cap = maxDiscount.max(BigDecimal.ZERO);
            if (discount.compareTo(cap) > 0 && discount.signum() > 0) {
                byLine = scaleAllocations(byLine, cap, discount);
                discount = cap;
            }
        }
        BigDecimal subtotalCap = subtotal == null ? discount : subtotal.max(BigDecimal.ZERO);
        if (discount.compareTo(subtotalCap) > 0 && discount.signum() > 0) {
            byLine = scaleAllocations(byLine, subtotalCap, discount);
            discount = subtotalCap;
        }
        discount = discount.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        return new Result(discount, raw.missingItems(), raw.appliedGroups(), raw.nextTierMissing(), scaleMap(byLine));
    }

    private static Map<Integer, BigDecimal> scaleAllocations(
        Map<Integer, BigDecimal> byLine,
        BigDecimal target,
        BigDecimal current
    ) {
        if (current.signum() <= 0 || byLine.isEmpty()) {
            return Map.of();
        }
        // Weights are the pre-cap allocations; distribute target without capping at weight
        // (shares already sum to current and only shrink).
        return DiscountLineAllocator.distribute(target, byLine, false);
    }

    private static void mergeAllocations(Map<Integer, BigDecimal> into, Map<Integer, BigDecimal> from) {
        for (Map.Entry<Integer, BigDecimal> entry : from.entrySet()) {
            into.merge(entry.getKey(), entry.getValue(), BigDecimal::add);
        }
    }

    private static Map<Integer, BigDecimal> scaleMap(Map<Integer, BigDecimal> byLine) {
        if (byLine == null || byLine.isEmpty()) {
            return Map.of();
        }
        Map<Integer, BigDecimal> out = new LinkedHashMap<>();
        byLine.forEach((key, value) -> out.put(key, value.setScale(2, RoundingMode.HALF_UP)));
        return Map.copyOf(out);
    }

    private static List<Line> filterByCategory(List<Line> lines, Long eligibilityCategoryId) {
        if (eligibilityCategoryId == null) {
            return List.of();
        }
        return lines
            .stream()
            .filter(line -> Objects.equals(eligibilityCategoryId, line.categoryId()))
            .toList();
    }

    private static String skuKey(Line line) {
        if (StringUtils.isNotBlank(line.variantSku())) {
            return "sku:" + line.variantSku().trim().toLowerCase(Locale.ROOT);
        }
        if (StringUtils.isNotBlank(line.productId())) {
            return "pid:" + line.productId().trim();
        }
        return "idx:" + (line.lineIndex() != null ? line.lineIndex() : 0);
    }

    private static List<Line> indexLines(List<Line> lines) {
        if (lines == null || lines.isEmpty()) {
            return List.of();
        }
        List<Line> indexed = new ArrayList<>(lines.size());
        for (int i = 0; i < lines.size(); i++) {
            Line line = lines.get(i);
            if (line == null || line.qty() <= 0 || line.unitPrice() == null) {
                continue;
            }
            indexed.add(line.lineIndex() != null ? line : line.withIndex(i));
        }
        return indexed;
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

    private static List<PricedUnit> expandUnits(List<Line> lines) {
        List<PricedUnit> units = new ArrayList<>();
        if (lines == null) {
            return units;
        }
        for (Line line : lines) {
            if (line == null || line.qty() <= 0 || line.unitPrice() == null || line.lineIndex() == null) {
                continue;
            }
            BigDecimal price = line.unitPrice().max(BigDecimal.ZERO);
            int qty = Math.min(line.qty(), MAX_UNITS_PER_LINE);
            for (int i = 0; i < qty; i++) {
                if (units.size() >= MAX_TOTAL_UNITS) {
                    break;
                }
                units.add(new PricedUnit(line.lineIndex(), price));
            }
            if (units.size() >= MAX_TOTAL_UNITS) {
                break;
            }
        }
        return units;
    }
}
