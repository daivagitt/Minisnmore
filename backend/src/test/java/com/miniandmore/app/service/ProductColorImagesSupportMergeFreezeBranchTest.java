package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ProductColorImagesSupportMergeFreezeBranchTest {

    private ProductColorImagesSupport support;

    @BeforeEach
    void setUp() {
        support = new ProductColorImagesSupport(new ObjectMapper());
    }

    @ParameterizedTest
    @MethodSource("applyGalleryCases")
    void applyGalleryMapToColorsJson_matrix(String colorsJson, Map<String, List<String>> gallery, String fragment) {
        String merged = support.applyGalleryMapToColorsJson(colorsJson, gallery);
        assertThat(merged).contains(fragment);
    }

    private static Stream<Arguments> applyGalleryCases() {
        Map<String, List<String>> fromMap = new LinkedHashMap<>();
        fromMap.put("Teal", List.of("https://cdn.example.com/t1.jpg"));
        return Stream.of(
            Arguments.of(null, Map.of(), "[]"),
            Arguments.of("[]", fromMap, "Teal"),
            Arguments.of("[\"Red\"]", Map.of("Red", List.of("https://cdn.example.com/r.jpg")), "r.jpg"),
            Arguments.of("[{\"name\":\"Navy\"}]", Map.of("navy", List.of("https://cdn.example.com/n.jpg")), "n.jpg")
        );
    }

    @ParameterizedTest
    @MethodSource("mergeFrozenCases")
    void mergeFrozenColorMedia_restoresGalleriesBySlot(String matrix, String admin, String expectedUrl) {
        String merged = support.mergeFrozenColorMedia(matrix, admin);
        assertThat(merged).contains(expectedUrl);
    }

    private static Stream<Arguments> mergeFrozenCases() {
        return Stream.of(
            Arguments.of("[]", "[]", ""),
            Arguments.of(
                "[{\"name\":\"A\",\"imageUrls\":[\"https://cdn.example.com/a.jpg\"]}]",
                "[{\"name\":\"B\",\"imageUrls\":[\"https://cdn.example.com/b.jpg\"]}]",
                "b.jpg"
            ),
            Arguments.of(
                "[{\"name\":\"Slot0\"},{\"name\":\"Slot1\"}]",
                "[{\"name\":\"X\",\"imageUrls\":[\"https://cdn.example.com/slot.jpg\"]}]",
                "slot.jpg"
            )
        );
    }

    @ParameterizedTest
    @MethodSource("freezeSwatchCases")
    void freezeSwatchesFromStored_restoresByNameOrIndex(String current, String frozen, String expectedSwatch) {
        String out = support.freezeSwatchesFromStored(current, frozen);
        assertThat(out).contains(expectedSwatch);
    }

    private static Stream<Arguments> freezeSwatchCases() {
        return Stream.of(
            Arguments.of("[]", "[{\"name\":\"R\",\"swatchUrl\":\"https://cdn.example.com/r.png\"}]", "[]"),
            Arguments.of(
                "[{\"name\":\"Scarlet\",\"imageUrls\":[\"https://cdn.example.com/g.jpg\"]}]",
                "[{\"name\":\"Scarlet\",\"swatchUrl\":\"https://cdn.example.com/s.png\"}]",
                "s.png"
            ),
            Arguments.of(
                "[{\"name\":\"Renamed\"}]",
                "[{\"name\":\"Old\",\"swatchUrl\":\"https://cdn.example.com/pos.png\"}]",
                "pos.png"
            )
        );
    }

    @ParameterizedTest
    @MethodSource("galleryUrlCases")
    void galleryUrlsForColor_handlesMissingAndRichEntries(String colorsJson, String color, int expectedSize) {
        com.miniandmore.app.domain.Product product = new com.miniandmore.app.domain.Product().colorsJson(colorsJson);
        assertThat(support.galleryUrlsForColor(product, color)).hasSize(expectedSize);
    }

    private static Stream<Arguments> galleryUrlCases() {
        return Stream.of(
            Arguments.of(null, "Red", 0),
            Arguments.of("[{\"name\":\"Red\",\"imageUrls\":[\"https://cdn.example.com/r.jpg\"]}]", "red", 1),
            Arguments.of("[{\"name\":\"Red\",\"imageUrls\":[\"https://cdn.example.com/r.jpg\"]}]", "Blue", 0)
        );
    }
}
