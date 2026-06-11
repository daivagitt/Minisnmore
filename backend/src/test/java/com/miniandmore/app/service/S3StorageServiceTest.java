package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.miniandmore.app.config.ApplicationProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class S3StorageServiceTest {

    private ApplicationProperties applicationProperties;
    private S3StorageService s3StorageService;

    @BeforeEach
    void setUp() {
        applicationProperties = new ApplicationProperties();
        var s3 = applicationProperties.getStorage().getS3();
        s3.setEndpoint("https://account.r2.cloudflarestorage.com/my-bucket");
        s3.setRegion("auto");
        s3.setBucket("my-bucket");
        s3.setAccessKey("access");
        s3.setSecretKey("secret");
        s3.setPublicBaseUrl("https://cdn.example.com/my-bucket");
        s3.setMaxUploadBytes(5_000_000L);
        s3StorageService = new S3StorageService(applicationProperties);
    }

    @Test
    void presignUpload_invalidFolder_throwsBadRequest() {
        assertThatThrownBy(() -> s3StorageService.presignUpload("invalid", "photo.jpg"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Invalid upload folder");
    }

    @Test
    void presignUpload_blankFilename_throwsBadRequest() {
        assertThatThrownBy(() -> s3StorageService.presignUpload("products", " "))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Filename is required");
    }

    @Test
    void presignUpload_unsupportedExtension_throwsBadRequest() {
        assertThatThrownBy(() -> s3StorageService.presignUpload("products", "file.pdf"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Image type not accepted");
    }

    @Test
    void presignUpload_unconfiguredStorage_throwsServiceUnavailable() {
        S3StorageService unconfigured = new S3StorageService(new ApplicationProperties());

        assertThatThrownBy(() -> unconfigured.presignUpload("products", "photo.jpg"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("S3 storage is not configured");
    }

    @Test
    void uploadObject_exceedsMaxBytes_throwsBadRequest() {
        byte[] body = new byte[6_000_000];

        assertThatThrownBy(() -> s3StorageService.uploadObject("products", "photo.jpg", body, "image/jpeg"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("too large");
    }

    @Test
    void presignUpload_validFolder_returnsPresignedUpload() {
        var result = s3StorageService.presignUpload("products", "photo.jpg", "image/jpeg");

        assertThat(result.uploadUrl()).isNotBlank();
        assertThat(result.publicUrl()).startsWith("https://cdn.example.com/my-bucket/");
        assertThat(result.method()).isEqualTo("PUT");
        assertThat(result.maxUploadBytes()).isEqualTo(5_000_000L);
    }

    @Test
    void presignUpload_missingPublicBaseUrl_throwsServiceUnavailable() {
        applicationProperties.getStorage().getS3().setPublicBaseUrl("");
        S3StorageService service = new S3StorageService(applicationProperties);

        assertThatThrownBy(() -> service.presignUpload("products", "photo.jpg"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Public image base URL");
    }

    @Test
    void presignUpload_withKeyPrefix_includesPrefixInObjectKey() {
        applicationProperties.getStorage().getS3().setKeyPrefix("/uploads/");
        S3StorageService service = new S3StorageService(applicationProperties);

        var result = service.presignUpload("products", "photo.png");

        assertThat(result.objectKey()).startsWith("uploads/");
    }

    @Test
    void presignUpload_pngFilename_usesPngContentType() {
        var result = s3StorageService.presignUpload("products", "photo.png", null);

        assertThat(result.uploadUrl()).isNotBlank();
    }

    @Test
    void presignUpload_webpContentType_normalizesType() {
        var result = s3StorageService.presignUpload("products", "photo.webp", "image/webp");

        assertThat(result.publicUrl()).contains("photo.webp");
    }

    @Test
    void presignUpload_jpegAliasContentType_normalizesToJpeg() {
        var result = s3StorageService.presignUpload("blog", "photo.jpg", "image/pjpeg");

        assertThat(result.method()).isEqualTo("PUT");
    }

    @Test
    void uploadObject_success_returnsPublicUrl() {
        software.amazon.awssdk.services.s3.S3Client mockClient = org.mockito.Mockito.mock(software.amazon.awssdk.services.s3.S3Client.class);
        org.springframework.test.util.ReflectionTestUtils.setField(s3StorageService, "s3Client", mockClient);
        byte[] body = "image-bytes".getBytes();

        String url = s3StorageService.uploadObject("products", "photo.jpg", body, "image/jpeg");

        assertThat(url).startsWith("https://cdn.example.com/my-bucket/");
        assertThat(url).contains("photo.jpg");
    }

    @Test
    void uploadObject_storageFailure_throwsBadGateway() {
        software.amazon.awssdk.services.s3.S3Client mockClient = org.mockito.Mockito.mock(software.amazon.awssdk.services.s3.S3Client.class);
        org.mockito.Mockito.doThrow(new RuntimeException("network")).when(mockClient).putObject(
            org.mockito.ArgumentMatchers.any(software.amazon.awssdk.services.s3.model.PutObjectRequest.class),
            org.mockito.ArgumentMatchers.any(software.amazon.awssdk.core.sync.RequestBody.class)
        );
        org.springframework.test.util.ReflectionTestUtils.setField(s3StorageService, "s3Client", mockClient);

        assertThatThrownBy(() -> s3StorageService.uploadObject("products", "photo.png", new byte[] { 1 }, null))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Upload to storage failed");
    }

    @Test
    void presignUpload_gifWithoutContentType_usesGifMime() {
        var result = s3StorageService.presignUpload("blog", "animation.gif", null);
        assertThat(result.publicUrl()).contains("animation.gif");
    }

    @Test
    void presignUpload_unknownImageSubtype_normalizesGenericImage() {
        var result = s3StorageService.presignUpload("products", "photo.jpg", "image/svg+xml");
        assertThat(result.uploadUrl()).isNotBlank();
    }

    @Test
    void presignUpload_secondCall_reusesCachedPresigner() {
        var first = s3StorageService.presignUpload("products", "a.jpg");
        var second = s3StorageService.presignUpload("products", "b.jpg");
        assertThat(first.uploadUrl()).isNotBlank();
        assertThat(second.uploadUrl()).isNotBlank();
    }

    @Test
    void uploadObject_secondCall_reusesCachedClient() {
        software.amazon.awssdk.services.s3.S3Client mockClient = org.mockito.Mockito.mock(software.amazon.awssdk.services.s3.S3Client.class);
        org.springframework.test.util.ReflectionTestUtils.setField(s3StorageService, "s3Client", mockClient);
        byte[] body = "x".getBytes();
        s3StorageService.uploadObject("products", "a.jpg", body, "image/jpeg");
        s3StorageService.uploadObject("products", "b.jpg", body, "image/jpeg");
        org.mockito.Mockito.verify(mockClient, org.mockito.Mockito.times(2)).putObject(
            org.mockito.ArgumentMatchers.any(software.amazon.awssdk.services.s3.model.PutObjectRequest.class),
            org.mockito.ArgumentMatchers.any(software.amazon.awssdk.core.sync.RequestBody.class)
        );
    }

    @Test
    void presignUpload_imageJpgAliasContentType_normalizesToJpeg() {
        var result = s3StorageService.presignUpload("products", "photo.jpg", "image/jpg");
        assertThat(result.method()).isEqualTo("PUT");
    }

    @Test
    void presignUpload_cloudflarePublicBaseUrl_throwsServiceUnavailable() {
        applicationProperties.getStorage().getS3().setPublicBaseUrl("https://account.r2.cloudflarestorage.com/bucket");
        S3StorageService service = new S3StorageService(applicationProperties);

        assertThatThrownBy(() -> service.presignUpload("products", "photo.jpg"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("public bucket URL");
    }
}
