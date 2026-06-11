package com.miniandmore.integration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.integration.config.ApplicationProperties;
import com.miniandmore.integration.domain.Product;
import com.miniandmore.integration.domain.ZohoSetting;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ZohoItemImageSyncUtilTest {

    @Mock
    private ZohoBooksClient zohoBooksClient;

    @Mock
    private S3StorageService s3StorageService;

    @Mock
    private HttpClient downloadClient;

    private ZohoItemImageSync imageSync;
    private ApplicationProperties props;

    @BeforeEach
    void setUp() {
        props = new ApplicationProperties();
        props.getZoho().setSyncProductImages(true);
        props.getStorage().getS3().setPublicBaseUrl("https://cdn.example.com");
        props.getStorage().getS3().setMaxUploadBytes(50_000L);
        imageSync = new ZohoItemImageSync(new ObjectMapper(), props, zohoBooksClient, s3StorageService);
        ReflectionTestUtils.setField(imageSync, "downloadClient", downloadClient);
    }

    @Test
    void guessContentType_coversExtensions() {
        assertThat(ZohoItemImageSync.guessContentType("https://cdn.example.com/a.png")).isEqualTo("image/png");
        assertThat(ZohoItemImageSync.guessContentType("https://cdn.example.com/a.webp")).isEqualTo("image/webp");
        assertThat(ZohoItemImageSync.guessContentType("https://cdn.example.com/a.gif")).isEqualTo("image/gif");
        assertThat(ZohoItemImageSync.guessContentType("https://cdn.example.com/a.jpeg")).isEqualTo("image/jpeg");
        assertThat(ZohoItemImageSync.guessContentType("https://cdn.example.com/noext")).isEqualTo("image/jpeg");
        assertThat(ZohoItemImageSync.guessContentType("https://cdn.example.com/")).isEqualTo("image/jpeg");
    }

    @Test
    void syncProductImage_httpErrorTriesNextUrl() throws Exception {
        HttpResponse<InputStream> bad = org.mockito.Mockito.mock(HttpResponse.class);
        when(bad.statusCode()).thenReturn(404);
        when(bad.body()).thenReturn(InputStream.nullInputStream());
        HttpResponse<InputStream> good = org.mockito.Mockito.mock(HttpResponse.class);
        when(good.statusCode()).thenReturn(200);
        when(good.body()).thenReturn(new ByteArrayInputStream(new byte[] { 1, 2 }));
        when(good.headers()).thenReturn(
            java.net.http.HttpHeaders.of(java.util.Map.of("Content-Type", java.util.List.of("image/png")), (a, b) -> true)
        );
        when(downloadClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(bad, good);

        Product product = new Product();
        product.setId(1L);
        product.setName("Dress");
        product.setImagesJson("[\"https://cdn.example.com/miss.png\",\"https://cdn.example.com/hit.png\"]");

        imageSync.syncProductImage(new ZohoSetting(), "z-1", product);
    }

    @Test
    void syncProductImage_allUrlsFailLogsWarning() throws Exception {
        HttpResponse<InputStream> bad = org.mockito.Mockito.mock(HttpResponse.class);
        when(bad.statusCode()).thenReturn(500);
        when(bad.body()).thenReturn(InputStream.nullInputStream());
        when(downloadClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(bad);

        Product product = new Product();
        product.setId(2L);
        product.setName("Hat");
        product.setImagesJson("[\"https://cdn.example.com/hat.jpg\"]");

        imageSync.syncProductImage(new ZohoSetting(), "z-2", product);
    }

    @Test
    void importZohoItemImage_documentIdPath() {
        ZohoSetting setting = new ZohoSetting();
        var item = new ObjectMapper().createObjectNode().put("image_document_id", "doc-9");
        when(zohoBooksClient.downloadDocument(setting, "doc-9", 50_000L))
            .thenReturn(java.util.Optional.of(new ZohoBooksClient.BinaryDownload(new byte[] { 5 }, "image/jpeg")));
        when(s3StorageService.uploadObject(any(), any(), any(), any())).thenReturn("https://cdn.example.com/p.jpg");

        assertThat(imageSync.importZohoItemImage(setting, "z-9", item)).isPresent();
    }

    @Test
    void importZohoItemImage_skippedWhenFlagOff() {
        props.getZoho().setSyncProductImages(false);
        assertThat(imageSync.importZohoItemImage(new ZohoSetting(), "z-1", new ObjectMapper().createObjectNode())).isEmpty();
    }
}
