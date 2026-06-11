package com.miniandmore.app;

import com.miniandmore.app.config.AsyncSyncConfiguration;
import com.miniandmore.app.config.DatabaseTestcontainer;
import com.miniandmore.app.config.JacksonConfiguration;
import com.miniandmore.app.config.RedisTestContainer;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;

/**
 * Base composite annotation for integration tests.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(
    classes = {
        MiniandmoreApp.class,
        JacksonConfiguration.class,
        AsyncSyncConfiguration.class,
        com.miniandmore.app.config.JacksonHibernateConfiguration.class,
    }
)
@ImportTestcontainers({ DatabaseTestcontainer.class, RedisTestContainer.class })
public @interface IntegrationTest {}
