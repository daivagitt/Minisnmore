package com.miniandmore.store.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.util.ReflectionTestUtils;

class SecurityJwtConfigurationTest {

    private static final String JWT_KEY =
        "NWNhYWRmODZlZDAzNzg4NGY2NTA0MzAyZTdjNjI2ZjYzYWI2YmQ3Yjk4ZDA5NGVkODNhYWY2ZmExOTJmZjA3YmJhNDdiYTYxMTdjZmM2ZWUyMmMwMzQyMTY1NDBjNTc5ODg4MjNkZjExODFmZjUwNDdlNjVhOTlhMWE5MjRjYTU=";

    private SecurityJwtConfiguration configuration;

    @BeforeEach
    void setUp() {
        configuration = new SecurityJwtConfiguration();
        ReflectionTestUtils.setField(configuration, "jwtKey", JWT_KEY);
    }

    @Test
    void jwtDecoder_isCreated() {
        JwtDecoder decoder = configuration.jwtDecoder();
        assertThat(decoder).isNotNull();
    }

    @Test
    void jwtDecoder_rejectsMalformedToken() {
        JwtDecoder decoder = configuration.jwtDecoder();
        assertThatThrownBy(() -> decoder.decode("not-a-jwt")).isInstanceOf(Exception.class);
    }
}
