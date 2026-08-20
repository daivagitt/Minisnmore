package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
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
        when(booksClient.listItemFields()).thenReturn(sampleFields());
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
        assertThat(product.getSizesJson()).contains("S").contains("M");
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
