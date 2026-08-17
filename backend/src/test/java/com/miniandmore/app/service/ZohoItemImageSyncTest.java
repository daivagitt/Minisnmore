package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.miniandmore.app.client.ZohoBooksClient;
import com.miniandmore.app.client.ZohoBooksClient.BinaryDownload;
import com.miniandmore.app.config.ApplicationProperties;
import com.miniandmore.app.domain.Product;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ZohoItemImageSyncTest {

    @Mock
    private ZohoBooksClient booksClient;

    @Mock
    private ApplicationProperties applicationProperties;

    @Mock
    private S3StorageService s3StorageService;

    private ZohoItemImageSync itemImageSync;

    @BeforeEach
    void setUp() {
        itemImageSync = new ZohoItemImageSync(new com.fasterxml.jackson.databind.ObjectMapper(), applicationProperties, booksClient, s3StorageService);
        ApplicationProperties.Zoho zoho = new ApplicationProperties.Zoho();
        zoho.setSyncProductImages(true);
        when(applicationProperties.getZoho()).thenReturn(zoho);
        ApplicationProperties.Storage storage = new ApplicationProperties.Storage();
        storage.getS3().setPublicBaseUrl("https://cdn.example.com");
        storage.getS3().setMaxUploadBytes(8_192_000L);
        when(applicationProperties.getStorage()).thenReturn(storage);
    }

    @Test
    void importZohoItemImage_downloadsBinaryAndStoresOnProduct() {
        Product product = new Product().id(1L).sku("SKU-1");
        when(booksClient.downloadItemImage(eq("zoho-1"), any(Long.class)))
            .thenReturn(Optional.of(new BinaryDownload("bytes".getBytes(), "image/jpeg")));
        when(s3StorageService.uploadObject(eq("products"), any(), any(), eq("image/jpeg")))
            .thenReturn("https://cdn.example.com/uat/products/zoho-SKU-1.jpg");

        itemImageSync.importZohoItemImage(product, "zoho-1", Map.of("sku", "SKU-1"));

        assertThat(product.getImagesJson()).contains("https://cdn.example.com/uat/products/zoho-SKU-1.jpg");
    }

    @Test
    void importZohoItemImage_usesHttpUrlFromZohoItem() {
        Product product = new Product().id(2L).sku("SKU-2");
        itemImageSync.importZohoItemImage(
            product,
            "zoho-2",
            Map.of("sku", "SKU-2", "image_url", "https://cdn.example.com/uat/products/existing.jpg")
        );
        assertThat(product.getImagesJson()).contains("https://cdn.example.com/uat/products/existing.jpg");
    }
}
