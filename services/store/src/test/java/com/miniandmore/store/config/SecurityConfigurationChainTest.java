package com.miniandmore.store.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

class SecurityConfigurationChainTest {

    private static final String JWT_SECRET = Base64.getEncoder().encodeToString(new byte[64]);

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(TestSecurityConfig.class)
        .withPropertyValues("jhipster.security.authentication.jwt.base64-secret=" + JWT_SECRET);

    @Test
    void filterChainBeanIsCreated() {
        contextRunner.run(context -> assertThat(context.getBean(SecurityFilterChain.class)).isNotNull());
    }

    @Configuration
    @EnableWebSecurity
    @Import({ SecurityConfiguration.class, SecurityJwtConfiguration.class })
    static class TestSecurityConfig {}
}
