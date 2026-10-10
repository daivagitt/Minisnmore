package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.domain.Product;
import com.miniandmore.app.service.ProductVariantSupport.Aggregates;
import com.miniandmore.app.service.ProductVariantSupport.VariantRow;
import com.miniandmore.app.service.dto.ProductDTO;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** Focused branch/line coverage for remaining ProductVariantSupport gaps. */
class ProductVariantSupportBranchCoverageTest {

    private ProductVariantSupport support;

    @BeforeEach
    void setUp() {
        support = StorePricingTestSupport.variantSupport(new ObjectMapper());
    }

    @Test
    void applyMatrixToProduct_parsedEmptyNonBlankJson_returnsFalse() {
        Product product = new Product().sku("EMPTY-ROW");
        assertThat(support.applyMatrixToProduct(product, "VARIANTS", "[null]")).isFalse();
    }

    @Test
    void applyMatrixToProduct_serializeFailure_returnsFalse() throws Exception {
        ObjectMapper mapper = spy(new ObjectMapper());
        doThrow(new JsonProcessingException("fail") {}).when(mapper).writeValueAsString(anyList());
        ProductVariantSupport broken = new ProductVariantSupport(mapper, mock(StorePricingService.class));
        Product product = new Product().sku("SER-FAIL");
        String matrix =
            """
            [{"sku":"A-S","size":"S","color":"Red","rate":100,"stock":1,"active":true}]
            """;
        assertThat(broken.applyMatrixToProduct(product, "VARIANTS", matrix)).isFalse();
    }

    @Test
    void mergeColorsPreservingGalleries_skipsNullNodesBlankNamesAndBadColorImages() throws Exception {
        String merged = support.mergeColorsPreservingGalleries(
            "[null,{\"name\":\"  \",\"imageUrls\":[\"https://cdn.example.com/x.jpg\"]},\"Plain\"]",
            "{not-json",
            List.of("Plain", "Navy")
        );
        assertThat(merged).contains("Plain");

        String fromImages = support.mergeColorsPreservingGalleries("[]", "{not-json", List.of("Navy"));
        assertThat(fromImages).contains("Navy");

        String duplicateKey = support.mergeColorsPreservingGalleries(
            "[]",
            "{\"Navy\":[\"https://cdn.example.com/a.jpg\"],\"navy\":[\"https://cdn.example.com/b.jpg\"]}",
            List.of("Navy")
        );
        assertThat(duplicateKey).contains("Navy");

        String notObject = support.mergeColorsPreservingGalleries("[]", "\"string-root\"", List.of("Red"));
        assertThat(notObject).contains("Red");
    }

    @Test
    void freezeVariantColorsFromStored_skipsBlankSkuAndEmptyMaps() {
        Product product = variantProduct(
            """
            [{"sku":"  ","size":"S","color":"Red","rate":1,"stock":1,"active":true},
             {"sku":"KEEP","size":"M","color":"Blue","rate":1,"stock":1,"active":true}]
            """
        );
        support.freezeVariantColorsFromStored(product, "[{\"sku\":\"  \",\"color\":\"X\"}]");
        support.freezeVariantColorsFromStored(product, "[]");
        support.freezeVariantColorsFromStored(product, "[{\"sku\":\"\",\"color\":\"\"}]");
        assertThat(support.findBySku(support.parseProduct(product), "KEEP")).isPresent();
    }

    @Test
    void stockMutators_coverGuardAndMissPaths() {
        Product simple = new Product().itemStructure("SIMPLE");
        assertThat(support.decrementVariantStock(simple, "SKU", "S", "Red", 1)).isFalse();
        assertThat(support.decrementVariantStock(variantProduct("[{\"sku\":\"A\",\"size\":\"S\",\"color\":\"Red\",\"rate\":1,\"stock\":1,\"active\":true}]"), "A", "S", "Red", 0)).isFalse();
        assertThat(support.updateVariantStockByZohoItemId(simple, "z-1", 3)).isFalse();
        assertThat(support.updateVariantStockByZohoItemId(variantProduct("[{\"sku\":\"Z\",\"size\":\"S\",\"color\":\"Red\",\"rate\":1,\"stock\":1,\"active\":true,\"zohoItemId\":\"z-9\"}]"), "missing", 3)).isFalse();

        Product blankZoho = variantProduct(
            """
            [{"sku":"BZ","size":"S","color":"Red","rate":1,"stock":1,"active":true,"zohoItemId":"  "}]
            """
        );
        assertThat(support.zohoItemIdForSku(blankZoho, "BZ")).isEmpty();

        assertThat(support.setVariantImages(null, "SKU", List.of("https://cdn.example.com/a.jpg"))).isFalse();
        Product withSku = variantProduct("[{\"sku\":\"IMG\",\"size\":\"S\",\"color\":\"Red\",\"rate\":1,\"stock\":1,\"active\":true}]");
        assertThat(support.setVariantImages(withSku, "IMG", List.of(" ", ""))).isFalse();
        assertThat(support.setVariantImages(withSku, "MISSING", List.of("https://cdn.example.com/a.jpg"))).isFalse();

        support.clearAllVariantImages(null);
        support.clearAllVariantImages(simple);
        Product emptyMatrix = variantProduct("[]");
        support.clearAllVariantImages(emptyMatrix);
    }

    @Test
    void deactivateVariantsNotInPulledSet_nullSkusAndSuccessfulApply() {
        Product product = variantProduct(
            """
            [{"sku":"PULL-ME","size":"S","color":"Red","rate":1,"stock":1,"active":true,"zohoItemId":"z-1"}]
            """
        );
        assertThat(support.deactivateVariantsNotInPulledSet(product, null)).isZero();
        assertThat(support.deactivateVariantsNotInPulledSet(product, Set.of(), Set.of("z-other"))).isEqualTo(1);
    }

