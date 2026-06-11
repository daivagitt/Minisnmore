package com.miniandmore.integration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.miniandmore.integration.config.ApplicationProperties;
import java.net.URI;
import java.net.URL;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.http.SdkHttpRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

class S3StorageServicePresignTest {

    private ApplicationProperties applicationProperties;
    private S3StorageService service;

    @BeforeEach
    void setUp() {
        applicationProperties = new ApplicationProperties();
        ApplicationProperties.Storage.S3 s3 = applicationProperties.getStorage().getS3();
        s3.setEndpoint("https://example.r2.cloudflarestorage.com/bucket");
        s3.setBucket("bucket");
        s3.setAccessKey("key");
        s3.setSecretKey("secret");
        s3.setRegion("auto");
        s3.setPublicBaseUrl("https://cdn.example.com");
        service = new S3StorageService(applicationProperties);
        ReflectionTestUtils.setField(service, "clock", Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void presignUpload_returnsUploadDetails() throws Exception {
        S3Presigner presigner = mock(S3Presigner.class);
        PresignedPutObjectRequest presigned = mock(PresignedPutObjectRequest.class);
        SdkHttpRequest httpRequest = SdkHttpRequest.builder()
            .uri(URI.create("https://s3.example.com/upload"))
            .method(SdkHttpMethod.PUT)
            .putHeader("Host", "s3.example.com")
            .build();
        when(presigned.url()).thenReturn(new URL("https://s3.example.com/upload"));
        when(presigned.httpRequest()).thenReturn(httpRequest);
        when(presigned.isBrowserExecutable()).thenReturn(false);
        when(presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(presigned);
        ReflectionTestUtils.setField(service, "s3Presigner", presigner);

        S3StorageService.PresignedUpload upload = service.presignUpload("products", "photo.jpg", "image/png");

        assertThat(upload.uploadUrl()).contains("s3.example.com");
        assertThat(upload.publicUrl()).contains("cdn.example.com");
        assertThat(upload.method()).isEqualTo("PUT");
        assertThat(upload.maxUploadBytes()).isPositive();
    }

    @Test
    void uploadObject_success() {
        software.amazon.awssdk.services.s3.S3Client s3Client = mock(software.amazon.awssdk.services.s3.S3Client.class);
        ReflectionTestUtils.setField(service, "s3Client", s3Client);

        String url = service.uploadObject("blog", "post.png", new byte[] { 1, 2, 3 }, "image/png");

        assertThat(url).startsWith("https://cdn.example.com/");
    }
}
