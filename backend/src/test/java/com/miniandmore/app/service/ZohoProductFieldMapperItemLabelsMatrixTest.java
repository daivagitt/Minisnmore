package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.client.ZohoBooksClient;
import com.miniandmore.app.config.ApplicationProperties;
import com.miniandmore.app.domain.Product;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ZohoProductFieldMapperItemLabelsMatrixTest {

    @Mock
    private ZohoBooksClient booksClient;

    @Mock
    private LedgerlyCategoryResolver categoryResolver;

    private ZohoProductFieldMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ZohoProductFieldMapper(
            booksClient,
            new ApplicationProperties(),
            new ProductDescriptionCodec(new ObjectMapper()),
            categoryResolver
        );
    }

    @Test
    void applyMissingCatalogFromItemLabels_ignoresNonListAndNonMapEntries() {
        Product product = new Product();
        ReflectionTestUtils.invokeMethod(mapper, "applyMissingCatalogFromItemLabels", product, Map.of());
        ReflectionTestUtils.invokeMethod(mapper, "applyMissingCatalogFromItemLabels", product, Map.of("custom_fields", "nope"));
        ReflectionTestUtils.invokeMethod(
            mapper,
            "applyMissingCatalogFromItemLabels",
            product,
            Map.of("custom_fields", List.of("skip", Map.of("value", "   ")))
        );
        assertThat(product.getFabricType()).isNull();
    }

    @Test
    void applyMissingCatalogFromItemLabels_readsAgeFabricAndSizesFromAlternateKeys() {
        Product product = new Product();
        Map<String, Object> item = Map.of(
            "custom_fields",
            List.of(
                Map.of("label", "Age Max", "value", "18-24"),
                Map.of("field_name_formatted", "Age", "value", "3"),
                Map.of("field_name", "Fabric Type", "value", "Cotton"),
                Map.of("api_name", "sizes", "value", "S, M, L"),
                Map.of("label", "  ", "value", "ignored"),
                Map.of("label", "unknown", "value", "x")
            )
        );

        ReflectionTestUtils.invokeMethod(mapper, "applyMissingCatalogFromItemLabels", product, item);

        assertThat(product.getAgeMaxMonths()).isEqualTo(18);
        assertThat(product.getAgeMinMonths()).isEqualTo(3);
        assertThat(product.getFabricType()).isEqualTo("Cotton");
        assertThat(product.getSizesJson()).contains("S");
    }

    @Test
    void applyMissingCatalogFromItemLabels_doesNotOverwriteExistingCatalog() {
        Product product = new Product();
        product.setFabricType("Linen");
        product.setAgeMinMonths(1);
        product.setAgeMaxMonths(12);
        product.setSizesJson("[\"XL\"]");
        Map<String, Object> item = Map.of(
            "custom_fields",
            List.of(
                Map.of("label", "fabric", "value", "Silk"),
                Map.of("label", "age min", "value", "9"),
                Map.of("label", "agemax", "value", "99"),
                Map.of("label", "size", "value", "XXS")
            )
        );

        ReflectionTestUtils.invokeMethod(mapper, "applyMissingCatalogFromItemLabels", product, item);

        assertThat(product.getFabricType()).isEqualTo("Linen");
        assertThat(product.getAgeMinMonths()).isEqualTo(1);
        assertThat(product.getAgeMaxMonths()).isEqualTo(12);
        assertThat(product.getSizesJson()).isEqualTo("[\"XL\"]");
    }

    @Test
    void applyMissingCatalogFromItemLabels_skipsUnparseableAgeAndEmptyParsedSizes() {
        Product product = new Product();
        Map<String, Object> item = Map.of(
            "custom_fields",
            List.of(
                Map.of("label", "age", "value", "not-a-number"),
                Map.of("label", "sizes", "value", "[]")
            )
        );

        ReflectionTestUtils.invokeMethod(mapper, "applyMissingCatalogFromItemLabels", product, item);

        assertThat(product.getAgeMinMonths()).isNull();
        assertThat(product.getSizesJson()).isNull();
    }

    @Test
    void parseInteger_blankDashAndNumber() {
        assertThat((Object) ReflectionTestUtils.invokeMethod(mapper, "parseInteger", "  ")).isEqualTo(java.util.Optional.empty());
        assertThat((Object) ReflectionTestUtils.invokeMethod(mapper, "parseInteger", "12-18")).isEqualTo(java.util.Optional.of(12));
        assertThat((Object) ReflectionTestUtils.invokeMethod(mapper, "parseInteger", "abc")).isEqualTo(java.util.Optional.empty());
    }
}