    @Test
    void deactivateBySkuOrZohoId_alreadyInactive_returnsFalse() {
        Product inactive = variantProduct(
            """
            [{"sku":"OFF","size":"S","color":"Red","rate":1,"stock":1,"active":false,"zohoItemId":"z-off"}]
            """
        );
        assertThat(support.deactivateVariantBySku(inactive, "OFF")).isFalse();
        assertThat(support.deactivateVariantByZohoItemId(inactive, "z-off")).isFalse();
    }

    @Test
    void variantRow_fromMap_coversLegacyAndTypeCoercion() {
        Map<String, Object> legacyMrp = new LinkedHashMap<>();
        legacyMrp.put("sku", "LEG");
        legacyMrp.put("size", "S");
        legacyMrp.put("color", "Red");
        legacyMrp.put("rate", 999);
        legacyMrp.put("stock", Integer.valueOf(2));
        legacyMrp.put("active", Boolean.FALSE);
        legacyMrp.put("swatch_url", " swatch.jpg ");
        legacyMrp.put("zoho_item_id", " z-1 ");
        legacyMrp.put("image_url", " hero.jpg ");
        legacyMrp.put("image_urls", List.of(" rear.jpg ", " "));
        VariantRow legacy = VariantRow.fromMap(legacyMrp);
        assertThat(legacy.mrp()).isEqualByComparingTo("999");
        assertThat(legacy.rate()).isEqualByComparingTo("0");
        assertThat(legacy.swatchUrl()).isEqualTo("swatch.jpg");
        assertThat(legacy.zohoItemId()).isEqualTo("z-1");
        assertThat(legacy.imageUrl()).isEqualTo("hero.jpg");
        assertThat(legacy.imageUrls()).containsExactly("rear.jpg");

        Map<String, Object> typed = Map.of(
            "sku",
            "T",
            "size",
            "M",
            "color",
            "Blue",
            "rate",
            new BigDecimal("10.5"),
            "mrp",
            20L,
            "price",
            15,
            "cost",
            "3.5",
            "stock",
            "4",
            "active",
            "0",
            "imageUrls",
            List.of(123, "https://cdn.example.com/x.jpg")
        );
        VariantRow row = VariantRow.fromMap(typed);
        assertThat(row.rate()).isEqualByComparingTo("15");
        assertThat(row.mrp()).isEqualByComparingTo("20");
        assertThat(row.cost()).isEqualByComparingTo("3.5");
        assertThat(row.stock()).isEqualTo(4);
        assertThat(row.active()).isFalse();
        assertThat(row.toMap()).containsKeys("sku", "rate", "stock", "active");
        assertThat(row.withImageUrls(null).imageUrls()).isEmpty();
    }

    @Test
    void mergeColorsPreservingGalleries_positionFallbackAndRichMediaShapes() throws Exception {
        String renamed = support.mergeColorsPreservingGalleries(
            "[{\"name\":\"Legacy\",\"swatchUrl\":\"https://cdn.example.com/swatch.jpg\"}]",
            null,
            List.of("Renamed")
        );
        assertThat(renamed).contains("Renamed").contains("swatch.jpg");

        String imageUrlOnly = support.mergeColorsPreservingGalleries(
            "[{\"name\":\"Coral\",\"imageUrl\":\"https://cdn.example.com/coral-hero.jpg\"}]",
            null,
            List.of("Coral")
        );
        assertThat(imageUrlOnly).contains("coral-hero.jpg");

        String textualPosition = support.mergeColorsPreservingGalleries("[\"Scarlet\"]", null, List.of("Scarlet"));
        assertThat(textualPosition).contains("Scarlet");

        String fromImagesOnly = support.mergeColorsPreservingGalleries(
            "[]",
            "{\"Mint\":[\"https://cdn.example.com/mint.jpg\"],\"bad\":{},\"\":[\"https://cdn.example.com/skip.jpg\"],\"Empty\":[]}",
            List.of("Mint")
        );
        assertThat(fromImagesOnly).contains("Mint").contains("mint.jpg");
    }

    @Test
    void mergeColorsPreservingGalleries_prefersExistingRichKeyOverColorImagesDuplicate() throws Exception {
        String merged = support.mergeColorsPreservingGalleries(
            "[{\"name\":\"Navy\",\"imageUrls\":[\"https://cdn.example.com/from-colors.jpg\"]}]",
            "{\"Navy\":[\"https://cdn.example.com/from-images.jpg\"]}",
            List.of("Navy")
        );
        assertThat(merged).contains("from-colors.jpg").doesNotContain("from-images.jpg");
    }

    @Test
    void setVariantImageUrl_withoutExistingExtras_andSingleHeroImage() {
        Product noExtras = variantProduct(
            """
            [{"sku":"IMG","size":"S","color":"Red","rate":1,"stock":1,"active":true,"imageUrl":"https://cdn.example.com/old.jpg"}]
            """
        );
        assertThat(support.setVariantImageUrl(noExtras, "IMG", "https://cdn.example.com/new.jpg")).isTrue();
        VariantRow row = support.findBySku(support.parseProduct(noExtras), "IMG").orElseThrow();
        assertThat(row.imageUrl()).isEqualTo("https://cdn.example.com/new.jpg");
        assertThat(row.imageUrls()).isEmpty();

        Product withDupExtra = variantProduct(
            """
            [{"sku":"IMG2","size":"S","color":"Red","rate":1,"stock":1,"active":true,"imageUrls":["https://cdn.example.com/hero.jpg","https://cdn.example.com/rear.jpg"]}]
            """
        );
        assertThat(support.setVariantImageUrl(withDupExtra, "IMG2", "https://cdn.example.com/hero.jpg")).isTrue();
        VariantRow merged = support.findBySku(support.parseProduct(withDupExtra), "IMG2").orElseThrow();
        assertThat(merged.imageUrls()).containsExactly("https://cdn.example.com/rear.jpg");

        assertThat(support.setVariantImages(withDupExtra, "IMG2", List.of("https://cdn.example.com/only.jpg"))).isTrue();
        assertThat(support.findBySku(support.parseProduct(withDupExtra), "IMG2").orElseThrow().imageUrls()).isEmpty();
    }

