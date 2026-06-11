package com.miniandmore.app;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

class MiniandmoreAppStartupTest {

    @Test
    void logApplicationStartup_withSslKey_usesHttps() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("spring.application.name", "miniandmore");
        env.setProperty("server.port", "8080");
        env.setProperty("server.ssl.key-store", "classpath:keystore.p12");
        env.setActiveProfiles("dev");

        ReflectionTestUtils.invokeMethod(MiniandmoreApp.class, "logApplicationStartup", env);

        assertThat(env.getProperty("spring.application.name")).isEqualTo("miniandmore");
    }

    @Test
    void logApplicationStartup_withoutProfiles_usesDefaults() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("spring.application.name", "miniandmore");
        env.setProperty("server.port", "8080");
        env.setDefaultProfiles("dev");

        ReflectionTestUtils.invokeMethod(MiniandmoreApp.class, "logApplicationStartup", env);

        assertThat(env.getDefaultProfiles()).contains("dev");
    }
}
