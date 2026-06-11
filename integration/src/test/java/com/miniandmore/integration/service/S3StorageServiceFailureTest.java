package com.miniandmore.integration.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import com.miniandmore.integration.config.ApplicationProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

class S3StorageServiceFailureTest {

    private S3StorageService service;

    @BeforeEach
    void setUp() {
        ApplicationProperties props = new ApplicationProperties();
        ApplicationProperties.Storage.S3 s3 = props.getStorage().getS3();
        s3.setEndpoint("https://example.r2.cloudflarestorage.com/bucket");
        s3.setBucket("bucket");
        s3.setAccessKey("key");
        s3.setSecretKey("secret");
        s3.setRegion("auto");
        s3.setPublicBaseUrl("https://cdn.example.com");
        service = new S3StorageService(props);
        ReflectionTestUtils.setField(service, "clock", Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void uploadObject_storageFailureMapsTo502() {
        S3Client s3Client = org.mockito.Mockito.mock(S3Client.class);
        doThrow(new RuntimeException("network")).when(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        ReflectionTestUtils.setField(service, "s3Client", s3Client);

        assertThatThrownBy(() -> service.uploadObject("products", "a.jpg", new byte[] { 1 }, "image/jpeg"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Upload to storage failed");
    }
}
