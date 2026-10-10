package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ProductVariantSupportIndexRichColorNodesMatrixTest {

    private ProductVariantSupport support;

    @BeforeEach
    void setUp() {
        support = new ProductVariantSupport(new ObjectMapper(), mock(StorePricingService.class));
    }

    @Test
    void indexRichColorNodesByPosition_coversArrayObjectTextAndFallbackMap() {
        assertThat((List<?>) ReflectionTestUtils.invokeMethod(support, "indexRichColorNodesByPosition", null, null)).isEmpty();
        assertThat((List<?>) ReflectionTestUtils.invokeMethod(support, "indexRichColorNodesByPosition", "   ", "")).isEmpty();
        assertThat((List<?>) ReflectionTestUtils.invokeMethod(support, "indexRichColorNodesByPosition", "{", null)).isEmpty();
        assertThat((List<?>) ReflectionTestUtils.invokeMethod(support, "indexRichColorNodesByPosition", "{\"x\":1}", null)).isEmpty();

        String colors =
            "[{\"name\":\"\"},{\"name\":\"Red\",\"imageUrl\":\"a.jpg\"},{\"name\":\"Blue\",\"swatchUrl\":\"s.png\"},{\"name\":\"Green\",\"imageUrls\":[\"g.jpg\"]},\"Yellow\", \"  \"]";
        List<JsonNode> fromColors = ReflectionTestUtils.invokeMethod(support, "indexRichColorNodesByPosition", colors, null);
        assertThat(fromColors).hasSize(4);

        List<JsonNode> fromImages = ReflectionTestUtils.invokeMethod(
            support,
            "indexRichColorNodesByPosition",
            "[]",
            "{\"Pink\":[\"p.jpg\"],\"\":[\"x.jpg\"],\"Blank\":[],\"Nope\":\"n.jpg\"}"
        );
        assertThat(fromImages).hasSize(1);
        assertThat(fromImages.get(0).path("name").asText()).isEqualTo("Pink");

        List<JsonNode> invalidImages = ReflectionTestUtils.invokeMethod(support, "indexRichColorNodesByPosition", "[]", "{");
        assertThat(invalidImages).isEmpty();
    }

    @Test
    void indexRichColorNodes_indexesByNameAndFallsBackToColorImages() {
        Map<String, JsonNode> empty = ReflectionTestUtils.invokeMethod(support, "indexRichColorNodes", null, null);
        assertThat(empty).isEmpty();

        Map<String, JsonNode> named = ReflectionTestUtils.invokeMethod(
            support,
            "indexRichColorNodes",
            "[\"skip\",{\"name\":\"\"},{\"name\":\"Navy\",\"imageUrl\":\"n.jpg\"}]",
            null
        );
        assertThat(named).containsKey("navy");

        Map<String, JsonNode> fallback = ReflectionTestUtils.invokeMethod(
            support,
            "indexRichColorNodes",
            "[]",
            "{\"Teal\":[\"t.jpg\"],\"x\":[]}"
        );
        assertThat(fallback).containsKey("teal");
    }
}
