package com.miniandmore.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.miniandmore.gateway.config.ApplicationProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.InetSocketAddress;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class ApiRateLimitWebFilterTest {

    private ApplicationProperties properties;
    private RedisRequestLimiter limiter;
    private ApiRateLimitWebFilter filter;

    @BeforeEach
    void setUp() {
        properties = new ApplicationProperties();
        limiter = mock(RedisRequestLimiter.class);
        filter = new ApiRateLimitWebFilter(properties, limiter, new SimpleMeterRegistry(), exchange -> null);
        when(limiter.check(anyString(), any())).thenReturn(Mono.just(0L));
    }

    @Test
    void rejectsWithoutCallingDownstreamAndIncludesRetryAfter() {
        when(limiter.check(anyString(), any())).thenReturn(Mono.just(42L));
        var exchange = exchange("/api/v1/products");
        StepVerifier.create(filter.filter(exchange, ignored -> Mono.error(new AssertionError("Must not forward")))).verifyComplete();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(exchange.getResponse().getHeaders().getFirst("Retry-After")).isEqualTo("42");
        assertThat(exchange.getResponse().getBodyAsString().block()).contains("\"status\":429", "Please wait");
    }

    @Test
    void rejectedRequestsKeepCorsHeadersForAllowedOrigins() {
        var cors = new org.springframework.web.cors.CorsConfiguration();
        cors.addAllowedOrigin("https://store.example.com");
        cors.addAllowedMethod("GET");
        filter = new ApiRateLimitWebFilter(properties, limiter, new SimpleMeterRegistry(), exchange -> cors);
        when(limiter.check(anyString(), any())).thenReturn(Mono.just(5L));
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("http://gateway.example.com/api/v1/products")
            .header("Origin", "https://store.example.com").build());
        StepVerifier.create(filter.filter(exchange, ignored -> Mono.error(new AssertionError("Must not forward")))).verifyComplete();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(exchange.getResponse().getHeaders().getAccessControlAllowOrigin()).isEqualTo("https://store.example.com");
    }

    @Test
    void appliesBothGeneralAndSensitiveLimitsIncludingTrailingSlash() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/api/v1/orders/").build());
        StepVerifier.create(filter.filter(exchange, ignored -> Mono.empty())).verifyComplete();
        verify(limiter).check("gateway:rate-limit:general:unknown", properties.getRateLimiting().getGeneral());
        verify(limiter).check("gateway:rate-limit:orders:unknown", properties.getRateLimiting().getOrders());
    }

    @Test
    void encodedPathsAndMatrixParametersCannotBypassAuthenticationLimit() {
        var request = MockServerHttpRequest.method(HttpMethod.POST, java.net.URI.create("/%61pi/authentic%61te;ignored=value")).build();
        StepVerifier.create(filter.filter(MockServerWebExchange.from(request), ignored -> Mono.empty())).verifyComplete();
        verify(limiter).check("gateway:rate-limit:auth:unknown", properties.getRateLimiting().getAuth());
    }

    @Test
    void webhookHasIndependentBudget() {
        StepVerifier.create(filter.filter(exchange("/internal/v1/webhooks/razorpay"), ignored -> Mono.empty())).verifyComplete();
        verify(limiter).check("gateway:rate-limit:webhook:unknown", properties.getRateLimiting().getWebhook());
        verifyNoMoreInteractions(limiter);
    }

    @Test
    void failsClosedOnRedisErrorAndCanExplicitlyFailOpen() {
        when(limiter.check(anyString(), any())).thenReturn(Mono.error(new IllegalStateException("Redis unavailable")));
        var closed = exchange("/api/authenticate");
        StepVerifier.create(filter.filter(closed, ignored -> Mono.error(new AssertionError("Must not forward")))).verifyComplete();
        assertThat(closed.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        properties.getRateLimiting().setFailOpen(true);
        var open = exchange("/api/authenticate");
        StepVerifier.create(filter.filter(open, ignored -> { open.getAttributes().put("passed", true); return Mono.empty(); })).verifyComplete();
        assertThat(open.<Boolean>getAttribute("passed")).isTrue();
    }

    @Test
    void doesNotSwallowOrRepeatDownstreamFailures() {
        var error = new IllegalStateException("Downstream error");
        StepVerifier.create(filter.filter(exchange("/api/v1/products"), ignored -> Mono.error(error))).expectErrorMatches(e -> e == error).verify();
        verify(limiter, times(1)).check(anyString(), any());
    }

    @Test
    void skipsHealthPreflightAndDisabledLimiter() {
        StepVerifier.create(filter.filter(exchange("/management/health"), ignored -> Mono.empty())).verifyComplete();
        StepVerifier.create(filter.filter(MockServerWebExchange.from(MockServerHttpRequest.options("/api/v1/orders")), ignored -> Mono.empty())).verifyComplete();
        properties.getRateLimiting().setEnabled(false);
        StepVerifier.create(filter.filter(exchange("/api/authenticate"), ignored -> Mono.empty())).verifyComplete();
        verifyNoInteractions(limiter);
    }

    @Test
    void trustsRealIpOnlyFromExplicitProxyAndIgnoresXff() {
        var direct = MockServerHttpRequest.get("/api/v1/products").remoteAddress(new InetSocketAddress("192.0.2.1", 1234))
            .header("X-Real-IP", "203.0.113.9").header("X-Forwarded-For", "198.51.100.1").build();
        assertThat(filter.clientAddress(direct)).isEqualTo("192.0.2.1");
        var proxy = MockServerHttpRequest.get("/api/v1/products").remoteAddress(new InetSocketAddress("127.0.0.1", 1234))
            .header("X-Real-IP", "203.0.113.9").header("X-Forwarded-For", "198.51.100.1").build();
        assertThat(filter.clientAddress(proxy)).isEqualTo("203.0.113.9");
        var invalid = MockServerHttpRequest.get("/api/v1/products").remoteAddress(new InetSocketAddress("127.0.0.1", 1234))
            .header("X-Real-IP", "attacker.example.com").build();
        assertThat(filter.clientAddress(invalid)).isEqualTo("127.0.0.1");
    }

    @Test
    void nativeForwardedHeadersCannotReplaceTransportPeerForProxyTrust() {
        properties.getRateLimiting().setTrustedProxyAddresses(java.util.List.of());
        var handler = org.springframework.web.server.adapter.WebHttpHandlerBuilder
            .webHandler(exchange -> exchange.getResponse().setComplete()).filter(filter).build();
        var server = reactor.netty.http.server.HttpServer.create().host("127.0.0.1").port(0).forwarded(true)
            .handle(new org.springframework.http.server.reactive.ReactorHttpHandlerAdapter(handler)).bindNow();
        try {
            reactor.netty.http.client.HttpClient.create().host("127.0.0.1").port(server.port())
                .headers(headers -> headers.set("X-Forwarded-For", "198.51.100.2").set("X-Real-IP", "203.0.113.9"))
                .get().uri("/api/v1/products").response().block(java.time.Duration.ofSeconds(5));
            verify(limiter).check("gateway:rate-limit:general:127.0.0.1", properties.getRateLimiting().getGeneral());
        } finally {
            server.disposeNow();
        }
    }

    @ParameterizedTest
    @CsvSource({
        "/api/authenticate,POST,auth", "/api/register,POST,auth", "/api/account/reset-password/init,POST,auth",
        "/oauth2/authorization/google,GET,auth", "/login/oauth2/code/google,GET,auth",
        "/api/v1/orders,POST,orders", "/services/miniandmore-commerce/api/v1/orders,POST,orders", "/api/v1/orders,GET,general", "/api/v1/orders/sync-payment,POST,payment",
        "/api/v1/orders/verify-payment,POST,payment", "/api/v1/orders/retry-payment,POST,payment",
        "/api/v1/admin/zoho/sync,POST,admin", "/api/v1/zoho/invoice-webhook,POST,webhook",
        "/internal/v1/ledgerly/webhooks,POST,webhook", "/api/v1/admin/webhook-settings,POST,admin"
    })
    void classifiesPolicies(String path, String method, String expected) {
        assertThat(ApiRateLimitWebFilter.policyName(path, HttpMethod.valueOf(method))).isEqualTo(expected);
    }

    private static MockServerWebExchange exchange(String path) {
        return MockServerWebExchange.from(MockServerHttpRequest.get(path).build());
    }
}
