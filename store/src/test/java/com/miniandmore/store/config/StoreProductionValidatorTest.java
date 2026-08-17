package com.miniandmore.store.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class StoreProductionValidatorTest {

    @Test
    void nonProdProfile_doesNotValidate() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("dev");

        new StoreProductionValidator(environment, "custom-jwt-secret");
    }

    @Test
    void prodWithSampleJwtSecret_throws() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        String sample =
            "NWNhYWRmODZlZDAzNzg4NGY2NTA0MzAyZTdjNjI2ZjYzYWI2YmQ3Yjk4ZDA5NGVkODNhYWY2ZmExOTJmZjA3YmJhNDdiYTYxMTdjZmM2ZWUyMmMwMzQyMTY1NDBjNTc5ODg4MjNkZjExODFmZjUwNDdlNjVhOTlhMWE5MjRjYTU=";

        assertThatThrownBy(() -> new StoreProductionValidator(environment, sample)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void prodWithSecretSamplesProfile_throws() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod", "secret-samples");

        assertThatThrownBy(() -> new StoreProductionValidator(environment, "custom-secret"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("secret-samples");
    }

    @Test
    void prodWithCustomJwtSecret_ok() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");

        new StoreProductionValidator(environment, "custom-production-secret");
    }
}
