package com.miniandmore.app.domain;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class ProductTestSamples {

    private static final Random random = new Random();
    private static final AtomicLong longCount = new AtomicLong(random.nextInt() + (2L * Integer.MAX_VALUE));
    private static final AtomicInteger intCount = new AtomicInteger(random.nextInt() + (2 * Short.MAX_VALUE));

    public static Product getProductSample1() {
        return new Product()
            .id(1L)
            .slug("slug1")
            .name("name1")
            .sku("sku1")
            .fabricType("fabricType1")
            .currency("currency1")
            .ageMinMonths(1)
            .ageMaxMonths(1)
            .badge("badge1")
            .stock(1)
            .sortOrder(1);
    }

    public static Product getProductSample2() {
        return new Product()
            .id(2L)
            .slug("slug2")
            .name("name2")
            .sku("sku2")
            .fabricType("fabricType2")
            .currency("currency2")
            .ageMinMonths(2)
            .ageMaxMonths(2)
            .badge("badge2")
            .stock(2)
            .sortOrder(2);
    }

    public static Product getProductRandomSampleGenerator() {
        return new Product()
            .id(longCount.incrementAndGet())
            .slug(UUID.randomUUID().toString())
            .name(UUID.randomUUID().toString())
            .sku(UUID.randomUUID().toString())
            .fabricType(UUID.randomUUID().toString())
            .currency(UUID.randomUUID().toString())
            .ageMinMonths(intCount.incrementAndGet())
            .ageMaxMonths(intCount.incrementAndGet())
            .badge(UUID.randomUUID().toString())
            .stock(intCount.incrementAndGet())
            .sortOrder(intCount.incrementAndGet());
    }
}
