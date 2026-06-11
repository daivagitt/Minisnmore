package com.miniandmore.integration.service;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * Zoho access tokens are valid for ~1 hour but bulk sync used to request a new token per API call,
 * which triggers Zoho rate limits ("Access Denied" on refresh). Cache per connected account.
 */
@Component
class ZohoAccessTokenCache {

    private static final class CachedToken {

        private final String accessToken;
        private final Instant expiresAt;

        private CachedToken(String accessToken, Instant expiresAt) {
            this.accessToken = accessToken;
            this.expiresAt = expiresAt;
        }
    }

    private final ConcurrentHashMap<String, CachedToken> tokens = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Object> keyLocks = new ConcurrentHashMap<>();

    String getOrRefresh(String cacheKey, Supplier<TokenBundle> refresher) {
        return getOrRefresh(cacheKey, refresher, false);
    }

    String getOrRefresh(String cacheKey, Supplier<TokenBundle> refresher, boolean forceRefresh) {
        if (cacheKey == null) {
            throw new IllegalArgumentException("cacheKey must not be null");
        }
        if (refresher == null) {
            throw new IllegalArgumentException("refresher must not be null");
        }
        Object lock = keyLocks.computeIfAbsent(cacheKey, ignored -> new Object());
        synchronized (lock) {
            Instant now = Instant.now();
            CachedToken cached = tokens.get(cacheKey);
            if (!forceRefresh && cached != null && now.isBefore(cached.expiresAt)) {
                return cached.accessToken;
            }
            TokenBundle fresh = refresher.get();
            if (fresh == null) {
                throw new IllegalArgumentException("refresher returned null TokenBundle");
            }
            String accessToken = fresh.accessToken();
            if (accessToken == null) {
                throw new IllegalArgumentException("TokenBundle.accessToken must not be null");
            }
            long ttlSeconds = fresh.expiresInSeconds() <= 0 ? 0 : Math.max(60, fresh.expiresInSeconds() - 120);
            tokens.put(cacheKey, new CachedToken(accessToken, now.plusSeconds(ttlSeconds)));
            return accessToken;
        }
    }

    boolean hasValidEntry(String cacheKey) {
        if (cacheKey == null) {
            return false;
        }
        CachedToken cached = tokens.get(cacheKey);
        return cached != null && Instant.now().isBefore(cached.expiresAt);
    }

    void invalidate(String cacheKey) {
        if (cacheKey == null) {
            return;
        }
        Object lock = keyLocks.get(cacheKey);
        if (lock == null) {
            return;
        }
        synchronized (lock) {
            tokens.remove(cacheKey);
        }
    }

    record TokenBundle(String accessToken, long expiresInSeconds) {}
}
