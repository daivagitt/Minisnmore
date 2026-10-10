package com.miniandmore.app.aop.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.util.ReflectionTestUtils;

class LoggingAspectSanitizeBranchTest {

    @ParameterizedTest
    @MethodSource("sensitiveValues")
    void sanitizeValue_redactsEverySensitiveTypeName(Object value) {
        Object sanitized = ReflectionTestUtils.invokeMethod(
            LoggingAspect.class,
            "sanitizeValue",
            value
        );
        assertThat(sanitized).isEqualTo("[REDACTED]");
    }

    private static Stream<Object> sensitiveValues() {
        return Stream.of(
            new PasswordPayload(),
            new ApiToken(),
            new CredentialEnvelope(),
            new JwtClaims(),
            new com.miniandmore.app.service.criteria.CustomerProfileCriteria(),
            new com.miniandmore.app.service.criteria.CustomerOrderCriteria(),
            new com.miniandmore.app.service.dto.CustomerProfileDTO(),
            new com.miniandmore.app.service.dto.CustomerOrderDTO(),
            new com.miniandmore.app.domain.LedgerlySetting()
        );
    }

    @ParameterizedTest
    @MethodSource("safeValues")
    void sanitizeValue_preservesSafeValues(Object value) {
        Object sanitized = ReflectionTestUtils.invokeMethod(
            LoggingAspect.class,
            "sanitizeValue",
            value
        );
        assertThat(sanitized).isSameAs(value);
    }

    private static Stream<Object> safeValues() {
        return Stream.of("plain", Integer.valueOf(2), new Object());
    }

    private static final class PasswordPayload {}

    private static final class ApiToken {}

    private static final class CredentialEnvelope {}

    private static final class JwtClaims {}
}
