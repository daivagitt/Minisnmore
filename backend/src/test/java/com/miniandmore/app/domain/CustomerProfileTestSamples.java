package com.miniandmore.app.domain;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public class CustomerProfileTestSamples {

    private static final Random random = new Random();
    private static final AtomicLong longCount = new AtomicLong(random.nextInt() + (2L * Integer.MAX_VALUE));

    public static CustomerProfile getCustomerProfileSample1() {
        return new CustomerProfile().id(1L).userLogin("userLogin1").fullName("fullName1").phone("phone1");
    }

    public static CustomerProfile getCustomerProfileSample2() {
        return new CustomerProfile().id(2L).userLogin("userLogin2").fullName("fullName2").phone("phone2");
    }

    public static CustomerProfile getCustomerProfileRandomSampleGenerator() {
        return new CustomerProfile()
            .id(longCount.incrementAndGet())
            .userLogin(UUID.randomUUID().toString())
            .fullName(UUID.randomUUID().toString())
            .phone(UUID.randomUUID().toString());
    }
}
