package com.miniandmore.integration.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.miniandmore.integration.config.ApplicationProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

class S3StorageServiceTest {

    private ApplicationProperties applicationProperties;
    private S3StorageService service;

    @BeforeEach
    void setUp() {
        applicationProperties = new ApplicationProperties();
        service = new S3StorageService(applicationProperties);
        ReflectionTestUtils.setField(service, "clock", Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void presignUpload_notConfigured() {
        assertThatThrownBy(() -> service.presignUpload("products", "photo.jpg"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("not configured");
    }

    @Test
    void presignUpload_invalidFolder() {
        configureS3();
        applicationProperties.getStorage().getS3().setPublicBaseUrl("https://cdn.example.com");
        assertThatThrownBy(() -> service.presignUpload("invalid", "photo.jpg"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Invalid upload folder");
    }

    @Test
    void presignUpload_missingFilename() {
        configureS3();
        applicationProperties.getStorage().getS3().setPublicBaseUrl("https://cdn.example.com");
        assertThatThrownBy(() -> service.presignUpload("products", " "))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Filename is required");
    }

    @Test
    void presignUpload_invalidExtension() {
        configureS3();
        applicationProperties.getStorage().getS3().setPublicBaseUrl("https://cdn.example.com");
        assertThatThrownBy(() -> service.presignUpload("products", "file.exe"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Image type not accepted");
    }

    @Test
    void uploadObject_fileTooLarge() {
        configureS3();
        applicationProperties.getStorage().getS3().setPublicBaseUrl("https://cdn.example.com");
        applicationProperties.getStorage().getS3().setMaxUploadBytes(10L);
        assertThatThrownBy(() -> service.uploadObject("products", "a.jpg", new byte[20], "image/jpeg"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("too large");
    }

    @Test
    void presignUpload_missingPublicBaseUrl() {
        configureS3();
        assertThatThrownBy(() -> service.presignUpload("products", "a.jpg"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Public image base URL");
    }

    @Test
    void presignUpload_rejectsApiEndpointAsPublicUrl() {
        configureS3();
        applicationProperties.getStorage().getS3().setPublicBaseUrl("https://acct.r2.cloudflarestorage.com/bucket");
        assertThatThrownBy(() -> service.presignUpload("products", "a.jpg"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("public bucket URL");
    }

    private void configureS3() {
        ApplicationProperties.Storage.S3 s3 = applicationProperties.getStorage().getS3();
        s3.setEndpoint("https://example.r2.cloudflarestorage.com/my-bucket");
        s3.setBucket("my-bucket");
        s3.setAccessKey("key");
        s3.setSecretKey("secret");
        s3.setRegion("auto");
    }
}
