package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.domain.Product;
import com.miniandmore.app.service.ProductVariantSupport.VariantRow;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.util.ReflectionTestUtils;

class ProductVariantSupportTopMissedMatrixTest {

    private ProductVariantSupport support;

    @BeforeEach
    void setUp() {
        support = StorePricingTestSupport.variantSupport(new ObjectMapper());
    }

    @ParameterizedTest
    @MethodSource("positionIndexCases")
    void positionIndex_usesOnlyUsableRichEntries(String colors, String galleries, List<String> expectedNames) {
        @SuppressWarnings("unchecked")
        List<JsonNode> indexed = ReflectionTestUtils.invokeMethod(
            support,
            "indexRichColorNodesByPosition",
            colors,
            galleries
        );

        assertThat(indexed)
            .extracting(node -> node.isTextual() ? node.asText() : node.path("name").asText())
            .containsExactlyElementsOf(expectedNames);
    }

    private static Stream<Arguments> positionIndexCases() {
        return Stream.of(
            Arguments.of("null", "{\" Plum \":[\"p.jpg\"],\"Bad\":\"x\"}", List.of("Plum")),
            Arguments.of("{}", "{\"Null\":null,\"Empty\":[],\"Good\":[\"g.jpg\"]}", List.of("Good")),
            Arguments.of(
                "[false,17,\" \",{\"name\":\"NoMedia\",\"imageUrls\":\"x\"},{\"name\":\"Hero\",\"imageUrl\":\"h.jpg\"}]",
                "{\"Fallback\":[\"f.jpg\"]}",
                List.of("Hero")
            ),
            Arguments.of(
                "[{\"name\":\"Swatch\",\"swatchUrl\":\"s.png\"},{\"name\":\"Gallery\",\"imageUrls\":[\"g.jpg\"]}]",
                "{\"Ignored\":[\"i.jpg\"]}",
                List.of("Swatch", "Gallery")
            )
        );
    }

    @ParameterizedTest
    @MethodSource("keyIndexCases")
    void keyedIndex_normalizesAndRejectsUnusableMedia(String colors, String galleries, List<String> expectedKeys) {
        @SuppressWarnings("unchecked")
        Map<String, JsonNode> indexed = ReflectionTestUtils.invokeMethod(support, "indexRichColorNodes", colors, galleries);

        assertThat(indexed.keySet()).containsExactlyElementsOf(expectedKeys);
    }

    private static Stream<Arguments> keyIndexCases() {
        return Stream.of(
            Arguments.of("null", "{\" Sea Green \":[\"g.jpg\"]}", List.of("sea green")),
            Arguments.of(
                "[null,\"Red\",7,{\"name\":\"NoMedia\",\"imageUrls\":[]},{\"name\":\"Hero\",\"imageUrl\":\"h.jpg\"}]",
                "{\" HERO \":[\"ignored.jpg\"],\"Other\":[\"o.jpg\"]}",
                List.of("hero", "other")
            ),
            Arguments.of(
                "[{\"name\":\"NullMedia\",\"imageUrl\":null,\"swatchUrl\":null,\"imageUrls\":\"bad\"}]",
                "{\"Blank\":[],\"Null\":null}",
                List.of()
            )
        );
    }

    @Test
    void freezeVariantColors_preservesFirstFrozenDuplicate_andSkipsUnmatchedRows() {
        Product product = variantProduct(
            """
            [{"sku":" A ","size":"S","color":"Incoming","rate":1,"stock":1,"active":true},
             {"sku":"B","size":"M","color":"Untouched","rate":1,"stock":1,"active":true},
             {"sku":" ","size":"L","color":"BlankSku","rate":1,"stock":1,"active":true}]
            """
        );

        support.freezeVariantColorsFromStored(
            product,
            """
            [{"sku":"A","color":" First "},{"sku":"A","color":"Second"},{"sku":"B","color":" "}]
            """
        );

        assertThat(support.findBySku(support.parseProduct(product), "A").orElseThrow().color()).isEqualTo("First");
        assertThat(support.findBySku(support.parseProduct(product), "B").orElseThrow().color()).isEqualTo("Untouched");
        assertThat(support.parseProduct(product)).anySatisfy(row -> {
            assertThat(row.sku()).isNull();
            assertThat(row.color()).isEqualTo("BlankSku");
        });
    }

