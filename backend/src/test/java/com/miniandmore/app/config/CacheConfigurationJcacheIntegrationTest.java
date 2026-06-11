package com.miniandmore.app.config;

import static org.assertj.core.api.Assertions.assertThat;

import javax.cache.configuration.Configuration;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tech.jhipster.config.JHipsterProperties;

@Testcontainers(disabledWithoutDocker = true)
class CacheConfigurationJcacheIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @Test
    void jcacheConfiguration_singleServer_connectsToRedis() {
        CacheConfiguration cacheConfiguration = new CacheConfiguration();
        JHipsterProperties properties = new JHipsterProperties();
        properties.getCache().getRedis().setServer(new String[] { "redis://127.0.0.1:" + redis.getMappedPort(6379) });
        properties.getCache().getRedis().setCluster(false);
        properties.getCache().getRedis().setExpiration(60);

        Configuration<Object, Object> configuration = cacheConfiguration.jcacheConfiguration(properties);

        assertThat(configuration).isNotNull();
    }
}
