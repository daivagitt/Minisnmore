package com.miniandmore.app.domain;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class OrderItemTestSamples {

    private static final Random random = new Random();
    private static final AtomicLong longCount = new AtomicLong(random.nextInt() + (2L * Integer.MAX_VALUE));
    private static final AtomicInteger intCount = new AtomicInteger(random.nextInt() + (2 * Short.MAX_VALUE));

    public static OrderItem getOrderItemSample1() {
        return new OrderItem()
            .id(1L)
            .productId("productId1")
            .productSlug("productSlug1")
            .productName("productName1")
            .productImage("productImage1")
            .itemSize("itemSize1")
            .color("color1")
            .qty(1);
    }

    public static OrderItem getOrderItemSample2() {
        return new OrderItem()
            .id(2L)
            .productId("productId2")
            .productSlug("productSlug2")
            .productName("productName2")
            .productImage("productImage2")
            .itemSize("itemSize2")
            .color("color2")
            .qty(2);
    }

    public static OrderItem getOrderItemRandomSampleGenerator() {
        return new OrderItem()
            .id(longCount.incrementAndGet())
            .productId(UUID.randomUUID().toString())
            .productSlug(UUID.randomUUID().toString())
            .productName(UUID.randomUUID().toString())
            .productImage(UUID.randomUUID().toString())
            .itemSize(UUID.randomUUID().toString())
            .color(UUID.randomUUID().toString())
            .qty(intCount.incrementAndGet());
    }
}
