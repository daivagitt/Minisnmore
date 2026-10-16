package com.miniandmore.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class RateLimitingConfigurationTest {

    private final ApplicationContextRunner context = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
        .withUserConfiguration(PropertiesConfiguration.class);

    @Test
    void bindsPolicyOverrides() {
        context.withPropertyValues("application.rate-limiting.payment.requests=90", "application.rate-limiting.enabled=false")
            .run(application -> {
                assertThat(application).hasNotFailed();
                var properties = application.getBean(ApplicationProperties.class).getRateLimiting();
                assertThat(properties.getPayment().getRequests()).isEqualTo(90);
                assertThat(properties.isEnabled()).isFalse();
            });
    }

    @Test
    void rejectsInvalidWindowAndAllowanceAtStartup() {
        context.withPropertyValues("application.rate-limiting.auth.requests=0").run(application -> assertThat(application).hasFailed());
        context.withPropertyValues("application.rate-limiting.payment.window-seconds=0").run(application -> assertThat(application).hasFailed());
    }

    @Test
    void splitsCommaSeparatedTrustedProxiesFromSingleProperty() {
        context.withPropertyValues("application.rate-limiting.trusted-proxy-addresses=127.0.0.1,::1,10.0.0.5").run(application -> {
            assertThat(application).hasNotFailed();
            assertThat(application.getBean(ApplicationProperties.class).getRateLimiting().getTrustedProxyAddresses())
                .containsExactly("127.0.0.1", "::1", "10.0.0.5");
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ApplicationProperties.class)
    static class PropertiesConfiguration {}
}
