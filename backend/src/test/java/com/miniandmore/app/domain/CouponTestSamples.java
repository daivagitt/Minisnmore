package com.miniandmore.app.domain;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public class CouponTestSamples {

    private static final Random random = new Random();
    private static final AtomicLong longCount = new AtomicLong(random.nextInt() + (2L * Integer.MAX_VALUE));

    public static Coupon getCouponSample1() {
        return new Coupon().id(1L).code("code1");
    }

    public static Coupon getCouponSample2() {
        return new Coupon().id(2L).code("code2");
    }

    public static Coupon getCouponRandomSampleGenerator() {
        return new Coupon().id(longCount.incrementAndGet()).code(UUID.randomUUID().toString());
    }
}
