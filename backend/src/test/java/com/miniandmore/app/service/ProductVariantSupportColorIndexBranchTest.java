package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.domain.Product;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.util.ReflectionTestUtils;

/** Targets private color-index helpers with dense branch coverage. */
class ProductVariantSupportColorIndexBranchTest {

    private ProductVariantSupport support;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        support = StorePricingTestSupport.variantSupport(objectMapper);
    }

    @Test
    void indexRichColorNodesByPosition_fallsBackToColorImagesMap() {
        @SuppressWarnings("unchecked")
        List<JsonNode> nodes = ReflectionTestUtils.invokeMethod(
            support,
            "indexRichColorNodesByPosition",
            "[]",
            "{\"Navy\":[\"https://cdn.example.com/n1.jpg\"],\"\":[],\"Bad\":null}"
        );
        assertThat(nodes).hasSize(1);
        assertThat(nodes.get(0).path("name").asText()).isEqualTo("Navy");
        assertThat(nodes.get(0).path("imageUrls").get(0).asText()).contains("n1.jpg");
    }

    @ParameterizedTest
    @MethodSource("richColorArraySources")
    void indexRichColorNodesByPosition_indexesArrayVariants(String colorsJson, int expectedSize, String expectedLabel) {
        @SuppressWarnings("unchecked")
        List<JsonNode> nodes = ReflectionTestUtils.invokeMethod(
            support,
            "indexRichColorNodesByPosition",
            colorsJson,
            null
        );
        assertThat(nodes).hasSize(expectedSize);
        if (expectedSize > 0 && expectedLabel != null && !expectedLabel.isEmpty()) {
            JsonNode first = nodes.get(0);
            String label = first.isTextual() ? first.asText() : first.path("name").asText();
            assertThat(label).isEqualTo(expectedLabel);
        }
    }

    private static Stream<Arguments> richColorArraySources() {
        return Stream.of(
            Arguments.of("[\"Coral\"]", 1, "Coral"),
            Arguments.of("[{\"name\":\"Mint\",\"swatchUrl\":\"https://cdn.example.com/m.png\"}]", 1, "Mint"),
            Arguments.of("[{\"name\":\"Rose\",\"imageUrls\":[\"https://cdn.example.com/r.jpg\"]}]", 1, "Rose"),
            Arguments.of("[{\"name\":\"\",\"imageUrl\":\"https://cdn.example.com/x.jpg\"}]", 0, ""),
            Arguments.of("[{\"name\":\"Plain\"}]", 0, ""),
            Arguments.of("[null,{\"name\":\"Sky\",\"imageUrl\":\"https://cdn.example.com/s.jpg\"}]", 1, "Sky"),
            Arguments.of("{not-array}", 0, "")
        );
    }

    @Test
    void indexRichColorNodes_mergesColorImagesWhenNotInColorsArray() {
        @SuppressWarnings("unchecked")
        Map<String, JsonNode> map = ReflectionTestUtils.invokeMethod(
            support,
            "indexRichColorNodes",
            "[{\"name\":\"Red\",\"imageUrl\":\"https://cdn.example.com/r-main.jpg\"}]",
            "{\"Blue\":[\"https://cdn.example.com/b1.jpg\"]}"
        );
        assertThat(map).hasSize(2);
        assertThat(map.get("red").path("imageUrl").asText()).contains("r-main");
        assertThat(map.get("blue").path("imageUrls").get(0).asText()).contains("b1");
    }

    @Test
    void indexRichColorNodes_skipsDuplicateColorImageWhenColorAlreadyIndexed() {
        @SuppressWarnings("unchecked")
        Map<String, JsonNode> map = ReflectionTestUtils.invokeMethod(
            support,
            "indexRichColorNodes",
            "[{\"name\":\"Teal\",\"imageUrls\":[\"https://cdn.example.com/t.jpg\"]}]",
            "{\"Teal\":[\"https://cdn.example.com/ignored.jpg\"]}"
        );
        assertThat(map).hasSize(1);
        assertThat(map.get("teal").path("imageUrls").get(0).asText()).contains("t.jpg");
    }

    @ParameterizedTest
    @MethodSource("invalidColorImagesJson")
    void indexRichColorNodes_handlesInvalidColorImages(String colorImagesJson) {
        @SuppressWarnings("unchecked")
        Map<String, JsonNode> map = ReflectionTestUtils.invokeMethod(
            support,
            "indexRichColorNodes",
            "[{\"name\":\"Gold\",\"swatchUrl\":\"https://cdn.example.com/g.png\"}]",
            colorImagesJson
        );
        assertThat(map).containsKey("gold");
    }

    private static Stream<String> invalidColorImagesJson() {
        return Stream.of(null, "  ", "[]", "{\"Empty\":[]}", "{bad-json");
    }

    @Test
    void indexRichColorNodesByPosition_indexesObjectWithImageUrlOnly() {
        @SuppressWarnings("unchecked")
        List<JsonNode> nodes = ReflectionTestUtils.invokeMethod(
            support,
            "indexRichColorNodesByPosition",
            "[{\"name\":\"Ivory\",\"imageUrl\":\"https://cdn.example.com/i.jpg\"}]",
            null
        );
        assertThat(nodes).hasSize(1);
        assertThat(nodes.get(0).path("imageUrl").asText()).contains("i.jpg");
    }

    @Test
    void indexRichColorNodesByPosition_invalidColorImagesJson_keepsArrayResults() {
        @SuppressWarnings("unchecked")
        List<JsonNode> nodes = ReflectionTestUtils.invokeMethod(
            support,
            "indexRichColorNodesByPosition",
            "[\"Peach\"]",
            "{not-json"
        );
        assertThat(nodes).hasSize(1);
        assertThat(nodes.get(0).asText()).isEqualTo("Peach");
    }

    @Test
    void indexRichColorNodes_nonObjectColorImagesRoot_returnsColorsOnly() {
        @SuppressWarnings("unchecked")
        Map<String, JsonNode> map = ReflectionTestUtils.invokeMethod(
            support,
            "indexRichColorNodes",
            "[{\"name\":\"Lime\",\"swatchUrl\":\"https://cdn.example.com/l.png\"}]",
            "[\"ignored\"]"
        );
        assertThat(map).hasSize(1).containsKey("lime");
    }

    @Test
    void freezeVariantColorsFromStored_skipsRowsWithoutSkuOrColor() {
        Product product = new Product()
            .variantsJson("[{\"sku\":\"KEEP\",\"size\":\"S\",\"color\":\"Blue\",\"rate\":1,\"stock\":1,\"active\":true}]");
        String frozen =
            """
            [{"sku":"","color":"Red"},{"sku":"KEEP","color":""},{"sku":"KEEP","size":"S","color":"FrozenBlue","rate":1,"stock":1,"active":true}]
            """;
        support.freezeVariantColorsFromStored(product, frozen);
        assertThat(product.getVariantsJson()).contains("FrozenBlue");
    }

    @Test
    void freezeVariantColorsFromStored_restoresColorsPerSku() {
        Product product = new Product()
            .variantsJson(
                """
                [{"sku":"A-SKU","size":"S","color":"NewName","rate":1,"stock":1,"active":true},
                 {"sku":"B-SKU","size":"M","color":"Blue","rate":1,"stock":1,"active":true}]
                """
            );
        String frozen =
            """
            [{"sku":"A-SKU","size":"S","color":"FrozenRed","rate":1,"stock":1,"active":true}]
            """;
        support.freezeVariantColorsFromStored(product, frozen);
        assertThat(product.getVariantsJson()).contains("FrozenRed").doesNotContain("NewName");
        assertThat(product.getVariantsJson()).contains("Blue");
    }

    @Test
    void freezeVariantColorsFromStored_noOpOnBlankInputs() {
        Product product = new Product().variantsJson("[{\"sku\":\"X\",\"color\":\"A\"}]");
        support.freezeVariantColorsFromStored(null, "[]");
        support.freezeVariantColorsFromStored(product, "  ");
        support.freezeVariantColorsFromStored(product, "[]");
        assertThat(product.getVariantsJson()).contains("A");
    }

    @ParameterizedTest
    @MethodSource("indexRichColorNodesByPositionEdgeCases")
    void indexRichColorNodesByPosition_coversRemainingBranches(String colorsJson, String colorImagesJson, int expectedSize) {
        @SuppressWarnings("unchecked")
        List<JsonNode> nodes = ReflectionTestUtils.invokeMethod(
            support,
            "indexRichColorNodesByPosition",
            colorsJson,
            colorImagesJson
        );
        assertThat(nodes).hasSize(expectedSize);
    }

    private static Stream<Arguments> indexRichColorNodesByPositionEdgeCases() {
        return Stream.of(
            Arguments.of("[{\"name\":\"Plain\"}]", null, 0),
            Arguments.of("[42,{\"name\":\"Teal\",\"imageUrl\":\"https://cdn.example.com/t.jpg\"}]", null, 1),
            Arguments.of("[\"A\",\"B\"]", "{\"C\":[\"https://cdn.example.com/c.jpg\"]}", 2),
            Arguments.of(
                "[{\"name\":\"Rose\",\"imageUrls\":[\"https://cdn.example.com/r.jpg\"]}]",
                "{\"Rose\":[\"https://cdn.example.com/ignored.jpg\"]}",
                1
            ),
            Arguments.of(null, "{\"Sage\":[\"https://cdn.example.com/s.jpg\"]}", 1),
            Arguments.of("[]", "{\"\":\"https://bad\"}", 0),
            Arguments.of("[\"A\"]", "{\"Only\":[]}", 1),
            Arguments.of("{bad-colors", "{\"Lime\":[\"https://cdn.example.com/l.jpg\"]}", 1),
            Arguments.of("[{\"name\":\"SwatchOnly\",\"swatchUrl\":\"https://cdn.example.com/s.png\"}]", null, 1),
            Arguments.of("[\"  \",{\"name\":\"Gold\",\"imageUrls\":[]}]", null, 0),
            Arguments.of("[\"A\"]", "{bad-images", 1)
        );
    }

    @ParameterizedTest
    @MethodSource("indexRichColorNodesEdgeCases")
    void indexRichColorNodes_coversRemainingBranches(String colorsJson, String colorImagesJson, int expectedSize) {
        @SuppressWarnings("unchecked")
        Map<String, JsonNode> map = ReflectionTestUtils.invokeMethod(
            support,
            "indexRichColorNodes",
            colorsJson,
            colorImagesJson
        );
        assertThat(map).hasSize(expectedSize);
    }

    private static Stream<Arguments> indexRichColorNodesEdgeCases() {
        return Stream.of(
            Arguments.of("[null,{\"name\":\"\",\"imageUrl\":\"https://cdn.example.com/x.jpg\"}]", null, 0),
            Arguments.of("[{\"name\":\"NoMedia\"}]", null, 0),
            Arguments.of("[\"TextOnly\"]", null, 0),
            Arguments.of("not-json", "{\"Navy\":[\"https://cdn.example.com/n.jpg\"]}", 1),
            Arguments.of("[{\"name\":\"Red\",\"swatchUrl\":\"https://cdn.example.com/s.png\"}]", "{\"Blue\":[\"https://cdn.example.com/b.jpg\"]}", 2),
            Arguments.of("[{\"name\":\"Dup\",\"imageUrl\":\"https://cdn.example.com/d.jpg\"}]", "{\"dup\":[\"https://cdn.example.com/other.jpg\"]}", 1),
            Arguments.of("[]", "[\"not-object\"]", 0),
            Arguments.of("[]", "{\"Skip\":\"not-array\"}", 0)
        );
    }

    @Test
    void mergeColorsPreservingGalleries_textualPositionAndDuplicateNames() throws Exception {
        String merged = support.mergeColorsPreservingGalleries("[\"Scarlet\"]", null, List.of("Scarlet", "scarlet", "Navy"));
        assertThat(merged).contains("Scarlet").contains("Navy");
    }

    @Test
    void freezeVariantColorsFromStored_noWriteWhenColorsAlreadyMatch() {
        Product product = new Product()
            .variantsJson("[{\"sku\":\"A\",\"size\":\"S\",\"color\":\"Frozen\",\"rate\":1,\"stock\":1,\"active\":true}]");
        String frozen = "[{\"sku\":\"A\",\"size\":\"S\",\"color\":\"Frozen\",\"rate\":1,\"stock\":1,\"active\":true}]";
        String before = product.getVariantsJson();
        support.freezeVariantColorsFromStored(product, frozen);
        assertThat(product.getVariantsJson()).isEqualTo(before);
    }

    @Test
    void freezeVariantColorsFromStored_allFrozenRowsMissingSkuOrColor() {
        Product product = new Product()
            .variantsJson("[{\"sku\":\"A\",\"size\":\"S\",\"color\":\"Blue\",\"rate\":1,\"stock\":1,\"active\":true}]");
        support.freezeVariantColorsFromStored(product, "[{\"sku\":\"\",\"color\":\"X\"},{\"color\":\"Y\"}]");
        assertThat(product.getVariantsJson()).contains("Blue");
    }
}
