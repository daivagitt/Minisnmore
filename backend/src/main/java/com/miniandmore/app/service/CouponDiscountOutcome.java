package com.miniandmore.app.service;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Typed result of coupon pricing (replaces string-key maps + unchecked casts).
 */
public record CouponDiscountOutcome(
    BigDecimal discount,
    Map<Integer, BigDecimal> lineAllocations,
    String nudge,
    String bogoDetails,
    String error
) {
    public static CouponDiscountOutcome error(String message) {
        return new CouponDiscountOutcome(null, Map.of(), null, null, message);
    }

    public static CouponDiscountOutcome ok(
        BigDecimal discount,
        Map<Integer, BigDecimal> lineAllocations,
        String nudge,
        String bogoDetails
    ) {
        return new CouponDiscountOutcome(
            discount,
            lineAllocations == null ? Map.of() : Map.copyOf(lineAllocations),
            nudge,
            bogoDetails,
            null
        );
    }

    public boolean hasError() {
        return error != null;
    }
}
