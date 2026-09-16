package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.miniandmore.app.client.ZohoBooksClient;
import com.miniandmore.app.client.ZohoBooksClient.BinaryDownload;
import com.miniandmore.app.client.ZohoBooksClient.ImageUpload;
import com.miniandmore.app.config.ApplicationProperties;
import com.miniandmore.app.domain.Product;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Optional;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
    private HttpServer imageServer;

    @AfterEach
    void tearDown() {
        if (imageServer != null) {
            imageServer.stop(0);
        }
    }

    private String startImageServer(Map<String, byte[]> pathsToBytes) throws java.io.IOException {
        imageServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        pathsToBytes.forEach((path, bytes) ->
            imageServer.createContext(path, exchange -> {
                exchange.getResponseHeaders().add("Content-Type", "image/jpeg");
                exchange.sendResponseHeaders(200, bytes.length);
                try (var body = exchange.getResponseBody()) {
                    body.write(bytes);
                }
            })
        );
        imageServer.start();
        return "http://127.0.0.1:" + imageServer.getAddress().getPort();
    }

    @BeforeEach
    void setUp() {
        itemImageSync = new ZohoItemImageSync(
            new com.fasterxml.jackson.databind.ObjectMapper(),
            applicationProperties,
            booksClient,
            s3StorageService,
            StorePricingTestSupport.variantSupport(new com.fasterxml.jackson.databind.ObjectMapper())
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
    void importZohoItemImage_storageFailureIsVisibleAndPreservesOldGallery() {
        Product product = new Product().id(99L).sku("SAFE").imagesJson("[\"old.jpg\"]");
        when(booksClient.downloadItemImage(eq("zoho-99"), any(Long.class)))
            .thenReturn(Optional.of(new BinaryDownload("bytes".getBytes(), "image/jpeg")));
        when(s3StorageService.uploadObject(eq("products"), any(), any(), eq("image/jpeg")))
            .thenThrow(new IllegalStateException("storage unavailable"));
        assertThatThrownBy(() -> itemImageSync.importZohoItemImage(product, "zoho-99", Map.of("image_name", "photo.jpg")))
            .isInstanceOf(IllegalStateException.class);
        assertThat(product.getImagesJson()).isEqualTo("[\"old.jpg\"]");
    }

    @Test
    void importZohoItemImage_declaredButUnavailableImageIsNotSuccess() {
        assertThatThrownBy(() -> itemImageSync.importZohoItemImage(new Product().sku("SAFE"), "missing", Map.of("image_name", "photo.jpg")))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("no usable image bytes");
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
    void importZohoItemImage_prefersNativeZohoImageOverLeftoverStoreCdnUrl() {
        Product product = new Product()
            .id(33L)
            .sku("SKU-33")
            .imagesJson("[\"https://cdn.example.com/uat/products/old.jpg\"]");
        when(booksClient.downloadItemImage(eq("zoho-33"), any(Long.class)))
            .thenReturn(Optional.of(new BinaryDownload("new-bytes".getBytes(), "image/jpeg")));
        when(s3StorageService.uploadObject(eq("products"), any(), eq("new-bytes".getBytes()), eq("image/jpeg")))
            .thenReturn("https://cdn.example.com/uat/products/zoho-new.jpg");

        itemImageSync.importZohoItemImage(
            product,
            "zoho-33",
            Map.of(
                "sku", "SKU-33",
                "image_name", "replaced.jpg",
                "image_url", "https://cdn.example.com/uat/products/old.jpg"
            )
        );

        assertThat(product.getImagesJson()).contains("https://cdn.example.com/uat/products/zoho-new.jpg");
        verify(booksClient).downloadItemImage(eq("zoho-33"), any(Long.class));
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
    void importZohoVariantColorImage_pullsEveryDocumentNotJustTheSingleItemImage() {
        // Zoho Books items can carry several photos (front/back/other) as a "documents" array —
        // GET /items/{id}/image (image_document_id) only ever returns the single front photo.
        Product product = new Product()
            .id(30L)
            .sku("JABLA")
            .itemStructure("VARIANTS")
            .variantsJson(
                """
                [
                  {"sku":"JAB-AQU","size":"0-3","color":"aqua","rate":380,"stock":0,"active":true}
                ]
                """
            );
        when(booksClient.downloadDocument(eq("doc-1"), any(Long.class)))
            .thenReturn(Optional.of(new BinaryDownload("front".getBytes(), "image/png")));
        when(booksClient.downloadDocument(eq("doc-2"), any(Long.class)))
            .thenReturn(Optional.of(new BinaryDownload("back".getBytes(), "image/png")));
        when(booksClient.downloadDocument(eq("doc-3"), any(Long.class)))
            .thenReturn(Optional.of(new BinaryDownload("other".getBytes(), "image/png")));
        when(s3StorageService.uploadObject(eq("products"), any(), eq("front".getBytes()), eq("image/png")))
            .thenReturn("https://cdn.example.com/uat/products/aqua-front.png");
        when(s3StorageService.uploadObject(eq("products"), any(), eq("back".getBytes()), eq("image/png")))
            .thenReturn("https://cdn.example.com/uat/products/aqua-back.png");
        when(s3StorageService.uploadObject(eq("products"), any(), eq("other".getBytes()), eq("image/png")))
            .thenReturn("https://cdn.example.com/uat/products/aqua-other.png");

        itemImageSync.importZohoVariantColorImage(
            product,
            "aqua",
            "zoho-item-1",
            Map.of(
                "sku", "JAB-AQU",
                "image_name", "1.png",
                "image_url", "https://cdn.example.com/uat/products/should-not-lead.jpg",
                "documents", List.of(
                    Map.of("document_id", "doc-3", "attachment_order", 3),
                    Map.of("document_id", "doc-1", "attachment_order", 1),
                    Map.of("document_id", "doc-2", "attachment_order", 2, "is_back_image", true)
                )
            )
        );

        assertThat(new ProductColorImagesSupport(new com.fasterxml.jackson.databind.ObjectMapper())
            .galleryMapFromColorsJson(product.getColorsJson()))
            .containsEntry(
                "aqua",
                List.of(
                    "https://cdn.example.com/uat/products/aqua-front.png",
                    "https://cdn.example.com/uat/products/aqua-back.png",
                    "https://cdn.example.com/uat/products/aqua-other.png"
                )
            );
        assertThat(product.getColorsJson()).doesNotContain("should-not-lead.jpg");
    }

    @Test
    void importZohoVariantColorImage_sameColorDifferentSizesShareOnePrintGallery() {
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

        // Zoho has no size axis for photos — every size of a colour is uploaded with the same set.
        // The first size we pull seeds the Pink gallery; the second size's own (duplicate) photo
        // must NOT also be appended, or the gallery would inflate every time a sibling size is pulled.
        assertThat(product.getColorsJson()).contains("pink-03.jpg");
        assertThat(product.getColorsJson()).doesNotContain("pink-36.jpg");
        assertThat(product.getVariantsJson()).doesNotContain("pink-03.jpg");
        assertThat(product.getVariantsJson()).doesNotContain("pink-36.jpg");
    }

    @Test
    void resolveVariantImageUrls_ignoresPerSkuPhotoUsesColorGalleryOnly() {
        Product product = new Product()
            .id(6L)
            .sku("KEDAR")
            .itemStructure("VARIANTS")
            .imagesJson("[\"https://cdn.example.com/parent-mixed.jpg\"]")
            .colorImagesJson("{\"Pink\":[\"https://cdn.example.com/pink-print.jpg\"]}")
            .variantsJson(
                """
                [
                  {"sku":"KEDAR-03-PINK","size":"0-3M","color":"Pink","rate":100,"stock":2,"active":true,"imageUrl":"https://cdn.example.com/pink-03.jpg"},
                  {"sku":"KEDAR-36-PINK","size":"3-6M","color":"Pink","rate":100,"stock":2,"active":true,"imageUrl":"https://cdn.example.com/pink-36.jpg"}
                ]
                """
            );

        // Every size of the same print pushes the print's shared gallery — the per-SKU imageUrl
        // fields (leftover/manually set) are not consulted, matching the Ledgerly image model.
        assertThat(itemImageSync.resolveVariantImageUrls(product, "KEDAR-03-PINK"))
            .containsExactly("https://cdn.example.com/pink-print.jpg");
        assertThat(itemImageSync.resolveVariantImageUrls(product, "KEDAR-36-PINK"))
            .containsExactly("https://cdn.example.com/pink-print.jpg");
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
    void resolveVariantImageUrls_leftoverPerSkuPhotosAreIgnoredWithoutPrintGallery() {
        // Stale per-SKU imageUrl values (e.g. from before this model existed) must not leak into a
        // Zoho push — a print with no shared gallery pushes nothing, regardless of what individual
        // variant rows still carry.
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
        assertThat(itemImageSync.resolveVariantImageUrls(product, "KED-RED-3-6")).isEmpty();
        assertThat(itemImageSync.resolveVariantImageUrls(product, "KED-RED-6-12")).isEmpty();
    }

    @Test
    void resolveVariantImageUrls_everySizeOfSamePrintGetsSameGallery() {
        Product product = new Product()
            .id(18L)
            .sku("KEDAR")
            .itemStructure("VARIANTS")
            .colorImagesJson("{\"blue\":[\"https://cdn.example.com/blue-print.jpg\"]}")
            .variantsJson(
                """
                [
                  {"sku":"KED-BLU-0-3","size":"0-3","color":"blue","rate":100,"stock":2,"active":true},
                  {"sku":"KED-BLU-3-6","size":"3-6","color":"blue","rate":100,"stock":2,"active":true},
                  {"sku":"KED-BLU-6-12","size":"6-12","color":"blue","rate":100,"stock":2,"active":true}
                ]
                """
            );

        assertThat(itemImageSync.resolveVariantImageUrls(product, "KED-BLU-0-3"))
            .containsExactly("https://cdn.example.com/blue-print.jpg");
        assertThat(itemImageSync.resolveVariantImageUrls(product, "KED-BLU-3-6"))
            .containsExactly("https://cdn.example.com/blue-print.jpg");
        assertThat(itemImageSync.resolveVariantImageUrls(product, "KED-BLU-6-12"))
            .containsExactly("https://cdn.example.com/blue-print.jpg");
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

        verify(booksClient).deleteItemImages("zoho-03");
    }

    @Test
    void importZohoVariantColorImage_replacesPerSkuPhotosWithPrintGalleryPhotos() {
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

        // Old per-SKU photos are gone, and the pull never writes new ones onto individual SKUs.
        // The first size pulled (0-3) seeds the "blue" print gallery; the second size's own photo
        // is a duplicate upload of the same set from Zoho's point of view and must not be appended.
        assertThat(product.getVariantsJson()).doesNotContain("screenshot.jpg");
        assertThat(product.getVariantsJson()).doesNotContain("wood.jpg");
        assertThat(product.getVariantsJson()).doesNotContain("fresh-03.jpg");
        assertThat(product.getVariantsJson()).doesNotContain("fresh-36.jpg");
        assertThat(product.getColorsJson()).contains("fresh-03.jpg");
        assertThat(product.getColorsJson()).doesNotContain("fresh-36.jpg");
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
            Map.of("sku", "BS-03-BLUE", "image_name", "blue-1.png", "image_url", "https://cdn.example.com/blue-1.png")
        );
        itemImageSync.importZohoVariantColorImage(
            product,
            "Blue",
            "zoho-36",
            Map.of("sku", "BS-36-BLUE", "image_name", "blue-1.png", "image_url", "https://cdn.example.com/blue-1.png")
        );

        assertThat(product.getColorsJson()).contains("blue-1.png");
        assertThat(product.getColorsJson()).contains("blue-2.png");
        assertThat(product.getColorsJson()).contains("blue-3.png");
    }

    @Test
    void importZohoVariantColorImage_replacesExistingPrintGalleryWhenZohoPhotoChanged() {
        Product product = new Product()
            .id(31L)
            .sku("KEDAR")
            .itemStructure("VARIANTS")
            .colorsJson("[{\"name\":\"Blue\",\"imageUrls\":[\"https://cdn.example.com/uat/products/old-blue.jpg\"]}]")
            .colorImagesJson("{\"Blue\":[\"https://cdn.example.com/uat/products/old-blue.jpg\"]}")
            .variantsJson(
                """
                [
                  {"sku":"KED-BLU-0-3","size":"0-3","color":"Blue","rate":100,"stock":2,"active":true},
                  {"sku":"KED-BLU-3-6","size":"3-6","color":"Blue","rate":100,"stock":2,"active":true}
                ]
                """
            );

        itemImageSync.importZohoVariantColorImage(
            product,
            "Blue",
            "zoho-03",
            Map.of("sku", "KED-BLU-0-3", "image_name", "new.jpg", "image_url", "https://cdn.example.com/uat/products/new-blue.jpg")
        );
        itemImageSync.importZohoVariantColorImage(
            product,
            "Blue",
            "zoho-36",
            Map.of("sku", "KED-BLU-3-6", "image_name", "old.jpg", "image_url", "https://cdn.example.com/uat/products/old-blue.jpg")
        );

        assertThat(new ProductColorImagesSupport(new com.fasterxml.jackson.databind.ObjectMapper())
            .galleryUrlsForColor(product, "Blue"))
            .containsExactly("https://cdn.example.com/uat/products/new-blue.jpg");
        assertThat(product.getColorsJson()).doesNotContain("old-blue.jpg");
    }

    @Test
    void importZohoItemImage_replacesSimpleProductColorGalleryWhenZohoPhotoChanged() {
        Product product = new Product()
            .id(32L)
            .sku("SIDE-BUTTON")
            .itemStructure("SIMPLE")
            .imagesJson("[\"https://cdn.example.com/uat/products/old-front.jpg\"]")
            .colorsJson("[{\"name\":\"Aqua\",\"imageUrls\":[\"https://cdn.example.com/uat/products/old-front.jpg\"]}]")
            .colorImagesJson("{\"Aqua\":[\"https://cdn.example.com/uat/products/old-front.jpg\"]}");

        itemImageSync.importZohoItemImage(
            product,
            "zoho-side",
            Map.of("sku", "SIDE-BUTTON", "image_name", "new.jpg", "image_url", "https://cdn.example.com/uat/products/new-front.jpg")
        );

        assertThat(product.getImagesJson()).contains("new-front.jpg");
        assertThat(new ProductColorImagesSupport(new com.fasterxml.jackson.databind.ObjectMapper())
            .galleryUrlsForColor(product, "Aqua"))
            .containsExactly("https://cdn.example.com/uat/products/new-front.jpg");
        assertThat(product.getColorsJson()).doesNotContain("old-front.jpg");
    }

    @Test
    void importZohoVariantColorImage_secondPullCanReplaceAgainAfterBeginImageImportPass() {
        Product product = new Product()
            .id(34L)
            .sku("KEDAR")
            .itemStructure("VARIANTS")
            .colorsJson("[{\"name\":\"Blue\",\"imageUrls\":[\"https://cdn.example.com/uat/products/first.jpg\"]}]")
            .variantsJson(
                """
                [
                  {"sku":"KED-BLU-0-3","size":"0-3","color":"Blue","rate":100,"stock":2,"active":true}
                ]
                """
            );

        itemImageSync.importZohoVariantColorImage(
            product,
            "Blue",
            "zoho-03",
            Map.of("sku", "KED-BLU-0-3", "image_name", "second.jpg", "image_url", "https://cdn.example.com/uat/products/second.jpg")
        );
        itemImageSync.beginImageImportPass();
        itemImageSync.importZohoVariantColorImage(
            product,
            "Blue",
            "zoho-03",
            Map.of("sku", "KED-BLU-0-3", "image_name", "third.jpg", "image_url", "https://cdn.example.com/uat/products/third.jpg")
        );

        assertThat(new ProductColorImagesSupport(new com.fasterxml.jackson.databind.ObjectMapper())
            .galleryUrlsForColor(product, "Blue"))
            .containsExactly("https://cdn.example.com/uat/products/third.jpg");
    }

    @Test
    void importZohoVariantColorImage_emptySameColorSizeSharesSiblingPrintGallery() {
        // The 0-3 size's photo was pulled first onto the "blue" print gallery. When the 3-6 size's
        // Zoho item has no photo of its own, it still resolves the same print photo because images
        // are looked up by colour, not by SKU.
        Product product = new Product()
            .id(16L)
            .sku("KEDAR")
            .itemStructure("VARIANTS")
            .variantsJson(
                """
                [
                  {"sku":"KED-BLU-0-3","size":"0-3","color":"blue","rate":380,"stock":2,"active":true},
                  {"sku":"KED-BLU-3-6","size":"3-6","color":"blue","rate":380,"stock":2,"active":true}
                ]
                """
            );
        itemImageSync.importZohoVariantColorImage(
            product,
            "blue",
            "zoho-03",
            Map.of("sku", "KED-BLU-0-3", "image_name", "photo.jpg", "image_url", "https://cdn.example.com/uat/products/blue-03.jpg")
        );
        itemImageSync.importZohoVariantColorImage(
            product,
            "blue",
            "zoho-36",
            Map.of("sku", "KED-BLU-3-6")
        );

        assertThat(itemImageSync.resolveVariantImageUrls(product, "KED-BLU-0-3"))
            .containsExactly("https://cdn.example.com/uat/products/blue-03.jpg");
        assertThat(itemImageSync.resolveVariantImageUrls(product, "KED-BLU-3-6"))
            .containsExactly("https://cdn.example.com/uat/products/blue-03.jpg");
    }

    @Test
    void importZohoVariantColorImage_emptySizePulledFirstStillSharesLaterSiblingsPhoto() {
        // Same as the sibling-sharing case above but in the opposite pull order: the photo-less
        // size (3-6) is pulled before the size that actually has a photo (0-3). Order must not
        // matter — the gallery should still end up seeded and shared by both sizes.
        Product product = new Product()
            .id(27L)
            .sku("KEDAR")
            .itemStructure("VARIANTS")
            .variantsJson(
                """
                [
                  {"sku":"KED-BLU-0-3","size":"0-3","color":"blue","rate":380,"stock":2,"active":true},
                  {"sku":"KED-BLU-3-6","size":"3-6","color":"blue","rate":380,"stock":2,"active":true}
                ]
                """
            );
        itemImageSync.importZohoVariantColorImage(product, "blue", "zoho-36", Map.of("sku", "KED-BLU-3-6"));
        itemImageSync.importZohoVariantColorImage(
            product,
            "blue",
            "zoho-03",
            Map.of("sku", "KED-BLU-0-3", "image_name", "photo.jpg", "image_url", "https://cdn.example.com/uat/products/blue-03.jpg")
        );

        assertThat(itemImageSync.resolveVariantImageUrls(product, "KED-BLU-0-3"))
            .containsExactly("https://cdn.example.com/uat/products/blue-03.jpg");
        assertThat(itemImageSync.resolveVariantImageUrls(product, "KED-BLU-3-6"))
            .containsExactly("https://cdn.example.com/uat/products/blue-03.jpg");
    }

    @Test
    void fullPull_twoColorsTwoSizesEach_firstSizePulledSeedsSharedGalleryOthersIgnored() {
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

        // Mirrors ZohoSyncService.mergePulledZohoVariantItem: each SKU imported as its own Zoho
        // item arrives, in arbitrary/page order.
        itemImageSync.importZohoVariantColorImage(
            product,
            "Pink",
            "zoho-pink-03",
            Map.of("sku", "KED-PINK-0-3", "image_name", "p.jpg", "image_url", "https://cdn.example.com/uat/pink-03.jpg")
        );
        itemImageSync.importZohoVariantColorImage(
            product,
            "Blue",
            "zoho-blue-03",
            Map.of("sku", "KED-BLUE-0-3", "image_name", "b.jpg", "image_url", "https://cdn.example.com/uat/blue-03.jpg")
        );
        itemImageSync.importZohoVariantColorImage(
            product,
            "Pink",
            "zoho-pink-36",
            Map.of("sku", "KED-PINK-3-6", "image_name", "p2.jpg", "image_url", "https://cdn.example.com/uat/pink-36.jpg")
        );
        itemImageSync.importZohoVariantColorImage(
            product,
            "Blue",
            "zoho-blue-36",
            Map.of("sku", "KED-BLUE-3-6", "image_name", "b2.jpg", "image_url", "https://cdn.example.com/uat/blue-36.jpg")
        );

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
        colorEntries.forEach(node -> names.add(node.isObject() ? node.path("name").asText() : node.asText()));
        assertThat(names).containsExactlyInAnyOrder("Pink", "Blue");

        // The first size pulled for each print (the 0-3 SKUs, imported first above) seeds that
        // print's one shared gallery. The 3-6 SKUs' own photos are Zoho's duplicate copy of the
        // same set and must not also be appended — every size still resolves to one shared set,
        // it's just sourced from whichever size we saw photos for first, not a union of both.
        assertThat(itemImageSync.resolveVariantImageUrls(product, "KED-PINK-0-3"))
            .containsExactly("https://cdn.example.com/uat/pink-03.jpg");
        assertThat(itemImageSync.resolveVariantImageUrls(product, "KED-PINK-3-6"))
            .containsExactly("https://cdn.example.com/uat/pink-03.jpg");
        assertThat(itemImageSync.resolveVariantImageUrls(product, "KED-BLUE-0-3"))
            .containsExactly("https://cdn.example.com/uat/blue-03.jpg");
        assertThat(itemImageSync.resolveVariantImageUrls(product, "KED-BLUE-3-6"))
            .containsExactly("https://cdn.example.com/uat/blue-03.jpg");
    }

    @Test
    void syncVariantImage_pushesEveryColourGalleryPhotoInOneMultiImageUpload() throws Exception {
        String base = startImageServer(Map.of("/1.jpg", "one".getBytes(), "/2.jpg", "two".getBytes()));
        Product product = new Product()
            .id(25L)
            .sku("KEDAR")
            .itemStructure("VARIANTS")
            .colorImagesJson("{\"blue\":[\"" + base + "/1.jpg\",\"" + base + "/2.jpg\"]}")
            .variantsJson(
                """
                [
                  {"sku":"KED-BLU-0-3","size":"0-3","color":"blue","rate":100,"stock":2,"active":true}
                ]
                """
            );

        itemImageSync.syncVariantImage("zoho-03", product, "KED-BLU-0-3");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ImageUpload>> captor = ArgumentCaptor.forClass(List.class);
        verify(booksClient).uploadItemImages(eq("zoho-03"), captor.capture());
        assertThat(captor.getValue()).hasSize(2);
        verify(booksClient, never()).uploadItemImage(any(), any(), any(), any());
    }

    @Test
    void syncProductImage_combinesPrimaryAndColourGalleryImages() throws Exception {
        String base = startImageServer(Map.of("/1.jpg", "one".getBytes(), "/2.jpg", "two".getBytes(), "/3.jpg", "three".getBytes()));
        Product product = new Product()
            .id(27L)
            .name("Side Button")
            .sku("SIDE-BUTTON-T-SHIRT-HALF-SLEEVE")
            .itemStructure("SIMPLE")
            .imagesJson("[\"" + base + "/1.jpg\"]")
            .colorsJson(
                "[{\"name\":\"Aqua\",\"imageUrls\":[\"" +
                base +
                "/1.jpg\",\"" +
                base +
                "/2.jpg\",\"" +
                base +
                "/3.jpg\"]}]"
            )
            .colorImagesJson("{\"Aqua\":[\"" + base + "/1.jpg\",\"" + base + "/2.jpg\",\"" + base + "/3.jpg\"]}");

        itemImageSync.syncProductImage("zoho-side-button", product);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ImageUpload>> captor = ArgumentCaptor.forClass(List.class);
        verify(booksClient).uploadItemImages(eq("zoho-side-button"), captor.capture());
        assertThat(captor.getValue()).hasSize(3);
    }

    @Test
    void syncVariantImage_doesNotTruncateColourGalleryAfterThreePhotos() throws Exception {
        String base = startImageServer(
            Map.of(
                "/1.jpg", "one".getBytes(),
                "/2.jpg", "two".getBytes(),
                "/3.jpg", "three".getBytes(),
                "/4.jpg", "four".getBytes(),
                "/5.jpg", "five".getBytes()
            )
        );
        Product product = new Product()
            .id(28L)
            .sku("KEDAR")
            .itemStructure("VARIANTS")
            .colorImagesJson(
                "{\"blue\":[\"" +
                base +
                "/1.jpg\",\"" +
                base +
                "/2.jpg\",\"" +
                base +
                "/3.jpg\",\"" +
                base +
                "/4.jpg\",\"" +
                base +
                "/5.jpg\"]}"
            )
            .variantsJson(
                """
                [
                  {"sku":"KED-BLU-0-3","size":"0-3","color":"blue","rate":100,"stock":2,"active":true}
                ]
                """
            );

        itemImageSync.syncVariantImage("zoho-03", product, "KED-BLU-0-3");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ImageUpload>> captor = ArgumentCaptor.forClass(List.class);
        verify(booksClient).uploadItemImages(eq("zoho-03"), captor.capture());
        assertThat(captor.getValue()).hasSize(5);
    }

    @Test
    void syncVariantImage_reportsFailureWhenZohoRejectsMultiImageUpload() throws Exception {
        String base = startImageServer(Map.of("/1.jpg", "one".getBytes(), "/2.jpg", "two".getBytes()));
        Product product = new Product()
            .id(26L)
            .sku("KEDAR")
            .itemStructure("VARIANTS")
            .colorImagesJson("{\"blue\":[\"" + base + "/1.jpg\",\"" + base + "/2.jpg\"]}")
            .variantsJson(
                """
                [
                  {"sku":"KED-BLU-0-3","size":"0-3","color":"blue","rate":100,"stock":2,"active":true}
                ]
                """
            );
        org.mockito.Mockito
            .doThrow(new IllegalStateException("HTTP 404"))
            .when(booksClient)
            .uploadItemImages(any(), any());

        assertThatThrownBy(() -> itemImageSync.syncVariantImage("zoho-03", product, "KED-BLU-0-3"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("HTTP 404");

        verify(booksClient, never()).uploadItemImage(any(), any(), any(), any());
    }

    @Test
    void syncVariantImage_truncatesGalleryAtMaxImagesPerColor() throws Exception {
        applicationProperties.getZoho().setMaxProductImages(3);
        java.util.Map<String, byte[]> files = new java.util.LinkedHashMap<>();
        for (int i = 1; i <= ProductColorImagesSupport.MAX_IMAGES_PER_COLOR + 2; i++) {
            files.put("/" + i + ".jpg", ("img-" + i).getBytes());
        }
        String base = startImageServer(files);
        StringBuilder gallery = new StringBuilder("[");
        for (int i = 1; i <= ProductColorImagesSupport.MAX_IMAGES_PER_COLOR + 2; i++) {
            if (i > 1) {
                gallery.append(',');
            }
            gallery.append("\"").append(base).append("/").append(i).append(".jpg\"");
        }
        gallery.append(']');
        Product product = new Product()
            .id(29L)
            .sku("KEDAR")
            .itemStructure("VARIANTS")
            .colorImagesJson("{\"blue\":" + gallery + "}")
            .variantsJson(
                """
                [
                  {"sku":"KED-BLU-0-3","size":"0-3","color":"blue","rate":100,"stock":2,"active":true}
                ]
                """
            );

        itemImageSync.syncVariantImage("zoho-03", product, "KED-BLU-0-3");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ImageUpload>> captor = ArgumentCaptor.forClass(List.class);
        verify(booksClient).uploadItemImages(eq("zoho-03"), captor.capture());
        assertThat(captor.getValue()).hasSize(3);
    }

    @Test
    void syncVariantImage_failsWhenAnyImageDownloadFails() throws Exception {
        String base = startImageServer(Map.of("/1.jpg", "one".getBytes(), "/2.jpg", "two".getBytes()));
        Product product = new Product()
            .id(30L)
            .sku("KEDAR")
            .itemStructure("VARIANTS")
            .colorImagesJson(
                "{\"blue\":[\"" + base + "/1.jpg\",\"https://invalid.example.test/missing.jpg\",\"" + base + "/2.jpg\"]}"
            )
            .variantsJson(
                """
                [
                  {"sku":"KED-BLU-0-3","size":"0-3","color":"blue","rate":100,"stock":2,"active":true}
                ]
                """
            );

        assertThatThrownBy(() -> itemImageSync.syncVariantImage("zoho-03", product, "KED-BLU-0-3"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Could not download");

        verify(booksClient, never()).uploadItemImages(any(), any());
    }
}
