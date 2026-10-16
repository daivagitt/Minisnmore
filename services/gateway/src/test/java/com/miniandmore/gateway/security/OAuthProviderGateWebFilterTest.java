package com.miniandmore.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.miniandmore.gateway.config.ApplicationProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class OAuthProviderGateWebFilterTest {

    private ApplicationProperties applicationProperties;
    private OAuthProviderGateWebFilter filter;

    @BeforeEach
    void setUp() {
        applicationProperties = new ApplicationProperties();
        applicationProperties.getOAuth2().setFrontendCallbackUrl("http://localhost:5173/auth/callback");
        applicationProperties.getOAuth2().setEnabledProviders(java.util.List.of("google"));
        filter = new OAuthProviderGateWebFilter(applicationProperties);
    }

    @Test
    void allowsEnabledProvider() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/oauth2/authorization/google").build()
        );
        WebFilterChain chain = serverWebExchange -> Mono.empty();

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void blocksDisabledProvider() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/oauth2/authorization/facebook").build()
        );
        WebFilterChain chain = serverWebExchange -> Mono.empty();

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(exchange.getResponse().getHeaders().getLocation()).hasToString(
            "http://localhost:5173/auth/callback?error=" +
            java.net.URLEncoder.encode(OAuthProviderGateWebFilter.DISABLED_PROVIDER_MESSAGE, java.nio.charset.StandardCharsets.UTF_8)
        );
    }

    @Test
    void ignoresUnrelatedPaths() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/authenticate").build());
        WebFilterChain chain = serverWebExchange -> {
            serverWebExchange.getAttributes().put("passed", true);
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(exchange.<Boolean>getAttribute("passed")).isTrue();
    }
}
