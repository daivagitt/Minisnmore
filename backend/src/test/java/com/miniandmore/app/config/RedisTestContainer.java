package com.miniandmore.app.config;

import org.slf4j.LoggerFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.junit.jupiter.Container;

public interface RedisTestContainer {
    @Container
    GenericContainer redisContainer = new GenericContainer("redis:8.6.1")
        .withExposedPorts(6379)
        .withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger(RedisTestContainer.class)))
        .withReuse(true);

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add(
            "jhipster.cache.redis.server",
            () -> "redis://" + redisContainer.getContainerIpAddress() + ":" + redisContainer.getMappedPort(6379)
        );
    }
}