    @Test
    void clearAllVariantImages_clearsHeroAndExtras() {
        Product product = variantProduct(
            """
            [{"sku":"A","size":"S","color":"Red","rate":1,"stock":1,"active":true,"imageUrl":"https://cdn.example.com/a.jpg","imageUrls":["https://cdn.example.com/b.jpg"]}]
            """
        );
        support.clearAllVariantImages(product);
        VariantRow row = support.findBySku(support.parseProduct(product), "A").orElseThrow();
        assertThat(row.imageUrl()).isNull();
        assertThat(row.imageUrls()).isEmpty();
    }

    @Test
    void deactivateVariantsNotInPulledSet_linkedOnly_respectsZohoLinkageAndItemIds() {
        Product product = variantProduct(
            """
            [{"sku":"LOCAL","size":"S","color":"Red","rate":1,"stock":1,"active":true},
             {"sku":"LINKED","size":"M","color":"Blue","rate":1,"stock":1,"active":true,"zohoItemId":"z-linked"}]
            """
        );
        assertThat(support.deactivateVariantsNotInPulledSet(product, Set.of("other-sku"), Set.of("z-other"))).isEqualTo(1);
        assertThat(support.findBySku(support.parseProduct(product), "LOCAL").orElseThrow().active()).isTrue();
        assertThat(support.findBySku(support.parseProduct(product), "LINKED").orElseThrow().active()).isFalse();

        Product byItemId = variantProduct(
            """
            [{"sku":"SKU-Z","size":"S","color":"Red","rate":1,"stock":1,"active":true,"zohoItemId":"z-keep"}]
            """
        );
        assertThat(support.deactivateVariantsNotInPulledSet(byItemId, Set.of("missing-sku"), Set.of("z-keep"))).isZero();
        assertThat(support.findBySku(support.parseProduct(byItemId), "SKU-Z").orElseThrow().active()).isTrue();

        Product localOnly = variantProduct(
            """
            [{"sku":"DROP-LOCAL","size":"S","color":"Red","rate":1,"stock":1,"active":true}]
            """
        );
        assertThat(support.deactivateVariantsNotInPulledSet(localOnly, Set.of("KEEP-OTHER"))).isEqualTo(1);
        assertThat(support.findBySku(support.parseProduct(localOnly), "DROP-LOCAL").orElseThrow().active()).isFalse();
    }

    @Test
    void upsertPulledVariantFromZoho_inheritsDimensionsAndPreservesNullPricingUpdates() {
        Product product = variantProduct(
            """
            [{"sku":"OLD-SKU","size":"S","color":"Navy","rate":50,"mrp":100,"stock":4,"active":true,"zohoItemId":"z-stable"}]
            """
        );
        assertThat(
            support.upsertPulledVariantFromZoho(product, "NEW-SKU", null, null, null, null, null, "z-stable", false)
        ).isTrue();
        VariantRow updated = support.findByZohoItemId(support.parseProduct(product), "z-stable").orElseThrow();
        assertThat(updated.sku()).isEqualTo("NEW-SKU");
        assertThat(updated.size()).isEqualTo("S");
        assertThat(updated.color()).isEqualTo("Navy");
        assertThat(updated.rate()).isEqualByComparingTo("50");
        assertThat(updated.mrp()).isEqualByComparingTo("100");
        assertThat(updated.stock()).isEqualTo(4);
    }

    @Test
    void upsertPulledVariantFromZoho_addIfMissing_usesZeroDefaultsForNullPricing() {
        Product product = variantProduct("[]");
        assertThat(
            support.upsertPulledVariantFromZoho(product, "NEW-1", "L", "Green", null, null, null, "z-new", true)
        ).isTrue();
        VariantRow row = support.findBySku(support.parseProduct(product), "NEW-1").orElseThrow();
        assertThat(row.rate()).isEqualByComparingTo("0");
        assertThat(row.stock()).isZero();
        assertThat(row.zohoItemId()).isEqualTo("z-new");
    }

    @Test
    void colorsForSize_skipsBlankColorLabels() {
        List<VariantRow> rows = support.parse(
            """
            [{"sku":"A","size":"S","color":"  ","rate":1,"stock":1,"active":true},
             {"sku":"B","size":"S","color":"Navy","rate":1,"stock":1,"active":true}]
            """
        );
        assertThat(support.colorsForSize(rows, "S")).containsExactly("Navy");
    }

    @Test
    void variantSellingPrice_prefersRateWhenMrpMissing() {
        VariantRow rateOnly = new VariantRow("1", "R", "S", "Red", new BigDecimal("125"), null, null, 1, true, null, null, null, List.of());
        assertThat(support.variantSellingPrice(rateOnly)).isEqualByComparingTo("125");
    }

