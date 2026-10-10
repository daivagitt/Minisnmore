package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.miniandmore.app.config.ApplicationProperties;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

class S3StorageServiceExceptionMessageMatrixTest {

    private final S3StorageService service = new S3StorageService(new ApplicationProperties());

    @ParameterizedTest
    @CsvSource(
        {
            "AccessDenied,R2 delete denied",
            "Access Denied,R2 delete denied",
            "HTTP 403 from R2,R2 delete denied",
            "timeout,R2 delete failed: timeout",
            "'',R2 delete failed:",
        }
    )
    void storageDeleteException_mapsAccessDeniedAndGeneric(String message, String expected) {
        Exception ex = message.isEmpty() ? new RuntimeException((String) null) : new RuntimeException(message);
        ResponseStatusException mapped = ReflectionTestUtils.invokeMethod(service, "storageDeleteException", ex);
        assertThat(mapped.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(mapped.getReason()).contains(expected);
        assertThat(mapped.getCause()).isSameAs(ex);
    }

    @ParameterizedTest
    @CsvSource(
        {
            "AccessDenied,R2 list denied",
            "Access Denied,R2 list denied",
            "403,R2 list denied",
            "boom,R2 list failed: boom",
            "'',R2 list failed:",
        }
    )
    void storageListException_mapsAccessDeniedAndGeneric(String message, String expected) {
        Exception ex = message.isEmpty() ? new RuntimeException((String) null) : new RuntimeException(message);
        ResponseStatusException mapped = ReflectionTestUtils.invokeMethod(service, "storageListException", ex);
        assertThat(mapped.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(mapped.getReason()).contains(expected);
    }
}
