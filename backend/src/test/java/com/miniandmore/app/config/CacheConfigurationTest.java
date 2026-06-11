package com.miniandmore.app.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import javax.cache.CacheManager;
import javax.cache.configuration.Configuration;
import org.junit.jupiter.api.Test;

class CacheConfigurationTest {

    private final CacheConfiguration cacheConfiguration = new CacheConfiguration();

    @Test
    @SuppressWarnings("unchecked")
    void cacheManagerCustomizer_registersEntityCaches() {
        Configuration<Object, Object> jcacheConfiguration = mock(Configuration.class);
        CacheManager cacheManager = mock(CacheManager.class);
        when(cacheManager.getCache(any())).thenReturn(null);

        cacheConfiguration.cacheManagerCustomizer(jcacheConfiguration).customize(cacheManager);

        verify(cacheManager, atLeastOnce()).createCache(any(), eq(jcacheConfiguration));
    }

    @Test
    @SuppressWarnings("unchecked")
    void cacheManagerCustomizer_clearsExistingCaches() {
        Configuration<Object, Object> jcacheConfiguration = mock(Configuration.class);
        CacheManager cacheManager = mock(CacheManager.class);
        javax.cache.Cache<Object, Object> existingCache = mock(javax.cache.Cache.class);
        when(cacheManager.getCache(any())).thenReturn(existingCache);

        cacheConfiguration.cacheManagerCustomizer(jcacheConfiguration).customize(cacheManager);

        verify(existingCache, atLeastOnce()).clear();
    }
}