    @Test
    void aggregates_tracksPositiveCostAmongActiveRows() {
        List<VariantRow> rows = List.of(
            new VariantRow("1", "A", "S", "Red", new BigDecimal("10"), null, new BigDecimal("8"), 1, true, null, null, null, List.of()),
            new VariantRow("2", "B", "M", "Blue", new BigDecimal("10"), null, new BigDecimal("5"), 1, true, null, null, null, List.of())
        );
        Aggregates agg = support.aggregates(rows);
        assertThat(agg.minCost()).isEqualByComparingTo("5");
    }

    @Test
    void applyRuntimePricing_rateOnlyMatrix_setsSellingWithoutCompareAt() {
        ProductDTO dto = new ProductDTO();
        dto.setItemStructure("VARIANTS");
        dto.setVariantsJson(
            """
            [{"sku":"A","size":"S","color":"Red","rate":200,"mrp":0,"stock":1,"active":true},
             {"sku":"B","size":"M","color":"Blue","rate":150,"mrp":0,"stock":1,"active":true}]
            """
        );
        support.applyRuntimePricing(dto);
        assertThat(dto.getCompareAtPrice()).isNull();
        assertThat(dto.getPrice()).isEqualByComparingTo("150");
    }

    @Test
    void variantRow_toMap_filtersExtrasAndOmitsZeroMrp() {
        VariantRow row = new VariantRow(
            null,
            "SKU",
            null,
            null,
            BigDecimal.ONE,
            BigDecimal.ZERO,
            BigDecimal.ONE,
            1,
            true,
            " ",
            null,
            "https://cdn.example.com/hero.jpg",
            List.of("https://cdn.example.com/hero.jpg", " ", "https://cdn.example.com/rear.jpg")
        );
        Map<String, Object> map = row.toMap();
        assertThat(map).doesNotContainKeys("id", "size", "color", "mrp", "swatchUrl", "zohoItemId");
        assertThat(map.get("imageUrl")).isEqualTo("https://cdn.example.com/hero.jpg");
        @SuppressWarnings("unchecked")
        List<String> extras = (List<String>) map.get("imageUrls");
        assertThat(extras).containsExactly("https://cdn.example.com/rear.jpg");

        VariantRow falseActive = VariantRow.fromMap(Map.of("sku", "F", "size", "S", "rate", 1, "stock", 1, "active", "false"));
        assertThat(falseActive.active()).isFalse();
        assertThat(VariantRow.fromMap(Map.of("sku", "Z", "size", "S", "rate", 1, "stock", 1, "active", "0")).active()).isFalse();
    }

    @Test
    void renameVariantColors_noOpWhenNoKeysMatch() {
        Product product = variantProduct(
            """
            [{"sku":"X","size":"S","color":"Navy","rate":1,"stock":1,"active":true}]
            """
        );
        String before = product.getVariantsJson();
        support.renameVariantColors(product, Map.of("Other", "Renamed"));
        support.renameVariantColors(null, Map.of("Navy", "Midnight"));
        assertThat(product.getVariantsJson()).isEqualTo(before);
    }

    @Test
    void decrementVariantStock_rejectsInactiveOrInsufficientStock() {
        Product inactive = variantProduct(
            """
            [{"sku":"OFF","size":"S","color":"Red","rate":1,"stock":5,"active":false}]
            """
        );
        assertThat(support.decrementVariantStock(inactive, "OFF", "S", "Red", 1)).isFalse();

        Product low = variantProduct(
            """
            [{"sku":"LOW","size":"S","color":"Red","rate":1,"stock":1,"active":true}]
            """
        );
        assertThat(support.decrementVariantStock(low, "LOW", "S", "Red", 3)).isFalse();
    }

    @Test
    void anyVariantSkuInSet_matchesIgnoreCase() {
        Product product = variantProduct(
            """
            [{"sku":"Mix-Case","size":"S","color":"Red","rate":1,"stock":1,"active":true}]
            """
        );
        assertThat(support.anyVariantSkuInSet(product, Set.of("mix-case"))).isTrue();
    }

    @Test
    void deactivateVariantByZohoItemId_flipsActiveRow() {
        Product product = variantProduct(
            """
            [{"sku":"Z","size":"S","color":"Red","rate":1,"stock":1,"active":true,"zohoItemId":"z-off"}]
            """
        );
        assertThat(support.deactivateVariantByZohoItemId(product, "z-off")).isTrue();
        assertThat(support.findByZohoItemId(support.parseProduct(product), "z-off").orElseThrow().active()).isFalse();
    }

    @Test
    void upsertPulledVariantFromZoho_conflictWithInactiveRowStillThrows() {
        Product product = variantProduct(
            """
            [{"sku":"ACTIVE","size":"S","color":"Navy","rate":1,"stock":1,"active":true,"zohoItemId":"z-active"},
             {"sku":"INACTIVE","size":"M","color":"Navy","rate":1,"stock":1,"active":false}]
            """
        );
        assertThatThrownBy(() ->
            support.upsertPulledVariantFromZoho(product, "NEW-SKU", "M", "Navy", BigDecimal.TEN, null, 1, "z-new", true)
        ).isInstanceOf(IllegalStateException.class).hasMessageContaining("size/color conflict");
    }

    @Test
    void applyMatrixToProduct_nullStructure_defaultsSimpleAndClearsVariants() {
        Product product = new Product().sku("SIMPLE-1").variantsJson("[{\"sku\":\"X\"}]");
        assertThat(support.applyMatrixToProduct(product, null, null)).isTrue();
        assertThat(product.getItemStructure()).isEqualTo("SIMPLE");
        assertThat(product.getVariantsJson()).isNull();
    }

    @Test
    void applyRuntimePricing_blankVariantsJsonOnVariantsStructure_isNoOp() {
        ProductDTO dto = new ProductDTO();
        dto.setItemStructure("VARIANTS");
        dto.setVariantsJson("   ");
        dto.setPrice(new BigDecimal("42"));
        support.applyRuntimePricing(dto);
        assertThat(dto.getPrice()).isEqualByComparingTo("42");
    }

