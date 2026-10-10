package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.client.ZohoBooksClient;
import com.miniandmore.app.config.ApplicationProperties;
import com.miniandmore.app.domain.OrderItem;
import com.miniandmore.app.domain.Product;
import com.miniandmore.app.repository.CustomerOrderRepository;
import com.miniandmore.app.repository.CustomerProfileRepository;
import com.miniandmore.app.repository.OrderItemRepository;
import com.miniandmore.app.repository.ProductRepository;
import com.miniandmore.app.repository.ZohoSettingRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientResponseException;

/** Targets JaCoCo-missed branches in {@link ZohoSyncService} helpers and guards. */
@ExtendWith(MockitoExtension.class)
class ZohoSyncServiceBranchCoverageTest {

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
    private ProductVariantSupport variantSupport;
    private ZohoBooksAccountResolver accountResolver;
    private ZohoCatalogStockAdjuster stockAdjuster;
    private ZohoStockReconcileService stockReconcileService;
    private ZohoSyncService service;

    @BeforeEach
    void setUp() {
        variantSupport = StorePricingTestSupport.variantSupport(objectMapper);
        accountResolver = new ZohoBooksAccountResolver(booksClient, applicationProperties);
        stockAdjuster = new ZohoCatalogStockAdjuster(booksClient, accountResolver);
        stockReconcileService =
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
    }

