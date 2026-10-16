package com.miniandmore.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class JacksonConfigurationTest {

    @Test
    void jacksonConfiguration_registersModules() {
        JacksonConfiguration configuration = new JacksonConfiguration();

        assertThat(configuration.javaTimeModule()).isNotNull();
        assertThat(configuration.jdk8TimeModule()).isNotNull();
    }
}
