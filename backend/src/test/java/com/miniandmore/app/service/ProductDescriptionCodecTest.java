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
    void encode_includesMerchandisingPrefixesAndBody() {
        Product product = new Product()
            .fabricType("Organic Cotton")
            .sizesJson("[\"0-3M\",\"3-6M\"]")
            .colorsJson("[\"Blush\"]")
            .ageMinMonths(0)
            .ageMaxMonths(6)
            .compareAtPrice(new BigDecimal("799"))
            .description("Soft everyday wear");

        String encoded = codec.encode(product, "Babywear");

        assertThat(encoded).contains("Category: Babywear");
        assertThat(encoded).contains("Fabric: Organic Cotton");
        assertThat(encoded).contains("Sizes: 0-3M, 3-6M");
        assertThat(encoded).contains("Colors: Blush");
        assertThat(encoded).contains("Age: 0-6 months");
        assertThat(encoded).contains("CompareAt: 799");
        assertThat(encoded).contains("Soft everyday wear");
    }

    @Test
    void decode_extractsMetadataAndBody() {
        String raw =
            """
            Category: Essentials
            Fabric: Wool
            CompareAt: 599
            Age: 0-3 months

            Warm and cozy
            """;

        ProductDescriptionCodec.Decoded decoded = codec.decode(raw);

        assertThat(decoded.categoryName()).isEqualTo("Essentials");
        assertThat(decoded.fabric()).isEqualTo("Wool");
        assertThat(decoded.compareAtPrice()).isEqualByComparingTo("599");
        assertThat(decoded.ageMinMonths()).isEqualTo(0);
        assertThat(decoded.ageMaxMonths()).isEqualTo(3);
        assertThat(decoded.bodyDescription()).isEqualTo("Warm and cozy");
    }

    @Test
    void applyDecoded_setsProductFields() {
        Product product = new Product();
        ProductDescriptionCodec.Decoded decoded = new ProductDescriptionCodec.Decoded(
            "Baby",
            "Cotton",
            List.of("S"),
            List.of("Blue"),
            0,
            3,
            "Story text",
            "New",
            new BigDecimal("499"),
            "Body"
        );

        codec.applyDecoded(product, decoded);

        assertThat(product.getFabricType()).isEqualTo("Cotton");
        assertThat(codec.parseDelimitedOrJson(product.getSizesJson())).containsExactly("S");
        assertThat(codec.parseDelimitedOrJson(product.getColorsJson())).containsExactly("Blue");
        assertThat(product.getAgeMinMonths()).isEqualTo(0);
        assertThat(product.getAgeMaxMonths()).isEqualTo(3);
        assertThat(product.getStory()).isEqualTo("Story text");
        assertThat(product.getBadge()).isEqualTo("New");
        assertThat(product.getCompareAtPrice()).isEqualByComparingTo("499");
        assertThat(product.getDescription()).isEqualTo("Body");
    }

    @Test
    void parseDelimitedOrJson_acceptsCommaSeparatedText() {
        assertThat(codec.parseDelimitedOrJson("Red, Blue")).containsExactly("Red", "Blue");
    }

    @Test
    void parseDelimitedOrJson_acceptsJsonArray() {
        assertThat(codec.parseDelimitedOrJson("[\"Red\",\"Blue\"]")).containsExactly("Red", "Blue");
    }

    @Test
    void decode_extractsSizesFromSizePrefixLine() {
        ProductDescriptionCodec.Decoded decoded = codec.decode("Size: S, M, L");

        assertThat(decoded.sizes()).containsExactly("S", "M", "L");
    }

    @Test
    void decode_extractsColorsFromColorPrefixLine() {
        ProductDescriptionCodec.Decoded decoded = codec.decode("Color: Navy, Ivory");

        assertThat(decoded.colors()).containsExactly("Navy", "Ivory");
    }

    @Test
    void parseDelimitedOrJson_splitsSemicolonSeparatedValues() {
        assertThat(codec.parseDelimitedOrJson("0-3 M; 3-6 M; 6-12 M")).containsExactly("0-3 M", "3-6 M", "6-12 M");
    }
}
