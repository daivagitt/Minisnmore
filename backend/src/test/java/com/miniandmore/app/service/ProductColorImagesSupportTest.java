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
}
