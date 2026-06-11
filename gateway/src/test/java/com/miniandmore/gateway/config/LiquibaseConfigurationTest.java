package com.miniandmore.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseProperties;
import org.springframework.boot.autoconfigure.r2dbc.R2dbcProperties;
import org.springframework.mock.env.MockEnvironment;
import tech.jhipster.config.JHipsterConstants;

class LiquibaseConfigurationTest {

    @Test
    void liquibase_beanIsCreated() {
        LiquibaseConfiguration configuration = new LiquibaseConfiguration(new MockEnvironment());
        LiquibaseProperties liquibaseProperties = new LiquibaseProperties();
        liquibaseProperties.setUrl("jdbc:postgresql://localhost:5432/gateway");
        liquibaseProperties.setEnabled(false);
        R2dbcProperties r2dbcProperties = new R2dbcProperties();
        r2dbcProperties.setUsername("gateway");
        r2dbcProperties.setPassword("gateway");

        assertThat(configuration.liquibase(Executors.newSingleThreadExecutor(), liquibaseProperties, r2dbcProperties)).isNotNull();
    }

    @Test
    void liquibase_noLiquibaseProfile_disablesExecution() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles(JHipsterConstants.SPRING_PROFILE_NO_LIQUIBASE);
        LiquibaseConfiguration configuration = new LiquibaseConfiguration(env);
        LiquibaseProperties liquibaseProperties = new LiquibaseProperties();
        liquibaseProperties.setUrl("jdbc:postgresql://localhost:5432/gateway");
        R2dbcProperties r2dbcProperties = new R2dbcProperties();

        var liquibase = configuration.liquibase(Executors.newSingleThreadExecutor(), liquibaseProperties, r2dbcProperties);

        assertThat(liquibase).isNotNull();
    }
}
