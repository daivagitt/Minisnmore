package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.client.ZohoBooksClient;
import com.miniandmore.app.config.ApplicationProperties;
import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.CustomerProfile;
import com.miniandmore.app.domain.OrderItem;
import com.miniandmore.app.domain.Product;
import com.miniandmore.app.domain.ZohoSetting;
import com.miniandmore.app.domain.enumeration.OrderStatus;
import com.miniandmore.app.domain.enumeration.PaymentStatus;
import com.miniandmore.app.domain.enumeration.ZohoSyncStatus;
import com.miniandmore.app.repository.CustomerOrderRepository;
import com.miniandmore.app.repository.CustomerProfileRepository;
import com.miniandmore.app.repository.OrderItemRepository;
import com.miniandmore.app.repository.ProductRepository;
import com.miniandmore.app.repository.ZohoSettingRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

/**
 * JaCoCo branch gaps in {@link ZohoSyncService#pullProducts}, catalog upsert paths, order contact
 * resolution, and order-line Zoho item lookup.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ZohoSyncServiceTargetMethodsBranchTest {

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
    private ZohoSyncService service;

    @BeforeEach
    void setUp() {
        variantSupport = StorePricingTestSupport.variantSupport(objectMapper);
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
        lenient().when(customerOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(itemImageSync.isEnabled()).thenReturn(false);
    }

    // --- pullProducts ---

    @Test
    void pullProducts_skipsImageRetryWhenFailureMessageIsNotImageImport() {
        Product product = new Product()
            .id(881L)
            .sku("NO-RETRY")
            .zohoItemId("zoho-no-retry")
            .zohoSyncStatus(ZohoSyncStatus.FAILED)
            .zohoSyncError("Zoho API unavailable");
        Instant cursor = Instant.parse("2026-08-01T00:00:00Z");
        Map<String, Object> listRow = Map.of(
            "item_id",
            "zoho-no-retry",
            "sku",
            "NO-RETRY",
            "status",
            "active",
            "last_modified_time",
            "2026-01-01T00:00:00Z"
        );
        when(zohoSettingRepository.findActiveOrLatest()).thenReturn(Optional.of(new ZohoSetting().lastProductPullAt(cursor)));
        when(productRepository.findAll()).thenReturn(List.of(product));

        service.pullProducts(false);

        verify(booksClient, never()).getItem(any());
    }

    @Test
    void pullProducts_imageRetrySkipsSimpleProductWithoutZohoItemId() {
        Instant cursor = Instant.parse("2026-08-01T00:00:00Z");
        Product product = new Product()
            .id(884L)
            .sku("NO-ZOHO-ID")
            .zohoSyncStatus(ZohoSyncStatus.FAILED)
            .zohoSyncError("Image import failed: missing id");
        when(zohoSettingRepository.findActiveOrLatest()).thenReturn(Optional.of(new ZohoSetting().lastProductPullAt(cursor)));
        when(productRepository.findAll()).thenReturn(List.of(product));
        when(booksClient.listAllStatusItems(1)).thenReturn(new ZohoBooksClient.ItemPage(List.of(), false));

        service.pullProducts(false);

        verify(booksClient, never()).getItem(any());
    }

    @Test
    void pullProducts_retriesSimpleProductImageFailureDuringIncrementalPull() {
        Instant cursor = Instant.parse("2026-08-01T00:00:00Z");
        Product product = new Product()
            .id(882L)
            .sku("SIMPLE-RETRY")
            .zohoItemId("zoho-simple-retry")
            .zohoSyncStatus(ZohoSyncStatus.FAILED)
            .zohoSyncError("Image import failed: s3 down");
        Map<String, Object> listRow = Map.of(
            "item_id",
            "zoho-simple-retry",
            "sku",
            "SIMPLE-RETRY",
            "status",
            "active",
            "last_modified_time",
            "2026-01-01T00:00:00Z",
            "rate",
            10,
            "stock_on_hand",
            1
        );
        when(zohoSettingRepository.findActiveOrLatest()).thenReturn(Optional.of(new ZohoSetting().lastProductPullAt(cursor)));
        when(productRepository.findAll()).thenReturn(List.of(product));
        when(productRepository.findAllByZohoItemIdIsNotNull()).thenReturn(List.of());
        when(booksClient.listAllStatusItems(1)).thenReturn(new ZohoBooksClient.ItemPage(List.of(listRow), false));
        when(booksClient.getItem("zoho-simple-retry")).thenReturn(Map.of("item", listRow));
        when(productRepository.findOneByZohoItemId("zoho-simple-retry")).thenReturn(Optional.of(product));

        service.pullProducts(false);

        verify(booksClient).getItem("zoho-simple-retry");
    }

    @Test
    void pullProducts_variantImageRetryIgnoresRowsWithoutZohoItemId() {
        Instant cursor = Instant.parse("2026-08-01T00:00:00Z");
        Product parent = new Product()
            .id(883L)
            .sku("PARTIAL-MATRIX")
            .itemStructure(ProductVariantSupport.STRUCTURE_VARIANTS)
            .zohoSyncStatus(ZohoSyncStatus.FAILED)
            .zohoSyncError("Image import failed: variant image")
            .variantsJson(
                """
                [{"sku":"ROW-WITH-ID","size":"S","color":"Red","rate":1,"stock":1,"active":true,"zohoItemId":"zoho-row"},
                 {"sku":"ROW-NO-ID","size":"M","color":"Blue","rate":1,"stock":1,"active":true}]
                """
            );
        Map<String, Object> listRow = groupedZohoItem("zoho-other", "OTHER-SKU", "L", "Green", 1, 1);
        listRow.put("last_modified_time", "2026-01-01T00:00:00Z");
        when(zohoSettingRepository.findActiveOrLatest()).thenReturn(Optional.of(new ZohoSetting().lastProductPullAt(cursor)));
        when(productRepository.findAll()).thenReturn(List.of(parent));
        when(booksClient.listAllStatusItems(1)).thenReturn(new ZohoBooksClient.ItemPage(List.of(listRow), false));

        service.pullProducts(false);

        verify(booksClient, never()).getItem("zoho-row");
        verify(booksClient, never()).getItem("zoho-other");
    }

    @Test
    void pullProducts_itemPageWithMorePagesFetchesNextPage() {
        Map<String, Object> pageOneItem = Map.of(
            "item_id",
            "zoho-p1",
            "sku",
            "PAGE-ONE",
            "status",
            "active",
            "rate",
            1,
            "stock_on_hand",
            1
        );
        when(zohoSettingRepository.findActiveOrLatest()).thenReturn(Optional.empty());
        when(productRepository.findAll()).thenReturn(List.of());
        when(productRepository.findAllByZohoItemIdIsNotNull()).thenReturn(List.of());
        when(booksClient.listItems(1)).thenReturn(new ZohoBooksClient.ItemPage(List.of(pageOneItem), true));
        when(booksClient.listItems(2)).thenReturn(new ZohoBooksClient.ItemPage(List.of(), false));
        when(booksClient.getItem("zoho-p1")).thenReturn(Map.of("item", pageOneItem));
        when(productRepository.findMaxSortOrder()).thenReturn(0);
        when(productRepository.findOneByZohoItemId(any())).thenReturn(Optional.empty());
        when(productRepository.findOneBySku(any())).thenReturn(Optional.empty());

        service.pullProducts(true);

        verify(booksClient).listItems(2);
    }

    @Test
    void pullProducts_retriesVariantRowImageFailureDuringIncrementalPull() {
        Instant cursor = Instant.parse("2026-08-01T00:00:00Z");
        Product parent = new Product()
            .id(880L)
            .sku("MATRIX-PARENT")
            .zohoItemId("grp-matrix")
            .itemStructure(ProductVariantSupport.STRUCTURE_VARIANTS)
            .zohoSyncStatus(ZohoSyncStatus.FAILED)
            .zohoSyncError("Image import failed: timeout")
            .variantsJson(
                """
                [{"sku":"MATRIX-S-RED","size":"S","color":"Red","rate":499,"stock":2,"active":true,"zohoItemId":"zoho-matrix-s"}]
                """
            );
        Map<String, Object> listRow = groupedZohoItem("zoho-matrix-s", "MATRIX-S-RED", "S", "Red", 499, 2);
        listRow.put("last_modified_time", "2026-01-01T00:00:00Z");

        when(zohoSettingRepository.findActiveOrLatest()).thenReturn(Optional.of(new ZohoSetting().lastProductPullAt(cursor)));
        when(productRepository.findAll()).thenReturn(List.of(parent));
        when(productRepository.findAllByZohoItemIdIsNotNull()).thenReturn(List.of());
        when(booksClient.listAllStatusItems(1)).thenReturn(new ZohoBooksClient.ItemPage(List.of(listRow), false));
        when(booksClient.getItem("zoho-matrix-s")).thenReturn(Map.of("item", listRow));
        when(productRepository.findOneByZohoItemId(any())).thenReturn(Optional.empty());
        when(productRepository.findOneBySku(any())).thenReturn(Optional.empty());

        Map<String, Object> result = service.pullProducts(false);

        assertThat(result.get("success")).isEqualTo(1);
        verify(booksClient).getItem("zoho-matrix-s");
    }

    @Test
    void pullProducts_itemPageWithoutMorePagesCompletesFullPull() {
        Map<String, Object> simple = Map.of(
            "item_id",
            "zoho-simple",
            "sku",
            "SIMPLE-ONLY",
            "name",
            "Simple Tee",
            "status",
            "active",
            "rate",
            299,
            "stock_on_hand",
            4
        );
        when(zohoSettingRepository.findActiveOrLatest()).thenReturn(Optional.empty());
        when(productRepository.findAll()).thenReturn(List.of());
        when(productRepository.findAllByZohoItemIdIsNotNull()).thenReturn(List.of());
        when(booksClient.listItems(1)).thenReturn(new ZohoBooksClient.ItemPage(List.of(simple), false));
        when(booksClient.getItem("zoho-simple")).thenReturn(Map.of("item", simple));
        when(productRepository.findMaxSortOrder()).thenReturn(0);
        when(productRepository.findOneByZohoItemId("zoho-simple")).thenReturn(Optional.empty());
        when(productRepository.findOneBySku("SIMPLE-ONLY")).thenReturn(Optional.empty());

        Map<String, Object> result = service.pullProducts(true);

        assertThat(result.get("success")).isEqualTo(1);
        verify(booksClient, never()).listItems(2);
        verify(zohoSettingRepository).save(any(ZohoSetting.class));
    }

    @Test
    void pullProducts_nullItemListRecordsFailureWithoutStaleCleanup() {
        when(zohoSettingRepository.findActiveOrLatest()).thenReturn(Optional.empty());
        when(productRepository.findAll()).thenReturn(List.of());
        when(booksClient.listItems(1)).thenReturn(null);

        Map<String, Object> result = service.pullProducts(true);

        assertThat(result.get("failed")).isEqualTo(1);
        verify(productRepository, never()).findAllByZohoItemIdIsNotNull();
    }

    @Test
    void pullProducts_resourceAccessOnFirstPageStopsPull() {
        when(zohoSettingRepository.findActiveOrLatest()).thenReturn(Optional.empty());
        when(productRepository.findAll()).thenReturn(List.of());
        when(booksClient.listItems(1)).thenThrow(new ResourceAccessException("connection reset"));

        Map<String, Object> result = service.pullProducts(true);

        assertThat(result.get("failed")).isEqualTo(1);
        verify(booksClient, never()).listItems(2);
    }

    @Test
    void pullProducts_inactiveResolvedItemWithLocalMatchDoesNotCountAsPulledActiveSku() {
        Product local = new Product().id(50L).sku("GONE-SKU").zohoItemId("zoho-gone").active(true);
        Map<String, Object> listRow = Map.of("item_id", "zoho-gone", "sku", "GONE-SKU", "status", "active");
        Map<String, Object> detail = new LinkedHashMap<>(listRow);
        detail.put("status", "inactive");

        when(zohoSettingRepository.findActiveOrLatest()).thenReturn(Optional.empty());
        when(productRepository.findAll()).thenReturn(List.of(local));
        when(productRepository.findAllByZohoItemIdIsNotNull()).thenReturn(List.of(local));
        when(booksClient.listItems(1)).thenReturn(List.of(listRow));
        when(booksClient.listItems(2)).thenReturn(List.of());
        when(booksClient.getItem("zoho-gone")).thenReturn(Map.of("item", detail));
        when(productRepository.findOneByZohoItemId("zoho-gone")).thenReturn(Optional.of(local));

        service.pullProducts(true);

        verify(productRepository).save(local);
        assertThat(local.getActive()).isFalse();
    }

    @Test
    void registerUniqueZohoListRow_duplicateItemIdSameSkuIsSkipped() {
        ZohoSyncSummary.Builder builder = ZohoSyncSummary.builder();
        Map<String, String> byItem = new LinkedHashMap<>();
        Map<String, String> bySku = new LinkedHashMap<>();
        byItem.put("zoho-dup", "sku-dup");
        bySku.put("sku-dup", "zoho-dup");

        Boolean ok = ReflectionTestUtils.invokeMethod(
            ZohoSyncService.class,
            "registerUniqueZohoListRow",
            "zoho-dup",
            "SKU-DUP",
            byItem,
            bySku,
            builder
        );

        assertThat(ok).isFalse();
        assertThat(builder.build().toMap().get("skipped")).isNotNull();
    }

    @Test
    void pullStoppedMessage_rateLimitUsesDedicatedCopy() {
        RestClientResponseException rateLimit = new RestClientResponseException(
            "limit",
            429,
            "Too Many Requests",
            null,
            "{}".getBytes(),
            null
        );
        String message = ReflectionTestUtils.invokeMethod(ZohoSyncService.class, "pullStoppedMessage", rateLimit, 3);
        assertThat(message).contains("rate limit").contains("page 2");
    }

    // --- upsertPulledProduct / expected parent ---

    @Test
    void upsertPulledProduct_inactiveItemDeactivatesLocalAndReturnsFalse() {
        Product local = new Product().id(3L).sku("OLD").zohoItemId("zoho-old").active(true);
        Map<String, Object> inactive = Map.of("item_id", "zoho-old", "sku", "OLD", "status", "inactive");
        ZohoSyncSummary.Builder builder = ZohoSyncSummary.builder();
        when(productRepository.findOneByZohoItemId("zoho-old")).thenReturn(Optional.of(local));

        Boolean added = ReflectionTestUtils.invokeMethod(
            service,
            "upsertPulledProduct",
            inactive,
            false,
            builder,
            new ArrayList<Product>(),
            null,
            false
        );

        assertThat(added).isFalse();
        assertThat(local.getActive()).isFalse();
    }

    @Test
    void upsertPulledProduct_variantMissingOnlyColorRecordsNoticeWhenNotStrict() {
        Map<String, Object> variant = new LinkedHashMap<>();
        variant.put("item_id", "zoho-size-only");
        variant.put("sku", "SIZE-ONLY-VAR");
        variant.put("group_id", "grp-size");
        variant.put("group_name", "Size Only");
        variant.put("status", "active");
        variant.put("attribute_name1", "Size");
        variant.put("attribute_option_name1", "M");
        ZohoSyncSummary.Builder builder = ZohoSyncSummary.builder();
        when(productRepository.findMaxSortOrder()).thenReturn(0);
        when(productRepository.findOneBySku(any())).thenReturn(Optional.empty());
        when(productRepository.findOneBySlug(any())).thenReturn(Optional.empty());

        Boolean counted = ReflectionTestUtils.invokeMethod(
            service,
            "upsertPulledProduct",
            variant,
            false,
            builder,
            new ArrayList<Product>(),
            null,
            false
        );

        assertThat(counted).isTrue();
        assertThat(builder.build().toMap().get("notices")).isNotNull();
    }

    @Test
    void upsertPulledProduct_ignoresBlankStockOnHand() {
        Product existing = new Product().id(44L).sku("STOCK-SKU").zohoItemId("zoho-stock").stock(3);
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("item_id", "zoho-stock");
        item.put("sku", "STOCK-SKU");
        item.put("status", "active");
        item.put("stock_on_hand", "   ");
        when(productRepository.findOneByZohoItemId("zoho-stock")).thenReturn(Optional.of(existing));

        ReflectionTestUtils.invokeMethod(service, "upsertPulledProduct", item, false, ZohoSyncSummary.builder(), new ArrayList<Product>());

        assertThat(existing.getStock()).isEqualTo(3);
    }

    @Test
    void upsertPulledProduct_variantMissingSizeColorRecordsNoticeWhenNotStrict() {
        Map<String, Object> variant = new LinkedHashMap<>();
        variant.put("item_id", "zoho-notice-var");
        variant.put("sku", "NOTICE-VAR");
        variant.put("group_id", "grp-notice");
        variant.put("group_name", "Notice Group");
        variant.put("status", "active");
        ZohoSyncSummary.Builder builder = ZohoSyncSummary.builder();
        when(productRepository.findMaxSortOrder()).thenReturn(0);
        when(productRepository.findOneBySku(any())).thenReturn(Optional.empty());
        when(productRepository.findOneBySlug(any())).thenReturn(Optional.empty());

        Boolean counted = ReflectionTestUtils.invokeMethod(
            service,
            "upsertPulledProduct",
            variant,
            false,
            builder,
            new ArrayList<Product>(),
            null,
            false
        );

        assertThat(counted).isTrue();
        assertThat(builder.build().failureCount()).isZero();
        assertThat(builder.build().toMap().get("notices")).isNotNull();
    }

    @Test
    void upsertPulledProduct_variantMissingSizeColorWithFailFlagRecordsFailure() {
        Map<String, Object> variant = new LinkedHashMap<>();
        variant.put("item_id", "zoho-bad-var");
        variant.put("sku", "BAD-VAR-SKU");
        variant.put("group_id", "grp-bad");
        variant.put("group_name", "Bad Group");
        variant.put("status", "active");
        variant.put("rate", 10);
        ZohoSyncSummary.Builder builder = ZohoSyncSummary.builder();
        when(productRepository.findMaxSortOrder()).thenReturn(0);
        when(productRepository.findOneBySku(any())).thenReturn(Optional.empty());
        when(productRepository.findOneBySlug(any())).thenReturn(Optional.empty());

        Boolean counted = ReflectionTestUtils.invokeMethod(
            service,
            "upsertPulledProduct",
            variant,
            false,
            builder,
            new ArrayList<Product>(),
            null,
            true
        );

        assertThat(counted).isTrue();
        assertThat(builder.build().failureCount()).isEqualTo(1);
    }

    @Test
    void upsertPulledProduct_updatesExistingProductWithoutItemIdInPayload() {
        Product existing = new Product().id(41L).sku("KEEP-SKU").zohoItemId("zoho-keep").active(true);
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("sku", "KEEP-SKU");
        item.put("name", "Keep Name");
        item.put("status", "active");
        when(productRepository.findOneByZohoItemId(any())).thenReturn(Optional.empty());
        when(productRepository.findOneBySku("KEEP-SKU")).thenReturn(Optional.of(existing));

        ReflectionTestUtils.invokeMethod(service, "upsertPulledProduct", item, false, ZohoSyncSummary.builder(), new ArrayList<Product>());

        assertThat(existing.getZohoItemId()).isEqualTo("zoho-keep");
        assertThat(existing.getName()).isEqualTo("Keep Name");
        verify(productRepository).save(existing);
    }

    @Test
    void upsertPulledProduct_skipsPriceAndStockForVariantParents() {
        Product parent = new Product()
            .id(42L)
            .sku("VAR-PARENT")
            .itemStructure(ProductVariantSupport.STRUCTURE_VARIANTS)
            .variantsJson("[]")
            .price(BigDecimal.valueOf(100))
            .stock(5);
        Map<String, Object> item = Map.of(
            "item_id",
            "zoho-simple-on-parent",
            "sku",
            "VAR-PARENT",
            "status",
            "active",
            "rate",
            999,
            "stock_on_hand",
            50
        );
        when(productRepository.findOneByZohoItemId("zoho-simple-on-parent")).thenReturn(Optional.of(parent));

        ReflectionTestUtils.invokeMethod(service, "upsertPulledProduct", item, false, ZohoSyncSummary.builder(), new ArrayList<Product>());

        assertThat(parent.getPrice()).isEqualByComparingTo("100");
        assertThat(parent.getStock()).isEqualTo(5);
    }

    @Test
    void upsertPulledProduct_importsImageWhenImageSyncEnabled() {
        Product existing = new Product().id(43L).sku("IMG-SKU").active(true);
        Map<String, Object> item = Map.of(
            "item_id",
            "zoho-img-sku",
            "sku",
            "IMG-SKU",
            "status",
            "active",
            "rate",
            10
        );
        when(productRepository.findOneByZohoItemId("zoho-img-sku")).thenReturn(Optional.of(existing));
        when(itemImageSync.isEnabled()).thenReturn(true);

        ReflectionTestUtils.invokeMethod(service, "upsertPulledProduct", item, false, ZohoSyncSummary.builder(), new ArrayList<Product>());

        verify(itemImageSync).importZohoItemImage(existing, "zoho-img-sku", item);
    }

    @Test
    void upsertPulledProduct_renamesLocalSkuWhenZohoSkuChanges() {
        Product existing = new Product().id(4L).sku("OLD-SKU").zohoItemId("zoho-stable").active(true);
        Map<String, Object> item = Map.of(
            "item_id",
            "zoho-stable",
            "sku",
            "NEW-SKU",
            "name",
            "Renamed",
            "status",
            "active",
            "rate",
            50,
            "stock_on_hand",
            2
        );
        when(productRepository.findOneByZohoItemId("zoho-stable")).thenReturn(Optional.of(existing));

        Boolean ok = ReflectionTestUtils.invokeMethod(
            service,
            "upsertPulledProduct",
            item,
            false,
            ZohoSyncSummary.builder(),
            new ArrayList<Product>()
        );

        assertThat(ok).isTrue();
        assertThat(existing.getSku()).isEqualTo("NEW-SKU");
    }

    @Test
    void upsertPulledProductIntoExpectedVariantParent_rejectsItemLinkedToOtherParent() {
        Product expected = new Product().id(1L).sku("EXPECTED").itemStructure(ProductVariantSupport.STRUCTURE_VARIANTS);
        Product other = new Product()
            .id(2L)
            .sku("OTHER-PARENT")
            .itemStructure(ProductVariantSupport.STRUCTURE_VARIANTS)
            .variantsJson(
                """
                [{"sku":"TAKEN-S","size":"S","color":"Red","rate":1,"stock":1,"active":true,"zohoItemId":"zoho-taken"}]
                """
            );
        Map<String, Object> item = groupedZohoItem("zoho-taken", "TAKEN-S", "S", "Red", 1, 1);
        List<Product> parents = new ArrayList<>(List.of(expected, other));
        ZohoSyncSummary.Builder builder = ZohoSyncSummary.builder();

        Boolean merged = ReflectionTestUtils.invokeMethod(
            service,
            "upsertPulledProductIntoExpectedVariantParent",
            item,
            "TAKEN-S",
            false,
            builder,
            parents,
            expected
        );

        assertThat(merged).isFalse();
        assertThat(builder.build().failureCount()).isEqualTo(1);
    }

    @Test
    void upsertPulledProductIntoExpectedVariantParent_allowsMatchingGroupIdOnParent() {
        Product expected = new Product()
            .id(14L)
            .sku("PARENT-14")
            .zohoItemId("grp-match")
            .itemStructure(ProductVariantSupport.STRUCTURE_VARIANTS)
            .variantsJson("[]");
        Map<String, Object> item = groupedZohoItem("zoho-match-child", "MATCH-S", "S", "Gold", 10, 1);
        item.put("group_id", "grp-match");
        when(productRepository.findOneByZohoItemId("grp-match")).thenReturn(Optional.of(expected));

        Boolean merged = ReflectionTestUtils.invokeMethod(
            service,
            "upsertPulledProductIntoExpectedVariantParent",
            item,
            "MATCH-S",
            false,
            ZohoSyncSummary.builder(),
            List.of(expected),
            expected
        );

        assertThat(merged).isTrue();
    }

    @Test
    void upsertPulledProductIntoExpectedVariantParent_rejectsMismatchedGroupId() {
        Product expected = new Product()
            .id(10L)
            .sku("PARENT-10")
            .zohoItemId("grp-expected")
            .itemStructure(ProductVariantSupport.STRUCTURE_VARIANTS);
        Map<String, Object> item = groupedZohoItem("zoho-child", "CHILD-S", "S", "Blue", 10, 1);
        item.put("group_id", "grp-other");

        Boolean merged = ReflectionTestUtils.invokeMethod(
            service,
            "upsertPulledProductIntoExpectedVariantParent",
            item,
            "CHILD-S",
            false,
            ZohoSyncSummary.builder(),
            List.of(expected),
            expected
        );

        assertThat(merged).isFalse();
    }

    @Test
    void upsertPulledProductIntoExpectedVariantParent_allowsItemAlreadyOnExpectedParent() {
        Product expected = new Product()
            .id(12L)
            .sku("PARENT-12")
            .itemStructure(ProductVariantSupport.STRUCTURE_VARIANTS)
            .variantsJson(
                """
                [{"sku":"OWNED-S","size":"S","color":"Red","rate":1,"stock":1,"active":true,"zohoItemId":"zoho-owned"}]
                """
            );
        Map<String, Object> item = groupedZohoItem("zoho-owned", "OWNED-S", "S", "Red", 1, 1);

        Boolean merged = ReflectionTestUtils.invokeMethod(
            service,
            "upsertPulledProductIntoExpectedVariantParent",
            item,
            "OWNED-S",
            false,
            ZohoSyncSummary.builder(),
            List.of(expected),
            expected
        );

        assertThat(merged).isTrue();
    }

    @Test
    void upsertPulledProductIntoExpectedVariantParent_usesLocalSizeColorWhenZohoOmitsAttributes() {
        Product expected = new Product()
            .id(13L)
            .sku("PARENT-13")
            .itemStructure(ProductVariantSupport.STRUCTURE_VARIANTS)
            .variantsJson(
                """
                [{"sku":"LOCAL-ATTR","size":"M","color":"Teal","rate":1,"stock":1,"active":true}]
                """
            );
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("item_id", "zoho-local-attr");
        item.put("sku", "LOCAL-ATTR");
        item.put("status", "active");
        item.put("group_id", "grp-local");
        item.put("rate", 5);
        item.put("stock_on_hand", 2);

        Boolean merged = ReflectionTestUtils.invokeMethod(
            service,
            "upsertPulledProductIntoExpectedVariantParent",
            item,
            "LOCAL-ATTR",
            false,
            ZohoSyncSummary.builder(),
            List.of(expected),
            expected
        );

        assertThat(merged).isTrue();
        assertThat(expected.getVariantsJson()).contains("zoho-local-attr");
    }

    @Test
    void upsertPulledProductIntoExpectedVariantParent_mergesWhenLinkedToSameParent() {
        Product expected = new Product()
            .id(11L)
            .sku("PARENT-11")
            .itemStructure(ProductVariantSupport.STRUCTURE_VARIANTS)
            .variantsJson("[]");
        Map<String, Object> item = groupedZohoItem("zoho-new", "NEW-S", "S", "Green", 20, 3);

        Boolean merged = ReflectionTestUtils.invokeMethod(
            service,
            "upsertPulledProductIntoExpectedVariantParent",
            item,
            "NEW-S",
            false,
            ZohoSyncSummary.builder(),
            List.of(expected),
            expected
        );

        assertThat(merged).isTrue();
        assertThat(expected.getVariantsJson()).contains("NEW-S");
    }

    // --- deactivateStaleZohoProducts ---

    @Test
    void deactivateStaleZohoProducts_deactivatesWhenNeitherSkuNorItemIdPresent() {
        Product stale = new Product().id(25L).sku("STALE-25").zohoItemId("zoho-stale-25").active(true);
        when(productRepository.findAllByZohoItemIdIsNotNull()).thenReturn(List.of(stale));

        ReflectionTestUtils.invokeMethod(
            service,
            "deactivateStaleZohoProducts",
            Set.of("other"),
            Set.of("other-id"),
            List.of(),
            ZohoSyncSummary.builder()
        );

        assertThat(stale.getActive()).isFalse();
    }

    @Test
    void deactivateStaleZohoProducts_keepsSimpleProductWhenPulledBySkuOnly() {
        Product linked = new Product().id(24L).sku("BY-SKU").zohoItemId("zoho-not-in-set").active(true);
        when(productRepository.findAllByZohoItemIdIsNotNull()).thenReturn(List.of(linked));

        ReflectionTestUtils.invokeMethod(
            service,
            "deactivateStaleZohoProducts",
            Set.of("by-sku"),
            Set.of("other-id"),
            List.of(),
            ZohoSyncSummary.builder()
        );

        assertThat(linked.getActive()).isTrue();
    }

    @Test
    void deactivateStaleZohoProducts_keepsSimpleProductWhenPulledByItemIdOnly() {
        Product linked = new Product().id(20L).sku("BY-ID").zohoItemId("zoho-by-id").active(true);
        when(productRepository.findAllByZohoItemIdIsNotNull()).thenReturn(List.of(linked));
        ZohoSyncSummary.Builder builder = ZohoSyncSummary.builder();

        ReflectionTestUtils.invokeMethod(
            service,
            "deactivateStaleZohoProducts",
            Set.of("other-sku"),
            Set.of("zoho-by-id"),
            List.of(),
            builder
        );

        assertThat(linked.getActive()).isTrue();
        verify(productRepository, never()).save(linked);
    }

    @Test
    void deactivateStaleZohoProducts_skipsAlreadyInactiveSimpleProduct() {
        Product inactive = new Product().id(21L).sku("DEAD").zohoItemId("zoho-dead").active(false);
        when(productRepository.findAllByZohoItemIdIsNotNull()).thenReturn(List.of(inactive));

        ReflectionTestUtils.invokeMethod(
            service,
            "deactivateStaleZohoProducts",
            Set.of(),
            Set.of(),
            List.of(),
            ZohoSyncSummary.builder()
        );

        verify(productRepository, never()).save(inactive);
    }

    @Test
    void deactivateStaleZohoProducts_deactivatesMissingVariantRowsAndNoticesParent() {
        Product parent = new Product()
            .id(22L)
            .sku("PARENT-22")
            .itemStructure(ProductVariantSupport.STRUCTURE_VARIANTS)
            .active(true)
            .variantsJson(
                """
                [{"sku":"LIVE-VAR","size":"S","color":"Red","rate":1,"stock":1,"active":true,"zohoItemId":"zoho-live"},
                 {"sku":"STALE-VAR","size":"M","color":"Blue","rate":1,"stock":1,"active":true,"zohoItemId":"zoho-stale"}]
                """
            );
        when(productRepository.findAllByZohoItemIdIsNotNull()).thenReturn(List.of());

        ReflectionTestUtils.invokeMethod(
            service,
            "deactivateStaleZohoProducts",
            Set.of("live-var"),
            Set.of("zoho-live"),
            List.of(parent),
            ZohoSyncSummary.builder()
        );

        assertThat(parent.getVariantsJson()).contains("\"active\":false");
        assertThat(parent.getVariantsJson()).contains("STALE-VAR");
        verify(productRepository).save(parent);
    }

    @Test
    void deactivateStaleZohoProducts_skipsVariantParentWithNullSkuWhenNoticing() {
        Product parent = new Product()
            .id(23L)
            .sku("   ")
            .itemStructure(ProductVariantSupport.STRUCTURE_VARIANTS)
            .active(true)
            .variantsJson(
                """
                [{"sku":"ONLY-VAR","size":"S","color":"Red","rate":1,"stock":1,"active":true,"zohoItemId":"zoho-only"}]
                """
            );
        when(productRepository.findAllByZohoItemIdIsNotNull()).thenReturn(List.of());

        ReflectionTestUtils.invokeMethod(
            service,
            "deactivateStaleZohoProducts",
            Set.of(),
            Set.of(),
            List.of(parent),
            ZohoSyncSummary.builder()
        );

        assertThat(parent.getActive()).isFalse();
    }

    // --- resolveOrCreateContact ---

    @Test
    void resolveOrCreateContact_blankContactIdFromEmailLookupFallsThroughToCreate() {
        CustomerOrder order = new CustomerOrder()
            .orderNumber("MNM-NO-ID")
            .userLogin("shopper@example.com")
            .shippingAddressJson("{\"full_name\":\"Shopper Name\",\"email\":\"shopper@example.com\"}");
        when(booksClient.findContactByEmail("shopper@example.com")).thenReturn(Map.of("contact_name", "Shopper Name"));
        when(booksClient.findContactByName("Shopper Name")).thenReturn(null);
        when(booksClient.createContact(any())).thenReturn(Map.of("contact", Map.of("contact_id", "cust-created")));

        String contactId = ReflectionTestUtils.invokeMethod(service, "resolveOrCreateContact", order);

        assertThat(contactId).isEqualTo("cust-created");
        verify(booksClient).createContact(any());
    }

    @Test
    void resolveOrCreateContact_usesProfilePhoneWhenShippingPhoneMissing() {
        CustomerOrder order = new CustomerOrder()
            .orderNumber("MNM-PHONE")
            .userLogin("user@example.com")
            .shippingAddressJson("{\"full_name\":\"Profile User\"}");
        when(customerProfileRepository.findOneByUserLogin("user@example.com"))
            .thenReturn(Optional.of(new CustomerProfile().userLogin("user@example.com").phone("9988776655")));
        when(booksClient.findContactByEmail("user@example.com")).thenReturn(null);
        when(booksClient.findContactByName("Profile User")).thenReturn(null);
        when(booksClient.createContact(any())).thenReturn(Map.of("contact", Map.of("contact_id", "cust-phone")));

        ReflectionTestUtils.invokeMethod(service, "resolveOrCreateContact", order);

        verify(booksClient).createContact(argThat(payload -> {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> persons = (List<Map<String, Object>>) payload.get("contact_persons");
            return persons != null && "9988776655".equals(persons.get(0).get("phone"));
        }));
    }

    @Test
    void syncOrder_rethrowsUnexpectedContactEmailLookupErrors() {
        CustomerOrder order = new CustomerOrder()
            .id(501L)
            .orderNumber("MNM-EMAIL-500")
            .userLogin("buyer@example.com")
            .paymentStatus(PaymentStatus.PAID)
            .status(OrderStatus.CONFIRMED);
        when(customerOrderRepository.findById(501L)).thenReturn(Optional.of(order));
        when(booksClient.findContactByEmail("buyer@example.com")).thenThrow(new RestClientResponseException("down", 503, "Error", null, "{}".getBytes(), null));

        service.syncOrder(501L);

        assertThat(order.getZohoSyncStatus()).isEqualTo(ZohoSyncStatus.FAILED);
        assertThat(order.getZohoSyncError()).contains("down");
    }

    @Test
    void resolveOrCreateContact_invalidEmailLookupContinuesToCreate() {
        CustomerOrder order = new CustomerOrder()
            .orderNumber("MNM-BAD-MAIL")
            .userLogin("not-an-email")
            .shippingAddressJson("{\"full_name\":\"Guest Shopper\",\"email\":\"not-an-email\"}");
        when(booksClient.findContactByEmail("not-an-email")).thenThrow(invalidEmailError());
        when(booksClient.findContactByName("Guest Shopper")).thenReturn(null);
        when(booksClient.createContact(any())).thenReturn(Map.of("contact", Map.of("contact_id", "cust-guest")));

        String contactId = ReflectionTestUtils.invokeMethod(service, "resolveOrCreateContact", order);

        assertThat(contactId).isEqualTo("cust-guest");
    }

    @Test
    void resolveOrCreateContact_nonDuplicateCreateErrorRethrows() {
        CustomerOrder order = new CustomerOrder()
            .orderNumber("MNM-CREATE-ERR")
            .shippingAddressJson("{\"full_name\":\"Error Buyer\"}");
        when(booksClient.findContactByName("Error Buyer")).thenReturn(null);
        when(booksClient.createContact(any())).thenThrow(new RestClientResponseException("fail", 500, "Error", null, "{}".getBytes(), null));

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service, "resolveOrCreateContact", order))
            .isInstanceOf(RestClientResponseException.class);
    }

    @Test
    void resolveOrCreateContact_duplicateCreateWithoutResolvableNameRethrows() {
        CustomerOrder order = new CustomerOrder()
            .orderNumber("MNM-DUP-FAIL")
            .shippingAddressJson("{\"full_name\":\"Unique Buyer\"}");
        when(booksClient.findContactByName("Unique Buyer")).thenReturn(null);
        when(booksClient.createContact(any())).thenThrow(duplicateContactError());

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service, "resolveOrCreateContact", order))
            .isInstanceOf(RestClientResponseException.class);
    }

    // --- resolveZohoItemIdForOrderLine ---

    @Test
    void resolveZohoItemIdForOrderLine_pushesVariantWhenBooksLookupMissingItemId() {
        String variants = "[{\"sku\":\"PUSH-VAR\",\"size\":\"L\",\"color\":\"Navy\",\"rate\":10,\"stock\":1,\"active\":true}]";
        Product product = new Product().id(30L).sku("PARENT-30").itemStructure(ProductVariantSupport.STRUCTURE_VARIANTS).variantsJson(variants);
        OrderItem line = new OrderItem().productId("30").variantSku("PUSH-VAR");
        when(productRepository.findById(30L)).thenReturn(Optional.of(product));
        when(booksClient.findItemBySku("PUSH-VAR")).thenReturn(Map.of("sku", "PUSH-VAR"));
        when(booksClient.createItem(any())).thenReturn(Map.of("item", Map.of("item_id", "zoho-pushed")));

        Optional<String> itemId = ReflectionTestUtils.invokeMethod(service, "resolveZohoItemIdForOrderLine", line);

        assertThat(itemId).contains("zoho-pushed");
        assertThat(product.getVariantsJson()).contains("zoho-pushed");
    }

    @Test
    void resolveZohoItemIdForOrderLine_createsSimpleProductWhenZohoHasNoRow() {
        Product product = new Product().id(31L).sku("CREATE-ME").name("Create Me").active(true).price(BigDecimal.TEN);
        OrderItem line = new OrderItem().productId("31");
        when(productRepository.findById(31L)).thenReturn(Optional.of(product));
        when(booksClient.findItemBySku("CREATE-ME")).thenReturn(null);
        when(booksClient.createItem(any())).thenReturn(Map.of("item", Map.of("item_id", "zoho-created")));

        Optional<String> itemId = ReflectionTestUtils.invokeMethod(service, "resolveZohoItemIdForOrderLine", line);

        assertThat(itemId).contains("zoho-created");
        verify(booksClient).createItem(any());
    }

    @Test
    void resolveZohoItemIdForOrderLine_doesNotBackfillVariantParentFromBooksLookup() {
        Product parent = new Product()
            .id(34L)
            .sku("VAR-PARENT-34")
            .itemStructure(ProductVariantSupport.STRUCTURE_VARIANTS)
            .variantsJson("[{\"sku\":\"VAR-34\",\"size\":\"S\",\"color\":\"Red\",\"rate\":1,\"stock\":1,\"active\":true}]");
        OrderItem line = new OrderItem().productId("34").variantSku("VAR-34");
        when(productRepository.findById(34L)).thenReturn(Optional.of(parent));
        when(booksClient.findItemBySku("VAR-34")).thenReturn(Map.of("item_id", "zoho-var-34"));

        ReflectionTestUtils.invokeMethod(service, "resolveZohoItemIdForOrderLine", line);

        verify(productRepository).save(parent);
        assertThat(parent.getVariantsJson()).contains("zoho-var-34");
    }

    @Test
    void resolveZohoItemIdForOrderLine_skipsBackfillWhenProductAlreadyLinked() {
        Product product = new Product().id(32L).sku("LINKED").zohoItemId("zoho-already");
        OrderItem line = new OrderItem().productId("32");
        when(productRepository.findById(32L)).thenReturn(Optional.of(product));
        when(booksClient.findItemBySku("LINKED")).thenReturn(Map.of("item_id", "zoho-from-books"));

        Optional<String> itemId = ReflectionTestUtils.invokeMethod(service, "resolveZohoItemIdForOrderLine", line);

        assertThat(itemId).contains("zoho-already");
        verify(productRepository, never()).save(product);
    }

    @Test
    void resolveZohoItemIdForOrderLine_throwsWhenCreateReturnsBlankItemId() {
        Product product = new Product().id(33L).sku("BLANK-ID").name("Blank").active(false);
        OrderItem line = new OrderItem().productId("33");
        when(productRepository.findById(33L)).thenReturn(Optional.of(product));
        when(booksClient.findItemBySku("BLANK-ID")).thenReturn(null);

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service, "resolveZohoItemIdForOrderLine", line))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Could not create an active Zoho item");
    }

    @Test
    void resolveZohoItemIdForOrderLine_throwsWhenNoLocalProductToCreateMissingSku() {
        OrderItem line = new OrderItem().variantSku("ORPHAN-SKU");
        when(booksClient.findItemBySku("ORPHAN-SKU")).thenReturn(null);

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service, "resolveZohoItemIdForOrderLine", line))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("No local product");
    }

    // --- syncOrder ---

    @Test
    void syncOrder_missingOrderIsNoOp() {
        when(customerOrderRepository.findById(9999L)).thenReturn(Optional.empty());

        service.syncOrder(9999L);

        verify(booksClient, never()).findContactByEmail(any());
        verify(customerOrderRepository, never()).save(any());
    }

    @Test
    void syncOrder_failurePathPersistsFailedStatus() {
        CustomerOrder order = new CustomerOrder()
            .id(500L)
            .orderNumber("MNM-FAIL-SYNC")
            .userLogin("fail@example.com")
            .paymentStatus(PaymentStatus.PAID)
            .status(OrderStatus.CONFIRMED);
        when(customerOrderRepository.findById(500L)).thenReturn(Optional.of(order));
        when(booksClient.findContactByEmail("fail@example.com")).thenThrow(new RuntimeException("Zoho down"));

        service.syncOrder(500L);

        assertThat(order.getZohoSyncStatus()).isEqualTo(ZohoSyncStatus.FAILED);
        assertThat(order.getZohoSyncError()).contains("Zoho down");
        verify(customerOrderRepository).save(order);
    }

    @Test
    void mergePulledZohoVariantItem_clearsImagesWhenImageSyncDisabled() {
        Product parent = new Product()
            .id(40L)
            .sku("IMG-PARENT")
            .itemStructure(ProductVariantSupport.STRUCTURE_VARIANTS)
            .variantsJson(
                """
                [{"sku":"IMG-VAR","size":"S","color":"Coral","rate":1,"stock":1,"active":true,"zohoItemId":"zoho-img"}]
                """
            );
        Map<String, Object> item = groupedZohoItem("zoho-img", "IMG-VAR", "S", "Coral", 1, 1);
        when(itemImageSync.isEnabled()).thenReturn(false);

        Boolean ok = ReflectionTestUtils.invokeMethod(
            service,
            "mergePulledZohoVariantItem",
            parent,
            item,
            "IMG-VAR",
            false,
            ZohoSyncSummary.builder()
        );

        assertThat(ok).isTrue();
        verify(itemImageSync, never()).importZohoVariantColorImage(any(), any(), any(), any());
    }

    private static Map<String, Object> groupedZohoItem(
        String itemId,
        String sku,
        String size,
        String color,
        double rate,
        int stock
    ) {
        LinkedHashMap<String, Object> item = new LinkedHashMap<>();
        item.put("item_id", itemId);
        item.put("sku", sku);
        item.put("name", "Group — " + size + " / " + color);
        item.put("group_id", "grp-" + sku);
        item.put("group_name", "Group");
        item.put("attribute_name1", "Size");
        item.put("attribute_option_name1", size);
        item.put("attribute_name2", "Color");
        item.put("attribute_option_name2", color);
        item.put("rate", rate);
        item.put("label_rate", rate);
        item.put("stock_on_hand", stock);
        item.put("status", "active");
        return item;
    }

    private static RestClientResponseException invalidEmailError() {
        return new RestClientResponseException(
            "bad email",
            400,
            "Bad Request",
            null,
            "{\"message\":\"Invalid value passed for email\"}".getBytes(),
            null
        );
    }

    private static RestClientResponseException duplicateContactError() {
        return new RestClientResponseException(
            "duplicate",
            400,
            "Bad Request",
            null,
            "{\"code\":3062,\"message\":\"already exists\"}".getBytes(),
            null
        );
    }
}
