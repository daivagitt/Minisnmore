package com.miniandmore.gateway.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import tech.jhipster.config.JHipsterProperties;

class GatewayProductionValidatorTest {

    @Test
    void nonProdProfile_doesNotValidate() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("dev");

        new GatewayProductionValidator(environment, jhipsterProperties("custom-secret"));
    }

    @Test
    void prodWithSampleJwtSecret_throws() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        String sample =
            "NWNhYWRmODZlZDAzNzg4NGY2NTA0MzAyZTdjNjI2ZjYzYWI2YmQ3Yjk4ZDA5NGVkODNhYWY2ZmExOTJmZjA3YmJhNDdiYTYxMTdjZmM2ZWUyMmMwMzQyMTY1NDBjNTc5ODg4MjNkZjExODFmZjUwNDdlNjVhOTlhMWE5MjRjYTU=";

        assertThatThrownBy(() -> new GatewayProductionValidator(environment, jhipsterProperties(sample)))
            .isInstanceOf(IllegalStateException.class);
    }

    private static JHipsterProperties jhipsterProperties(String secret) {
        JHipsterProperties properties = new JHipsterProperties();
        properties.getSecurity().getAuthentication().getJwt().setBase64Secret(secret);
        return properties;
    }
}
