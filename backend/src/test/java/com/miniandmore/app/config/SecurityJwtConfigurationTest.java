package com.miniandmore.app.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.miniandmore.app.management.SecurityMetersService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.util.ReflectionTestUtils;

class SecurityJwtConfigurationTest {

    private SecurityJwtConfiguration configuration;
    private SecurityMetersService metersService;

    @BeforeEach
    void setUp() {
        configuration = new SecurityJwtConfiguration();
        ReflectionTestUtils.setField(
            configuration,
            "jwtKey",
            "NWNhYWRmODZlZDAzNzg4NGY2NTA0MzAyZTdjNjI2ZjYzYWI2YmQ3Yjk4ZDA5NGVkODNhYWY2ZmExOTJmZjA3YmJhNDdiYTYxMTdjZmM2ZWUyMmMwMzQyMTY1NDBjNTc5ODg4MjNkZjExODFmZjUwNDdlNjVhOTlhMWE5MjRjYTU="
        );
        metersService = mock(SecurityMetersService.class);
    }

    @Test
    void jwtDecoder_invalidToken_throws() {
        JwtDecoder decoder = configuration.jwtDecoder(metersService);

        assertThatThrownBy(() -> decoder.decode("not-a-jwt")).isInstanceOf(Exception.class);
        verify(metersService).trackTokenMalformed();
    }

    @Test
    void jwtDecoder_beanIsCreated() {
        assertThat(configuration.jwtDecoder(metersService)).isNotNull();
    }
}
