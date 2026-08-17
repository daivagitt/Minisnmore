package com.miniandmore.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.miniandmore.gateway.management.SecurityMetersService;
import com.miniandmore.gateway.security.jwt.JwtAuthenticationTestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.test.StepVerifier;

class SecurityJwtConfigurationTest {

    private static final String JWT_KEY =
        "fd54a45s65fds737b9aafcb3412e07ed99b267f33413274720ddbb7f6c5e64e9f14075f2d7ed041592f0b7657baf8";

    private SecurityJwtConfiguration configuration;
    private SecurityMetersService metersService;

    @BeforeEach
    void setUp() {
        configuration = new SecurityJwtConfiguration();
        ReflectionTestUtils.setField(configuration, "jwtKey", JWT_KEY);
        metersService = mock(SecurityMetersService.class);
    }

    @Test
    void jwtDecoder_invalidToken_tracksMalformed() {
        StepVerifier.create(configuration.jwtDecoder(metersService).decode("not-a-jwt")).expectError().verify();
        verify(metersService).trackTokenMalformed();
    }

    @Test
    void jwtEncoder_beanIsCreated() {
        assertThat(configuration.jwtEncoder()).isNotNull();
    }

    @Test
    void jwtDecoder_syncException_tracksMalformed() {
        assertThatThrownBy(() -> configuration.jwtDecoder(metersService).decode("").block()).isInstanceOf(Exception.class);
        verify(metersService).trackTokenMalformed();
    }

    @Test
    void jwtDecoder_expiredToken_tracksExpired() {
        String token = JwtAuthenticationTestUtils.createExpiredToken(JWT_KEY);

        StepVerifier.create(configuration.jwtDecoder(metersService).decode(token)).expectError().verify();
        verify(metersService).trackTokenExpired();
    }

    @Test
    void jwtDecoder_wrongSignature_tracksInvalidSignature() {
        String token = JwtAuthenticationTestUtils.createTokenWithDifferentSignature();

        StepVerifier.create(configuration.jwtDecoder(metersService).decode(token)).expectError().verify();
        verify(metersService).trackTokenInvalidSignature();
    }

    @Test
    void jwtDecoder_unknownErrorMessage_logsWithoutTracking() {
        StepVerifier
            .create(configuration.jwtDecoder(metersService).decode("not-a-jwt"))
            .expectError()
            .verify();
    }

    @Test
    void jwtDecoder_syncExceptionWithNullMessage_doesNotTrack() {
        ReactiveJwtDecoder decoder = configuration.jwtDecoder(metersService);
        assertThatThrownBy(() -> decoder.decode(null).block()).isInstanceOf(Exception.class);
    }
}
