package com.miniandmore.app.domain.enumeration;

/**
 * Which cart items a coupon (especially BOGO) may apply to.
 */
public enum CouponEligibilityScope {
    /** All cart units pool together. */
    ALL,
    /** BOGO pairing only within the same product/SKU. */
    SAME_SKU,
    /** Only items in {@code eligibilityCategoryId}. */
    CATEGORY,
}
