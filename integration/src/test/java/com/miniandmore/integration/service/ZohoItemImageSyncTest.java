package com.miniandmore.integration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.miniandmore.integration.config.ApplicationProperties;
import com.miniandmore.integration.domain.Product;
import com.miniandmore.integration.domain.ZohoSetting;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ZohoItemImageSyncTest {

    @Mock
    private ZohoBooksClient zohoBooksClient;

    @Mock
    private S3StorageService s3StorageService;

    private ApplicationProperties applicationProperties;
    private ZohoItemImageSync imageSync;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        applicationProperties = new ApplicationProperties();
        imageSync = new ZohoItemImageSync(objectMapper, applicationProperties, zohoBooksClient, s3StorageService);
        applicationProperties.getStorage().getS3().setPublicBaseUrl("https://cdn.example.com");
    }

    @Test
    void parseImageUrls_handlesArrayAndRelativePaths() throws Exception {
        assertThat(imageSync.parseImageUrls("[\"https://cdn.example.com/a.jpg\"]")).containsExactly("https://cdn.example.com/a.jpg");
        assertThat(imageSync.parseImageUrls("[\"/relative/path.jpg\"]")).containsExactly("https://cdn.example.com/relative/path.jpg");
        assertThat(imageSync.parseImageUrls("not-json")).isEmpty();
        assertThat(imageSync.parseImageUrls("{\"x\":1}")).isEmpty();
    }

    @Test
    void resolvePublicUrl() {
        assertThat(imageSync.resolvePublicUrl("https://x.com/a.jpg")).isEqualTo("https://x.com/a.jpg");
        assertThat(imageSync.resolvePublicUrl("/img.jpg")).isEqualTo("https://cdn.example.com/img.jpg");
        assertThat(imageSync.resolvePublicUrl("img.jpg")).isEqualTo("https://cdn.example.com/img.jpg");
    }

    @Test
    void guessContentType_fromExtension() {
        assertThat(ZohoItemImageSync.guessContentType("https://x.com/a.png")).isEqualTo("image/png");
        assertThat(ZohoItemImageSync.guessContentType("https://x.com/a")).isEqualTo("image/jpeg");
    }

    @Test
    void importZohoItemImage_skipsWhenDisabled() {
        applicationProperties.getZoho().setSyncProductImages(false);
        ZohoSetting setting = new ZohoSetting();
        assertThat(imageSync.importZohoItemImage(setting, "z-1", objectMapper.createObjectNode())).isEmpty();
        verify(zohoBooksClient, never()).downloadItemImage(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void syncProductImage_skipsWithoutUrls() {
        Product product = new Product();
        product.setId(1L);
        product.setName("Hat");
        imageSync.syncProductImage(new ZohoSetting(), "z-1", product);
        verify(zohoBooksClient, never()).uploadItemImage(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void firstPublicImageUrl_emptyWhenMissing() {
        Product product = new Product();
        assertThat(imageSync.firstPublicImageUrl(product)).isEmpty();
    }

    @Test
    void collectPublicImageUrls_viaImportPath() throws Exception {
        applicationProperties.getZoho().setSyncProductImages(true);
        ObjectNode item = objectMapper.createObjectNode();
        item.put("image_url", "https://cdn.example.com/public.jpg");
        ZohoSetting setting = new ZohoSetting();
        assertThat(imageSync.importZohoItemImage(setting, "", item)).isEmpty();
    }
}
