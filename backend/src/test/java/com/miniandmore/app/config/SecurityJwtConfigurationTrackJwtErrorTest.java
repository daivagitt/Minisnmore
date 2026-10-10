package com.miniandmore.app.config;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.miniandmore.app.management.SecurityMetersService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

class SecurityJwtConfigurationTrackJwtErrorTest {

    @Test
    void trackJwtError_nullMessageLogsAndReturns() {
        SecurityMetersService meters = mock(SecurityMetersService.class);
        ReflectionTestUtils.invokeMethod(
            SecurityJwtConfiguration.class,
            "trackJwtError",
            new RuntimeException((String) null),
            meters
        );
        verifyNoInteractions(meters);
    }

    @Test
    void trackJwtError_invalidSignature() {
        SecurityMetersService meters = mock(SecurityMetersService.class);
        ReflectionTestUtils.invokeMethod(
            SecurityJwtConfiguration.class,
            "trackJwtError",
            new RuntimeException("Invalid signature at idx"),
            meters
        );
        verify(meters).trackTokenInvalidSignature();
    }

    @Test
    void trackJwtError_expired() {
        SecurityMetersService meters = mock(SecurityMetersService.class);
        ReflectionTestUtils.invokeMethod(
            SecurityJwtConfiguration.class,
            "trackJwtError",
            new RuntimeException("Jwt expired at 2020-01-01"),
            meters
        );
        verify(meters).trackTokenExpired();
    }

    @ParameterizedTest
    @ValueSource(strings = { "Invalid JWT serialization xyz", "Malformed token", "Invalid unsecured/JWS/JWE header" })
    void trackJwtError_malformedTokens(String message) {
        SecurityMetersService meters = mock(SecurityMetersService.class);
        ReflectionTestUtils.invokeMethod(SecurityJwtConfiguration.class, "trackJwtError", new RuntimeException(message), meters);
        verify(meters).trackTokenMalformed();
    }

    @Test
    void trackJwtError_unknownMessageDoesNotTrack() {
        SecurityMetersService meters = mock(SecurityMetersService.class);
        ReflectionTestUtils.invokeMethod(SecurityJwtConfiguration.class, "trackJwtError", new RuntimeException("something else"), meters);
        verifyNoInteractions(meters);
    }
}
