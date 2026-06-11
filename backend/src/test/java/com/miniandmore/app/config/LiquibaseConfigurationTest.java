package com.miniandmore.app.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.concurrent.Executor;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.boot.liquibase.autoconfigure.LiquibaseProperties;
import org.springframework.mock.env.MockEnvironment;
import tech.jhipster.config.JHipsterConstants;

class LiquibaseConfigurationTest {

    @Test
    void liquibase_asyncStart_buildsSpringLiquibaseBean() {
        MockEnvironment env = new MockEnvironment();
        LiquibaseConfiguration configuration = new LiquibaseConfiguration(env);
        ApplicationProperties applicationProperties = new ApplicationProperties();
        applicationProperties.getLiquibase().setAsyncStart(true);
        LiquibaseProperties liquibaseProperties = new LiquibaseProperties();
        liquibaseProperties.setEnabled(true);
        liquibaseProperties.setContexts(List.of("dev"));
        liquibaseProperties.setDefaultSchema("public");
        DataSourceProperties dataSourceProperties = new DataSourceProperties();
        dataSourceProperties.setUrl("jdbc:h2:mem:test");
        DataSource dataSource = mock(DataSource.class);
        Executor executor = mock(Executor.class);
        ObjectProvider<DataSource> liquibaseDataSource = mock(ObjectProvider.class);
        ObjectProvider<DataSource> defaultDataSource = mock(ObjectProvider.class);
        org.mockito.Mockito.when(liquibaseDataSource.getIfAvailable()).thenReturn(dataSource);
        org.mockito.Mockito.when(defaultDataSource.getIfUnique()).thenReturn(dataSource);

        var liquibase = configuration.liquibase(
            executor,
            liquibaseProperties,
            liquibaseDataSource,
            defaultDataSource,
            applicationProperties,
            dataSourceProperties
        );

        assertThat(liquibase.getChangeLog()).isEqualTo("classpath:config/liquibase/master.xml");
        assertThat(liquibase.getDefaultSchema()).isEqualTo("public");
    }

    @Test
    void liquibase_noLiquibaseProfile_disablesExecution() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles(JHipsterConstants.SPRING_PROFILE_NO_LIQUIBASE);
        LiquibaseConfiguration configuration = new LiquibaseConfiguration(env);
        ApplicationProperties applicationProperties = new ApplicationProperties();
        applicationProperties.getLiquibase().setAsyncStart(false);
        LiquibaseProperties liquibaseProperties = new LiquibaseProperties();
        liquibaseProperties.setEnabled(true);
        DataSourceProperties dataSourceProperties = new DataSourceProperties();
        dataSourceProperties.setUrl("jdbc:h2:mem:test");
        DataSource dataSource = mock(DataSource.class);
        Executor executor = mock(Executor.class);
        ObjectProvider<DataSource> liquibaseDataSource = mock(ObjectProvider.class);
        ObjectProvider<DataSource> defaultDataSource = mock(ObjectProvider.class);
        org.mockito.Mockito.when(liquibaseDataSource.getIfAvailable()).thenReturn(dataSource);
        org.mockito.Mockito.when(defaultDataSource.getIfUnique()).thenReturn(dataSource);

        var liquibase = configuration.liquibase(
            executor,
            liquibaseProperties,
            liquibaseDataSource,
            defaultDataSource,
            applicationProperties,
            dataSourceProperties
        );

        assertThat(liquibase.getChangeLog()).isEqualTo("classpath:config/liquibase/master.xml");
    }
}
