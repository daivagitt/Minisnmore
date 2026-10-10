package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.client.ZohoBooksClient;
import com.miniandmore.app.config.ApplicationProperties;
import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.CustomerProfile;
import com.miniandmore.app.domain.OrderItem;
import com.miniandmore.app.domain.Product;
import com.miniandmore.app.domain.enumeration.OrderStatus;
import com.miniandmore.app.domain.enumeration.PaymentStatus;
import com.miniandmore.app.domain.enumeration.ZohoSyncStatus;
import com.miniandmore.app.repository.CustomerOrderRepository;
import com.miniandmore.app.repository.CustomerProfileRepository;
import com.miniandmore.app.repository.OrderItemRepository;
import com.miniandmore.app.repository.ProductRepository;
import com.miniandmore.app.repository.ZohoSettingRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientResponseException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ZohoSyncServiceResidualBranchMatrix20261008Test {

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
        zoho.setSyncProductStock(true);
        lenient().when(applicationProperties.getZoho()).thenReturn(zoho);
        lenient().when(productFieldMapper.applyPulledItem(any(), any(), anyBoolean())).thenReturn(Optional.empty());
        lenient().when(productRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(customerOrderRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void descriptionMetadataAlternativesExerciseEachShortCircuitPosition() {
        Product fabric = new Product().description("old");
        Product sizes = new Product().description("old");
        Product colors = new Product().description("old");
        Product story = new Product().description("old");
        when(descriptionCodec.decode("fabric")).thenReturn(decoded(null, "Cotton", List.of(), List.of(), null, null));
        when(descriptionCodec.decode("sizes")).thenReturn(decoded(null, null, List.of("M"), List.of(), null, null));
        when(descriptionCodec.decode("colors")).thenReturn(decoded(null, null, List.of(), List.of("Blue"), null, null));
        when(descriptionCodec.decode("story")).thenReturn(decoded(null, null, List.of(), List.of(), "Story", null));

        ReflectionTestUtils.invokeMethod(service, "applyPulledDescription", fabric, "fabric");
        ReflectionTestUtils.invokeMethod(service, "applyPulledDescription", sizes, "sizes");
        ReflectionTestUtils.invokeMethod(service, "applyPulledDescription", colors, "colors");
        ReflectionTestUtils.invokeMethod(service, "applyPulledDescription", story, "story");

        assertThat(List.of(fabric, sizes, colors, story)).allMatch(product -> product.getDescription() == null);
    }

    @Test
    void payloadBuildersExerciseMissingOptionalStockAndMrpInputs() {
        Product product = new Product().id(8L).name("Plain").sku("PLAIN").price(null).stock(null).units(" ");
        @SuppressWarnings("unchecked")
        Map<String, Object> simple = ReflectionTestUtils.invokeMethod(
            service,
            "buildItemPayload",
            product,
            true,
            Optional.of("inventory-account"),
            " "
        );
        ProductVariantSupport.VariantRow row = new ProductVariantSupport.VariantRow(
            "row-1",
            "PLAIN-S",
            " ",
            null,
            BigDecimal.ZERO,
            null,
            null,
            -2,
            true,
            null,
            null,
            null,
            List.of()
        );
        @SuppressWarnings("unchecked")
        Map<String, Object> variant = ReflectionTestUtils.invokeMethod(
            service,
            "buildVariantItemPayload",
            product,
            row,
            true,
            Optional.empty(),
            "loc-unused"
        );

        assertThat(simple).doesNotContainKey("locations").containsEntry("rate", BigDecimal.ZERO);
        assertThat(variant).doesNotContainKeys("locations", "label_rate", "description");
        assertThat(variant).containsEntry("item_type", "sales").containsEntry("unit", "pcs");
    }

    @Test
    void labelRateGuardExercisesNullZeroAndNonLowerValues() {
        Map<String, Object> missingRate = new LinkedHashMap<>();
        missingRate.put("label_rate", "10");
        Map<String, Object> missingLabel = new LinkedHashMap<>();
        missingLabel.put("rate", "10");
        Map<String, Object> zeroRate = new LinkedHashMap<>();
        zeroRate.put("rate", "0");
        zeroRate.put("label_rate", "10");
        Map<String, Object> zeroLabel = new LinkedHashMap<>();
        zeroLabel.put("rate", "10");
        zeroLabel.put("label_rate", "0");
        Map<String, Object> equal = new LinkedHashMap<>();
        equal.put("rate", "10");
        equal.put("label_rate", "10");

        for (Map<String, Object> payload : List.of(missingRate, missingLabel, zeroRate, zeroLabel, equal)) {
            ReflectionTestUtils.invokeMethod(service, "ensureLabelRateAtLeastRate", payload);
        }

        assertThat(equal).containsEntry("label_rate", "10");
    }

    @Test
    void jsonParsersHandleObjectMapperReturningNullRoots() throws Exception {
        ObjectMapper mapper = org.mockito.Mockito.spy(objectMapper);
        org.mockito.Mockito.doReturn(null).when(mapper).readTree("null-root");
        ReflectionTestUtils.setField(service, "objectMapper", mapper);

        @SuppressWarnings("unchecked")
        List<String> array = ReflectionTestUtils.invokeMethod(service, "parseJsonStringArray", "null-root");
        @SuppressWarnings("unchecked")
        Map<String, List<String>> map = ReflectionTestUtils.invokeMethod(service, "parseJsonStringListMap", "null-root");

        assertThat(array).isEmpty();
        assertThat(map).isEmpty();
    }

    @Test
    void textFromJsonCoversNullMissingNullFieldBlankAndTrimmedText() throws Exception {
        JsonNode missing = objectMapper.readTree("{}").path("absent");
        JsonNode object = objectMapper.readTree("{\"nullField\":null,\"blank\":\"  \",\"name\":\"  Jane  \"}");

        String fromNull = ReflectionTestUtils.invokeMethod(ZohoSyncService.class, "textFromJson", null, "name");
        String fromMissingNode = ReflectionTestUtils.invokeMethod(ZohoSyncService.class, "textFromJson", missing, "name");
        String fromNullField = ReflectionTestUtils.invokeMethod(ZohoSyncService.class, "textFromJson", object, "nullField");
        String fromBlank = ReflectionTestUtils.invokeMethod(ZohoSyncService.class, "textFromJson", object, "blank");
        String fromText = ReflectionTestUtils.invokeMethod(ZohoSyncService.class, "textFromJson", object, "name");

        assertThat(fromNull).isNull();
        assertThat(fromMissingNode).isNull();
        assertThat(fromNullField).isNull();
        assertThat(fromBlank).isNull();
        assertThat(fromText).isEqualTo("Jane");
    }

    @Test
    void buildAndApplyOrderAddressCoversEveryOptionalField() throws Exception {
        JsonNode node = objectMapper.readTree(
            "{\"full_name\":\"Jane\",\"line1\":\"One\",\"line2\":\"Two\",\"city\":\"Pune\",\"state\":\"MH\",\"pincode\":\"411001\",\"phone\":\"999\"}"
        );
        @SuppressWarnings("unchecked")
        Map<String, Object> address = ReflectionTestUtils.invokeMethod(service, "buildZohoAddress", node);
        CustomerOrder order = new CustomerOrder().shippingAddressJson(node.toString());

        ReflectionTestUtils.invokeMethod(service, "applySalesOrderAddresses", "so-address", order);

        assertThat(address).containsKeys("attention", "address", "street2", "city", "state", "zip", "phone");
        verify(booksClient).updateSalesOrderBillingAddress(eq("so-address"), any());
        verify(booksClient).updateSalesOrderShippingAddress(eq("so-address"), any());
    }

    @Test
    void resolveContactNameUsesProfileThenEmailThenLoginFallbacks() {
        CustomerProfile profile = new CustomerProfile().fullName("Profile Name");
        String fromProfile = ReflectionTestUtils.invokeMethod(
            service,
            "resolveZohoContactName",
            objectMapper.createObjectNode(),
            profile,
            "mail@example.com",
            "login",
            null
        );
        String fromEmail = ReflectionTestUtils.invokeMethod(
            service,
            "resolveZohoContactName",
            objectMapper.createObjectNode(),
            null,
            "mail@example.com",
            null,
            null
        );
        String fromLogin = ReflectionTestUtils.invokeMethod(
            service,
            "resolveZohoContactName",
            objectMapper.createObjectNode(),
            null,
            null,
            "login",
            null
        );

        assertThat(fromProfile).isEqualTo("Profile Name");
        assertThat(fromEmail).isEqualTo("mail@example.com");
        assertThat(fromLogin).isEqualTo("login");
    }

    @Test
    void unexpectedContactNameRestErrorIsRethrownFromCatchBranch() {
        RestClientResponseException unavailable = zohoError(503, "{\"message\":\"unavailable\"}");
        when(booksClient.findContactByName("Jane")).thenThrow(unavailable);

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service, "findContactByNameIfAllowed", "Jane"))
            .isSameAs(unavailable);
    }

    @Test
    void variantLookupFailureFallsThroughToPushAndCachesCreatedItem() {
        Product parent = new Product()
            .id(77L)
            .name("Matrix")
            .sku("MATRIX")
            .itemStructure(ProductVariantSupport.STRUCTURE_VARIANTS)
            .variantsJson(
                "[{\"sku\":\"MATRIX-S\",\"size\":\"S\",\"color\":\"Red\",\"rate\":10,\"stock\":1,\"active\":true}]"
            );
        OrderItem line = new OrderItem().productId("77").variantSku("MATRIX-S");
        when(productRepository.findById(77L)).thenReturn(Optional.of(parent));
        when(booksClient.findItemBySku("MATRIX-S")).thenThrow(new RuntimeException("lookup down")).thenReturn(null);
        when(booksClient.createItem(any())).thenReturn(Map.of("item", Map.of("item_id", "created-after-lookup-error")));

        Optional<String> result = ReflectionTestUtils.invokeMethod(service, "resolveZohoItemIdForOrderLine", line);

        assertThat(result).contains("created-after-lookup-error");
        assertThat(parent.getVariantsJson()).contains("created-after-lookup-error");
    }

    @Test
    void retryFailedOrdersCatchesExceptionFromCodPage() {
        CustomerOrder cod = new CustomerOrder().id(91L).orderNumber("COD-91").paymentMethod("cod");
        when(customerOrderRepository.findPaidOrdersForZohoRetry(eq(PaymentStatus.PAID), any(), eq(PageRequest.of(0, 200))))
            .thenReturn(Page.empty());
        when(customerOrderRepository.findConfirmedCodOrdersForZohoRetry(any(), eq(PageRequest.of(0, 200))))
            .thenReturn(new PageImpl<>(List.of(cod)));
        when(customerOrderRepository.findById(91L)).thenThrow(new RuntimeException("cod retry failed"));

        Map<String, Object> result = service.retryFailedOrders();

        assertThat(result.get("failed")).isEqualTo(1);
    }

    @Test
    void recordRetryOutcomeHandlesNullIdAndBlankFailureReason() {
        CustomerOrder order = new CustomerOrder()
            .orderNumber("NO-ID")
            .zohoSyncStatus(ZohoSyncStatus.FAILED)
            .zohoSyncError(" ");
        ZohoSyncSummary.Builder builder = ZohoSyncSummary.builder();

        ReflectionTestUtils.invokeMethod(service, "recordOrderRetryOutcome", builder, order);

        assertThat(builder.build().failureCount()).isEqualTo(1);
        verify(customerOrderRepository, never()).findById(any());
    }

    @Test
    void trackingPackageNonAuthFailureStillSavesOrderWithoutLifecycleChange() {
        CustomerOrder order = new CustomerOrder()
            .id(105L)
            .status(OrderStatus.CONFIRMED)
            .zohoSalesOrderId("so-105");
        when(customerOrderRepository.findById(105L)).thenReturn(Optional.of(order));
        when(booksClient.getSalesOrder("so-105"))
            .thenReturn(Map.of("salesorder", Map.of("status", "confirmed", "salesorder_number", "SO-105")));
        when(booksClient.listPackagesForSalesOrder("so-105", "SO-105")).thenThrow(new RuntimeException("packages unavailable"));

        service.pullOrderTracking(105L);

        verify(customerOrderRepository).save(order);
        verify(orderEventService, never()).syncLifecycleEvents(any());
    }

    @Test
    void invoicePullWithoutStatusChangeSkipsLifecycleEvent() {
        CustomerOrder order = new CustomerOrder()
            .id(106L)
            .status(OrderStatus.CONFIRMED)
            .zohoInvoiceId("inv-106");
        when(booksClient.getInvoice("inv-106"))
            .thenReturn(Map.of("invoice", Map.of("invoice_id", "inv-106", "status", "sent")));

        ReflectionTestUtils.invokeMethod(service, "pullInvoiceStatusOnly", order);

        verify(customerOrderRepository).save(order);
        verify(orderEventService, never()).syncLifecycleEvents(any());
    }

    @Test
    void saveRetryRejectsUnsupportedFieldsWhenInventoryErrorsAlsoApply() {
        Map<String, Object> inventoryDisabledPayload = new LinkedHashMap<>();
        inventoryDisabledPayload.put("sku", "INV-DISABLED");
        inventoryDisabledPayload.put("group_id", "group");
        RestClientResponseException inventoryDisabled = zohoError(
            400,
            "{\"message\":\"invalid group_id because inventory tracking 2024 is disabled\"}"
        );
        when(booksClient.updateItem("item-disabled", inventoryDisabledPayload)).thenThrow(inventoryDisabled);

        assertThatThrownBy(() ->
            ReflectionTestUtils.invokeMethod(service, "saveZohoItem", false, "item-disabled", inventoryDisabledPayload)
        ).isSameAs(inventoryDisabled);
    }

    @Test
    void saveRetryGuardBranchesRethrowWithoutEligibleFallback() {
        Map<String, Object> noCustomFields = new LinkedHashMap<>(Map.of("sku", "NO-CUSTOM"));
        RestClientResponseException customError = zohoError(400, "{\"code\":120100,\"message\":\"custom field missing\"}");
        when(booksClient.updateItem("no-custom", noCustomFields)).thenThrow(customError);

        Map<String, Object> noVariantFields = new LinkedHashMap<>(Map.of("sku", "NO-VARIANT"));
        RestClientResponseException variantError = zohoError(400, "{\"message\":\"unsupported group_id\"}");
        when(booksClient.updateItem("no-variant", noVariantFields)).thenThrow(variantError);

        Map<String, Object> invalidAccountPayload = new LinkedHashMap<>(
            Map.of("sku", "INVALID-ACCOUNT", "group_id", "group")
        );
        RestClientResponseException invalidAccount = zohoError(
            400,
            "{\"message\":\"invalid group_id; inventory tracking account does not exist\"}"
        );
        when(booksClient.updateItem("invalid-account", invalidAccountPayload)).thenThrow(invalidAccount);

        assertThatThrownBy(() ->
            ReflectionTestUtils.invokeMethod(service, "saveZohoItem", false, "no-custom", noCustomFields)
        ).isSameAs(customError);
        assertThatThrownBy(() ->
            ReflectionTestUtils.invokeMethod(service, "saveZohoItem", false, "no-variant", noVariantFields)
        ).isSameAs(variantError);
        assertThatThrownBy(() ->
            ReflectionTestUtils.invokeMethod(service, "saveZohoItem", false, "invalid-account", invalidAccountPayload)
        ).isSameAs(invalidAccount);
    }

    @Test
    void pullInventoryHandlesSkuLessVariantAndSkuLessRowFailure() {
        Product parent = new Product()
            .id(201L)
            .sku("PARENT-201")
            .itemStructure(ProductVariantSupport.STRUCTURE_VARIANTS)
            .variantsJson(
                "[{\"sku\":\"ROW-201\",\"size\":\"S\",\"color\":\"Red\",\"rate\":10,\"stock\":1,\"active\":true,\"zohoItemId\":\"zoho-row-201\"}]"
            );
        Map<String, Object> skuLessVariant = new LinkedHashMap<>();
        skuLessVariant.put("item_id", "zoho-row-201");
        skuLessVariant.put("stock_on_hand", 7);
        Map<String, Object> skuLessFailure = new LinkedHashMap<>();
        skuLessFailure.put("item_id", "zoho-failure");
        skuLessFailure.put("stock_on_hand", 3);
        when(productRepository.findAll()).thenReturn(List.of(parent));
        when(productRepository.findOneByZohoItemId("zoho-row-201")).thenReturn(Optional.of(parent));
        when(productRepository.findOneByZohoItemId("zoho-failure")).thenThrow(new RuntimeException("repository failure"));
        when(booksClient.listItems(1))
            .thenReturn(new ZohoBooksClient.ItemPage(List.of(skuLessVariant, skuLessFailure), false));

        Map<String, Object> result = service.pullInventory();

        assertThat(result.get("success")).isEqualTo(1);
        assertThat(result.get("failed")).isEqualTo(1);
        assertThat(parent.getVariantsJson()).contains("\"stock\":7");
    }

    @Test
    void orderLineWithoutLocalProductReturnsExistingZohoItem() {
        OrderItem orphan = new OrderItem().variantSku("ORPHAN-EXISTS");
        when(booksClient.findItemBySku("ORPHAN-EXISTS")).thenReturn(Map.of("item_id", "zoho-orphan"));

        Optional<String> result = ReflectionTestUtils.invokeMethod(service, "resolveZohoItemIdForOrderLine", orphan);

        assertThat(result).contains("zoho-orphan");
    }

    private static ProductDescriptionCodec.Decoded decoded(
        String category,
        String fabric,
        List<String> sizes,
        List<String> colors,
        String story,
        String body
    ) {
        return new ProductDescriptionCodec.Decoded(category, fabric, sizes, colors, null, null, story, null, null, body);
    }

    private static RestClientResponseException zohoError(int status, String body) {
        return new RestClientResponseException(
            "Zoho error",
            status,
            HttpStatus.valueOf(status).getReasonPhrase(),
            null,
            body.getBytes(),
            null
        );
    }
}
