package com.miniandmore.app.domain;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class BannerTestSamples {

    private static final Random random = new Random();
    private static final AtomicLong longCount = new AtomicLong(random.nextInt() + (2L * Integer.MAX_VALUE));
    private static final AtomicInteger intCount = new AtomicInteger(random.nextInt() + (2 * Short.MAX_VALUE));

    public static Banner getBannerSample1() {
        return new Banner()
            .id(1L)
            .title("title1")
            .subtitle("subtitle1")
            .imageUrl("imageUrl1")
            .linkUrl("linkUrl1")
            .ctaLabel("ctaLabel1")
            .sortOrder(1);
    }

    public static Banner getBannerSample2() {
        return new Banner()
            .id(2L)
            .title("title2")
            .subtitle("subtitle2")
            .imageUrl("imageUrl2")
            .linkUrl("linkUrl2")
            .ctaLabel("ctaLabel2")
            .sortOrder(2);
    }

    public static Banner getBannerRandomSampleGenerator() {
        return new Banner()
            .id(longCount.incrementAndGet())
            .title(UUID.randomUUID().toString())
            .subtitle(UUID.randomUUID().toString())
            .imageUrl(UUID.randomUUID().toString())
            .linkUrl(UUID.randomUUID().toString())
            .ctaLabel(UUID.randomUUID().toString())
            .sortOrder(intCount.incrementAndGet());
    }
}
