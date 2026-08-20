package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.miniandmore.app.client.ZohoBooksClient;
import com.miniandmore.app.client.ZohoBooksClient.BinaryDownload;
import com.miniandmore.app.config.ApplicationProperties;
import com.miniandmore.app.domain.Product;
import java.util.Map;
import java.util.Optional;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
        itemImageSync = new ZohoItemImageSync(
            new com.fasterxml.jackson.databind.ObjectMapper(),
            applicationProperties,
            booksClient,
            s3StorageService,
            new ProductVariantSupport(new com.fasterxml.jackson.databind.ObjectMapper())
        );
        ApplicationProperties.Zoho zoho = new ApplicationProperties.Zoho();
        zoho.setSyncProductImages(true);
        lenient().when(applicationProperties.getZoho()).thenReturn(zoho);
        ApplicationProperties.Storage storage = new ApplicationProperties.Storage();
        storage.getS3().setPublicBaseUrl("https://cdn.example.com");
        storage.getS3().setMaxUploadBytes(8_192_000L);
        lenient().when(applicationProperties.getStorage()).thenReturn(storage);
    }

    @Test
    void importZohoItemImage_downloadsBinaryAndStoresOnProduct() {
        Product product = new Product().id(1L).sku("SKU-1");
        when(booksClient.downloadItemImage(eq("zoho-1"), any(Long.class)))
            .thenReturn(Optional.of(new BinaryDownload("bytes".getBytes(), "image/jpeg")));
        when(s3StorageService.uploadObject(eq("products"), any(), any(), eq("image/jpeg")))
            .thenReturn("https://cdn.example.com/uat/products/zoho-SKU-1.jpg");

        itemImageSync.importZohoItemImage(product, "zoho-1", Map.of("sku", "SKU-1", "image_name", "item.jpg"));

        assertThat(product.getImagesJson()).contains("https://cdn.example.com/uat/products/zoho-SKU-1.jpg");
    }

    @Test
    void importZohoItemImage_usesHttpUrlFromZohoItem() {
        Product product = new Product().id(2L).sku("SKU-2");
        itemImageSync.importZohoItemImage(
            product,
            "zoho-2",
            Map.of("sku", "SKU-2", "image_name", "photo.jpg", "image_url", "https://cdn.example.com/uat/products/existing.jpg")
        );
        assertThat(product.getImagesJson()).contains("https://cdn.example.com/uat/products/existing.jpg");
    }

    @Test
    void importZohoItemImage_keepsRearImageWhenUpdatingPrimary() {
        Product product = new Product()
            .id(3L)
            .sku("SKU-3")
            .imagesJson("[\"https://cdn.example.com/front.jpg\",\"https://cdn.example.com/rear.jpg\"]");
        itemImageSync.importZohoItemImage(
            product,
            "zoho-3",
            Map.of("sku", "SKU-3", "image_name", "photo.jpg", "image_url", "https://cdn.example.com/uat/products/existing.jpg")
        );
        assertThat(product.getImagesJson()).contains("https://cdn.example.com/uat/products/existing.jpg");
        assertThat(product.getImagesJson()).contains("https://cdn.example.com/rear.jpg");
        assertThat(product.getImagesJson()).contains("https://cdn.example.com/front.jpg");
    }

    @Test
    void importZohoVariantColorImage_storesDistinctZohoPhotosPerSameColorSize() {
        Product product = new Product()
            .id(5L)
            .sku("KEDAR")
            .itemStructure("VARIANTS")
            .variantsJson(
                """
                [
                  {"sku":"KEDAR-03-PINK","size":"0-3M","color":"Pink","rate":100,"stock":2,"active":true},
                  {"sku":"KEDAR-36-PINK","size":"3-6M","color":"Pink","rate":100,"stock":2,"active":true}
                ]
                """
            );
        itemImageSync.importZohoVariantColorImage(
            product,
            "Pink",
            "zoho-03",
            Map.of("sku", "KEDAR-03-PINK", "image_name", "photo.jpg", "image_url", "https://cdn.example.com/uat/products/pink-03.jpg")
        );
        itemImageSync.importZohoVariantColorImage(
            product,
            "Pink",
            "zoho-36",
            Map.of("sku", "KEDAR-36-PINK", "image_name", "photo.jpg", "image_url", "https://cdn.example.com/uat/products/pink-36.jpg")
        );

        assertThat(product.getVariantsJson()).contains("pink-03.jpg");
        assertThat(product.getVariantsJson()).contains("pink-36.jpg");
        assertThat(product.getVariantsJson()).contains("imageUrl");
    }

    @Test
    void resolveVariantImageUrls_prefersSkuPhotoOverParentGallery() {
        Product product = new Product()
            .id(6L)
            .sku("KEDAR")
            .itemStructure("VARIANTS")
            .imagesJson("[\"https://cdn.example.com/parent-mixed.jpg\"]")
            .variantsJson(
                """
                [
                  {"sku":"KEDAR-03-PINK","size":"0-3M","color":"Pink","rate":100,"stock":2,"active":true,"imageUrl":"https://cdn.example.com/pink-03.jpg"},
                  {"sku":"KEDAR-36-PINK","size":"3-6M","color":"Pink","rate":100,"stock":2,"active":true,"imageUrl":"https://cdn.example.com/pink-36.jpg"}
                ]
                """
            );

        assertThat(itemImageSync.resolveVariantImageUrls(product, "KEDAR-03-PINK"))
            .containsExactly("https://cdn.example.com/pink-03.jpg");
        assertThat(itemImageSync.resolveVariantImageUrls(product, "KEDAR-36-PINK"))
            .containsExactly("https://cdn.example.com/pink-36.jpg");
        assertThat(itemImageSync.resolveVariantImageUrls(product, "KEDAR-03-PINK"))
            .doesNotContain("https://cdn.example.com/parent-mixed.jpg");
    }

    @Test
    void resolveVariantImageUrls_fallsBackToColourGalleryWhenSkuHasNoOwnPhoto() {
        Product product = new Product()
            .id(19L)
            .sku("BASIC-SHORTS")
            .itemStructure("VARIANTS")
            .colorImagesJson(
                "{\"Snail Blue\":[\"https://cdn.example.com/snail-blue-1.png\",\"https://cdn.example.com/snail-blue-2.png\"]}"
            )
            .variantsJson(
                """
                [
                  {"sku":"BASIC-SHORTS-03-SNAIL-BLUE","size":"0-3","color":"Snail Blue","rate":100,"stock":2,"active":true},
                  {"sku":"BASIC-SHORTS-36-SNAIL-BLUE","size":"3-6","color":"Snail Blue","rate":100,"stock":2,"active":true}
                ]
                """
            );

        assertThat(itemImageSync.resolveVariantImageUrls(product, "BASIC-SHORTS-03-SNAIL-BLUE"))
            .containsExactly("https://cdn.example.com/snail-blue-1.png", "https://cdn.example.com/snail-blue-2.png");
        assertThat(itemImageSync.resolveVariantImageUrls(product, "BASIC-SHORTS-36-SNAIL-BLUE"))
            .containsExactly("https://cdn.example.com/snail-blue-1.png", "https://cdn.example.com/snail-blue-2.png");
    }

    @Test
    void resolveVariantImageUrls_emptySkuDoesNotPushSiblingPhotosToZoho() {
        Product product = new Product()
            .id(17L)
            .sku("KEDAR")
            .itemStructure("VARIANTS")
            .variantsJson(
                """
                [
                  {"sku":"KED-RED-0-3","size":"0-3","color":"red","rate":100,"stock":0,"active":true},
                  {"sku":"KED-RED-3-6","size":"3-6","color":"red","rate":100,"stock":0,"active":true,"imageUrl":"https://cdn.example.com/red-36.jpg"},
                  {"sku":"KED-RED-6-12","size":"6-12","color":"red","rate":100,"stock":0,"active":true}
                ]
                """
            );

        assertThat(itemImageSync.resolveVariantImageUrls(product, "KED-RED-0-3")).isEmpty();
        assertThat(itemImageSync.resolveVariantImageUrls(product, "KED-RED-6-12")).isEmpty();
        assertThat(itemImageSync.resolveVariantImageUrls(product, "KED-RED-3-6"))
            .containsExactly("https://cdn.example.com/red-36.jpg");
    }

    @Test
    void resolveVariantImageUrls_doesNotMixDifferentSameColourPhotos() {
        Product product = new Product()
            .id(18L)
            .sku("KEDAR")
            .itemStructure("VARIANTS")
            .variantsJson(
                """
                [
                  {"sku":"KED-BLU-0-3","size":"0-3","color":"blue","rate":100,"stock":2,"active":true,"imageUrl":"https://cdn.example.com/blue-03.jpg"},
                  {"sku":"KED-BLU-3-6","size":"3-6","color":"blue","rate":100,"stock":2,"active":true,"imageUrl":"https://cdn.example.com/blue-36.jpg"},
                  {"sku":"KED-BLU-6-12","size":"6-12","color":"blue","rate":100,"stock":2,"active":true}
                ]
                """
            );

        assertThat(itemImageSync.resolveVariantImageUrls(product, "KED-BLU-6-12")).isEmpty();
        assertThat(itemImageSync.resolveVariantImageUrls(product, "KED-BLU-0-3"))
            .containsExactly("https://cdn.example.com/blue-03.jpg");
    }

    @Test
    void syncVariantImage_withoutOwnPhotoDeletesZohoItemImage() {
        Product product = new Product()
            .id(21L)
            .sku("KEDAR")
            .itemStructure("VARIANTS")
            .variantsJson(
                """
                [
                  {"sku":"KED-BLU-0-3","size":"0-3","color":"blue","rate":100,"stock":2,"active":true}
                ]
                """
            );

        itemImageSync.syncVariantImage("zoho-03", product, "KED-BLU-0-3");

        verify(booksClient).deleteItemImage("zoho-03");
    }

    @Test
    void importZohoVariantColorImage_replacesStoredPhotosWithZohoPhotos() {
        Product product = new Product()
            .id(22L)
            .sku("KEDAR")
            .itemStructure("VARIANTS")
            .variantsJson(
                """
                [
                  {"sku":"KED-BLU-0-3","size":"0-3","color":"blue","rate":380,"stock":89,"active":true,"imageUrl":"https://cdn.example.com/screenshot.jpg"},
                  {"sku":"KED-BLU-3-6","size":"3-6","color":"blue","rate":380,"stock":12,"active":true,"imageUrl":"https://cdn.example.com/wood.jpg"}
                ]
                """
            );

        itemImageSync.importZohoVariantColorImage(
            product,
            "blue",
            "zoho-03",
            Map.of("sku", "KED-BLU-0-3", "image_name", "screenshot.jpg", "image_url", "https://cdn.example.com/uat/products/fresh-03.jpg")
        );
        itemImageSync.importZohoVariantColorImage(
            product,
            "blue",
            "zoho-36",
            Map.of("sku", "KED-BLU-3-6", "image_name", "wood.jpg", "image_url", "https://cdn.example.com/uat/products/fresh-36.jpg")
        );

        assertThat(product.getVariantsJson()).doesNotContain("screenshot.jpg");
        assertThat(product.getVariantsJson()).doesNotContain("wood.jpg");
        assertThat(product.getVariantsJson()).contains("fresh-03.jpg");
        assertThat(product.getVariantsJson()).contains("fresh-36.jpg");
    }

    @Test
    void importZohoVariantColorImage_samePhotoAcrossSizes_doesNotCollapseRichColourGallery() {
        // The shop uploads photos once per colour, never per size — every size's Zoho item is
        // pushed the same single representative photo (Zoho items hold only one image each).
        // Pulling that single photo back down must not replace the admin's richer local gallery.
        Product product = new Product()
            .id(24L)
            .sku("BASIC-SHORTS")
            .itemStructure("VARIANTS")
            .colorsJson(
                "[{\"name\":\"Blue\",\"imageUrls\":[" +
                "\"https://cdn.example.com/blue-1.png\",\"https://cdn.example.com/blue-2.png\"," +
                "\"https://cdn.example.com/blue-3.png\"]}]"
            )
            .variantsJson(
                """
                [
                  {"sku":"BS-03-BLUE","size":"0-3","color":"Blue","rate":100,"stock":2,"active":true},
                  {"sku":"BS-36-BLUE","size":"3-6","color":"Blue","rate":100,"stock":2,"active":true}
                ]
                """
            );

        itemImageSync.importZohoVariantColorImage(
            product,
            "Blue",
            "zoho-03",
            Map.of("sku", "BS-03-BLUE", "image_name", "blue-1.png", "image_url", "https://cdn.example.com/blue-1.png"),
            false
        );
        itemImageSync.importZohoVariantColorImage(
            product,
            "Blue",
            "zoho-36",
            Map.of("sku", "BS-36-BLUE", "image_name", "blue-1.png", "image_url", "https://cdn.example.com/blue-1.png"),
            false
        );

        assertThat(product.getColorsJson()).contains("blue-1.png");
        assertThat(product.getColorsJson()).contains("blue-2.png");
        assertThat(product.getColorsJson()).contains("blue-3.png");
    }

    @Test
    void importZohoVariantColorImage_emptySameColorSizeInheritsSiblingPhotos() {
        Product product = new Product()
            .id(16L)
            .sku("KEDAR")
            .itemStructure("VARIANTS")
            .variantsJson(
                """
                [
                  {"sku":"KED-BLU-0-3","size":"0-3","color":"blue","rate":380,"stock":2,"active":true,"imageUrl":"https://cdn.example.com/blue-03.jpg"},
                  {"sku":"KED-BLU-3-6","size":"3-6","color":"blue","rate":380,"stock":2,"active":true}
                ]
                """
            );
        itemImageSync.importZohoVariantColorImage(
            product,
            "blue",
            "zoho-36",
            Map.of("sku", "KED-BLU-3-6")
        );

        ProductVariantSupport variants = new ProductVariantSupport(new com.fasterxml.jackson.databind.ObjectMapper());
        Map<String, String> imageBySku = new java.util.LinkedHashMap<>();
        for (ProductVariantSupport.VariantRow row : variants.parseProduct(product)) {
            imageBySku.put(row.sku(), row.imageUrl());
        }
        assertThat(imageBySku.get("KED-BLU-0-3")).contains("blue-03.jpg");
        assertThat(imageBySku.get("KED-BLU-3-6")).contains("blue-03.jpg");
    }

    @Test
    void fullPull_twoColorsTwoSizesEach_yieldsExactlyTwoColorSwatches() {
        Product product = new Product()
            .id(23L)
            .sku("KEDAR")
            .itemStructure("VARIANTS")
            .variantsJson(
                """
                [
                  {"sku":"KED-PINK-0-3","size":"0-3","color":"Pink","rate":100,"stock":2,"active":true},
                  {"sku":"KED-PINK-3-6","size":"3-6","color":"Pink","rate":100,"stock":2,"active":true},
                  {"sku":"KED-BLUE-0-3","size":"0-3","color":"Blue","rate":100,"stock":2,"active":true},
                  {"sku":"KED-BLUE-3-6","size":"3-6","color":"Blue","rate":100,"stock":2,"active":true}
                ]
                """
            );

        // Mirrors ZohoSyncService.mergePulledZohoVariantItem: each SKU imported with
        // shareEmptySizes=false as its own Zoho item arrives, in arbitrary/page order.
        itemImageSync.importZohoVariantColorImage(
            product,
            "Pink",
            "zoho-pink-03",
            Map.of("sku", "KED-PINK-0-3", "image_name", "p.jpg", "image_url", "https://cdn.example.com/uat/pink-03.jpg"),
            false
        );
        itemImageSync.importZohoVariantColorImage(
            product,
            "Blue",
            "zoho-blue-03",
            Map.of("sku", "KED-BLUE-0-3", "image_name", "b.jpg", "image_url", "https://cdn.example.com/uat/blue-03.jpg"),
            false
        );
        itemImageSync.importZohoVariantColorImage(
            product,
            "Pink",
            "zoho-pink-36",
            Map.of("sku", "KED-PINK-3-6", "image_name", "p2.jpg", "image_url", "https://cdn.example.com/uat/pink-36.jpg"),
            false
        );
        itemImageSync.importZohoVariantColorImage(
            product,
            "Blue",
            "zoho-blue-36",
            Map.of("sku", "KED-BLUE-3-6", "image_name", "b2.jpg", "image_url", "https://cdn.example.com/uat/blue-36.jpg"),
            false
        );
        // Mirrors ZohoSyncService.finishVariantPhotoSharing, run once after every item is merged.
        itemImageSync.applySameColorPhotoSharing(product);

        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.JsonNode colorEntries;
        try {
            colorEntries = mapper.readTree(product.getColorsJson());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        assertThat(colorEntries.isArray()).isTrue();
        assertThat(colorEntries).hasSize(2);
        java.util.List<String> names = new java.util.ArrayList<>();
        java.util.Map<String, String> thumbnailByColor = new java.util.LinkedHashMap<>();
        colorEntries.forEach(node -> {
            if (node.isObject()) {
                names.add(node.path("name").asText());
                String thumb = node.hasNonNull("imageUrl")
                    ? node.path("imageUrl").asText()
                    : node.path("imageUrls").isArray() && node.path("imageUrls").size() > 0
                        ? node.path("imageUrls").get(0).asText()
                        : null;
                thumbnailByColor.put(node.path("name").asText(), thumb);
            } else {
                names.add(node.asText());
            }
        });
        assertThat(names).containsExactlyInAnyOrder("Pink", "Blue");
        // Neither size's front is shared with the other for the same colour — the colour entry
        // must still carry a representative print thumbnail (one size's front) so the storefront
        // swatch chip has something to show, instead of falling back to a plain hex dot.
        assertThat(thumbnailByColor.get("Pink")).isNotBlank();
        assertThat(thumbnailByColor.get("Blue")).isNotBlank();

        ProductVariantSupport variants = new ProductVariantSupport(mapper);
        Map<String, String> imageBySku = new java.util.LinkedHashMap<>();
        for (ProductVariantSupport.VariantRow row : variants.parseProduct(product)) {
            imageBySku.put(row.sku(), row.imageUrl());
        }
        assertThat(imageBySku.get("KED-PINK-0-3")).contains("pink-03.jpg");
        assertThat(imageBySku.get("KED-PINK-3-6")).contains("pink-36.jpg");
        assertThat(imageBySku.get("KED-BLUE-0-3")).contains("blue-03.jpg");
        assertThat(imageBySku.get("KED-BLUE-3-6")).contains("blue-36.jpg");
    }
}
