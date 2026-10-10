package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.miniandmore.app.client.ZohoBooksClient;
import com.miniandmore.app.config.ApplicationProperties;
import com.miniandmore.app.domain.Category;
import com.miniandmore.app.domain.Product;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ZohoProductFieldMapperTest {

    @Mock
    private ZohoBooksClient booksClient;

    @Mock
    private LedgerlyCategoryResolver categoryResolver;

    private ZohoProductFieldMapper mapper;

    @BeforeEach
    void setUp() {
        ApplicationProperties properties = new ApplicationProperties();
        ProductDescriptionCodec descriptionCodec = new ProductDescriptionCodec(new ObjectMapper());
        mapper = new ZohoProductFieldMapper(booksClient, properties, descriptionCodec, categoryResolver);
        lenient().when(booksClient.listItemFields()).thenReturn(sampleFields());
    }

    @Test
    void discoveryFailureIsRetriedAndNeverReportedAsFieldlessSuccess() {
        when(booksClient.listItemFields()).thenThrow(new IllegalStateException("temporary outage")).thenReturn(sampleFields());
        Product product = new Product().fabricType("Cotton");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> mapper.enrichPushPayload(product, new LinkedHashMap<>()))
            .isInstanceOf(IllegalStateException.class);
        Map<String, Object> payload = new LinkedHashMap<>();
        mapper.enrichPushPayload(product, payload);
        assertThat(payload).containsKey("custom_fields");
        org.mockito.Mockito.verify(booksClient, org.mockito.Mockito.times(2)).listItemFields();
    }

    @Test
    void changingOrganizationInvalidatesFieldCache() {
        when(booksClient.itemFieldCacheScope()).thenReturn("in/org-a", "in/org-b");
        mapper.enrichPushPayload(new Product(), new LinkedHashMap<>());
        mapper.enrichPushPayload(new Product(), new LinkedHashMap<>());
        org.mockito.Mockito.verify(booksClient, org.mockito.Mockito.times(2)).listItemFields();
    }

    @Test
    void expiredFieldDefinitionsAreRefreshed() {
        mapper.enrichPushPayload(new Product(), new LinkedHashMap<>());
        org.springframework.test.util.ReflectionTestUtils.setField(mapper, "cacheExpiresAt", java.time.Instant.MIN);
        mapper.enrichPushPayload(new Product(), new LinkedHashMap<>());
        org.mockito.Mockito.verify(booksClient, org.mockito.Mockito.times(2)).listItemFields();
    }

    @Test
    void enrichPushPayload_mapsMrpFabricSizesAndMandatoryAge() {
        Product product = new Product()
            .fabricType("Cotton")
            .compareAtPrice(BigDecimal.valueOf(699))
            .sizesJson("[\"0-3 M\",\"3-6 M\"]")
            .ageMinMonths(0)
            .ageMaxMonths(6);

        Map<String, Object> payload = new LinkedHashMap<>();
        mapper.enrichPushPayload(product, payload);

        assertThat(payload.get("label_rate")).isEqualTo("699");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> customFields = (List<Map<String, Object>>) payload.get("custom_fields");
        assertThat(customFields).hasSize(4);
        assertThat(customFields).anySatisfy(entry -> {
            assertThat(entry.get("customfield_id")).isEqualTo("4063993000000072061");
            assertThat(entry.get("value")).isEqualTo("Cotton");
        });
        assertThat(customFields).anySatisfy(entry -> {
            assertThat(entry.get("customfield_id")).isEqualTo("4063993000000072065");
            assertThat(entry.get("value")).isEqualTo("0");
        });
        assertThat(customFields).anySatisfy(entry -> {
            assertThat(entry.get("customfield_id")).isEqualTo("4063993000000072092");
            assertThat(entry.get("value")).isEqualTo("6");
        });
        assertThat(customFields).anySatisfy(entry -> {
            assertThat(entry.get("customfield_id")).isEqualTo("4063993000000072088");
            assertThat(entry.get("value")).isEqualTo("0-3 M, 3-6 M");
        });
    }

    @Test
    void enrichPushPayload_sendsZeroAgeWhenMandatoryAndMissing() {
        Product product = new Product().sku("SKU-1").name("Test");

        Map<String, Object> payload = new LinkedHashMap<>();
        mapper.enrichPushPayload(product, payload);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> customFields = (List<Map<String, Object>>) payload.get("custom_fields");
        assertThat(customFields).anySatisfy(entry -> {
            assertThat(entry.get("customfield_id")).isEqualTo("4063993000000072065");
            assertThat(entry.get("value")).isEqualTo("0");
        });
    }

    @Test
    void enrichPushPayload_mapsCategoryFromProduct() {
        Product product = new Product().category(new Category().name("Bodysuits"));

        Map<String, Object> payload = new LinkedHashMap<>();
        mapper.enrichPushPayload(product, payload);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> customFields = (List<Map<String, Object>>) payload.get("custom_fields");
        assertThat(customFields).anySatisfy(entry -> {
            assertThat(entry.get("customfield_id")).isEqualTo("4063993000000072096");
            assertThat(entry.get("value")).isEqualTo("Bodysuits");
        });
    }

    @Test
    void enrichPushPayload_prefersEntityCustomFieldIdWhenZohoReturnsBothIds() {
        Map<String, Object> categoryField = new LinkedHashMap<>(sampleFields().get(5));
        categoryField.put("field_id", "generic-settings-field");
        categoryField.put("customfield_id", "item-custom-field");
        when(booksClient.listItemFields()).thenReturn(List.of(categoryField));
        Product product = new Product().category(new Category().name("Bodysuits"));

        Map<String, Object> payload = new LinkedHashMap<>();
        mapper.enrichPushPayload(product, payload);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> customFields = (List<Map<String, Object>>) payload.get("custom_fields");
        assertThat(customFields).containsExactly(Map.of("customfield_id", "item-custom-field", "value", "Bodysuits"));
    }

    @Test
    void enrichPushPayload_prefersNativeZohoCategoryIdOverCategoryCustomField() {
        Product product = new Product().category(new Category().name("Bodysuits").zohoCategoryId("zoho-category-1"));

        Map<String, Object> payload = new LinkedHashMap<>();
        mapper.enrichPushPayload(product, payload);

        assertThat(payload).containsEntry("category_id", "zoho-category-1");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> customFields = (List<Map<String, Object>>) payload.get("custom_fields");
        assertThat(customFields).noneMatch(field -> "4063993000000072096".equals(field.get("customfield_id")));
    }

    @Test
    void applyPulledItem_mapsCategoryWhenAutoCreateEnabled() {
        Product product = new Product();
        Category category = new Category().name("Swaddles");
        when(categoryResolver.resolve(eq("Swaddles"), anyBoolean())).thenReturn(Optional.of(category));

        Optional<String> notice = mapper.applyPulledItem(
            product,
            Map.of(
                "custom_fields",
                List.of(
                    Map.of(
                        "customfield_id",
                        "4063993000000072096",
                        "api_name",
                        "cf__sb_60082852067_category",
                        "value",
                        "Swaddles"
                    )
                )
            ),
            true
        );

        assertThat(product.getCategory()).isEqualTo(category);
        assertThat(notice).contains("Assigned category Swaddles");
    }

    @Test
    void applyPulledItem_mapsNativeZohoCategoryNameWhenCustomFieldIsAbsent() {
        Product product = new Product();
        Category category = new Category().name("Jablas");
        when(categoryResolver.resolve("Jablas", true)).thenReturn(Optional.of(category));

        Optional<String> notice = mapper.applyPulledItem(product, Map.of("category_name", "Jablas"), true);

        assertThat(product.getCategory()).isEqualTo(category);
        assertThat(notice).contains("Assigned category Jablas");
    }

    @Test
    void applyPulledItem_retainsNativeZohoCategoryId() {
        Product product = new Product();
        Category category = new Category().name("Jablas");
        when(categoryResolver.resolve("Jablas", true)).thenReturn(Optional.of(category));

        mapper.applyPulledItem(
            product,
            Map.of("category_id", "zoho-category-2", "category_name", "Jablas"),
            true
        );

        assertThat(product.getCategory()).isEqualTo(category);
        assertThat(category.getZohoCategoryId()).isEqualTo("zoho-category-2");
    }

    @Test
    void applyPulledItem_mapsNestedNativeZohoCategory() {
        Product product = new Product();
        Category category = new Category().name("Rompers");
        when(categoryResolver.resolve("Rompers", false)).thenReturn(Optional.of(category));

        mapper.applyPulledItem(product, Map.of("category", Map.of("name", "Rompers")), false);

        assertThat(product.getCategory()).isEqualTo(category);
    }

    @Test
    void applyPulledItem_mapsMrpFabricSizesAndAge() {
        Product product = new Product();
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("label_rate", 888);
        item.put(
            "custom_fields",
            List.of(
                Map.of(
                    "customfield_id",
                    "4063993000000072061",
                    "api_name",
                    "cf__sb_60082852067_fabric_type",
                    "value",
                    "Fleece"
                ),
                Map.of(
                    "customfield_id",
                    "4063993000000072065",
                    "api_name",
                    "cf__sb_60082852067_age",
                    "value",
                    "6"
                ),
                Map.of(
                    "customfield_id",
                    "4063993000000072092",
                    "api_name",
                    "cf__sb_60082852067_age_max",
                    "value",
                    "18"
                ),
                Map.of(
                    "customfield_id",
                    "4063993000000072088",
                    "api_name",
                    "cf__sb_60082852067_sizes",
                    "value",
                    "S, M"
                )
            )
        );

        Optional<String> notice = mapper.applyPulledItem(product, item, false);

        assertThat(notice).isEmpty();
        assertThat(product.getCompareAtPrice()).isEqualByComparingTo("888");
        assertThat(product.getFabricType()).isEqualTo("Fleece");
        assertThat(product.getAgeMinMonths()).isEqualTo(6);
        assertThat(product.getAgeMaxMonths()).isEqualTo(18);
        assertThat(product.getSizesJson()).contains("S").contains("M");
    }

    @Test
    void enrichPushPayload_noOpForNullInputs() {
        Map<String, Object> payload = new LinkedHashMap<>();
        mapper.enrichPushPayload(null, payload);
        mapper.enrichPushPayload(new Product(), null);
        assertThat(payload).isEmpty();
    }

    @Test
    void enrichPushPayload_mapsHsnAndConfiguredGstTaxId() {
        ApplicationProperties properties = new ApplicationProperties();
        properties.getZoho().setGstTaxId("gst-tax-123");
        ProductDescriptionCodec descriptionCodec = new ProductDescriptionCodec(new ObjectMapper());
        ZohoProductFieldMapper gstMapper = new ZohoProductFieldMapper(booksClient, properties, descriptionCodec, categoryResolver);
        when(booksClient.listItemFields()).thenReturn(sampleFields());

        Product product = new Product().hsn("6111").compareAtPrice(BigDecimal.valueOf(599));
        Map<String, Object> payload = new LinkedHashMap<>();
        gstMapper.enrichPushPayload(product, payload);

        assertThat(payload).containsEntry("hsn_or_sac", "6111");
        assertThat(payload).containsEntry("tax_id", "gst-tax-123");
        assertThat(payload).containsEntry("label_rate", "599");
    }

    @Test
    void invalidateFieldCache_forcesFieldRediscovery() {
        mapper.enrichPushPayload(new Product(), new LinkedHashMap<>());
        mapper.invalidateFieldCache();
        mapper.enrichPushPayload(new Product().fabricType("Linen"), new LinkedHashMap<>());
        org.mockito.Mockito.verify(booksClient, org.mockito.Mockito.times(2)).listItemFields();
    }

    @Test
    void applyPulledItem_emptyOrNullInputsReturnEmpty() {
        assertThat(mapper.applyPulledItem(null, Map.of("sku", "X"), false)).isEmpty();
        assertThat(mapper.applyPulledItem(new Product(), null, false)).isEmpty();
        assertThat(mapper.applyPulledItem(new Product(), Map.of(), false)).isEmpty();
    }

    @Test
    void applyPulledItem_readsCustomFieldValueByFieldId() {
        Product product = new Product();
        mapper.applyPulledItem(
            product,
            Map.of(
                "custom_fields",
                List.of(Map.of("customfield_id", "4063993000000072061", "value", "Muslin"))
            ),
            false
        );
        assertThat(product.getFabricType()).isEqualTo("Muslin");
    }

    @Test
    void applyPulledItem_parsesAgeRangePrefixFromDashValue() {
        Product product = new Product();
        Map<String, Object> item = new LinkedHashMap<>();
        item.put(
            "custom_fields",
            List.of(
                Map.of(
                    "customfield_id",
                    "4063993000000072065",
                    "api_name",
                    "cf__sb_60082852067_age",
                    "value",
                    "6-12"
                )
            )
        );
        mapper.applyPulledItem(product, item, false);
        assertThat(product.getAgeMinMonths()).isEqualTo(6);
    }

    @Test
    void buildIndex_skipsInactiveCustomFields() {
        List<Map<String, Object>> fields = new java.util.ArrayList<>(sampleFields());
        Map<String, Object> inactiveFabric = field(
            "4063993000000072999",
            "Fabric Type",
            "cf__sb_60082852067_fabric_type",
            "string",
            true,
            false
        );
        inactiveFabric.put("is_active", false);
        fields.add(inactiveFabric);
        when(booksClient.listItemFields()).thenReturn(fields);

        Product product = new Product().fabricType("Silk");
        Map<String, Object> payload = new LinkedHashMap<>();
        mapper.enrichPushPayload(product, payload);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> customFields = (List<Map<String, Object>>) payload.get("custom_fields");
        assertThat(customFields).anySatisfy(entry -> {
            assertThat(entry.get("customfield_id")).isEqualTo("4063993000000072061");
            assertThat(entry.get("value")).isEqualTo("Silk");
        });
        assertThat(customFields).noneMatch(entry -> "4063993000000072999".equals(entry.get("customfield_id")));
    }

    @Test
    void applyPulledItem_readsAgeFabricFromZohoLabelsWhenFieldCatalogMissing() {
        when(booksClient.listItemFields()).thenReturn(List.of());
        Product product = new Product();
        mapper.applyPulledItem(
            product,
            Map.of(
                "custom_fields",
                List.of(
                    Map.of("label", "Fabric Type", "value", "Muslin"),
                    Map.of("label", "age", "value", "0"),
                    Map.of("label", "age(max)", "value", "12")
                )
            ),
            false
        );

        assertThat(product.getFabricType()).isEqualTo("Muslin");
        assertThat(product.getAgeMinMonths()).isEqualTo(0);
        assertThat(product.getAgeMaxMonths()).isEqualTo(12);
    }

    @Test
    void applyPulledItem_inactiveFieldStatus_isIgnored() {
        List<Map<String, Object>> fields = new java.util.ArrayList<>(sampleFields());
        fields.add(field("4063993000000999999", "Retired", "cf_retired", "string", true, false));
        fields.get(fields.size() - 1).put("status", "Inactive");
        when(booksClient.listItemFields()).thenReturn(fields);
        Product product = new Product();
        mapper.applyPulledItem(
            product,
            Map.of("custom_fields", List.of(Map.of("label", "Retired", "value", "ShouldNotApply"))),
            false
        );
        assertThat(product.getFabricType()).isNull();
    }

    @Test
    void applyPulledItem_labelRateOnItem_setsMrpWhenCustomFieldMissing() {
        when(booksClient.listItemFields()).thenReturn(sampleFields());
        Product product = new Product();
        mapper.applyPulledItem(product, Map.of("label_rate", "499.50", "custom_fields", List.of()), false);
        assertThat(product.getCompareAtPrice()).isEqualByComparingTo("499.50");
    }

    @Test
    void applyPulledItem_sizesPlainText_persistsSizesJson() {
        when(booksClient.listItemFields()).thenReturn(sampleFields());
        Product product = new Product();
        mapper.applyPulledItem(
            product,
            Map.of("custom_fields", List.of(Map.of("label", "sizes", "value", "0-3, 3-6"))),
            false
        );
        assertThat(product.getSizesJson()).contains("0-3").contains("3-6");
    }

    @Test
    void fieldIndex_cachedOnSecondApplyPulledItem() {
        when(booksClient.listItemFields()).thenReturn(sampleFields());
        Product first = new Product();
        Product second = new Product();
        mapper.applyPulledItem(
            first,
            Map.of("custom_fields", List.of(Map.of("label", "Fabric Type", "value", "Cotton"))),
            false
        );
        mapper.applyPulledItem(
            second,
            Map.of("custom_fields", List.of(Map.of("label", "Fabric Type", "value", "Linen"))),
            false
        );
        verify(booksClient, org.mockito.Mockito.times(1)).listItemFields();
        assertThat(first.getFabricType()).isEqualTo("Cotton");
        assertThat(second.getFabricType()).isEqualTo("Linen");
    }

    @Test
    void buildFieldIndex_whenAgeMinAndMaxShareId_dropsAgeMaxField() {
        List<Map<String, Object>> fields = List.of(
            field("age-shared", "age", "cf_age", "number", true, true),
            field("4063993000000072065", "age(max)", "cf_age_max", "number", true, false)
        );
        fields.get(0).put("customfield_id", "age-shared");
        fields.get(1).put("customfield_id", "age-shared");
        ZohoProductFieldMapper.FieldIndex index = mapper.buildIndex(fields);
        assertThat(index.ageMin()).isPresent();
        assertThat(index.ageMax()).isEmpty();
    }

    private static List<Map<String, Object>> sampleFields() {
        return List.of(
            field("4063993000000072027", "MRP", "mrp", "amount", false, false),
            field("4063993000000072061", "Fabric Type", "cf__sb_60082852067_fabric_type", "string", true, false),
            field("4063993000000072065", "age", "cf__sb_60082852067_age", "number", true, true),
            field("4063993000000072092", "age(max)", "cf__sb_60082852067_age_max", "number", true, false),
            field("4063993000000072088", "sizes", "cf__sb_60082852067_sizes", "string", true, false),
            field("4063993000000072096", "category", "cf__sb_60082852067_category", "string", true, false)
        );
    }

    private static Map<String, Object> field(
        String fieldId,
        String label,
        String apiName,
        String dataType,
        boolean custom,
        boolean mandatory
    ) {
        Map<String, Object> field = new LinkedHashMap<>();
        field.put("field_id", fieldId);
        field.put("label", label);
        field.put("field_name_formatted", label);
        field.put("api_name", apiName);
        field.put("data_type", dataType);
        field.put("is_custom_field", custom);
        field.put("is_mandatory", mandatory);
        field.put("is_active", true);
        field.put("status", "Active");
        return field;
    }
}