    @Test
    void applyRuntimePricing_mrpOnlyActiveRows_setsCompareAtAndSelling() {
        ProductDTO dto = new ProductDTO();
        dto.setItemStructure("VARIANTS");
        dto.setVariantsJson(
            """
            [{"sku":"A","size":"S","color":"Red","rate":0,"mrp":500,"stock":1,"active":true},
             {"sku":"B","size":"M","color":"Blue","rate":0,"mrp":0,"stock":1,"active":false}]
            """
        );
        support.applyRuntimePricing(dto);
        assertThat(dto.getCompareAtPrice()).isEqualByComparingTo("500");
        assertThat(dto.getPrice()).isEqualByComparingTo("400.00");
    }

    @Test
    void variantSellingPrice_positiveMrp_usesStorefrontDiscount() {
        VariantRow mrpRow = new VariantRow("1", "R", "S", "Red", BigDecimal.ZERO, new BigDecimal("100"), null, 1, true, null, null, null, List.of());
        assertThat(support.variantSellingPrice(mrpRow)).isEqualByComparingTo("80.00");
        assertThat(support.variantSellingPrice(null)).isEqualByComparingTo("0");
    }

    @Test
    void aggregates_skipsZeroSellingAndTracksMinMrpAmongActives() {
        List<VariantRow> rows = List.of(
            new VariantRow("1", "A", "S", "Red", BigDecimal.ZERO, new BigDecimal("200"), null, 2, true, null, null, null, List.of()),
            new VariantRow("2", "B", "M", "Blue", new BigDecimal("50"), null, new BigDecimal("3"), 1, true, null, null, null, List.of()),
            new VariantRow("3", "C", "L", "Green", new BigDecimal("10"), null, null, 5, false, null, null, null, List.of())
        );
        Aggregates agg = support.aggregates(rows);
        assertThat(agg.minSelling()).isEqualByComparingTo("50");
        assertThat(agg.minMrp()).isEqualByComparingTo("200");
        assertThat(agg.stockSum()).isEqualTo(3);
    }

    @Test
    void renameVariantColors_rewritesMatchingLabels() {
        Product product = variantProduct(
            """
            [{"sku":"A","size":"S","color":"Navy","rate":1,"stock":1,"active":true}]
            """
        );
        support.renameVariantColors(product, Map.of("Navy", "Midnight"));
        assertThat(product.getVariantsJson()).contains("Midnight").doesNotContain("\"Navy\"");
    }

    @Test
    void hasActiveVariants_andHasZohoLinkedVariants_coverFalsePaths() {
        Product simple = new Product().itemStructure("SIMPLE");
        assertThat(support.hasActiveVariants(simple)).isFalse();
        assertThat(support.hasZohoLinkedVariants(simple)).isFalse();

        Product inactive = variantProduct(
            """
            [{"sku":"OFF","size":"S","color":"Red","rate":1,"stock":1,"active":false}]
            """
        );
        assertThat(support.hasActiveVariants(inactive)).isFalse();
        assertThat(support.hasZohoLinkedVariants(inactive)).isFalse();
    }

    @Test
    void hasZohoLinkedVariants_trueWhenAnyRowLinked() {
        Product linked = variantProduct(
            """
            [{"sku":"Z","size":"S","color":"Red","rate":1,"stock":1,"active":true,"zohoItemId":"z-1"}]
            """
        );
        assertThat(support.hasZohoLinkedVariants(linked)).isTrue();
        assertThat(support.hasActiveVariants(linked)).isTrue();
    }

    @Test
    void anyVariantSkuInSet_nullOrEmptySet_returnsFalse() {
        Product product = variantProduct("[{\"sku\":\"A\",\"size\":\"S\",\"color\":\"Red\",\"rate\":1,\"stock\":1,\"active\":true}]");
        assertThat(support.anyVariantSkuInSet(product, null)).isFalse();
        assertThat(support.anyVariantSkuInSet(product, Set.of())).isFalse();
    }

    @Test
    void anyVariantSkuInSet_ignoresNullEntriesInPulledSet() {
        Product product = variantProduct("[{\"sku\":\"A\",\"size\":\"S\",\"color\":\"Red\",\"rate\":1,\"stock\":1,\"active\":true}]");
        Set<String> withNull = new java.util.HashSet<>();
        withNull.add(null);
        withNull.add("other");
        assertThat(support.anyVariantSkuInSet(product, withNull)).isFalse();
        withNull.add("A");
        assertThat(support.anyVariantSkuInSet(product, withNull)).isTrue();
    }

    @Test
    void deactivateVariantBySku_activeRow_flipsInactive() {
        Product product = variantProduct(
            """
            [{"sku":"LIVE","size":"S","color":"Red","rate":1,"stock":1,"active":true}]
            """
        );
        assertThat(support.deactivateVariantBySku(product, "LIVE")).isTrue();
        assertThat(support.findBySku(support.parseProduct(product), "LIVE").orElseThrow().active()).isFalse();
    }

    @Test
    void deactivateVariantBySku_nonVariantsOrBlankSku_returnsFalse() {
        Product simple = new Product().itemStructure("SIMPLE");
        assertThat(support.deactivateVariantBySku(simple, "X")).isFalse();
        Product variants = variantProduct("[{\"sku\":\"X\",\"size\":\"S\",\"color\":\"Red\",\"rate\":1,\"stock\":1,\"active\":true}]");
        assertThat(support.deactivateVariantBySku(variants, "  ")).isFalse();
    }

