package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.domain.Product;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.util.ReflectionTestUtils;

class ProductColorImagesSupportTopMissedMatrixTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private ProductColorImagesSupport support;

    @BeforeEach
    void setUp() {
        support = new ProductColorImagesSupport(objectMapper);
    }

    @Test
    void coerceMap_handlesNullKeysScalarValuesAndConvertibleJsonObject() throws Exception {
        Map<Object, Object> raw = new LinkedHashMap<>();
        raw.put(null, List.of("skip.jpg"));
        raw.put(" ", "skip-too.jpg");
        raw.put(" Navy ", " navy.jpg ");
        raw.put("Red", java.util.Arrays.asList(null, " red.jpg ", "red.jpg", ""));

        assertThat(support.coerceMap(raw))
            .containsEntry("Navy", List.of("navy.jpg"))
            .containsEntry("Red", List.of("red.jpg"))
            .hasSize(2);

        JsonNode convertible = objectMapper.readTree("{\"Teal\":[\" t.jpg \",\"t.jpg\"],\"Empty\":[]}");
        assertThat(support.coerceMap(convertible)).containsEntry("Teal", List.of("t.jpg")).hasSize(1);
        assertThat(support.coerceMap(new Object())).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("galleryParsingCases")
    void parseGalleryUrls_handlesMixedNodeShapes(String json, String swatch, List<String> expected) throws Exception {
        JsonNode node = objectMapper.readTree(json);

        @SuppressWarnings("unchecked")
        List<String> actual = ReflectionTestUtils.invokeMethod(support, "parseGalleryUrls", node, swatch);

        assertThat(actual).containsExactlyElementsOf(expected);
    }

    private static Stream<Arguments> galleryParsingCases() {
        return Stream.of(
            Arguments.of(
                "{\"imageUrls\":[null,7,\" a.jpg \",\"a.jpg\"],\"imageUrl\":\"hero.jpg\"}",
                null,
                List.of("7", "a.jpg", "hero.jpg")
            ),
            Arguments.of(
                "{\"imageUrls\":\"not-an-array\",\"imageUrl\":\" hero.jpg \"}",
                null,
                List.of("hero.jpg")
            ),
            Arguments.of(
                "{\"imageUrls\":[\"swatch.png\",\"gallery.jpg\"],\"imageUrl\":\"gallery.jpg\"}",
                " swatch.png ",
                List.of("gallery.jpg")
            ),
            Arguments.of("{\"imageUrls\":null,\"imageUrl\":\" \"}", null, List.of())
        );
    }

    @ParameterizedTest
    @MethodSource("colorEntryParsingCases")
    void parseColorEntries_rejectsMalformedAndDuplicateEntries(String json, List<String> expectedNames) {
        @SuppressWarnings("unchecked")
        List<Object> entries = ReflectionTestUtils.invokeMethod(support, "parseColorEntries", json);

        String normalized = support.normalizeColorsJson(json);
        assertThat(entries).hasSize(expectedNames.size());
        expectedNames.forEach(name -> assertThat(normalized).contains(name));
    }

    private static Stream<Arguments> colorEntryParsingCases() {
        return Stream.of(
            Arguments.of("[null,false,{}, {\"name\":null}, \" Blue \", \"blue\"]", List.of("Blue")),
            Arguments.of("[{\"name\":\"Red\",\"swatchUrl\":7}]", List.of("Red")),
            Arguments.of("42", List.of()),
            Arguments.of("{bad-json", List.of())
        );
    }

    @Test
    void applyGalleryMap_exactEmptyValueFallsBackToCaseInsensitiveNonEmptyKey() {
        Map<String, List<String>> gallery = new LinkedHashMap<>();
        gallery.put("Navy", List.of());
        gallery.put(" navy ", List.of("n.jpg", "n.jpg"));

        String merged = support.applyGalleryMapToColorsJson("[\"NAVY\",\"Other\"]", gallery);

        assertThat(merged).contains("n.jpg").contains("Other");
        Product product = new Product().colorsJson(merged);
        assertThat(support.galleryUrlsForColor(product, "navy")).containsExactly("n.jpg");
    }

    @Test
    void frozenMedia_usesNameThenPositionAndLeavesUnmatchedTailEmpty() {
        String matrix = "[\"A\",\"B\",\"C\"]";
        String admin =
            """
            [{"name":"Old-A","imageUrls":["by-position.jpg"]},
             {"name":"B","swatchUrl":"ignored.png","imageUrls":["by-name.jpg"]}]
            """;

        String merged = support.mergeFrozenColorMedia(matrix, admin);

        assertThat(merged).contains("by-name.jpg").contains("by-position.jpg").doesNotContain("ignored.png");
        Product product = new Product().colorsJson(merged);
        assertThat(support.galleryUrlsForColor(product, "A")).containsExactly("by-position.jpg");
        assertThat(support.galleryUrlsForColor(product, "B")).containsExactly("by-name.jpg");
        assertThat(support.galleryUrlsForColor(product, "C")).isEmpty();
    }

    @Test
    void freezeSwatches_handlesBlankStoredSlotsAndCurrentLongerThanFrozen() {
        String frozen =
            """
            [{"name":"First"},{"name":"Target","swatchUrl":" target.png "}]
            """;
        String current =
            """
            [{"name":"Renamed","swatchUrl":"remove.png"},{"name":"Target"},{"name":"Tail","swatchUrl":"remove-tail.png"}]
            """;

        String out = support.freezeSwatchesFromStored(current, frozen);

        assertThat(out).contains("target.png").doesNotContain("remove.png").doesNotContain("remove-tail.png");
        assertThat(support.collectSwatchUrlsFromColorsJson(out)).containsExactly("target.png");
    }

    @Test
    void mergeRichMetadata_emptyAndBlankMatrixEntries_haveDeterministicResults() {
        String rich =
            """
            [{"name":" Navy ","swatchUrl":"n.png","imageUrls":["n.jpg"]},
             {"name":"navy","imageUrls":["ignored.jpg"]},
             {"name":"Coral","imageUrl":"c.jpg"}]
            """;

        String fromEmptyMatrix = support.mergeRichColorMetadata("[]", rich);
        assertThat(fromEmptyMatrix).contains("Navy").contains("n.jpg").contains("Coral").contains("c.jpg");

        String matrixWithInvalidEntries = "[null,\" \",\"NAVY\",\"navy\",{\"name\":\"Coral\"}]";
        String merged = support.mergeRichColorMetadata(matrixWithInvalidEntries, rich);
        assertThat(merged).contains("NAVY").contains("n.jpg").contains("Coral").contains("c.jpg").doesNotContain("ignored.jpg");
    }

    @ParameterizedTest
    @MethodSource("stripCases")
    void stripUrls_invalidShapesDoNotRewrite(String imagesJson) {
        Product product = new Product().imagesJson(imagesJson);

        support.stripUrlsFromImagesJson(product, List.of("drop.jpg"));

        assertThat(product.getImagesJson()).isEqualTo(imagesJson);
    }

    private static Stream<String> stripCases() {
        return Stream.of("\"scalar\"", "{}", "{bad-json", "[\"keep.jpg\"]");
    }

    @Test
    void writeColorEntries_emitsRichObjectsForPlainNeighbors() {
        @SuppressWarnings("unchecked")
        List<Object> entries = ReflectionTestUtils.invokeMethod(
            support,
            "parseColorEntries",
            "[\"Plain\",{\"name\":\"One\",\"imageUrl\":\"one.jpg\"},{\"name\":\"Many\",\"imageUrls\":[\"a.jpg\",\"b.jpg\"]}]"
        );

        String out = ReflectionTestUtils.invokeMethod(support, "writeColorEntries", entries);

        assertThat(out)
            .contains("{\"name\":\"Plain\"}")
            .contains("\"imageUrl\":\"one.jpg\"")
            .contains("\"imageUrls\":[\"a.jpg\",\"b.jpg\"]");
    }

    @Test
    void sanitizeMap_acceptsNullMapAndFiltersNullValues() {
        @SuppressWarnings("unchecked")
        Map<String, List<String>> nullResult = ReflectionTestUtils.invokeMethod(support, "sanitizeMap", (Object) null);
        assertThat(nullResult).isEmpty();

        Map<String, List<String>> raw = new LinkedHashMap<>();
        raw.put(null, List.of("skip.jpg"));
        raw.put("Empty", null);
        raw.put(" Good ", new ArrayList<>(List.of(" g.jpg ", "g.jpg")));
        @SuppressWarnings("unchecked")
        Map<String, List<String>> sanitized = ReflectionTestUtils.invokeMethod(support, "sanitizeMap", raw);
        assertThat(sanitized).containsEntry("Good", List.of("g.jpg")).hasSize(1);
    }
}
