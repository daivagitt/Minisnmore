package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.miniandmore.app.client.ZohoBooksClient;
import com.miniandmore.app.config.ApplicationProperties;
import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.OrderItem;
import com.miniandmore.app.domain.Product;
import com.miniandmore.app.domain.ZohoSetting;
import com.miniandmore.app.domain.enumeration.ZohoSyncStatus;
import com.miniandmore.app.repository.CustomerOrderRepository;
import com.miniandmore.app.repository.CustomerProfileRepository;
import com.miniandmore.app.repository.OrderItemRepository;
import com.miniandmore.app.repository.ProductRepository;
import com.miniandmore.app.repository.ZohoSettingRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

@ExtendWith(MockitoExtension.class)
class ZohoSyncServiceTest {

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
    private ProductVariantSupport variantSupport;

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
    private ZohoBooksAccountResolver accountResolver;
    private ZohoCatalogStockAdjuster stockAdjuster;
    private ZohoStockReconcileService stockReconcileService;

    @BeforeEach
    void setUpZohoProperties() {
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
        lenient().when(customerOrderRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void syncProductAsync_whenAutoSyncDisabled_skipsPush() {
        service.syncProductAsync(99L);

        verify(productRepository, never()).findOneWithEagerRelationships(any());
        verify(booksClient, never()).createItem(any());
        verify(booksClient, never()).updateItem(any(), any());
    }

    @Test
    void syncAllProducts_rateLimitStopsBeforeRemainingProducts() {
        Product first = new Product().id(501L).sku("FIRST-SYNC").name("First Sync").zohoItemId("zoho-first").active(true);
        Product later = new Product().id(502L).sku("LATER-SYNC").name("Later Sync").zohoItemId("zoho-later").active(true);
        when(productRepository.findAllWithEagerRelationships(any())).thenReturn(new PageImpl<>(List.of(first, later)));
        when(booksClient.markItemActive("zoho-first"))
            .thenThrow(new RestClientResponseException("Rate limited", 429, "Too Many Requests", null, null, null));

        Map<String, Object> result = service.syncAllProducts();

        assertThat(result.get("success")).isEqualTo(0);
        assertThat(result.get("failed")).isEqualTo(1);
        verify(productRepository).updateZohoSyncFailure(
            eq(501L),
            eq(ZohoSyncStatus.FAILED),
            argThat(message -> message.contains("Rate limited"))
        );
        verify(booksClient, never()).markItemActive("zoho-later");
        verify(productRepository, never()).updateZohoSyncFailure(eq(502L), any(), any());
    }

    @Test
    void syncAllProducts_productValidationFailureDoesNotBlockLaterProducts() {
        Product first = new Product().id(503L).sku("BAD-SYNC").name("Bad Sync").zohoItemId("zoho-bad").active(true);
        Product later = new Product().id(504L).sku("GOOD-SYNC").name("Good Sync").zohoItemId("zoho-good").active(true);
        when(productRepository.findAllWithEagerRelationships(any())).thenReturn(new PageImpl<>(List.of(first, later)));
        when(booksClient.markItemActive("zoho-bad"))
            .thenThrow(new RestClientResponseException("Invalid item", 400, "Bad Request", null, null, null));
        when(booksClient.markItemActive("zoho-good")).thenReturn(Map.of());
        when(booksClient.updateItem(eq("zoho-good"), any()))
            .thenReturn(Map.of("item", Map.of("item_id", "zoho-good")));

        Map<String, Object> result = service.syncAllProducts();

        assertThat(result.get("success")).isEqualTo(1);
        assertThat(result.get("failed")).isEqualTo(1);
        verify(booksClient).updateItem(eq("zoho-good"), any());
        verify(productRepository).updateZohoSyncSuccess(eq(504L), eq("zoho-good"), eq(ZohoSyncStatus.SYNCED), any());
    }

    @Test
    void syncProduct_update_existingItem_usesSalesItemWithoutStockFields() {
        Product product = new Product()
            .id(1L)
            .sku("SKU-1")
            .name("Body Suit")
            .zohoItemId("zoho-1")
            .stock(10)
            .price(BigDecimal.valueOf(499))
            .units(" box ")
            .active(true);
        when(productRepository.findOneWithEagerRelationships(1L)).thenReturn(Optional.of(product));
        when(booksClient.updateItem(eq("zoho-1"), any())).thenReturn(Map.of("item", Map.of("item_id", "zoho-1")));

        service.syncProduct(1L);

        verify(booksClient, never()).createItem(any());
        verify(booksClient).updateItem(
            eq("zoho-1"),
            argThat(payload ->
                "sales".equals(payload.get("item_type")) &&
                !payload.containsKey("inventory_account_id") &&
                !payload.containsKey("locations") &&
                "Body Suit".equals(payload.get("name")) &&
                "box".equals(payload.get("unit")) &&
                BigDecimal.valueOf(499).equals(payload.get("rate"))
            )
        );
        verify(productRepository).updateZohoSyncSuccess(eq(1L), eq("zoho-1"), eq(ZohoSyncStatus.SYNCED), any());
    }

    @Test
    void syncProduct_imageFailureMarksProductFailedInsteadOfReportingSuccess() {
        Product product = new Product()
            .id(2L)
            .sku("SKU-2")
            .name("Image failure")
            .zohoItemId("zoho-2")
            .price(BigDecimal.valueOf(499))
            .active(true);
        when(productRepository.findOneWithEagerRelationships(2L)).thenReturn(Optional.of(product));
        when(booksClient.updateItem(eq("zoho-2"), any())).thenReturn(Map.of("item", Map.of("item_id", "zoho-2")));
        doThrow(new IllegalStateException("Zoho rejected image 4"))
            .when(itemImageSync)
            .syncProductImage("zoho-2", product);

        service.syncProduct(2L);

        verify(productRepository).updateZohoSyncFailure(2L, ZohoSyncStatus.FAILED, "Zoho rejected image 4");
        verify(productRepository, never()).updateZohoSyncSuccess(eq(2L), any(), any(), any());
    }

    @Test
    void syncProduct_imageFailureStoresRootCauseDetail() {
        Product product = new Product()
            .id(24L)
            .sku("SKU-24")
            .name("Nested image failure")
            .zohoItemId("zoho-24")
            .price(BigDecimal.valueOf(499))
            .active(true);
        when(productRepository.findOneWithEagerRelationships(24L)).thenReturn(Optional.of(product));
        when(booksClient.updateItem(eq("zoho-24"), any())).thenReturn(Map.of("item", Map.of("item_id", "zoho-24")));
        doThrow(
            new IllegalStateException(
                "Zoho item images delete failed",
                new java.io.IOException("Zoho item images delete failed: HTTP 400 {\"message\":\"The document id does not exist.\"}")
            )
        )
            .when(itemImageSync)
            .syncProductImage("zoho-24", product);

        service.syncProduct(24L);

        verify(productRepository)
            .updateZohoSyncFailure(
                24L,
                ZohoSyncStatus.FAILED,
                "Zoho item images delete failed: HTTP 400 {\"message\":\"The document id does not exist.\"}"
            );
    }

    @Test
    void syncProduct_imageFailureRedactsSignedUrlAndFitsDatabaseColumn() {
        Product product = new Product()
            .id(22L)
            .sku("SKU-22")
            .name("Long image failure")
            .zohoItemId("zoho-22")
            .price(BigDecimal.valueOf(499))
            .active(true);
        when(productRepository.findOneWithEagerRelationships(22L)).thenReturn(Optional.of(product));
        when(booksClient.updateItem(eq("zoho-22"), any())).thenReturn(Map.of("item", Map.of("item_id", "zoho-22")));
        doThrow(
            new IllegalStateException(
                "Download failed https://cdn.example.com/photo.jpg?token=SECRET&signature=PRIVATE " + "x".repeat(1200)
            )
        )
            .when(itemImageSync)
            .syncProductImage("zoho-22", product);

        service.syncProduct(22L);

        verify(productRepository)
            .updateZohoSyncFailure(
                eq(22L),
                eq(ZohoSyncStatus.FAILED),
                argThat(message ->
                    message.length() <= 1000 &&
                    message.contains("photo.jpg?<redacted>") &&
                    !message.contains("SECRET") &&
                    !message.contains("PRIVATE")
                )
            );
    }

    @Test
    void syncProduct_variantImageFailureMarksProductFailedInsteadOfReportingSuccess() {
        ProductVariantSupport realVariantSupport = StorePricingTestSupport.variantSupport(objectMapper);
        service =
            new ZohoSyncService(
                booksClient,
                objectMapper,
                applicationProperties,
                itemImageSync,
                productFieldMapper,
                descriptionCodec,
                realVariantSupport,
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

        Product product = new Product()
            .id(23L)
            .sku("HORIZON")
            .name("Horizon Set")
            .itemStructure("VARIANTS")
            .variantsJson(
                """
                [{"sku":"HORIZON-S-NAVY","size":"S","color":"Navy","rate":799.20,"stock":3,"active":true,"zohoItemId":"zoho-s"}]
                """
            )
            .active(true);
        when(productRepository.findOneWithEagerRelationships(23L)).thenReturn(Optional.of(product));
        when(booksClient.updateItem(eq("zoho-s"), any())).thenReturn(Map.of("item", Map.of("item_id", "zoho-s")));
        doThrow(new IllegalStateException("Zoho rejected variant gallery"))
            .when(itemImageSync)
            .syncVariantImage(eq("zoho-s"), eq(product), eq("HORIZON-S-NAVY"));

        service.syncProduct(23L);

        verify(productRepository).updateZohoSyncFailure(23L, ZohoSyncStatus.FAILED, "Zoho rejected variant gallery");
        verify(productRepository, never()).updateZohoSyncSuccess(eq(23L), any(), any(), any());
    }

    @Test
    void syncProduct_update_staleZohoItemId_recreatesInsteadOfFailing() {
        Product product = new Product()
            .id(11L)
            .sku("SKU-11")
            .name("Basic Shorts")
            .zohoItemId("zoho-deleted")
            .stock(10)
            .price(BigDecimal.valueOf(499))
            .active(true);
        when(productRepository.findOneWithEagerRelationships(11L)).thenReturn(Optional.of(product));
        when(booksClient.updateItem(eq("zoho-deleted"), any()))
            .thenThrow(
                new RestClientResponseException(
                    "This item cannot be edited as it does not exist.",
                    400,
                    "Bad Request",
                    null,
                    "{\"code\":2006,\"message\":\"This item cannot be edited as it does not exist.\"}".getBytes(),
                    null
                )
            );
        when(booksClient.createItem(any())).thenReturn(Map.of("item", Map.of("item_id", "zoho-11-new")));

        service.syncProduct(11L);

        verify(booksClient).createItem(any());
        verify(productRepository).updateZohoSyncSuccess(eq(11L), eq("zoho-11-new"), eq(ZohoSyncStatus.SYNCED), any());
        verify(productRepository, never()).updateZohoSyncFailure(eq(11L), any(), any());
    }

    @Test
    void syncProduct_update_groupedZohoItem_preservesExistingAttributesWhenZohoRequiresThem() {
        Product product = new Product()
            .id(13L)
            .sku("BSJ-0-3-AQUA")
            .name("Book Style Tying Jabla")
            .zohoItemId("zoho-jabla-aqua")
            .stock(1)
            .price(BigDecimal.valueOf(499))
            .active(true);
        when(productRepository.findOneWithEagerRelationships(13L)).thenReturn(Optional.of(product));
        when(booksClient.updateItem(eq("zoho-jabla-aqua"), any()))
            .thenThrow(
                new RestClientResponseException(
                    "Missing destination group attributes",
                    400,
                    "Bad Request",
                    null,
                    "{\"code\":700019,\"message\":\"Looks like the item is missing some attributes with respect to the destination group.\"}".getBytes(),
                    null
                )
            )
            .thenReturn(Map.of("item", Map.of("item_id", "zoho-jabla-aqua")));
        when(booksClient.getItem("zoho-jabla-aqua"))
            .thenReturn(
                Map.of(
                    "item",
                    Map.of(
                        "item_id",
                        "zoho-jabla-aqua",
                        "group_id",
                        "grp-jabla",
                        "attribute_name1",
                        "Size",
                        "attribute_option_name1",
                        "0-3 M",
                        "attribute_name2",
                        "Color",
                        "attribute_option_name2",
                        "Aqua"
                    )
                )
            );

        service.syncProduct(13L);

        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(booksClient, times(2)).updateItem(eq("zoho-jabla-aqua"), payloadCaptor.capture());
        assertThat(payloadCaptor.getAllValues().get(0)).doesNotContainKey("attribute_name1");
        assertThat(payloadCaptor.getAllValues().get(1))
            .containsEntry("group_id", "grp-jabla")
            .containsEntry("attribute_name1", "Size")
            .containsEntry("attribute_option_name1", "0-3 M")
            .containsEntry("attribute_name2", "Color")
            .containsEntry("attribute_option_name2", "Aqua");
        verify(productRepository).updateZohoSyncSuccess(eq(13L), eq("zoho-jabla-aqua"), eq(ZohoSyncStatus.SYNCED), any());
        verify(productRepository, never()).updateZohoSyncFailure(eq(13L), any(), any());
    }

    @Test
    void syncProduct_create_duplicateItemName_updatesExistingInsteadOfFailing() {
        Product product = new Product()
            .id(12L)
            .sku("SKU-12")
            .name("Baby Swaddle")
            .stock(3)
            .price(BigDecimal.valueOf(499))
            .active(true);
        when(productRepository.findOneWithEagerRelationships(12L)).thenReturn(Optional.of(product));
        when(booksClient.createItem(any()))
            .thenThrow(
                new RestClientResponseException(
                    "Item already exists.",
                    400,
                    "Bad Request",
                    null,
                    "{\"code\":1001,\"message\":\"Item \\\"Baby Swaddle\\\" already exists.\"}".getBytes(),
                    null
                )
            );
        when(booksClient.findItemBySku("SKU-12"))
            .thenReturn(null)
            .thenReturn(Map.of("item_id", "zoho-existing-12"));
        when(booksClient.updateItem(eq("zoho-existing-12"), any())).thenReturn(Map.of("item", Map.of("item_id", "zoho-existing-12")));

        service.syncProduct(12L);

        verify(booksClient).createItem(any());
        verify(booksClient).updateItem(eq("zoho-existing-12"), any());
        verify(productRepository).updateZohoSyncSuccess(eq(12L), eq("zoho-existing-12"), eq(ZohoSyncStatus.SYNCED), any());
        verify(productRepository, never()).updateZohoSyncFailure(eq(12L), any(), any());
    }

    @Test
    void syncProduct_create_withoutInventoryAccount_usesSalesItem() {
        when(booksClient.listAssetAccounts()).thenReturn(List.of());

        Product product = new Product()
            .id(2L)
            .sku("SKU-2")
            .name("Baby Swaddle")
            .stock(5)
            .price(BigDecimal.valueOf(299))
            .active(true);
        when(productRepository.findOneWithEagerRelationships(2L)).thenReturn(Optional.of(product));
        when(booksClient.findItemBySku("SKU-2")).thenReturn(null);
        when(booksClient.createItem(any())).thenReturn(Map.of("item", Map.of("item_id", "zoho-2")));

        service.syncProduct(2L);

        verify(booksClient).createItem(
            argThat(payload ->
                "sales".equals(payload.get("item_type")) &&
                !payload.containsKey("locations") &&
                !payload.containsKey("inventory_account_id")
            )
        );
    }

    @Test
    void syncProduct_create_withInventoryAccount_putsOpeningStockInLocationsArray() {
        ApplicationProperties.Zoho zoho = new ApplicationProperties.Zoho();
        zoho.setSyncProductStock(true);
        zoho.setInventoryAccountId("inv-acct-1");
        zoho.setDefaultLocationId("loc-1");
        when(applicationProperties.getZoho()).thenReturn(zoho);

        Product product = new Product()
            .id(3L)
            .sku("SKU-3")
            .name("Round Cap")
            .stock(2)
            .price(BigDecimal.valueOf(199))
            .active(true);
        when(productRepository.findOneWithEagerRelationships(3L)).thenReturn(Optional.of(product));
        when(booksClient.findItemBySku("SKU-3")).thenReturn(null);
        when(booksClient.createItem(any())).thenReturn(Map.of("item", Map.of("item_id", "zoho-3")));

        service.syncProduct(3L);

        verify(booksClient).createItem(
            argThat(payload -> {
                if (!"inventory".equals(payload.get("item_type")) || !"inv-acct-1".equals(payload.get("inventory_account_id"))) {
                    return false;
                }
                Object locationsObj = payload.get("locations");
                if (!(locationsObj instanceof List<?> locations) || locations.size() != 1) {
                    return false;
                }
                Object entryObj = locations.get(0);
                if (!(entryObj instanceof Map<?, ?> location)) {
                    return false;
                }
                return (
                    "loc-1".equals(location.get("location_id")) &&
                    "2".equals(location.get("initial_stock")) &&
                    "199".equals(location.get("initial_stock_rate"))
                );
            })
        );
    }

    @Test
    void syncProduct_create_inventoryDisabledInZoho_retriesAsSalesItem() {
        ApplicationProperties.Zoho zoho = new ApplicationProperties.Zoho();
        zoho.setSyncProductStock(true);
        zoho.setInventoryAccountId("inv-acct-1");
        zoho.setDefaultLocationId("loc-1");
        when(applicationProperties.getZoho()).thenReturn(zoho);

        Product product = new Product()
            .id(4L)
            .sku("SKU-4")
            .name("Socks")
            .stock(1)
            .price(BigDecimal.TEN)
            .active(true);
        when(productRepository.findOneWithEagerRelationships(4L)).thenReturn(Optional.of(product));
        when(booksClient.findItemBySku("SKU-4")).thenReturn(null);
        when(booksClient.createItem(any()))
            .thenThrow(
                new RestClientResponseException(
                    "Inventory tracking is disabled!",
                    400,
                    "Bad Request",
                    null,
                    "{\"code\":2024,\"message\":\"Inventory tracking is disabled!\"}".getBytes(),
                    null
                )
            )
            .thenReturn(Map.of("item", Map.of("item_id", "zoho-4")));

        service.syncProduct(4L);

        verify(booksClient, times(2)).createItem(any());
    }

    @Test
    void syncProduct_create_invalidInventoryAccount_retriesWithOrgAssetAccount() {
        ApplicationProperties.Zoho zoho = new ApplicationProperties.Zoho();
        zoho.setSyncProductStock(true);
        zoho.setInventoryAccountId("stale-other-org");
        zoho.setDefaultLocationId("loc-1");
        when(applicationProperties.getZoho()).thenReturn(zoho);
        when(booksClient.listAssetAccounts())
            .thenReturn(List.of())
            .thenReturn(List.of(Map.of("account_id", "acct-inv", "account_name", "Inventory Asset")));

        Product product = new Product()
            .id(41L)
            .sku("SKU-41")
            .name("Romper")
            .stock(1)
            .price(BigDecimal.TEN)
            .active(true);
        when(productRepository.findOneWithEagerRelationships(41L)).thenReturn(Optional.of(product));
        when(booksClient.findItemBySku("SKU-41")).thenReturn(null);
        when(booksClient.createItem(any()))
            .thenThrow(
                new RestClientResponseException(
                    "The account given for inventory tracking does not exist.",
                    400,
                    "Bad Request",
                    null,
                    "{\"code\":2035,\"message\":\"The account given for inventory tracking does not exist.\"}".getBytes(),
                    null
                )
            )
            .thenReturn(Map.of("item", Map.of("item_id", "zoho-41")));

        service.syncProduct(41L);

        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(booksClient, times(2)).createItem(payloadCaptor.capture());
        assertThat(payloadCaptor.getAllValues().get(0).get("inventory_account_id")).isEqualTo("stale-other-org");
        assertThat(payloadCaptor.getAllValues().get(1).get("inventory_account_id")).isEqualTo("acct-inv");
        verify(productRepository).updateZohoSyncSuccess(eq(41L), eq("zoho-41"), eq(ZohoSyncStatus.SYNCED), any());
    }

    @Test
    void syncProduct_create_autoResolvesInventoryAccountFromChartOfAccounts() {
        when(booksClient.listAssetAccounts()).thenReturn(
            List.of(Map.of("account_id", "acct-inv", "account_name", "Inventory Asset"))
        );
        ApplicationProperties.Zoho zoho = new ApplicationProperties.Zoho();
        zoho.setSyncProductStock(true);
        zoho.setDefaultLocationId("loc-1");
        when(applicationProperties.getZoho()).thenReturn(zoho);

        Product product = new Product()
            .id(5L)
            .sku("SKU-5")
            .name("Bibs")
            .stock(3)
            .price(BigDecimal.valueOf(150))
            .active(true);
        when(productRepository.findOneWithEagerRelationships(5L)).thenReturn(Optional.of(product));
        when(booksClient.findItemBySku("SKU-5")).thenReturn(null);
        when(booksClient.createItem(any())).thenReturn(Map.of("item", Map.of("item_id", "zoho-5")));

        service.syncProduct(5L);

        verify(booksClient).createItem(
            argThat(payload -> "inventory".equals(payload.get("item_type")) && "acct-inv".equals(payload.get("inventory_account_id")))
        );
    }

    @Test
    void syncProduct_create_enrichesPayloadWithCustomProductFields() {
        Product product = new Product()
            .id(6L)
            .sku("SKU-6")
            .name("Dress")
            .compareAtPrice(BigDecimal.valueOf(500))
            .active(true);
        when(productRepository.findOneWithEagerRelationships(6L)).thenReturn(Optional.of(product));
        when(booksClient.findItemBySku("SKU-6")).thenReturn(null);
        when(booksClient.createItem(any())).thenReturn(Map.of("item", Map.of("item_id", "zoho-6")));

        service.syncProduct(6L);

        verify(productFieldMapper).enrichPushPayload(eq(product), any());
    }

    @Test
    void syncOrder_includesCheckoutShippingAddressOnSalesOrder() {
        Product product = new Product().id(10L).sku("SKU-10").zohoItemId("zoho-item-10");
        OrderItem line = new OrderItem()
            .productId("10")
            .productName("Dress")
            .qty(1)
            .price(BigDecimal.valueOf(499));
        CustomerOrder order = new CustomerOrder()
            .id(120L)
            .orderNumber("MNM-ADDR-1")
            .userLogin("buyer@example.com")
            .shippingAddressJson(
                "{\"full_name\":\"Jane Doe\",\"email\":\"buyer@example.com\",\"phone\":\"9876543210\",\"line1\":\"42 New Street\",\"line2\":\"Apt 5\",\"city\":\"Hyderabad\",\"state\":\"Telangana\",\"pincode\":\"500001\"}"
            );
        order.addItems(line);
        line.setOrder(order);

        when(customerOrderRepository.findById(120L)).thenReturn(Optional.of(order));
        when(orderItemRepository.findAllByOrderId(120L)).thenReturn(List.of(line));
        when(booksClient.findContactByEmail("buyer@example.com")).thenReturn(Map.of("contact_id", "cust-1"));
        when(booksClient.createSalesOrder(any())).thenReturn(Map.of("salesorder", Map.of("salesorder_id", "so-addr")));
        when(booksClient.createInvoiceFromSalesOrder("so-addr")).thenReturn(Map.of("invoice", Map.of("invoice_id", "inv-addr")));
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));

        service.syncOrder(120L);

        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(booksClient).createSalesOrder(payloadCaptor.capture());
        Map<String, Object> payload = payloadCaptor.getValue();
        assertThat(payload).doesNotContainKey("shipping_address");
        assertThat(payload).doesNotContainKey("billing_address");
        assertThat(payload)
            .containsEntry("place_of_supply", "TG")
            .containsEntry("gst_treatment", "consumer")
            .containsEntry("is_inclusive_tax", true);

        ArgumentCaptor<Map<String, Object>> billingAddressCaptor = ArgumentCaptor.forClass(Map.class);
        verify(booksClient).updateSalesOrderBillingAddress(eq("so-addr"), billingAddressCaptor.capture());
        ArgumentCaptor<Map<String, Object>> shippingAddressCaptor = ArgumentCaptor.forClass(Map.class);
        verify(booksClient).updateSalesOrderShippingAddress(eq("so-addr"), shippingAddressCaptor.capture());
        Map<String, Object> shippingAddress = shippingAddressCaptor.getValue();
        assertThat(shippingAddress).isEqualTo(billingAddressCaptor.getValue());
        assertThat(shippingAddress)
            .containsEntry("attention", "Jane Doe")
            .containsEntry("address", "42 New Street")
            .containsEntry("street2", "Apt 5")
            .containsEntry("city", "Hyderabad")
            .containsEntry("state", "Telangana")
            .containsEntry("zip", "500001")
            .containsEntry("country", "India")
            .containsEntry("phone", "9876543210");
    }

    @Test
    void syncOrder_reusesExistingSalesOrderByReferenceInsteadOfCreatingASecond() {
        Product product = new Product().id(10L).sku("SKU-10").zohoItemId("zoho-item-10");
        OrderItem line = new OrderItem()
            .productId("10")
            .productName("Dress")
            .qty(1)
            .price(BigDecimal.valueOf(499));
        CustomerOrder order = new CustomerOrder()
            .id(125L)
            .orderNumber("MNM-DUP-SO")
            .userLogin("buyer@example.com")
            .paymentStatus(com.miniandmore.app.domain.enumeration.PaymentStatus.PAID)
            .status(com.miniandmore.app.domain.enumeration.OrderStatus.CONFIRMED);
        order.addItems(line);
        line.setOrder(order);

        when(customerOrderRepository.findById(125L)).thenReturn(Optional.of(order));
        when(booksClient.findContactByEmail("buyer@example.com")).thenReturn(Map.of("contact_id", "cust-1"));
        when(booksClient.findSalesOrderByReferenceNumber("MNM-DUP-SO"))
            .thenReturn(Map.of("salesorder_id", "so-closed", "reference_number", "MNM-DUP-SO", "status", "closed"));
        when(booksClient.createInvoiceFromSalesOrder("so-closed"))
            .thenReturn(Map.of("invoice", Map.of("invoice_id", "inv-closed")));

        service.syncOrder(125L);

        assertThat(order.getZohoSalesOrderId()).isEqualTo("so-closed");
        assertThat(order.getZohoInvoiceId()).isEqualTo("inv-closed");
        verify(booksClient, never()).createSalesOrder(any());
        verify(booksClient).createInvoiceFromSalesOrder("so-closed");
    }

    @Test
    void syncOrder_linksInventoryItemsAndMarksInvoiceSent() {
        ApplicationProperties.Zoho zoho = new ApplicationProperties.Zoho();
        zoho.setSyncProductStock(true);
        zoho.setInventoryAccountId("inv-acct-1");
        zoho.setDefaultLocationId("loc-1");
        when(applicationProperties.getZoho()).thenReturn(zoho);

        Product product = new Product().id(10L).sku("SKU-10").zohoItemId("zoho-item-10");
        OrderItem line = new OrderItem()
            .productId("10")
            .productName("Dress")
            .qty(2)
            .price(BigDecimal.valueOf(499));
        CustomerOrder order = new CustomerOrder().id(100L).orderNumber("MNM-TEST-1").userLogin("buyer@example.com");
        order.addItems(line);
        line.setOrder(order);

        when(customerOrderRepository.findById(100L)).thenReturn(Optional.of(order));
        when(orderItemRepository.findAllByOrderId(100L)).thenReturn(List.of(line));
        when(booksClient.findContactByEmail("buyer@example.com")).thenReturn(Map.of("contact_id", "cust-1"));
        when(booksClient.createSalesOrder(any())).thenReturn(Map.of("salesorder", Map.of("salesorder_id", "so-1")));
        when(booksClient.createInvoiceFromSalesOrder("so-1")).thenReturn(Map.of("invoice", Map.of("invoice_id", "inv-1")));
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));

