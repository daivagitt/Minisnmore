package com.miniandmore.integration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
class ZohoItemImageSyncExtendedTest {

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
        props.getStorage().getS3().setMaxUploadBytes(5000L);
        imageSync = new ZohoItemImageSync(new ObjectMapper(), props, zohoBooksClient, s3StorageService);
        ReflectionTestUtils.setField(imageSync, "downloadClient", downloadClient);
    }

    @Test
    void importZohoItemImage_usesDocumentId() throws Exception {
        ZohoSetting setting = new ZohoSetting();
        ObjectNode item = new ObjectMapper().createObjectNode();
        item.put("image_document_id", "doc-1");
        when(zohoBooksClient.downloadDocument(setting, "doc-1", 5000L)).thenReturn(Optional.of(new BinaryDownload(new byte[] { 9 }, "image/png")));
        when(s3StorageService.uploadObject(any(), any(), any(), any())).thenReturn("https://cdn.example.com/z.png");

        assertThat(imageSync.importZohoItemImage(setting, "z-1", item)).isPresent();
    }

    @Test
    void parseImageUrls_invalidJsonReturnsEmpty() {
        assertThat(imageSync.parseImageUrls("not-json")).isEmpty();
    }

    @Test
    void resolvePublicUrl_withoutBaseReturnsNull() {
        ApplicationProperties emptyBase = new ApplicationProperties();
        ZohoItemImageSync sync = new ZohoItemImageSync(new ObjectMapper(), emptyBase, zohoBooksClient, s3StorageService);
        assertThat(sync.resolvePublicUrl("relative/path.jpg")).isNull();
    }

    @Test
    void downloadImage_exceedsMaxSize() throws Exception {
        HttpResponse<InputStream> response = org.mockito.Mockito.mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(new ByteArrayInputStream(new byte[100]));
        when(response.headers()).thenReturn(
            java.net.http.HttpHeaders.of(
                java.util.Map.of("Content-Length", java.util.List.of("10000"), "Content-Type", java.util.List.of("image/jpeg")),
                (a, b) -> true
            )
        );
        when(downloadClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        Product product = new Product();
        product.setId(1L);
        product.setName("Dress");
        product.setImagesJson("[\"https://cdn.example.com/big.jpg\"]");

        imageSync.syncProductImage(new ZohoSetting(), "z-1", product);

        verify(zohoBooksClient, never()).uploadItemImage(any(), any(), any(), any(), any());
    }

    @Test
    void importZohoItemImage_uploadFailureReturnsEmpty() {
        ZohoSetting setting = new ZohoSetting();
        when(zohoBooksClient.downloadItemImage(any(), any(), any(Long.class)))
            .thenReturn(Optional.of(new BinaryDownload(new byte[] { 1 }, "image/png")));
        when(s3StorageService.uploadObject(any(), any(), any(), any())).thenThrow(new RuntimeException("upload failed"));

        assertThat(imageSync.importZohoItemImage(setting, "z-2", new ObjectMapper().createObjectNode())).isEmpty();
    }

    @Test
    void firstPublicImageUrl_returnsFirstResolved() {
        Product product = new Product();
        product.setImagesJson("[\"products/photo.jpg\"]");
        assertThat(imageSync.firstPublicImageUrl(product)).contains("https://cdn.example.com/products/photo.jpg");
    }
}
