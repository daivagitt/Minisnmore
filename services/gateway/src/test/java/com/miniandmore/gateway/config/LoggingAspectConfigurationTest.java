package com.miniandmore.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class LoggingAspectConfigurationTest {

    @Test
    void loggingAspect_isCreated() {
        LoggingAspectConfiguration configuration = new LoggingAspectConfiguration();
        assertThat(configuration.loggingAspect(new MockEnvironment())).isNotNull();
    }
}
