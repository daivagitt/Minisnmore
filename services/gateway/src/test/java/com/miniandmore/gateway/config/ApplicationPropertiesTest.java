package com.miniandmore.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ApplicationPropertiesTest {

    @Test
    void registerAutoActivate_roundTrip() {
        ApplicationProperties properties = new ApplicationProperties();
        properties.setRegisterAutoActivate(true);
        assertThat(properties.isRegisterAutoActivate()).isTrue();
    }
}
