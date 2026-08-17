package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.domain.Product;
import com.miniandmore.app.service.ProductVariantSupport.Aggregates;
import com.miniandmore.app.service.ProductVariantSupport.VariantRow;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProductVariantSupportTest {

    private ProductVariantSupport support;

    @BeforeEach
    void setUp() {
        support = new ProductVariantSupport(new ObjectMapper());
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
        assertThat(legacy.rate()).isEqualByComparingTo("80.00");

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
            "zoho-99"
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
            .containsEntry("zohoItemId", "zoho-99");
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
    void aggregates_ignoresInactiveRowsAndNegativeStock() {
        List<VariantRow> rows = support.parse(
            """
            [{"sku":"A","size":"S","color":"Red","rate":0,"mrp":0,"cost":0,"stock":-2,"active":true},\
            {"sku":"B","size":"M","color":"Blue","rate":200,"mrp":250,"cost":80,"stock":5,"active":false},\
            {"sku":"C","size":"L","color":"Green","rate":150,"mrp":180,"cost":60,"stock":4,"active":true}]
            """
        );

        Aggregates agg = support.aggregates(rows);

        assertThat(agg.minSelling()).isEqualByComparingTo("150");
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
    void variantSellingPrice_prefersRateOverMrp() {
        VariantRow row = new VariantRow("id", "SKU", "M", "Blue", new BigDecimal("80"), new BigDecimal("100"), null, 1, true, null, null);
        assertThat(support.variantSellingPrice(row)).isEqualByComparingTo("80");
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

    private Product variantProduct(String json) {
        Product product = new Product().sku("HORIZON").price(BigDecimal.ZERO).stock(0);
        support.applyMatrixToProduct(product, "VARIANTS", json);
        return product;
    }
}