    @Test
    void deactivateVariantByZohoItemId_guardPathsReturnFalse() {
        Product simple = new Product().itemStructure("SIMPLE");
        assertThat(support.deactivateVariantByZohoItemId(simple, "z-1")).isFalse();
        assertThat(support.deactivateVariantByZohoItemId(variantProduct("[{\"sku\":\"X\",\"size\":\"S\",\"color\":\"Red\",\"rate\":1,\"stock\":1,\"active\":true}]"), "  ")).isFalse();
    }

    @Test
    void findProductContainingVariantZohoItemId_skipsSimpleAndBlankId() {
        Product variants = variantProduct("[{\"sku\":\"V\",\"size\":\"S\",\"color\":\"Red\",\"rate\":1,\"stock\":1,\"active\":true,\"zohoItemId\":\"z-find\"}]");
        Product simple = new Product().itemStructure("SIMPLE").sku("S");
        assertThat(support.findProductContainingVariantZohoItemId(List.of(simple, variants), "z-find")).contains(variants);
        assertThat(support.findProductContainingVariantZohoItemId(List.of(variants), "  ")).isEmpty();
    }

    @Test
    void variantHasSizeAndColor_requiresBothDimensions() {
        Product partial = variantProduct(
            """
            [{"sku":"PART","size":"S","color":"","rate":1,"stock":1,"active":true}]
            """
        );
        assertThat(support.variantHasSizeAndColor(partial, "PART")).isFalse();
        Product full = variantProduct(
            """
            [{"sku":"FULL","size":"S","color":"Red","rate":1,"stock":1,"active":true}]
            """
        );
        assertThat(support.variantHasSizeAndColor(full, "FULL")).isTrue();
    }

    @Test
    void zohoItemIdForSku_returnsTrimmedId() {
        Product product = variantProduct(
            """
            [{"sku":"Z","size":"S","color":"Red","rate":1,"stock":1,"active":true,"zohoItemId":" z-42 "}]
            """
        );
        assertThat(support.zohoItemIdForSku(product, "Z")).contains("z-42");
    }

    @Test
    void upsertPulledVariantFromZoho_nullProductOrBlankSku_returnsFalse() {
        Product product = variantProduct("[]");
        assertThat(support.upsertPulledVariantFromZoho(null, "SKU", "S", "Red", BigDecimal.ONE, null, 1, "z", false)).isFalse();
        assertThat(support.upsertPulledVariantFromZoho(product, "  ", "S", "Red", BigDecimal.ONE, null, 1, "z", false)).isFalse();
        assertThat(support.upsertPulledVariantFromZoho(product, "MISSING", "S", "Red", BigDecimal.ONE, null, 1, "z", false)).isFalse();
    }

    @Test
    void upsertPulledVariantFromZoho_identityConflict_throws() {
        Product product = variantProduct(
            """
            [{"sku":"SKU-A","size":"S","color":"Red","rate":1,"stock":1,"active":true,"zohoItemId":"z-a"},
             {"sku":"SKU-B","size":"M","color":"Blue","rate":1,"stock":1,"active":true,"zohoItemId":"z-b"}]
            """
        );
        assertThatThrownBy(() ->
            support.upsertPulledVariantFromZoho(product, "SKU-B", "M", "Blue", BigDecimal.TEN, null, 1, "z-a", false)
        ).isInstanceOf(IllegalStateException.class).hasMessageContaining("identity conflict");
    }

    @Test
    void setVariantImageUrl_nullProductOrBlankInputs_returnsFalse() {
        assertThat(support.setVariantImageUrl(null, "SKU", "https://cdn.example.com/a.jpg")).isFalse();
        Product product = variantProduct("[{\"sku\":\"I\",\"size\":\"S\",\"color\":\"Red\",\"rate\":1,\"stock\":1,\"active\":true}]");
        assertThat(support.setVariantImageUrl(product, "I", "  ")).isFalse();
    }

    @Test
    void setVariantImages_multipleUrls_setsHeroAndExtras() {
        Product product = variantProduct("[{\"sku\":\"M\",\"size\":\"S\",\"color\":\"Red\",\"rate\":1,\"stock\":1,\"active\":true}]");
        assertThat(
            support.setVariantImages(
                product,
                "M",
                List.of("https://cdn.example.com/hero.jpg", "https://cdn.example.com/rear.jpg")
            )
        ).isTrue();
        VariantRow row = support.findBySku(support.parseProduct(product), "M").orElseThrow();
        assertThat(row.imageUrl()).isEqualTo("https://cdn.example.com/hero.jpg");
        assertThat(row.imageUrls()).containsExactly("https://cdn.example.com/rear.jpg");
    }

    @Test
    void clearAllVariantImages_clearsExtrasOnlyRows() {
        Product product = variantProduct(
            """
            [{"sku":"E","size":"S","color":"Red","rate":1,"stock":1,"active":true,"imageUrls":["https://cdn.example.com/extra.jpg"]}]
            """
        );
        support.clearAllVariantImages(product);
        VariantRow row = support.findBySku(support.parseProduct(product), "E").orElseThrow();
        assertThat(row.imageUrl()).isNull();
        assertThat(row.imageUrls()).isEmpty();
    }

    @Test
    void clearAllVariantImages_noImagesOnRows_isNoOp() {
        Product product = variantProduct("[{\"sku\":\"B\",\"size\":\"S\",\"color\":\"Red\",\"rate\":1,\"stock\":1,\"active\":true}]");
        String before = product.getVariantsJson();
        support.clearAllVariantImages(product);
        assertThat(product.getVariantsJson()).isEqualTo(before);
    }

