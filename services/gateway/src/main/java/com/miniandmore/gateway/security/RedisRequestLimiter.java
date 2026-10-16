package com.miniandmore.gateway.security;

import com.miniandmore.gateway.config.RateLimitingProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class RedisRequestLimiter {

    private static final RedisScript<Long> SCRIPT = RedisScript.of(loadScript(), Long.class);
    private final ReactiveStringRedisTemplate redis;

    public RedisRequestLimiter(ReactiveStringRedisTemplate redis) {
        this.redis = redis;
    }

    private static String loadScript() {
        try {
            // Load at startup, so Redis NOSCRIPT fallback never reads a resource on an event loop.
            return new ClassPathResource("rate-limit.lua").getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new ExceptionInInitializerError(error);
        }
    }

    /** Returns zero when allowed, or the seconds until the current window expires. */
    public Mono<Long> check(String key, RateLimitingProperties.Policy policy) {
        return redis.execute(SCRIPT, List.of(key), List.of(
            Integer.toString(policy.getRequests()), Integer.toString(policy.getWindowSeconds())
        )).single().timeout(Duration.ofMillis(500));
    }
}
