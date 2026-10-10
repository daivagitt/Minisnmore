package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.client.ZohoBooksClient;
import com.miniandmore.app.config.ApplicationProperties;
import com.miniandmore.app.repository.CustomerOrderRepository;
import com.miniandmore.app.repository.CustomerProfileRepository;
import com.miniandmore.app.repository.OrderItemRepository;
import com.miniandmore.app.repository.ProductRepository;
import com.miniandmore.app.repository.ZohoSettingRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientResponseException;

/** Branch coverage for {@link ZohoSyncService} save/retry paths on Zoho item push. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ZohoSyncServiceSaveZohoItemBranchTest {

    @Mock
    private ZohoBooksClient booksClient;

    @Mock
    private ApplicationProperties applicationProperties;

    @Mock
    private ZohoItemImageSync itemImageSync;

    @Mock
    private ZohoProductFieldMapper productFieldMapper;

    @Mock
    private ProductDescriptionCodec descriptionCodec;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ZohoSettingRepository zohoSettingRepository;

    @Mock
    private CustomerOrderRepository customerOrderRepository;

    @Mock
    private CustomerProfileRepository customerProfileRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private S3StorageService s3StorageService;

    @Mock
    private OrderEventService orderEventService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private ZohoSyncService service;

    @BeforeEach
    void setUp() {
        ProductVariantSupport variantSupport = StorePricingTestSupport.variantSupport(objectMapper);
        ZohoBooksAccountResolver accountResolver = new ZohoBooksAccountResolver(booksClient, applicationProperties);
        ZohoCatalogStockAdjuster stockAdjuster = new ZohoCatalogStockAdjuster(booksClient, accountResolver);
        ZohoStockReconcileService stockReconcileService =
            new ZohoStockReconcileService(
                productRepository,
                orderItemRepository,
                variantSupport,
                accountResolver,
                stockAdjuster
            );
        service =
            new ZohoSyncService(
                booksClient,
                objectMapper,
                applicationProperties,
                itemImageSync,
                productFieldMapper,
                descriptionCodec,
                variantSupport,
                productRepository,
                zohoSettingRepository,
                customerOrderRepository,
                customerProfileRepository,
                orderItemRepository,
                s3StorageService,
                orderEventService,
                accountResolver,
                stockAdjuster,
                stockReconcileService
            );
        ApplicationProperties.Zoho zoho = new ApplicationProperties.Zoho();
        when(applicationProperties.getZoho()).thenReturn(zoho);
    }

    @Test
    void saveZohoItem_retriesWithoutCustomFieldsWhenZohoRejectsThem() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sku", "SKU-CF");
        payload.put("custom_fields", List.of(Map.of("api_name", "x")));
        RestClientResponseException invalidFields = zoho400("{\"code\":120100,\"message\":\"custom field missing\"}");
        when(booksClient.updateItem(eq("item-1"), any()))
            .thenThrow(invalidFields)
            .thenReturn(Map.of("item", Map.of("item_id", "item-1")));

        @SuppressWarnings("unchecked")
        Map<String, Object> result = ReflectionTestUtils.invokeMethod(service, "saveZohoItem", false, "item-1", payload);

        assertThat(result).containsKey("item");
        verify(booksClient).updateItem(eq("item-1"), argThat(m -> m.containsKey("custom_fields")));
        verify(booksClient).updateItem(eq("item-1"), argThat(m -> !m.containsKey("custom_fields")));
    }

    @Test
    void saveZohoItem_recreatesWhenUpdateTargetMissingInZoho() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sku", "SKU-RECREATE");
        RestClientResponseException notFound = zoho400("{\"code\":2006,\"message\":\"Item does not exist\"}");
        when(booksClient.updateItem(eq("gone"), any())).thenThrow(notFound);
        when(booksClient.createItem(any())).thenReturn(Map.of("item", Map.of("item_id", "new-item")));

        @SuppressWarnings("unchecked")
        Map<String, Object> result = ReflectionTestUtils.invokeMethod(service, "saveZohoItem", false, "gone", payload);

        assertThat(result.get("item")).isNotNull();
    }

    @Test
    void saveZohoItem_stripsVariantFieldsWhenZohoRejectsGroupAttributes() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sku", "SKU-VAR");
        payload.put("group_id", "grp-1");
        payload.put("attribute_name1", "Size");
        RestClientResponseException unsupported = zoho400("{\"message\":\"invalid group_id for item\"}");
        when(booksClient.updateItem(eq("item-v"), any()))
            .thenThrow(unsupported)
            .thenReturn(Map.of("item", Map.of("item_id", "item-v")));

        @SuppressWarnings("unchecked")
        Map<String, Object> result = ReflectionTestUtils.invokeMethod(service, "saveZohoItem", false, "item-v", payload);

        assertThat(result).containsKey("item");
        verify(booksClient, times(2)).updateItem(eq("item-v"), any());
        verify(booksClient).updateItem(eq("item-v"), argThat(m -> !m.containsKey("group_id")));
    }

    @Test
    void saveZohoItem_retriesWithExistingGroupAttributesWhenDestinationGroupMissing() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sku", "GRP-SKU");
        payload.put("name", "Grouped");
        RestClientResponseException missingAttrs = zoho400("{\"code\":700019,\"message\":\"destination group missing attributes\"}");
        when(booksClient.getItem("item-grp"))
            .thenReturn(
                Map.of(
                    "item",
                    Map.of(
                        "item_id",
                        "item-grp",
                        "group_id",
                        "g-1",
                        "attribute_name1",
                        "Size",
                        "attribute_option_name1",
                        "L"
                    )
                )
            );
        when(booksClient.updateItem(eq("item-grp"), any()))
            .thenThrow(missingAttrs)
            .thenReturn(Map.of("item", Map.of("item_id", "item-grp")));

        @SuppressWarnings("unchecked")
        Map<String, Object> result = ReflectionTestUtils.invokeMethod(service, "saveZohoItem", false, "item-grp", payload);

        assertThat(result).containsKey("item");
        verify(booksClient, org.mockito.Mockito.times(2)).updateItem(eq("item-grp"), any());
    }

    @Test
    void saveZohoItem_openingStockUsesCreatePath() {
        Map<String, Object> payload = Map.of("sku", "NEW-SKU", "name", "New");
        when(booksClient.createItem(payload)).thenReturn(Map.of("item", Map.of("item_id", "created")));

        @SuppressWarnings("unchecked")
        Map<String, Object> result = ReflectionTestUtils.invokeMethod(service, "saveZohoItem", true, null, payload);

        assertThat(result.get("item")).isNotNull();
    }

    private static RestClientResponseException zoho400(String body) {
        return new RestClientResponseException("Bad Request", HttpStatus.BAD_REQUEST.value(), "Bad Request", null, body.getBytes(), null);
    }
}