    @Test
    void deactivateVariantsNotInPulledSet_singleArg_deactivatesUnlinkedLocals() {
        Product product = variantProduct(
            """
            [{"sku":"LOCAL-DROP","size":"S","color":"Red","rate":1,"stock":1,"active":true}]
            """
        );
        assertThat(support.deactivateVariantsNotInPulledSet(product, Set.of("OTHER"))).isEqualTo(1);
        assertThat(support.findBySku(support.parseProduct(product), "LOCAL-DROP").orElseThrow().active()).isFalse();
    }

    @Test
    void deactivateVariantsNotInPulledSet_allPresent_returnsZeroWithoutRewrite() {
        Product product = variantProduct(
            """
            [{"sku":"KEEP","size":"S","color":"Red","rate":1,"stock":1,"active":true,"zohoItemId":"z-keep"}]
            """
        );
        String before = product.getVariantsJson();
        assertThat(support.deactivateVariantsNotInPulledSet(product, Set.of("KEEP"), Set.of("z-keep"))).isZero();
        assertThat(product.getVariantsJson()).isEqualTo(before);
    }

    @Test
    void deactivateVariantsNotInPulledSet_nullPulledItemIds_doesNotMatchByItemId() {
        Product product = variantProduct(
            """
            [{"sku":"BY-ID","size":"S","color":"Red","rate":1,"stock":1,"active":true,"zohoItemId":"z-only"}]
            """
        );
        assertThat(support.deactivateVariantsNotInPulledSet(product, Set.of("missing"), null)).isEqualTo(1);
    }

    @Test
    void variantRow_toMap_emitsAllOptionalFields() {
        VariantRow row = new VariantRow(
            "id-1",
            "SKU",
            "S",
            "Red",
            new BigDecimal("10"),
            new BigDecimal("20"),
            new BigDecimal("5"),
            3,
            true,
            "https://cdn.example.com/sw.png",
            "z-9",
            "https://cdn.example.com/hero.jpg",
            List.of("https://cdn.example.com/rear.jpg")
        );
        Map<String, Object> map = row.toMap();
        assertThat(map)
            .containsEntry("id", "id-1")
            .containsEntry("size", "S")
            .containsEntry("color", "Red")
            .containsEntry("mrp", new BigDecimal("20"))
            .containsEntry("swatchUrl", "https://cdn.example.com/sw.png")
            .containsEntry("zohoItemId", "z-9");
    }

