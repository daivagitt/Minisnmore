package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.miniandmore.app.domain.enumeration.CouponEligibilityScope;
import com.miniandmore.app.domain.enumeration.DiscountType;
import com.miniandmore.app.service.dto.CouponDTO;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class CouponServiceNormalizeCouponMatrixTest {

    @Test
    void normalizeCoupon_nonCategoryClearsCategoryId() {
        CouponDTO coupon = base(DiscountType.PERCENT);
        coupon.setEligibilityScope(CouponEligibilityScope.ALL);
        coupon.setEligibilityCategoryId(44L);

        CouponService.normalizeCoupon(coupon);

        assertThat(coupon.getEligibilityCategoryId()).isNull();
    }

    @Test
    void normalizeCoupon_categoryRequiresId() {
        CouponDTO coupon = base(DiscountType.FLAT);
        coupon.setEligibilityScope(CouponEligibilityScope.CATEGORY);
        coupon.setEligibilityCategoryId(null);

        assertThatThrownBy(() -> CouponService.normalizeCoupon(coupon)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void normalizeCoupon_categoryKeepsId() {
        CouponDTO coupon = base(DiscountType.PERCENT);
        coupon.setEligibilityScope(CouponEligibilityScope.CATEGORY);
        coupon.setEligibilityCategoryId(7L);

        CouponService.normalizeCoupon(coupon);

        assertThat(coupon.getEligibilityCategoryId()).isEqualTo(7L);
    }

    @Test
    void normalizeCoupon_bogoClampsPercentBoundsAndNullValue() {
        CouponDTO low = base(DiscountType.BOGO);
        low.setDiscountValue(new BigDecimal("0.2"));
        CouponService.normalizeCoupon(low);
        assertThat(low.getDiscountValue()).isEqualByComparingTo("1");

        CouponDTO high = base(DiscountType.BOGO);
        high.setDiscountValue(new BigDecimal("250"));
        CouponService.normalizeCoupon(high);
        assertThat(high.getDiscountValue()).isEqualByComparingTo("100");

        CouponDTO mid = base(DiscountType.BOGO);
        mid.setDiscountValue(new BigDecimal("25"));
        CouponService.normalizeCoupon(mid);
        assertThat(mid.getDiscountValue()).isEqualByComparingTo("25");

        CouponDTO none = base(DiscountType.BOGO);
        none.setDiscountValue(null);
        CouponService.normalizeCoupon(none);
        assertThat(none.getDiscountValue()).isNull();
    }

    private static CouponDTO base(DiscountType type) {
        CouponDTO coupon = new CouponDTO();
        coupon.setDiscountType(type);
        coupon.setDiscountValue(BigDecimal.TEN);
        coupon.setEligibilityScope(CouponEligibilityScope.ALL);
        return coupon;
    }
}
