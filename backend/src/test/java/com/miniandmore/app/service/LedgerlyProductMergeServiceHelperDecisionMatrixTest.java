package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.domain.Product;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class LedgerlyProductMergeServiceHelperDecisionMatrixTest {

    @Mock
    private LedgerlyItemImageSync itemImageSync;

    @Mock
    private ProductVariantSupport variantSupport;

    @Mock
    private StorePricingService storePricingService;

    private ProductDescriptionCodec descriptionCodec;
    private ProductColorImagesSupport colorImagesSupport;
    private LedgerlyProductMergeService service;

    @BeforeEach
    void createService() {
        ObjectMapper mapper = new ObjectMapper();
        descriptionCodec = new ProductDescriptionCodec(mapper);
        colorImagesSupport = new ProductColorImagesSupport(mapper);
        service = new LedgerlyProductMergeService(
            descriptionCodec,
            itemImageSync,
            variantSupport,
            colorImagesSupport,
            storePricingService
        );
        lenient().when(storePricingService.sellingFromMrp(any())).thenReturn(new BigDecimal("80"));
        lenient().when(storePricingService.mrpFromSelling(any())).thenReturn(new BigDecimal("125"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("conversionRows")
    void privateNumericConverters_coverNullNativeAndTextValues(
        String label,
        String method,
        Object input,
        Object expected
    ) {
        Object actual = ReflectionTestUtils.invokeMethod(service, method, new Object[] { input });

        assertThat(actual).as(label).isEqualTo(expected);
    }

    private static Stream<Arguments> conversionRows() {
        return Stream.of(
            Arguments.of("decimal null", "toBigDecimal", null, BigDecimal.ZERO),
            Arguments.of("decimal identity", "toBigDecimal", new BigDecimal("4.25"), new BigDecimal("4.25")),
            Arguments.of("decimal text", "toBigDecimal", "6.75", new BigDecimal("6.75")),
            Arguments.of("integer null", "toInteger", null, 0),
            Arguments.of("integer number", "toInteger", 7.8d, 7),
            Arguments.of("integer text", "toInteger", "9.9", 9),
            Arguments.of("nullable integer null", "intValue", null, null),
            Arguments.of("nullable integer number", "intValue", 11L, 11),
            Arguments.of("nullable integer blank", "intValue", "  ", null),
            Arguments.of("nullable integer text", "intValue", "13.8", 13)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("pricingRows")
    void applyPricingFromItem_coversPrecedenceAndFallbackMatrix(
        String label,
        Map<String, Object> item,
        ProductDescriptionCodec.Decoded decoded,
        BigDecimal expectedSelling,
        BigDecimal expectedMrp
    ) {
        Product product = new Product();

        ReflectionTestUtils.invokeMethod(service, "applyPricingFromItem", product, item, decoded);

        if (expectedSelling == null) {
            assertThat(product.getPrice()).as(label).isNull();
        } else {
            assertThat(product.getPrice()).as(label).isEqualByComparingTo(expectedSelling);
        }
        if (expectedMrp == null) {
            assertThat(product.getCompareAtPrice()).as(label).isNull();
        } else {
            assertThat(product.getCompareAtPrice()).as(label).isEqualByComparingTo(expectedMrp);
        }
    }

    private static Stream<Arguments> pricingRows() {
        return Stream.of(
            Arguments.of("no price data leaves product unchanged", Map.of(), null, null, null),
            Arguments.of("positive MRP and selling are kept", Map.of("mrp", 900, "price", 500), null, bd("500"), bd("900")),
            Arguments.of("zero MRP falls through to compareAtPrice", Map.of("mrp", 0, "compareAtPrice", 850, "rate", 450), null, bd("450"), bd("850")),
            Arguments.of("MRP without positive selling uses store price", Map.of("mrp", 700, "price", 0), null, bd("80"), bd("700")),
            Arguments.of("selling without MRP derives storefront MRP", Map.of("price", 400), null, bd("400"), bd("125")),
            Arguments.of("decoded compare-at fills absent item MRP", Map.of(), decodedWithMrp("600"), bd("80"), bd("600")),
            Arguments.of("nonpositive decoded compare-at is ignored", Map.of("rate", 0), decodedWithMrp("0"), null, null)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("ageRows")
    void applyAgeFromItem_coversExplicitAndDecodedFallbacks(
        String label,
        Map<String, Object> item,
        ProductDescriptionCodec.Decoded decoded,
        Integer min,
        Integer max
    ) {
        Product product = new Product();

        ReflectionTestUtils.invokeMethod(service, "applyAgeFromItem", product, item, decoded);

        assertThat(product.getAgeMinMonths()).as(label).isEqualTo(min);
        assertThat(product.getAgeMaxMonths()).as(label).isEqualTo(max);
    }

    private static Stream<Arguments> ageRows() {
        return Stream.of(
            Arguments.of("explicit numeric ages", Map.of("ageMinMonths", 1, "ageMaxMonths", "12"), null, 1, 12),
            Arguments.of("explicit minimum only", Map.of("ageMinMonths", 3), decodedWithAges(8, 20), 3, null),
            Arguments.of("decoded ages fill fully absent values", Map.of(), decodedWithAges(4, 18), 4, 18),
            Arguments.of("decoded null members stay absent", Map.of(), decodedWithAges(null, null), null, null),
            Arguments.of("no decoded metadata stays absent", Map.of(), null, null, null)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("sellingPriceRows")
    void storeSellingPrice_coversPriceMrpAndZeroFallback(
        String label,
        BigDecimal price,
        BigDecimal mrp,
        BigDecimal expected
    ) {
        Product product = new Product().price(price).compareAtPrice(mrp);

        assertThat(service.storeSellingPrice(product)).as(label).isEqualByComparingTo(expected);
    }

    private static Stream<Arguments> sellingPriceRows() {
        return Stream.of(
            Arguments.of("positive selling wins", bd("50"), bd("100"), bd("50")),
            Arguments.of("zero selling falls back to MRP", BigDecimal.ZERO, bd("100"), bd("80")),
            Arguments.of("null selling falls back to MRP", null, bd("100"), bd("80")),
            Arguments.of("zero MRP produces zero", null, BigDecimal.ZERO, BigDecimal.ZERO),
            Arguments.of("fully absent pricing produces zero", null, null, BigDecimal.ZERO)
        );
    }

    @Test
    void applyVariantsFromItem_exercisesCarryForwardLoopBranches() {
        Product product = new Product().sku("PARENT").itemStructure(ProductVariantSupport.STRUCTURE_VARIANTS);
        ProductVariantSupport.VariantRow oldMatch = row("MATCH", "S", "Blue", "one.jpg", List.of("two.jpg"));
        ProductVariantSupport.VariantRow oldChanged = row("CHANGED", "S", "Blue", "stale.jpg", List.of());
        ProductVariantSupport.VariantRow oldNoPhotos = row("NO-PHOTOS", "S", "Blue", null, List.of());
        List<ProductVariantSupport.VariantRow> before = List.of(oldMatch, oldChanged, oldNoPhotos);
        List<ProductVariantSupport.VariantRow> after = List.of(
            row(null, "S", "Blue", null, List.of()),
            row("NEW", "S", "Blue", null, List.of()),
            row("CHANGED", "M", "Blue", null, List.of()),
            row("NO-PHOTOS", "S", "Blue", null, List.of()),
            row("MATCH", "S", "Blue", null, List.of())
        );
        when(variantSupport.coerceVariantsJson("matrix")).thenReturn("matrix");
        when(variantSupport.parseProduct(product)).thenReturn(before, after);
        when(variantSupport.applyMatrixToProduct(product, ProductVariantSupport.STRUCTURE_VARIANTS, "matrix")).thenReturn(true);

        ReflectionTestUtils.invokeMethod(
            service,
            "applyVariantsFromItem",
            product,
            Map.of("itemStructure", ProductVariantSupport.STRUCTURE_VARIANTS, "variantsJson", "matrix")
        );

        verify(variantSupport).setVariantImages(product, "MATCH", List.of("one.jpg", "two.jpg"));
        verify(variantSupport, never()).setVariantImages(product, "CHANGED", List.of("stale.jpg"));
        verify(variantSupport, never()).setVariantImages(eq(product), eq("NO-PHOTOS"), any());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("variantInferenceRows")
    void applyVariantsFromItem_inferenceAndRejectedMatrixMatrix(
        String label,
        Map<String, Object> item,
        String coerced,
        boolean applied,
        String initialStructure,
        String expectedStructure
    ) {
        Product product = new Product().sku("PARENT").itemStructure(initialStructure);
        Object raw = item.containsKey("variantsJson") ? item.get("variantsJson") : item.get("variants_json");
        if (raw != null || item.containsKey("itemStructure") || item.containsKey("item_structure")) {
            when(variantSupport.coerceVariantsJson(raw)).thenReturn(coerced);
            when(variantSupport.parseProduct(product)).thenReturn(List.of());
            String requested = item.containsKey("itemStructure")
                ? item.get("itemStructure").toString()
                : item.containsKey("item_structure") ? item.get("item_structure").toString() : expectedStructure;
            when(variantSupport.applyMatrixToProduct(product, requested, coerced)).thenReturn(applied);
        }

        ReflectionTestUtils.invokeMethod(service, "applyVariantsFromItem", product, item);

        assertThat(product.getItemStructure()).as(label).isEqualTo(expectedStructure);
    }

    private static Stream<Arguments> variantInferenceRows() {
        return Stream.of(
            Arguments.of("null variants value is treated as absent", mapWith("variantsJson", null), null, false, null, null),
            Arguments.of("blank variants infer simple", Map.of("variantsJson", " "), " ", false, null, ProductVariantSupport.STRUCTURE_SIMPLE),
            Arguments.of("empty array infers simple", Map.of("variants_json", "[]"), "[]", false, null, ProductVariantSupport.STRUCTURE_SIMPLE),
            Arguments.of(
                "nonempty variants infer variants",
                Map.of("variantsJson", "[{}]"),
                "[{}]",
                false,
                null,
                ProductVariantSupport.STRUCTURE_VARIANTS
            ),
            Arguments.of(
                "rejected matrix preserves existing structure",
                Map.of("item_structure", ProductVariantSupport.STRUCTURE_VARIANTS, "variants_json", "bad"),
                "bad",
                false,
                ProductVariantSupport.STRUCTURE_SIMPLE,
                ProductVariantSupport.STRUCTURE_SIMPLE
            )
        );
    }

    @Test
    void mergeLedgerlyItem_blankOptionalFieldsAndEmptyColorImports_areIgnored() {
        Product product = new Product().sku("SKU").ledgerlyItemId("existing").stock(5);
        Map<String, Object> item = new HashMap<>();
        item.put("ledgerlyItemId", " ");
        item.put("imageUrl", " ");
        item.put("colorImagesJson", Map.of("Blue", List.of("remote.jpg")));
        when(itemImageSync.importColorImages(product, Map.of("Blue", List.of("remote.jpg")))).thenReturn(Map.of());

        service.mergeLedgerlyItem(product, item, name -> java.util.Optional.empty(), null);

        assertThat(product.getLedgerlyItemId()).isEqualTo("existing");
        assertThat(product.getStock()).isEqualTo(5);
        assertThat(product.getColorImagesJson()).isNull();
        verify(itemImageSync, never()).importItemImage(any(), any());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("pushRows")
    void buildPushPayload_optionalFieldMatrix(String label, Product product, String category, List<String> expectedKeys, List<String> absentKeys) {
        when(itemImageSync.firstImageUrl(product.getImagesJson())).thenReturn(null);

        Map<String, Object> payload = service.buildPushPayload(product, category);

        assertThat(payload.keySet()).as(label).containsAll(expectedKeys);
        if (!absentKeys.isEmpty()) {
            assertThat(payload.keySet()).as(label).doesNotContainAnyElementsOf(absentKeys);
        }
    }

    private static Stream<Arguments> pushRows() {
        Product sparse = new Product().id(8L).price(BigDecimal.ZERO).stock(0).units(" ").active(false);
        Product rich = new Product()
            .sku("RICH")
            .price(bd("10"))
            .compareAtPrice(bd("20"))
            .stock(2)
            .units(" pair ")
            .description(" text ")
            .fabricType(" cotton ")
            .ageMinMonths(1)
            .ageMaxMonths(2)
            .itemStructure(ProductVariantSupport.STRUCTURE_VARIANTS)
            .variantsJson("[]")
            .hsn(" 6109 ")
            .taxPct(bd("5"))
            .active(true);
        return Stream.of(
            Arguments.of(
                "sparse payload uses defaults",
                sparse,
                " ",
                List.of("sku", "price", "unit", "active", "itemStructure"),
                List.of("mrp", "description", "category", "fabricType", "variantsJson", "hsn", "taxPct")
            ),
            Arguments.of(
                "rich payload trims and includes optionals",
                rich,
                " Baby ",
                List.of("mrp", "compareAtPrice", "description", "category", "fabricType", "ageMinMonths", "ageMaxMonths", "variantsJson", "hsn", "taxPct"),
                List.of()
            )
        );
    }

    private static ProductVariantSupport.VariantRow row(
        String sku,
        String size,
        String color,
        String imageUrl,
        List<String> imageUrls
    ) {
        return new ProductVariantSupport.VariantRow(
            null,
            sku,
            size,
            color,
            BigDecimal.TEN,
            null,
            null,
            1,
            true,
            null,
            null,
            imageUrl,
            imageUrls
        );
    }

    private static ProductDescriptionCodec.Decoded decodedWithMrp(String mrp) {
        return new ProductDescriptionCodec.Decoded(null, null, List.of(), List.of(), null, null, null, null, bd(mrp), null);
    }

    private static ProductDescriptionCodec.Decoded decodedWithAges(Integer min, Integer max) {
        return new ProductDescriptionCodec.Decoded(null, null, List.of(), List.of(), min, max, null, null, null, null);
    }

    private static Map<String, Object> mapWith(String key, Object value) {
        Map<String, Object> map = new HashMap<>();
        map.put(key, value);
        return map;
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
