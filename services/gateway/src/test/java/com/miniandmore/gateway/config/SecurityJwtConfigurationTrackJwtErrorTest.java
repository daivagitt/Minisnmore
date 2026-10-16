package com.miniandmore.gateway.config;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.miniandmore.gateway.management.SecurityMetersService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

class SecurityJwtConfigurationTrackJwtErrorTest {

    @ParameterizedTest
    @CsvSource({ "true", "false" })
    void trackJwtError_nullMessage(boolean reactive) {
        SecurityMetersService meters = mock(SecurityMetersService.class);
        ReflectionTestUtils.invokeMethod(
            SecurityJwtConfiguration.class,
            "trackJwtError",
            new RuntimeException((String) null),
            meters,
            reactive
        );
        verifyNoInteractions(meters);
    }

    @Test
    void trackJwtError_expiredAndSignatureAndMalformed() {
        SecurityMetersService meters = mock(SecurityMetersService.class);
        ReflectionTestUtils.invokeMethod(
            SecurityJwtConfiguration.class,
            "trackJwtError",
            new RuntimeException("Jwt expired at now"),
            meters,
            true
        );
        verify(meters).trackTokenExpired();

        SecurityMetersService signature = mock(SecurityMetersService.class);
        ReflectionTestUtils.invokeMethod(
            SecurityJwtConfiguration.class,
            "trackJwtError",
            new RuntimeException("Failed to validate the token"),
            signature,
            false
        );
        verify(signature).trackTokenInvalidSignature();
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
            "Invalid JWT serialization: x",
            "Invalid unsecured/JWS/JWE header: y",
            "An error occurred while attempting to decode the Jwt",
        }
    )
    void trackJwtError_malformed(String message) {
        SecurityMetersService meters = mock(SecurityMetersService.class);
        ReflectionTestUtils.invokeMethod(SecurityJwtConfiguration.class, "trackJwtError", new RuntimeException(message), meters, true);
        verify(meters).trackTokenMalformed();
    }

    @Test
    void trackJwtError_unknown() {
        SecurityMetersService meters = mock(SecurityMetersService.class);
        ReflectionTestUtils.invokeMethod(SecurityJwtConfiguration.class, "trackJwtError", new RuntimeException("nope"), meters, false);
        verifyNoInteractions(meters);
    }
}
