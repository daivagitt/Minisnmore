package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.domain.Product;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProductDescriptionCodecTest {

    private ProductDescriptionCodec codec;

    @BeforeEach
    void setUp() {
        codec = new ProductDescriptionCodec(new ObjectMapper());
    }

    @Test
    void encode_includesMerchandisingLinesAndBody() {
        Product product = new Product()
            .fabricType("100% Cotton")
            .sizesJson("[\"0-3m\",\"3-6m\"]")
            .colorsJson("[\"Blush\",\"Sand\"]")
            .ageMinMonths(0)
            .ageMaxMonths(24)
            .story("Soft organic fabric")
            .badge("Bestseller")
            .compareAtPrice(BigDecimal.valueOf(1599))
            .description("Customer-facing copy");

        String encoded = codec.encode(product, "Newborn Essentials");

        assertThat(encoded).contains("Category: Newborn Essentials");
        assertThat(encoded).contains("Fabric: 100% Cotton");
        assertThat(encoded).contains("Sizes: 0-3m, 3-6m");
        assertThat(encoded).contains("Colors: Blush, Sand");
        assertThat(encoded).contains("Age: 0-24 months");
        assertThat(encoded).contains("Story: Soft organic fabric");
        assertThat(encoded).contains("Badge: Bestseller");
        assertThat(encoded).contains("CompareAt: 1599");
        assertThat(encoded).endsWith("Customer-facing copy");
    }

    @Test
    void decode_parsesMetadataAndStripsPrefixLines() {
        String raw =
            """
            Category: Babywear
            Fabric: Linen
            Sizes: S, M
            Colors: Blue
            Age: 6-12 months
            Story: Breathable
            Badge: New
            CompareAt: 899

            Plain description
            """;

        ProductDescriptionCodec.Decoded decoded = codec.decode(raw);

        assertThat(decoded.categoryName()).isEqualTo("Babywear");
        assertThat(decoded.fabric()).isEqualTo("Linen");
        assertThat(decoded.sizes()).containsExactly("S", "M");
        assertThat(decoded.colors()).containsExactly("Blue");
        assertThat(decoded.ageMinMonths()).isEqualTo(6);
        assertThat(decoded.ageMaxMonths()).isEqualTo(12);
        assertThat(decoded.story()).isEqualTo("Breathable");
        assertThat(decoded.badge()).isEqualTo("New");
        assertThat(decoded.compareAtPrice()).isEqualByComparingTo(BigDecimal.valueOf(899));
        assertThat(decoded.bodyDescription()).isEqualTo("Plain description");
    }

    @Test
    void applyDecoded_setsProductFields() {
        Product product = new Product();
        ProductDescriptionCodec.Decoded decoded = new ProductDescriptionCodec.Decoded(
            "Cat",
            "Silk",
            List.of("L"),
            List.of("Red"),
            12,
            24,
            "Handmade",
            "Limited",
            BigDecimal.TEN,
            "Body text"
        );

        codec.applyDecoded(product, decoded);

        assertThat(product.getFabricType()).isEqualTo("Silk");
        assertThat(product.getSizesJson()).contains("L");
        assertThat(product.getColorsJson()).contains("Red");
        assertThat(product.getAgeMinMonths()).isEqualTo(12);
        assertThat(product.getAgeMaxMonths()).isEqualTo(24);
        assertThat(product.getStory()).isEqualTo("Handmade");
        assertThat(product.getBadge()).isEqualTo("Limited");
        assertThat(product.getCompareAtPrice()).isEqualByComparingTo(BigDecimal.TEN);
        assertThat(product.getDescription()).isEqualTo("Body text");
    }
}
