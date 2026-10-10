package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.domain.Product;
import com.miniandmore.app.service.ProductVariantSupport.Aggregates;
import com.miniandmore.app.service.ProductVariantSupport.VariantRow;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProductVariantSupportTest {

    private ProductVariantSupport support;

    @BeforeEach
    void setUp() {
        support = StorePricingTestSupport.variantSupport(new ObjectMapper());
    }

    @Test
    void applyMatrixToProduct_invalidNonEmptyVariantsJson_returnsFalse() {
        Product product = new Product();
        assertThat(support.applyMatrixToProduct(product, ProductVariantSupport.STRUCTURE_VARIANTS, "not-valid-json")).isFalse();
    }

    @Test
    void parseAndAggregateSparseMatrix() {
        String json =
            """
            [{"id":"a","sku":"HORIZON-S-NAVY","size":"S","color":"Navy","rate":999.0,"cost":450.0,"stock":3.0,"active":true},\
            {"id":"b","sku":"HORIZON-M-NAVY","size":"M","color":"Navy","rate":1199.0,"cost":510.0,"stock":4.0,"active":true},\
            {"id":"c","sku":"HORIZON-L-IVORY","size":"L","color":"Ivory","rate":1299.0,"cost":540.0,"stock":4.0,"active":true}]
            """;
        List<VariantRow> rows = support.parse(json);
        assertThat(rows).hasSize(3);
        assertThat(support.colorsForSize(rows, "S")).containsExactly("Navy");
        assertThat(support.colorsForSize(rows, "L")).containsExactly("Ivory");
        assertThat(support.findBySizeColor(rows, "S", "Ivory")).isEmpty();
        assertThat(support.aggregates(rows).minSelling()).isEqualByComparingTo("799.20");
        assertThat(support.aggregates(rows).minMrp()).isEqualByComparingTo("999.0");
        assertThat(support.aggregates(rows).stockSum()).isEqualTo(11);
    }

    @Test
    void applyMatrixRebuildsParentHints() {
        Product product = new Product().sku("HORIZON").price(BigDecimal.ZERO).stock(0);
        String json =
            """
            [{"sku":"HORIZON-S-NAVY","size":"S","color":"Navy","rate":999,"stock":3,"active":true},\
            {"sku":"HORIZON-M-NAVY","size":"M","color":"Navy","rate":1199,"stock":4,"active":true}]
            """;
        support.applyMatrixToProduct(product, "VARIANTS", json);
        assertThat(product.getItemStructure()).isEqualTo("VARIANTS");
        assertThat(product.getStock()).isEqualTo(7);
        assertThat(product.getCompareAtPrice()).isEqualByComparingTo("999");
        assertThat(product.getPrice()).isEqualByComparingTo("799.20");
        assertThat(product.getSizesJson()).contains("S").contains("M");
        assertThat(product.getColorsJson()).contains("Navy");
    }

    @Test
    void applyMatrix_keepsFrontAndRearGalleryUrlsForMatchingPrints() {
        Product product = new Product()
            .sku("HORIZON")
            .price(BigDecimal.ZERO)
            .stock(0)
            .colorsJson(
                """
                [{"name":"Navy","imageUrls":["https://cdn.example.com/navy-front.jpg","https://cdn.example.com/navy-rear.jpg"]}]
                """
            );
        String json =
            """
            [{"sku":"HORIZON-S-NAVY","size":"S","color":"Navy","rate":999,"stock":3,"active":true}]
            """;

        support.applyMatrixToProduct(product, "VARIANTS", json);

        assertThat(product.getColorsJson()).contains("navy-front.jpg").contains("navy-rear.jpg");
    }

    @Test
    void decrementAndResolveSellable() {
        Product product = new Product().sku("HORIZON").price(BigDecimal.ZERO).stock(0);
        String json =
            """
            [{"sku":"HORIZON-S-NAVY","size":"S","color":"Navy","rate":999,"stock":3,"active":true}]
            """;
        support.applyMatrixToProduct(product, "VARIANTS", json);
        assertThat(support.resolveSellable(product, "HORIZON-S-NAVY", "S", "Navy")).isPresent();
        assertThat(support.decrementVariantStock(product, "HORIZON-S-NAVY", "S", "Navy", 2)).isTrue();
        assertThat(support.parseProduct(product).get(0).stock()).isEqualTo(1);
        assertThat(product.getStock()).isEqualTo(1);
        assertThat(support.decrementVariantStock(product, "HORIZON-S-NAVY", "S", "Navy", 5)).isFalse();
    }

    @Test
    void resolveSellableRejectsParentSkuEvenWhenSizeColorMatch() {
        Product product = new Product().sku("HORIZON").price(BigDecimal.ZERO).stock(0);
        String json =
            """
            [{"sku":"HORIZON-S-NAVY","size":"S","color":"Navy","rate":999,"stock":3,"active":true}]
            """;
        support.applyMatrixToProduct(product, "VARIANTS", json);
        assertThat(support.resolveSellable(product, "HORIZON", "S", "Navy")).isEmpty();
        assertThat(support.resolveSellable(product, "WRONG-SKU", "S", "Navy")).isEmpty();
        assertThat(support.resolveSellable(product, null, "S", "Navy")).isPresent();
        assertThat(support.resolveSellable(product, "  ", "S", "Navy")).isPresent();
    }

    @Test
    void applyMatrixRejectsMalformedJsonWithoutWiping() {
        Product product = new Product()
            .sku("HORIZON")
            .price(BigDecimal.TEN)
            .stock(5)
            .itemStructure("VARIANTS")
            .variantsJson("[{\"sku\":\"HORIZON-S-NAVY\",\"size\":\"S\",\"color\":\"Navy\",\"rate\":999,\"stock\":3,\"active\":true}]");
        assertThat(support.applyMatrixToProduct(product, "VARIANTS", "not-json{{{")).isFalse();
        assertThat(product.getStock()).isEqualTo(5);
        assertThat(product.getVariantsJson()).contains("HORIZON-S-NAVY");
    }

    @Test
    void coerceVariantsJsonSerializesDecodedList() {
        String json = support.coerceVariantsJson(
            List.of(Map.of("sku", "A-S", "size", "S", "rate", 10, "stock", 1, "active", true))
        );
        assertThat(json).contains("\"sku\":\"A-S\"");
        assertThat(support.tryParse(json)).isPresent();
        assertThat(support.tryParse(json).orElseThrow()).hasSize(1);
    }

    @Test
    void applyMatrixPreservesVariantSwatchUrls() {
        Product product = new Product().sku("BODY-SUIT").price(BigDecimal.ZERO).stock(0);
        String swatch = "https://cdn.example.com/aqua-swatch.jpg";
        String json =
            """
            [{"sku":"BODY-SUIT-03-P1","size":"0-3 M","color":"AQUA","rate":240,"mrp":300,"stock":15,"active":true,"swatchUrl":"%s"}]
            """.formatted(swatch);
        support.applyMatrixToProduct(product, "VARIANTS", json);
        assertThat(product.getVariantsJson()).contains("swatchUrl").contains(swatch);
        List<VariantRow> rows = support.parseProduct(product);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).swatchUrl()).isEqualTo(swatch);
    }

    @Test
    void clearAllVariantImages_dropsSkuPhotosAndKeepsSwatch() {
        Product product = new Product().sku("KED").price(BigDecimal.ZERO).stock(0);
        support.applyMatrixToProduct(
            product,
            "VARIANTS",
            """
            [{"sku":"KED-BLU-0-3","size":"0-3","color":"blue","rate":380,"stock":89,"active":true,"imageUrl":"https://cdn.example.com/shot.jpg","swatchUrl":"https://cdn.example.com/chip.jpg"}]
            """
        );
        support.clearAllVariantImages(product);
        assertThat(product.getVariantsJson()).doesNotContain("shot.jpg");
        assertThat(product.getVariantsJson()).doesNotContain("imageUrl");
        assertThat(support.parseProduct(product).get(0).swatchUrl()).isEqualTo("https://cdn.example.com/chip.jpg");
    }

    @Test
    void applyMatrixPreservesSwatchUrlFromSnakeCaseField() {
        Product product = new Product().sku("BODY-SUIT").price(BigDecimal.ZERO).stock(0);
        String swatch = "https://cdn.example.com/bird-swatch.jpg";
        String json =
            """
            [{"sku":"BODY-SUIT-03-P2","size":"0-3 M","color":"BIRD","rate":240,"stock":10,"active":true,"swatch_url":"%s"}]
            """.formatted(swatch);
        support.applyMatrixToProduct(product, "VARIANTS", json);
        assertThat(support.parseProduct(product).get(0).swatchUrl()).isEqualTo(swatch);
        assertThat(product.getVariantsJson()).contains("\"swatchUrl\"").contains(swatch);
    }

    @Test
    void coerceVariantsJsonListPreservesSwatchUrlThroughApplyMatrix() {
        String swatch = "https://cdn.example.com/snail-swatch.jpg";
        String json = support.coerceVariantsJson(
            List.of(
                Map.of(
                    "sku",
                    "BODY-SUIT-03-P3",
                    "size",
                    "0-3 M",
                    "color",
                    "SNAIL",
                    "rate",
                    240,
                    "stock",
                    8,
                    "active",
                    true,
                    "swatchUrl",
                    swatch
                )
            )
        );
        Product product = new Product().sku("BODY-SUIT").price(BigDecimal.ZERO).stock(0);
        support.applyMatrixToProduct(product, "VARIANTS", json);
        assertThat(product.getVariantsJson()).contains(swatch);
        assertThat(support.decrementVariantStock(product, "BODY-SUIT-03-P3", "0-3 M", "SNAIL", 1)).isTrue();
        assertThat(support.parseProduct(product).get(0).swatchUrl()).isEqualTo(swatch);
    }

    @Test
    void neverTreatsParentAsVariantWhenSimple() {
        Product product = new Product().sku("SIMPLE-1").itemStructure("SIMPLE").price(BigDecimal.TEN).stock(5);
        assertThat(support.isVariants(product)).isFalse();
        assertThat(support.resolveSellable(product, "SIMPLE-1", null, null)).isEmpty();
    }

    @Test
    void parseDoubleEncodedJsonAndInactiveRows() throws Exception {
        String inner =
            "[{\"sku\":\"A-S\",\"size\":\"S\",\"color\":\"Red\",\"rate\":100,\"stock\":2,\"active\":false},{\"sku\":\"A-M\",\"size\":\"M\",\"color\":\"Blue\",\"rate\":120,\"stock\":3,\"active\":true}]";
        String wrapped = new ObjectMapper().writeValueAsString(inner);
        List<VariantRow> rows = support.parse(wrapped);
        assertThat(rows).hasSize(2);
        assertThat(support.colors(rows)).containsExactly("Blue");
        assertThat(support.sizes(rows)).contains("S", "M");
    }

    @Test
    void renameVariantColorsUpdatesJson() {
        Product product = new Product().sku("P").price(BigDecimal.TEN).stock(5);
        String json =
            """
            [{"sku":"P-S-OLD","size":"S","color":"OldName","rate":100,"stock":1,"active":true}]
            """;
        support.applyMatrixToProduct(product, "VARIANTS", json);
        support.renameVariantColors(product, Map.of("OldName", "NewName"));
        assertThat(support.parseProduct(product).get(0).color()).isEqualTo("NewName");
    }

    @Test
    void variantSellingPriceUsesMrpWhenRateMissing() {
        VariantRow row = VariantRow.fromMap(
            Map.of("sku", "X", "size", "S", "mrp", 200, "stock", 1, "active", true)
        );
        assertThat(support.variantSellingPrice(row)).isEqualByComparingTo("160.00");
    }

    @Test
    void applyMatrixToSimpleClearsVariantsJson() {
        Product product = new Product().sku("S").itemStructure("SIMPLE").variantsJson("[]").price(BigDecimal.TEN).stock(1);
        assertThat(support.applyMatrixToProduct(product, "SIMPLE", "[]")).isTrue();
        assertThat(product.getVariantsJson()).isNull();
    }

    @Test
    void coerceVariantsJsonReturnsStringAsIs() {
        assertThat(support.coerceVariantsJson("[{\"sku\":\"A\"}]")).isEqualTo("[{\"sku\":\"A\"}]");
        assertThat(support.coerceVariantsJson(null)).isNull();
    }

    @Test
    void incrementVariantStock_bySkuAndSizeColor() {
        Product product = variantProduct(
            """
            [{"sku":"HORIZON-S-NAVY","size":"S","color":"Navy","rate":999,"stock":2,"active":true}]
            """
        );

        assertThat(support.incrementVariantStock(product, "HORIZON-S-NAVY", null, null, 3)).isTrue();
        assertThat(support.parseProduct(product).get(0).stock()).isEqualTo(5);
        assertThat(product.getStock()).isEqualTo(5);

        assertThat(support.incrementVariantStock(product, null, "S", "Navy", 1)).isTrue();
        assertThat(support.parseProduct(product).get(0).stock()).isEqualTo(6);
    }

    @Test
    void incrementVariantStock_rejectsInvalidQtyOrMissingRow() {
        Product product = variantProduct(
            """
            [{"sku":"HORIZON-S-NAVY","size":"S","color":"Navy","rate":999,"stock":2,"active":true}]
            """
        );

        assertThat(support.incrementVariantStock(product, "MISSING", "XL", "Ivory", 1)).isFalse();
        assertThat(support.incrementVariantStock(product, "HORIZON-S-NAVY", "S", "Navy", 0)).isFalse();
        assertThat(support.incrementVariantStock(new Product().itemStructure("SIMPLE"), "HORIZON-S-NAVY", "S", "Navy", 1)).isFalse();
    }

    @Test
    void upsertPulledVariantFromZoho_rejectsNegativeInboundStock() {
        Product product = variantProduct(
            """
            [{"sku":"HORIZON-S-NAVY","size":"S","color":"Navy","rate":999,"stock":4,"active":true}]
            """
        );

        assertThatThrownBy(() ->
            support.upsertPulledVariantFromZoho(product, "HORIZON-S-NAVY", "S", "Navy", null, null, -1, "zoho-1", true)
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Negative Zoho stock (-1)");
        assertThat(support.parseProduct(product).get(0).stock()).isEqualTo(4);
    }

    @Test
    void updateVariantStockBySku_clampsNegativeAndReaggregates() {
        Product product = variantProduct(
            """
            [{"sku":"HORIZON-S-NAVY","size":"S","color":"Navy","rate":999,"stock":4,"active":true},\
            {"sku":"HORIZON-M-NAVY","size":"M","color":"Navy","rate":999,"stock":6,"active":true}]
            """
        );

        assertThat(support.updateVariantStockBySku(product, "HORIZON-S-NAVY", -5)).isTrue();
        assertThat(support.parseProduct(product).get(0).stock()).isZero();
        assertThat(product.getStock()).isEqualTo(6);

        assertThat(support.updateVariantStockBySku(product, "UNKNOWN", 10)).isFalse();
    }

    @Test
    void decrementVariantStock_bySizeColorWhenSkuMissing() {
        Product product = variantProduct(
            """
            [{"sku":"HORIZON-S-NAVY","size":"S","color":"Navy","rate":999,"stock":3,"active":true}]
            """
        );

        assertThat(support.decrementVariantStock(product, null, "S", "Navy", 1)).isTrue();
        assertThat(support.parseProduct(product).get(0).stock()).isEqualTo(2);
    }

    @Test
    void decrementVariantStock_rejectsInactiveVariant() {
        Product product = variantProduct(
            """
            [{"sku":"HORIZON-S-NAVY","size":"S","color":"Navy","rate":999,"stock":3,"active":false}]
            """
        );

        assertThat(support.decrementVariantStock(product, "HORIZON-S-NAVY", "S", "Navy", 1)).isFalse();
    }

    @Test
    void findProductContainingVariantSku_matchesParent() {
        Product variants = variantProduct(
            """
            [{"sku":"HORIZON-S-NAVY","size":"S","color":"Navy","rate":999,"stock":1,"active":true}]
            """
        );
        Product simple = new Product().sku("SIMPLE").itemStructure("SIMPLE");

        assertThat(support.findProductContainingVariantSku(List.of(simple, variants), "HORIZON-S-NAVY")).contains(variants);
        assertThat(support.findProductContainingVariantSku(List.of(simple), "HORIZON-S-NAVY")).isEmpty();
        assertThat(support.findProductContainingVariantSku(List.of(variants), "  ")).isEmpty();
    }

    @Test
    void findBySku_isCaseInsensitive() {
        List<VariantRow> rows = support.parse(
            """
            [{"sku":"ABC-S","size":"S","color":"Red","rate":100,"stock":1,"active":true}]
            """
        );

        assertThat(support.findBySku(rows, "abc-s")).isPresent();
        assertThat(support.findBySku(rows, null)).isEmpty();
    }

    @Test
    void variantRow_fromMap_handlesLegacyRateAsMrpAndExplicitPrice() {
        VariantRow legacy = VariantRow.fromMap(
            Map.of("sku", "L", "size", "S", "rate", 100, "stock", 1, "active", true)
        );
        assertThat(legacy.mrp()).isEqualByComparingTo("100");
        assertThat(legacy.rate()).isEqualByComparingTo("0");

        VariantRow explicit = VariantRow.fromMap(
            Map.of("sku", "E", "size", "S", "rate", 50, "price", 75, "mrp", 90, "stock", 1, "active", true)
        );
        assertThat(explicit.rate()).isEqualByComparingTo("75");
        assertThat(explicit.mrp()).isEqualByComparingTo("90");
    }

    @Test
    void variantRow_toMap_includesPopulatedFields() {
        VariantRow row = new VariantRow(
            "id-1",
            "SKU-1",
            "M",
            "Blue",
            new BigDecimal("80.00"),
            new BigDecimal("100"),
            new BigDecimal("40"),
            7,
            true,
            "https://cdn.example.com/swatch.jpg",
            "zoho-99",
            "https://cdn.example.com/sku.jpg",
            List.of()
        );

        Map<String, Object> map = row.toMap();

        assertThat(map)
            .containsEntry("id", "id-1")
            .containsEntry("sku", "SKU-1")
            .containsEntry("size", "M")
            .containsEntry("color", "Blue")
            .containsEntry("stock", 7)
            .containsEntry("active", true)
            .containsEntry("swatchUrl", "https://cdn.example.com/swatch.jpg")
            .containsEntry("zohoItemId", "zoho-99")
            .containsEntry("imageUrl", "https://cdn.example.com/sku.jpg");
        assertThat(map.get("rate")).isEqualTo(new BigDecimal("80.00"));
        assertThat(map.get("mrp")).isEqualTo(new BigDecimal("100"));
    }

    @Test
    void storefrontPrice_appliesTwentyPercentDiscount() {
        assertThat(support.storefrontPrice(new BigDecimal("100"))).isEqualByComparingTo("80.00");
        assertThat(support.storefrontPrice(BigDecimal.ZERO)).isEqualByComparingTo("0");
        assertThat(support.storefrontPrice(null)).isEqualByComparingTo("0");
    }

    @Test
    void applyRuntimePricing_variantDtoUsesMinVariantMrp() {
        com.miniandmore.app.service.dto.ProductDTO dto = new com.miniandmore.app.service.dto.ProductDTO();
        dto.setItemStructure("VARIANTS");
        dto.setPrice(new BigDecimal("1"));
        dto.setVariantsJson(
            """
            [{"sku":"A","size":"S","color":"Red","rate":0,"mrp":400,"stock":2,"active":true},\
            {"sku":"B","size":"M","color":"Blue","rate":0,"mrp":500,"stock":3,"active":true}]
            """
        );
        support.applyRuntimePricing(dto);
        assertThat(dto.getCompareAtPrice()).isEqualByComparingTo("400");
        assertThat(dto.getPrice()).isEqualByComparingTo("320.00");
    }

    @Test
    void aggregates_ignoresInactiveRowsAndNegativeStock() {
        List<VariantRow> rows = support.parse(
            """
            [{"sku":"A","size":"S","color":"Red","rate":0,"mrp":0,"cost":0,"stock":-2,"active":true},\
            {"sku":"B","size":"M","color":"Blue","rate":200,"mrp":250,"cost":80,"stock":5,"active":false},\
            {"sku":"C","size":"L","color":"Green","rate":150,"mrp":180,"cost":60,"stock":4,"active":true}]
            """
        );

        Aggregates agg = support.aggregates(rows);

        assertThat(agg.minSelling()).isEqualByComparingTo("144.00");
        assertThat(agg.minMrp()).isEqualByComparingTo("180");
        assertThat(agg.minCost()).isEqualByComparingTo("60");
        assertThat(agg.stockSum()).isEqualTo(4);
    }

    @Test
    void resolveSellable_rejectsInactiveVariant() {
        Product product = variantProduct(
            """
            [{"sku":"HORIZON-S-NAVY","size":"S","color":"Navy","rate":999,"stock":3,"active":false}]
            """
        );

        assertThat(support.resolveSellable(product, null, "S", "Navy")).isEmpty();
        assertThat(support.resolveSellable(product, "HORIZON-S-NAVY", "S", "Navy")).isEmpty();
    }

    @Test
    void serialize_emptyRowsReturnsEmptyArray() {
        assertThat(support.serialize(List.of())).isEqualTo("[]");
        assertThat(support.serialize(null)).isEqualTo("[]");
    }

    @Test
    void applyMatrix_defaultsNullStructureToSimple() {
        Product product = new Product().sku("S").variantsJson("[]").price(BigDecimal.TEN).stock(1);

        assertThat(support.applyMatrixToProduct(product, null, null)).isTrue();
        assertThat(product.getItemStructure()).isEqualTo("SIMPLE");
        assertThat(product.getVariantsJson()).isNull();
    }

    @Test
    void applyMatrix_keepsGalleriesFromColorImagesJson() {
        Product product = new Product()
            .sku("HORIZON")
            .price(BigDecimal.ZERO)
            .stock(0)
            .colorImagesJson(
                """
                {"Navy":["https://cdn.example.com/navy-front.jpg","https://cdn.example.com/navy-rear.jpg"]}
                """
            );
        String json =
            """
            [{"sku":"HORIZON-S-NAVY","size":"S","color":"Navy","rate":999,"stock":3,"active":true}]
            """;

        support.applyMatrixToProduct(product, "VARIANTS", json);

        assertThat(product.getColorsJson()).contains("navy-front.jpg").contains("navy-rear.jpg");
    }

    @Test
    void updateVariantStockBySku_updatesMatchingRow() {
        Product product = variantProduct(
            """
            [{"sku":"HORIZON-S-NAVY","size":"S","color":"Navy","rate":999,"stock":3,"active":true}]
            """
        );

        assertThat(support.updateVariantStockBySku(product, "HORIZON-S-NAVY", 9)).isTrue();
        assertThat(support.parseProduct(product).get(0).stock()).isEqualTo(9);
    }

    @Test
    void pulledVariantRejectsSameSizeColorForDifferentIdentityWithoutChangingParent() {
        Product product = variantProduct("[{\"sku\":\"LOCAL-S\",\"size\":\"S\",\"color\":\"Navy\",\"rate\":100,\"stock\":3,\"active\":true,\"zohoItemId\":\"local-id\"}]");
        String before = product.getVariantsJson();
        Integer stock = product.getStock();
        assertThatThrownBy(() -> support.upsertPulledVariantFromZoho(product, "REMOTE-S", " s ", " navy ", BigDecimal.TEN, null, 99, "remote-id", true))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("size/color conflict").hasMessageContaining("LOCAL-S");
        assertThat(product.getVariantsJson()).isEqualTo(before);
        assertThat(product.getStock()).isEqualTo(stock);
    }

    @Test
    void pulledVariantRejectsMovingExistingIdentityIntoAnotherRowsCombination() {
        Product product = variantProduct("[{\"sku\":\"LOCAL-S\",\"size\":\"S\",\"color\":\"Navy\",\"rate\":100,\"stock\":3,\"active\":true,\"zohoItemId\":\"id-s\"},{\"sku\":\"LOCAL-M\",\"size\":\"M\",\"color\":\"Navy\",\"rate\":100,\"stock\":3,\"active\":true,\"zohoItemId\":\"id-m\"}]");
        String before = product.getVariantsJson();
        assertThatThrownBy(() -> support.upsertPulledVariantFromZoho(product, "LOCAL-M", "S", "Navy", BigDecimal.TEN, null, 99, "id-m", true))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("size/color conflict");
        assertThat(product.getVariantsJson()).isEqualTo(before);
        assertThatThrownBy(() -> support.upsertPulledVariantFromZoho(product, "LOCAL-S", "M", "Navy", BigDecimal.TEN, null, 99, "id-m", true))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("identity conflict");
        assertThat(product.getVariantsJson()).isEqualTo(before);
    }

    @Test
    void pulledVariantDoesNotReuseInactiveRowsCombination() {
        Product product = variantProduct("[{\"sku\":\"OLD-S\",\"size\":\"S\",\"color\":\"Navy\",\"rate\":100,\"stock\":0,\"active\":false}]");
        assertThatThrownBy(() -> support.upsertPulledVariantFromZoho(product, "NEW-S", "S", "Navy", BigDecimal.TEN, null, 1, "new-id", true))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("size/color conflict");
    }

    @Test
    void pulledVariantSameCombinationOnAnotherParentIsIndependent() {
        Product product = variantProduct("[{\"sku\":\"LOCAL-S\",\"size\":\"S\",\"color\":\"Navy\",\"rate\":100,\"stock\":3,\"active\":true}]");
        Product other = variantProduct("[]");
        assertThat(support.upsertPulledVariantFromZoho(other, "OTHER-S", "S", "Navy", BigDecimal.TEN, null, 1, "other-id", true)).isTrue();
        assertThat(support.parseProduct(product)).hasSize(1);
    }

    @Test
    void freezeVariantColorsFromStored_restoresStoredLabelsPerSku() {
        Product product = variantProduct(
            """
            [{"sku":"SKU-AQUA-S","size":"0-3m","color":"Manual Blue","rate":999,"stock":2,"active":true}]
            """
        );
        String frozen =
            """
            [{"sku":"SKU-AQUA-S","size":"0-3m","color":"AQUA","rate":999,"stock":2,"active":true}]
            """;

        support.freezeVariantColorsFromStored(product, frozen);

        assertThat(support.parseProduct(product).get(0).color()).isEqualTo("AQUA");
    }

    @Test
    void upsertPulledVariantFromZoho_overridesFrozenColorNames() {
        Product product = variantProduct(
            """
            [{"sku":"SKU-AQUA-S","size":"0-3m","color":"AQUA","rate":999,"stock":2,"active":true}]
            """
        );
        product.setColorsJson("[{\"name\":\"AQUA\",\"swatchUrl\":\"https://cdn.example.com/aqua.jpg\"}]");

        assertThat(
            support.upsertPulledVariantFromZoho(
                product,
                "SKU-AQUA-S",
                "0-3m",
                "Baby Blue",
                BigDecimal.valueOf(999),
                null,
                2,
                "zoho-aqua",
                false
            )
        ).isTrue();

        assertThat(support.parseProduct(product).get(0).color()).isEqualTo("Baby Blue");
        assertThat(product.getColorsJson()).contains("Baby Blue");
    }

    @Test
    void upsertPulledVariantFromZoho_appendsMissingSkuOntoParent() {
        Product product = variantProduct(
            """
            [{"sku":"HORIZON-S-NAVY","size":"S","color":"Navy","rate":799.20,"stock":1,"active":true}]
            """
        );

        assertThat(
            support.upsertPulledVariantFromZoho(
                product,
                "HORIZON-M-NAVY",
                "M",
                "Navy",
                BigDecimal.valueOf(959.20),
                BigDecimal.valueOf(1199),
                4,
                "zoho-m",
                true
            )
        ).isTrue();
        assertThat(support.parseProduct(product)).hasSize(2);
        assertThat(support.findBySku(support.parseProduct(product), "HORIZON-M-NAVY")).isPresent();
        assertThat(product.getStock()).isEqualTo(5);
        assertThat(product.getItemStructure()).isEqualTo("VARIANTS");
        assertThat(
            support.upsertPulledVariantFromZoho(
                product,
                "MISSING-SKU",
                "L",
                "Navy",
                BigDecimal.ONE,
                BigDecimal.TEN,
                1,
                "zoho-x",
                false
            )
        ).isFalse();
    }

    @Test
    void deactivateVariantsNotInPulledSet_marksMissingRowsInactive() {
        Product product = variantProduct(
            """
            [{"sku":"KEEP-ME","size":"S","color":"Navy","rate":10,"stock":1,"active":true},\
            {"sku":"DROP-ME","size":"S","color":"Pink","rate":10,"stock":2,"active":true}]
            """
        );

        assertThat(support.deactivateVariantsNotInPulledSet(product, Set.of("KEEP-ME"))).isEqualTo(1);
        assertThat(support.findBySku(support.parseProduct(product), "KEEP-ME").orElseThrow().active()).isTrue();
        assertThat(support.findBySku(support.parseProduct(product), "DROP-ME").orElseThrow().active()).isFalse();
        assertThat(product.getStock()).isEqualTo(1);
    }

    @Test
    void upsertPulledVariantFromZoho_reactivatesPreviouslyInactiveRow() {
        Product product = variantProduct(
            """
            [{"sku":"HORIZON-S-NAVY","size":"S","color":"Navy","rate":10,"stock":0,"active":false,"zohoItemId":"zoho-s"}]
            """
        );

        assertThat(
            support.upsertPulledVariantFromZoho(
                product,
                "HORIZON-S-NAVY",
                "S",
                "Navy",
                BigDecimal.TEN,
                BigDecimal.TEN,
                3,
                "zoho-s",
                true
            )
        ).isTrue();
        assertThat(support.findBySku(support.parseProduct(product), "HORIZON-S-NAVY").orElseThrow().active()).isTrue();
        assertThat(product.getStock()).isEqualTo(3);
    }

    @Test
    void incrementVariantStock_restoresParentAggregate() {
        Product product = variantProduct(
            """
            [{"sku":"HORIZON-S-NAVY","size":"S","color":"Navy","rate":999,"stock":1,"active":true}]
            """
        );
        support.decrementVariantStock(product, "HORIZON-S-NAVY", "S", "Navy", 1);

        assertThat(support.incrementVariantStock(product, "HORIZON-S-NAVY", "S", "Navy", 1)).isTrue();
        assertThat(product.getStock()).isEqualTo(1);
    }

    @Test
    void coerceVariantsJson_fromListAndInvalidObject() {
        assertThat(support.coerceVariantsJson(List.of(Map.of("sku", "A", "size", "S", "color", "Red", "rate", 1, "stock", 1, "active", true))))
            .contains("A");
        assertThat(support.coerceVariantsJson(42)).isEqualTo("42");
    }

    @Test
    void tryParse_invalidJson_returnsEmpty() {
        assertThat(support.parse("not-json")).isEmpty();
    }

    @Test
    void variantSellingPrice_usesMrpAtRuntime() {
        VariantRow row = new VariantRow("id", "SKU", "M", "Blue", new BigDecimal("80"), new BigDecimal("100"), null, 1, true, null, null, null, List.of());
        assertThat(support.variantSellingPrice(row)).isEqualByComparingTo("80.00");
    }

    @Test
    void colorsForSize_emptyWhenNoMatch() {
        List<VariantRow> rows = support.parse(
            """
            [{"sku":"A","size":"S","color":"Red","rate":10,"stock":1,"active":true}]
            """
        );
        assertThat(support.colorsForSize(rows, "XL")).isEmpty();
    }

    @Test
    void applyMatrix_invalidJson_returnsFalse() {
        Product product = new Product().sku("BAD").price(BigDecimal.TEN).stock(1);
        assertThat(support.applyMatrixToProduct(product, "VARIANTS", "not-json")).isFalse();
    }

    @Test
    void upsertPulledVariant_matchesStableZohoIdAcrossSkuRenameAndAcceptsZeroPrices() {
        Product product = variantProduct(
            """
            [{"sku":"OLD-SKU","size":"S","color":"Navy","rate":100,"mrp":120,"stock":1,"active":false,"zohoItemId":"zoho-stable"}]
            """
        );

        assertThat(
            support.upsertPulledVariantFromZoho(
                product,
                "NEW-SKU",
                "S",
                "Navy",
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                4,
                "zoho-stable",
                true
            )
        ).isTrue();

        assertThat(support.parseProduct(product)).hasSize(1);
        VariantRow renamed = support.findByZohoItemId(support.parseProduct(product), "zoho-stable").orElseThrow();
        assertThat(renamed.sku()).isEqualTo("NEW-SKU");
        assertThat(renamed.rate()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(renamed.mrp()).isNull();
        assertThat(renamed.stock()).isEqualTo(4);
        assertThat(renamed.active()).isTrue();
    }

    @Test
    void fullPullReconciliation_deactivatesOnlyMissingZohoLinkedRows() {
        Product product = variantProduct(
            """
            [{"sku":"LOCAL-ONLY","size":"S","color":"Navy","rate":10,"stock":2,"active":true},
             {"sku":"LINKED-MISSING","size":"M","color":"Navy","rate":10,"stock":3,"active":true,"zohoItemId":"zoho-missing"}]
            """
        );

        assertThat(support.deactivateVariantsNotInPulledSet(product, Set.of(), Set.of())).isEqualTo(1);
        assertThat(support.findBySku(support.parseProduct(product), "LOCAL-ONLY").orElseThrow().active()).isTrue();
        assertThat(support.findBySku(support.parseProduct(product), "LINKED-MISSING").orElseThrow().active()).isFalse();
        assertThat(support.hasActiveVariants(product)).isTrue();
        assertThat(product.getStock()).isEqualTo(2);
    }

    @Test
    void fullPullReconciliation_matchesSkuCaseInsensitively() {
        Product product = variantProduct(
            """
            [{"sku":"Mixed-Case-Sku","size":"S","color":"Navy","rate":10,"stock":2,"active":true,"zohoItemId":"zoho-case"}]
            """
        );

        assertThat(support.deactivateVariantsNotInPulledSet(product, Set.of("mixed-case-sku"), Set.of())).isZero();
        assertThat(support.findBySku(support.parseProduct(product), "MIXED-CASE-SKU").orElseThrow().active()).isTrue();
    }

    @Test
    void updateVariantStockByZohoItemId_handlesSkuRenameDuringInventoryOnlyPull() {
        Product product = variantProduct(
            """
            [{"sku":"OLD-SKU","size":"S","color":"Navy","rate":10,"stock":2,"active":true,"zohoItemId":"zoho-stable"}]
            """
        );

        assertThat(support.updateVariantStockByZohoItemId(product, "zoho-stable", 7)).isTrue();
        assertThat(support.findByZohoItemId(support.parseProduct(product), "zoho-stable").orElseThrow().stock()).isEqualTo(7);
        assertThat(product.getStock()).isEqualTo(7);
    }

    @Test
    void variantSellingPrice_nullRow_returnsZero() {
        assertThat(support.variantSellingPrice(null)).isEqualByComparingTo("0");
    }

    @Test
    void variantSellingPrice_withoutRateOrMrp_returnsZero() {
        VariantRow row = new VariantRow("id", "SKU", "M", "Blue", null, null, null, 1, true, null, null, null, List.of());
        assertThat(support.variantSellingPrice(row)).isEqualByComparingTo("0");
    }

    @Test
    void storefrontPrice_nullOrNonPositive_returnsZero() {
        assertThat(support.storefrontPrice(null)).isEqualByComparingTo("0");
        assertThat(support.storefrontPrice(BigDecimal.ZERO)).isEqualByComparingTo("0");
    }

    @Test
    void applyMatrix_simpleStructure_clearsVariantsJson() {
        Product product = new Product().sku("SIMPLE").variantsJson("[{\"sku\":\"X\"}]");
        assertThat(support.applyMatrixToProduct(product, "SIMPLE", null)).isTrue();
        assertThat(product.getVariantsJson()).isNull();
    }

    @Test
    void mergeColorsPreservingGalleries_nullColorNames_returnsPreviousJson() throws Exception {
        String previous = "[{\"name\":\"Navy\"}]";
        assertThat(support.mergeColorsPreservingGalleries(previous, List.of())).isEqualTo(previous);
        assertThat(support.mergeColorsPreservingGalleries(null, null)).isEqualTo("[]");
    }

    @Test
    void decrementVariantStock_unknownVariant_returnsFalse() {
        Product product = variantProduct(
            """
            [{"sku":"HORIZON-S-NAVY","size":"S","color":"Navy","rate":999,"stock":1,"active":true}]
            """
        );
        assertThat(support.decrementVariantStock(product, "MISSING", "XL", "Green", 1)).isFalse();
    }

    @Test
    void tryParse_skipsNullRowsInArray() {
        Optional<List<VariantRow>> parsed = support.tryParse("[null,{\"sku\":\"A\",\"size\":\"S\",\"color\":\"Red\",\"rate\":10,\"stock\":1,\"active\":true}]");
        assertThat(parsed).isPresent();
        assertThat(parsed.orElseThrow()).hasSize(1);
        assertThat(parsed.orElseThrow().get(0).sku()).isEqualTo("A");
    }

    @Test
    void coerceVariantsJson_serializesDecodedList() {
        List<Map<String, Object>> rows = List.of(Map.of("sku", "A", "size", "S", "color", "Red", "rate", 10, "stock", 1, "active", true));
        assertThat(support.coerceVariantsJson(rows)).contains("\"sku\":\"A\"");
    }

    @Test
    void colorsForSize_returnsActiveColorsForSize() {
        List<VariantRow> rows = support.parse(
            """
            [{"sku":"A","size":"S","color":"Red","rate":10,"stock":1,"active":true},
             {"sku":"B","size":"M","color":"Blue","rate":10,"stock":1,"active":true},
             {"sku":"C","size":"S","color":"Green","rate":10,"stock":1,"active":false}]
            """
        );
        assertThat(support.colorsForSize(rows, "S")).containsExactly("Red");
        assertThat(support.colorsForSize(rows, "XL")).isEmpty();
    }

    @Test
    void findBySku_nullSku_returnsEmpty() {
        assertThat(support.findBySku(support.parse("[]"), null)).isEmpty();
        assertThat(support.findBySku(support.parse("[]"), "  ")).isEmpty();
    }

    @Test
    void incrementVariantStock_unknownVariant_returnsFalse() {
        Product product = variantProduct(
            """
            [{"sku":"HORIZON-S-NAVY","size":"S","color":"Navy","rate":999,"stock":1,"active":true}]
            """
        );
        assertThat(support.incrementVariantStock(product, "MISSING", "XL", "Green", 1)).isFalse();
    }

    @Test
    void aggregates_skipsInactiveRows() {
        List<VariantRow> rows = support.parse(
            """
            [{"sku":"A","size":"S","color":"Red","rate":10,"stock":5,"active":false},
             {"sku":"B","size":"M","color":"Blue","rate":20,"stock":2,"active":true}]
            """
        );
        Aggregates agg = support.aggregates(rows);
        assertThat(agg.stockSum()).isEqualTo(2);
        assertThat(agg.minSelling()).isEqualByComparingTo("16.00");
    }

    @Test
    void coerceVariantsJson_null_returnsNull() {
        assertThat(support.coerceVariantsJson(null)).isNull();
    }

    @Test
    void normalize_trimsWhitespace() {
        assertThat(ProductVariantSupport.normalize("  Variants  ")).isEqualTo("Variants");
        assertThat(ProductVariantSupport.normalize(null)).isNull();
    }

    @Test
    void setVariantZohoItemId_andLookup() {
        Product product = variantProduct(
            """
            [{"sku":"HORIZON-S-NAVY","size":"S","color":"Navy","rate":999,"stock":1,"active":true}]
            """
        );
        assertThat(support.setVariantZohoItemId(product, "HORIZON-S-NAVY", "zoho-1")).isTrue();
        assertThat(support.zohoItemIdForSku(product, "HORIZON-S-NAVY")).contains("zoho-1");
    }

    @Test
    void clearVariantImages_clearsGallery() {
        Product product = variantProduct(
            """
            [{"sku":"HORIZON-S-NAVY","size":"S","color":"Navy","rate":999,"stock":1,"active":true,"imageUrl":"https://cdn.example.com/a.jpg"}]
            """
        );
        assertThat(support.clearVariantImages(product, "HORIZON-S-NAVY")).isTrue();
        assertThat(support.parseProduct(product).get(0).imageUrl()).isNull();
    }

    @Test
    void hasZohoLinkedVariants_detectsLinkedRows() {
        Product product = variantProduct(
            """
            [{"sku":"HORIZON-S-NAVY","size":"S","color":"Navy","rate":999,"stock":1,"active":true,"zohoItemId":"zoho-1"}]
            """
        );
        assertThat(support.hasZohoLinkedVariants(product)).isTrue();
    }

    @Test
    void variantHasSizeAndColor_requiresBothFields() {
        Product product = variantProduct(
            """
            [{"sku":"HORIZON-S-NAVY","size":"S","color":"Navy","rate":999,"stock":1,"active":true}]
            """
        );
        assertThat(support.variantHasSizeAndColor(product, "HORIZON-S-NAVY")).isTrue();
    }

    @Test
    void findProductContainingVariantSku_returnsMatch() {
        Product product = variantProduct(
            """
            [{"sku":"HORIZON-S-NAVY","size":"S","color":"Navy","rate":999,"stock":1,"active":true}]
            """
        );
        assertThat(support.findProductContainingVariantSku(List.of(product), "HORIZON-S-NAVY")).contains(product);
    }

    @Test
    void renameVariantColors_updatesMatrix() {
        Product product = variantProduct(
            """
            [{"sku":"HORIZON-S-NAVY","size":"S","color":"Navy","rate":999,"stock":1,"active":true}]
            """
        );
        support.renameVariantColors(product, Map.of("Navy", "Midnight"));
        assertThat(support.parseProduct(product).get(0).color()).isEqualTo("Midnight");
    }

    @Test
    void deactivateVariantBySku_marksInactive() {
        Product product = variantProduct(
            """
            [{"sku":"HORIZON-S-NAVY","size":"S","color":"Navy","rate":999,"stock":1,"active":true}]
            """
        );
        assertThat(support.deactivateVariantBySku(product, "HORIZON-S-NAVY")).isTrue();
        assertThat(support.parseProduct(product).get(0).active()).isFalse();
    }

    @Test
    void deactivateVariantByZohoItemId_marksInactive() {
        Product product = variantProduct(
            """
            [{"sku":"HORIZON-S-NAVY","size":"S","color":"Navy","rate":999,"stock":1,"active":true,"zohoItemId":"zoho-9"}]
            """
        );
        assertThat(support.deactivateVariantByZohoItemId(product, "zoho-9")).isTrue();
        assertThat(support.parseProduct(product).get(0).active()).isFalse();
    }

    @Test
    void findProductContainingVariantZohoItemId_returnsMatch() {
        Product product = variantProduct(
            """
            [{"sku":"HORIZON-S-NAVY","size":"S","color":"Navy","rate":999,"stock":1,"active":true,"zohoItemId":"zoho-9"}]
            """
        );
        assertThat(support.findProductContainingVariantZohoItemId(List.of(product), "zoho-9")).contains(product);
    }

    @Test
    void setVariantImages_updatesGallery() {
        Product product = variantProduct(
            """
            [{"sku":"HORIZON-S-NAVY","size":"S","color":"Navy","rate":999,"stock":1,"active":true}]
            """
        );
        assertThat(
            support.setVariantImages(product, "HORIZON-S-NAVY", List.of("https://cdn.example.com/a.jpg", "https://cdn.example.com/b.jpg"))
        ).isTrue();
        VariantRow row = support.parseProduct(product).get(0);
        assertThat(row.imageUrl()).isEqualTo("https://cdn.example.com/a.jpg");
        assertThat(row.imageUrls()).contains("https://cdn.example.com/b.jpg");
    }

    @Test
    void anyVariantSkuInSet_detectsMembership() {
        Product product = variantProduct(
            """
            [{"sku":"HORIZON-S-NAVY","size":"S","color":"Navy","rate":999,"stock":1,"active":true}]
            """
        );
        assertThat(support.anyVariantSkuInSet(product, Set.of("HORIZON-S-NAVY"))).isTrue();
        assertThat(support.anyVariantSkuInSet(product, Set.of("OTHER"))).isFalse();
    }

    @Test
    void hasActiveVariants_falseWhenAllInactive() {
        Product product = variantProduct(
            """
            [{"sku":"HORIZON-S-NAVY","size":"S","color":"Navy","rate":999,"stock":1,"active":false}]
            """
        );
        assertThat(support.hasActiveVariants(product)).isFalse();
    }

    @Test
    void setVariantImageUrl_mergesExistingAndNewImages() {
        Product product = variantProduct(
            """
            [{"sku":"HORIZON-S-NAVY","size":"S","color":"Navy","rate":999,"stock":1,"active":true,"imageUrl":"https://cdn.example.com/old1.jpg","imageUrls":["https://cdn.example.com/old2.jpg"]}]
            """
        );
        assertThat(support.setVariantImageUrl(null, "SKU", "https://cdn.example.com/new.jpg")).isFalse();
        assertThat(support.setVariantImageUrl(product, " ", "https://cdn.example.com/new.jpg")).isFalse();
        assertThat(support.setVariantImageUrl(product, "HORIZON-S-NAVY", " ")).isFalse();

        assertThat(support.setVariantImageUrl(product, "HORIZON-S-NAVY", "https://cdn.example.com/new.jpg")).isTrue();
        VariantRow row = support.parseProduct(product).get(0);
        assertThat(row.imageUrl()).isEqualTo("https://cdn.example.com/new.jpg");
        assertThat(row.imageUrls()).contains("https://cdn.example.com/old2.jpg");
    }

    @Test
    void clearVariantImages_removesImages() {
        Product product = variantProduct(
            """
            [{"sku":"HORIZON-S-NAVY","size":"S","color":"Navy","rate":999,"stock":1,"active":true,"imageUrl":"https://cdn.example.com/old1.jpg"}]
            """
        );
        assertThat(support.clearVariantImages(null, "SKU")).isFalse();
        assertThat(support.clearVariantImages(product, " ")).isFalse();
        assertThat(support.clearVariantImages(product, "NON-EXISTENT")).isFalse();
        assertThat(support.clearVariantImages(product, "HORIZON-S-NAVY")).isTrue();
        assertThat(support.parseProduct(product).get(0).imageUrl()).isNull();
    }

    @Test
    void zohoItemIdForSku_and_setVariantZohoItemId() {
        Product simple = new Product().itemStructure("SIMPLE");
        assertThat(support.zohoItemIdForSku(simple, "SKU")).isEmpty();
        assertThat(support.setVariantZohoItemId(simple, "SKU", "Z-1")).isFalse();

        Product product = variantProduct(
            """
            [{"sku":"HORIZON-S-NAVY","size":"S","color":"Navy","rate":999,"stock":1,"active":true,"zohoItemId":"Z-100"}]
            """
        );
        assertThat(support.zohoItemIdForSku(product, "HORIZON-S-NAVY")).contains("Z-100");
        assertThat(support.zohoItemIdForSku(product, "UNKNOWN")).isEmpty();

        assertThat(support.setVariantZohoItemId(product, " ", "Z-200")).isFalse();
        assertThat(support.setVariantZohoItemId(product, "HORIZON-S-NAVY", " ")).isFalse();
        assertThat(support.setVariantZohoItemId(product, "UNKNOWN", "Z-200")).isFalse();
        assertThat(support.setVariantZohoItemId(product, "HORIZON-S-NAVY", "Z-200")).isTrue();
        assertThat(support.zohoItemIdForSku(product, "HORIZON-S-NAVY")).contains("Z-200");
    }

    @Test
    void upsertPulledVariantFromZoho_edgeCases() {
        Product product = variantProduct(
            """
            [{"sku":"HORIZON-S-NAVY","size":"S","color":"Navy","rate":999,"stock":1,"active":true,"zohoItemId":"Z-1"},
             {"sku":"HORIZON-M-NAVY","size":"M","color":"Navy","rate":999,"stock":1,"active":true,"zohoItemId":"Z-2"}]
            """
        );

        // Null or blank SKU
        assertThat(support.upsertPulledVariantFromZoho(null, "SKU", "S", "Navy", BigDecimal.TEN, null, 1, "Z-1", false)).isFalse();
        assertThat(support.upsertPulledVariantFromZoho(product, " ", "S", "Navy", BigDecimal.TEN, null, 1, "Z-1", false)).isFalse();

        // Identity conflict: Zoho item ID matches Z-1 (HORIZON-S-NAVY), but variantSku matches HORIZON-M-NAVY
        assertThatThrownBy(() -> support.upsertPulledVariantFromZoho(product, "HORIZON-M-NAVY", "S", "Navy", BigDecimal.TEN, null, 1, "Z-1", false))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Variant identity conflict");

        // Size/color collision
        assertThatThrownBy(() -> support.upsertPulledVariantFromZoho(product, "HORIZON-M-NAVY", "S", "Navy", BigDecimal.TEN, null, 1, "Z-2", false))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Variant size/color conflict");

        // Missing match, addIfMissing false
        assertThat(support.upsertPulledVariantFromZoho(product, "NEW-SKU", "L", "Navy", BigDecimal.TEN, null, 1, "Z-3", false)).isFalse();

        // Missing match, addIfMissing true -> adds
        assertThat(support.upsertPulledVariantFromZoho(product, "NEW-SKU", "L", "Navy", new BigDecimal("800"), new BigDecimal("1000"), 5, "Z-3", true)).isTrue();
        assertThat(support.findBySku(support.parseProduct(product), "NEW-SKU")).isPresent();
    }

    @Test
    void anyVariantSkuInSet_emptyOrNonVariants() {
        Product simple = new Product().itemStructure("SIMPLE");
        assertThat(support.anyVariantSkuInSet(simple, Set.of("SKU"))).isFalse();
        Product product = variantProduct("[{\"sku\":\"SKU-1\",\"size\":\"S\",\"color\":\"Red\",\"rate\":100,\"stock\":1,\"active\":true}]");
        assertThat(support.anyVariantSkuInSet(product, null)).isFalse();
        assertThat(support.anyVariantSkuInSet(product, Set.of())).isFalse();
    }

    @Test
    void variantRow_withMethods() {
        VariantRow row = new VariantRow(
            "id-1",
            "SKU-1",
            "S",
            "Red",
            BigDecimal.valueOf(100),
            BigDecimal.valueOf(120),
            BigDecimal.valueOf(50),
            5,
            true,
            "swatch.jpg",
            "z-1",
            "img.jpg",
            List.of("extra.jpg")
        );

        assertThat(row.withColor("Blue").color()).isEqualTo("Blue");
        assertThat(row.withSize("M").size()).isEqualTo("M");
        assertThat(row.withStock(10).stock()).isEqualTo(10);
        assertThat(row.withPricing(BigDecimal.valueOf(200), BigDecimal.valueOf(250)).rate()).isEqualTo(BigDecimal.valueOf(200));
        assertThat(row.withZohoItemId("z-2").zohoItemId()).isEqualTo("z-2");
        assertThat(row.withActive(false).active()).isFalse();
        assertThat(row.withSku("SKU-2").sku()).isEqualTo("SKU-2");
        assertThat(row.withImageUrl("new.jpg").imageUrl()).isEqualTo("new.jpg");
        assertThat(row.withImageUrls(List.of("a.jpg", "b.jpg")).imageUrls()).containsExactly("a.jpg", "b.jpg");
        assertThat(row.toMap()).containsKey("sku");
    }

    @Test
    void applyRuntimePricing_nullDto_isNoOp() {
        support.applyRuntimePricing(null);
    }

    @Test
    void applyRuntimePricing_compareAtOnly_setsSellingFromMrp() {
        com.miniandmore.app.service.dto.ProductDTO dto = new com.miniandmore.app.service.dto.ProductDTO();
        dto.setCompareAtPrice(new BigDecimal("250"));
        dto.setPrice(new BigDecimal("1"));
        support.applyRuntimePricing(dto);
        assertThat(dto.getPrice()).isEqualByComparingTo("200.00");
    }

    @Test
    void applyRuntimePricing_unparseableVariantsJson_leavesDtoUnchanged() {
        com.miniandmore.app.service.dto.ProductDTO dto = new com.miniandmore.app.service.dto.ProductDTO();
        dto.setItemStructure("VARIANTS");
        dto.setPrice(new BigDecimal("99"));
        dto.setVariantsJson("{not-json");
        support.applyRuntimePricing(dto);
        assertThat(dto.getPrice()).isEqualByComparingTo("99");
    }

    @Test
    void applyRuntimePricing_variantMatrix_setsMinMrpAndSelling() {
        com.miniandmore.app.service.dto.ProductDTO dto = new com.miniandmore.app.service.dto.ProductDTO();
        dto.setItemStructure("VARIANTS");
        dto.setVariantsJson(
            """
            [{"sku":"A-S","size":"S","color":"Red","rate":800,"mrp":1000,"stock":1,"active":true},\
            {"sku":"A-M","size":"M","color":"Red","rate":960,"mrp":1200,"stock":2,"active":true}]
            """
        );
        support.applyRuntimePricing(dto);
        assertThat(dto.getCompareAtPrice()).isEqualByComparingTo("1000");
        assertThat(dto.getPrice()).isEqualByComparingTo("800.00");
    }

    @Test
    void tryParse_doubleEncodedJsonArray() throws Exception {
        String inner =
            """
            [{"sku":"ENC-S","size":"S","color":"Red","rate":100,"stock":1,"active":true}]
            """;
        String wrapped = new ObjectMapper().writeValueAsString(inner.trim());
        List<VariantRow> rows = support.parse(wrapped);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).sku()).isEqualTo("ENC-S");
    }

    @Test
    void applyMatrix_invalidNonEmptyJson_returnsFalse() {
        Product product = new Product().sku("BAD").price(BigDecimal.TEN).stock(1);
        boolean ok = support.applyMatrixToProduct(product, "VARIANTS", "{broken");
        assertThat(ok).isFalse();
    }

    @Test
    void mergeColorsPreservingGalleries_buildsFromColorImagesWhenColorsMissing() throws Exception {
        String colorImages =
            """
            {"Navy":["https://cdn.example.com/navy-front.jpg","https://cdn.example.com/navy-rear.jpg"]}
            """;
        String merged = support.mergeColorsPreservingGalleries(null, colorImages, List.of("Navy"));
        assertThat(merged).contains("Navy").contains("navy-front.jpg");
    }

    @Test
    void findByZohoItemId_blankId_returnsEmpty() {
        List<VariantRow> rows = support.parse("[{\"sku\":\"A\",\"zohoItemId\":\"z-1\",\"size\":\"S\",\"color\":\"Red\",\"rate\":1,\"stock\":1,\"active\":true}]");
        assertThat(support.findByZohoItemId(rows, " ")).isEmpty();
        assertThat(support.findByZohoItemId(rows, null)).isEmpty();
    }

    private Product variantProduct(String json) {
        Product product = new Product().sku("HORIZON").price(BigDecimal.ZERO).stock(0);
        support.applyMatrixToProduct(product, "VARIANTS", json);
        return product;
    }

    @Test
    void parseProduct_nullProduct_returnsEmptyRows() {
        assertThat(support.parseProduct(null)).isEmpty();
    }

    @Test
    void parse_invalidJson_returnsEmptyList() {
        assertThat(support.parse("{not-json")).isEmpty();
    }

    @Test
    void mergeColorsPreservingGalleries_keepsImageUrlsFromColorsJson() throws Exception {
        String colors =
            """
            [{"name":"Navy","imageUrls":["https://cdn.example.com/navy.jpg"]}]
            """;
        String merged = support.mergeColorsPreservingGalleries(colors, null, List.of("Navy"));
        assertThat(merged).contains("imageUrls").contains("navy.jpg");
    }

    @Test
    void findProductContainingVariantSku_returnsMatchingParent() {
        Product parent = variantProduct(
            """
            [{"sku":"NEW-SKU","size":"M","color":"Red","rate":10,"stock":1,"active":true}]
            """
        );
        assertThat(support.findProductContainingVariantSku(List.of(parent), "NEW-SKU")).contains(parent);
    }

    @Test
    void aggregates_emptyRows_returnsZeros() {
        Aggregates agg = support.aggregates(List.of());
        assertThat(agg.stockSum()).isZero();
        assertThat(agg.minSelling()).isNull();
        assertThat(agg.minMrp()).isNull();
    }

    @Test
    void coerceVariantsJson_nonStringValue_serializesToJson() {
        Map<String, Object> payload = Map.of("sku", "A", "size", "S");
        assertThat(support.coerceVariantsJson(payload)).contains("\"sku\":\"A\"");
    }

    @Test
    void coerceVariantsJson_unserializableValue_returnsNull() {
        Map<String, Object> cyclic = new java.util.HashMap<>();
        cyclic.put("self", cyclic);
        assertThat(support.coerceVariantsJson(cyclic)).isNull();
    }

    @Test
    void serialize_jsonWriteFailure_returnsEmptyArray() throws Exception {
        ObjectMapper failingMapper = mock(ObjectMapper.class);
        when(failingMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("boom") {});
        ProductVariantSupport broken = new ProductVariantSupport(failingMapper, mock(StorePricingService.class));
        List<VariantRow> rows = support.parse(
            """
            [{"sku":"FAIL-S","size":"S","color":"Red","rate":100,"stock":1,"active":true}]
            """
        );
        assertThat(broken.serialize(rows)).isEqualTo("[]");
    }

    @Test
    void mergePulledVariantFromZoho_updatesExistingSkuRow() {
        Product product = variantProduct(
            """
            [{"sku":"PULL-S","size":"S","color":"Red","rate":100,"stock":5,"active":true}]
            """
        );
        boolean merged = support.mergePulledVariantFromZoho(
            product,
            "PULL-S",
            new java.math.BigDecimal("90"),
            new java.math.BigDecimal("120"),
            12,
            "zoho-pull-1"
        );
        assertThat(merged).isTrue();
        VariantRow row = support.findBySku(support.parseProduct(product), "PULL-S").orElseThrow();
        assertThat(row.stock()).isEqualTo(12);
        assertThat(row.zohoItemId()).isEqualTo("zoho-pull-1");
    }

    @Test
    void mergePulledVariantFromZoho_unknownSku_returnsFalse() {
        Product product = variantProduct(
            """
            [{"sku":"KNOWN","size":"S","color":"Red","rate":100,"stock":1,"active":true}]
            """
        );
        assertThat(
            support.mergePulledVariantFromZoho(product, "MISSING", java.math.BigDecimal.TEN, null, 3, "zoho-x")
        ).isFalse();
    }

    @Test
    void upsertPulledVariantFromZoho_addIfMissing_appendsNewRow() {
        Product product = variantProduct(
            """
            [{"sku":"OLD","size":"S","color":"Red","rate":100,"stock":1,"active":true}]
            """
        );
        assertThat(
            support.upsertPulledVariantFromZoho(
                product,
                "NEW-SKU",
                "M",
                "Blue",
                new java.math.BigDecimal("200"),
                new java.math.BigDecimal("250"),
                4,
                "zoho-new",
                true
            )
        ).isTrue();
        assertThat(support.parseProduct(product)).hasSize(2);
        assertThat(support.findBySku(support.parseProduct(product), "NEW-SKU")).isPresent();
    }

    @Test
    void clearAllVariantImages_noPerSkuPhotos_leavesVariantsJsonUnchanged() {
        Product product = variantProduct(
            """
            [{"sku":"PLAIN","size":"S","color":"Red","rate":100,"stock":1,"active":true}]
            """
        );
        String before = product.getVariantsJson();
        support.clearAllVariantImages(product);
        assertThat(product.getVariantsJson()).isEqualTo(before);
    }

    @Test
    void updateVariantStockBySku_nullProduct_returnsFalse() {
        assertThat(support.updateVariantStockBySku(null, "SKU", 1)).isFalse();
    }

    @Test
    void applyRuntimePricing_simpleStructure_skipsVariantMatrix() {
        com.miniandmore.app.service.dto.ProductDTO dto = new com.miniandmore.app.service.dto.ProductDTO();
        dto.setItemStructure("SIMPLE");
        dto.setVariantsJson("[{\"sku\":\"X\",\"size\":\"S\",\"color\":\"Red\",\"rate\":50,\"stock\":1,\"active\":true}]");
        dto.setPrice(new java.math.BigDecimal("99"));
        support.applyRuntimePricing(dto);
        assertThat(dto.getPrice()).isEqualByComparingTo("99");
    }

    @Test
    void mergeColorsPreservingGalleries_malformedColorsJson_fallsBackToColorImagesMap() throws Exception {
        String merged = support.mergeColorsPreservingGalleries(
            "{not-an-array",
            "{\"Green\":[\"https://cdn.example.com/green.jpg\"]}",
            List.of("Green")
        );
        assertThat(merged).contains("Green").contains("green.jpg");
    }

    @Test
    void applyMatrixToProduct_invalidVariantsJson_returnsFalse() {
        Product product = new Product().sku("X").itemStructure("VARIANTS").variantsJson("[{bad json");
        assertThat(support.applyMatrixToProduct(product, "VARIANTS", "[{bad")).isFalse();
    }

    @Test
    void applyMatrixToProduct_nonEmptyUnparseableMatrix_returnsFalse() {
        Product product = new Product().sku("X").variantsJson("not-json");
        assertThat(support.applyMatrixToProduct(product, "VARIANTS", "not-json")).isFalse();
    }

    @Test
    void applyMatrixToProduct_simpleStructure_clearsVariantsJson() {
        Product product = new Product().sku("X").variantsJson("[{\"sku\":\"A\",\"size\":\"S\",\"color\":\"Red\",\"rate\":1,\"stock\":1,\"active\":true}]");
        assertThat(support.applyMatrixToProduct(product, "SIMPLE", null)).isTrue();
        assertThat(product.getVariantsJson()).isNull();
    }

    @Test
    void mergeColorsPreservingGalleries_emptyColorNames_returnsPreviousJson() throws Exception {
        assertThat(support.mergeColorsPreservingGalleries("[\"Navy\"]", List.of())).isEqualTo("[\"Navy\"]");
    }

    @Test
    void applyMatrixToProduct_nullVariantsJson_usesProductStoredMatrix() {
        Product product = new Product()
            .sku("USE-STORED")
            .itemStructure("VARIANTS")
            .variantsJson(
                """
                [{"sku":"USE-S","size":"S","color":"Red","rate":100,"stock":2,"active":true}]
                """
            );
        assertThat(support.applyMatrixToProduct(product, "VARIANTS", null)).isTrue();
        assertThat(product.getVariantsJson()).contains("USE-S");
    }

    @Test
    void mergeColorsPreservingGalleries_skipsDuplicateColorNames() throws Exception {
        String merged = support.mergeColorsPreservingGalleries(
            "[{\"name\":\"Navy\",\"imageUrls\":[\"https://cdn.example.com/n.jpg\"]}]",
            null,
            List.of("Navy", "NAVY", "navy")
        );
        assertThat(merged).contains("Navy").doesNotContain("NAVY");
    }

    @Test
    void mergeColorsPreservingGalleries_objectWithEmptyImageUrlsArray_isNotRichMedia() throws Exception {
        String merged = support.mergeColorsPreservingGalleries(
            "[{\"name\":\"Plain\",\"imageUrls\":[]}]",
            null,
            List.of("Plain")
        );
        assertThat(merged).isEqualTo("[\"Plain\"]");
    }

    @Test
    void freezeVariantColorsFromStored_nullProduct_isNoOp() {
        support.freezeVariantColorsFromStored(null, "[{\"sku\":\"A\",\"color\":\"Red\"}]");
    }

    @Test
    void applyMatrixToProduct_nonEmptyButUnparseableMatrix_returnsFalse() {
        Product product = new Product().sku("X").itemStructure("VARIANTS").variantsJson("[{broken");
        assertThat(support.applyMatrixToProduct(product, "VARIANTS", "[{broken")).isFalse();
    }

    @Test
    void mergeColorsPreservingGalleries_buildsFromColorImagesMapWhenColorsEmpty() throws Exception {
        String merged = support.mergeColorsPreservingGalleries(
            "[]",
            "{\"Ocean\":[\"https://cdn.example.com/ocean.jpg\"]}",
            List.of("Ocean")
        );
        assertThat(merged).contains("Ocean").contains("ocean.jpg");
    }

    @Test
    void mergeColorsPreservingGalleries_skipsBlankColorNamesInList() throws Exception {
        String merged = support.mergeColorsPreservingGalleries(
            "[{\"name\":\"Keep\",\"imageUrls\":[\"https://cdn.example.com/k.jpg\"]}]",
            null,
            List.of("Keep", "  ", "Keep")
        );
        assertThat(merged).contains("Keep").contains("k.jpg");
    }

    @Test
    void mergeColorsPreservingGalleries_buildsFromMalformedColorImagesObject() throws Exception {
        String merged = support.mergeColorsPreservingGalleries(
            "[]",
            "{\"Forest\":[\"https://cdn.example.com/forest.jpg\"],\"\":[]}",
            List.of("Forest")
        );
        assertThat(merged).contains("Forest").contains("forest.jpg");
    }

    @Test
    void freezeVariantColorsFromStored_overwritesIncomingColorWithFrozenSkuColor() {
        Product product = variantProduct(
            """
            [{"sku":"SKU-A","size":"M","color":"Admin Blue","rate":100,"stock":1,"active":true}]
            """
        );
        String frozen =
            """
            [{"sku":"SKU-A","size":"M","color":"Zoho Navy","rate":100,"stock":1,"active":true}]
            """;
        support.freezeVariantColorsFromStored(product, frozen);
        assertThat(support.findBySku(support.parseProduct(product), "SKU-A")).get().extracting(VariantRow::color).isEqualTo("Zoho Navy");
    }
}