    @Test
    void constructor_usesInjectedCreditNoteServicesWhenProvided() {
        ZohoCreditNoteService creditNoteService = mock(ZohoCreditNoteService.class);
        ZohoOrderCancellationSync cancellationSync = mock(ZohoOrderCancellationSync.class);
        ZohoSyncService wired =
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
                stockReconcileService,
                creditNoteService,
                cancellationSync
            );
        assertThat(ReflectionTestUtils.getField(wired, "creditNoteService")).isSameAs(creditNoteService);
        assertThat(ReflectionTestUtils.getField(wired, "orderCancellationSync")).isSameAs(cancellationSync);
    }

    @ParameterizedTest
    @ValueSource(ints = { 401, 403, 408, 429, 500, 503 })
    void isOperationWideHttpFailure_trueForAuthTimeoutRateLimitAndServerErrors(int status) {
        RestClientResponseException ex = new RestClientResponseException("err", status, "x", null, "{}".getBytes(), null);
        Boolean wide = ReflectionTestUtils.invokeMethod(ZohoSyncService.class, "isOperationWideHttpFailure", ex);
        assertThat(wide).isTrue();
    }

    @Test
    void isOperationWideHttpFailure_falseForTypicalClientErrors() {
        RestClientResponseException ex = new RestClientResponseException("err", 404, "x", null, "{}".getBytes(), null);
        Boolean wide = ReflectionTestUtils.invokeMethod(ZohoSyncService.class, "isOperationWideHttpFailure", ex);
        assertThat(wide).isFalse();
    }

    @Test
    void isUnavailableSalesOrderItem_matchesSalesOrderItemNotFound() {
        RestClientResponseException ex = new RestClientResponseException(
            "so",
            400,
            "Bad Request",
            null,
            "{\"code\":2006,\"message\":\"salesorder not found\"}".getBytes(),
            null
        );
        Boolean unavailable = ReflectionTestUtils.invokeMethod(ZohoSyncService.class, "isUnavailableSalesOrderItem", ex);
        assertThat(unavailable).isTrue();
    }

    @Test
    void isUnavailableSalesOrderItem_falseWhenBodyDoesNotMentionSalesOrder() {
        RestClientResponseException ex = new RestClientResponseException(
            "other",
            400,
            "Bad Request",
            null,
            "{\"code\":2006}".getBytes(),
            null
        );
        Boolean unavailable = ReflectionTestUtils.invokeMethod(ZohoSyncService.class, "isUnavailableSalesOrderItem", ex);
        assertThat(unavailable).isFalse();
    }

    @Test
    void shouldProcessListRow_sinceNullAlwaysProcesses() {
        Boolean process = ReflectionTestUtils.invokeMethod(
            ZohoSyncService.class,
            "shouldProcessListRow",
            Map.of("status", "active"),
            null,
            "SKU-1"
        );
        assertThat(process).isTrue();
    }

    @Test
    void shouldProcessListRow_inactiveSkuStillProcessedWhenUnchanged() {
        Instant since = Instant.parse("2026-06-01T12:00:00Z");
        Map<String, Object> item = Map.of(
            "status",
            "inactive",
            "last_modified_time",
            "2026-01-01T00:00:00+0000"
        );
        Boolean process = ReflectionTestUtils.invokeMethod(ZohoSyncService.class, "shouldProcessListRow", item, since, "SKU-INACTIVE");
        assertThat(process).isTrue();
    }

    @Test
    void shouldProcessListRow_unchangedActiveRowSkipped() {
        Instant since = Instant.parse("2026-06-01T12:00:00Z");
        Map<String, Object> item = Map.of(
            "status",
            "active",
            "last_modified_time",
            "2026-01-01T00:00:00+0000"
        );
        Boolean process = ReflectionTestUtils.invokeMethod(ZohoSyncService.class, "shouldProcessListRow", item, since, "SKU-OLD");
        assertThat(process).isFalse();
    }

    @Test
    void zohoItemPageIdentity_sortsAndHandlesNullRow() {
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(null);
        items.add(Map.of("item_id", "2", "sku", "B"));
        items.add(Map.of("item_id", "1", "sku", "A"));
        String identity = ReflectionTestUtils.invokeMethod(service, "zohoItemPageIdentity", items);
        assertThat(identity).contains("<null>").contains("1").contains("2").contains("A").contains("B");
    }

    @Test
    void parseJsonStringListMap_skipsBlankUrlsInArrays() {
        @SuppressWarnings("unchecked")
        Map<String, List<String>> parsed = ReflectionTestUtils.invokeMethod(
            service,
            "parseJsonStringListMap",
            "{\"Blue\":[\"  \",\"https://cdn.example.com/ok.jpg\",null]}"
        );
        assertThat(parsed).containsOnlyKeys("Blue");
        assertThat(parsed.get("Blue")).containsExactly("https://cdn.example.com/ok.jpg");
    }

    @Test
    void parseJsonStringListMap_skipsColorKeysWithOnlyBlankUrls() {
        @SuppressWarnings("unchecked")
        Map<String, List<String>> parsed = ReflectionTestUtils.invokeMethod(
            service,
            "parseJsonStringListMap",
            "{\"Empty\":[\"  \",\"\"]}"
        );
        assertThat(parsed).isEmpty();
    }

    @Test
    void mergeColorsJsonPreferringRich_keepsTextualDuplicateWhenPreferRich() {
        String parent = "[{\"name\":\"Navy\",\"imageUrls\":[\"https://cdn.example.com/old.jpg\"]}]";
        String duplicate = "[\"Navy\",\"Red\"]";
        String merged = ReflectionTestUtils.invokeMethod(service, "mergeColorsJsonPreferringRich", parent, duplicate);
        assertThat(merged).contains("Navy").contains("Red");
    }

    @Test
    void isDestinationGroupMissingAttributes_falseForNon400Status() {
        RestClientResponseException ex = new RestClientResponseException(
            "group",
            500,
            "Error",
            null,
            "{\"message\":\"destination group missing attributes\"}".getBytes(),
            null
        );
        Boolean missing = ReflectionTestUtils.invokeMethod(ZohoSyncService.class, "isDestinationGroupMissingAttributes", ex);
        assertThat(missing).isFalse();
    }

    @Test
    void isDestinationGroupMissingAttributes_trueForCodeOrNaturalLanguageOn400() {
        RestClientResponseException byCode = new RestClientResponseException(
            "group",
            400,
            "Bad Request",
            null,
            "{\"code\":700019}".getBytes(),
            null
        );
        RestClientResponseException byMessage = new RestClientResponseException(
            "group",
            400,
            "Bad Request",
            null,
            "{\"message\":\"Destination group is missing required attributes\"}".getBytes(),
            null
        );
        assertThat(
            (Boolean) ReflectionTestUtils.invokeMethod(ZohoSyncService.class, "isDestinationGroupMissingAttributes", byCode)
        ).isTrue();
        assertThat(
            (Boolean) ReflectionTestUtils.invokeMethod(ZohoSyncService.class, "isDestinationGroupMissingAttributes", byMessage)
        ).isTrue();
    }

    @Test
    void isInvalidCustomFields_matchesCustomFieldDoesNotExistMessage() {
        RestClientResponseException ex = new RestClientResponseException(
            "cf",
            400,
            "Bad Request",
            null,
            "{\"message\":\"Custom field foo does not exist\"}".getBytes(),
            null
        );
        Boolean invalid = ReflectionTestUtils.invokeMethod(ZohoSyncService.class, "isInvalidCustomFields", ex);
        assertThat(invalid).isTrue();
    }

    @Test
    void isUnsupportedVariantFields_matchesAttributeNameField() {
        RestClientResponseException ex = new RestClientResponseException(
            "variant",
            400,
            "Bad Request",
            null,
            "{\"message\":\"unsupported attribute_name value\"}".getBytes(),
            null
        );
        Boolean unsupported = ReflectionTestUtils.invokeMethod(ZohoSyncService.class, "isUnsupportedVariantFields", ex);
        assertThat(unsupported).isTrue();
    }

    @Test
    void isZohoAuthFailure_403AndNonRestClientPaths() {
        RestClientResponseException forbidden = new RestClientResponseException(
            "auth",
            403,
            "Forbidden",
            null,
            "{}".getBytes(),
            null
        );
        Boolean forbiddenAuth = ReflectionTestUtils.invokeMethod(ZohoSyncService.class, "isZohoAuthFailure", forbidden);
        RestClientResponseException badRequest = new RestClientResponseException(
            "other",
            400,
            "Bad Request",
            null,
            "{}".getBytes(),
            null
        );
        Boolean notAuth = ReflectionTestUtils.invokeMethod(ZohoSyncService.class, "isZohoAuthFailure", badRequest);
        assertThat(forbiddenAuth).isTrue();
        assertThat(notAuth).isFalse();
    }

    @Test
    void syncProductAsync_skipsWhenAutoSyncDisabled() {
        ApplicationProperties.Zoho zoho = new ApplicationProperties.Zoho();
        zoho.setAutoSyncEnabled(false);
        when(applicationProperties.getZoho()).thenReturn(zoho);
        service.syncProductAsync(99L);
        verify(productRepository, never()).findOneWithEagerRelationships(any());
    }

    @Test
    void resolveZohoItemId_backfillsProductZohoItemIdOnLookup() {
        Product product = new Product().id(5L).sku("LOOKUP-SKU").itemStructure("SIMPLE");
        OrderItem line = new OrderItem().productId("5").variantSku("LOOKUP-SKU");
        when(productRepository.findById(5L)).thenReturn(Optional.of(product));
        when(booksClient.findItemBySku("LOOKUP-SKU")).thenReturn(Map.of("item_id", "zoho-55"));
        when(productRepository.save(product)).thenAnswer(inv -> inv.getArgument(0));

        Optional<String> resolved = service.resolveZohoItemId(line);

        assertThat(resolved).contains("zoho-55");
        assertThat(product.getZohoItemId()).isEqualTo("zoho-55");
        verify(productRepository).save(product);
    }

    @Test
    void toInteger_coversNumberEmptyAndDecimalText() {
        assertThat((Integer) ReflectionTestUtils.invokeMethod(service, "toInteger", new Object[] { null })).isEqualTo(0);
        assertThat((Integer) ReflectionTestUtils.invokeMethod(service, "toInteger", 9L)).isEqualTo(9);
        assertThat((Integer) ReflectionTestUtils.invokeMethod(service, "toInteger", "  ")).isEqualTo(0);
        assertThat((Integer) ReflectionTestUtils.invokeMethod(service, "toInteger", "12.9")).isEqualTo(12);
    }

    @Test
    void resolveZohoItemIdForOrderLine_backfillsSimpleProductZohoItemIdFromLookup() {
        Product product = new Product().id(10L).sku("SIMPLE-SKU").itemStructure("SIMPLE");
        OrderItem item = new OrderItem().productId("10");
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        when(booksClient.findItemBySku("SIMPLE-SKU")).thenReturn(Map.of("item_id", "zoho-10"));
        when(productRepository.save(product)).thenAnswer(inv -> inv.getArgument(0));

        Optional<String> itemId = ReflectionTestUtils.invokeMethod(service, "resolveZohoItemIdForOrderLine", item);

        assertThat(itemId).contains("zoho-10");
        assertThat(product.getZohoItemId()).isEqualTo("zoho-10");
        verify(productRepository).save(product);
    }

    @Test
    void parseJsonStringListMap_skipsNonObjectRootAndNonArrayValues() {
        @SuppressWarnings("unchecked")
        Map<String, List<String>> fromArrayRoot = ReflectionTestUtils.invokeMethod(service, "parseJsonStringListMap", "[]");
        @SuppressWarnings("unchecked")
        Map<String, List<String>> fromScalarValue = ReflectionTestUtils.invokeMethod(
            service,
            "parseJsonStringListMap",
            "{\"Blue\":\"not-an-array\"}"
        );
        assertThat(fromArrayRoot).isEmpty();
        assertThat(fromScalarValue).isEmpty();
    }

    @Test
    void mergeColorImageMaps_mergesUrlsForSameColorKey() {
        String first = "{\"Blue\":[\"https://cdn.example.com/one.jpg\"]}";
        String second = "{\"Blue\":[\"https://cdn.example.com/two.jpg\"]}";
        String merged = ReflectionTestUtils.invokeMethod(service, "mergeColorImageMaps", first, second);
        assertThat(merged).contains("one.jpg").contains("two.jpg");
    }

    @Test
    void mergeColorsJsonPreferringRich_overwritesExistingRichObjectWithSameName() {
        String parent = "[{\"name\":\"Navy\",\"imageUrls\":[\"https://cdn.example.com/old.jpg\"]}]";
        String duplicate = "[{\"name\":\"Navy\",\"imageUrls\":[\"https://cdn.example.com/new.jpg\"]}]";
        String merged = ReflectionTestUtils.invokeMethod(service, "mergeColorsJsonPreferringRich", parent, duplicate);
        assertThat(merged).contains("new.jpg").doesNotContain("old.jpg");
    }

    @Test
    void isInvalidCustomFields_matchesSpacedCode120100() {
        RestClientResponseException ex = new RestClientResponseException(
            "cf",
            400,
            "Bad Request",
            null,
            "{\"code\": 120100}".getBytes(),
            null
        );
        Boolean invalid = ReflectionTestUtils.invokeMethod(ZohoSyncService.class, "isInvalidCustomFields", ex);
        assertThat(invalid).isTrue();
    }

    @Test
    void isInvalidGstTreatment_andPlaceOfSupply_matchZohoCode8Bodies() {
        RestClientResponseException gst = new RestClientResponseException(
            "gst",
            400,
            "Bad Request",
            null,
            "{\"code\":8,\"message\":\"invalid gst_treatment\"}".getBytes(),
            null
        );
        RestClientResponseException pos = new RestClientResponseException(
            "pos",
            400,
            "Bad Request",
            null,
            "{\"code\":8,\"message\":\"invalid place_of_supply\"}".getBytes(),
            null
        );
        assertThat((Boolean) ReflectionTestUtils.invokeMethod(ZohoSyncService.class, "isInvalidGstTreatment", gst)).isTrue();
        assertThat((Boolean) ReflectionTestUtils.invokeMethod(ZohoSyncService.class, "isInvalidPlaceOfSupply", pos)).isTrue();
    }

    @Test
    void isSalesOrderAlreadyInvoiced_matchesCode36026On400() {
        RestClientResponseException ex = new RestClientResponseException(
            "so",
            400,
            "Bad Request",
            null,
            "{\"code\":36026}".getBytes(),
            null
        );
        Boolean invoiced = ReflectionTestUtils.invokeMethod(ZohoSyncService.class, "isSalesOrderAlreadyInvoiced", ex);
        assertThat(invoiced).isTrue();
    }

    @Test
    void sameProduct_coversIdentityAndNullBranches() {
        Product left = new Product().id(1L);
        Product right = new Product().id(1L);
        assertThat((Boolean) ReflectionTestUtils.invokeMethod(ZohoSyncService.class, "sameProduct", left, right)).isTrue();
        assertThat((Boolean) ReflectionTestUtils.invokeMethod(ZohoSyncService.class, "sameProduct", left, left)).isTrue();
        assertThat((Boolean) ReflectionTestUtils.invokeMethod(ZohoSyncService.class, "sameProduct", left, null)).isFalse();
        assertThat((Boolean) ReflectionTestUtils.invokeMethod(ZohoSyncService.class, "sameProduct", new Product(), right)).isFalse();
    }

    @Test
    void mergeJsonUrlArrays_emptyUnion_returnsOriginalJson() {
        String merged = ReflectionTestUtils.invokeMethod(service, "mergeJsonUrlArrays", "[\"https://cdn.example.com/a.jpg\"]", "[]");
        assertThat(merged).contains("a.jpg");
        String secondOnly = ReflectionTestUtils.invokeMethod(service, "mergeJsonUrlArrays", (String) null, "[\"https://cdn.example.com/b.jpg\"]");
        assertThat(secondOnly).contains("b.jpg");
    }

    @Test
    void parseJsonStringArray_skipsNonTextualAndInvalidJson() {
        @SuppressWarnings("unchecked")
        List<String> fromObject = ReflectionTestUtils.invokeMethod(service, "parseJsonStringArray", "{\"x\":1}");
        @SuppressWarnings("unchecked")
        List<String> fromMixed = ReflectionTestUtils.invokeMethod(service, "parseJsonStringArray", "[\"https://cdn.example.com/a.jpg\",123]");
        assertThat(fromObject).isEmpty();
        assertThat(fromMixed).containsExactly("https://cdn.example.com/a.jpg");
    }

    @Test
    void deactivateSimpleDuplicateForVariantSku_deactivatesActiveSimpleDuplicate() {
        Product parent = new Product().id(1L).sku("PARENT");
        Product duplicate = new Product().id(2L).sku("VAR-SKU").itemStructure("SIMPLE").active(true);
        when(productRepository.findOneBySku("VAR-SKU")).thenReturn(Optional.of(duplicate));
        when(productRepository.save(duplicate)).thenAnswer(inv -> inv.getArgument(0));
        ZohoSyncSummary.Builder builder = ZohoSyncSummary.builder();

        Product result = ReflectionTestUtils.invokeMethod(service, "deactivateSimpleDuplicateForVariantSku", parent, "VAR-SKU", builder);

        assertThat(result).isSameAs(duplicate);
        assertThat(duplicate.getActive()).isFalse();
        assertThat(duplicate.getZohoSyncError()).contains("Superseded");
    }

    @Test
    void deactivateSimpleDuplicateForVariantSku_skipsVariantsParentAndSameId() {
        Product parent = new Product().id(1L).sku("PARENT");
        Product sameId = new Product().id(1L).sku("VAR-SKU").itemStructure("SIMPLE");
        when(productRepository.findOneBySku("VAR-SKU")).thenReturn(Optional.of(sameId));
        Object skippedSameId = ReflectionTestUtils.invokeMethod(
            service,
            "deactivateSimpleDuplicateForVariantSku",
            parent,
            "VAR-SKU",
            ZohoSyncSummary.builder()
        );
        assertThat(skippedSameId).isNull();

        Product variantParent = new Product().id(3L).sku("VAR-SKU").itemStructure("VARIANTS");
        when(productRepository.findOneBySku("VAR-SKU")).thenReturn(Optional.of(variantParent));
        Object skippedVariants = ReflectionTestUtils.invokeMethod(
            service,
            "deactivateSimpleDuplicateForVariantSku",
            parent,
            "VAR-SKU",
            ZohoSyncSummary.builder()
        );
        assertThat(skippedVariants).isNull();
    }
}
