package com.miniandmore.app;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import tech.jhipster.config.JHipsterConstants;

class MiniandmoreAppTest {

    @Test
    void initApplication_logsWhenDevAndProdProfilesCombined() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles(JHipsterConstants.SPRING_PROFILE_DEVELOPMENT, JHipsterConstants.SPRING_PROFILE_PRODUCTION);

        MiniandmoreApp app = new MiniandmoreApp(env);
        app.initApplication();

        assertThat(env.getActiveProfiles()).contains(JHipsterConstants.SPRING_PROFILE_DEVELOPMENT);
    }

    @Test
    void initApplication_logsWhenDevAndCloudProfilesCombined() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles(JHipsterConstants.SPRING_PROFILE_DEVELOPMENT, JHipsterConstants.SPRING_PROFILE_CLOUD);

        MiniandmoreApp app = new MiniandmoreApp(env);
        app.initApplication();

        assertThat(env.getActiveProfiles()).contains(JHipsterConstants.SPRING_PROFILE_CLOUD);
    }

    @Test
    void initApplication_singleProfile_doesNotThrow() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles(JHipsterConstants.SPRING_PROFILE_DEVELOPMENT);

        MiniandmoreApp app = new MiniandmoreApp(env);
        app.initApplication();
    }
}