        service.syncOrder(100L);

        verify(booksClient).createSalesOrder(
            argThat(payload -> {
                Object linesObj = payload.get("line_items");
                if (!(linesObj instanceof List<?> lines) || lines.isEmpty()) {
                    return false;
                }
                Object lineObj = lines.get(0);
                if (!(lineObj instanceof Map<?, ?> salesLine)) {
                    return false;
                }
                return (
                    "zoho-item-10".equals(salesLine.get("item_id")) &&
                    !salesLine.containsKey("location_id") &&
                    Integer.valueOf(2).equals(salesLine.get("quantity"))
                );
            })
        );
        verify(booksClient).markInvoiceAsSent("inv-1");
    }

    @Test
    void syncOrder_createSalesOrder_includesOnlineChannelCustomField() {
        Product product = new Product().id(10L).sku("SKU-10").zohoItemId("zoho-item-10");
        OrderItem line = new OrderItem()
            .productId("10")
            .productName("Dress")
            .qty(1)
            .price(BigDecimal.valueOf(499));
        CustomerOrder order = new CustomerOrder().id(126L).orderNumber("MNM-CHANNEL-CF").userLogin("buyer@example.com");
        order.addItems(line);
        line.setOrder(order);

        when(customerOrderRepository.findById(126L)).thenReturn(Optional.of(order));
        when(orderItemRepository.findAllByOrderId(126L)).thenReturn(List.of(line));
        when(booksClient.findContactByEmail("buyer@example.com")).thenReturn(Map.of("contact_id", "cust-1"));
        when(booksClient.createSalesOrder(any())).thenReturn(Map.of("salesorder", Map.of("salesorder_id", "so-cf")));
        when(booksClient.createInvoiceFromSalesOrder("so-cf")).thenReturn(Map.of("invoice", Map.of("invoice_id", "inv-cf")));
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));

        service.syncOrder(126L);

        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(booksClient).createSalesOrder(payloadCaptor.capture());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> customFields = (List<Map<String, Object>>) payloadCaptor.getValue().get("custom_fields");
        assertThat(customFields).containsExactly(
            Map.of("customfield_id", "4066570000000458006", "value", "Online")
        );
    }

    @Test
    void syncOrder_codPending_doesNotCreatePostSyncInventoryAdjustment() {
        Product product = new Product()
            .id(10L)
            .sku("SKU-10")
            .zohoItemId("zoho-item-10")
            .stock(4)
            .price(BigDecimal.valueOf(499))
            .active(true);
        OrderItem line = new OrderItem()
            .productId("10")
            .productName("Dress")
            .qty(1)
            .price(BigDecimal.valueOf(499));
        CustomerOrder order = new CustomerOrder()
            .id(101L)
            .orderNumber("MNM-COD-STOCK")
            .userLogin("buyer@example.com")
            .paymentMethod("cod")
            .paymentStatus(com.miniandmore.app.domain.enumeration.PaymentStatus.PENDING)
            .status(com.miniandmore.app.domain.enumeration.OrderStatus.CONFIRMED);
        order.addItems(line);
        line.setOrder(order);

        when(customerOrderRepository.findById(101L)).thenReturn(Optional.of(order));
        when(orderItemRepository.findAllByOrderId(101L)).thenReturn(List.of(line));
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        when(booksClient.findContactByEmail("buyer@example.com")).thenReturn(Map.of("contact_id", "cust-1"));
        when(booksClient.createSalesOrder(any())).thenReturn(Map.of("salesorder", Map.of("salesorder_id", "so-cod-stock")));

        service.syncOrder(101L);

        verify(booksClient).createSalesOrder(any());
        verify(booksClient, never()).createInventoryAdjustment(any());
        verify(booksClient, never()).getItem("zoho-item-10");
    }

    @Test
    void syncOrder_missingZohoItem_createsItemBeforeSalesOrder() {
        Product product = new Product()
            .id(47450L)
            .sku("BOOK-STYLE-TYING-JABLA-SHORT-SET")
            .name("Book Style Tying Jabla Short Set")
            .stock(0)
            .price(BigDecimal.valueOf(1890))
            .active(true);
        OrderItem line = new OrderItem()
            .productId("47450")
            .productName("Book Style Tying Jabla Short Set")
            .itemSize("0-3m")
            .color("Aqua")
            .qty(1)
            .price(BigDecimal.valueOf(1890));
        CustomerOrder order = new CustomerOrder().id(49251L).orderNumber("MNM-NEW-SHIPPABLE").userLogin("buyer@example.com");
        order.addItems(line);
        line.setOrder(order);

        when(customerOrderRepository.findById(49251L)).thenReturn(Optional.of(order));
        when(orderItemRepository.findAllByOrderId(49251L)).thenReturn(List.of(line));
        when(productRepository.findById(47450L)).thenReturn(Optional.of(product));
        when(booksClient.findContactByEmail("buyer@example.com")).thenReturn(Map.of("contact_id", "cust-1"));
        when(booksClient.findItemBySku("BOOK-STYLE-TYING-JABLA-SHORT-SET")).thenReturn(null);
        when(booksClient.createItem(any())).thenReturn(Map.of("item", Map.of("item_id", "zoho-jabla")));
        when(booksClient.createSalesOrder(any())).thenReturn(Map.of("salesorder", Map.of("salesorder_id", "so-jabla")));
        when(booksClient.createInvoiceFromSalesOrder("so-jabla"))
            .thenReturn(Map.of("invoice", Map.of("invoice_id", "inv-jabla")));

        service.syncOrder(49251L);

        verify(booksClient).createItem(argThat(payload -> "BOOK-STYLE-TYING-JABLA-SHORT-SET".equals(payload.get("sku"))));
        verify(booksClient).createSalesOrder(
            argThat(payload -> {
                Object rawLines = payload.get("line_items");
                return rawLines instanceof List<?> lines &&
                !lines.isEmpty() &&
                lines.get(0) instanceof Map<?, ?> salesLine &&
                "zoho-jabla".equals(salesLine.get("item_id"));
            })
        );
    }

    @Test
    void syncOrder_existingFreeTextSalesOrder_repairsLineWithZohoItem() {
        Product product = new Product()
            .id(47450L)
            .sku("BOOK-STYLE-TYING-JABLA-SHORT-SET")
            .name("Book Style Tying Jabla Short Set")
            .stock(0)
            .price(BigDecimal.valueOf(1890))
            .active(true);
        OrderItem line = new OrderItem()
            .productId("47450")
            .productName("Book Style Tying Jabla Short Set")
            .itemSize("0-3m")
            .color("Aqua")
            .qty(1)
            .price(BigDecimal.valueOf(1890));
        CustomerOrder order = new CustomerOrder()
            .id(49251L)
            .orderNumber("MNM-6F2155AEA0BE")
            .userLogin("buyer@example.com")
            .paymentMethod("cod")
            .paymentStatus(com.miniandmore.app.domain.enumeration.PaymentStatus.PENDING)
            .status(com.miniandmore.app.domain.enumeration.OrderStatus.CONFIRMED)
            .zohoSalesOrderId("4066570000000341012");
        order.addItems(line);
        line.setOrder(order);

        when(customerOrderRepository.findById(49251L)).thenReturn(Optional.of(order));
        when(orderItemRepository.findAllByOrderId(49251L)).thenReturn(List.of(line));
        when(productRepository.findById(47450L)).thenReturn(Optional.of(product));
        when(booksClient.findContactByEmail("buyer@example.com")).thenReturn(Map.of("contact_id", "cust-1"));
        when(booksClient.getSalesOrder("4066570000000341012"))
            .thenReturn(Map.of("salesorder", Map.of("line_items", List.of(Map.of("name", "Book Style Tying Jabla Short Set")))));
        when(booksClient.findItemBySku("BOOK-STYLE-TYING-JABLA-SHORT-SET")).thenReturn(null);
        when(booksClient.createItem(any())).thenReturn(Map.of("item", Map.of("item_id", "zoho-jabla")));
        when(booksClient.updateSalesOrder(eq("4066570000000341012"), any())).thenReturn(Map.of());

        service.syncOrder(49251L);

        verify(booksClient).updateSalesOrder(
            eq("4066570000000341012"),
            argThat(payload -> {
                Object rawLines = payload.get("line_items");
                return rawLines instanceof List<?> lines &&
                !lines.isEmpty() &&
                lines.get(0) instanceof Map<?, ?> salesLine &&
                "zoho-jabla".equals(salesLine.get("item_id")) &&
                !payload.containsKey("custom_fields");
            })
        );
        verify(booksClient, never()).createInvoiceFromSalesOrder(any());
    }

    @Test
    void syncOrder_closedSalesOrder_doesNotAttemptLegacyLineRepair() {
        CustomerOrder order = new CustomerOrder()
            .id(49252L)
            .orderNumber("MNM-CLOSED")
            .userLogin("buyer@example.com")
            .paymentMethod("cod")
            .paymentStatus(com.miniandmore.app.domain.enumeration.PaymentStatus.PENDING)
            .status(com.miniandmore.app.domain.enumeration.OrderStatus.CONFIRMED)
            .zohoSalesOrderId("so-closed");
        OrderItem line = new OrderItem().productId("47450").productName("Legacy item").qty(1).price(BigDecimal.TEN);
        order.addItems(line);

        when(customerOrderRepository.findById(49252L)).thenReturn(Optional.of(order));
        when(booksClient.findContactByEmail("buyer@example.com")).thenReturn(Map.of("contact_id", "cust-1"));
        when(booksClient.getSalesOrder("so-closed"))
            .thenReturn(
                Map.of("salesorder", Map.of("status", "closed", "line_items", List.of(Map.of("name", "Legacy item"))))
            );

        service.syncOrder(49252L);

        verify(booksClient, never()).updateSalesOrder(any(), any());
        verify(booksClient, never()).findItemBySku(any());
    }

    @Test
    void syncOrder_newInvoice_cachesPdfToStorageAndSavesUrlOnOrder() {
        ApplicationProperties.Storage storage = new ApplicationProperties.Storage();
        storage.getS3().setMaxUploadBytes(8_192_000L);
        when(applicationProperties.getStorage()).thenReturn(storage);

        Product product = new Product().id(11L).sku("SKU-11").zohoItemId("zoho-item-11");
        OrderItem line = new OrderItem().productId("11").productName("Romper").qty(1).price(BigDecimal.valueOf(699));
        CustomerOrder order = new CustomerOrder().id(101L).orderNumber("MNM-TEST-2").userLogin("buyer@example.com");
        order.addItems(line);
        line.setOrder(order);

        when(customerOrderRepository.findById(101L)).thenReturn(Optional.of(order));
        when(orderItemRepository.findAllByOrderId(101L)).thenReturn(List.of(line));
        when(booksClient.findContactByEmail("buyer@example.com")).thenReturn(Map.of("contact_id", "cust-2"));
        when(booksClient.createSalesOrder(any())).thenReturn(Map.of("salesorder", Map.of("salesorder_id", "so-2")));
        when(booksClient.createInvoiceFromSalesOrder("so-2")).thenReturn(Map.of("invoice", Map.of("invoice_id", "inv-2")));
        when(productRepository.findById(11L)).thenReturn(Optional.of(product));
        when(booksClient.downloadInvoicePdf(eq("inv-2"), any(Long.class)))
            .thenReturn(Optional.of(new ZohoBooksClient.BinaryDownload("pdf-bytes".getBytes(), "application/pdf")));
        when(s3StorageService.uploadInvoicePdf(eq("invoice-inv-2.pdf"), any()))
            .thenReturn("https://cdn.example.com/uat/invoices/invoice-inv-2.pdf");

        service.syncOrder(101L);

        assertThat(order.getZohoInvoicePdfUrl()).isEqualTo("https://cdn.example.com/uat/invoices/invoice-inv-2.pdf");
    }

    @Test
    void syncOrder_invoicePdfDownloadFails_syncStillSucceeds() {
        ApplicationProperties.Storage storage = new ApplicationProperties.Storage();
        storage.getS3().setMaxUploadBytes(8_192_000L);
        when(applicationProperties.getStorage()).thenReturn(storage);

        Product product = new Product().id(12L).sku("SKU-12b").zohoItemId("zoho-item-12b");
        OrderItem line = new OrderItem().productId("12").productName("Onesie").qty(1).price(BigDecimal.valueOf(399));
        CustomerOrder order = new CustomerOrder().id(102L).orderNumber("MNM-TEST-3").userLogin("buyer@example.com");
        order.addItems(line);
        line.setOrder(order);

        when(customerOrderRepository.findById(102L)).thenReturn(Optional.of(order));
        when(orderItemRepository.findAllByOrderId(102L)).thenReturn(List.of(line));
        when(booksClient.findContactByEmail("buyer@example.com")).thenReturn(Map.of("contact_id", "cust-3"));
        when(booksClient.createSalesOrder(any())).thenReturn(Map.of("salesorder", Map.of("salesorder_id", "so-3")));
        when(booksClient.createInvoiceFromSalesOrder("so-3")).thenReturn(Map.of("invoice", Map.of("invoice_id", "inv-3")));
        when(productRepository.findById(12L)).thenReturn(Optional.of(product));
        when(booksClient.downloadInvoicePdf(eq("inv-3"), any(Long.class))).thenThrow(new IllegalStateException("Zoho unreachable"));

        service.syncOrder(102L);

        assertThat(order.getZohoInvoicePdfUrl()).isNull();
        assertThat(order.getZohoInvoiceId()).isEqualTo("inv-3");
        assertThat(order.getZohoSyncStatus()).isEqualTo(com.miniandmore.app.domain.enumeration.ZohoSyncStatus.SYNCED);
    }

    @Test
    void ensureInvoicePdfCached_alreadyCached_returnsExistingUrlWithoutCallingZoho() {
        CustomerOrder order = new CustomerOrder()
            .id(103L)
            .zohoInvoiceId("inv-4")
            .zohoInvoicePdfUrl("https://cdn.example.com/uat/invoices/invoice-inv-4.pdf");
        when(customerOrderRepository.findById(103L)).thenReturn(Optional.of(order));

        String url = service.ensureInvoicePdfCached(103L);

        assertThat(url).isEqualTo("https://cdn.example.com/uat/invoices/invoice-inv-4.pdf");
        verify(booksClient, never()).downloadInvoicePdf(any(), any(Long.class));
    }

    @Test
    void ensureInvoicePdfCached_noZohoInvoiceYet_returnsNull() {
        CustomerOrder order = new CustomerOrder().id(104L);
        when(customerOrderRepository.findById(104L)).thenReturn(Optional.of(order));

        assertThat(service.ensureInvoicePdfCached(104L)).isNull();
        verify(booksClient, never()).downloadInvoicePdf(any(), any(Long.class));
    }

    @Test
    void ensureInvoicePdfCached_notYetCached_downloadsAndSavesOrder() {
        ApplicationProperties.Storage storage = new ApplicationProperties.Storage();
        storage.getS3().setMaxUploadBytes(8_192_000L);
        when(applicationProperties.getStorage()).thenReturn(storage);
        CustomerOrder order = new CustomerOrder().id(105L).zohoInvoiceId("inv-5");
        when(customerOrderRepository.findById(105L)).thenReturn(Optional.of(order));
        when(booksClient.downloadInvoicePdf(eq("inv-5"), any(Long.class)))
            .thenReturn(Optional.of(new ZohoBooksClient.BinaryDownload("pdf-bytes".getBytes(), "application/pdf")));
        when(s3StorageService.uploadInvoicePdf(eq("invoice-inv-5.pdf"), any()))
            .thenReturn("https://cdn.example.com/uat/invoices/invoice-inv-5.pdf");

        String url = service.ensureInvoicePdfCached(105L);

        assertThat(url).isEqualTo("https://cdn.example.com/uat/invoices/invoice-inv-5.pdf");
        verify(customerOrderRepository).save(order);
    }

    @Test
    void syncOrder_codOrder_includesPaymentTermsShippingAndCodFee() {
        Product product = new Product().id(10L).sku("SKU-10").zohoItemId("zoho-item-10");
        OrderItem line = new OrderItem()
            .productId("10")
            .productName("Dress")
            .qty(1)
            .price(BigDecimal.valueOf(1000));
        CustomerOrder order = new CustomerOrder()
            .id(110L)
            .orderNumber("MNM-COD-1")
            .userLogin("buyer@example.com")
            .paymentMethod("cod")
            .paymentStatus(com.miniandmore.app.domain.enumeration.PaymentStatus.PENDING)
            .status(com.miniandmore.app.domain.enumeration.OrderStatus.CONFIRMED)
            .shipping(BigDecimal.valueOf(99))
            .codFee(BigDecimal.valueOf(49))
            .discount(BigDecimal.ZERO);
        order.addItems(line);
        line.setOrder(order);

        when(customerOrderRepository.findById(110L)).thenReturn(Optional.of(order));
        when(orderItemRepository.findAllByOrderId(110L)).thenReturn(List.of(line));
        when(booksClient.findContactByEmail("buyer@example.com")).thenReturn(Map.of("contact_id", "cust-1"));
        when(booksClient.createSalesOrder(any())).thenReturn(Map.of("salesorder", Map.of("salesorder_id", "so-cod")));
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));

        service.syncOrder(110L);

        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(booksClient).createSalesOrder(payloadCaptor.capture());
        Map<String, Object> payload = payloadCaptor.getValue();
        assertThat(payload.get("payment_terms_label")).isEqualTo("Cash on Delivery");
        assertThat(String.valueOf(payload.get("notes"))).contains("COD");
        assertThat(new BigDecimal(String.valueOf(payload.get("shipping_charge")))).isEqualByComparingTo("99");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lines = (List<Map<String, Object>>) payload.get("line_items");
        assertThat(lines).anyMatch(salesLine -> "COD handling fee".equals(salesLine.get("name")));
        assertThat(order.getZohoInvoiceId()).isNull();
        verify(booksClient, never()).createInvoiceFromSalesOrder(any());
        verify(booksClient, never()).createCustomerPayment(any());
    }

    @Test
    void syncOrder_codCollectedWithoutInvoice_createsInvoiceThenRecordsCashPayment() {
        CustomerOrder order = new CustomerOrder()
            .id(112L)
            .orderNumber("MNM-COD-COLLECTED")
            .userLogin("buyer@example.com")
            .paymentMethod("cod")
            .paymentStatus(com.miniandmore.app.domain.enumeration.PaymentStatus.PAID)
            .status(com.miniandmore.app.domain.enumeration.OrderStatus.FULFILLED)
            .total(BigDecimal.valueOf(1148))
            .zohoSalesOrderId("so-cod-collected");

        when(customerOrderRepository.findById(112L)).thenReturn(Optional.of(order));
        when(booksClient.findContactByEmail("buyer@example.com")).thenReturn(Map.of("contact_id", "cust-1"));
        when(booksClient.createInvoiceFromSalesOrder("so-cod-collected"))
            .thenReturn(Map.of("invoice", Map.of("invoice_id", "inv-cod-collected")));
        when(booksClient.getInvoice("inv-cod-collected"))
            .thenReturn(Map.of("invoice", Map.of("invoice_id", "inv-cod-collected", "status", "sent", "balance", 1148)));
        when(booksClient.createCustomerPayment(any())).thenReturn(Map.of("payment", Map.of("payment_id", "pay-2")));

        service.syncOrder(112L);

        assertThat(order.getZohoInvoiceId()).isEqualTo("inv-cod-collected");
        verify(booksClient).createInvoiceFromSalesOrder("so-cod-collected");
        verify(booksClient, never()).markInvoiceAsSent("inv-cod-collected");
        verify(booksClient).createCustomerPayment(
            argThat(payload -> {
                Object invoices = payload.get("invoices");
                return invoices instanceof List<?> rows &&
                !rows.isEmpty() &&
                rows.get(0) instanceof Map<?, ?> row &&
                "inv-cod-collected".equals(row.get("invoice_id"));
            })
        );
    }

    @Test
    void syncOrder_codCollectedBeforeDelivery_convertsSalesOrderToInvoice() {
        CustomerOrder order = new CustomerOrder()
            .id(113L)
            .orderNumber("MNM-COD-COLLECTED-EARLY")
            .userLogin("buyer@example.com")
            .paymentMethod("cod")
            .paymentStatus(com.miniandmore.app.domain.enumeration.PaymentStatus.PAID)
            .status(com.miniandmore.app.domain.enumeration.OrderStatus.CONFIRMED)
            .total(BigDecimal.valueOf(1148))
            .zohoSalesOrderId("so-cod-collected-early");

        when(customerOrderRepository.findById(113L)).thenReturn(Optional.of(order));
        when(booksClient.findContactByEmail("buyer@example.com")).thenReturn(Map.of("contact_id", "cust-1"));
        when(booksClient.createInvoiceFromSalesOrder("so-cod-collected-early"))
            .thenReturn(Map.of("invoice", Map.of("invoice_id", "inv-cod-collected-early")));
        when(booksClient.getInvoice("inv-cod-collected-early"))
            .thenReturn(Map.of("invoice", Map.of("status", "sent", "balance", 1148)));
        when(booksClient.createCustomerPayment(any())).thenReturn(Map.of("payment", Map.of("payment_id", "pay-cod-collected-early")));

        service.syncOrder(113L);

        assertThat(order.getZohoInvoiceId()).isEqualTo("inv-cod-collected-early");
        verify(booksClient).createInvoiceFromSalesOrder("so-cod-collected-early");
        verify(booksClient).createCustomerPayment(any());
    }

    @Test
    void syncOrder_paidOnline_marksInvoiceSentWithoutStockAdjustmentAccount() {
        Product product = new Product().id(10L).sku("SKU-10").zohoItemId("zoho-item-10");
        OrderItem line = new OrderItem()
            .productId("10")
            .productName("Dress")
            .qty(1)
            .price(BigDecimal.valueOf(1000));
        CustomerOrder order = new CustomerOrder()
            .id(117L)
            .orderNumber("MNM-ONLINE-PAID")
            .userLogin("buyer@example.com")
            .paymentMethod("online")
            .paymentStatus(com.miniandmore.app.domain.enumeration.PaymentStatus.PAID)
            .status(com.miniandmore.app.domain.enumeration.OrderStatus.CONFIRMED)
            .total(BigDecimal.valueOf(1000));
        order.addItems(line);
        line.setOrder(order);

        when(customerOrderRepository.findById(117L)).thenReturn(Optional.of(order));
        when(orderItemRepository.findAllByOrderId(117L)).thenReturn(List.of(line));
        when(booksClient.findContactByEmail("buyer@example.com")).thenReturn(Map.of("contact_id", "cust-1"));
        when(booksClient.createSalesOrder(any())).thenReturn(Map.of("salesorder", Map.of("salesorder_id", "so-online-paid")));
        when(booksClient.createInvoiceFromSalesOrder("so-online-paid"))
            .thenReturn(Map.of("invoice", Map.of("invoice_id", "inv-online-paid")));
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));

        service.syncOrder(117L);

        assertThat(order.getZohoInvoiceId()).isEqualTo("inv-online-paid");
        verify(booksClient).markInvoiceAsSent("inv-online-paid");
    }

    @Test
    void syncOrder_codCollected_recordsCashPaymentAgainstInvoice() {
        Product product = new Product().id(10L).sku("SKU-10").zohoItemId("zoho-item-10");
        OrderItem line = new OrderItem()
            .productId("10")
            .productName("Dress")
            .qty(1)
            .price(BigDecimal.valueOf(1000));
        CustomerOrder order = new CustomerOrder()
            .id(111L)
            .orderNumber("MNM-COD-PAID")
            .userLogin("buyer@example.com")
            .paymentMethod("cod")
            .paymentStatus(com.miniandmore.app.domain.enumeration.PaymentStatus.PAID)
            .status(com.miniandmore.app.domain.enumeration.OrderStatus.CONFIRMED)
            .total(BigDecimal.valueOf(1148))
            .zohoSalesOrderId("so-cod-paid")
            .zohoInvoiceId("inv-cod-paid");
        order.addItems(line);
        line.setOrder(order);

        when(customerOrderRepository.findById(111L)).thenReturn(Optional.of(order));
        when(booksClient.findContactByEmail("buyer@example.com")).thenReturn(Map.of("contact_id", "cust-1"));
        when(booksClient.getInvoice("inv-cod-paid")).thenReturn(
            Map.of("invoice", Map.of("invoice_id", "inv-cod-paid", "status", "sent", "balance", 1148))
        );
        when(booksClient.createCustomerPayment(any())).thenReturn(Map.of("payment", Map.of("payment_id", "pay-1")));

        service.syncOrder(111L);

        verify(booksClient, never()).createSalesOrder(any());
        ArgumentCaptor<Map<String, Object>> paymentCaptor = ArgumentCaptor.forClass(Map.class);
        verify(booksClient).createCustomerPayment(paymentCaptor.capture());
        Map<String, Object> payment = paymentCaptor.getValue();
        assertThat(payment.get("payment_mode")).isEqualTo("cash");
        assertThat(payment.get("customer_id")).isEqualTo("cust-1");
        assertThat(new BigDecimal(String.valueOf(payment.get("amount")))).isEqualByComparingTo("1148");
        assertThat(payment.get("reference_number")).isEqualTo("MNM-COD-PAID");
    }

    @Test
    void syncOrder_codCollected_usesInvoiceBalanceWhenLowerThanOrderTotal() {
        CustomerOrder order = new CustomerOrder()
            .id(114L)
            .orderNumber("MNM-COD-BALANCE")
            .userLogin("buyer@example.com")
            .paymentMethod("cod")
            .paymentStatus(com.miniandmore.app.domain.enumeration.PaymentStatus.PAID)
            .status(com.miniandmore.app.domain.enumeration.OrderStatus.CONFIRMED)
            .total(BigDecimal.valueOf(419.2))
            .zohoSalesOrderId("so-cod-balance")
            .zohoInvoiceId("inv-cod-balance");

        when(customerOrderRepository.findById(114L)).thenReturn(Optional.of(order));
        when(booksClient.findContactByEmail("buyer@example.com")).thenReturn(Map.of("contact_id", "cust-1"));
        when(booksClient.getInvoice("inv-cod-balance")).thenReturn(
            Map.of("invoice", Map.of("invoice_id", "inv-cod-balance", "status", "sent", "balance", 320.2))
        );
        when(booksClient.createCustomerPayment(any())).thenReturn(Map.of("payment", Map.of("payment_id", "pay-balance")));

        service.syncOrder(114L);

        ArgumentCaptor<Map<String, Object>> paymentCaptor = ArgumentCaptor.forClass(Map.class);
        verify(booksClient).createCustomerPayment(paymentCaptor.capture());
        Map<String, Object> payment = paymentCaptor.getValue();
        assertThat(new BigDecimal(String.valueOf(payment.get("amount")))).isEqualByComparingTo("320.2");
        @SuppressWarnings("unchecked")
        Map<String, Object> invoicePayment = ((List<Map<String, Object>>) payment.get("invoices")).get(0);
        assertThat(new BigDecimal(String.valueOf(invoicePayment.get("amount_applied")))).isEqualByComparingTo("320.2");
    }

    @Test
    void syncOrder_createsContactWithoutInvalidLoginEmail() {
        Product product = new Product().id(10L).sku("SKU-10").zohoItemId("zoho-item-10");
        OrderItem line = new OrderItem()
            .productId("10")
            .productName("Dress")
            .qty(1)
            .price(BigDecimal.valueOf(499));
        CustomerOrder order = new CustomerOrder()
            .id(101L)
            .orderNumber("MNM-ADMIN-1")
            .userLogin("admin")
            .shippingAddressJson(
                "{\"full_name\":\"Kedar\",\"email\":\"admin\",\"phone\":\"+917036306469\",\"line1\":\"Line 1\",\"city\":\"City\",\"state\":\"State\",\"pincode\":\"522235\"}"
            );
        order.addItems(line);
        line.setOrder(order);

        when(customerOrderRepository.findById(101L)).thenReturn(Optional.of(order));
        when(orderItemRepository.findAllByOrderId(101L)).thenReturn(List.of(line));
        when(booksClient.findContactByName("Kedar")).thenReturn(null);
        when(booksClient.createContact(any())).thenReturn(Map.of("contact", Map.of("contact_id", "cust-admin")));
        when(booksClient.createSalesOrder(any())).thenReturn(Map.of("salesorder", Map.of("salesorder_id", "so-admin")));
        when(booksClient.createInvoiceFromSalesOrder("so-admin")).thenReturn(Map.of("invoice", Map.of("invoice_id", "inv-admin")));
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));

        service.syncOrder(101L);

        verify(booksClient, never()).findContactByEmail(any());
        verify(booksClient).createContact(
            argThat(payload -> {
                Object personsObj = payload.get("contact_persons");
                if (!(personsObj instanceof List<?> persons) || persons.isEmpty()) {
                    return false;
                }
                Object personObj = persons.get(0);
                if (!(personObj instanceof Map<?, ?> person)) {
                    return false;
                }
                return "Kedar".equals(payload.get("contact_name")) && !person.containsKey("email");
            })
        );
    }

    @Test
    void syncOrder_skipsContactNameLookupWhenFullNameIsEmail() {
        Product product = new Product().id(10L).sku("SKU-10").zohoItemId("zoho-item-10");
        OrderItem line = new OrderItem()
            .productId("10")
            .productName("Dress")
            .qty(1)
            .price(BigDecimal.valueOf(499));
        CustomerOrder order = new CustomerOrder()
            .id(121L)
            .orderNumber("MNM-EMAIL-NAME")
            .userLogin("guest")
            .shippingAddressJson(
                "{\"full_name\":\"buyer@example.com\",\"email\":\"buyer@example.com\",\"phone\":\"9876543210\",\"line1\":\"Line 1\",\"city\":\"City\",\"state\":\"Telangana\",\"pincode\":\"500001\"}"
            );
        order.addItems(line);
        line.setOrder(order);

        when(customerOrderRepository.findById(121L)).thenReturn(Optional.of(order));
        when(orderItemRepository.findAllByOrderId(121L)).thenReturn(List.of(line));
        when(booksClient.findContactByEmail("buyer@example.com")).thenReturn(null);
        when(booksClient.findContactByName("MNM-EMAIL-NAME")).thenReturn(null);
        when(booksClient.createContact(any())).thenReturn(Map.of("contact", Map.of("contact_id", "cust-email")));
        when(booksClient.createSalesOrder(any())).thenReturn(Map.of("salesorder", Map.of("salesorder_id", "so-email")));
        when(booksClient.createInvoiceFromSalesOrder("so-email")).thenReturn(Map.of("invoice", Map.of("invoice_id", "inv-email")));
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));

        service.syncOrder(121L);

        verify(booksClient).findContactByName("MNM-EMAIL-NAME");
        verify(booksClient, never()).findContactByName(argThat(name -> name != null && name.contains("@")));
        verify(booksClient).createContact(argThat(payload -> "MNM-EMAIL-NAME".equals(payload.get("contact_name"))));
    }

    @Test
    void syncProduct_update_adjustsZohoStockWhenStoreStockDiffers() {
        ApplicationProperties.Zoho zoho = new ApplicationProperties.Zoho();
        zoho.setSyncProductStock(true);
        zoho.setInventoryAccountId("inv-acct-1");
        zoho.setInventoryAdjustmentAccountId("adj-acct-1");
        when(applicationProperties.getZoho()).thenReturn(zoho);

        Product product = new Product()
            .id(11L)
            .sku("SKU-11")
            .name("Scarf")
            .zohoItemId("zoho-11")
            .stock(100)
            .price(BigDecimal.valueOf(299))
            .active(true);
        when(productRepository.findOneWithEagerRelationships(11L)).thenReturn(Optional.of(product));
        when(booksClient.updateItem(eq("zoho-11"), any())).thenReturn(Map.of("item", Map.of("item_id", "zoho-11", "item_type", "inventory")));
        when(booksClient.getItem("zoho-11")).thenReturn(Map.of("item", Map.of("item_id", "zoho-11", "item_type", "inventory", "stock_on_hand", 0, "rate", 299)));

        service.syncProduct(11L);

        verify(booksClient).createInventoryAdjustment(
            argThat(payload -> {
                Object linesObj = payload.get("line_items");
                if (!(linesObj instanceof List<?> lines) || lines.isEmpty()) {
                    return false;
                }
                Object lineObj = lines.get(0);
                if (!(lineObj instanceof Map<?, ?> line)) {
                    return false;
                }
                return "zoho-11".equals(line.get("item_id")) && Integer.valueOf(100).equals(line.get("quantity_adjusted"));
            })
        );
    }

    @Test
    void syncProduct_update_adjustsStockUsingOrgLocationAndAdjustmentAccount() {
        ApplicationProperties.Zoho zoho = new ApplicationProperties.Zoho();
        zoho.setSyncProductStock(true);
        zoho.setInventoryAccountId("inv-acct-1");
        zoho.setInventoryAdjustmentAccountId("stale-adj");
        zoho.setDefaultLocationId("stale-loc");
        zoho.setDefaultLocationName("Head Office");
        when(applicationProperties.getZoho()).thenReturn(zoho);
        when(booksClient.listAssetAccounts()).thenReturn(
            List.of(Map.of("account_id", "inv-acct-1", "account_name", "Inventory Asset"))
        );
        when(booksClient.listExpenseAccounts()).thenReturn(
            List.of(Map.of("account_id", "cogs-1", "account_name", "Cost of Goods Sold"))
        );
        when(booksClient.listLocations()).thenReturn(
            List.of(Map.of("location_id", "org-loc", "location_name", "Head Office", "is_primary", true))
        );

        Product product = new Product()
            .id(12L)
            .sku("SKU-12")
            .name("Scarf")
            .zohoItemId("zoho-12")
            .stock(12)
            .price(BigDecimal.valueOf(299))
            .active(true);
        when(productRepository.findOneWithEagerRelationships(12L)).thenReturn(Optional.of(product));
        when(booksClient.updateItem(eq("zoho-12"), any())).thenReturn(
            Map.of("item", Map.of("item_id", "zoho-12", "item_type", "inventory"))
        );
        when(booksClient.getItem("zoho-12")).thenReturn(
            Map.of("item", Map.of("item_id", "zoho-12", "item_type", "inventory", "stock_on_hand", 0, "rate", 299))
        );

        service.syncProduct(12L);

        verify(booksClient).createInventoryAdjustment(
            argThat(payload -> {
                Object linesObj = payload.get("line_items");
                if (!(linesObj instanceof List<?> lines) || lines.isEmpty()) {
                    return false;
                }
                Object lineObj = lines.get(0);
                if (!(lineObj instanceof Map<?, ?> line)) {
                    return false;
                }
                return (
                    "cogs-1".equals(payload.get("adjustment_account_id")) &&
                    "org-loc".equals(payload.get("location_id")) &&
                    "zoho-12".equals(line.get("item_id")) &&
                    Integer.valueOf(12).equals(line.get("quantity_adjusted"))
                );
            })
        );
    }

    @Test
    void syncProductStockOnly_whenZohoAlreadyMatches_skipsAdjustment() {
        ApplicationProperties.Zoho zoho = new ApplicationProperties.Zoho();
        zoho.setSyncProductStock(true);
        zoho.setInventoryAccountId("inv-acct-1");
        zoho.setInventoryAdjustmentAccountId("adj-acct-1");
        when(applicationProperties.getZoho()).thenReturn(zoho);

        Product product = new Product()
            .id(14L)
            .sku("SKU-14")
            .name("Hat")
            .zohoItemId("zoho-14")
            .stock(20)
            .price(BigDecimal.valueOf(199))
            .active(true);
        when(productRepository.findOneWithEagerRelationships(14L)).thenReturn(Optional.of(product));
        when(booksClient.getItem("zoho-14")).thenReturn(
            Map.of("item", Map.of("item_id", "zoho-14", "item_type", "inventory", "stock_on_hand", 20, "rate", 199))
        );

        Map<String, Object> result = service.syncProductStockOnly(14L);

        assertThat(result).containsEntry("ok", true);
        verify(booksClient, never()).createInventoryAdjustment(any());
    }

    @Test
    void syncOrder_afterInvoice_reconcilesStoreStockWhenZohoWasDoubleDeducted() {
        ApplicationProperties.Zoho zoho = new ApplicationProperties.Zoho();
        zoho.setSyncProductStock(true);
        zoho.setInventoryAccountId("inv-acct-1");
        zoho.setInventoryAdjustmentAccountId("adj-acct-1");
        when(applicationProperties.getZoho()).thenReturn(zoho);

        Product product = new Product().id(10L).sku("SKU-10").zohoItemId("zoho-item-10").stock(98).price(BigDecimal.valueOf(499));
        OrderItem line = new OrderItem()
            .productId("10")
            .productName("Dress")
            .qty(2)
            .price(BigDecimal.valueOf(499));
        CustomerOrder order = new CustomerOrder().id(100L).orderNumber("MNM-TEST-1").userLogin("buyer@example.com");
        order.addItems(line);
        line.setOrder(order);

        when(customerOrderRepository.findById(100L)).thenReturn(Optional.of(order));
        when(orderItemRepository.findAllByOrderId(100L)).thenReturn(List.of(line));
        when(booksClient.findContactByEmail("buyer@example.com")).thenReturn(Map.of("contact_id", "cust-1"));
        when(booksClient.createSalesOrder(any())).thenReturn(Map.of("salesorder", Map.of("salesorder_id", "so-1")));
        when(booksClient.createInvoiceFromSalesOrder("so-1")).thenReturn(Map.of("invoice", Map.of("invoice_id", "inv-1")));
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        when(productRepository.findOneWithEagerRelationships(10L)).thenReturn(Optional.of(product));
        when(booksClient.getItem("zoho-item-10")).thenReturn(
            Map.of("item", Map.of("item_id", "zoho-item-10", "item_type", "inventory", "stock_on_hand", 96, "rate", 499))
        );

        service.syncOrder(100L);

        verify(booksClient).markInvoiceAsSent("inv-1");
        verify(booksClient).createInventoryAdjustment(
            argThat(payload -> {
                Object linesObj = payload.get("line_items");
                if (!(linesObj instanceof List<?> lines) || lines.isEmpty()) {
                    return false;
                }
                Object lineObj = lines.get(0);
                if (!(lineObj instanceof Map<?, ?> adjustmentLine)) {
                    return false;
                }
                return "zoho-item-10".equals(adjustmentLine.get("item_id")) && Integer.valueOf(2).equals(adjustmentLine.get("quantity_adjusted"));
            })
        );
    }

    @Test
    void syncProduct_update_enablesInventoryWhenZohoItemWasCreatedWithoutOpeningStock() {
        ApplicationProperties.Zoho zoho = new ApplicationProperties.Zoho();
        zoho.setSyncProductStock(true);
        zoho.setInventoryAccountId("inv-acct-1");
        zoho.setInventoryAdjustmentAccountId("adj-acct-1");
        zoho.setDefaultLocationId("loc-1");
        when(applicationProperties.getZoho()).thenReturn(zoho);

        Product product = new Product()
            .id(13L)
            .sku("SKU-13")
            .name("Scarf")
            .zohoItemId("zoho-13")
            .stock(12)
            .price(BigDecimal.valueOf(299))
            .active(true);
        when(productRepository.findOneWithEagerRelationships(13L)).thenReturn(Optional.of(product));
        when(booksClient.updateItem(eq("zoho-13"), any())).thenReturn(
            Map.of("item", Map.of("item_id", "zoho-13", "item_type", "sales"))
        );
        when(booksClient.getItem("zoho-13"))
            .thenReturn(
                Map.of(
                    "item",
                    Map.of("item_id", "zoho-13", "item_type", "sales", "name", "Scarf", "sku", "SKU-13", "rate", 299, "stock_on_hand", 0)
                )
            )
            .thenReturn(
                Map.of("item", Map.of("item_id", "zoho-13", "item_type", "inventory", "stock_on_hand", 0, "rate", 299))
            );

        service.syncProduct(13L);

        verify(booksClient, atLeastOnce()).updateItem(
            eq("zoho-13"),
            argThat(
                payload ->
                    "inventory".equals(payload.get("item_type")) &&
                    payload.containsKey("locations")
            )
        );
        verify(booksClient).createInventoryAdjustment(
            argThat(payload -> {
                Object linesObj = payload.get("line_items");
                if (!(linesObj instanceof List<?> lines) || lines.isEmpty()) {
                    return false;
                }
                Object lineObj = lines.get(0);
                if (!(lineObj instanceof Map<?, ?> line)) {
                    return false;
                }
                return "zoho-13".equals(line.get("item_id")) && Integer.valueOf(12).equals(line.get("quantity_adjusted"));
            })
        );
    }

    @Test
    void pullOrderTracking_appliesSalesOrderShipmentFields() {
        CustomerOrder order = new CustomerOrder()
            .id(41302L)
            .zohoSalesOrderId("4063993000000110007")
            .zohoInvoiceId("4063993000000103028");
        when(customerOrderRepository.findById(41302L)).thenReturn(Optional.of(order));
        when(booksClient.getSalesOrder("4063993000000110007"))
            .thenReturn(
                Map.of(
                    "salesorder",
                    Map.of(
                        "status",
                        "invoiced",
                        "shipment_date",
                        "2026-08-14",
                        "delivery_method",
                        "DTDC",
                        "tracking_number",
                        "TRK-1"
                    )
                )
            );
        service.pullOrderTracking(41302L);

        verify(booksClient).getSalesOrder("4063993000000110007");
        verify(customerOrderRepository).save(argThat(saved -> "2026-08-14".equals(saved.getShipmentDate()) && "DTDC".equals(saved.getTrackingCarrier())));
    }

    @Test
    void pullOrderTracking_marksHandWrappedWhenZohoPackageExists() {
        CustomerOrder order = new CustomerOrder()
            .id(41304L)
            .orderNumber("MNM-PACKED")
            .paymentStatus(com.miniandmore.app.domain.enumeration.PaymentStatus.PAID)
            .status(com.miniandmore.app.domain.enumeration.OrderStatus.CONFIRMED)
            .zohoSalesOrderId("so-packed");
        when(customerOrderRepository.findById(41304L)).thenReturn(Optional.of(order));
        when(booksClient.getSalesOrder("so-packed"))
            .thenReturn(Map.of("salesorder", Map.of("status", "invoiced", "salesorder_number", "SO-42")));
        when(booksClient.listPackagesForSalesOrder("so-packed", "SO-42"))
            .thenReturn(List.of(Map.of("package_id", "pkg-42", "salesorder_id", "so-packed")));
        service.pullOrderTracking(41304L);

        assertThat(order.getStatus()).isEqualTo(com.miniandmore.app.domain.enumeration.OrderStatus.HAND_WRAPPED);
        verify(customerOrderRepository).save(order);
        verify(orderEventService).syncLifecycleEvents(order);
    }

    @Test
    void pullOrderTracking_marksDeliveredWhenZohoPackageStatusIsFulfilled() {
        CustomerOrder order = new CustomerOrder()
            .id(41306L)
            .orderNumber("MNM-SHIPPED")
            .paymentStatus(com.miniandmore.app.domain.enumeration.PaymentStatus.PAID)
            .status(com.miniandmore.app.domain.enumeration.OrderStatus.HAND_WRAPPED)
            .shipmentDate("2026-08-30")
            .zohoSalesOrderId("so-shipped");
        when(customerOrderRepository.findById(41306L)).thenReturn(Optional.of(order));
        when(booksClient.getSalesOrder("so-shipped"))
            .thenReturn(
                Map.of(
                    "salesorder",
                    Map.of("status", "fulfilled", "shipment_date", "2026-08-30", "salesorder_number", "SO-46")
                )
            );
        when(booksClient.listPackagesForSalesOrder("so-shipped", "SO-46"))
            .thenReturn(
                List.of(
                    Map.of(
                        "package_id",
                        "pkg-46",
                        "salesorder_id",
                        "so-shipped",
                        "shipment_id",
                        "ship-46",
                        "status",
                        "fulfilled"
                    )
                )
            );
        service.pullOrderTracking(41306L);

        assertThat(order.getStatus()).isEqualTo(com.miniandmore.app.domain.enumeration.OrderStatus.FULFILLED);
        verify(customerOrderRepository).save(order);
    }

    @Test
    void pullOrderTracking_packageLookupFailureDoesNotBlockOtherTrackingUpdates() {
        CustomerOrder order = new CustomerOrder()
            .id(41305L)
            .paymentStatus(com.miniandmore.app.domain.enumeration.PaymentStatus.PAID)
            .status(com.miniandmore.app.domain.enumeration.OrderStatus.CONFIRMED)
            .zohoSalesOrderId("so-package-error");
        when(customerOrderRepository.findById(41305L)).thenReturn(Optional.of(order));
        when(booksClient.getSalesOrder("so-package-error"))
            .thenReturn(Map.of("salesorder", Map.of("status", "invoiced", "salesorder_number", "SO-45")));
        when(booksClient.listPackagesForSalesOrder("so-package-error", "SO-45"))
            .thenThrow(new RestClientResponseException("Missing scope", 401, "Unauthorized", null, null, null));
        service.pullOrderTracking(41305L);

        assertThat(order.getStatus()).isEqualTo(com.miniandmore.app.domain.enumeration.OrderStatus.CONFIRMED);
        assertThat(order.getZohoTrackingLastPulledAt()).isNotNull();
        verify(customerOrderRepository).save(order);
    }

    @Test
    void pullOrderTracking_clearsStaleShipmentWhenZohoReportsPendingUnpacked() {
        CustomerOrder order = new CustomerOrder()
            .id(41307L)
            .orderNumber("MNM-26BEB5577791")
            .paymentStatus(com.miniandmore.app.domain.enumeration.PaymentStatus.PAID)
            .status(com.miniandmore.app.domain.enumeration.OrderStatus.HAND_WRAPPED)
            .shipmentDate("2026-09-02")
            .zohoSalesOrderId("so-stale-ship");
        when(customerOrderRepository.findById(41307L)).thenReturn(Optional.of(order));
        when(booksClient.getSalesOrder("so-stale-ship"))
            .thenReturn(
                Map.of(
                    "salesorder",
                    Map.of(
                        "status",
                        "invoiced",
                        "shipped_status",
                        "pending",
                        "salesorder_number",
                        "SO-STALE",
                        "line_items",
                        List.of(Map.of("quantity_packed", 0, "quantity_shipped", 0))
                    )
                )
            );
        when(booksClient.listPackagesForSalesOrder("so-stale-ship", "SO-STALE")).thenReturn(List.of());
        service.pullOrderTracking(41307L);

        assertThat(order.getStatus()).isEqualTo(com.miniandmore.app.domain.enumeration.OrderStatus.CONFIRMED);
        assertThat(order.getShipmentDate()).isNull();
        verify(customerOrderRepository).save(order);
        verify(orderEventService).syncLifecycleEvents(order);
    }

    @Test
    void pullOrderTracking_invoiceFulfilledPaidCod_doesNotMarkDeliveredOrCreateAccountingRecords() {
        CustomerOrder order = new CustomerOrder()
            .id(41303L)
            .orderNumber("MNM-COD-DELIVERED")
            .userLogin("buyer@example.com")
            .paymentMethod("cod")
            .paymentStatus(com.miniandmore.app.domain.enumeration.PaymentStatus.PAID)
            .status(com.miniandmore.app.domain.enumeration.OrderStatus.CONFIRMED)
            .total(BigDecimal.valueOf(1148))
            .zohoSalesOrderId("so-cod-delivered");
        when(customerOrderRepository.findById(41303L)).thenReturn(Optional.of(order));
        when(booksClient.getSalesOrder("so-cod-delivered"))
            .thenReturn(Map.of("salesorder", Map.of("status", "fulfilled", "shipment_date", "2026-08-29")));
        service.pullOrderTracking(41303L);

        assertThat(order.getStatus()).isEqualTo(com.miniandmore.app.domain.enumeration.OrderStatus.CONFIRMED);
        assertThat(order.getZohoInvoiceId()).isNull();
        verify(booksClient, never()).createInvoiceFromSalesOrder(any());
        verify(booksClient, never()).createCustomerPayment(any());
    }

    @Test
    void pullProducts_clampsNegativeZohoStockToZero() {
        when(zohoSettingRepository.findActiveOrLatest()).thenReturn(Optional.empty());
        when(booksClient.listItems(1))
            .thenReturn(
                List.of(
                    Map.of(
                        "sku",
                        "NEG-STOCK",
                        "item_id",
                        "zoho-neg",
                        "name",
                        "Negative Stock Item",
                        "unit",
                        "dozen",
                        "rate",
                        199,
                        "stock_on_hand",
                        -3
                    )
                )
            );
        when(booksClient.listItems(2)).thenReturn(List.of());
        when(booksClient.getItem("zoho-neg"))
            .thenReturn(
                Map.of(
                    "item",
                    Map.of(
                        "sku",
                        "NEG-STOCK",
                        "item_id",
                        "zoho-neg",
                        "name",
                        "Negative Stock Item",
                        "unit",
                        "dozen",
                        "rate",
                        199,
                        "stock_on_hand",
                        -3
                    )
                )
            );
        when(productRepository.findOneBySku("NEG-STOCK")).thenReturn(Optional.empty());
        when(productRepository.findOneByZohoItemId("zoho-neg")).thenReturn(Optional.empty());
        when(productRepository.findMaxSortOrder()).thenReturn(0);
        when(productRepository.findAllByZohoItemIdIsNotNull()).thenReturn(List.of());
        when(productRepository.findAll()).thenReturn(List.of());
        when(productFieldMapper.applyPulledItem(any(), any(), eq(false))).thenReturn(Optional.empty());
        when(productRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Map<String, Object> result = service.pullProducts();

        verify(productRepository).save(
            argThat(product -> Integer.valueOf(0).equals(product.getStock()) && "dozen".equals(product.getUnits()))
        );
        org.junit.jupiter.api.Assertions.assertEquals(1, result.get("success"));
    }

    @Test
    void pullProducts_zohoRateLimitMidPagination_stopsCleanlyInsteadOfThrowing() {
        // A 429 mid-pagination must not bubble up as an unhandled 500 to the admin UI, and must not
        // run deactivateStaleZohoProducts on the partial SKU set (that would wrongly deactivate every
        // product on pages never reached).
        when(zohoSettingRepository.findActiveOrLatest()).thenReturn(Optional.empty());
        when(productRepository.findAll()).thenReturn(List.of());
        when(booksClient.listItems(1))
            .thenReturn(List.of(Map.of("sku", "OK-ITEM", "item_id", "zoho-ok", "name", "Ok Item", "rate", 100)));
        when(booksClient.getItem("zoho-ok"))
            .thenReturn(Map.of("item", Map.of("sku", "OK-ITEM", "item_id", "zoho-ok", "name", "Ok Item", "rate", 100)));
        when(productRepository.findOneBySku("OK-ITEM")).thenReturn(Optional.empty());
        when(productRepository.findOneByZohoItemId("zoho-ok")).thenReturn(Optional.empty());
        when(productRepository.findMaxSortOrder()).thenReturn(0);
        when(productFieldMapper.applyPulledItem(any(), any(), eq(false))).thenReturn(Optional.empty());
        when(productRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(booksClient.listItems(2))
            .thenThrow(
                org.springframework.web.client.HttpClientErrorException.create(
                    org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,
                    "Too Many Requests",
                    null,
                    null,
                    null
                )
            );

        Map<String, Object> result = org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> service.pullProducts());

        org.junit.jupiter.api.Assertions.assertEquals(1, result.get("success"));
        org.junit.jupiter.api.Assertions.assertEquals(1, result.get("failed"));
        verify(productRepository, never()).findAllByZohoItemIdIsNotNull();
    }

    @Test
    void pullProducts_fetchesItemDetailEvenWhenImageSyncDisabled() {
        when(zohoSettingRepository.findActiveOrLatest()).thenReturn(Optional.empty());
        when(itemImageSync.isEnabled()).thenReturn(false);
        when(booksClient.listItems(1))
            .thenReturn(
                List.of(
                    Map.of(
                        "sku",
                        "DETAIL-SIZES",
                        "item_id",
                        "zoho-detail",
                        "name",
                        "Detail Fetch",
                        "rate",
                        199,
                        "stock_on_hand",
                        4
                    )
                )
            );
        when(booksClient.listItems(2)).thenReturn(List.of());
        when(booksClient.getItem("zoho-detail"))
            .thenReturn(
                Map.of(
                    "item",
                    Map.of(
                        "sku",
                        "DETAIL-SIZES",
                        "item_id",
                        "zoho-detail",
                        "name",
                        "Detail Fetch",
                        "rate",
                        199,
                        "stock_on_hand",
                        4,
                        "custom_fields",
                        List.of(
                            Map.of(
                                "customfield_id",
                                "4063993000000072088",
                                "api_name",
                                "cf__sb_60082852067_sizes",
                                "value",
                                "0-3 M, 3-6 M, 6-12 M"
                            )
                        )
                    )
                )
            );
        when(productRepository.findOneBySku("DETAIL-SIZES")).thenReturn(Optional.empty());
        when(productRepository.findOneByZohoItemId("zoho-detail")).thenReturn(Optional.empty());
        when(productRepository.findMaxSortOrder()).thenReturn(0);
        when(productRepository.findAllByZohoItemIdIsNotNull()).thenReturn(List.of());
        when(productRepository.findAll()).thenReturn(List.of());
        when(productFieldMapper.applyPulledItem(any(), any(), eq(false))).thenReturn(Optional.empty());
        when(productRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.pullProducts();

        verify(booksClient).getItem("zoho-detail");
        verify(itemImageSync, never()).importZohoItemImage(any(), any(), any());
    }

    @Test
    void pullProducts_mapsSizesFromDescriptionMetadata() {
        service =
            new ZohoSyncService(
                booksClient,
                objectMapper,
                applicationProperties,
                itemImageSync,
                productFieldMapper,
                new ProductDescriptionCodec(objectMapper),
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
        when(zohoSettingRepository.findActiveOrLatest()).thenReturn(Optional.empty());
        when(itemImageSync.isEnabled()).thenReturn(false);
        when(booksClient.listItems(1))
            .thenReturn(
                List.of(
                    Map.of(
                        "sku",
                        "DESC-SIZES",
                        "item_id",
                        "zoho-desc",
                        "name",
                        "Described",
                        "rate",
                        199,
                        "description",
                        "Sizes: 0-3 M, 3-6 M, 6-12 M\n\nSoft fabric"
                    )
                )
            );
        when(booksClient.listItems(2)).thenReturn(List.of());
        when(booksClient.getItem("zoho-desc"))
            .thenReturn(
                Map.of(
                    "item",
                    Map.of(
                        "sku",
                        "DESC-SIZES",
                        "item_id",
                        "zoho-desc",
                        "name",
                        "Described",
                        "rate",
                        199,
                        "description",
                        "Sizes: 0-3 M, 3-6 M, 6-12 M\n\nSoft fabric",
                        "custom_fields",
                        List.of()
                    )
                )
            );
        when(productRepository.findOneBySku("DESC-SIZES")).thenReturn(Optional.empty());
        when(productRepository.findOneByZohoItemId("zoho-desc")).thenReturn(Optional.empty());
        when(productRepository.findMaxSortOrder()).thenReturn(0);
        when(productRepository.findAllByZohoItemIdIsNotNull()).thenReturn(List.of());
        when(productRepository.findAll()).thenReturn(List.of());
        when(productFieldMapper.applyPulledItem(any(), any(), eq(false))).thenReturn(Optional.empty());
        ArgumentCaptor<Product> saved = ArgumentCaptor.forClass(Product.class);
        when(productRepository.save(saved.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        service.pullProducts();

        Product product = saved.getValue();
        assertThat(product.getSizesJson()).contains("0-3 M", "3-6 M", "6-12 M");
        assertThat(product.getDescription()).isEqualTo("Soft fabric");
    }

    @Test
    void syncProduct_variants_pushesEachActiveVariantSku() {
        ProductVariantSupport realVariantSupport = StorePricingTestSupport.variantSupport(objectMapper);
        service =
            new ZohoSyncService(
                booksClient,
                objectMapper,
                applicationProperties,
                itemImageSync,
                productFieldMapper,
                descriptionCodec,
                realVariantSupport,
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

        Product product = new Product()
            .id(20L)
            .sku("HORIZON")
            .name("Horizon Set")
            .itemStructure("VARIANTS")
            .variantsJson(
                """
                [{"sku":"HORIZON-S-NAVY","size":"S","color":"Navy","rate":799.20,"mrp":999,"stock":3,"active":true},\
                {"sku":"HORIZON-M-NAVY","size":"M","color":"Navy","rate":959.20,"mrp":1199,"stock":4,"active":true}]
                """
            )
            .units("set")
            .active(true);
        when(productRepository.findOneWithEagerRelationships(20L)).thenReturn(Optional.of(product));
        when(booksClient.findItemBySku("HORIZON-S-NAVY")).thenReturn(null);
        when(booksClient.findItemBySku("HORIZON-M-NAVY")).thenReturn(null);
        when(booksClient.createItem(any()))
            .thenReturn(Map.of("item", Map.of("item_id", "zoho-s")))
            .thenReturn(Map.of("item", Map.of("item_id", "zoho-m")));
        when(productRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.syncProduct(20L);

        verify(booksClient, times(2)).createItem(any());
        verify(booksClient).createItem(argThat(payload -> "HORIZON-S-NAVY".equals(payload.get("sku"))));
        verify(booksClient).createItem(argThat(payload -> "HORIZON-M-NAVY".equals(payload.get("sku"))));
        verify(booksClient).createItem(
            argThat(
                payload ->
                    "HORIZON-S-NAVY".equals(payload.get("sku")) &&
                    "Size".equals(payload.get("attribute_name1")) &&
                    "S".equals(payload.get("attribute_option_name1")) &&
                    "Color".equals(payload.get("attribute_name2")) &&
                    "Navy".equals(payload.get("attribute_option_name2")) &&
                    "set".equals(payload.get("unit")) &&
                    !payload.containsKey("group_id")
            )
        );
        // Saved once per row as each Zoho item id lands, plus the final save — not just once at the end.
        verify(productRepository, times(2)).save(argThat(saved ->
            saved.getVariantsJson().contains("zoho-s") || saved.getVariantsJson().contains("zoho-m")
        ));
        verify(productRepository).updateZohoSyncSuccess(eq(20L), any(), eq(ZohoSyncStatus.SYNCED), any());
    }

    @Test
    void syncProduct_variants_longSkuStockAdjustment_keepsReferenceNumberUnder50Chars() {
        ProductVariantSupport realVariantSupport = StorePricingTestSupport.variantSupport(objectMapper);
        service =
            new ZohoSyncService(
                booksClient,
                objectMapper,
                applicationProperties,
                itemImageSync,
                productFieldMapper,
                descriptionCodec,
                realVariantSupport,
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
        zoho.setInventoryAccountId("inv-acct-1");
        zoho.setInventoryAdjustmentAccountId("adj-acct-1");
        when(applicationProperties.getZoho()).thenReturn(zoho);

        Product product = new Product()
            .id(39351L)
            .sku("BASIC-SHORTS")
            .name("Basic Shorts")
            .itemStructure("VARIANTS")
            .variantsJson(
                """
                [{"sku":"BASIC-SHORTS-1824-SNAIL-BLUE","size":"18-24 M","color":"Snail Blue","rate":100,"mrp":349,"stock":24,"active":true,"zohoItemId":"zoho-long"}]
                """
            )
            .active(true);
        when(productRepository.findOneWithEagerRelationships(39351L)).thenReturn(Optional.of(product));
        when(booksClient.updateItem(eq("zoho-long"), any())).thenReturn(Map.of("item", Map.of("item_id", "zoho-long")));
        when(booksClient.getItem("zoho-long")).thenReturn(
            Map.of("item", Map.of("item_id", "zoho-long", "item_type", "inventory", "stock_on_hand", 0, "rate", 100))
        );
        when(productRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.syncProduct(39351L);

        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(booksClient).createInventoryAdjustment(payloadCaptor.capture());
        String referenceNumber = (String) payloadCaptor.getValue().get("reference_number");
        assertThat(referenceNumber).isNotNull();
        assertThat(referenceNumber.length()).isLessThanOrEqualTo(50);
    }

    @Test
    void syncProduct_variants_staleZohoItemId_recreatesRowInsteadOfFailing() {
        ProductVariantSupport realVariantSupport = StorePricingTestSupport.variantSupport(objectMapper);
        service =
            new ZohoSyncService(
                booksClient,
                objectMapper,
                applicationProperties,
                itemImageSync,
                productFieldMapper,
                descriptionCodec,
                realVariantSupport,
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

        Product product = new Product()
            .id(21L)
            .sku("SHORTS")
            .name("Basic Shorts")
            .itemStructure("VARIANTS")
            .variantsJson(
                """
                [{"sku":"SHORTS-S-BLUE","size":"S","color":"Blue","rate":399.20,"mrp":499,"stock":3,"active":true,"zohoItemId":"zoho-stale"}]
                """
            )
            .active(true);
        when(productRepository.findOneWithEagerRelationships(21L)).thenReturn(Optional.of(product));
        when(booksClient.updateItem(eq("zoho-stale"), any()))
            .thenThrow(
                new RestClientResponseException(
                    "This item cannot be edited as it does not exist.",
                    400,
                    "Bad Request",
                    null,
                    "{\"code\":2006,\"message\":\"This item cannot be edited as it does not exist.\"}".getBytes(),
                    null
                )
            );
        when(booksClient.createItem(any())).thenReturn(Map.of("item", Map.of("item_id", "zoho-fresh")));
        when(productRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.syncProduct(21L);

        verify(booksClient, never()).findItemBySku(any());
        verify(booksClient).createItem(argThat(payload -> "SHORTS-S-BLUE".equals(payload.get("sku"))));
        verify(productRepository).save(argThat(saved -> saved.getVariantsJson().contains("zoho-fresh")));
        verify(productRepository).updateZohoSyncSuccess(eq(21L), any(), eq(ZohoSyncStatus.SYNCED), any());
        verify(productRepository, never()).updateZohoSyncFailure(eq(21L), any(), any());
    }

    @Test
    void pullProducts_mergesVariantSkuIntoParentMatrix() {
        ProductVariantSupport realVariantSupport = StorePricingTestSupport.variantSupport(objectMapper);
        service =
            new ZohoSyncService(
                booksClient,
                objectMapper,
                applicationProperties,
                itemImageSync,
                productFieldMapper,
                descriptionCodec,
                realVariantSupport,
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

        Product parent = new Product()
            .id(30L)
            .sku("HORIZON")
            .name("Horizon Set")
            .itemStructure("VARIANTS")
            .variantsJson(
                """
                [{"sku":"HORIZON-S-NAVY","size":"S","color":"Navy","rate":799.20,"mrp":999,"stock":1,"active":true}]
                """
            )
            .active(true);
        when(zohoSettingRepository.findActiveOrLatest()).thenReturn(Optional.empty());
        when(booksClient.listItems(1))
            .thenReturn(
                List.of(
                    Map.of(
                        "sku",
                        "HORIZON-S-NAVY",
                        "item_id",
                        "zoho-var",
                        "name",
                        "Horizon S Navy",
                        "rate",
                        799.20,
                        "label_rate",
                        999,
                        "stock_on_hand",
                        5
                    )
                )
            );
        when(booksClient.listItems(2)).thenReturn(List.of());
        when(booksClient.getItem("zoho-var"))
            .thenReturn(
                Map.of(
                    "item",
                    Map.of(
                        "sku",
                        "HORIZON-S-NAVY",
                        "item_id",
                        "zoho-var",
                        "rate",
                        799.20,
                        "label_rate",
                        999,
                        "stock_on_hand",
                        5
                    )
                )
            );
        when(productRepository.findAll()).thenReturn(List.of(parent));
        when(productRepository.findAllByZohoItemIdIsNotNull()).thenReturn(List.of());
        when(productRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.pullProducts();

        verify(productRepository, atLeastOnce()).save(argThat(saved ->
            saved.getVariantsJson().contains("zoho-var") &&
            Integer.valueOf(5).equals(saved.getStock()) &&
            saved.getVariantsJson().contains("\"stock\":5")
        ));
    }

    @Test
    void syncOrder_variants_usesVariantZohoItemId() {
        ProductVariantSupport realVariantSupport = StorePricingTestSupport.variantSupport(objectMapper);
        service =
            new ZohoSyncService(
                booksClient,
                objectMapper,
                applicationProperties,
                itemImageSync,
                productFieldMapper,
                descriptionCodec,
                realVariantSupport,
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

        Product product = new Product()
            .id(40L)
            .sku("HORIZON")
            .itemStructure("VARIANTS")
            .variantsJson(
                """
                [{"sku":"HORIZON-S-NAVY","size":"S","color":"Navy","rate":799.20,"mrp":999,"stock":3,"active":true,"zohoItemId":"zoho-var-line"}]
                """
            );
        OrderItem line = new OrderItem()
            .productId("40")
            .productName("Horizon Set")
            .variantSku("HORIZON-S-NAVY")
            .itemSize("S")
            .color("Navy")
            .qty(1)
            .price(BigDecimal.valueOf(799.20));
        CustomerOrder order = new CustomerOrder().id(200L).orderNumber("MNM-VAR-1").userLogin("buyer@example.com");
        order.addItems(line);
        line.setOrder(order);

        when(customerOrderRepository.findById(200L)).thenReturn(Optional.of(order));
        when(orderItemRepository.findAllByOrderId(200L)).thenReturn(List.of(line));
        when(booksClient.findContactByEmail("buyer@example.com")).thenReturn(Map.of("contact_id", "cust-1"));
        when(booksClient.createSalesOrder(any())).thenReturn(Map.of("salesorder", Map.of("salesorder_id", "so-var")));
        when(booksClient.createInvoiceFromSalesOrder("so-var")).thenReturn(Map.of("invoice", Map.of("invoice_id", "inv-var")));
        when(productRepository.findById(40L)).thenReturn(Optional.of(product));

        service.syncOrder(200L);

        verify(booksClient).createSalesOrder(
            argThat(payload -> {
                Object linesObj = payload.get("line_items");
                if (!(linesObj instanceof List<?> lines) || lines.isEmpty()) {
                    return false;
                }
                Object lineObj = lines.get(0);
                if (!(lineObj instanceof Map<?, ?> salesLine)) {
                    return false;
                }
                return "zoho-var-line".equals(salesLine.get("item_id"));
            })
        );
    }

    @Test
    void pullProducts_groupsZohoItemGroupIntoOneVariantsParent() {
        ProductVariantSupport realVariantSupport = StorePricingTestSupport.variantSupport(objectMapper);
        service =
            new ZohoSyncService(
                booksClient,
                objectMapper,
                applicationProperties,
                itemImageSync,
                productFieldMapper,
                descriptionCodec,
                realVariantSupport,
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

        Map<String, Object> small = groupedZohoItem("zoho-s", "HORIZON-S-NAVY", "S", "Navy", 799.20, 3);
        Map<String, Object> medium = groupedZohoItem("zoho-m", "HORIZON-M-NAVY", "M", "Navy", 959.20, 4);
        small.put("unit", "bundle");
        medium.put("unit", "bundle");
        when(zohoSettingRepository.findActiveOrLatest()).thenReturn(Optional.empty());
        when(booksClient.listItems(1)).thenReturn(List.of(small, medium));
        when(booksClient.listItems(2)).thenReturn(List.of());
        when(booksClient.getItem("zoho-s")).thenReturn(Map.of("item", small));
        when(booksClient.getItem("zoho-m")).thenReturn(Map.of("item", medium));
        when(productRepository.findAll()).thenReturn(List.of());
        when(productRepository.findAllByZohoItemIdIsNotNull()).thenReturn(List.of());
        when(productRepository.findMaxSortOrder()).thenReturn(0);
        when(productRepository.findOneBySku(any())).thenAnswer(invocation -> {
            String sku = invocation.getArgument(0);
            if ("HORIZON-SET".equals(sku)) {
                return Optional.of(new Product().id(5L).sku("HORIZON-SET"));
            }
            return Optional.empty();
        });
        when(productRepository.findOneBySlug(any())).thenReturn(Optional.empty());
        java.util.Map<String, Product> byZohoId = new java.util.HashMap<>();
        when(productRepository.findOneByZohoItemId(any())).thenAnswer(invocation ->
            Optional.ofNullable(byZohoId.get(invocation.getArgument(0)))
        );
        when(productRepository.save(any())).thenAnswer(invocation -> {
            Product saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(80L);
            }
            if (saved.getZohoItemId() != null) {
                byZohoId.put(saved.getZohoItemId(), saved);
            }
            return saved;
        });

        service.pullProducts();

        ArgumentCaptor<Product> saved = ArgumentCaptor.forClass(Product.class);
        verify(productRepository, org.mockito.Mockito.atLeast(2)).save(saved.capture());
        Product parent = saved
            .getAllValues()
            .stream()
            .filter(p -> "VARIANTS".equals(p.getItemStructure()))
            .reduce((a, b) -> b)
            .orElseThrow();
        assertThat(parent.getZohoItemId()).isEqualTo("grp-horizon");
        assertThat(parent.getName()).isEqualTo("Horizon Set");
        assertThat(parent.getSku()).isEqualTo("HORIZON-SET-1");
        assertThat(parent.getVariantsJson()).contains("HORIZON-S-NAVY", "HORIZON-M-NAVY", "zoho-s", "zoho-m");
        assertThat(parent.getStock()).isEqualTo(7);
        assertThat(parent.getUnits()).isEqualTo("bundle");
        assertThat(parent.getItemStructure()).isEqualTo("VARIANTS");
        assertThat(parent.getSizesJson()).contains("S", "M");
        assertThat(parent.getColorsJson()).contains("Navy");
    }

    @Test
    void pullProducts_variantGroup_pullsCustomFieldsAndEachColorImage() {
        ProductVariantSupport realVariantSupport = StorePricingTestSupport.variantSupport(objectMapper);
        service =
            new ZohoSyncService(
                booksClient,
                objectMapper,
                applicationProperties,
                itemImageSync,
                productFieldMapper,
                descriptionCodec,
                realVariantSupport,
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

        Map<String, Object> pink = groupedZohoItem("zoho-pink", "KEDAR-S-PINK", "S", "Pink", 799.20, 2);
        Map<String, Object> blue = groupedZohoItem("zoho-blue", "KEDAR-S-BLUE", "S", "Blue", 799.20, 2);
        when(zohoSettingRepository.findActiveOrLatest()).thenReturn(Optional.empty());
        when(booksClient.listItems(1)).thenReturn(List.of(pink, blue));
        when(booksClient.listItems(2)).thenReturn(List.of());
        when(booksClient.getItem("zoho-pink")).thenReturn(Map.of("item", pink));
        when(booksClient.getItem("zoho-blue")).thenReturn(Map.of("item", blue));
        when(productRepository.findAll()).thenReturn(List.of());
        when(productRepository.findAllByZohoItemIdIsNotNull()).thenReturn(List.of());
        when(productRepository.findMaxSortOrder()).thenReturn(0);
        when(productRepository.findOneBySku(any())).thenReturn(Optional.empty());
        when(productRepository.findOneBySlug(any())).thenReturn(Optional.empty());
        java.util.Map<String, Product> byZohoId = new java.util.HashMap<>();
        when(productRepository.findOneByZohoItemId(any())).thenAnswer(invocation ->
            Optional.ofNullable(byZohoId.get(invocation.getArgument(0)))
        );
        when(productRepository.save(any())).thenAnswer(invocation -> {
            Product saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(81L);
            }
            if (saved.getZohoItemId() != null) {
                byZohoId.put(saved.getZohoItemId(), saved);
            }
            return saved;
        });
        when(itemImageSync.isEnabled()).thenReturn(true);
        when(productFieldMapper.applyPulledItem(any(), any(), anyBoolean())).thenReturn(Optional.empty());

        service.pullProducts();

        verify(productFieldMapper, times(2)).applyPulledItem(any(Product.class), any(), anyBoolean());
        verify(itemImageSync).importZohoVariantColorImage(any(Product.class), eq("Pink"), eq("zoho-pink"), any());
        verify(itemImageSync).importZohoVariantColorImage(any(Product.class), eq("Blue"), eq("zoho-blue"), any());
    }

    @Test
    void pullProducts_booksVariantNames_mergeIntoOneAdminParent() {
        ProductVariantSupport realVariantSupport = StorePricingTestSupport.variantSupport(objectMapper);
        service =
            new ZohoSyncService(
                booksClient,
                objectMapper,
                applicationProperties,
                itemImageSync,
                productFieldMapper,
                descriptionCodec,
                realVariantSupport,
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

        Map<String, Object> small = booksVariantItem("zoho-s", "HORIZON-S-NAVY", "S", "Navy", 799.20, 3);
        Map<String, Object> medium = booksVariantItem("zoho-m", "HORIZON-M-NAVY", "M", "Navy", 959.20, 4);
        when(zohoSettingRepository.findActiveOrLatest()).thenReturn(Optional.empty());
        when(booksClient.listItems(1)).thenReturn(List.of(small, medium));
        when(booksClient.listItems(2)).thenReturn(List.of());
        when(booksClient.getItem("zoho-s")).thenReturn(Map.of("item", small));
        when(booksClient.getItem("zoho-m")).thenReturn(Map.of("item", medium));
        when(productRepository.findAll()).thenReturn(List.of());
        when(productRepository.findAllByZohoItemIdIsNotNull()).thenReturn(List.of());
        when(productRepository.findMaxSortOrder()).thenReturn(0);
        when(productRepository.findOneBySku(any())).thenReturn(Optional.empty());
        when(productRepository.findOneBySlug(any())).thenReturn(Optional.empty());
        when(productRepository.save(any())).thenAnswer(invocation -> {
            Product saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(81L);
            }
            return saved;
        });

        service.pullProducts();

        ArgumentCaptor<Product> saved = ArgumentCaptor.forClass(Product.class);
        verify(productRepository, org.mockito.Mockito.atLeast(2)).save(saved.capture());
        Product parent = saved
            .getAllValues()
            .stream()
            .filter(p -> "VARIANTS".equals(p.getItemStructure()))
            .reduce((a, b) -> b)
            .orElseThrow();
        assertThat(parent.getName()).isEqualTo("Horizon Set");
        assertThat(parent.getSku()).isEqualTo("HORIZON-SET");
        assertThat(parent.getItemStructure()).isEqualTo("VARIANTS");
        assertThat(parent.getVariantsJson()).contains("HORIZON-S-NAVY", "HORIZON-M-NAVY", "\"size\":\"S\"", "\"size\":\"M\"", "Navy");
        assertThat(parent.getSizesJson()).contains("S", "M");
        assertThat(parent.getColorsJson()).contains("Navy");
        assertThat(parent.getStock()).isEqualTo(7);
    }

    @Test
    void pullProducts_skipsInactiveZohoItemsAndDoesNotImportThem() {
        ProductVariantSupport realVariantSupport = StorePricingTestSupport.variantSupport(objectMapper);
        service =
            new ZohoSyncService(
                booksClient,
                objectMapper,
                applicationProperties,
                itemImageSync,
                productFieldMapper,
                descriptionCodec,
                realVariantSupport,
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

        Map<String, Object> inactive = new java.util.LinkedHashMap<>(
            groupedZohoItem("zoho-dead", "HORIZON-S-PINK", "S", "Pink", 100, 9)
        );
        inactive.put("status", "inactive");
        when(zohoSettingRepository.findActiveOrLatest()).thenReturn(Optional.empty());
        when(booksClient.listItems(1)).thenReturn(List.of(inactive));
        when(booksClient.listItems(2)).thenReturn(List.of());
        when(productRepository.findAll()).thenReturn(List.of());
        when(booksClient.getItem("zoho-dead")).thenReturn(Map.of("item", inactive));

        Map<String, Object> result = service.pullProducts();

        verify(booksClient).getItem("zoho-dead");
        verify(productRepository, never()).save(any());
        assertThat(result.get("notices").toString()).doesNotContain("inactive");
    }

    @Test
    void pullProducts_fullPull_deactivatesMatrixRowsMissingFromActiveCatalog() {
        ProductVariantSupport realVariantSupport = StorePricingTestSupport.variantSupport(objectMapper);
        service =
            new ZohoSyncService(
                booksClient,
                objectMapper,
                applicationProperties,
                itemImageSync,
                productFieldMapper,
                descriptionCodec,
                realVariantSupport,
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

        Product parent = new Product()
            .id(120L)
            .sku("HORIZON")
            .name("Horizon Set")
            .zohoItemId("grp-horizon")
            .itemStructure("VARIANTS")
            .active(true)
            .variantsJson(
                """
                [{"sku":"HORIZON-S-NAVY","size":"S","color":"Navy","rate":799.20,"stock":3,"active":true,"zohoItemId":"zoho-s"},\
                {"sku":"HORIZON-S-PINK","size":"S","color":"Pink","rate":100,"stock":9,"active":true,"zohoItemId":"zoho-pink"}]
                """
            );
        Map<String, Object> activeOnly = groupedZohoItem("zoho-s", "HORIZON-S-NAVY", "S", "Navy", 799.20, 3);
        when(zohoSettingRepository.findActiveOrLatest()).thenReturn(Optional.empty());
        when(booksClient.listItems(1)).thenReturn(List.of(activeOnly));
        when(booksClient.listItems(2)).thenReturn(List.of());
        when(booksClient.getItem("zoho-s")).thenReturn(Map.of("item", activeOnly));
        when(productRepository.findAll()).thenReturn(List.of(parent));
        when(productRepository.findAllByZohoItemIdIsNotNull()).thenReturn(List.of());
        when(productRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(productFieldMapper.applyPulledItem(any(), any(), anyBoolean())).thenReturn(Optional.empty());

        service.pullProducts();

        ArgumentCaptor<Product> saved = ArgumentCaptor.forClass(Product.class);
        verify(productRepository, atLeastOnce()).save(saved.capture());
        Product last = saved
            .getAllValues()
            .stream()
            .filter(p -> p.getId() != null && p.getId().equals(120L))
            .reduce((a, b) -> b)
            .orElseThrow();
        assertThat(last.getVariantsJson()).contains("\"active\":false");
        assertThat(last.getVariantsJson()).contains("HORIZON-S-PINK");
        assertThat(realVariantSupport.findBySku(realVariantSupport.parseProduct(last), "HORIZON-S-NAVY").orElseThrow().active())
            .isTrue();
        assertThat(realVariantSupport.findBySku(realVariantSupport.parseProduct(last), "HORIZON-S-PINK").orElseThrow().active())
            .isFalse();
    }

    @Test
    void pullProducts_skipsVariantWhenSizeAndColorCannotBeParsed() {
        ProductVariantSupport realVariantSupport = StorePricingTestSupport.variantSupport(objectMapper);
        service =
            new ZohoSyncService(
                booksClient,
                objectMapper,
                applicationProperties,
                itemImageSync,
                productFieldMapper,
                descriptionCodec,
                realVariantSupport,
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

        Map<String, Object> unparseable = new java.util.LinkedHashMap<>();
        unparseable.put("item_id", "zoho-bad");
        unparseable.put("sku", "BAD-VARIANT-SKU");
        unparseable.put("name", "Mystery Item");
        unparseable.put("group_id", "grp-mystery");
        unparseable.put("group_name", "Mystery Item");
        unparseable.put("rate", 50);
        unparseable.put("stock_on_hand", 2);
        when(zohoSettingRepository.findActiveOrLatest()).thenReturn(Optional.empty());
        when(booksClient.listItems(1)).thenReturn(List.of(unparseable));
        when(booksClient.listItems(2)).thenReturn(List.of());
        when(booksClient.getItem("zoho-bad")).thenReturn(Map.of("item", unparseable));
        when(productRepository.findAll()).thenReturn(List.of());
        when(productRepository.findAllByZohoItemIdIsNotNull()).thenReturn(List.of());
        when(productRepository.findOneByZohoItemId(any())).thenReturn(Optional.empty());

        Map<String, Object> result = service.pullProducts();

        verify(productRepository, never()).save(any());
        assertThat(result.get("notices").toString()).containsIgnoringCase("missing size/color");
    }

    @Test
    void pullProducts_emptyFullCatalogue_deactivatesLinkedProductsButPreservesLocalOnlyVariants() {
        ProductVariantSupport realVariantSupport = StorePricingTestSupport.variantSupport(objectMapper);
        service =
            new ZohoSyncService(
                booksClient,
                objectMapper,
                applicationProperties,
                itemImageSync,
                productFieldMapper,
                descriptionCodec,
                realVariantSupport,
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
        Product linkedSimple = new Product().id(301L).sku("GONE").zohoItemId("zoho-gone").active(true);
        Product linkedVariants = new Product()
            .id(302L)
            .sku("LINKED-PARENT")
            .itemStructure("VARIANTS")
            .active(true)
            .variantsJson(
                """
                [{"sku":"LINKED-ROW","size":"S","color":"Blue","rate":10,"stock":2,"active":true,"zohoItemId":"zoho-row"}]
                """
            );
        Product localOnlyVariants = new Product()
            .id(303L)
            .sku("LOCAL-PARENT")
            .itemStructure("VARIANTS")
            .active(true)
            .variantsJson(
                """
                [{"sku":"LOCAL-ROW","size":"S","color":"Pink","rate":10,"stock":3,"active":true}]
                """
            );
        when(zohoSettingRepository.findActiveOrLatest()).thenReturn(Optional.empty());
        when(booksClient.listItems(1)).thenReturn(List.of());
        when(productRepository.findAll()).thenReturn(List.of(linkedVariants, localOnlyVariants));
        when(productRepository.findAllByZohoItemIdIsNotNull()).thenReturn(List.of(linkedSimple));
        when(productRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.pullProducts(true);

        assertThat(linkedSimple.getActive()).isFalse();
        assertThat(linkedVariants.getActive()).isFalse();
        assertThat(realVariantSupport.parseProduct(linkedVariants).get(0).active()).isFalse();
        assertThat(localOnlyVariants.getActive()).isTrue();
        assertThat(realVariantSupport.parseProduct(localOnlyVariants).get(0).active()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void pullProducts_incrementalInactiveVariant_deactivatesRowAndParent(boolean staleTimestamp) {
        ProductVariantSupport realVariantSupport = StorePricingTestSupport.variantSupport(objectMapper);
        service =
            new ZohoSyncService(
                booksClient,
                objectMapper,
                applicationProperties,
                itemImageSync,
                productFieldMapper,
                descriptionCodec,
                realVariantSupport,
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
        Instant cursor = Instant.parse("2026-08-27T01:02:03Z");
        ZohoSetting setting = new ZohoSetting().connected(true).lastProductPullAt(cursor);
        Product parent = new Product()
            .id(304L)
            .sku("PARENT")
            .itemStructure("VARIANTS")
            .active(true)
            .variantsJson(
                """
                [{"sku":"OLD-SKU","size":"S","color":"Blue","rate":10,"stock":2,"active":true,"zohoItemId":"zoho-stable"}]
                """
            );
        java.util.LinkedHashMap<String, Object> inactive = new java.util.LinkedHashMap<>();
        inactive.put("sku", "RENAMED-SKU");
        inactive.put("item_id", "zoho-stable");
        inactive.put("status", "inactive");
        if (staleTimestamp) {
            inactive.put("last_modified_time", "2026-08-26T12:00:00Z");
        }
        when(zohoSettingRepository.findActiveOrLatest()).thenReturn(Optional.of(setting));
        when(booksClient.listAllStatusItems(1)).thenReturn(List.of(inactive));
        when(booksClient.listAllStatusItems(2)).thenReturn(List.of());
        when(productRepository.findAll()).thenReturn(List.of(parent));
        when(productRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(booksClient.getItem("zoho-stable")).thenReturn(Map.of("item", inactive));

        Map<String, Object> result = service.pullProducts(false);

        assertThat(result.get("failed")).isEqualTo(0);
        assertThat(parent.getActive()).isFalse();
        assertThat(realVariantSupport.findByZohoItemId(realVariantSupport.parseProduct(parent), "zoho-stable").orElseThrow().active())
            .isFalse();
        verify(booksClient).listAllStatusItems(1);
        verify(booksClient).getItem("zoho-stable");
        assertThat(setting.getLastProductPullAt()).isAfter(cursor);
    }

    @Test
    void pullProducts_incrementalInactiveSimpleProduct_stillDeactivates() {
        Instant cursor = Instant.parse("2026-08-27T01:02:03Z");
        ZohoSetting setting = new ZohoSetting().connected(true).lastProductPullAt(cursor);
        Product simple = new Product()
            .id(306L)
            .sku("SWADDLE")
            .zohoItemId("zoho-simple")
            .itemStructure("SIMPLE")
            .active(true);
        Map<String, Object> inactive = Map.of(
            "sku",
            "SWADDLE",
            "item_id",
            "zoho-simple",
            "status",
            "inactive",
            "last_modified_time",
            "2026-08-26T12:00:00Z"
        );
        when(zohoSettingRepository.findActiveOrLatest()).thenReturn(Optional.of(setting));
        when(booksClient.listAllStatusItems(1)).thenReturn(List.of(inactive));
        when(booksClient.listAllStatusItems(2)).thenReturn(List.of());
        when(productRepository.findAll()).thenReturn(List.of());
        when(productRepository.findOneByZohoItemId("zoho-simple")).thenReturn(Optional.of(simple));
        when(variantSupport.isVariants(simple)).thenReturn(false);
        when(productRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(booksClient.getItem("zoho-simple")).thenReturn(Map.of("item", inactive));

        Map<String, Object> result = service.pullProducts(false);

        assertThat(result.get("failed")).isEqualTo(0);
        assertThat(simple.getActive()).isFalse();
        verify(booksClient).getItem("zoho-simple");
        assertThat(setting.getLastProductPullAt()).isAfter(cursor);
    }

    @Test
    void pullProducts_incremental_staleInactiveWithoutLocalProduct_verifiesBeforeSkipping() {
        Instant cursor = Instant.parse("2026-08-27T01:02:03Z");
        ZohoSetting setting = new ZohoSetting().connected(true).lastProductPullAt(cursor);
        Map<String, Object> staleInactive = Map.of(
            "sku",
            "GHOST-SKU",
            "item_id",
            "zoho-ghost",
            "status",
            "inactive",
            "last_modified_time",
            "2026-08-26T12:00:00Z"
        );
        when(zohoSettingRepository.findActiveOrLatest()).thenReturn(Optional.of(setting));
        when(booksClient.listAllStatusItems(1)).thenReturn(List.of(staleInactive));
        when(booksClient.listAllStatusItems(2)).thenReturn(List.of());
        when(productRepository.findAll()).thenReturn(List.of());
        when(booksClient.getItem("zoho-ghost")).thenReturn(Map.of("item", staleInactive));

        Map<String, Object> result = service.pullProducts(false);

        assertThat(result.get("failed")).isEqualTo(0);
        verify(booksClient).getItem("zoho-ghost");
        verify(productRepository, never()).save(any());
        assertThat(result.get("notices").toString()).doesNotContain("GHOST-SKU");
        assertThat(setting.getLastProductPullAt()).isAfter(cursor);
    }

    @Test
    void pullProducts_inactiveListWithEmptyDetail_doesNotDeactivateOrAdvanceCursor() {
        Instant cursor = Instant.parse("2026-08-27T01:02:03Z");
        ZohoSetting setting = new ZohoSetting().connected(true).lastProductPullAt(cursor);
        Product local = new Product().id(307L).sku("SAFE-SKU").zohoItemId("zoho-safe").active(true);
        Map<String, Object> inactive = Map.of(
            "sku",
            "SAFE-SKU",
            "item_id",
            "zoho-safe",
            "status",
            "inactive",
            "last_modified_time",
            "2026-08-28T12:00:00Z"
        );
        when(zohoSettingRepository.findActiveOrLatest()).thenReturn(Optional.of(setting));
        when(booksClient.listAllStatusItems(1)).thenReturn(List.of(inactive));
        when(booksClient.listAllStatusItems(2)).thenReturn(List.of());
        when(productRepository.findAll()).thenReturn(List.of());
        when(booksClient.getItem("zoho-safe")).thenReturn(Map.of());

        Map<String, Object> result = service.pullProducts(false);

        assertThat(result.get("failed")).isEqualTo(1);
        assertThat(result.get("failures").toString()).contains("local data was not changed");
        assertThat(local.getActive()).isTrue();
        assertThat(setting.getLastProductPullAt()).isEqualTo(cursor);
        verify(productRepository, never()).save(any());
        verify(zohoSettingRepository, never()).save(any());
    }

    @Test
    void pullProducts_listInactiveButDetailActive_importsProduct() {
        ProductVariantSupport realVariantSupport = StorePricingTestSupport.variantSupport(objectMapper);
        service =
            new ZohoSyncService(
                booksClient,
                objectMapper,
                applicationProperties,
                itemImageSync,
                productFieldMapper,
                descriptionCodec,
                realVariantSupport,
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
        Map<String, Object> listed = new java.util.LinkedHashMap<>();
        listed.put("item_id", "zoho-live");
        listed.put("sku", "SWADDLE-AQUA");
        listed.put("name", "Baby Swaddle Aqua");
        listed.put("status", "inactive");
        listed.put("rate", 499);
        listed.put("stock_on_hand", 5);
        Map<String, Object> detail = new java.util.LinkedHashMap<>(listed);
        detail.put("status", "active");
        when(zohoSettingRepository.findActiveOrLatest()).thenReturn(Optional.empty());
        when(booksClient.listItems(1)).thenReturn(List.of(listed));
        when(booksClient.listItems(2)).thenReturn(List.of());
        when(productRepository.findAll()).thenReturn(List.of());
        when(productRepository.findOneByZohoItemId("zoho-live")).thenReturn(Optional.empty());
        when(productRepository.findOneBySku("SWADDLE-AQUA")).thenReturn(Optional.empty());
        when(productRepository.findMaxSortOrder()).thenReturn(0);
        when(productRepository.findOneBySlug(any())).thenReturn(Optional.empty());
        when(productRepository.save(any())).thenAnswer(invocation -> {
            Product saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(501L);
            }
            return saved;
        });
        when(booksClient.getItem("zoho-live")).thenReturn(Map.of("item", detail));
        when(productFieldMapper.applyPulledItem(any(), any(), anyBoolean())).thenReturn(Optional.empty());

        Map<String, Object> result = service.pullProducts();

        assertThat(result.get("failed")).isEqualTo(0);
        verify(booksClient, times(1)).getItem("zoho-live");
        ArgumentCaptor<Product> saved = ArgumentCaptor.forClass(Product.class);
        verify(productRepository, atLeastOnce()).save(saved.capture());
        Product recreated = saved.getAllValues().get(saved.getAllValues().size() - 1);
        assertThat(recreated.getZohoItemId()).isEqualTo("zoho-live");
        assertThat(recreated.getSku()).isEqualTo("SWADDLE-AQUA");
        assertThat(recreated.getActive()).isTrue();
        assertThat(recreated.getZohoSyncError()).isNull();
        assertThat(result.get("notices").toString()).doesNotContain("inactive");
    }

    @Test
    void pullProducts_incremental_adminDeletedProductWithStaleInactiveList_recreatesActiveProduct() {
        ProductVariantSupport realVariantSupport = StorePricingTestSupport.variantSupport(objectMapper);
        service =
            new ZohoSyncService(
                booksClient,
                objectMapper,
                applicationProperties,
                itemImageSync,
                productFieldMapper,
                descriptionCodec,
                realVariantSupport,
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
        Instant cursor = Instant.parse("2026-08-27T01:02:03Z");
        ZohoSetting setting = new ZohoSetting().connected(true).lastProductPullAt(cursor);
        Map<String, Object> listed = new java.util.LinkedHashMap<>();
        listed.put("item_id", "zoho-deleted-locally");
        listed.put("sku", "RESTORE-ME");
        listed.put("name", "Restore Me");
        listed.put("status", "inactive");
        listed.put("rate", 699);
        listed.put("stock_on_hand", 8);
        listed.put("last_modified_time", "2026-08-26T12:00:00Z");
        Map<String, Object> detail = new java.util.LinkedHashMap<>(listed);
        detail.put("status", "active");
        when(zohoSettingRepository.findActiveOrLatest()).thenReturn(Optional.of(setting));
        when(booksClient.listAllStatusItems(1)).thenReturn(List.of(listed));
        when(booksClient.listAllStatusItems(2)).thenReturn(List.of());
        when(booksClient.getItem("zoho-deleted-locally")).thenReturn(Map.of("item", detail));
        when(productRepository.findAll()).thenReturn(List.of());
        when(productRepository.findOneByZohoItemId("zoho-deleted-locally")).thenReturn(Optional.empty());
        when(productRepository.findOneBySku("RESTORE-ME")).thenReturn(Optional.empty());
        when(productRepository.findMaxSortOrder()).thenReturn(0);
        when(productRepository.findOneBySlug(any())).thenReturn(Optional.empty());
        when(productRepository.save(any())).thenAnswer(invocation -> {
            Product saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(502L);
            }
            return saved;
        });
        when(productFieldMapper.applyPulledItem(any(), any(), anyBoolean())).thenReturn(Optional.empty());

        Map<String, Object> result = service.pullProducts(false);

        assertThat(result.get("failed")).isEqualTo(0);
        ArgumentCaptor<Product> saved = ArgumentCaptor.forClass(Product.class);
        verify(productRepository, atLeastOnce()).save(saved.capture());
        Product recreated = saved.getAllValues().get(saved.getAllValues().size() - 1);
        assertThat(recreated.getSku()).isEqualTo("RESTORE-ME");
        assertThat(recreated.getZohoItemId()).isEqualTo("zoho-deleted-locally");
        assertThat(recreated.getActive()).isTrue();
        assertThat(recreated.getZohoSyncError()).isNull();
        verify(booksClient, times(1)).getItem("zoho-deleted-locally");
        assertThat(setting.getLastProductPullAt()).isAfter(cursor);
    }

    @Test
    void pullProducts_incremental_staleInactiveListButDetailActive_reimportsProduct() {
        Instant cursor = Instant.parse("2026-08-27T01:02:03Z");
        ZohoSetting setting = new ZohoSetting().connected(true).lastProductPullAt(cursor);
        Product simple = new Product()
            .id(306L)
            .sku("SWADDLE")
            .zohoItemId("zoho-simple")
            .itemStructure("SIMPLE")
            .active(false)
            .zohoSyncError("Zoho item marked inactive");
        Map<String, Object> listed = new java.util.LinkedHashMap<>();
        listed.put("sku", "SWADDLE");
        listed.put("item_id", "zoho-simple");
        listed.put("status", "inactive");
        listed.put("name", "Baby Swaddle");
        listed.put("rate", 499);
        listed.put("last_modified_time", "2026-08-26T12:00:00Z");
        Map<String, Object> detail = new java.util.LinkedHashMap<>(listed);
        detail.put("status", "active");
        when(zohoSettingRepository.findActiveOrLatest()).thenReturn(Optional.of(setting));
        when(booksClient.listAllStatusItems(1)).thenReturn(List.of(listed));
        when(booksClient.listAllStatusItems(2)).thenReturn(List.of());
        when(productRepository.findAll()).thenReturn(List.of());
        when(productRepository.findOneByZohoItemId("zoho-simple")).thenReturn(Optional.of(simple));
        when(variantSupport.isVariants(simple)).thenReturn(false);
        when(productRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(booksClient.getItem("zoho-simple")).thenReturn(Map.of("item", detail));
        when(productFieldMapper.applyPulledItem(any(), any(), anyBoolean())).thenReturn(Optional.empty());

        Map<String, Object> result = service.pullProducts(false);

        assertThat(result.get("failed")).isEqualTo(0);
        assertThat(simple.getActive()).isTrue();
        assertThat(simple.getZohoSyncError()).isNull();
        verify(booksClient, times(1)).getItem("zoho-simple");
        assertThat(setting.getLastProductPullAt()).isAfter(cursor);
    }

    @Test
    void pullProducts_incremental_skipsUnchangedSkuLessItemsWithoutFailure() {
        Instant cursor = Instant.parse("2026-08-27T01:02:03Z");
        ZohoSetting setting = new ZohoSetting().connected(true).lastProductPullAt(cursor);
        Map<String, Object> unchangedNoSku = Map.of(
            "item_id",
            "zoho-legacy",
            "status",
            "active",
            "last_modified_time",
            "2026-08-26T12:00:00Z"
        );
        when(zohoSettingRepository.findActiveOrLatest()).thenReturn(Optional.of(setting));
        when(booksClient.listAllStatusItems(1)).thenReturn(List.of(unchangedNoSku));
        when(booksClient.listAllStatusItems(2)).thenReturn(List.of());
        when(productRepository.findAll()).thenReturn(List.of());

        Map<String, Object> result = service.pullProducts(false);

        assertThat(result.get("failed")).isEqualTo(0);
        verify(booksClient, never()).getItem(any());
        verify(productRepository, never()).save(any());
        assertThat(setting.getLastProductPullAt()).isAfter(cursor);
    }

    @Test
    void pullProducts_incremental_skipsItemsUnchangedSinceCursor() {
        Instant cursor = Instant.parse("2026-08-27T01:02:03Z");
        ZohoSetting setting = new ZohoSetting().connected(true).lastProductPullAt(cursor);
        Map<String, Object> unchanged = Map.of(
            "sku",
            "OLD-SKU",
            "item_id",
            "zoho-old",
            "status",
            "active",
            "last_modified_time",
            "2026-08-26T12:00:00Z"
        );
        when(zohoSettingRepository.findActiveOrLatest()).thenReturn(Optional.of(setting));
        when(booksClient.listAllStatusItems(1)).thenReturn(List.of(unchanged));
        when(booksClient.listAllStatusItems(2)).thenReturn(List.of());
        when(productRepository.findAll()).thenReturn(List.of());

        Map<String, Object> result = service.pullProducts(false);

        assertThat(result.get("failed")).isEqualTo(0);
        verify(booksClient, never()).getItem(any());
        verify(productRepository, never()).save(any());
        assertThat(setting.getLastProductPullAt()).isAfter(cursor);
    }

    @Test
    void pullProducts_incrementalActiveSkuRenameWithStaleTimestamp_isIntentionallySkipped() {
        Instant cursor = Instant.parse("2026-08-27T01:02:03Z");
        ZohoSetting setting = new ZohoSetting().connected(true).lastProductPullAt(cursor);
        Product existing = new Product()
            .id(307L)
            .sku("OLD-SKU")
            .zohoItemId("zoho-stable")
            .itemStructure("SIMPLE")
            .active(true);
        Map<String, Object> staleRename = Map.of(
            "sku",
            "RENAMED-SKU",
            "item_id",
            "zoho-stable",
            "status",
            "active",
            "last_modified_time",
            "2026-08-26T12:00:00Z"
        );
        when(zohoSettingRepository.findActiveOrLatest()).thenReturn(Optional.of(setting));
        when(booksClient.listAllStatusItems(1)).thenReturn(List.of(staleRename));
        when(booksClient.listAllStatusItems(2)).thenReturn(List.of());
        when(productRepository.findAll()).thenReturn(List.of(existing));

        Map<String, Object> result = service.pullProducts(false);

        assertThat(result.get("failed")).isEqualTo(0);
        assertThat(existing.getSku()).isEqualTo("OLD-SKU");
        verify(booksClient, never()).getItem(any());
        verify(productRepository, never()).findOneByZohoItemId(any());
        verify(productRepository, never()).save(any());
        assertThat(setting.getLastProductPullAt()).isAfter(cursor);
    }

    @Test
    void pullProducts_variantSkuRename_updatesExistingRowAndReactivatesParent() {
        ProductVariantSupport realVariantSupport = StorePricingTestSupport.variantSupport(objectMapper);
        service =
            new ZohoSyncService(
                booksClient,
                objectMapper,
                applicationProperties,
                itemImageSync,
                productFieldMapper,
                descriptionCodec,
                realVariantSupport,
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
        Product parent = new Product()
            .id(305L)
            .sku("HORIZON")
            .name("Horizon Set")
            .zohoItemId("grp-horizon")
            .itemStructure("VARIANTS")
            .active(false)
            .variantsJson(
                """
                [{"sku":"OLD-SKU","size":"S","color":"Navy","rate":100,"stock":0,"active":false,"zohoItemId":"zoho-stable"}]
                """
            );
        Map<String, Object> renamed = groupedZohoItem("zoho-stable", "NEW-SKU", "S", "Navy", 0, 4);
        when(zohoSettingRepository.findActiveOrLatest()).thenReturn(Optional.empty());
        when(booksClient.listItems(1)).thenReturn(List.of(renamed));
        when(booksClient.listItems(2)).thenReturn(List.of());
        when(booksClient.getItem("zoho-stable")).thenReturn(Map.of("item", renamed));
        when(productRepository.findAll()).thenReturn(List.of(parent));
        when(productRepository.findAllByZohoItemIdIsNotNull()).thenReturn(List.of());
        when(productRepository.findOneBySku("NEW-SKU")).thenReturn(Optional.empty());
        when(productRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.pullProducts(true);

        assertThat(parent.getActive()).isTrue();
        assertThat(realVariantSupport.parseProduct(parent)).hasSize(1);
        ProductVariantSupport.VariantRow row = realVariantSupport
            .findByZohoItemId(realVariantSupport.parseProduct(parent), "zoho-stable")
            .orElseThrow();
        assertThat(row.sku()).isEqualTo("NEW-SKU");
        assertThat(row.rate()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(row.stock()).isEqualTo(4);
        assertThat(row.active()).isTrue();
    }

    @Test
    void pullProducts_inactiveListDetail404_doesNotDeactivateOrAdvanceCursor() {
        Instant cursor = Instant.parse("2026-08-27T01:02:03Z");
        ZohoSetting setting = new ZohoSetting().connected(true).lastProductPullAt(cursor);
        Product local = new Product().id(308L).sku("KEEP-ME").zohoItemId("zoho-gone").active(true);
        Map<String, Object> inactive = Map.of(
            "sku",
            "KEEP-ME",
            "item_id",
            "zoho-gone",
            "status",
            "inactive",
            "last_modified_time",
            "2026-08-26T12:00:00Z"
        );
        when(zohoSettingRepository.findActiveOrLatest()).thenReturn(Optional.of(setting));
        when(booksClient.listAllStatusItems(1)).thenReturn(List.of(inactive));
        when(booksClient.listAllStatusItems(2)).thenReturn(List.of());
        when(productRepository.findAll()).thenReturn(List.of());
        when(booksClient.getItem("zoho-gone"))
            .thenThrow(new RestClientResponseException("Not found", 404, "Not Found", null, null, null));

        Map<String, Object> result = service.pullProducts(false);

        assertThat(result.get("failed")).isEqualTo(1);
        assertThat(local.getActive()).isTrue();
        assertThat(setting.getLastProductPullAt()).isEqualTo(cursor);
        verify(productRepository, never()).save(any());
        verify(zohoSettingRepository, never()).save(any());
    }

    @Test
    void pullProducts_inactiveListDetailResourceAccessException_stopsPullWithoutAdvancingCursor() {
        Instant cursor = Instant.parse("2026-08-27T01:02:03Z");
        ZohoSetting setting = new ZohoSetting().connected(true).lastProductPullAt(cursor);
        Map<String, Object> inactive = Map.of(
            "sku",
            "UNREACHABLE",
            "item_id",
            "zoho-down",
            "status",
            "inactive",
            "last_modified_time",
            "2026-08-28T12:00:00Z"
        );
        Map<String, Object> later = Map.of("sku", "DO-NOT-CALL", "item_id", "zoho-later", "status", "inactive");
        when(zohoSettingRepository.findActiveOrLatest()).thenReturn(Optional.of(setting));
        when(productRepository.findAll()).thenReturn(List.of());
        when(booksClient.listAllStatusItems(1)).thenReturn(List.of(inactive, later));
        when(booksClient.getItem("zoho-down")).thenThrow(new ResourceAccessException("connection refused"));

        Map<String, Object> result = service.pullProducts(false);

        assertThat(result.get("failed")).isEqualTo(1);
        assertThat(result.get("failures").toString()).contains("unreachable");
        assertThat(setting.getLastProductPullAt()).isEqualTo(cursor);
        verify(booksClient, never()).getItem("zoho-later");
        verify(booksClient, never()).listAllStatusItems(2);
        verify(zohoSettingRepository, never()).save(any());
    }

    @Test
    void pullProducts_detailFailureDoesNotAdvanceIncrementalCursor() {
        Instant cursor = Instant.parse("2026-08-27T01:02:03Z");
        ZohoSetting setting = new ZohoSetting().connected(true).lastProductPullAt(cursor);
        Map<String, Object> listed = Map.of("sku", "RETRY-ME", "item_id", "zoho-retry", "name", "Retry Me");
        Map<String, Object> later = Map.of("sku", "DO-NOT-CALL", "item_id", "zoho-later", "name", "Later Item");
        when(zohoSettingRepository.findActiveOrLatest()).thenReturn(Optional.of(setting));
        when(productRepository.findAll()).thenReturn(List.of());
        when(booksClient.listAllStatusItems(1)).thenReturn(List.of(listed, later));
        when(booksClient.getItem("zoho-retry"))
            .thenThrow(
                new RestClientResponseException(
                    "Too many requests",
                    429,
                    "Too Many Requests",
                    null,
                    null,
                    null
                )
            );

        Map<String, Object> result = service.pullProducts(false);

        assertThat(result.get("failed")).isEqualTo(1);
        assertThat(setting.getLastProductPullAt()).isEqualTo(cursor);
        verify(zohoSettingRepository, never()).save(any());
        verify(productRepository, never()).save(any());
        verify(booksClient, never()).getItem("zoho-later");
        verify(booksClient, never()).listAllStatusItems(2);
    }

    @Test
    void pullProduct_byZohoItemId_allowsRemoteSkuRename() {
        Product product = new Product().id(700L).sku("OLD-SKU").name("Old Name").zohoItemId("zoho-700").active(true);
        Map<String, Object> detail = Map.of(
            "sku",
            "NEW-SKU",
            "item_id",
            "zoho-700",
            "name",
            "New Name",
            "status",
            "active",
            "rate",
            499,
            "stock_on_hand",
            7
        );
        when(productRepository.findById(700L)).thenReturn(Optional.of(product));
        when(zohoSettingRepository.findActiveOrLatest()).thenReturn(Optional.empty());
        when(productRepository.findAll()).thenReturn(List.of());
        when(booksClient.getItem("zoho-700")).thenReturn(Map.of("item", detail));
        when(productRepository.findOneByZohoItemId("zoho-700")).thenReturn(Optional.of(product));
        when(productRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Map<String, Object> result = service.pullProduct(700L);

        assertThat(result.get("success")).isEqualTo(1);
        assertThat(result.get("failed")).isEqualTo(0);
        assertThat(product.getSku()).isEqualTo("NEW-SKU");
        assertThat(product.getName()).isEqualTo("New Name");
        assertThat(product.getStock()).isEqualTo(7);
        verify(booksClient, never()).findItemBySku(any());
        verify(productRepository, never()).findAllByZohoItemIdIsNotNull();
        verify(zohoSettingRepository, never()).save(any());
    }

    @Test
    void pullProduct_withoutZohoItemId_fallsBackToSkuLookup() {
        Product product = new Product().id(701L).sku("SKU-701").name("Local Name").active(true);
        Map<String, Object> listRow = Map.of("sku", "SKU-701", "item_id", "zoho-701", "name", "Remote Name");
        Map<String, Object> detail = Map.of(
            "sku",
            "SKU-701",
            "item_id",
            "zoho-701",
            "name",
            "Remote Name",
            "status",
            "active",
            "rate",
            299,
            "stock_on_hand",
            3
        );
        when(productRepository.findById(701L)).thenReturn(Optional.of(product));
        when(zohoSettingRepository.findActiveOrLatest()).thenReturn(Optional.empty());
        when(productRepository.findAll()).thenReturn(List.of());
        when(booksClient.findItemBySku("SKU-701")).thenReturn(listRow);
        when(booksClient.getItem("zoho-701")).thenReturn(Map.of("item", detail));
        when(productRepository.findOneByZohoItemId("zoho-701")).thenReturn(Optional.empty());
        when(productRepository.findOneBySku("SKU-701")).thenReturn(Optional.of(product));
        when(productRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Map<String, Object> result = service.pullProduct(701L);

        assertThat(result.get("success")).isEqualTo(1);
        assertThat(result.get("failed")).isEqualTo(0);
        assertThat(product.getZohoItemId()).isEqualTo("zoho-701");
        assertThat(product.getName()).isEqualTo("Remote Name");
        assertThat(product.getStock()).isEqualTo(3);
        verify(booksClient).findItemBySku("SKU-701");
        verify(booksClient).getItem("zoho-701");
        verify(zohoSettingRepository, never()).save(any());
    }

    @Test
    void pullProduct_missingLocalProduct_returnsFailureWithoutCallingZoho() {
        when(productRepository.findById(702L)).thenReturn(Optional.empty());

        Map<String, Object> result = service.pullProduct(702L);

        assertThat(result.get("success")).isEqualTo(0);
        assertThat(result.get("failed")).isEqualTo(1);
        assertThat(result.get("failures").toString()).contains("Product not found");
        verify(booksClient, never()).getItem(any());
        verify(booksClient, never()).findItemBySku(any());
        verify(productRepository, never()).save(any());
    }

    @Test
    void pullProduct_withoutSkuOrZohoItemId_returnsValidationFailure() {
        Product product = new Product().id(703L).name("No Identity").active(true);
        when(productRepository.findById(703L)).thenReturn(Optional.of(product));
        when(zohoSettingRepository.findActiveOrLatest()).thenReturn(Optional.empty());
        when(productRepository.findAll()).thenReturn(List.of());

        Map<String, Object> result = service.pullProduct(703L);

        assertThat(result.get("success")).isEqualTo(0);
        assertThat(result.get("failed")).isEqualTo(1);
        assertThat(result.get("failures").toString()).contains("Zoho item id or SKU is required");
        verify(booksClient, never()).getItem(any());
        verify(booksClient, never()).findItemBySku(any());
        verify(productRepository, never()).save(any());
        verify(zohoSettingRepository, never()).save(any());
    }

    @Test
    void pullProduct_staleZohoItemId_fallsBackToSkuWithoutAdvancingCursor() {
        ZohoSetting setting = new ZohoSetting().connected(true).lastProductPullAt(Instant.parse("2026-08-27T01:02:03Z"));
        Product product = new Product().id(704L).sku("SKU-704").name("Local Name").zohoItemId("stale-704").active(true);
        Map<String, Object> listRow = Map.of("sku", "SKU-704", "item_id", "zoho-704", "name", "Remote Name");
        Map<String, Object> detail = Map.of(
            "sku",
            "SKU-704",
            "item_id",
            "zoho-704",
            "name",
            "Remote Name",
            "status",
            "active",
            "rate",
            399,
            "stock_on_hand",
            4
        );
        when(productRepository.findById(704L)).thenReturn(Optional.of(product));
        when(zohoSettingRepository.findActiveOrLatest()).thenReturn(Optional.of(setting));
        when(productRepository.findAll()).thenReturn(List.of());
        when(booksClient.getItem("stale-704"))
            .thenThrow(new RestClientResponseException("Not found", 404, "Not Found", null, null, null));
        when(booksClient.findItemBySku("SKU-704")).thenReturn(listRow);
        when(booksClient.getItem("zoho-704")).thenReturn(Map.of("item", detail));
        when(productRepository.findOneByZohoItemId("zoho-704")).thenReturn(Optional.empty());
        when(productRepository.findOneBySku("SKU-704")).thenReturn(Optional.of(product));
        when(productRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Map<String, Object> result = service.pullProduct(704L);

        assertThat(result.get("success")).isEqualTo(1);
        assertThat(result.get("failed")).isEqualTo(0);
        assertThat(result.get("notices").toString()).contains("retried by SKU");
        assertThat(product.getZohoItemId()).isEqualTo("zoho-704");
        assertThat(setting.getLastProductPullAt()).isEqualTo(Instant.parse("2026-08-27T01:02:03Z"));
        verify(booksClient).getItem("stale-704");
        verify(booksClient).findItemBySku("SKU-704");
        verify(booksClient).getItem("zoho-704");
        verify(zohoSettingRepository, never()).save(any());
    }

    @Test
    void pullProduct_detailIdentityMismatch_doesNotSaveOrFallback() {
        Product product = new Product().id(705L).sku("SKU-705").name("Local Name").zohoItemId("zoho-705").active(true);
        Map<String, Object> wrongDetail = Map.of("sku", "SKU-705", "item_id", "other-705", "status", "active");
        when(productRepository.findById(705L)).thenReturn(Optional.of(product));
        when(zohoSettingRepository.findActiveOrLatest()).thenReturn(Optional.empty());
        when(productRepository.findAll()).thenReturn(List.of());
        when(booksClient.getItem("zoho-705")).thenReturn(Map.of("item", wrongDetail));

        Map<String, Object> result = service.pullProduct(705L);

        assertThat(result.get("success")).isEqualTo(0);
        assertThat(result.get("failed")).isEqualTo(1);
        assertThat(result.get("failures").toString()).contains("identity mismatch");
        verify(booksClient, never()).findItemBySku(any());
        verify(productRepository, never()).save(any());
    }

    @Test
    void pullProduct_inactiveRemoteItem_deactivatesSimpleProduct() {
        Product product = new Product().id(706L).sku("SKU-706").name("Local Name").zohoItemId("zoho-706").active(true);
        Map<String, Object> inactive = Map.of("sku", "SKU-706", "item_id", "zoho-706", "status", "inactive");
        when(productRepository.findById(706L)).thenReturn(Optional.of(product));
        when(zohoSettingRepository.findActiveOrLatest()).thenReturn(Optional.empty());
        when(productRepository.findAll()).thenReturn(List.of());
        when(booksClient.getItem("zoho-706")).thenReturn(Map.of("item", inactive));
        when(productRepository.findOneByZohoItemId("zoho-706")).thenReturn(Optional.of(product));
        when(productRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Map<String, Object> result = service.pullProduct(706L);

        assertThat(result.get("success")).isEqualTo(0);
        assertThat(result.get("failed")).isEqualTo(0);
        assertThat(result.get("notices").toString()).contains("Deactivated");
        assertThat(product.getActive()).isFalse();
        verify(productRepository).save(product);
        verify(productRepository, never()).findAllByZohoItemIdIsNotNull();
    }

    @Test
    void pullProduct_variantRows_recordsBadRowsAndContinuesWithValidRows() {
        Product parent = new Product().id(707L).sku("PARENT-707").name("Parent").active(true).itemStructure("VARIANTS");
        ProductVariantSupport.VariantRow missingIdentity = new ProductVariantSupport.VariantRow(
            "row-1",
            null,
            "0-3 M",
            "Aqua",
            null,
            null,
            null,
            0,
            true,
            null,
            null,
            null,
            List.of()
        );
        ProductVariantSupport.VariantRow valid = new ProductVariantSupport.VariantRow(
            "row-2",
            "VAR-707-A",
            "0-3 M",
            "Aqua",
            null,
            null,
            null,
            0,
            true,
            null,
            "zoho-var-707-a",
            null,
            List.of()
        );
        Map<String, Object> detail = Map.of(
            "sku",
            "VAR-707-A",
            "item_id",
            "zoho-var-707-a",
            "name",
            "Parent - Aqua - 0-3 M",
            "status",
            "active",
            "attribute_name1",
            "Size",
            "attribute_option_name1",
            "0-3 M",
            "attribute_name2",
            "Color",
            "attribute_option_name2",
            "Aqua",
            "stock_on_hand",
            5,
            "rate",
            499
        );
        when(productRepository.findById(707L)).thenReturn(Optional.of(parent));
        when(zohoSettingRepository.findActiveOrLatest()).thenReturn(Optional.empty());
        when(productRepository.findAll()).thenReturn(List.of(parent));
        when(variantSupport.isVariants(parent)).thenReturn(true);
        when(variantSupport.parseProduct(parent)).thenReturn(List.of(missingIdentity, valid));
        when(booksClient.getItem("zoho-var-707-a")).thenReturn(Map.of("item", detail));
        when(variantSupport.findProductContainingVariantZohoItemId(any(), eq("zoho-var-707-a"))).thenReturn(Optional.of(parent));
        when(
            variantSupport.upsertPulledVariantFromZoho(
                eq(parent),
                eq("VAR-707-A"),
                eq("0-3 M"),
                eq("Aqua"),
                any(),
                any(),
                eq(5),
                eq("zoho-var-707-a"),
                eq(true)
            )
        )
            .thenReturn(true);
        when(productRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Map<String, Object> result = service.pullProduct(707L);

        assertThat(result.get("success")).isEqualTo(1);
        assertThat(result.get("failed")).isEqualTo(1);
        assertThat(result.get("failures").toString()).contains("Zoho item id or SKU is required");
        verify(booksClient).getItem("zoho-var-707-a");
        verify(productRepository).save(parent);
        verify(zohoSettingRepository, never()).save(any());
    }

    @Test
    void pullProductVariant_groupsSingleZohoChildIntoSelectedParent() {
        Product parent = new Product()
            .id(708L)
            .sku("PARENT-708")
            .name("Selected Parent")
            .active(true)
            .itemStructure("VARIANTS")
            .variantsJson("[]");
        Map<String, Object> detail = Map.ofEntries(
            Map.entry("sku", "PARENT-708-0-3-AQUA"),
            Map.entry("item_id", "zoho-child-708"),
            Map.entry("group_id", "zoho-group-708"),
            Map.entry("group_name", "Selected Parent"),
            Map.entry("status", "active"),
            Map.entry("attribute_name1", "Size"),
            Map.entry("attribute_option_name1", "0-3 M"),
            Map.entry("attribute_name2", "Color"),
            Map.entry("attribute_option_name2", "Aqua"),
            Map.entry("stock_on_hand", 8),
            Map.entry("rate", 599)
        );
        when(productRepository.findById(708L)).thenReturn(Optional.of(parent));
        when(zohoSettingRepository.findActiveOrLatest()).thenReturn(Optional.empty());
        when(productRepository.findAll()).thenReturn(List.of(parent));
        when(variantSupport.isVariants(parent)).thenReturn(true);
        when(booksClient.getItem("zoho-child-708")).thenReturn(Map.of("item", detail));
        when(variantSupport.findProductContainingVariantZohoItemId(any(), eq("zoho-child-708"))).thenReturn(Optional.empty());
        when(variantSupport.findProductContainingVariantSku(any(), eq("PARENT-708-0-3-AQUA"))).thenReturn(Optional.empty());
        when(productRepository.findOneByZohoItemId("zoho-group-708")).thenReturn(Optional.empty());
        when(
            variantSupport.upsertPulledVariantFromZoho(
                eq(parent),
                eq("PARENT-708-0-3-AQUA"),
                eq("0-3 M"),
                eq("Aqua"),
                any(),
                any(),
                eq(8),
                eq("zoho-child-708"),
                eq(true)
            )
        )
            .thenReturn(true);
        when(productRepository.findOneBySku("PARENT-708-0-3-AQUA")).thenReturn(Optional.empty());
        when(productRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Map<String, Object> result = service.pullProductVariant(708L, "PARENT-708-0-3-AQUA", "zoho-child-708");

        assertThat(result.get("success")).isEqualTo(1);
        assertThat(result.get("failed")).isEqualTo(0);
        assertThat(parent.getZohoItemId()).isEqualTo("zoho-group-708");
        verify(productRepository).save(parent);
        verify(zohoSettingRepository, never()).save(any());
    }

    @Test
    void pullProductVariant_refusesChildAlreadyLinkedToDifferentParent() {
        Product selected = new Product().id(709L).sku("SELECTED").name("Selected").active(true).itemStructure("VARIANTS");
        Product other = new Product().id(710L).sku("OTHER").name("Other").active(true).itemStructure("VARIANTS");
        Map<String, Object> detail = Map.of(
            "sku",
            "OTHER-S",
            "item_id",
            "zoho-child-other",
            "group_id",
            "zoho-group-other",
            "status",
            "active",
            "attribute_name1",
            "Size",
            "attribute_option_name1",
            "S",
            "attribute_name2",
            "Color",
            "attribute_option_name2",
            "Blue"
        );
        when(productRepository.findById(709L)).thenReturn(Optional.of(selected));
        when(zohoSettingRepository.findActiveOrLatest()).thenReturn(Optional.empty());
        when(productRepository.findAll()).thenReturn(List.of(selected, other));
        when(variantSupport.isVariants(selected)).thenReturn(true);
        when(variantSupport.isVariants(other)).thenReturn(true);
        when(booksClient.getItem("zoho-child-other")).thenReturn(Map.of("item", detail));
        when(variantSupport.findProductContainingVariantZohoItemId(any(), eq("zoho-child-other"))).thenReturn(Optional.of(other));

        Map<String, Object> result = service.pullProductVariant(709L, "OTHER-S", "zoho-child-other");

        assertThat(result.get("success")).isEqualTo(0);
        assertThat(result.get("failed")).isEqualTo(1);
        assertThat(result.get("failures").toString()).contains("already linked to another variant parent");
        verify(productRepository, never()).save(any());
        verify(variantSupport, never())
            .upsertPulledVariantFromZoho(any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean());
    }

    @Test
    void pullProductVariant_withoutRemoteOrLocalSizeColorFailsWithoutSaving() {
        Product parent = new Product().id(711L).sku("PARENT-711").name("Parent").active(true).itemStructure("VARIANTS");
        Map<String, Object> detail = Map.of("sku", "PLAIN-711", "item_id", "zoho-plain-711", "status", "active", "rate", 199);
        when(productRepository.findById(711L)).thenReturn(Optional.of(parent));
        when(zohoSettingRepository.findActiveOrLatest()).thenReturn(Optional.empty());
        when(productRepository.findAll()).thenReturn(List.of(parent));
        when(variantSupport.isVariants(parent)).thenReturn(true);
        when(booksClient.getItem("zoho-plain-711")).thenReturn(Map.of("item", detail));
        when(variantSupport.findProductContainingVariantZohoItemId(any(), eq("zoho-plain-711"))).thenReturn(Optional.empty());
        when(variantSupport.findProductContainingVariantSku(any(), eq("PLAIN-711"))).thenReturn(Optional.empty());
        when(variantSupport.variantHasSizeAndColor(parent, "PLAIN-711")).thenReturn(false);

        Map<String, Object> result = service.pullProductVariant(711L, "PLAIN-711", "zoho-plain-711");

        assertThat(result.get("success")).isEqualTo(0);
        assertThat(result.get("failed")).isEqualTo(1);
        assertThat(result.get("failures").toString()).contains("missing size/color");
        verify(productRepository, never()).save(any());
        verify(variantSupport, never())
            .upsertPulledVariantFromZoho(any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean());
    }

    @Test
    void pullExternalProduct_emptyAdminCreatesVariantParentThenAddsNextVariantToSameGroup() {
        ProductVariantSupport realVariantSupport = StorePricingTestSupport.variantSupport(objectMapper);
        service =
            new ZohoSyncService(
                booksClient,
                objectMapper,
                applicationProperties,
                itemImageSync,
                productFieldMapper,
                descriptionCodec,
                realVariantSupport,
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
        java.util.List<Product> localProducts = new java.util.ArrayList<>();
        long[] nextId = { 900L };
        when(zohoSettingRepository.findActiveOrLatest()).thenReturn(Optional.empty());
        when(productRepository.findAll()).thenAnswer(invocation -> List.copyOf(localProducts));
        when(productRepository.findOneBySku(any()))
            .thenAnswer(invocation -> {
                String sku = invocation.getArgument(0);
                return localProducts.stream().filter(product -> sku.equals(product.getSku())).findFirst();
            });
        when(productRepository.findOneBySlug(any()))
            .thenAnswer(invocation -> {
                String slug = invocation.getArgument(0);
                return localProducts.stream().filter(product -> slug.equals(product.getSlug())).findFirst();
            });
        when(productRepository.findOneByZohoItemId(any()))
            .thenAnswer(invocation -> {
                String zohoItemId = invocation.getArgument(0);
                return localProducts.stream().filter(product -> zohoItemId.equals(product.getZohoItemId())).findFirst();
            });
        when(productRepository.findMaxSortOrder()).thenReturn(0);
        when(productRepository.save(any()))
            .thenAnswer(invocation -> {
                Product product = invocation.getArgument(0);
                if (product.getId() == null) {
                    product.setId(nextId[0]++);
                }
                if (localProducts.stream().noneMatch(existing -> existing == product)) {
                    localProducts.add(product);
                }
                return product;
            });
        Map<String, Object> firstVariant = Map.ofEntries(
            Map.entry("sku", "EMPTY-GROUP-0-3-AQUA"),
            Map.entry("item_id", "zoho-empty-child-1"),
            Map.entry("group_id", "zoho-empty-group"),
            Map.entry("group_name", "Empty Group"),
            Map.entry("status", "active"),
            Map.entry("attribute_name1", "Size"),
            Map.entry("attribute_option_name1", "0-3 M"),
            Map.entry("attribute_name2", "Color"),
            Map.entry("attribute_option_name2", "Aqua"),
            Map.entry("stock_on_hand", 4),
            Map.entry("rate", 499)
        );
        Map<String, Object> secondVariant = Map.ofEntries(
            Map.entry("sku", "EMPTY-GROUP-3-6-AQUA"),
            Map.entry("item_id", "zoho-empty-child-2"),
            Map.entry("group_id", "zoho-empty-group"),
            Map.entry("group_name", "Empty Group"),
            Map.entry("status", "active"),
            Map.entry("attribute_name1", "Size"),
            Map.entry("attribute_option_name1", "3-6 M"),
            Map.entry("attribute_name2", "Color"),
            Map.entry("attribute_option_name2", "Aqua"),
            Map.entry("stock_on_hand", 6),
            Map.entry("rate", 549)
        );
        when(booksClient.getItem("zoho-empty-child-1")).thenReturn(Map.of("item", firstVariant));
        when(booksClient.getItem("zoho-empty-child-2")).thenReturn(Map.of("item", secondVariant));

        Map<String, Object> firstResult = service.pullExternalProduct(null, "zoho-empty-child-1");
        Map<String, Object> secondResult = service.pullExternalProduct(null, "zoho-empty-child-2");

        assertThat(firstResult.get("success")).isEqualTo(1);
        assertThat(secondResult.get("success")).isEqualTo(1);
        assertThat(localProducts).hasSize(1);
        Product parent = localProducts.get(0);
        assertThat(parent.getSku()).isEqualTo("EMPTY-GROUP");
        assertThat(parent.getName()).isEqualTo("Empty Group");
        assertThat(parent.getZohoItemId()).isEqualTo("zoho-empty-group");
        assertThat(parent.getItemStructure()).isEqualTo("VARIANTS");
        assertThat(parent.getVariantsJson()).contains("EMPTY-GROUP-0-3-AQUA", "EMPTY-GROUP-3-6-AQUA");
        assertThat(parent.getVariantsJson()).contains("zoho-empty-child-1", "zoho-empty-child-2");
        assertThat(parent.getStock()).isEqualTo(10);
        verify(zohoSettingRepository, never()).save(any());
    }

    @Test
    void pullExternalProduct_emptyAdminCreatesSimpleProductBySku() {
        Map<String, Object> listRow = Map.of("sku", "SIMPLE-EXT", "item_id", "zoho-simple-ext", "name", "Simple External");
        Map<String, Object> detail = Map.of(
            "sku",
            "SIMPLE-EXT",
            "item_id",
            "zoho-simple-ext",
            "name",
            "Simple External",
            "status",
            "active",
            "rate",
            349,
            "stock_on_hand",
            7
        );
        when(zohoSettingRepository.findActiveOrLatest()).thenReturn(Optional.empty());
        when(productRepository.findAll()).thenReturn(List.of());
        when(booksClient.findItemBySku("SIMPLE-EXT")).thenReturn(listRow);
        when(booksClient.getItem("zoho-simple-ext")).thenReturn(Map.of("item", detail));
        when(productRepository.findOneByZohoItemId("zoho-simple-ext")).thenReturn(Optional.empty());
        when(productRepository.findOneBySku("SIMPLE-EXT")).thenReturn(Optional.empty());
        when(productRepository.findMaxSortOrder()).thenReturn(4);
        ArgumentCaptor<Product> saved = ArgumentCaptor.forClass(Product.class);
        when(productRepository.save(saved.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        Map<String, Object> result = service.pullExternalProduct("SIMPLE-EXT", null);

        assertThat(result.get("success")).isEqualTo(1);
        Product product = saved.getValue();
        assertThat(product.getSku()).isEqualTo("SIMPLE-EXT");
        assertThat(product.getZohoItemId()).isEqualTo("zoho-simple-ext");
        assertThat(product.getName()).isEqualTo("Simple External");
        assertThat(product.getPrice()).isEqualByComparingTo("349");
        assertThat(product.getStock()).isEqualTo(7);
        assertThat(product.getSortOrder()).isEqualTo(5);
        assertThat(product.getZohoSyncStatus()).isEqualTo(ZohoSyncStatus.SYNCED);
        verify(zohoSettingRepository, never()).save(any());
    }

    @Test
    void pullExternalProduct_staleItemIdFallsBackToSkuAndDoesNotCreateDuplicate() {
        Product existing = new Product().id(902L).sku("FALLBACK-SKU").name("Old").active(true).zohoItemId("old-id");
        Map<String, Object> listRow = Map.of("sku", "FALLBACK-SKU", "item_id", "fresh-id", "name", "Fresh");
        Map<String, Object> detail = Map.of(
            "sku",
            "FALLBACK-SKU",
            "item_id",
            "fresh-id",
            "name",
            "Fresh",
            "status",
            "active",
            "rate",
            799,
            "stock_on_hand",
            3
        );
        when(zohoSettingRepository.findActiveOrLatest()).thenReturn(Optional.empty());
        when(productRepository.findAll()).thenReturn(List.of());
        when(booksClient.getItem("old-id"))
            .thenThrow(new RestClientResponseException("Not found", 404, "Not Found", null, null, null));
        when(booksClient.findItemBySku("FALLBACK-SKU")).thenReturn(listRow);
        when(booksClient.getItem("fresh-id")).thenReturn(Map.of("item", detail));
        when(productRepository.findOneByZohoItemId("fresh-id")).thenReturn(Optional.empty());
        when(productRepository.findOneBySku("FALLBACK-SKU")).thenReturn(Optional.of(existing));
        when(productRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Map<String, Object> result = service.pullExternalProduct("FALLBACK-SKU", "old-id");

        assertThat(result.get("success")).isEqualTo(1);
        assertThat(result.get("failed")).isEqualTo(0);
        assertThat(result.get("notices").toString()).contains("retried by SKU");
        assertThat(existing.getZohoItemId()).isEqualTo("fresh-id");
        assertThat(existing.getName()).isEqualTo("Fresh");
        verify(productRepository).save(existing);
        verify(productRepository, never()).findMaxSortOrder();
        verify(zohoSettingRepository, never()).save(any());
    }

    @Test
    void pullExternalProduct_variantWithoutSizeColorFailsWithoutCreatingParent() {
        Map<String, Object> detail = Map.of(
            "sku",
            "BROKEN-VARIANT",
            "item_id",
            "zoho-broken-variant",
            "group_id",
            "zoho-broken-group",
            "group_name",
            "Broken Group",
            "status",
            "active",
            "rate",
            499
        );
        when(zohoSettingRepository.findActiveOrLatest()).thenReturn(Optional.empty());
        when(productRepository.findAll()).thenReturn(List.of());
        when(booksClient.getItem("zoho-broken-variant")).thenReturn(Map.of("item", detail));
        when(productRepository.findOneByZohoItemId("zoho-broken-group")).thenReturn(Optional.empty());
        when(productRepository.findOneBySlug("broken-group")).thenReturn(Optional.empty());
        when(productRepository.findOneBySku("BROKEN-GROUP")).thenReturn(Optional.empty());
        when(productRepository.findOneBySku("BROKEN-VARIANT")).thenReturn(Optional.empty());

        Map<String, Object> result = service.pullExternalProduct(null, "zoho-broken-variant");

        assertThat(result.get("success")).isEqualTo(0);
        assertThat(result.get("failed")).isEqualTo(1);
        assertThat(result.get("failures").toString()).contains("missing size/color");
        verify(productRepository, never()).save(any());
        verify(zohoSettingRepository, never()).save(any());
    }

    @Test
    void pullExternalProduct_requiresSkuOrZohoItemId() {
        Map<String, Object> result = service.pullExternalProduct(" ", null);

        assertThat(result.get("success")).isEqualTo(0);
        assertThat(result.get("failed")).isEqualTo(1);
        assertThat(result.get("failures").toString()).contains("Zoho item id or SKU is required");
        verify(booksClient, never()).getItem(any());
        verify(booksClient, never()).findItemBySku(any());
        verify(productRepository, never()).save(any());
    }

    @Test
    void pullProducts_missingItemId_doesNotCreateUnlinkedProductOrAdvanceCursor() {
        Instant cursor = Instant.parse("2026-08-27T01:02:03Z");
        ZohoSetting setting = new ZohoSetting().connected(true).lastProductPullAt(cursor);
        Map<String, Object> missingId = Map.of(
            "sku",
            "NO-ID",
            "name",
            "Missing Identity",
            "status",
            "active",
            "last_modified_time",
            "2026-08-28T12:00:00Z"
        );
        when(zohoSettingRepository.findActiveOrLatest()).thenReturn(Optional.of(setting));
        when(productRepository.findAll()).thenReturn(List.of());
        when(booksClient.listAllStatusItems(1)).thenReturn(List.of(missingId));
        when(booksClient.listAllStatusItems(2)).thenReturn(List.of());

        Map<String, Object> result = service.pullProducts(false);

        assertThat(result.get("failed")).isEqualTo(1);
        assertThat(result.get("failures").toString()).contains("no item_id");
        assertThat(setting.getLastProductPullAt()).isEqualTo(cursor);
        verify(booksClient, never()).getItem(any());
        verify(productRepository, never()).save(any());
        verify(zohoSettingRepository, never()).save(any());
    }

    @Test
    void pullProducts_detailIdentityMismatch_doesNotRelinkProductOrAdvanceCursor() {
        Instant cursor = Instant.parse("2026-08-27T01:02:03Z");
        ZohoSetting setting = new ZohoSetting().connected(true).lastProductPullAt(cursor);
        Map<String, Object> listed = Map.of(
            "sku",
            "IDENTITY-SAFE",
            "item_id",
            "zoho-requested",
            "status",
            "active",
            "last_modified_time",
            "2026-08-28T12:00:00Z"
        );
        Map<String, Object> wrongDetail = Map.of(
            "sku",
            "IDENTITY-SAFE",
            "item_id",
            "zoho-different",
            "status",
            "active"
        );
        when(zohoSettingRepository.findActiveOrLatest()).thenReturn(Optional.of(setting));
        when(productRepository.findAll()).thenReturn(List.of());
        when(booksClient.listAllStatusItems(1)).thenReturn(List.of(listed));
        when(booksClient.listAllStatusItems(2)).thenReturn(List.of());
        when(booksClient.getItem("zoho-requested")).thenReturn(Map.of("item", wrongDetail));

        Map<String, Object> result = service.pullProducts(false);

        assertThat(result.get("failed")).isEqualTo(1);
        assertThat(result.get("failures").toString()).contains("identity mismatch");
        assertThat(setting.getLastProductPullAt()).isEqualTo(cursor);
        verify(productRepository, never()).findOneByZohoItemId(any());
        verify(productRepository, never()).save(any());
        verify(zohoSettingRepository, never()).save(any());
    }

    @Test
    void pullProducts_duplicateRowInOnePage_fetchesAndSavesOnlyOnce() {
        Map<String, Object> row = Map.of(
            "sku",
            "ONE-COPY",
            "item_id",
            "zoho-one-copy",
            "name",
            "One Copy",
            "status",
            "active",
            "rate",
            100
        );
        when(zohoSettingRepository.findActiveOrLatest()).thenReturn(Optional.empty());
        when(productRepository.findAll()).thenReturn(List.of());
        when(booksClient.listItems(1)).thenReturn(List.of(row, row));
        when(booksClient.listItems(2)).thenReturn(List.of());
        when(booksClient.getItem("zoho-one-copy")).thenReturn(Map.of("item", row));
        when(productRepository.findOneByZohoItemId("zoho-one-copy")).thenReturn(Optional.empty());
        when(productRepository.findOneBySku("ONE-COPY")).thenReturn(Optional.empty());
        when(productRepository.findMaxSortOrder()).thenReturn(0);
        when(productRepository.findAllByZohoItemIdIsNotNull()).thenReturn(List.of());
        when(productRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Map<String, Object> result = service.pullProducts(true);

        assertThat(result.get("success")).isEqualTo(1);
        assertThat(result.get("failed")).isEqualTo(0);
        assertThat(result.get("skipped")).isEqualTo(1);
        verify(booksClient, times(1)).getItem("zoho-one-copy");
        verify(productRepository, times(1)).save(any());
    }

    @Test
    void pullProducts_sameSkuForDifferentItemIds_rejectsConflictAndKeepsCursor() {
        Instant cursor = Instant.parse("2026-08-27T01:02:03Z");
        ZohoSetting setting = new ZohoSetting().connected(true).lastProductPullAt(cursor);
        Map<String, Object> first = Map.of(
            "sku",
            "DUPLICATE-SKU",
            "item_id",
            "zoho-first-id",
            "name",
            "First",
            "status",
            "active",
            "last_modified_time",
            "2026-08-28T12:00:00Z"
        );
        Map<String, Object> conflicting = Map.of(
            "sku",
            "DUPLICATE-SKU",
            "item_id",
            "zoho-second-id",
            "name",
            "Second",
            "status",
            "active",
            "last_modified_time",
            "2026-08-28T12:00:00Z"
        );
        when(zohoSettingRepository.findActiveOrLatest()).thenReturn(Optional.of(setting));
        when(productRepository.findAll()).thenReturn(List.of());
        when(booksClient.listAllStatusItems(1)).thenReturn(List.of(first, conflicting));
        when(booksClient.listAllStatusItems(2)).thenReturn(List.of());
        when(booksClient.getItem("zoho-first-id")).thenReturn(Map.of("item", first));
        when(productRepository.findOneByZohoItemId("zoho-first-id")).thenReturn(Optional.empty());
        when(productRepository.findOneBySku("DUPLICATE-SKU")).thenReturn(Optional.empty());
        when(productRepository.findMaxSortOrder()).thenReturn(0);
        when(productRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Map<String, Object> result = service.pullProducts(false);

        assertThat(result.get("success")).isEqualTo(1);
        assertThat(result.get("failed")).isEqualTo(1);
        assertThat(result.get("failures").toString()).contains("multiple item IDs");
        assertThat(setting.getLastProductPullAt()).isEqualTo(cursor);
        verify(booksClient, never()).getItem("zoho-second-id");
        verify(productRepository, times(1)).save(any());
        verify(zohoSettingRepository, never()).save(any());
    }

    @Test
    void pullProducts_repeatedNonEmptyPage_stopsWithoutCleanupOrCursorAdvance() {
        Instant cursor = Instant.parse("2026-08-27T01:02:03Z");
        ZohoSetting setting = new ZohoSetting().connected(true).lastProductPullAt(cursor);
        Map<String, Object> row = Map.of(
            "sku",
            "REPEATED-PAGE",
            "item_id",
            "zoho-repeat",
            "status",
            "inactive",
            "last_modified_time",
            "2026-08-26T12:00:00Z"
        );
        when(zohoSettingRepository.findActiveOrLatest()).thenReturn(Optional.of(setting));
        when(productRepository.findAll()).thenReturn(List.of());
        when(booksClient.listAllStatusItems(1)).thenReturn(List.of(row));
        when(booksClient.listAllStatusItems(2)).thenReturn(List.of(row));
        when(booksClient.getItem("zoho-repeat")).thenReturn(Map.of("item", row));

        Map<String, Object> result = service.pullProducts(false);

        assertThat(result.get("failed")).isEqualTo(1);
        assertThat(result.get("failures").toString()).contains("repeated product page");
        assertThat(setting.getLastProductPullAt()).isEqualTo(cursor);
        verify(booksClient, times(1)).getItem("zoho-repeat");
        verify(booksClient, never()).listAllStatusItems(3);
        verify(productRepository, never()).findAllByZohoItemIdIsNotNull();
        verify(zohoSettingRepository, never()).save(any());
    }

    @Test
    void pullProducts_detailServerFailure_stopsBeforeLaterItemsAndKeepsCursor() {
        Instant cursor = Instant.parse("2026-08-27T01:02:03Z");
        ZohoSetting setting = new ZohoSetting().connected(true).lastProductPullAt(cursor);
        Map<String, Object> first = Map.of(
            "sku",
            "SERVER-DOWN",
            "item_id",
            "zoho-server-down",
            "status",
            "active",
            "last_modified_time",
            "2026-08-28T12:00:00Z"
        );
        Map<String, Object> later = Map.of(
            "sku",
            "DO-NOT-FETCH",
            "item_id",
            "zoho-do-not-fetch",
            "status",
            "active",
            "last_modified_time",
            "2026-08-28T12:00:00Z"
        );
        when(zohoSettingRepository.findActiveOrLatest()).thenReturn(Optional.of(setting));
        when(productRepository.findAll()).thenReturn(List.of());
        when(booksClient.listAllStatusItems(1)).thenReturn(List.of(first, later));
        when(booksClient.getItem("zoho-server-down"))
            .thenThrow(new RestClientResponseException("Unavailable", 503, "Service Unavailable", null, null, null));

        Map<String, Object> result = service.pullProducts(false);

        assertThat(result.get("failed")).isEqualTo(1);
        assertThat(setting.getLastProductPullAt()).isEqualTo(cursor);
        verify(booksClient, never()).getItem("zoho-do-not-fetch");
        verify(booksClient, never()).listAllStatusItems(2);
        verify(productRepository, never()).save(any());
        verify(zohoSettingRepository, never()).save(any());
    }

    @Test
    void pullInventory_blankStockPreservesExistingValue() {
        Product product = new Product().id(306L).sku("NO-STOCK").stock(9).active(true);
        when(productRepository.findAll()).thenReturn(List.of());
        when(booksClient.listItems(1)).thenReturn(List.of(Map.of("sku", "NO-STOCK", "item_id", "zoho-no-stock")));
        when(booksClient.listItems(2)).thenReturn(List.of());

        Map<String, Object> result = service.pullInventory();

        assertThat(result.get("skipped")).isEqualTo(1);
        assertThat(product.getStock()).isEqualTo(9);
        verify(productRepository, never()).save(any());
    }

    @Test
    void pullInventory_parentLinkedByVariantItemId_updatesMatrixRowNotRawParentStock() {
        ProductVariantSupport realVariantSupport = StorePricingTestSupport.variantSupport(objectMapper);
        service =
            new ZohoSyncService(
                booksClient,
                objectMapper,
                applicationProperties,
                itemImageSync,
                productFieldMapper,
                descriptionCodec,
                realVariantSupport,
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
        Product parent = new Product()
            .id(307L)
            .sku("PARENT")
            .zohoItemId("zoho-child")
            .itemStructure("VARIANTS")
            .active(true)
            .variantsJson(
                """
                [{"sku":"OLD-CHILD-SKU","size":"S","color":"Blue","rate":10,"stock":2,"active":true,"zohoItemId":"zoho-child"},
                 {"sku":"OTHER-SKU","size":"M","color":"Blue","rate":10,"stock":3,"active":true,"zohoItemId":"zoho-other"}]
                """
            );
        when(productRepository.findAll()).thenReturn(List.of(parent));
        when(booksClient.listItems(1))
            .thenReturn(List.of(Map.of("sku", "RENAMED-CHILD-SKU", "item_id", "zoho-child", "stock_on_hand", 8)));
        when(booksClient.listItems(2)).thenReturn(List.of());
        when(productRepository.findOneByZohoItemId("zoho-child")).thenReturn(Optional.of(parent));
        when(productRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Map<String, Object> result = service.pullInventory();

        assertThat(result.get("success")).isEqualTo(1);
        assertThat(parent.getStock()).isEqualTo(11);
        assertThat(realVariantSupport.findByZohoItemId(realVariantSupport.parseProduct(parent), "zoho-child").orElseThrow().stock())
            .isEqualTo(8);
    }

    @Test
    void pullInventory_rateLimitAfterFirstPageKeepsPartialSuccessInSummary() {
        Product product = new Product().id(308L).sku("FIRST").stock(1).active(true);
        when(productRepository.findAll()).thenReturn(List.of());
        when(booksClient.listItems(1))
            .thenReturn(List.of(Map.of("sku", "FIRST", "item_id", "zoho-first", "stock_on_hand", 5)));
        when(productRepository.findOneByZohoItemId("zoho-first")).thenReturn(Optional.of(product));
        when(productRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(booksClient.listItems(2))
            .thenThrow(
                org.springframework.web.client.HttpClientErrorException.create(
                    org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,
                    "Too Many Requests",
                    null,
                    null,
                    null
                )
            );

        Map<String, Object> result = service.pullInventory();

        assertThat(result.get("success")).isEqualTo(1);
        assertThat(result.get("failed")).isEqualTo(1);
        assertThat(product.getStock()).isEqualTo(5);
    }

    @Test
    void pullProducts_attachesGroupedItemToExistingParentAndDeactivatesSimpleDuplicate() {
        ProductVariantSupport realVariantSupport = StorePricingTestSupport.variantSupport(objectMapper);
        service =
            new ZohoSyncService(
                booksClient,
                objectMapper,
                applicationProperties,
                itemImageSync,
                productFieldMapper,
                descriptionCodec,
                realVariantSupport,
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

        Product parent = new Product()
            .id(90L)
            .sku("HORIZON")
            .name("Horizon Set")
            .zohoItemId("grp-horizon")
            .itemStructure("VARIANTS")
            .variantsJson("[]")
            .active(true);
        Product simpleDup = new Product()
            .id(91L)
            .sku("HORIZON-S-NAVY")
            .name("Horizon S Navy")
            .itemStructure("SIMPLE")
            .imagesJson("[\"https://cdn.example.com/front.jpg\",\"https://cdn.example.com/rear.jpg\"]")
            .active(true);
        Map<String, Object> small = groupedZohoItem("zoho-s", "HORIZON-S-NAVY", "S", "Navy", 799.20, 3);
        when(zohoSettingRepository.findActiveOrLatest()).thenReturn(Optional.empty());
        when(booksClient.listItems(1)).thenReturn(List.of(small));
        when(booksClient.listItems(2)).thenReturn(List.of());
        when(booksClient.getItem("zoho-s")).thenReturn(Map.of("item", small));
        when(productRepository.findAll()).thenReturn(List.of(parent));
        when(productRepository.findAllByZohoItemIdIsNotNull()).thenReturn(List.of());
        when(productRepository.findOneBySku("HORIZON-S-NAVY")).thenReturn(Optional.of(simpleDup));
        when(productRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.pullProducts();

        verify(productRepository, atLeastOnce()).save(argThat(saved ->
            saved.getId() != null &&
            saved.getId().equals(90L) &&
            saved.getVariantsJson() != null &&
            saved.getVariantsJson().contains("HORIZON-S-NAVY") &&
            !saved.getVariantsJson().contains("front.jpg") &&
            !saved.getVariantsJson().contains("rear.jpg")
        ));
        verify(productRepository).save(argThat(saved ->
            saved.getId() != null && saved.getId().equals(91L) && Boolean.FALSE.equals(saved.getActive())
        ));
    }

    @Test
    void pullProducts_reusesExistingVariantParentBySlugWhenZohoOmitsGroupId() {
        ProductVariantSupport realVariantSupport = StorePricingTestSupport.variantSupport(objectMapper);
        service =
            new ZohoSyncService(
                booksClient,
                objectMapper,
                applicationProperties,
                itemImageSync,
                productFieldMapper,
                descriptionCodec,
                realVariantSupport,
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

        Product parent = new Product()
            .id(2951L)
            .sku("BABY-SWADDLE")
            .slug("baby-swaddle")
            .name("Baby Swaddle")
            .itemStructure("VARIANTS")
            .variantsJson("[]")
            .active(true);
        Map<String, Object> variant = Map.of(
            "item_id",
            "zoho-new",
            "sku",
            "MN-06-1218M-P01-058",
            "name",
            "Baby Swaddle | Age: COMMON SIZE | Print: Cycle",
            "description",
            "Baby Swaddle | Age: COMMON SIZE | Print: Cycle",
            "rate",
            799.20,
            "stock_on_hand",
            3
        );
        when(zohoSettingRepository.findActiveOrLatest()).thenReturn(Optional.empty());
        when(booksClient.listItems(1)).thenReturn(List.of(variant));
        when(booksClient.listItems(2)).thenReturn(List.of());
        when(booksClient.getItem("zoho-new")).thenReturn(Map.of("item", variant));
        when(productRepository.findAll()).thenReturn(List.of());
        when(productRepository.findAllByZohoItemIdIsNotNull()).thenReturn(List.of());
        when(productRepository.findOneBySlug("baby-swaddle")).thenReturn(Optional.of(parent));
        when(productRepository.findOneBySku("MN-06-1218M-P01-058")).thenReturn(Optional.empty());
        when(productRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(productFieldMapper.applyPulledItem(any(), any(), eq(false))).thenReturn(Optional.empty());

        Map<String, Object> result = service.pullProducts();

        assertThat(result.get("success")).isEqualTo(1);
        verify(productRepository, never()).save(argThat(saved -> saved.getId() == null && "baby-swaddle".equals(saved.getSlug())));
        verify(productRepository, atLeastOnce()).save(argThat(saved ->
            saved.getId() != null && saved.getId().equals(2951L) && saved.getVariantsJson() != null && saved.getVariantsJson().contains("MN-06-1218M-P01-058")
        ));
    }

    @Test
    void pullProducts_doesNotAbsorbSimpleItemThatSharesVariantParentName() {
        ProductVariantSupport realVariantSupport = StorePricingTestSupport.variantSupport(objectMapper);
        service =
            new ZohoSyncService(
                booksClient,
                objectMapper,
                applicationProperties,
                itemImageSync,
                productFieldMapper,
                descriptionCodec,
                realVariantSupport,
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

        Product parent = new Product()
            .id(90L)
            .sku("HORIZON")
            .name("Horizon Set")
            .itemStructure("VARIANTS")
            .variantsJson("[]")
            .active(true);
        Map<String, Object> simple = Map.of(
            "item_id",
            "zoho-swaddle",
            "sku",
            "SWADDLE-1",
            "name",
            "Horizon Set",
            "rate",
            500,
            "stock_on_hand",
            2
        );
        when(zohoSettingRepository.findActiveOrLatest()).thenReturn(Optional.empty());
        when(booksClient.listItems(1)).thenReturn(List.of(simple));
        when(booksClient.listItems(2)).thenReturn(List.of());
        when(booksClient.getItem("zoho-swaddle")).thenReturn(Map.of("item", simple));
        when(productRepository.findAll()).thenReturn(List.of(parent));
        when(productRepository.findAllByZohoItemIdIsNotNull()).thenReturn(List.of());
        when(productRepository.findMaxSortOrder()).thenReturn(0);
        when(productRepository.findOneBySku(any())).thenReturn(Optional.empty());
        when(productRepository.findOneByZohoItemId(any())).thenReturn(Optional.empty());
        when(productRepository.findOneBySlug(any())).thenReturn(Optional.empty());
        when(productRepository.save(any())).thenAnswer(invocation -> {
            Product saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(92L);
            }
            return saved;
        });
        when(productFieldMapper.applyPulledItem(any(), any(), eq(false))).thenReturn(Optional.empty());

        service.pullProducts();

        ArgumentCaptor<Product> saved = ArgumentCaptor.forClass(Product.class);
        verify(productRepository, org.mockito.Mockito.atLeastOnce()).save(saved.capture());
        assertThat(saved.getAllValues())
            .noneMatch(p -> p.getId() != null && p.getId().equals(90L) && p.getVariantsJson() != null && p.getVariantsJson().contains("SWADDLE-1"));
        assertThat(saved.getAllValues())
            .anyMatch(p -> "SWADDLE-1".equals(p.getSku()) && !"VARIANTS".equals(p.getItemStructure()));
    }

    @Test
    void syncProduct_variants_sendsGroupIdWhenParentLinked() {
        ProductVariantSupport realVariantSupport = StorePricingTestSupport.variantSupport(objectMapper);
        service =
            new ZohoSyncService(
                booksClient,
                objectMapper,
                applicationProperties,
                itemImageSync,
                productFieldMapper,
                descriptionCodec,
                realVariantSupport,
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

        Product product = new Product()
            .id(21L)
            .sku("HORIZON")
            .name("Horizon Set")
            .zohoItemId("grp-horizon")
            .itemStructure("VARIANTS")
            .variantsJson(
                """
                [{"sku":"HORIZON-S-NAVY","size":"S","color":"Navy","rate":799.20,"mrp":999,"stock":3,"active":true}]
                """
            )
            .active(true);
        when(productRepository.findOneWithEagerRelationships(21L)).thenReturn(Optional.of(product));
        when(booksClient.findItemBySku("HORIZON-S-NAVY")).thenReturn(null);
        when(booksClient.createItem(any())).thenReturn(Map.of("item", Map.of("item_id", "zoho-s")));
        when(productRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.syncProduct(21L);

        verify(booksClient).createItem(
            argThat(
                payload ->
                    "HORIZON-S-NAVY".equals(payload.get("sku")) &&
                    "grp-horizon".equals(payload.get("group_id")) &&
                    "S".equals(payload.get("attribute_option_name1"))
            )
        );
        verify(booksClient, never()).updateItem(eq("grp-horizon"), any());
        verify(itemImageSync).syncVariantImage(eq("zoho-s"), any(Product.class), eq("HORIZON-S-NAVY"));
        verify(itemImageSync, never()).syncProductImage(any(), any());
    }

    @Test
    void syncProduct_variants_neverSendsMrpLowerThanSellingPrice() {
        ProductVariantSupport realVariantSupport = StorePricingTestSupport.variantSupport(objectMapper);
        service =
            new ZohoSyncService(
                booksClient,
                objectMapper,
                applicationProperties,
                itemImageSync,
                productFieldMapper,
                descriptionCodec,
                realVariantSupport,
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

        Product product = new Product()
            .id(25L)
            .sku("BSJ")
            .name("Book Style Tying Jabla")
            .zohoItemId("grp-jabla")
            .itemStructure("VARIANTS")
            .variantsJson(
                """
                [{"sku":"BSJ-0-3-AQUA","size":"0-3 M","color":"Aqua","rate":799.20,"mrp":499,"stock":1,"active":true}]
                """
            )
            .active(true);
        when(productRepository.findOneWithEagerRelationships(25L)).thenReturn(Optional.of(product));
        when(booksClient.findItemBySku("BSJ-0-3-AQUA")).thenReturn(null);
        when(booksClient.createItem(any())).thenReturn(Map.of("item", Map.of("item_id", "zoho-bsj-aqua")));
        when(productRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.syncProduct(25L);

        // Selling price is derived from MRP (20% store discount); label_rate must stay >= rate.
        verify(booksClient).createItem(
            argThat(payload -> {
                if (!"BSJ-0-3-AQUA".equals(payload.get("sku"))) {
                    return false;
                }
                BigDecimal rate = payload.get("rate") instanceof BigDecimal bd ? bd : null;
                BigDecimal labelRate = payload.get("label_rate") == null
                    ? null
                    : new BigDecimal(String.valueOf(payload.get("label_rate")));
                return (
                    rate != null &&
                    labelRate != null &&
                    labelRate.compareTo(rate) >= 0 &&
                    BigDecimal.valueOf(399.20).compareTo(rate) == 0 &&
                    labelRate.compareTo(BigDecimal.valueOf(499)) == 0 &&
                    "grp-jabla".equals(payload.get("group_id")) &&
                    "0-3 M".equals(payload.get("attribute_option_name1")) &&
                    "Aqua".equals(payload.get("attribute_option_name2"))
                );
            })
        );
    }

    @Test
    void syncProduct_variants_priceValidationDoesNotRetryAsPlainItem() {
        ProductVariantSupport realVariantSupport = StorePricingTestSupport.variantSupport(objectMapper);
        service =
            new ZohoSyncService(
                booksClient,
                objectMapper,
                applicationProperties,
                itemImageSync,
                productFieldMapper,
                descriptionCodec,
                realVariantSupport,
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

        Product product = new Product()
            .id(26L)
            .sku("BSJ")
            .name("Book Style Tying Jabla")
            .zohoItemId("grp-jabla")
            .itemStructure("VARIANTS")
            .variantsJson(
                """
                [{"sku":"BSJ-0-3-AQUA","size":"0-3 M","color":"Aqua","rate":799.20,"mrp":499,"stock":1,"active":true}]
                """
            )
            .active(true);
        when(productRepository.findOneWithEagerRelationships(26L)).thenReturn(Optional.of(product));
        when(booksClient.findItemBySku("BSJ-0-3-AQUA")).thenReturn(null);
        when(booksClient.createItem(any()))
            .thenThrow(
                new RestClientResponseException(
                    "Selling Price cannot be greater than MRP.",
                    400,
                    "Bad Request",
                    null,
                    "{\"code\":6,\"message\":\"Selling Price cannot be greater than MRP.\"}".getBytes(),
                    null
                )
            );

        service.syncProduct(26L);

        verify(booksClient, times(1)).createItem(any());
        verify(productRepository)
            .updateZohoSyncFailure(
                eq(26L),
                eq(ZohoSyncStatus.FAILED),
                argThat(message -> message.contains("Selling Price cannot be greater than MRP"))
            );
    }

    @Test
    void syncProduct_variants_retriesPlainItemWhenZohoRejectsAttributes() {
        ProductVariantSupport realVariantSupport = StorePricingTestSupport.variantSupport(objectMapper);
        service =
            new ZohoSyncService(
                booksClient,
                objectMapper,
                applicationProperties,
                itemImageSync,
                productFieldMapper,
                descriptionCodec,
                realVariantSupport,
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

        Product product = new Product()
            .id(22L)
            .sku("HORIZON")
            .name("Horizon Set")
            .itemStructure("VARIANTS")
            .variantsJson(
                """
                [{"sku":"HORIZON-S-NAVY","size":"S","color":"Navy","rate":799.20,"mrp":999,"stock":3,"active":true}]
                """
            )
            .active(true);
        when(productRepository.findOneWithEagerRelationships(22L)).thenReturn(Optional.of(product));
        when(booksClient.findItemBySku("HORIZON-S-NAVY")).thenReturn(null);
        when(booksClient.createItem(any()))
            .thenThrow(
                new RestClientResponseException(
                    "Invalid field",
                    400,
                    "Bad Request",
                    null,
                    "{\"message\":\"Invalid column group_id\"}".getBytes(),
                    null
                )
            )
            .thenReturn(Map.of("item", Map.of("item_id", "zoho-plain")));
        when(productRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.syncProduct(22L);

        verify(booksClient, times(2)).createItem(any());
        verify(booksClient).createItem(argThat(payload -> !payload.containsKey("attribute_name1") && !payload.containsKey("group_id")));
        verify(productRepository).save(argThat(saved -> saved.getVariantsJson().contains("zoho-plain")));
    }

    @Test
    void syncProduct_inactiveVariants_doesNotMarkGroupIdInactive() {
        ProductVariantSupport realVariantSupport = StorePricingTestSupport.variantSupport(objectMapper);
        service =
            new ZohoSyncService(
                booksClient,
                objectMapper,
                applicationProperties,
                itemImageSync,
                productFieldMapper,
                descriptionCodec,
                realVariantSupport,
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

        Product product = new Product()
            .id(23L)
            .sku("HORIZON")
            .zohoItemId("grp-horizon")
            .itemStructure("VARIANTS")
            .variantsJson(
                """
                [{"sku":"HORIZON-S-NAVY","size":"S","color":"Navy","rate":799.20,"stock":1,"active":true,"zohoItemId":"zoho-s"}]
                """
            )
            .active(false);
        when(productRepository.findOneWithEagerRelationships(23L)).thenReturn(Optional.of(product));

        service.syncProduct(23L);

        verify(booksClient).markItemInactive("zoho-s");
        verify(booksClient, never()).markItemInactive("grp-horizon");
    }

    @Test
    void syncProduct_inactiveVariants_staleZohoItemId_skipsRatherThanFailingWholeProduct() {
        ProductVariantSupport realVariantSupport = StorePricingTestSupport.variantSupport(objectMapper);
        service =
            new ZohoSyncService(
                booksClient,
                objectMapper,
                applicationProperties,
                itemImageSync,
                productFieldMapper,
                descriptionCodec,
                realVariantSupport,
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

        Product product = new Product()
            .id(40801L)
            .sku("BABY-SWADDLE")
            .itemStructure("VARIANTS")
            .variantsJson(
                """
                [
                  {"sku":"BABY-SWADDLE-CS-BROWN","size":"COMMON SIZE","color":"Brown","rate":10,"stock":1,"active":true,"zohoItemId":"stale-1"},
                  {"sku":"BABY-SWADDLE-CS-GREEN","size":"COMMON SIZE","color":"Green","rate":10,"stock":1,"active":true,"zohoItemId":"stale-2"},
                  {"sku":"BABY-SWADDLE-CS-WHITE","size":"COMMON SIZE","color":"White","rate":10,"stock":1,"active":true,"zohoItemId":"stale-3"}
                ]
                """
            )
            .zohoSyncStatus(ZohoSyncStatus.SYNCED)
            .active(false);
        when(productRepository.findOneWithEagerRelationships(40801L)).thenReturn(Optional.of(product));
        RestClientResponseException notFound = new RestClientResponseException(
            "Resource does not exist",
            400,
            "Bad Request",
            null,
            "{\"code\":2006,\"message\":\"Resource does not exist\"}".getBytes(),
            null
        );
        when(booksClient.markItemInactive("stale-1")).thenThrow(notFound);
        when(booksClient.markItemInactive("stale-2")).thenReturn(Map.of());
        when(booksClient.markItemInactive("stale-3")).thenThrow(notFound);

        service.syncProduct(40801L);

        verify(booksClient).markItemInactive("stale-1");
        verify(booksClient).markItemInactive("stale-2");
        verify(booksClient).markItemInactive("stale-3");
        verify(productRepository, never()).updateZohoSyncFailure(eq(40801L), eq(ZohoSyncStatus.FAILED), any());
        verify(productRepository).updateZohoSyncFailure(40801L, ZohoSyncStatus.SYNCED, null);
    }

    @Test
    void syncProduct_activeParent_deactivatesLinkedInactiveVariantRows() {
        ProductVariantSupport realVariantSupport = StorePricingTestSupport.variantSupport(objectMapper);
        service =
            new ZohoSyncService(
                booksClient,
                objectMapper,
                applicationProperties,
                itemImageSync,
                productFieldMapper,
                descriptionCodec,
                realVariantSupport,
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
        Product product = new Product()
            .id(40802L)
            .sku("HORIZON")
            .name("Horizon")
            .itemStructure("VARIANTS")
            .variantsJson(
                """
                [{"sku":"HORIZON-S-BLUE","size":"S","color":"Blue","rate":10,"stock":0,"active":false,"zohoItemId":"zoho-inactive"}]
                """
            )
            .active(true);
        when(productRepository.findOneWithEagerRelationships(40802L)).thenReturn(Optional.of(product));

        service.syncProduct(40802L);

        verify(booksClient).markItemInactive("zoho-inactive");
        verify(booksClient, never()).createItem(any());
        verify(booksClient, never()).updateItem(any(), any());
        verify(productRepository).updateZohoSyncSuccess(eq(40802L), eq(null), eq(ZohoSyncStatus.SYNCED), any());
        verify(productRepository, never()).updateZohoSyncFailure(eq(40802L), eq(ZohoSyncStatus.FAILED), any());
    }

    @Test
    void syncProduct_variantValidationRejectsDuplicateActiveSkusBeforeCallingZoho() {
        ProductVariantSupport realVariantSupport = StorePricingTestSupport.variantSupport(objectMapper);
        service =
            new ZohoSyncService(
                booksClient,
                objectMapper,
                applicationProperties,
                itemImageSync,
                productFieldMapper,
                descriptionCodec,
                realVariantSupport,
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
        Product product = new Product()
            .id(40803L)
            .sku("HORIZON")
            .name("Horizon")
            .itemStructure("VARIANTS")
            .variantsJson(
                """
                [{"sku":"DUP-SKU","size":"S","color":"Blue","rate":10,"stock":1,"active":true},
                 {"sku":"dup-sku","size":"M","color":"Blue","rate":10,"stock":1,"active":true}]
                """
            )
            .active(true);
        when(productRepository.findOneWithEagerRelationships(40803L)).thenReturn(Optional.of(product));

        service.syncProduct(40803L);

        verify(booksClient, never()).findItemBySku(any());
        verify(booksClient, never()).createItem(any());
        verify(booksClient, never()).updateItem(any(), any());
        verify(productRepository).updateZohoSyncFailure(
            eq(40803L),
            eq(ZohoSyncStatus.FAILED),
            argThat(message -> message.contains("Duplicate active variant SKU"))
        );
    }

    @Test
    void syncProduct_inactiveParentWithOnlyRowLinks_stillDeactivatesZohoItems() {
        ProductVariantSupport realVariantSupport = StorePricingTestSupport.variantSupport(objectMapper);
        service =
            new ZohoSyncService(
                booksClient,
                objectMapper,
                applicationProperties,
                itemImageSync,
                productFieldMapper,
                descriptionCodec,
                realVariantSupport,
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
        Product product = new Product()
            .id(40804L)
            .sku("ROW-LINKS-ONLY")
            .itemStructure("VARIANTS")
            .variantsJson(
                """
                [{"sku":"ROW-SKU","size":"S","color":"Blue","rate":10,"stock":0,"active":true,"zohoItemId":"zoho-row-only"}]
                """
            )
            .active(false);
        when(productRepository.findOneWithEagerRelationships(40804L)).thenReturn(Optional.of(product));

        service.syncProduct(40804L);

        verify(booksClient).markItemInactive("zoho-row-only");
        verify(productRepository).updateZohoSyncFailure(40804L, ZohoSyncStatus.SYNCED, null);
    }

    @Test
    void syncOrder_simpleProduct_stillUsesParentZohoItemId() {
        Product product = new Product().id(50L).sku("SWADDLE").zohoItemId("zoho-simple").itemStructure("SIMPLE");
        OrderItem line = new OrderItem().productId("50").productName("Muslin Swaddle").qty(2).price(BigDecimal.TEN);
        CustomerOrder order = new CustomerOrder().id(201L).orderNumber("MNM-SIMP-1").userLogin("buyer@example.com");
        order.addItems(line);
        line.setOrder(order);

        when(customerOrderRepository.findById(201L)).thenReturn(Optional.of(order));
        when(orderItemRepository.findAllByOrderId(201L)).thenReturn(List.of(line));
        when(booksClient.findContactByEmail("buyer@example.com")).thenReturn(Map.of("contact_id", "cust-1"));
        when(booksClient.createSalesOrder(any())).thenReturn(Map.of("salesorder", Map.of("salesorder_id", "so-simple")));
        when(booksClient.createInvoiceFromSalesOrder("so-simple")).thenReturn(Map.of("invoice", Map.of("invoice_id", "inv-simple")));
        when(productRepository.findById(50L)).thenReturn(Optional.of(product));

        service.syncOrder(201L);

        verify(booksClient).createSalesOrder(
            argThat(payload -> {
                Object linesObj = payload.get("line_items");
                if (!(linesObj instanceof List<?> lines) || lines.isEmpty()) {
                    return false;
                }
                Object lineObj = lines.get(0);
                if (!(lineObj instanceof Map<?, ?> salesLine)) {
                    return false;
                }
                return (
                    "zoho-simple".equals(salesLine.get("item_id")) &&
                    !salesLine.containsKey("group_id") &&
                    !salesLine.containsKey("attribute_name1") &&
                    Integer.valueOf(2).equals(salesLine.get("quantity"))
                );
            })
        );
    }

    @Test
    void syncOrder_inactiveVariantItem_reactivatesMappingAndRetriesSalesOrder() {
        ProductVariantSupport realVariantSupport = StorePricingTestSupport.variantSupport(objectMapper);
        service =
            new ZohoSyncService(
                booksClient,
                objectMapper,
                applicationProperties,
                itemImageSync,
                productFieldMapper,
                descriptionCodec,
                realVariantSupport,
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
        Product product = new Product()
            .id(60L)
            .name("Baby Swaddle")
            .sku("BABY-SWADDLE")
            .active(true)
            .itemStructure("VARIANTS")
            .variantsJson(
                """
                [{"sku":"BABY-SWADDLE-CS-BROWN","size":"COMMON SIZE","color":"Brown","rate":10,"stock":5,"active":true,"zohoItemId":"stale-item"}]
                """
            );
        OrderItem line = new OrderItem()
            .productId("60")
            .productName("Baby Swaddle")
            .variantSku("BABY-SWADDLE-CS-BROWN")
            .itemSize("COMMON SIZE")
            .color("Brown")
            .qty(1)
            .price(BigDecimal.TEN);
        CustomerOrder order = new CustomerOrder().id(202L).orderNumber("MNM-REPAIR-1").userLogin("buyer@example.com");
        order.addItems(line);

        RestClientResponseException unavailable = new RestClientResponseException(
            "Inactive item",
            400,
            "Bad Request",
            null,
            "{\"code\":2006,\"message\":\"SalesOrder cannot be raised for items that have been deleted or marked as inactive.\"}".getBytes(),
            null
        );
        when(customerOrderRepository.findById(202L)).thenReturn(Optional.of(order));
        when(orderItemRepository.findAllByOrderId(202L)).thenReturn(List.of(line));
        when(productRepository.findById(60L)).thenReturn(Optional.of(product));
        when(booksClient.findContactByEmail("buyer@example.com")).thenReturn(Map.of("contact_id", "cust-1"));
        when(booksClient.createSalesOrder(any()))
            .thenThrow(unavailable)
            .thenReturn(Map.of("salesorder", Map.of("salesorder_id", "so-repaired")));
        when(booksClient.markItemActive("stale-item")).thenReturn(Map.of());
        when(booksClient.updateItem(eq("stale-item"), any())).thenReturn(Map.of("item", Map.of("item_id", "stale-item")));
        when(booksClient.createInvoiceFromSalesOrder("so-repaired"))
            .thenReturn(Map.of("invoice", Map.of("invoice_id", "inv-repaired")));

        service.syncOrder(202L);

        verify(booksClient, times(2)).createSalesOrder(any());
        verify(booksClient).markItemActive("stale-item");
        verify(booksClient).updateItem(eq("stale-item"), any());
        assertThat(order.getZohoSalesOrderId()).isEqualTo("so-repaired");
        assertThat(order.getZohoSyncStatus()).isEqualTo(ZohoSyncStatus.SYNCED);
    }

    @Test
    void syncOrder_marksSalesOrderOpenBeforeInvoicing() {
        CustomerOrder order = new CustomerOrder()
            .id(114L)
            .orderNumber("MNM-MARK-OPEN")
            .userLogin("buyer@example.com")
            .paymentMethod("cod")
            .paymentStatus(com.miniandmore.app.domain.enumeration.PaymentStatus.PAID)
            .status(com.miniandmore.app.domain.enumeration.OrderStatus.CONFIRMED)
            .total(BigDecimal.valueOf(500))
            .zohoSalesOrderId("so-mark-open");

        when(customerOrderRepository.findById(114L)).thenReturn(Optional.of(order));
        when(booksClient.findContactByEmail("buyer@example.com")).thenReturn(Map.of("contact_id", "cust-1"));
        when(booksClient.createInvoiceFromSalesOrder("so-mark-open"))
            .thenReturn(Map.of("invoice", Map.of("invoice_id", "inv-mark-open")));
        when(booksClient.getInvoice("inv-mark-open"))
            .thenReturn(Map.of("invoice", Map.of("status", "sent", "balance", 500)));
        when(booksClient.createCustomerPayment(any())).thenReturn(Map.of("payment", Map.of("payment_id", "pay-mark-open")));

        service.syncOrder(114L);

        var inOrder = org.mockito.Mockito.inOrder(booksClient);
        inOrder.verify(booksClient).markSalesOrderAsOpen("so-mark-open");
        inOrder.verify(booksClient).createInvoiceFromSalesOrder("so-mark-open");
        assertThat(order.getZohoInvoiceId()).isEqualTo("inv-mark-open");
        assertThat(order.getZohoSyncStatus()).isEqualTo(ZohoSyncStatus.SYNCED);
    }

    @Test
    void syncOrder_alreadyInvoicedSalesOrder_linksExistingInvoice() {
        CustomerOrder order = new CustomerOrder()
            .id(115L)
            .orderNumber("MNM-ALREADY-INV")
            .userLogin("buyer@example.com")
            .paymentStatus(com.miniandmore.app.domain.enumeration.PaymentStatus.PAID)
            .status(com.miniandmore.app.domain.enumeration.OrderStatus.CONFIRMED)
            .total(BigDecimal.valueOf(419))
            .zohoSalesOrderId("so-already-inv")
            .zohoSyncStatus(ZohoSyncStatus.FAILED)
            .zohoSyncError("36026");

        RestClientResponseException alreadyInvoiced = new RestClientResponseException(
            "Bad Request",
            400,
            "Bad Request",
            null,
            "{\"code\":36026,\"message\":\"There are no items in this sales order to be invoiced.\"}".getBytes(),
            null
        );

        when(customerOrderRepository.findById(115L)).thenReturn(Optional.of(order));
        when(booksClient.findContactByEmail("buyer@example.com")).thenReturn(Map.of("contact_id", "cust-1"));
        when(booksClient.createInvoiceFromSalesOrder("so-already-inv")).thenThrow(alreadyInvoiced);
        when(booksClient.getSalesOrder("so-already-inv"))
            .thenReturn(
                Map.of(
                    "salesorder",
                    Map.of("salesorder_id", "so-already-inv", "invoices", List.of(Map.of("invoice_id", "inv-existing", "status", "sent")))
                )
            );

        service.syncOrder(115L);

        assertThat(order.getZohoInvoiceId()).isEqualTo("inv-existing");
        assertThat(order.getZohoSyncStatus()).isEqualTo(ZohoSyncStatus.SYNCED);
        assertThat(order.getZohoSyncError()).isNull();
    }

    @Test
    void syncOrder_alreadyInvoiced_codPaid_recordsPayment() {
        CustomerOrder order = new CustomerOrder()
            .id(116L)
            .orderNumber("MNM-ALREADY-INV-COD")
            .userLogin("buyer@example.com")
            .paymentMethod("cod")
            .paymentStatus(com.miniandmore.app.domain.enumeration.PaymentStatus.PAID)
            .status(com.miniandmore.app.domain.enumeration.OrderStatus.CONFIRMED)
            .total(BigDecimal.valueOf(419))
            .zohoSalesOrderId("so-already-inv-cod");

        RestClientResponseException alreadyInvoiced = new RestClientResponseException(
            "Bad Request",
            400,
            "Bad Request",
            null,
            "{\"code\":36026,\"message\":\"There are no items in this sales order to be invoiced.\"}".getBytes(),
            null
        );

        when(customerOrderRepository.findById(116L)).thenReturn(Optional.of(order));
        when(booksClient.findContactByEmail("buyer@example.com")).thenReturn(Map.of("contact_id", "cust-1"));
        when(booksClient.createInvoiceFromSalesOrder("so-already-inv-cod")).thenThrow(alreadyInvoiced);
        when(booksClient.getSalesOrder("so-already-inv-cod"))
            .thenReturn(
                Map.of(
                    "salesorder",
                    Map.of("salesorder_id", "so-already-inv-cod", "invoices", List.of(Map.of("invoice_id", "inv-cod-existing", "status", "sent")))
                )
            );
        when(booksClient.getInvoice("inv-cod-existing"))
            .thenReturn(Map.of("invoice", Map.of("invoice_id", "inv-cod-existing", "status", "sent", "balance", 419)));
        when(booksClient.createCustomerPayment(any())).thenReturn(Map.of("payment", Map.of("payment_id", "pay-cod-existing")));

        service.syncOrder(116L);

        assertThat(order.getZohoInvoiceId()).isEqualTo("inv-cod-existing");
        verify(booksClient).createCustomerPayment(any());
    }

    @Test
    void cancelOrder_voidsZohoInvoiceAndSalesOrder() {
        CustomerOrder order = new CustomerOrder()
            .id(203L)
            .orderNumber("MNM-CANCEL-ZOHO")
            .zohoInvoiceId("inv-cancel")
            .zohoSalesOrderId("so-cancel");
        when(customerOrderRepository.findById(203L)).thenReturn(Optional.of(order));

        service.cancelOrder(203L);

        verify(booksClient).voidInvoice("inv-cancel");
        verify(booksClient).voidSalesOrder("so-cancel", "Customer cancelled before dispatch");
    }

    private static Map<String, Object> groupedZohoItem(
        String itemId,
        String sku,
        String size,
        String color,
        double rate,
        int stock
    ) {
        java.util.LinkedHashMap<String, Object> item = new java.util.LinkedHashMap<>();
        item.put("item_id", itemId);
        item.put("sku", sku);
        item.put("name", "Horizon Set — " + size + " / " + color);
        item.put("group_id", "grp-horizon");
        item.put("group_name", "Horizon Set");
        item.put("attribute_name1", "Size");
        item.put("attribute_option_name1", size);
        item.put("attribute_name2", "Color");
        item.put("attribute_option_name2", color);
        item.put("rate", rate);
        item.put("label_rate", rate);
        item.put("stock_on_hand", stock);
        return item;
    }

    private static Map<String, Object> booksVariantItem(
        String itemId,
        String sku,
        String size,
        String color,
        double rate,
        int stock
    ) {
        java.util.LinkedHashMap<String, Object> item = new java.util.LinkedHashMap<>();
        item.put("item_id", itemId);
        item.put("sku", sku);
        item.put("name", "Horizon Set — " + size + " / " + color);
        item.put("rate", rate);
        item.put("label_rate", rate);
        item.put("stock_on_hand", stock);
        return item;
    }
}
