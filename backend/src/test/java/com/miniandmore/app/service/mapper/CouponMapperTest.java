package com.miniandmore.app.service.mapper;

import static com.miniandmore.app.domain.CouponAsserts.*;
import static com.miniandmore.app.domain.CouponTestSamples.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CouponMapperTest {

    private CouponMapper couponMapper;

    @BeforeEach
    void setUp() {
        couponMapper = new CouponMapperImpl();
    }

    @Test
    void shouldConvertToDtoAndBack() {
        var expected = getCouponSample1();
        var actual = couponMapper.toEntity(couponMapper.toDto(expected));
        assertCouponAllPropertiesEquals(expected, actual);
    }
}
