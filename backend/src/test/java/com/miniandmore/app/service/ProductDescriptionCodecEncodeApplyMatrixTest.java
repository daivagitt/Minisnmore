package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.domain.Product;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProductDescriptionCodecEncodeApplyMatrixTest {

    private final ProductDescriptionCodec codec = new ProductDescriptionCodec(new ObjectMapper());

    @Test
    void encode_coversEveryOptionalLineAndEmptyBody() {
        Product empty = new Product();
        assertThat(codec.encode(empty, null)).isNull();
        assertThat(codec.encode(empty, "  ")).isNull();
        empty.setDescription("  body  ");
        assertThat(codec.encode(empty, null)).isEqualTo("body");

        Product full = new Product();
        full.setFabricType(" Cotton ");
        full.setSizesJson("[\"S\",\"M\"]");
        full.setColorsJson("[\"Red\"]");
        full.setAgeMinMonths(3);
        full.setAgeMaxMonths(12);
        full.setStory(" story ");
        full.setBadge(" NEW ");
        full.setCompareAtPrice(new BigDecimal("199.50"));
        full.setDescription("details");

        String encoded = codec.encode(full, " Rompers ");
        assertThat(encoded)
            .contains("Category: Rompers")
            .contains("Fabric: Cotton")
            .contains("Sizes: S, M")
            .contains("Colors: Red")
            .contains("Age: 3-12 months")
            .contains("Story: story")
            .contains("Badge: NEW")
            .contains("CompareAt: 199.5")
            .contains("details");

        Product metaOnly = new Product();
        metaOnly.setFabricType("Wool");
        metaOnly.setDescription("  ");
        assertThat(codec.encode(metaOnly, null)).isEqualTo("Fabric: Wool");

        Product zeroCompare = new Product();
        zeroCompare.setCompareAtPrice(BigDecimal.ZERO);
        zeroCompare.setAgeMinMonths(1);
        assertThat(codec.encode(zeroCompare, null)).isNull();
    }

    @Test
    void applyDecoded_coversNullBlankAndPresentFields() {
        Product product = new Product();
        codec.applyDecoded(product, null);
        assertThat(product.getFabricType()).isNull();

        ProductDescriptionCodec.Decoded blank = new ProductDescriptionCodec.Decoded(
            null,
            "  ",
            List.of(),
            List.of(),
            null,
            null,
            "  ",
            "",
            null,
            "  "
        );
        codec.applyDecoded(product, blank);
        assertThat(product.getFabricType()).isNull();
        assertThat(product.getStory()).isNull();
        assertThat(product.getDescription()).isNull();

        ProductDescriptionCodec.Decoded filled = new ProductDescriptionCodec.Decoded(
            "Cat",
            "Silk",
            List.of("S"),
            List.of("Blue"),
            2,
            8,
            "tale",
            "HOT",
            new BigDecimal("10"),
            " body "
        );
        codec.applyDecoded(product, filled);
        assertThat(product.getFabricType()).isEqualTo("Silk");
        assertThat(product.getSizesJson()).contains("S");
        assertThat(product.getColorsJson()).contains("Blue");
        assertThat(product.getAgeMinMonths()).isEqualTo(2);
        assertThat(product.getAgeMaxMonths()).isEqualTo(8);
        assertThat(product.getStory()).isEqualTo("tale");
        assertThat(product.getBadge()).isEqualTo("HOT");
        assertThat(product.getCompareAtPrice()).isEqualByComparingTo("10");
        assertThat(product.getDescription()).isEqualTo("body");

        ProductDescriptionCodec.Decoded metadataOnly = new ProductDescriptionCodec.Decoded(
            null,
            null,
            List.of(),
            List.of(),
            null,
            null,
            null,
            null,
            null,
            null
        );
        codec.applyDecoded(product, metadataOnly);
        assertThat(codec.categoryNameFrom(null)).isNull();
        assertThat(codec.categoryNameFrom(filled)).isEqualTo("Cat");
    }
}
