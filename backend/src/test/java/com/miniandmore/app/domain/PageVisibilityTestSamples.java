package com.miniandmore.app.domain;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public class PageVisibilityTestSamples {

    private static final Random random = new Random();
    private static final AtomicLong longCount = new AtomicLong(random.nextInt() + (2L * Integer.MAX_VALUE));

    public static PageVisibility getPageVisibilitySample1() {
        return new PageVisibility().id(1L).pageKey("pageKey1");
    }

    public static PageVisibility getPageVisibilitySample2() {
        return new PageVisibility().id(2L).pageKey("pageKey2");
    }

    public static PageVisibility getPageVisibilityRandomSampleGenerator() {
        return new PageVisibility().id(longCount.incrementAndGet()).pageKey(UUID.randomUUID().toString());
    }
}
