package com.miniandmore.app.domain;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public class PolicyTestSamples {

    private static final Random random = new Random();
    private static final AtomicLong longCount = new AtomicLong(random.nextInt() + (2L * Integer.MAX_VALUE));

    public static Policy getPolicySample1() {
        return new Policy().id(1L).slug("slug1").title("title1");
    }

    public static Policy getPolicySample2() {
        return new Policy().id(2L).slug("slug2").title("title2");
    }

    public static Policy getPolicyRandomSampleGenerator() {
        return new Policy().id(longCount.incrementAndGet()).slug(UUID.randomUUID().toString()).title(UUID.randomUUID().toString());
    }
}
