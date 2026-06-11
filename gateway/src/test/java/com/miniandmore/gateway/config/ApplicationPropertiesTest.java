package com.miniandmore.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ApplicationPropertiesTest {

    @Test
    void oauth2Properties_roundTrip() {
        ApplicationProperties properties = new ApplicationProperties();
        properties.getOAuth2().setFrontendCallbackUrl("https://app.example/auth");

        assertThat(properties.getOAuth2().getFrontendCallbackUrl()).isEqualTo("https://app.example/auth");
    }
}
