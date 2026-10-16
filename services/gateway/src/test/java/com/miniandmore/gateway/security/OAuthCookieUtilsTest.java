package com.miniandmore.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;

class OAuthCookieUtilsTest {

    @Test
    void isSecure_trueWhenXForwardedProtoIsHttps() {
        var request = MockServerHttpRequest.get("/oauth2/authorization/google").header("X-Forwarded-Proto", "https").build();
        assertThat(OAuthCookieUtils.isSecure(request)).isTrue();
    }

    @Test
    void isSecure_falseWithoutTlsOrForwardedHeader() {
        var request = MockServerHttpRequest.get("/oauth2/authorization/google").build();
        assertThat(OAuthCookieUtils.isSecure(request)).isFalse();
    }
}
