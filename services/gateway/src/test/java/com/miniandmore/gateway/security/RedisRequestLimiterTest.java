package com.miniandmore.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.miniandmore.gateway.config.RateLimitingProperties;
import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;
import reactor.util.retry.Retry;

/** Exercises the real Lua script with an isolated Redis, when redis-server is installed. */
class RedisRequestLimiterTest {

    private static Process server;
    private static LettuceConnectionFactory connection;
    private static ReactiveStringRedisTemplate redis;

    @BeforeAll
    static void startRedis() throws IOException {
        int port;
        try (var socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        try {
            server = new ProcessBuilder("redis-server", "--bind", "127.0.0.1", "--port", Integer.toString(port),
                "--save", "", "--appendonly", "no").redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD).start();
        } catch (IOException error) {
            Assumptions.abort("redis-server is not installed");
        }
        connection = new LettuceConnectionFactory("127.0.0.1", port);
        connection.afterPropertiesSet();
        connection.start();
        redis = new ReactiveStringRedisTemplate(connection);
        redis.hasKey("ready").retryWhen(Retry.fixedDelay(30, Duration.ofMillis(50))).block(Duration.ofSeconds(5));
    }

    @AfterAll
    static void stopRedis() {
        if (connection != null) connection.destroy();
        if (server != null) server.destroy();
    }

    @Test
    void concurrentGatewaysShareOneAtomicLimitAndKeysExpire() {
        String key = "test:rate-limit:" + UUID.randomUUID();
        var policy = new RateLimitingProperties.Policy(5, 60);
        var firstGateway = new RedisRequestLimiter(redis);
        var secondGateway = new RedisRequestLimiter(redis);
        var decisions = Flux.range(0, 50).flatMap(index ->
            (index % 2 == 0 ? firstGateway : secondGateway).check(key, policy), 20
        ).collectList().block(Duration.ofSeconds(5));
        assertThat(decisions).hasSize(50);
        assertThat(decisions.stream().filter(wait -> wait == 0).count()).isEqualTo(5);
        assertThat(decisions.stream().filter(wait -> wait > 0).count()).isEqualTo(45);
        assertThat(redis.getExpire(key).block()).isBetween(Duration.ofSeconds(1), Duration.ofSeconds(60));
    }

    @Test
    void resetsAfterExpiryAndKeepsClientsIndependent() {
        String key = "test:rate-limit:" + UUID.randomUUID();
        var limiter = new RedisRequestLimiter(redis);
        var policy = new RateLimitingProperties.Policy(1, 1);
        StepVerifier.create(limiter.check(key, policy)).expectNext(0L).verifyComplete();
        StepVerifier.create(limiter.check(key, policy)).expectNext(1L).verifyComplete();
        StepVerifier.create(limiter.check(key + ":other-client", policy)).expectNext(0L).verifyComplete();
        StepVerifier.create(reactor.core.publisher.Mono.delay(Duration.ofMillis(1100)).then(limiter.check(key, policy)))
            .expectNext(0L).verifyComplete();
    }
}
