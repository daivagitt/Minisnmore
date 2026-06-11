package com.miniandmore.integration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.integration.config.ApplicationProperties;
import com.miniandmore.integration.domain.Product;
import com.miniandmore.integration.domain.ZohoSetting;
import com.miniandmore.integration.service.ZohoBooksClient.BinaryDownload;
import java.io.ByteArrayInputStream;
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
class ZohoItemImageSyncHttpTest {

    @Mock
    private ZohoBooksClient zohoBooksClient;

    @Mock
    private S3StorageService s3StorageService;

    @Mock
    private HttpClient downloadClient;

    @Mock
    private HttpResponse<InputStream> httpResponse;

    private ZohoItemImageSync imageSync;
    private ApplicationProperties props;

    @BeforeEach
    void setUp() throws Exception {
        props = new ApplicationProperties();
        props.getStorage().getS3().setPublicBaseUrl("https://cdn.example.com");
        props.getStorage().getS3().setMaxUploadBytes(1024L);
        imageSync = new ZohoItemImageSync(new ObjectMapper(), props, zohoBooksClient, s3StorageService);
        ReflectionTestUtils.setField(imageSync, "downloadClient", downloadClient);
    }

    @Test
    void importZohoItemImage_uploadsToS3() throws Exception {
        ZohoSetting setting = new ZohoSetting();
        var item = new ObjectMapper().createObjectNode().put("sku", "SKU1");
        when(zohoBooksClient.downloadItemImage(any(), any(), any(Long.class)))
            .thenReturn(Optional.of(new BinaryDownload(new byte[] { 1, 2, 3 }, "image/png")));
        when(s3StorageService.uploadObject(any(), any(), any(), any())).thenReturn("https://cdn.example.com/p.png");

        assertThat(imageSync.importZohoItemImage(setting, "z-1", item)).isPresent();
    }

    @Test
    void syncProductImage_downloadsAndUploads() throws Exception {
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(new ByteArrayInputStream(new byte[] { 9, 8, 7 }));
        when(httpResponse.headers()).thenReturn(
            java.net.http.HttpHeaders.of(
                java.util.Map.of("Content-Type", java.util.List.of("image/jpeg")),
                (a, b) -> true
            )
        );
        when(downloadClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(httpResponse);
        doNothing().when(zohoBooksClient).uploadItemImage(any(), any(), any(), any(), any());

        Product product = new Product();
        product.setId(1L);
        product.setName("Dress");
        product.setImagesJson("[\"https://cdn.example.com/dress.jpg\"]");

        imageSync.syncProductImage(new ZohoSetting(), "z-1", product);
    }
}
