package com.miniandmore.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;

class SecurityConfigurationTest {

    @Test
    void reactiveAuthenticationManager_isConfigured() {
        SecurityConfiguration configuration = new SecurityConfiguration();
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        ReactiveUserDetailsService userDetailsService = mock(ReactiveUserDetailsService.class);

        ReactiveAuthenticationManager authManager = configuration.reactiveAuthenticationManager(userDetailsService, passwordEncoder);

        assertThat(authManager).isNotNull();
    }
}
