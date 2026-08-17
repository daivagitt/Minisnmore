package com.miniandmore.commerce.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class CommerceProductionValidatorTest {

    private static final String SAMPLE_JWT_SECRET =
        "NWNhYWRmODZlZDAzNzg4NGY2NTA0MzAyZTdjNjI2ZjYzYWI2YmQ3Yjk4ZDA5NGVkODNhYWY2ZmExOTJmZjA3YmJhNDdiYTYxMTdjZmM2ZWUyMmMwMzQyMTY1NDBjNTc5ODg4MjNkZjExODFmZjUwNDdlNjVhOTlhMWE5MjRjYTU=";

    @Test
    void nonProdProfile_doesNotValidate() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("dev");

        new CommerceProductionValidator(environment, SAMPLE_JWT_SECRET);
    }

    @Test
    void prodWithSecretSamplesProfile_throws() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod", "secret-samples");

        assertThatThrownBy(() -> new CommerceProductionValidator(environment, "custom-secret"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("secret-samples");
    }

    @Test
    void prodWithSampleJwtSecret_throws() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");

        assertThatThrownBy(() -> new CommerceProductionValidator(environment, SAMPLE_JWT_SECRET))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("sample JWT secret");
    }

    @Test
    void prodWithCustomJwtSecret_ok() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");

        new CommerceProductionValidator(environment, "custom-production-secret");
    }
}
