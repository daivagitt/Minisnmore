package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.config.ApplicationProperties;
import com.miniandmore.app.domain.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
    void firstImageUrl_returnsFirstEntry() throws Exception {
        Product product = new Product().imagesJson("[\"https://cdn.example.com/a.jpg\",\"https://cdn.example.com/b.jpg\"]");

        assertThat(itemImageSync.firstImageUrl(product.getImagesJson())).isEqualTo("https://cdn.example.com/a.jpg");
    }
}
