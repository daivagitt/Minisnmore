package com.miniandmore.app.domain;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public class CustomerOrderTestSamples {

    private static final Random random = new Random();
    private static final AtomicLong longCount = new AtomicLong(random.nextInt() + (2L * Integer.MAX_VALUE));

    public static CustomerOrder getCustomerOrderSample1() {
        return new CustomerOrder()
            .id(1L)
            .userLogin("userLogin1")
            .orderNumber("orderNumber1")
            .razorpayOrderId("razorpayOrderId1")
            .razorpayPaymentId("razorpayPaymentId1")
            .razorpaySignature("razorpaySignature1")
            .couponCode("couponCode1")
            .notes("notes1");
    }

    public static CustomerOrder getCustomerOrderSample2() {
        return new CustomerOrder()
            .id(2L)
            .userLogin("userLogin2")
            .orderNumber("orderNumber2")
            .razorpayOrderId("razorpayOrderId2")
            .razorpayPaymentId("razorpayPaymentId2")
            .razorpaySignature("razorpaySignature2")
            .couponCode("couponCode2")
            .notes("notes2");
    }

    public static CustomerOrder getCustomerOrderRandomSampleGenerator() {
        return new CustomerOrder()
            .id(longCount.incrementAndGet())
            .userLogin(UUID.randomUUID().toString())
            .orderNumber(UUID.randomUUID().toString())
            .razorpayOrderId(UUID.randomUUID().toString())
            .razorpayPaymentId(UUID.randomUUID().toString())
            .razorpaySignature(UUID.randomUUID().toString())
            .couponCode(UUID.randomUUID().toString())
            .notes(UUID.randomUUID().toString());
    }
}
