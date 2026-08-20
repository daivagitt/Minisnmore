package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.config.ApplicationProperties;
import com.miniandmore.app.domain.Product;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class LedgerlyItemImageSyncTest {

    @Mock
    private S3StorageService s3StorageService;

    private ApplicationProperties applicationProperties;
    private LedgerlyItemImageSync itemImageSync;

    @BeforeEach
    void setUp() {
        applicationProperties = new ApplicationProperties();
        applicationProperties.getStorage().getS3().setPublicBaseUrl("https://cdn.example.com");
        applicationProperties.getStorage().getS3().setMaxUploadBytes(8_192_000L);
        itemImageSync = new LedgerlyItemImageSync(new ObjectMapper(), applicationProperties, s3StorageService);
    }

    @Test
    void importItemImage_whenDisabled_passthroughsLedgerlyUrl() throws Exception {
        applicationProperties.getLedgerly().setSyncProductImages(false);
        Product product = new Product().sku("SKU-1");

        itemImageSync.importItemImage(product, "https://external.example.com/a.jpg");

        verifyNoInteractions(s3StorageService);
        assertThat(new ObjectMapper().readValue(product.getImagesJson(), String[].class))
            .containsExactly("https://external.example.com/a.jpg");
    }

    @Test
    void importItemImage_whenAlreadyOnCdn_skipsUploadAndLinksUrl() throws Exception {
        applicationProperties.getLedgerly().setSyncProductImages(true);
        Product product = new Product().sku("SKU-1");

        itemImageSync.importItemImage(product, "https://cdn.example.com/uat/products/existing.jpg");

        verifyNoInteractions(s3StorageService);
        assertThat(new ObjectMapper().readValue(product.getImagesJson(), String[].class))
            .containsExactly("https://cdn.example.com/uat/products/existing.jpg");
    }

    @Test
    void importItemImage_replacesPrimaryKeepsAdditionalImages() throws Exception {
        applicationProperties.getLedgerly().setSyncProductImages(false);
        Product product = new Product()
            .sku("SKU-1")
            .imagesJson("[\"https://cdn.example.com/front.jpg\",\"https://cdn.example.com/rear.jpg\"]");

        itemImageSync.importItemImage(product, "https://cdn.example.com/new-front.jpg");

        assertThat(new ObjectMapper().readValue(product.getImagesJson(), String[].class))
            .containsExactly(
                "https://cdn.example.com/new-front.jpg",
                "https://cdn.example.com/front.jpg",
                "https://cdn.example.com/rear.jpg"
            );
    }

    @Test
    void importItemImage_repeatedPull_doesNotAppendDuplicatePrimary() throws Exception {
        applicationProperties.getLedgerly().setSyncProductImages(false);
        Product product = new Product().sku("SKU-1").imagesJson("[\"https://external.example.com/old.jpg\"]");

        itemImageSync.importItemImage(product, "https://external.example.com/new.jpg");
        itemImageSync.importItemImage(product, "https://external.example.com/new.jpg");

        assertThat(new ObjectMapper().readValue(product.getImagesJson(), String[].class))
            .containsExactly("https://external.example.com/new.jpg", "https://external.example.com/old.jpg");
    }

    @Test
    void firstImageUrl_returnsFirstEntry() throws Exception {
        Product product = new Product().imagesJson("[\"https://cdn.example.com/a.jpg\",\"https://cdn.example.com/b.jpg\"]");

        assertThat(itemImageSync.firstImageUrl(product.getImagesJson())).isEqualTo("https://cdn.example.com/a.jpg");
    }

    @Test
    void isEnabled_reflectsLedgerlyConfig() {
        applicationProperties.getLedgerly().setSyncProductImages(true);
        assertThat(itemImageSync.isEnabled()).isTrue();

        applicationProperties.getLedgerly().setSyncProductImages(false);
        assertThat(itemImageSync.isEnabled()).isFalse();
    }

    @Test
    void importItemImage_blankSourceUrl_doesNothing() throws Exception {
        Product product = new Product().sku("SKU-1").imagesJson("[\"https://cdn.example.com/existing.jpg\"]");

        itemImageSync.importItemImage(product, " ");
        itemImageSync.importItemImage(product, null);

        verifyNoInteractions(s3StorageService);
        assertThat(new ObjectMapper().readValue(product.getImagesJson(), String[].class))
            .containsExactly("https://cdn.example.com/existing.jpg");
    }

    @Test
    void firstImageUrl_invalidJson_returnsNull() {
        assertThat(itemImageSync.firstImageUrl("not-json")).isNull();
        assertThat(itemImageSync.firstImageUrl("")).isNull();
        assertThat(itemImageSync.firstImageUrl(null)).isNull();
    }

    @Test
    void importItemImage_whenEnabled_downloadsAndUploadsToStore() throws Exception {
        applicationProperties.getLedgerly().setSyncProductImages(true);
        Product product = new Product().id(42L).sku("SKU-1");
        HttpClient mockHttpClient = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<byte[]> mockResponse = mock(HttpResponse.class);
        ReflectionTestUtils.setField(itemImageSync, "httpClient", mockHttpClient);
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockResponse);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn("image-bytes".getBytes());
        when(mockResponse.headers()).thenReturn(
            java.net.http.HttpHeaders.of(Map.of("content-type", List.of("image/jpeg")), (a, b) -> true)
        );
        when(s3StorageService.uploadObject(eq("products"), contains("SKU-1-ledgerly"), any(byte[].class), eq("image/jpeg")))
            .thenReturn("https://cdn.example.com/products/SKU-1-ledgerly.jpg");

        itemImageSync.importItemImage(product, "https://external.example.com/photo.jpg");

        verify(s3StorageService).uploadObject(eq("products"), contains("SKU-1-ledgerly"), any(byte[].class), eq("image/jpeg"));
        assertThat(new ObjectMapper().readValue(product.getImagesJson(), String[].class))
            .containsExactly("https://cdn.example.com/products/SKU-1-ledgerly.jpg");
    }

    @Test
    void importItemImage_whenEnabled_downloadFailure_fallsBackToSourceUrl() throws Exception {
        applicationProperties.getLedgerly().setSyncProductImages(true);
        Product product = new Product().id(42L).sku("SKU-1");
        HttpClient mockHttpClient = mock(HttpClient.class);
        ReflectionTestUtils.setField(itemImageSync, "httpClient", mockHttpClient);
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenThrow(new java.io.IOException("connection reset"));

        itemImageSync.importItemImage(product, "https://external.example.com/photo.jpg");

        verifyNoInteractions(s3StorageService);
        assertThat(new ObjectMapper().readValue(product.getImagesJson(), String[].class))
            .containsExactly("https://external.example.com/photo.jpg");
    }

    @Test
    void importColorImages_whenEnabled_uploadsExternalUrls() throws Exception {
        applicationProperties.getLedgerly().setSyncProductImages(true);
        Product product = new Product().id(7L).sku("SKU-RED");
        HttpClient mockHttpClient = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<byte[]> mockResponse = mock(HttpResponse.class);
        ReflectionTestUtils.setField(itemImageSync, "httpClient", mockHttpClient);
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockResponse);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn("png-bytes".getBytes());
        when(mockResponse.headers()).thenReturn(
            java.net.http.HttpHeaders.of(Map.of("content-type", List.of("image/png")), (a, b) -> true)
        );
        when(s3StorageService.uploadObject(eq("products"), contains("SKU-RED-red-1"), any(byte[].class), eq("image/png")))
            .thenReturn("https://cdn.example.com/products/SKU-RED-red-1.png");

        Map<String, List<String>> result = itemImageSync.importColorImages(
            product,
            Map.of("Red", List.of("https://external.example.com/red.png", "https://cdn.example.com/already.jpg"))
        );

        assertThat(result).containsKey("Red");
        assertThat(result.get("Red"))
            .containsExactly("https://cdn.example.com/products/SKU-RED-red-1.png", "https://cdn.example.com/already.jpg");
        verify(s3StorageService, times(1)).uploadObject(anyString(), anyString(), any(byte[].class), anyString());
    }

    @Test
    void importColorImages_whenDisabled_passthroughsExternalUrls() {
        applicationProperties.getLedgerly().setSyncProductImages(false);
        Product product = new Product().sku("SKU-1");

        Map<String, List<String>> result = itemImageSync.importColorImages(
            product,
            Map.of("Blue", List.of("https://external.example.com/blue.jpg"))
        );

        verifyNoInteractions(s3StorageService);
        assertThat(result.get("Blue")).containsExactly("https://external.example.com/blue.jpg");
    }

    @Test
    void importItemImage_whenAlreadyPrimary_skipsRewrite() throws Exception {
        Product product = new Product().sku("SKU-1").imagesJson("[\"https://external.example.com/same.jpg\"]");

        itemImageSync.importItemImage(product, "https://external.example.com/same.jpg");

        assertThat(new ObjectMapper().readValue(product.getImagesJson(), String[].class))
            .containsExactly("https://external.example.com/same.jpg");
    }

    @Test
    void importItemImage_whenEnabled_httpError_fallsBackToSourceUrl() throws Exception {
        applicationProperties.getLedgerly().setSyncProductImages(true);
        Product product = new Product().id(1L).sku("SKU-1");
        HttpClient mockHttpClient = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<byte[]> mockResponse = mock(HttpResponse.class);
        ReflectionTestUtils.setField(itemImageSync, "httpClient", mockHttpClient);
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockResponse);
        when(mockResponse.statusCode()).thenReturn(404);

        itemImageSync.importItemImage(product, "https://external.example.com/missing.jpg");

        assertThat(new ObjectMapper().readValue(product.getImagesJson(), String[].class))
            .containsExactly("https://external.example.com/missing.jpg");
    }

    @Test
    void importItemImage_whenEnabled_nonImageContentType_fallsBack() throws Exception {
        applicationProperties.getLedgerly().setSyncProductImages(true);
        Product product = new Product().id(2L).sku("SKU-2");
        HttpClient mockHttpClient = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<byte[]> mockResponse = mock(HttpResponse.class);
        ReflectionTestUtils.setField(itemImageSync, "httpClient", mockHttpClient);
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockResponse);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn("not-image".getBytes());
        when(mockResponse.headers()).thenReturn(
            java.net.http.HttpHeaders.of(Map.of("content-type", List.of("text/plain")), (a, b) -> true)
        );

        itemImageSync.importItemImage(product, "https://external.example.com/file.txt");

        assertThat(new ObjectMapper().readValue(product.getImagesJson(), String[].class))
            .containsExactly("https://external.example.com/file.txt");
    }

    @Test
    void importColorImages_emptySource_returnsEmptyMap() {
        assertThat(itemImageSync.importColorImages(new Product(), Map.of())).isEmpty();
        assertThat(itemImageSync.importColorImages(new Product(), null)).isEmpty();
    }

    @Test
    void importColorImages_skipsBlankColorNamesAndUrls() {
        applicationProperties.getLedgerly().setSyncProductImages(false);
        Product product = new Product().sku("SKU-1");

        Map<String, List<String>> result = itemImageSync.importColorImages(
            product,
            Map.of(" ", List.of("https://external.example.com/x.jpg"), "Blue", List.of(" ", ""))
        );

        assertThat(result).isEmpty();
    }

    @Test
    void importColorImages_whenEnabled_colorDownloadFailure_fallsBackToSource() throws Exception {
        applicationProperties.getLedgerly().setSyncProductImages(true);
        Product product = new Product().id(8L).sku("SKU-8");
        HttpClient mockHttpClient = mock(HttpClient.class);
        ReflectionTestUtils.setField(itemImageSync, "httpClient", mockHttpClient);
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenThrow(new java.io.IOException("timeout"));

        Map<String, List<String>> result = itemImageSync.importColorImages(
            product,
            Map.of("Green", List.of("https://external.example.com/green.webp"))
        );

        assertThat(result.get("Green")).containsExactly("https://external.example.com/green.webp");
    }
}
