package com.miniandmore.integration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.miniandmore.integration.config.ApplicationProperties;
import com.miniandmore.integration.domain.Product;
import com.miniandmore.integration.domain.ZohoSetting;
import com.miniandmore.integration.service.ZohoBooksClient.BinaryDownload;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;
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
class ZohoItemImageSyncDownloadPathsTest {

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
        props.getStorage().getS3().setMaxUploadBytes(200L);
        imageSync = new ZohoItemImageSync(new ObjectMapper(), props, zohoBooksClient, s3StorageService);
        ReflectionTestUtils.setField(imageSync, "downloadClient", downloadClient);
    }

    @Test
    void importZohoItemImage_warnsWhenNoBytes() {
        ZohoSetting setting = new ZohoSetting();
        ObjectNode item = new ObjectMapper().createObjectNode().put("sku", "SKU-1");
        when(zohoBooksClient.downloadDocument(any(), anyString(), anyLong())).thenReturn(Optional.empty());
        when(zohoBooksClient.downloadItemImage(any(), anyString(), anyLong())).thenReturn(Optional.empty());

        assertThat(imageSync.importZohoItemImage(setting, "z-1", item)).isEmpty();
    }

    @Test
    void importZohoItemImage_handlesUploadFailure() {
        ZohoSetting setting = new ZohoSetting();
        ObjectNode item = new ObjectMapper().createObjectNode();
        when(zohoBooksClient.downloadItemImage(any(), anyString(), anyLong())).thenReturn(Optional.of(new BinaryDownload(new byte[] { 1 }, "image/png")));
        when(s3StorageService.uploadObject(any(), any(), any(), any())).thenThrow(new RuntimeException("upload failed"));

        assertThat(imageSync.importZohoItemImage(setting, "z-2", item)).isEmpty();
    }

    @Test
    void importZohoItemImage_nullItemSkipsDocumentDownload() {
        assertThat(imageSync.importZohoItemImage(new ZohoSetting(), "z-3", null)).isEmpty();
    }

    @Test
    void syncProductImage_disabledOrBlankItemId() {
        props.getZoho().setSyncProductImages(false);
        imageSync.syncProductImage(new ZohoSetting(), "z-4", new Product());
        imageSync.syncProductImage(new ZohoSetting(), "", new Product());
    }

    @Test
    void resolvePublicUrl_blankReturnsNull() {
        assertThat(imageSync.resolvePublicUrl("   ")).isNull();
    }

    @Test
    void filenameForZohoImport_gifAndWebp() throws Exception {
        ObjectNode item = new ObjectMapper().createObjectNode().put("sku", "SKU-G");
        when(zohoBooksClient.downloadItemImage(any(), anyString(), anyLong())).thenReturn(Optional.of(new BinaryDownload(new byte[] { 2 }, "image/gif")));
        when(s3StorageService.uploadObject(any(), any(), any(), any())).thenReturn("https://cdn.example.com/z.gif");

        assertThat(imageSync.importZohoItemImage(new ZohoSetting(), "z-g", item)).isPresent();

        when(zohoBooksClient.downloadItemImage(any(), anyString(), anyLong())).thenReturn(Optional.of(new BinaryDownload(new byte[] { 3 }, "image/webp")));
        when(s3StorageService.uploadObject(any(), any(), any(), any())).thenReturn("https://cdn.example.com/z.webp");
        assertThat(imageSync.importZohoItemImage(new ZohoSetting(), "z-w", item)).isPresent();
    }

    @Test
    void syncProductImage_emptyBodyAndInterrupted() throws Exception {
        HttpResponse<InputStream> empty = mockResponse(200, new byte[0], "image/jpeg", null);
        when(downloadClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(empty);

        Product product = productWithUrl("https://cdn.example.com/empty.jpg");
        imageSync.syncProductImage(new ZohoSetting(), "z-5", product);

        when(downloadClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenThrow(new InterruptedException("stop"));
        imageSync.syncProductImage(new ZohoSetting(), "z-5", product);
    }

    @Test
    void syncProductImage_exceedsMaxWhileReading() throws Exception {
        byte[] chunk = new byte[150];
        HttpResponse<InputStream> response = mockResponse(200, chunk, "image/jpeg", null);
        when(downloadClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        Product product = productWithUrl("https://cdn.example.com/big.bin");
        imageSync.syncProductImage(new ZohoSetting(), "z-6", product);
    }

    @Test
    void syncProductImage_downloadIOException() throws Exception {
        when(downloadClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenThrow(new IOException("net"));
        imageSync.syncProductImage(new ZohoSetting(), "z-9", productWithUrl("https://cdn.example.com/x.jpg"));
    }

    @Test
    void syncProductImage_usesFilenameFromUrlAndGuessContentType() throws Exception {
        HttpResponse<InputStream> response = mockResponse(200, new byte[] { 7 }, "application/octet-stream", null);
        when(downloadClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        Product product = productWithUrl("https://cdn.example.com/photos/item.webp");
        imageSync.syncProductImage(new ZohoSetting(), "z-7", product);

        assertThat(ZohoItemImageSync.guessContentType("https://cdn.example.com/x.png")).isEqualTo("image/png");
        assertThat(ZohoItemImageSync.guessContentType("https://cdn.example.com/x.unknown")).isEqualTo("image/jpeg");
        assertThat(ZohoItemImageSync.guessContentType("https://cdn.example.com/")).isEqualTo("image/jpeg");
        assertThat(ZohoItemImageSync.guessContentType("https://cdn.example.com/x.gif")).isEqualTo("image/gif");
        assertThat(ZohoItemImageSync.guessContentType("https://cdn.example.com/x.webp")).isEqualTo("image/webp");
    }

    @Test
    void syncProductImage_contentLengthHeaderAllowsDownload() throws Exception {
        HttpResponse<InputStream> response = mockResponse(200, new byte[] { 1, 2 }, "image/png", "50");
        when(downloadClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        org.mockito.Mockito.doNothing().when(zohoBooksClient).uploadItemImage(any(), any(), any(), any(), any());

        Product product = productWithUrl("https://cdn.example.com/small.png");
        imageSync.syncProductImage(new ZohoSetting(), "z-8", product);
    }

    private static Product productWithUrl(String url) {
        Product product = new Product();
        product.setId(1L);
        product.setName("Dress");
        product.setImagesJson("[\"" + url + "\"]");
        return product;
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse<InputStream> mockResponse(int status, byte[] body, String contentType, String contentLength)
        throws Exception {
        HttpResponse<InputStream> response = org.mockito.Mockito.mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(new ByteArrayInputStream(body));
        java.util.Map<String, java.util.List<String>> headers = new java.util.HashMap<>();
        headers.put("Content-Type", java.util.List.of(contentType));
        if (contentLength != null) {
            headers.put("Content-Length", java.util.List.of(contentLength));
        }
        when(response.headers()).thenReturn(java.net.http.HttpHeaders.of(headers, (a, b) -> true));
        return response;
    }
}
