package com.miniandmore.app.domain;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class HomeSectionTestSamples {

    private static final Random random = new Random();
    private static final AtomicLong longCount = new AtomicLong(random.nextInt() + (2L * Integer.MAX_VALUE));
    private static final AtomicInteger intCount = new AtomicInteger(random.nextInt() + (2 * Short.MAX_VALUE));

    public static HomeSection getHomeSectionSample1() {
        return new HomeSection().id(1L).sectionKey("sectionKey1").title("title1").subtitle("subtitle1").sortOrder(1);
    }

    public static HomeSection getHomeSectionSample2() {
        return new HomeSection().id(2L).sectionKey("sectionKey2").title("title2").subtitle("subtitle2").sortOrder(2);
    }

    public static HomeSection getHomeSectionRandomSampleGenerator() {
        return new HomeSection()
            .id(longCount.incrementAndGet())
            .sectionKey(UUID.randomUUID().toString())
            .title(UUID.randomUUID().toString())
            .subtitle(UUID.randomUUID().toString())
            .sortOrder(intCount.incrementAndGet());
    }
}
