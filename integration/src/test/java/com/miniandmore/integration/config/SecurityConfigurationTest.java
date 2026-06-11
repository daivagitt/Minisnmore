package com.miniandmore.integration.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.web.SecurityFilterChain;

class SecurityConfigurationTest {

    @Test
    void configurationClassExists() {
        SecurityConfiguration configuration = new SecurityConfiguration();
        assertThat(configuration).isNotNull();
        assertThat(SecurityFilterChain.class).isNotNull();
    }
}
