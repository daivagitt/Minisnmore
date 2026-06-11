package com.miniandmore.app.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import tech.jhipster.config.JHipsterProperties;

class CacheConfigurationJcacheMockTest {

    @Test
    void jcacheConfiguration_singleServer_usesRedissonClient() {
        CacheConfiguration cacheConfiguration = new CacheConfiguration();
        JHipsterProperties properties = new JHipsterProperties();
        properties.getCache().getRedis().setServer(new String[] { "redis://127.0.0.1:6379" });
        properties.getCache().getRedis().setCluster(false);
        properties.getCache().getRedis().setExpiration(3600);

        RedissonClient client = mock(RedissonClient.class);
        try (MockedStatic<Redisson> redissonStatic = mockStatic(Redisson.class)) {
            redissonStatic.when(() -> Redisson.create(any(org.redisson.config.Config.class))).thenReturn(client);

            javax.cache.configuration.Configuration<Object, Object> configuration = cacheConfiguration.jcacheConfiguration(properties);

            assertThat(configuration).isNotNull();
        }
    }

    @Test
    void jcacheConfiguration_clusterWithPassword_usesClusterServers() {
        CacheConfiguration cacheConfiguration = new CacheConfiguration();
        JHipsterProperties properties = new JHipsterProperties();
        properties.getCache().getRedis().setServer(new String[] { "redis://:secret@127.0.0.1:6379", "redis://127.0.0.1:6380" });
        properties.getCache().getRedis().setCluster(true);
        properties.getCache().getRedis().setExpiration(3600);

        RedissonClient client = mock(RedissonClient.class);
        try (MockedStatic<Redisson> redissonStatic = mockStatic(Redisson.class)) {
            redissonStatic.when(() -> Redisson.create(any(org.redisson.config.Config.class))).thenReturn(client);

            javax.cache.configuration.Configuration<Object, Object> configuration = cacheConfiguration.jcacheConfiguration(properties);

            assertThat(configuration).isNotNull();
        }
    }
}
