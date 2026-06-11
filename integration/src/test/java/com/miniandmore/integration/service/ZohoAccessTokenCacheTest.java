package com.miniandmore.integration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ZohoAccessTokenCacheTest {

    private ZohoAccessTokenCache cache;

    @BeforeEach
    void setUp() {
        cache = new ZohoAccessTokenCache();
    }

    @Test
    void getOrRefresh_cachesUntilExpiry() {
        var counter = new int[] { 0 };
        String first = cache.getOrRefresh("k1", () -> {
            counter[0]++;
            return new ZohoAccessTokenCache.TokenBundle("token-a", 3600);
        });
        String second = cache.getOrRefresh("k1", () -> {
            counter[0]++;
            return new ZohoAccessTokenCache.TokenBundle("token-b", 3600);
        });

        assertThat(first).isEqualTo("token-a");
        assertThat(second).isEqualTo("token-a");
        assertThat(counter[0]).isEqualTo(1);
        assertThat(cache.hasValidEntry("k1")).isTrue();
    }

    @Test
    void getOrRefresh_forceRefreshBypassesCache() {
        cache.getOrRefresh("k1", () -> new ZohoAccessTokenCache.TokenBundle("token-a", 3600));
        String forced = cache.getOrRefresh(
            "k1",
            () -> new ZohoAccessTokenCache.TokenBundle("token-b", 3600),
            true
        );
        assertThat(forced).isEqualTo("token-b");
    }

    @Test
    void invalidate_removesEntry() {
        cache.getOrRefresh("k1", () -> new ZohoAccessTokenCache.TokenBundle("token-a", 3600));
        cache.invalidate("k1");
        assertThat(cache.hasValidEntry("k1")).isFalse();
    }

    @Test
    void validationErrors() {
        assertThatThrownBy(() -> cache.getOrRefresh(null, () -> new ZohoAccessTokenCache.TokenBundle("t", 60)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> cache.getOrRefresh("k", null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> cache.getOrRefresh("k", () -> null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> cache.getOrRefresh("k", () -> new ZohoAccessTokenCache.TokenBundle(null, 60)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void hasValidEntry_nullKeyFalse() {
        assertThat(cache.hasValidEntry(null)).isFalse();
    }

    @Test
    void invalidate_nullKeyNoOp() {
        cache.invalidate(null);
    }
}