    @Test
    void variantRow_fromMap_coversSnakeCaseImageUrlsAndLegacyKeys() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("sku", "S");
        map.put("size", "M");
        map.put("rate", 1);
        map.put("stock", 1);
        map.put("active", "yes");
        map.put("image_urls", List.of("https://cdn.example.com/a.jpg", 404));
        VariantRow row = VariantRow.fromMap(map);
        assertThat(row.active()).isTrue();
        assertThat(row.imageUrls()).contains("https://cdn.example.com/a.jpg");
    }

    @Test
    void tryParse_doubleWrappedJsonArray_parsesInnerList() throws Exception {
        String inner = "[{\"sku\":\"W\",\"size\":\"S\",\"color\":\"Red\",\"rate\":1,\"stock\":1,\"active\":true}]";
        String wrapped = new ObjectMapper().writeValueAsString(inner);
        assertThat(support.tryParse(wrapped)).isPresent();
        assertThat(support.tryParse(wrapped).orElseThrow()).hasSize(1);
    }

    @Test
    void sizes_skipsBlankSizeLabels() {
        List<VariantRow> rows = support.parse(
            """
            [{"sku":"A","size":"  ","color":"Red","rate":1,"stock":1,"active":true},
             {"sku":"B","size":"M","color":"Blue","rate":1,"stock":1,"active":true}]
            """
        );
        assertThat(support.sizes(rows)).containsExactly("M");
    }

    @Test
    void aggregates_allZeroSelling_leavesMinSellingNull() {
        Aggregates agg = support.aggregates(
            List.of(new VariantRow("1", "A", "S", "Red", BigDecimal.ZERO, null, null, 1, true, null, null, null, List.of()))
        );
        assertThat(agg.minSelling()).isNull();
    }

    @Test
    void variantSellingPrice_zeroMrpAndRate_returnsZero() {
        VariantRow row = new VariantRow("1", "A", "S", "Red", BigDecimal.ZERO, BigDecimal.ZERO, null, 1, true, null, null, null, List.of());
        assertThat(support.variantSellingPrice(row)).isEqualByComparingTo("0");
    }

    @Test
    void applyRuntimePricing_setsPriceWithoutCompareAtWhenNoMrp() {
        ProductDTO dto = new ProductDTO();
        dto.setItemStructure("VARIANTS");
        dto.setVariantsJson("[{\"sku\":\"A\",\"size\":\"S\",\"color\":\"Red\",\"rate\":125,\"price\":125,\"mrp\":0,\"stock\":1,\"active\":true}]");
        support.applyRuntimePricing(dto);
        assertThat(dto.getPrice()).isEqualByComparingTo("125");
        assertThat(dto.getCompareAtPrice()).isNull();
    }

    @Test
    void applyMatrixToProduct_usesStoredVariantsJsonWhenArgumentNull() {
        Product product = new Product()
            .sku("P")
            .itemStructure("VARIANTS")
            .variantsJson("[{\"sku\":\"A\",\"size\":\"S\",\"color\":\"Red\",\"rate\":10,\"stock\":2,\"active\":true}]");
        assertThat(support.applyMatrixToProduct(product, "VARIANTS", null)).isTrue();
        assertThat(product.getStock()).isEqualTo(2);
    }

    @Test
    void mergeColorsPreservingGalleries_emptyColorNames_returnsPreviousJson() throws Exception {
        String previous = "[{\"name\":\"Navy\",\"imageUrls\":[\"https://cdn.example.com/n.jpg\"]}]";
        assertThat(support.mergeColorsPreservingGalleries(previous, List.of())).isEqualTo(previous);
    }

    @Test
    void setVariantImageUrl_existingRowWithoutExtras_setsHeroOnly() {
        Product product = variantProduct("[{\"sku\":\"SOLO\",\"size\":\"S\",\"color\":\"Red\",\"rate\":1,\"stock\":1,\"active\":true}]");
        assertThat(support.setVariantImageUrl(product, "SOLO", "https://cdn.example.com/new.jpg")).isTrue();
        VariantRow row = support.findBySku(support.parseProduct(product), "SOLO").orElseThrow();
        assertThat(row.imageUrl()).isEqualTo("https://cdn.example.com/new.jpg");
        assertThat(row.imageUrls()).isEmpty();
    }

    @Test
    void anyVariantSkuInSet_simpleProduct_returnsFalse() {
        Product simple = new Product().itemStructure("SIMPLE");
        assertThat(support.anyVariantSkuInSet(simple, Set.of("A"))).isFalse();
    }

    @Test
    void deactivateVariantBySku_missingSku_returnsFalse() {
        Product product = variantProduct("[{\"sku\":\"A\",\"size\":\"S\",\"color\":\"Red\",\"rate\":1,\"stock\":1,\"active\":true}]");
        assertThat(support.deactivateVariantBySku(product, "MISSING")).isFalse();
    }

    @Test
    void deactivateVariantByZohoItemId_missingId_returnsFalse() {
        Product product = variantProduct("[{\"sku\":\"A\",\"size\":\"S\",\"color\":\"Red\",\"rate\":1,\"stock\":1,\"active\":true}]");
        assertThat(support.deactivateVariantByZohoItemId(product, "z-missing")).isFalse();
    }

    @Test
    void clearAllVariantImages_nonVariantProduct_isNoOp() {
        Product simple = new Product().itemStructure("SIMPLE");
        support.clearAllVariantImages(simple);
    }

    @Test
    void upsertPulledVariantFromZoho_updatesPricingAndDimensions() {
        Product product = variantProduct(
            """
            [{"sku":"OLD","size":"S","color":"Red","rate":10,"mrp":20,"stock":1,"active":true,"zohoItemId":"z-old"}]
            """
        );
        assertThat(
            support.upsertPulledVariantFromZoho(product, "NEW", "M", "Blue", new BigDecimal("99"), new BigDecimal("120"), 5, "z-old", false)
        ).isTrue();
        VariantRow row = support.findByZohoItemId(support.parseProduct(product), "z-old").orElseThrow();
        assertThat(row.sku()).isEqualTo("NEW");
        assertThat(row.size()).isEqualTo("M");
        assertThat(row.color()).isEqualTo("Blue");
        assertThat(row.rate()).isEqualByComparingTo("99");
    }

    @Test
    void variantRow_fromMap_legacyRateTreatedAsMrp() {
        VariantRow row = VariantRow.fromMap(Map.of("sku", "L", "size", "S", "rate", 500, "stock", 1, "active", true));
        assertThat(row.mrp()).isEqualByComparingTo("500");
        assertThat(row.rate()).isEqualByComparingTo("0");
    }

    @Test
    void variantRow_toMap_omitsBlankOptionalFieldsAndDedupesHeroFromExtras() {
        VariantRow row = new VariantRow(
            null,
            "SKU",
            null,
            null,
            BigDecimal.ONE,
            null,
            null,
            1,
            true,
            " ",
            " ",
            "https://cdn.example.com/hero.jpg",
            List.of("https://cdn.example.com/hero.jpg", " ", "https://cdn.example.com/rear.jpg")
        );
        Map<String, Object> map = row.toMap();
        assertThat(map).containsOnlyKeys("sku", "rate", "cost", "stock", "active", "imageUrl", "imageUrls");
        @SuppressWarnings("unchecked")
        List<String> extras = (List<String>) map.get("imageUrls");
        assertThat(extras).containsExactly("https://cdn.example.com/rear.jpg");
    }

    @Test
    void variantRow_imageUrlsValue_usesSnakeCaseList() {
        VariantRow row = VariantRow.fromMap(
            Map.of("sku", "S", "size", "M", "rate", 1, "stock", 1, "active", true, "image_urls", List.of())
        );
        assertThat(row.imageUrls()).isEmpty();
    }

    @Test
    void variantRow_toBoolean_emptyStringUsesDefaultTrue() {
        VariantRow row = VariantRow.fromMap(Map.of("sku", "S", "size", "M", "rate", 1, "stock", 1, "active", "  "));
        assertThat(row.active()).isTrue();
    }

    @Test
    void setContainsIgnoreCase_exercisedViaReflection() {
        assertThat((Boolean) ReflectionTestUtils.invokeMethod(ProductVariantSupport.class, "setContainsIgnoreCase", Set.of("A"), null)).isFalse();
        assertThat((Boolean) ReflectionTestUtils.invokeMethod(ProductVariantSupport.class, "setContainsIgnoreCase", null, "a")).isFalse();
        assertThat((Boolean) ReflectionTestUtils.invokeMethod(ProductVariantSupport.class, "setContainsIgnoreCase", Set.of(" Mix "), "mix")).isTrue();
    }

    private Product variantProduct(String json) {
        Product product = new Product().sku("P").price(BigDecimal.ZERO).stock(0);
        support.applyMatrixToProduct(product, "VARIANTS", json);
        return product;
    }
}
