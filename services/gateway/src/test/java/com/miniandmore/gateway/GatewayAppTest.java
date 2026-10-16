package com.miniandmore.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import tech.jhipster.config.JHipsterConstants;

class GatewayAppTest {

    @Test
    void initApplication_logsWhenDevAndProdProfilesCombined() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles(JHipsterConstants.SPRING_PROFILE_DEVELOPMENT, JHipsterConstants.SPRING_PROFILE_PRODUCTION);

        GatewayApp app = new GatewayApp(env);
        app.initApplication();

        assertThat(env.getActiveProfiles()).contains(JHipsterConstants.SPRING_PROFILE_DEVELOPMENT);
    }

    @Test
    void initApplication_logsWhenDevAndCloudProfilesCombined() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles(JHipsterConstants.SPRING_PROFILE_DEVELOPMENT, JHipsterConstants.SPRING_PROFILE_CLOUD);

        GatewayApp app = new GatewayApp(env);
        app.initApplication();

        assertThat(env.getActiveProfiles()).contains(JHipsterConstants.SPRING_PROFILE_CLOUD);
    }

    @Test
    void initApplication_singleProfile_doesNotThrow() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles(JHipsterConstants.SPRING_PROFILE_DEVELOPMENT);

        GatewayApp app = new GatewayApp(env);
        app.initApplication();
    }

    @Test
    void logApplicationStartup_logsWithHttpsWhenSslConfigured() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("server.ssl.key-store", "keystore");
        env.setProperty("spring.application.name", "gateway");
        env.setProperty("server.port", "8080");
        env.setProperty("configserver.status", "connected");

        org.springframework.test.util.ReflectionTestUtils.invokeMethod(GatewayApp.class, "logApplicationStartup", env);
    }

    @Test
    void logApplicationStartup_httpAndDefaultProfiles() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("spring.application.name", "gateway");
        env.setProperty("server.port", "8080");
        env.setDefaultProfiles("dev");

        org.springframework.test.util.ReflectionTestUtils.invokeMethod(GatewayApp.class, "logApplicationStartup", env);
    }

    @Test
    void logApplicationStartup_withContextPath() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("spring.application.name", "gateway");
        env.setProperty("server.port", "8080");
        env.setProperty("server.servlet.context-path", "/gateway");
        env.setActiveProfiles("dev");

        org.springframework.test.util.ReflectionTestUtils.invokeMethod(GatewayApp.class, "logApplicationStartup", env);
    }
}
