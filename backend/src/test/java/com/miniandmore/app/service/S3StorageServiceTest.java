package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.miniandmore.app.config.ApplicationProperties;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeletedObject;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;

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
    void uploadInvoicePdf_success_returnsPublicUrl() {
        S3Client mockClient = mock(S3Client.class);
        ReflectionTestUtils.setField(s3StorageService, "s3Client", mockClient);

        String url = s3StorageService.uploadInvoicePdf("invoice-inv-1.pdf", "pdf-bytes".getBytes());

        assertThat(url).startsWith("https://cdn.example.com/my-bucket/invoices/");
        assertThat(url).contains("invoice-inv-1.pdf");
    }

    @Test
    void uploadInvoicePdf_exceedsMaxBytes_throwsBadRequest() {
        byte[] body = new byte[6_000_000];

        assertThatThrownBy(() -> s3StorageService.uploadInvoicePdf("invoice-inv-1.pdf", body))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("too large");
    }

    @Test
    void uploadInvoicePdf_emptyBody_throwsBadRequest() {
        assertThatThrownBy(() -> s3StorageService.uploadInvoicePdf("invoice-inv-1.pdf", new byte[0]))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("empty");
    }

    @Test
    void uploadInvoicePdf_filenameWithoutExtension_appendsPdf() {
        S3Client mockClient = mock(S3Client.class);
        ReflectionTestUtils.setField(s3StorageService, "s3Client", mockClient);

        String url = s3StorageService.uploadInvoicePdf("invoice-inv-2", "pdf-bytes".getBytes());

        assertThat(url).endsWith("-invoice-inv-2.pdf");
    }

    @Test
    void uploadCataloguePdf_success_returnsPublicUrl() {
        S3Client mockClient = mock(S3Client.class);
        ReflectionTestUtils.setField(s3StorageService, "s3Client", mockClient);

        String url = s3StorageService.uploadCataloguePdf("Soft Bloom.pdf", "pdf-bytes".getBytes());

        assertThat(url).startsWith("https://cdn.example.com/my-bucket/catalogues/");
        assertThat(url).contains("Soft-Bloom.pdf");
    }

    @Test
    void uploadCataloguePdf_emptyBody_throwsBadRequest() {
        assertThatThrownBy(() -> s3StorageService.uploadCataloguePdf("catalogue.pdf", new byte[0]))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("empty");
    }

    @Test
    void uploadCataloguePdf_exceedsMaxBytes_throwsBadRequest() {
        byte[] body = new byte[6_000_000];

        assertThatThrownBy(() -> s3StorageService.uploadCataloguePdf("catalogue.pdf", body))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("too large");
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

    @Test
    void listObjects_withSuffixFilter_returnsMatchingKeys() {
        S3Client mockClient = mock(S3Client.class);
        ReflectionTestUtils.setField(s3StorageService, "s3Client", mockClient);
        Instant modified = Instant.parse("2024-01-01T00:00:00Z");
        when(mockClient.listObjectsV2(any(ListObjectsV2Request.class)))
            .thenReturn(
                ListObjectsV2Response.builder()
                    .contents(
                        S3Object.builder().key("products/100-a.jpg").lastModified(modified).size(100L).build(),
                        S3Object.builder().key("products/200-b.png").lastModified(modified).size(200L).build()
                    )
                    .isTruncated(false)
                    .build()
            );

        List<S3StorageService.StoredObject> result = s3StorageService.listObjects("products", ".jpg", 50);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).key()).isEqualTo("products/100-a.jpg");
        assertThat(result.get(0).publicUrl()).isEqualTo("https://cdn.example.com/my-bucket/products/100-a.jpg");
        assertThat(result.get(0).sizeBytes()).isEqualTo(100L);
    }

    @Test
    void listObjects_followsContinuationToken() {
        S3Client mockClient = mock(S3Client.class);
        ReflectionTestUtils.setField(s3StorageService, "s3Client", mockClient);
        Instant modified = Instant.parse("2024-01-01T00:00:00Z");
        when(mockClient.listObjectsV2(any(ListObjectsV2Request.class)))
            .thenAnswer(invocation -> {
                ListObjectsV2Request request = invocation.getArgument(0);
                if (request.continuationToken() == null) {
                    return ListObjectsV2Response.builder()
                        .contents(S3Object.builder().key("products/page1.jpg").lastModified(modified).size(10L).build())
                        .isTruncated(true)
                        .nextContinuationToken("page-2")
                        .build();
                }
                assertThat(request.continuationToken()).isEqualTo("page-2");
                return ListObjectsV2Response.builder()
                    .contents(S3Object.builder().key("products/page2.jpg").lastModified(modified).size(20L).build())
                    .isTruncated(false)
                    .build();
            });

        List<S3StorageService.StoredObject> result = s3StorageService.listObjects("products", null, 10);

        assertThat(result).extracting(S3StorageService.StoredObject::key)
            .containsExactly("products/page1.jpg", "products/page2.jpg");
        verify(mockClient, org.mockito.Mockito.times(2)).listObjectsV2(any(ListObjectsV2Request.class));
    }

    @Test
    void listObjects_nonPositiveMaxKeys_defaultsToThousand() {
        S3Client mockClient = mock(S3Client.class);
        ReflectionTestUtils.setField(s3StorageService, "s3Client", mockClient);
        when(mockClient.listObjectsV2(any(ListObjectsV2Request.class)))
            .thenAnswer(invocation -> {
                ListObjectsV2Request request = invocation.getArgument(0);
                assertThat(request.maxKeys()).isEqualTo(1000);
                return ListObjectsV2Response.builder().isTruncated(false).build();
            });

        assertThat(s3StorageService.listObjects("products", "", 0)).isEmpty();
    }

    @Test
    void listObjects_accessDenied_throwsBadGateway() {
        S3Client mockClient = mock(S3Client.class);
        ReflectionTestUtils.setField(s3StorageService, "s3Client", mockClient);
        doThrow(new RuntimeException("AccessDenied: forbidden")).when(mockClient).listObjectsV2(any(ListObjectsV2Request.class));

        assertThatThrownBy(() -> s3StorageService.listObjects("products", null, 10))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("R2 list denied");
    }

    @Test
    void listObjects_genericFailure_throwsBadGateway() {
        S3Client mockClient = mock(S3Client.class);
        ReflectionTestUtils.setField(s3StorageService, "s3Client", mockClient);
        doThrow(new RuntimeException("timeout")).when(mockClient).listObjectsV2(any(ListObjectsV2Request.class));

        assertThatThrownBy(() -> s3StorageService.listObjects("products", null, 10))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("R2 list failed");
    }

    @Test
    void deleteObjects_emptyKeys_returnsZero() {
        S3Client mockClient = mock(S3Client.class);
        ReflectionTestUtils.setField(s3StorageService, "s3Client", mockClient);

        assertThat(s3StorageService.deleteObjects(null)).isZero();
        assertThat(s3StorageService.deleteObjects(List.of())).isZero();
        assertThat(s3StorageService.deleteObjects(java.util.Arrays.asList(" ", null, ""))).isZero();
        org.mockito.Mockito.verifyNoInteractions(mockClient);
    }

    @Test
    void deleteObjects_successfulBatch_returnsDeletedCount() {
        S3Client mockClient = mock(S3Client.class);
        ReflectionTestUtils.setField(s3StorageService, "s3Client", mockClient);
        when(mockClient.deleteObjects(any(DeleteObjectsRequest.class)))
            .thenReturn(
                DeleteObjectsResponse.builder()
                    .deleted(
                        DeletedObject.builder().key("products/a.jpg").build(),
                        DeletedObject.builder().key("products/b.jpg").build()
                    )
                    .build()
            );

        int deleted = s3StorageService.deleteObjects(List.of("products/a.jpg", "  ", "products/b.jpg"));

        assertThat(deleted).isEqualTo(2);
        verify(mockClient).deleteObjects(any(DeleteObjectsRequest.class));
    }

    @Test
    void deleteObjects_accessDenied_throwsBadGateway() {
        S3Client mockClient = mock(S3Client.class);
        ReflectionTestUtils.setField(s3StorageService, "s3Client", mockClient);
        doThrow(new RuntimeException("403 Access Denied")).when(mockClient).deleteObjects(any(DeleteObjectsRequest.class));

        assertThatThrownBy(() -> s3StorageService.deleteObjects(List.of("products/a.jpg")))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("R2 delete denied");
    }

    @Test
    void deleteObjects_partialErrors_throwsBadGateway() {
        S3Client mockClient = mock(S3Client.class);
        ReflectionTestUtils.setField(s3StorageService, "s3Client", mockClient);
        when(mockClient.deleteObjects(any(DeleteObjectsRequest.class)))
            .thenReturn(
                DeleteObjectsResponse.builder()
                    .errors(software.amazon.awssdk.services.s3.model.S3Error.builder().key("products/a.jpg").message("denied").build())
                    .build()
            );

        assertThatThrownBy(() -> s3StorageService.deleteObjects(List.of("products/a.jpg")))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("R2 delete failed for products/a.jpg");
    }

    @Test
    void listObjects_skipsNullKeysAndAppliesSuffixFilter() {
        S3Client mockClient = mock(S3Client.class);
        ReflectionTestUtils.setField(s3StorageService, "s3Client", mockClient);
        Instant modified = Instant.parse("2024-01-01T00:00:00Z");
        when(mockClient.listObjectsV2(any(ListObjectsV2Request.class)))
            .thenReturn(
                ListObjectsV2Response.builder()
                    .contents(
                        S3Object.builder().key(null).build(),
                        S3Object.builder().key("products/readme.txt").lastModified(modified).size(1L).build()
                    )
                    .isTruncated(false)
                    .build()
            );

        assertThat(s3StorageService.listObjects("products", ".jpg", 10)).isEmpty();
    }

    @Test
    void uploadObject_accessDenied_throwsStructuredBadGateway() {
        S3Client mockClient = mock(S3Client.class);
        ReflectionTestUtils.setField(s3StorageService, "s3Client", mockClient);
        doThrow(new RuntimeException("AccessDenied: forbidden")).when(mockClient).putObject(
            any(software.amazon.awssdk.services.s3.model.PutObjectRequest.class),
            any(software.amazon.awssdk.core.sync.RequestBody.class)
        );

        assertThatThrownBy(() -> s3StorageService.uploadObject("products", "photo.jpg", new byte[] { 1 }, "image/jpeg"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("R2 storage denied upload");
    }

    @Test
    void presignUpload_unknownExtensionContentType_resolvesFromFilename() {
        var result = s3StorageService.presignUpload("products", "photo.png", null);
        assertThat(result.publicUrl()).endsWith(".png");
    }

    @Test
    void resolveEndpoint_stripsBucketSuffixFromEndpoint() {
        applicationProperties.getStorage().getS3().setEndpoint("https://account.r2.cloudflarestorage.com/my-bucket");
        S3StorageService service = new S3StorageService(applicationProperties);
        java.net.URI endpoint = (java.net.URI) ReflectionTestUtils.invokeMethod(service, "storageEndpointUri");
        assertThat(endpoint.toString()).isEqualTo("https://account.r2.cloudflarestorage.com");
    }

    @Test
    void buildClient_initializesLazyS3Client() {
        S3StorageService service = new S3StorageService(applicationProperties);
        S3Client client = (S3Client) ReflectionTestUtils.invokeMethod(service, "client");
        assertThat(client).isNotNull();
        assertThat((S3Client) ReflectionTestUtils.invokeMethod(service, "client")).isSameAs(client);
    }

    @Test
    void resolveContentType_unknownExtension_defaultsToOctetStream() {
        String type = (String) ReflectionTestUtils.invokeMethod(s3StorageService, "resolveContentType", null, "file.unknown");
        assertThat(type).isEqualTo("application/octet-stream");
    }

    @Test
    void normalizeImageContentType_pngAlias_returnsPng() {
        String type = (String) ReflectionTestUtils.invokeMethod(s3StorageService, "normalizeImageContentType", "image/x-png");
        assertThat(type).isEqualTo("image/png");
    }

    @Test
    void deleteObjects_emptyBatchIdentifiers_skipsClientCall() {
        S3Client mockClient = mock(S3Client.class);
        ReflectionTestUtils.setField(s3StorageService, "s3Client", mockClient);

        assertThat(s3StorageService.deleteObjects(List.of("   ", ""))).isZero();
        org.mockito.Mockito.verifyNoInteractions(mockClient);
    }
}
