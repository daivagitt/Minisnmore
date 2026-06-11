package com.miniandmore.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.task.TaskExecutionProperties;

class AsyncConfigurationTest {

    @Test
    void getAsyncExecutor_returnsExecutor() {
        TaskExecutionProperties properties = new TaskExecutionProperties();
        properties.getPool().setCoreSize(2);
        properties.getPool().setMaxSize(4);
        properties.getPool().setQueueCapacity(100);
        properties.setThreadNamePrefix("test-");

        AsyncConfiguration configuration = new AsyncConfiguration(properties);

        assertThat(configuration.getAsyncExecutor()).isNotNull();
        assertThat(configuration.getAsyncUncaughtExceptionHandler()).isNotNull();
    }
}
