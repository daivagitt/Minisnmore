package com.miniandmore.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.miniandmore.gateway.management.SecurityMetersService;
import com.miniandmore.gateway.security.OAuth2AuthenticationFailureHandler;
import com.miniandmore.gateway.security.OAuth2AuthenticationSuccessHandler;
import com.miniandmore.gateway.security.WebFluxCookieOAuth2AuthorizationRequestRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.web.server.SecurityWebFilterChain;

class SecurityConfigurationChainTest {

    private static final String JWT_SECRET = Base64.getEncoder().encodeToString(new byte[64]);

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(TestSecurityConfig.class)
        .withPropertyValues("jhipster.security.authentication.jwt.base64-secret=" + JWT_SECRET);

    @Test
    void filterChainBeanIsCreated() {
        contextRunner.run(context -> assertThat(context.getBean(SecurityWebFilterChain.class)).isNotNull());
    }

    @Configuration
    @EnableWebFluxSecurity
    @Import({ SecurityConfiguration.class, SecurityJwtConfiguration.class })
    static class TestSecurityConfig {

        @Bean
        SecurityMetersService securityMetersService() {
            return new SecurityMetersService(new SimpleMeterRegistry());
        }

        @Bean
        ReactiveUserDetailsService reactiveUserDetailsService() {
            return mock(ReactiveUserDetailsService.class);
        }

        @Bean
        PasswordEncoder passwordEncoder() {
            return Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8();
        }

        @Bean
        ReactiveClientRegistrationRepository clientRegistrationRepository() {
            ClientRegistration registration = ClientRegistration
                .withRegistrationId("google")
                .clientId("client-id")
                .clientSecret("client-secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .authorizationUri("https://accounts.google.com/o/oauth2/auth")
                .tokenUri("https://oauth2.googleapis.com/token")
                .userInfoUri("https://openidconnect.googleapis.com/v1/userinfo")
                .userNameAttributeName("sub")
                .clientName("Google")
                .build();
            return new InMemoryReactiveClientRegistrationRepository(registration);
        }

        @Bean
        OAuth2AuthenticationSuccessHandler oauth2AuthenticationSuccessHandler() {
            return mock(OAuth2AuthenticationSuccessHandler.class);
        }

        @Bean
        OAuth2AuthenticationFailureHandler oauth2AuthenticationFailureHandler() {
            return mock(OAuth2AuthenticationFailureHandler.class);
        }

        @Bean
        WebFluxCookieOAuth2AuthorizationRequestRepository authorizationRequestRepository() {
            return new WebFluxCookieOAuth2AuthorizationRequestRepository();
        }
    }
}
