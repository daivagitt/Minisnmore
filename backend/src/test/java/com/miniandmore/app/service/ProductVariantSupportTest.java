package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.domain.Product;
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
    void neverTreatsParentAsVariantWhenSimple() {
        Product product = new Product().sku("SIMPLE-1").itemStructure("SIMPLE").price(BigDecimal.TEN).stock(5);
        assertThat(support.isVariants(product)).isFalse();
        assertThat(support.resolveSellable(product, "SIMPLE-1", null, null)).isEmpty();
    }
}
