package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.domain.Product;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProductColorImagesSupportTest {

    private ProductColorImagesSupport support;

    @BeforeEach
    void setUp() {
        support = new ProductColorImagesSupport(new ObjectMapper());
    }

    @Test
    void galleryMapFromColorsJson_usesGalleryOnlyNotSwatch() {
        String colorsJson =
            "[{\"name\":\"Print 1\",\"swatchUrl\":\"https://cdn.example.com/swatch.jpg\",\"imageUrls\":[\"https://cdn.example.com/a.jpg\",\"https://cdn.example.com/b.jpg\"]}," +
            "{\"name\":\"Print 2\",\"imageUrl\":\"https://cdn.example.com/c.jpg\"}]";

        Map<String, List<String>> map = support.galleryMapFromColorsJson(colorsJson);

        assertThat(map).containsEntry("Print 1", List.of("https://cdn.example.com/a.jpg", "https://cdn.example.com/b.jpg"));
        assertThat(map).containsEntry("Print 2", List.of("https://cdn.example.com/c.jpg"));
        assertThat(map.get("Print 1")).doesNotContain("https://cdn.example.com/swatch.jpg");
    }

    @Test
    void syncProductColorImages_derivesColorImagesJsonFromGallery() {
        Product product = new Product()
            .colorsJson(
                "[{\"name\":\"Navy\",\"imageUrls\":[\"https://cdn.example.com/navy-1.jpg\",\"https://cdn.example.com/navy-2.jpg\"]}," +
                "{\"name\":\"Blush\",\"swatchUrl\":\"https://cdn.example.com/blush-swatch.jpg\",\"imageUrl\":\"https://cdn.example.com/blush-1.jpg\"}]"
            );

        support.syncProductColorImages(product);

        assertThat(support.parseMap(product.getColorImagesJson()))
            .containsEntry("Navy", List.of("https://cdn.example.com/navy-1.jpg", "https://cdn.example.com/navy-2.jpg"))
            .containsEntry("Blush", List.of("https://cdn.example.com/blush-1.jpg"));
        assertThat(product.getColorsJson()).contains("swatchUrl").contains("blush-swatch.jpg");
    }

    @Test
    void syncProductColorImages_preservesExistingGalleryWhenColorsArePlainNames() {
        Product product = new Product()
            .colorsJson("[\"Print 1\",\"Print 2\"]")
            .colorImagesJson("{\"Print 1\":[\"https://cdn.example.com/p1.jpg\"]}");

        support.syncProductColorImages(product);

        assertThat(product.getColorsJson()).contains("Print 1").contains("p1.jpg");
        assertThat(support.parseMap(product.getColorImagesJson())).containsEntry("Print 1", List.of("https://cdn.example.com/p1.jpg"));
    }

    @Test
    void ensureProductImagesFromColorGalleries_fillsEmptyImagesJson() {
        Product product = new Product()
            .colorsJson("[{\"name\":\"Print 1\",\"imageUrls\":[\"https://cdn.example.com/a.jpg\",\"https://cdn.example.com/b.jpg\"]}]")
            .imagesJson("[]");

        support.ensureProductImagesFromColorGalleries(product);

        assertThat(product.getImagesJson()).contains("a.jpg").contains("b.jpg");
    }

    @Test
    void applyGalleryMapToColorsJson_mergesIntoStringColorNames() {
        String colorsJson = "[\"Navy\",\"Blush\"]";
        Map<String, List<String>> map = Map.of(
            "Navy",
            List.of("https://cdn.example.com/navy-1.jpg"),
            "Blush",
            List.of("https://cdn.example.com/blush-1.jpg")
        );

        String merged = support.applyGalleryMapToColorsJson(colorsJson, map);

        assertThat(merged).contains("\"name\":\"Navy\"").contains("navy-1.jpg").contains("blush-1.jpg");
        assertThat(support.galleryMapFromColorsJson(merged)).isEqualTo(map);
    }

    @Test
    void applyGalleryMapToColorsJson_matchesGalleryKeysCaseInsensitively() {
        String colorsJson = "[\"Navy\",\"Blush\"]";
        Map<String, List<String>> map = Map.of("navy", List.of("https://cdn.example.com/navy-1.jpg"));

        String merged = support.applyGalleryMapToColorsJson(colorsJson, map);

        assertThat(merged).contains("navy-1.jpg").contains("\"name\":\"Navy\"");
    }

    @Test
    void mergeRichColorMetadata_restoresSwatchesAfterMatrixPlainNames() {
        String adminColors =
            "[{\"name\":\"BEE\",\"swatchUrl\":\"https://cdn.example.com/bee-swatch.jpg\"}," +
            "{\"name\":\"BLUE\",\"swatchUrl\":\"https://cdn.example.com/blue-swatch.jpg\"}]";
        String matrixColors = "[\"BEE\",\"BLUE\",\"FISH\"]";

        String merged = support.mergeRichColorMetadata(matrixColors, adminColors);

        assertThat(merged).contains("bee-swatch.jpg").contains("blue-swatch.jpg");
        assertThat(merged).contains("\"name\":\"FISH\"");
        assertThat(merged).doesNotContain("fish-swatch");
    }

    @Test
    void ensureProductImagesFromColorGalleries_usesSwatchesWhenNoGallery() {
        Product product = new Product()
            .colorsJson("[{\"name\":\"BEE\",\"swatchUrl\":\"https://cdn.example.com/bee-swatch.jpg\"}]")
            .imagesJson("[]");

        support.ensureProductImagesFromColorGalleries(product);

        assertThat(product.getImagesJson()).contains("bee-swatch.jpg");
    }

    @Test
    void syncProductColorImages_realignsGenericPrintSlotsToGalleryKeys() {
        Product product = new Product()
            .colorsJson("[\"Print 3\",\"Print 4\",\"Print 5\"]")
            .colorImagesJson(
                "{\"BROWN SWADDLE\":[\"https://cdn.example.com/brown.jpg\"]," +
                "\"GREEN SWADDLE\":[\"https://cdn.example.com/green.jpg\"]," +
                "\"WHITE SWADDLE\":[\"https://cdn.example.com/white.jpg\"]}"
            );

        support.syncProductColorImages(product);

        assertThat(product.getColorsJson()).contains("BROWN SWADDLE").contains("brown.jpg").doesNotContain("Print 3");
        assertThat(support.parseMap(product.getColorImagesJson()))
            .containsEntry("BROWN SWADDLE", List.of("https://cdn.example.com/brown.jpg"));
    }

    @Test
    void buildPositionalColorRename_mapsOldPrintSlotsToGalleryNames() {
        Map<String, String> rename = support.buildPositionalColorRename(
            "[\"Print 3\",\"Print 4\"]",
            "[{\"name\":\"BROWN SWADDLE\",\"imageUrls\":[\"https://cdn.example.com/brown.jpg\"]}," +
            "{\"name\":\"GREEN SWADDLE\",\"imageUrls\":[\"https://cdn.example.com/green.jpg\"]}]"
        );

        assertThat(rename).containsEntry("Print 3", "BROWN SWADDLE").containsEntry("Print 4", "GREEN SWADDLE");
    }

    @Test
    void serializeAndParse_roundTrip() {
        Map<String, List<String>> map = Map.of("Navy", List.of("https://cdn.example.com/navy.jpg"));
        String json = support.serializeMap(map);

        assertThat(support.parseMap(json)).isEqualTo(map);
    }

    @Test
    void syncProductColorImages_nullProductIsNoOp() {
        support.syncProductColorImages(null);
    }

    @Test
    void ensureProductImagesFromColorGalleries_skipsWhenImagesJsonPopulated() {
        Product product = new Product()
            .colorsJson("[{\"name\":\"Navy\",\"imageUrls\":[\"https://cdn.example.com/navy.jpg\"]}]")
            .imagesJson("[\"https://cdn.example.com/existing.jpg\"]");

        support.ensureProductImagesFromColorGalleries(product);

        assertThat(product.getImagesJson()).contains("existing.jpg").doesNotContain("navy.jpg");
    }

    @Test
    void ensureProductImagesFromColorGalleries_nullProductIsNoOp() {
        support.ensureProductImagesFromColorGalleries(null);
    }

    @Test
    void collectSwatchAndGalleryUrls_deduplicateAndSkipBlanks() {
        String colorsJson =
            "[{\"name\":\"A\",\"swatchUrl\":\"https://cdn.example.com/s.jpg\"}," +
            "{\"name\":\"B\",\"swatchUrl\":\"  \"}," +
            "{\"name\":\"C\",\"imageUrls\":[\"https://cdn.example.com/g1.jpg\",\"https://cdn.example.com/g1.jpg\"]}]";

        assertThat(support.collectSwatchUrlsFromColorsJson(colorsJson)).containsExactly("https://cdn.example.com/s.jpg");
        assertThat(support.collectGalleryUrlsFromColorsJson(colorsJson)).containsExactly("https://cdn.example.com/g1.jpg");
    }

    @Test
    void allSimpleProductGallerySourceUrls_mergesPrimaryAndColourGalleriesWithoutDuplicates() {
        Product product = new Product()
            .imagesJson("[\"https://cdn.example.com/primary.jpg\"]")
            .colorsJson(
                "[{\"name\":\"Aqua\",\"imageUrls\":[\"https://cdn.example.com/primary.jpg\",\"https://cdn.example.com/g2.jpg\"]}]"
            )
            .colorImagesJson("{\"Aqua\":[\"https://cdn.example.com/primary.jpg\",\"https://cdn.example.com/g2.jpg\"]}");

        assertThat(support.allSimpleProductGallerySourceUrls(product))
            .containsExactly("https://cdn.example.com/primary.jpg", "https://cdn.example.com/g2.jpg");
    }

    @Test
    void normalizeColorsJson_dedupesPlainStringEntries() {
        String normalized = support.normalizeColorsJson("[\"Navy\",\"navy\",\"Blush\"]");

        assertThat(normalized).isEqualTo("[\"Navy\",\"Blush\"]");
    }

    @Test
    void coerceMap_handlesMapStringAndInvalidObject() {
        Map<String, List<String>> fromMap = support.coerceMap(Map.of("Navy", List.of("https://cdn.example.com/n.jpg")));
        assertThat(fromMap).containsEntry("Navy", List.of("https://cdn.example.com/n.jpg"));

        assertThat(support.coerceMap("{\"Blush\":[\"https://cdn.example.com/b.jpg\"]}")).containsKey("Blush");
        assertThat(support.coerceMap(42)).isEmpty();
        assertThat(support.coerceMap(null)).isEmpty();
    }

    @Test
    void parseMap_returnsEmptyForInvalidJson() {
        assertThat(support.parseMap("not-json")).isEmpty();
        assertThat(support.parseMap(null)).isEmpty();
    }

    @Test
    void realignColorNamesToGalleryKeys_buildsFromEmptyColors() {
        Map<String, List<String>> gallery = Map.of("BROWN", List.of("https://cdn.example.com/brown.jpg"));
        String realigned = support.realignColorNamesToGalleryKeys("[]", gallery);

        assertThat(realigned).contains("BROWN").contains("brown.jpg");
    }

    @Test
    void realignColorNamesToGalleryKeys_usesApplyWhenSizesMismatch() {
        String colorsJson = "[\"Print 1\",\"Print 2\",\"Print 3\"]";
        Map<String, List<String>> gallery = Map.of(
            "A",
            List.of("https://cdn.example.com/a.jpg"),
            "B",
            List.of("https://cdn.example.com/b.jpg")
        );

        String realigned = support.realignColorNamesToGalleryKeys(colorsJson, gallery);

        assertThat(realigned).contains("Print 1").contains("Print 2").contains("Print 3");
        assertThat(support.galleryMapFromColorsJson(realigned)).isEmpty();
    }

    @Test
    void buildPositionalColorRename_returnsEmptyWhenSizesDiffer() {
        assertThat(support.buildPositionalColorRename("[\"A\"]", "[\"A\",\"B\"]")).isEmpty();
    }

    @Test
    void mergeRichColorMetadata_returnsRichWhenMatrixEmpty() {
        String rich = "[{\"name\":\"BEE\",\"swatchUrl\":\"https://cdn.example.com/bee.jpg\"}]";

        assertThat(support.mergeRichColorMetadata("[]", rich)).contains("bee.jpg");
    }

    @Test
    void mergeFrozenColorMedia_ignoresAdminSwatchUploads() {
        String matrixColors = "[\"AQUA\",\"Baby Blue\"]";
        String adminColors =
            "[{\"name\":\"AQUA\",\"swatchUrl\":\"https://cdn.example.com/new-aqua.jpg\"}," +
            "{\"name\":\"Baby Blue\",\"imageUrls\":[\"https://cdn.example.com/gallery.jpg\"]}]";

        String merged = support.mergeFrozenColorMedia(matrixColors, adminColors);

        assertThat(merged).doesNotContain("new-aqua.jpg").contains("gallery.jpg");
    }

    @Test
    void freezeSwatchesFromStored_restoresSwatchBySlotWhenZohoRenamedPrint() {
        String currentColors = "[\"AQUA\",\"Baby Blue\"]";
        String frozenColors =
            "[{\"name\":\"OLD AQUA\",\"swatchUrl\":\"https://cdn.example.com/aqua.jpg\"}," +
            "{\"name\":\"OLD BLUE\",\"swatchUrl\":\"https://cdn.example.com/blue.jpg\"}]";

        String merged = support.freezeSwatchesFromStored(currentColors, frozenColors);

        assertThat(merged).contains("\"name\":\"AQUA\"").contains("aqua.jpg");
        assertThat(merged).contains("\"name\":\"Baby Blue\"").contains("blue.jpg");
    }

    @Test
    void applyGalleryMapToColorsJson_buildsFromEmptyColorsJson() {
        Map<String, List<String>> gallery = Map.of("Navy", List.of("https://cdn.example.com/navy.jpg"));

        String merged = support.applyGalleryMapToColorsJson("[]", gallery);

        assertThat(merged).contains("Navy").contains("navy.jpg");
    }

    @Test
    void appendGalleryUrl_keepsBothPrintGalleries() {
        Product product = new Product();
        support.appendGalleryUrl(product, "Pink", "https://cdn.example.com/pink.jpg");
        support.appendGalleryUrl(product, "Blue", "https://cdn.example.com/blue.jpg");

        assertThat(product.getColorImagesJson()).contains("Pink").contains("pink.jpg").contains("Blue").contains("blue.jpg");
        assertThat(product.getColorsJson()).contains("Pink").contains("Blue");
    }

    @Test
    void seedGalleryUrlIfEmpty_doesNotMixSameColourSizes() {
        Product product = new Product();
        support.seedGalleryUrlIfEmpty(product, "Pink", "https://cdn.example.com/pink-03.jpg");
        support.seedGalleryUrlIfEmpty(product, "Pink", "https://cdn.example.com/pink-36.jpg");

        assertThat(product.getColorImagesJson()).contains("pink-03.jpg");
        assertThat(product.getColorImagesJson()).doesNotContain("pink-36.jpg");
    }

    @Test
    void replaceColorGallery_unmixesSameColourSizePhotos() {
        Product product = new Product();
        support.appendGalleryUrl(product, "Pink", "https://cdn.example.com/pink-03.jpg");
        support.appendGalleryUrl(product, "Pink", "https://cdn.example.com/pink-36.jpg");
        support.replaceColorGallery(product, "Pink", List.of("https://cdn.example.com/pink-03.jpg"));

        assertThat(product.getColorImagesJson()).contains("pink-03.jpg");
        assertThat(product.getColorImagesJson()).doesNotContain("pink-36.jpg");
    }

    @Test
    void syncProductColorImages_clearsColorImagesWhenNoGallery() {
        Product product = new Product().colorsJson("[\"Plain\"]");

        support.syncProductColorImages(product);

        assertThat(product.getColorImagesJson()).isNull();
    }

    @Test
    void galleryUrls_capAtMaxPerColor() {
        List<String> urls = new java.util.ArrayList<>();
        for (int i = 0; i < ProductColorImagesSupport.MAX_IMAGES_PER_COLOR + 3; i++) {
            urls.add("https://cdn.example.com/img-" + i + ".jpg");
        }
        String colorsJson =
            "[{\"name\":\"Busy\",\"imageUrls\":" + new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(urls) + "}]";

        Map<String, List<String>> map = support.galleryMapFromColorsJson(colorsJson);

        assertThat(map.get("Busy")).hasSize(ProductColorImagesSupport.MAX_IMAGES_PER_COLOR);
    }

    @Test
    void ensureProductImagesFromColorGalleries_treatsWhitespaceOnlyArrayAsEmpty() {
        Product product = new Product()
            .colorsJson("[{\"name\":\"Navy\",\"imageUrls\":[\"https://cdn.example.com/navy.jpg\"]}]")
            .imagesJson("[\"   \"]");

        support.ensureProductImagesFromColorGalleries(product);

        assertThat(product.getImagesJson()).contains("navy.jpg");
    }

    @Test
    void appendGalleryUrl_nullOrBlankInputs_areNoOp() {
        Product product = new Product();
        support.appendGalleryUrl(null, "Navy", "https://cdn.example.com/n.jpg");
        support.appendGalleryUrl(product, " ", "https://cdn.example.com/n.jpg");
        support.appendGalleryUrl(product, "Navy", " ");

        assertThat(product.getColorsJson()).isNull();
    }

    @Test
    void appendGalleryUrl_trimsGalleryToMaxPerColor() {
        Product product = new Product();
        for (int i = 0; i < ProductColorImagesSupport.MAX_IMAGES_PER_COLOR + 2; i++) {
            support.appendGalleryUrl(product, "Busy", "https://cdn.example.com/img-" + i + ".jpg");
        }

        assertThat(support.galleryUrlsForColor(product, "Busy")).hasSize(ProductColorImagesSupport.MAX_IMAGES_PER_COLOR);
    }

    @Test
    void seedGalleryUrlIfEmpty_nullProduct_isNoOp() {
        support.seedGalleryUrlIfEmpty(null, "Navy", "https://cdn.example.com/n.jpg");
    }

    @Test
    void replaceColorGallery_nullProduct_isNoOp() {
        support.replaceColorGallery(null, "Navy", List.of("https://cdn.example.com/n.jpg"));
    }

    @Test
    void replaceColorGallery_blankColor_isNoOp() {
        Product product = new Product();
        support.replaceColorGallery(product, "  ", List.of("https://cdn.example.com/n.jpg"));
        assertThat(product.getColorsJson()).isNull();
    }

    @Test
    void galleryUrlsForColor_nullOrBlank_returnsEmpty() {
        Product product = new Product().colorsJson("[{\"name\":\"Navy\",\"imageUrls\":[\"https://cdn.example.com/n.jpg\"]}]");
        assertThat(support.galleryUrlsForColor(null, "Navy")).isEmpty();
        assertThat(support.galleryUrlsForColor(product, " ")).isEmpty();
    }

    @Test
    void galleryUrlsForColor_mergesColorsJsonAndColorImagesMap() {
        Product product = new Product()
            .colorsJson("[{\"name\":\"Navy\",\"imageUrls\":[\"https://cdn.example.com/navy.jpg\"]}]")
            .colorImagesJson("{\"Navy\":[\"https://cdn.example.com/extra.jpg\"]}");

        assertThat(support.galleryUrlsForColor(product, "navy"))
            .containsExactly("https://cdn.example.com/navy.jpg", "https://cdn.example.com/extra.jpg");
    }

    @Test
    void stripUrlsFromImagesJson_removesMatchingUrls() {
        Product product = new Product()
            .imagesJson("[\"https://cdn.example.com/keep.jpg\",\"https://cdn.example.com/remove.jpg\"]");

        support.stripUrlsFromImagesJson(product, List.of("https://cdn.example.com/remove.jpg"));

        assertThat(product.getImagesJson()).contains("keep.jpg").doesNotContain("remove.jpg");
    }

    @Test
    void stripUrlsFromImagesJson_nullInputs_areNoOp() {
        Product product = new Product().imagesJson("[\"https://cdn.example.com/a.jpg\"]");
        support.stripUrlsFromImagesJson(null, List.of("https://cdn.example.com/a.jpg"));
        support.stripUrlsFromImagesJson(product, null);
        support.stripUrlsFromImagesJson(product, List.of("  "));
        assertThat(product.getImagesJson()).contains("a.jpg");
    }

    @Test
    void stripUrlsFromImagesJson_invalidJson_isNoOp() {
        Product product = new Product().imagesJson("not-json");
        support.stripUrlsFromImagesJson(product, List.of("https://cdn.example.com/a.jpg"));
        assertThat(product.getImagesJson()).isEqualTo("not-json");
    }

    @Test
    void stripUrlsFromImagesJson_nonArrayJson_isNoOp() {
        Product product = new Product().imagesJson("{\"url\":\"https://cdn.example.com/a.jpg\"}");
        support.stripUrlsFromImagesJson(product, List.of("https://cdn.example.com/a.jpg"));
        assertThat(product.getImagesJson()).contains("a.jpg");
    }

    @Test
    void seedGalleryUrlIfEmpty_blankInputs_areNoOp() {
        Product product = new Product();
        support.seedGalleryUrlIfEmpty(product, " ", "https://cdn.example.com/n.jpg");
        support.seedGalleryUrlIfEmpty(product, "Navy", " ");
        assertThat(product.getColorsJson()).isNull();
    }

    @Test
    void allSimpleProductGallerySourceUrls_nullProduct_returnsEmpty() {
        assertThat(support.allSimpleProductGallerySourceUrls(null)).isEmpty();
    }

    @Test
    void stripUrlsFromImagesJson_clearingAllUrlsNullsImagesJson() {
        Product product = new Product().imagesJson("[\"https://cdn.example.com/only.jpg\"]");
        support.stripUrlsFromImagesJson(product, List.of("https://cdn.example.com/only.jpg"));
        assertThat(product.getImagesJson()).isNull();
    }

    @Test
    void collectSwatchUrlsFromColorsJson_blank_returnsEmpty() {
        assertThat(support.collectSwatchUrlsFromColorsJson(null)).isEmpty();
    }

    @Test
    void galleryMapFromColorsJson_doesNotPromoteImageUrlAheadOfImageUrls() {
        String colorsJson =
            "[{\"name\":\"Print\",\"imageUrl\":\"https://cdn.example.com/a.jpg\",\"imageUrls\":[\"https://cdn.example.com/b.jpg\",\"https://cdn.example.com/a.jpg\",\"https://cdn.example.com/c.jpg\"]}]";

        Map<String, List<String>> map = support.galleryMapFromColorsJson(colorsJson);

        assertThat(map.get("Print")).containsExactly(
            "https://cdn.example.com/b.jpg",
            "https://cdn.example.com/a.jpg",
            "https://cdn.example.com/c.jpg"
        );
    }

    @Test
    void galleryMapFromColorsJson_appendsImageUrlOnlyWhenMissingFromImageUrls() {
        String colorsJson =
            "[{\"name\":\"Print\",\"imageUrl\":\"https://cdn.example.com/extra.jpg\",\"imageUrls\":[\"https://cdn.example.com/b.jpg\",\"https://cdn.example.com/c.jpg\"]}]";

        Map<String, List<String>> map = support.galleryMapFromColorsJson(colorsJson);

        assertThat(map.get("Print")).containsExactly(
            "https://cdn.example.com/b.jpg",
            "https://cdn.example.com/c.jpg",
            "https://cdn.example.com/extra.jpg"
        );
    }
}
