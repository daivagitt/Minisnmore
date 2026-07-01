package com.miniandmore.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.miniandmore.gateway.security.OAuth2AuthenticationFailureHandler;
import com.miniandmore.gateway.security.OAuth2AuthenticationSuccessHandler;
import com.miniandmore.gateway.security.WebFluxCookieOAuth2AuthorizationRequestRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;

class SecurityConfigurationTest {

    @Test
    void reactiveAuthenticationManager_isConfigured() {
        SecurityConfiguration configuration = new SecurityConfiguration(
            mock(OAuth2AuthenticationSuccessHandler.class),
            mock(OAuth2AuthenticationFailureHandler.class),
            mock(WebFluxCookieOAuth2AuthorizationRequestRepository.class)
        );
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        ReactiveUserDetailsService userDetailsService = mock(ReactiveUserDetailsService.class);

        ReactiveAuthenticationManager authManager = configuration.reactiveAuthenticationManager(userDetailsService, passwordEncoder);

        assertThat(authManager).isNotNull();
    }
}