    @ParameterizedTest
    @MethodSource("invalidImageSetterCases")
    void imageSetters_rejectInvalidRequests(String operation, String sku, List<String> urls) {
        Product product = variantProduct(
            """
            [{"sku":"IMG","size":"S","color":"Red","rate":1,"stock":1,"active":true}]
            """
        );
        String before = product.getVariantsJson();

        boolean result = operation.equals("single")
            ? support.setVariantImageUrl(product, sku, urls == null || urls.isEmpty() ? null : urls.get(0))
            : support.setVariantImages(product, sku, urls);

        assertThat(result).isFalse();
        assertThat(product.getVariantsJson()).isEqualTo(before);
    }

    private static Stream<Arguments> invalidImageSetterCases() {
        return Stream.of(
            Arguments.of("single", "missing", List.of("hero.jpg")),
            Arguments.of("single", " ", List.of("hero.jpg")),
            Arguments.of("multi", "IMG", null),
            Arguments.of("multi", "IMG", List.of()),
            Arguments.of("multi", " ", List.of("hero.jpg")),
            Arguments.of("multi", "missing", List.of("hero.jpg"))
        );
    }

    @Test
    void imageSetters_trimDeduplicateAndRetainOrderedExistingExtras() {
        Product product = variantProduct(
            """
            [{"sku":"IMG","size":"S","color":"Red","rate":1,"stock":1,"active":true,
              "imageUrl":"old.jpg","imageUrls":[" new.jpg ","rear.jpg","old.jpg"]}]
            """
        );

        assertThat(support.setVariantImageUrl(product, " IMG ", " new.jpg ")).isTrue();
        VariantRow row = support.findBySku(support.parseProduct(product), "IMG").orElseThrow();
        assertThat(row.imageUrl()).isEqualTo("new.jpg");
        assertThat(row.imageUrls()).containsExactly("rear.jpg");

        assertThat(support.setVariantImages(product, "IMG", List.of(" hero.jpg ", "", "rear.jpg", "hero.jpg"))).isTrue();
        row = support.findBySku(support.parseProduct(product), "IMG").orElseThrow();
        assertThat(row.imageUrl()).isEqualTo("hero.jpg");
        assertThat(row.imageUrls()).containsExactly("rear.jpg");
    }

    @ParameterizedTest
    @MethodSource("upsertCases")
    void upsertMatrix_coversExistingAndAppendDefaults(
        String initial,
        String sku,
        String size,
        String color,
        Integer stock,
        String zohoId,
        boolean addIfMissing,
        String expectedSize,
        String expectedColor,
        int expectedStock
    ) {
        Product product = variantProduct(initial);

        assertThat(
            support.upsertPulledVariantFromZoho(
                product,
                sku,
                size,
                color,
                new BigDecimal("25"),
                null,
                stock,
                zohoId,
                addIfMissing
            )
        ).isTrue();

        VariantRow row = support.findBySku(support.parseProduct(product), sku).orElseThrow();
        assertThat(row.size()).isEqualTo(expectedSize);
        assertThat(row.color()).isEqualTo(expectedColor);
        assertThat(row.stock()).isEqualTo(expectedStock);
        if (addIfMissing) {
            assertThat(row.mrp()).isEqualByComparingTo("25");
            assertThat(support.variantSellingPrice(row)).isEqualByComparingTo("20.00");
        } else {
            assertThat(row.rate()).isEqualByComparingTo("25");
        }
    }

    private static Stream<Arguments> upsertCases() {
        return Stream.of(
            Arguments.of(
                "[{\"sku\":\"OLD\",\"size\":\"S\",\"color\":\"Navy\",\"rate\":10,\"stock\":3,\"active\":false}]",
                "OLD",
                " ",
                null,
                null,
                " ",
                false,
                "S",
                "Navy",
                3
            ),
            Arguments.of("[]", "NEW", "L", "Green", -4, " z-new ", true, "L", "Green", 0)
        );
    }

    private Product variantProduct(String json) {
        Product product = new Product().sku("MATRIX").price(BigDecimal.ZERO).stock(0);
        assertThat(support.applyMatrixToProduct(product, "VARIANTS", json)).isTrue();
        return product;
    }
}
