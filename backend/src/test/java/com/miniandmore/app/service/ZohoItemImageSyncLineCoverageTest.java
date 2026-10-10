package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.client.ZohoBooksClient;
import com.miniandmore.app.config.ApplicationProperties;
import com.miniandmore.app.domain.Product;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ZohoItemImageSyncLineCoverageTest {

    private ZohoItemImageSync sync;

    @BeforeEach
    void setUp() {
        sync =
            new ZohoItemImageSync(
                new com.fasterxml.jackson.databind.ObjectMapper(),
                new ApplicationProperties(),
                mock(ZohoBooksClient.class),
                mock(S3StorageService.class),
                StorePricingTestSupport.variantSupport(new com.fasterxml.jackson.databind.ObjectMapper())
            );
    }

    @Test
    void addResolvedUrls_nullCandidatesIsNoOp() {
        List<String> urls = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        ReflectionTestUtils.invokeMethod(sync, "addResolvedUrls", urls, seen, (Object) null);
        assertThat(urls).isEmpty();
    }

    @Test
    void addResolvedUrl_skipsBlankResolvedUrls() {
        List<String> urls = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        ReflectionTestUtils.invokeMethod(sync, "addResolvedUrl", urls, seen, "   ");
        assertThat(urls).isEmpty();
    }

    @Test
    void parseImageUrls_resolvesRelativeKeysAgainstConfiguredCdn() {
        ApplicationProperties properties = new ApplicationProperties();
        properties.getStorage().getS3().setPublicBaseUrl("https://cdn.example.com/bucket");
        sync =
            new ZohoItemImageSync(
                new com.fasterxml.jackson.databind.ObjectMapper(),
                properties,
                mock(ZohoBooksClient.class),
                mock(S3StorageService.class),
                StorePricingTestSupport.variantSupport(new com.fasterxml.jackson.databind.ObjectMapper())
            );
        @SuppressWarnings("unchecked")
        List<String> urls = ReflectionTestUtils.invokeMethod(sync, "parseImageUrls", "[\"/gallery/a.jpg\",\"https://other.test/b.jpg\"]");
        assertThat(urls).containsExactly("https://cdn.example.com/bucket/gallery/a.jpg", "https://other.test/b.jpg");
    }

    @Test
    void parseImageUrls_invalidJson_returnsEmpty() {
        @SuppressWarnings("unchecked")
        List<String> urls = ReflectionTestUtils.invokeMethod(sync, "parseImageUrls", "{bad");
        assertThat(urls).isEmpty();
    }

    @Test
    void filenameFromUrl_usesContentTypeWhenPathHasNoExtension() {
        String png = ReflectionTestUtils.invokeMethod(
            ZohoItemImageSync.class,
            "filenameFromUrl",
            "https://cdn.example.com/no-extension",
            "image/png"
        );
        String webp = ReflectionTestUtils.invokeMethod(
            ZohoItemImageSync.class,
            "filenameFromUrl",
            "https://cdn.example.com/no-extension",
            "image/webp"
        );
        assertThat(png).isEqualTo("product.png");
        assertThat(webp).isEqualTo("product.webp");
    }

    @Test
    void guessContentType_mapsCommonExtensions() {
        String png = ReflectionTestUtils.invokeMethod(ZohoItemImageSync.class, "guessContentType", "https://cdn/a.png");
        String gif = ReflectionTestUtils.invokeMethod(ZohoItemImageSync.class, "guessContentType", "https://cdn/a.gif");
        assertThat(png).isEqualTo("image/png");
        assertThat(gif).isEqualTo("image/gif");
    }

    @Test
    void isOnStoreCdn_matchesConfiguredBaseWithAndWithoutTrailingSlash() {
        ApplicationProperties properties = new ApplicationProperties();
        properties.getStorage().getS3().setPublicBaseUrl("https://cdn.example.com/bucket/");
        sync =
            new ZohoItemImageSync(
                new com.fasterxml.jackson.databind.ObjectMapper(),
                properties,
                mock(ZohoBooksClient.class),
                mock(S3StorageService.class),
                StorePricingTestSupport.variantSupport(new com.fasterxml.jackson.databind.ObjectMapper())
            );
        Boolean onCdn = ReflectionTestUtils.invokeMethod(sync, "isOnStoreCdn", "https://cdn.example.com/bucket/gallery/a.jpg");
        Boolean offCdn = ReflectionTestUtils.invokeMethod(sync, "isOnStoreCdn", "https://evil.example/a.jpg");
        assertThat(onCdn).isTrue();
        assertThat(offCdn).isFalse();
    }

    @Test
    void setPrimaryImage_promotesUrlToFrontOfGallery() {
        Product product = new Product().id(1L).imagesJson("[\"https://cdn.example.com/old.jpg\"]");
        ReflectionTestUtils.invokeMethod(sync, "setPrimaryImage", product, "https://cdn.example.com/new.jpg");
        assertThat(product.getImagesJson()).startsWith("[\"https://cdn.example.com/new.jpg\"");
    }

    @Test
    void storeHashedImage_emptyBytes_returnsNull() {
        Object stored = ReflectionTestUtils.invokeMethod(sync, "storeHashedImage", new byte[0], "image/jpeg");
        assertThat(stored).isNull();
    }

    @Test
    void extensionForContentType_mapsGif() {
        String ext = ReflectionTestUtils.invokeMethod(ZohoItemImageSync.class, "extensionForContentType", "image/gif");
        assertThat(ext).isEqualTo("gif");
    }

    @Test
    void collectItemHttpImageUrls_readsZohoHttpFields() {
        @SuppressWarnings("unchecked")
        List<String> urls = ReflectionTestUtils.invokeMethod(
            sync,
            "collectItemHttpImageUrls",
            java.util.Map.of("image_url", "https://books.zoho.com/item.jpg", "sku", "SKU-1")
        );
        assertThat(urls).containsExactly("https://books.zoho.com/item.jpg");
    }

    @Test
    void safeImageReference_hostOnlyUri_usesHostLabel() {
        String ref = ReflectionTestUtils.invokeMethod(ZohoItemImageSync.class, "safeImageReference", "https://cdn.example.com/");
        assertThat(ref).isEqualTo("cdn.example.com");
    }

    @Test
    void applyImportedColorGallery_replacesGalleryWhenZohoAddsNewPhoto() {
        sync.beginImageImportPass();
        Product product = new Product()
            .id(3L)
            .colorsJson("[{\"name\":\"Red\",\"imageUrls\":[\"https://cdn.example.com/old-red.jpg\"]}]");
        ReflectionTestUtils.invokeMethod(sync, "applyImportedColorGallery", product, "Red", List.of("https://cdn.example.com/new-red.jpg"));
        assertThat(product.getColorsJson()).contains("new-red.jpg");
    }

    @Test
    void safeImageReference_invalidUrl_returnsLabel() {
        String ref = ReflectionTestUtils.invokeMethod(ZohoItemImageSync.class, "safeImageReference", "not a valid uri %%");
        assertThat(ref).isEqualTo("invalid URL");
    }

    @Test
    void safeImageReference_withFilename_includesHostAndFile() {
        String ref = ReflectionTestUtils.invokeMethod(ZohoItemImageSync.class, "safeImageReference", "https://cdn.example.com/gallery/photo.jpg");
        assertThat(ref).isEqualTo("cdn.example.com/photo.jpg");
    }

    @Test
    void filenameFromUrl_withDottedPath_returnsFilename() {
        String name = ReflectionTestUtils.invokeMethod(
            ZohoItemImageSync.class,
            "filenameFromUrl",
            "https://cdn.example.com/catalog/item-99.png",
            (Object) null
        );
        assertThat(name).isEqualTo("item-99.png");
    }

    @Test
    void collectItemHttpImageUrls_nullItem_returnsEmpty() {
        @SuppressWarnings("unchecked")
        List<String> urls = ReflectionTestUtils.invokeMethod(sync, "collectItemHttpImageUrls", (Object) null);
        assertThat(urls).isEmpty();
    }

    @Test
    void filenameFromUrl_usesPathFilenameWhenExtensionPresent() {
        String name = ReflectionTestUtils.invokeMethod(
            ZohoItemImageSync.class,
            "filenameFromUrl",
            "https://cdn.example.com/gallery/sku-1.png",
            "image/jpeg"
        );
        assertThat(name).isEqualTo("sku-1.png");
    }

    @Test
    void setPrimaryImage_blankUrl_isNoOp() {
        Product product = new Product().id(2L).imagesJson("[\"https://cdn.example.com/keep.jpg\"]");
        ReflectionTestUtils.invokeMethod(sync, "setPrimaryImage", product, "   ");
        assertThat(product.getImagesJson()).contains("keep.jpg");
    }

    @Test
    void parseStoredImageUrls_skipsDuplicatesAndInvalidJson() {
        @SuppressWarnings("unchecked")
        List<String> parsed = ReflectionTestUtils.invokeMethod(
            sync,
            "parseStoredImageUrls",
            "[\"https://cdn/a.jpg\",\"https://cdn/a.jpg\",\"https://cdn/b.jpg\"]"
        );
        assertThat(parsed).containsExactly("https://cdn/a.jpg", "https://cdn/b.jpg");

        @SuppressWarnings("unchecked")
        List<String> invalid = ReflectionTestUtils.invokeMethod(sync, "parseStoredImageUrls", "not-json");
        assertThat(invalid).isEmpty();
    }

    @Test
    void parseImageUrls_blankJson_returnsEmpty() {
        @SuppressWarnings("unchecked")
        List<String> urls = ReflectionTestUtils.invokeMethod(sync, "parseImageUrls", "   ");
        assertThat(urls).isEmpty();
    }

    @Test
    void isOnStoreCdn_falseWhenBaseNotConfigured() {
        Boolean onCdn = ReflectionTestUtils.invokeMethod(sync, "isOnStoreCdn", "https://cdn.example.com/a.jpg");
        assertThat(onCdn).isFalse();
    }

    @Test
    void applyImportedColorGallery_nullProduct_isNoOp() {
        sync.beginImageImportPass();
        ReflectionTestUtils.invokeMethod(sync, "applyImportedColorGallery", null, "Red", List.of("https://cdn.example.com/x.jpg"));
    }

    @Test
    void storeHashedImage_uploadsBytesAndReturnsPublicUrl() {
        S3StorageService s3 = mock(S3StorageService.class);
        when(s3.uploadObject(any(), any(), any(), any())).thenReturn("https://cdn.example.com/products/zoho-abc.jpg");
        ZohoItemImageSync uploadingSync =
            new ZohoItemImageSync(
                new ObjectMapper(),
                new ApplicationProperties(),
                mock(ZohoBooksClient.class),
                s3,
                StorePricingTestSupport.variantSupport(new ObjectMapper())
            );
        String stored = ReflectionTestUtils.invokeMethod(uploadingSync, "storeHashedImage", new byte[] { 1, 2, 3 }, "image/jpeg");
        assertThat(stored).isEqualTo("https://cdn.example.com/products/zoho-abc.jpg");
    }

    @Test
    void filenameFromUrl_withoutExtension_defaultsToJpg() {
        String name = ReflectionTestUtils.invokeMethod(
            ZohoItemImageSync.class,
            "filenameFromUrl",
            "https://cdn.example.com/gallery/no-extension",
            null
        );
        assertThat(name).isEqualTo("product.jpg");
    }

    @Test
    void setPrimaryImage_serializationFailure_keepsExistingGallery() throws Exception {
        ObjectMapper mapper = mock(ObjectMapper.class);
        when(mapper.readValue(anyString(), any(TypeReference.class))).thenReturn(List.of("https://cdn.example.com/keep.jpg"));
        when(mapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("serialize failed") {});
        ZohoItemImageSync failingSync =
            new ZohoItemImageSync(
                mapper,
                new ApplicationProperties(),
                mock(ZohoBooksClient.class),
                mock(S3StorageService.class),
                StorePricingTestSupport.variantSupport(new ObjectMapper())
            );
        Product product = new Product().id(9L).imagesJson("[\"https://cdn.example.com/keep.jpg\"]");
        ReflectionTestUtils.invokeMethod(failingSync, "setPrimaryImage", product, "https://cdn.example.com/new.jpg");
        assertThat(product.getImagesJson()).contains("keep.jpg");
    }

    @Test
    void colorGalleryPassKey_newProductWithoutId_usesIdentityHash() {
        String key = ReflectionTestUtils.invokeMethod(ZohoItemImageSync.class, "colorGalleryPassKey", new Product(), "Green");
        assertThat(key).contains("new-").contains("green");
    }

    @Test
    void safeImageReference_trailingSlashPathUsesHostOnly() {
        String ref = ReflectionTestUtils.invokeMethod(ZohoItemImageSync.class, "safeImageReference", "https://cdn.example.com/gallery/");
        assertThat(ref).isEqualTo("cdn.example.com");
    }

    @Test
    void applyImportedColorGallery_skipsSecondReplaceInSamePass() {
        sync.beginImageImportPass();
        Product product = new Product()
            .id(4L)
            .colorsJson("[{\"name\":\"Blue\",\"imageUrls\":[\"https://cdn.example.com/old-blue.jpg\"]}]");
        ReflectionTestUtils.invokeMethod(sync, "applyImportedColorGallery", product, "Blue", List.of("https://cdn.example.com/new-blue.jpg"));
        String afterFirst = product.getColorsJson();
        ReflectionTestUtils.invokeMethod(sync, "applyImportedColorGallery", product, "Blue", List.of("https://cdn.example.com/another-blue.jpg"));
        assertThat(product.getColorsJson()).isEqualTo(afterFirst);
    }
}
