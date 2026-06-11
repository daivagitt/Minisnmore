package com.miniandmore.integration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.miniandmore.integration.config.ApplicationProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

class S3StorageServiceExtendedTest {

    private ApplicationProperties applicationProperties;
    private S3StorageService service;

    @BeforeEach
    void setUp() {
        applicationProperties = new ApplicationProperties();
        configureS3();
        service = new S3StorageService(applicationProperties);
        ReflectionTestUtils.setField(service, "clock", Clock.fixed(Instant.parse("2026-06-01T12:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void presignUpload_withKeyPrefix() throws Exception {
        applicationProperties.getStorage().getS3().setKeyPrefix("/media/");
        software.amazon.awssdk.services.s3.presigner.S3Presigner presigner = org.mockito.Mockito.mock(
            software.amazon.awssdk.services.s3.presigner.S3Presigner.class
        );
        var presigned = org.mockito.Mockito.mock(software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest.class);
        org.mockito.Mockito.when(presigned.url()).thenReturn(new java.net.URL("https://s3.example.com/u"));
        org.mockito.Mockito.when(presigned.httpRequest())
            .thenReturn(
                software.amazon.awssdk.http.SdkHttpRequest.builder()
                    .uri(java.net.URI.create("https://s3.example.com/u"))
                    .method(software.amazon.awssdk.http.SdkHttpMethod.PUT)
                    .putHeader("X-Amz-Date", "t")
                    .build()
            );
        org.mockito.Mockito.when(
            presigner.presignPutObject(org.mockito.ArgumentMatchers.any(software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest.class))
        )
            .thenReturn(presigned);
        ReflectionTestUtils.setField(service, "s3Presigner", presigner);

        S3StorageService.PresignedUpload upload = service.presignUpload("products", "photo.jpg");
        assertThat(upload.objectKey()).startsWith("media/");
    }

    @Test
    void presignUpload_resolvesContentTypeFromExtension() throws Exception {
        software.amazon.awssdk.services.s3.presigner.S3Presigner presigner = org.mockito.Mockito.mock(
            software.amazon.awssdk.services.s3.presigner.S3Presigner.class
        );
        var presigned = org.mockito.Mockito.mock(software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest.class);
        org.mockito.Mockito.when(presigned.url()).thenReturn(new java.net.URL("https://s3.example.com/u"));
        org.mockito.Mockito.when(presigned.httpRequest())
            .thenReturn(
                software.amazon.awssdk.http.SdkHttpRequest.builder()
                    .uri(java.net.URI.create("https://s3.example.com/u"))
                    .method(software.amazon.awssdk.http.SdkHttpMethod.PUT)
                    .putHeader("X-Amz-Date", "t")
                    .build()
            );
        org.mockito.Mockito.when(
            presigner.presignPutObject(org.mockito.ArgumentMatchers.any(software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest.class))
        )
            .thenReturn(presigned);
        ReflectionTestUtils.setField(service, "s3Presigner", presigner);

        S3StorageService.PresignedUpload upload = service.presignUpload("products", "photo.webp", null);
        assertThat(upload.objectKey()).contains("products/");
    }

    private void configureS3() {
        ApplicationProperties.Storage.S3 s3 = applicationProperties.getStorage().getS3();
        s3.setEndpoint("https://example.r2.cloudflarestorage.com/bucket");
        s3.setBucket("bucket");
        s3.setAccessKey("key");
        s3.setSecretKey("secret");
        s3.setRegion("auto");
        s3.setPublicBaseUrl("https://cdn.example.com");
    }
}
