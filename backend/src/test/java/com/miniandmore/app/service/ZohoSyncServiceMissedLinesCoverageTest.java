package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.client.ZohoBooksClient;
import com.miniandmore.app.config.ApplicationProperties;
import com.miniandmore.app.domain.CustomerOrder;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientResponseException;

/**
 * Branch coverage for {@link ZohoSyncService} helpers still under-covered in JaCoCo reports.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ZohoSyncServiceMissedLinesCoverageTest {

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
        lenient().when(customerOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // --- uniqueGroupSku ---

    @Test
    void uniqueGroupSku_returnsBaseWhenSkuAvailable() {
        when(productRepository.findOneBySku("HORIZON-SET")).thenReturn(Optional.empty());
        String sku = ReflectionTestUtils.invokeMethod(service, "uniqueGroupSku", "Horizon Set", "grp-1", "FALLBACK");
        assertThat(sku).isEqualTo("HORIZON-SET");
    }

    @Test
    void uniqueGroupSku_incrementsSuffixUntilFree() {
        when(productRepository.findOneBySku("HORIZON-SET")).thenReturn(Optional.of(new Product().id(1L)));
        when(productRepository.findOneBySku("HORIZON-SET-1")).thenReturn(Optional.of(new Product().id(2L)));
        when(productRepository.findOneBySku("HORIZON-SET-2")).thenReturn(Optional.empty());
        String sku = ReflectionTestUtils.invokeMethod(service, "uniqueGroupSku", "Horizon Set", "grp-1", "FALLBACK");
        assertThat(sku).isEqualTo("HORIZON-SET-2");
    }

    @Test
    void uniqueGroupSku_fallsBackToPrefixedGroupIdAfterFiftyCollisions() {
        when(productRepository.findOneBySku(any())).thenReturn(Optional.of(new Product().id(1L)));
        String sku = ReflectionTestUtils.invokeMethod(service, "uniqueGroupSku", "Busy", "grp-77", "FB");
        assertThat(sku).isEqualTo("G-grp-77");
    }

    @Test
    void uniqueGroupSku_nullNamesSlugifyToItemThenSuffix() {
        when(productRepository.findOneBySku("ITEM")).thenReturn(Optional.of(new Product().id(1L)));
        when(productRepository.findOneBySku("ITEM-1")).thenReturn(Optional.empty());
        String sku = ReflectionTestUtils.invokeMethod(service, "uniqueGroupSku", null, "grp-x", null);
        assertThat(sku).isEqualTo("ITEM-1");
    }

    // --- resolveOrCreateContact / findContactByNameIfAllowed ---

    @Test
    void resolveOrCreateContact_emailLookupHitReturnsContactId() {
        CustomerOrder order = new CustomerOrder()
            .orderNumber("MNM-EMAIL-HIT")
            .userLogin("buyer@example.com")
            .shippingAddressJson("{\"full_name\":\"Jane\",\"email\":\"buyer@example.com\"}");
        when(booksClient.findContactByEmail("buyer@example.com")).thenReturn(Map.of("contact_id", "cust-email"));

        String contactId = ReflectionTestUtils.invokeMethod(service, "resolveOrCreateContact", order);

        assertThat(contactId).isEqualTo("cust-email");
        verify(booksClient, never()).createContact(any());
    }

    @Test
    void resolveOrCreateContact_invalidEmailLookupSkipsAndFindsByName() {
        CustomerOrder order = new CustomerOrder()
            .orderNumber("MNM-BAD-EMAIL")
            .userLogin("buyer@example.com")
            .shippingAddressJson("{\"full_name\":\"Jane Doe\",\"email\":\"buyer@example.com\"}");
        when(booksClient.findContactByEmail("buyer@example.com")).thenThrow(invalidEmailError());
        when(booksClient.findContactByName("Jane Doe")).thenReturn(Map.of("contact_id", "cust-by-name"));

        String contactId = ReflectionTestUtils.invokeMethod(service, "resolveOrCreateContact", order);

        assertThat(contactId).isEqualTo("cust-by-name");
    }

    @Test
    void resolveOrCreateContact_createsContactWhenNoExistingMatch() {
        CustomerOrder order = new CustomerOrder()
            .orderNumber("MNM-CREATE")
            .shippingAddressJson("{\"full_name\":\"New Shopper\",\"phone\":\"9999888877\"}");
        when(booksClient.findContactByName("New Shopper")).thenReturn(null);
        when(booksClient.createContact(any())).thenReturn(Map.of("contact", Map.of("contact_id", "cust-new")));

        String contactId = ReflectionTestUtils.invokeMethod(service, "resolveOrCreateContact", order);

        assertThat(contactId).isEqualTo("cust-new");
        verify(booksClient).createContact(any());
    }

    @Test
    void resolveOrCreateContact_duplicateNameOnCreateFindsExisting() {
        CustomerOrder order = new CustomerOrder()
            .orderNumber("MNM-DUP")
            .shippingAddressJson("{\"full_name\":\"Jane Customer\"}");
        when(booksClient.findContactByName("Jane Customer")).thenReturn(null).thenReturn(Map.of("contact_id", "cust-dup"));
        when(booksClient.createContact(any())).thenThrow(duplicateContactError());

        String contactId = ReflectionTestUtils.invokeMethod(service, "resolveOrCreateContact", order);

        assertThat(contactId).isEqualTo("cust-dup");
    }

    @Test
    void findContactByNameIfAllowed_blankNameReturnsNull() {
        Map<String, Object> result = ReflectionTestUtils.invokeMethod(service, "findContactByNameIfAllowed", "   ");
        assertThat(result).isNull();
        verify(booksClient, never()).findContactByName(any());
    }

    @Test
    void findContactByNameIfAllowed_skipsEmailShapedNames() {
        Map<String, Object> result = ReflectionTestUtils.invokeMethod(service, "findContactByNameIfAllowed", "shopper@store.com");
        assertThat(result).isNull();
        verify(booksClient, never()).findContactByName(any());
    }

    @Test
    void findContactByNameIfAllowed_invalidLookupSwallowsError() {
        when(booksClient.findContactByName("Bad Name")).thenThrow(invalidContactNameLookupError());
        Map<String, Object> result = ReflectionTestUtils.invokeMethod(service, "findContactByNameIfAllowed", "Bad Name");
        assertThat(result).isNull();
    }

    @Test
    void findContactByNameIfAllowed_rethrowsUnexpectedLookupErrors() {
        when(booksClient.findContactByName("Jane")).thenThrow(new RuntimeException("Zoho down"));
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service, "findContactByNameIfAllowed", "Jane"))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Zoho down");
    }

    // --- resolveInvoiceBalanceDue / isInvoiceFullyPaid ---

    @Test
    void resolveInvoiceBalanceDue_paidStatusIgnoresBalanceField() {
        when(booksClient.getInvoice("inv-paid")).thenReturn(Map.of("invoice", Map.of("status", "paid", "balance", 500)));
        BigDecimal balance = ReflectionTestUtils.invokeMethod(service, "resolveInvoiceBalanceDue", "inv-paid");
        assertThat(balance).isEqualByComparingTo("0");
    }

    @Test
    void resolveInvoiceBalanceDue_readsBalanceForOpenInvoice() {
        when(booksClient.getInvoice("inv-open"))
            .thenReturn(Map.of("invoice", Map.of("status", "sent", "balance", 125.5)));
        BigDecimal balance = ReflectionTestUtils.invokeMethod(service, "resolveInvoiceBalanceDue", "inv-open");
        assertThat(balance).isEqualByComparingTo("125.50");
    }

    @Test
    void resolveInvoiceBalanceDue_emptyResponseThrows() {
        when(booksClient.getInvoice("inv-empty")).thenReturn(Map.of());
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service, "resolveInvoiceBalanceDue", "inv-empty"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("response was empty");
    }

    @Test
    void resolveInvoiceBalanceDue_emptyInvoicePayloadThrows() {
        when(booksClient.getInvoice("inv-no-body")).thenReturn(Map.of("invoice", Map.of()));
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service, "resolveInvoiceBalanceDue", "inv-no-body"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("payload was empty");
    }

    @Test
    void isInvoiceFullyPaid_trueWhenBalanceZero() {
        when(booksClient.getInvoice("inv-zero")).thenReturn(Map.of("invoice", Map.of("status", "paid")));
        Boolean fullyPaid = ReflectionTestUtils.invokeMethod(service, "isInvoiceFullyPaid", "inv-zero");
        assertThat(fullyPaid).isTrue();
    }

    @Test
    void isInvoiceFullyPaid_falseWhenBalanceDue() {
        when(booksClient.getInvoice("inv-due")).thenReturn(Map.of("invoice", Map.of("status", "sent", "balance", 10)));
        Boolean fullyPaid = ReflectionTestUtils.invokeMethod(service, "isInvoiceFullyPaid", "inv-due");
        assertThat(fullyPaid).isFalse();
    }

    // --- cacheInvoicePdf ---

    @Test
    void cacheInvoicePdf_skipsWhenUrlAlreadyCached() {
        CustomerOrder order = new CustomerOrder().zohoInvoicePdfUrl("https://cdn.example.com/existing.pdf");
        ReflectionTestUtils.invokeMethod(service, "cacheInvoicePdf", order, "inv-skip");
        verify(booksClient, never()).downloadInvoicePdf(any(), anyLong());
    }

    @Test
    void cacheInvoicePdf_skipsWhenInvoiceIdBlank() {
        CustomerOrder order = new CustomerOrder();
        ReflectionTestUtils.invokeMethod(service, "cacheInvoicePdf", order, "  ");
        verify(booksClient, never()).downloadInvoicePdf(any(), anyLong());
    }

    @Test
    void cacheInvoicePdf_noOpWhenDownloadEmpty() {
        CustomerOrder order = new CustomerOrder();
        ApplicationProperties.Storage storage = new ApplicationProperties.Storage();
        when(applicationProperties.getStorage()).thenReturn(storage);
        when(booksClient.downloadInvoicePdf(eq("inv-empty"), anyLong())).thenReturn(Optional.empty());

        ReflectionTestUtils.invokeMethod(service, "cacheInvoicePdf", order, "inv-empty");

        assertThat(order.getZohoInvoicePdfUrl()).isNull();
        verify(s3StorageService, never()).uploadInvoicePdf(any(), any());
    }

    @Test
    void cacheInvoicePdf_uploadsAndSetsTrimmedUrl() {
        CustomerOrder order = new CustomerOrder();
        ApplicationProperties.Storage storage = new ApplicationProperties.Storage();
        storage.getS3().setMaxUploadBytes(2_000_000L);
        when(applicationProperties.getStorage()).thenReturn(storage);
        when(booksClient.downloadInvoicePdf(eq("inv-ok"), anyLong()))
            .thenReturn(Optional.of(new ZohoBooksClient.BinaryDownload(new byte[] { 5, 6 }, "application/pdf")));
        when(s3StorageService.uploadInvoicePdf(any(), any())).thenReturn("  https://cdn.example.com/inv.pdf  ");

        ReflectionTestUtils.invokeMethod(service, "cacheInvoicePdf", order, "inv-ok");

        assertThat(order.getZohoInvoicePdfUrl()).isEqualTo("https://cdn.example.com/inv.pdf");
    }

    // --- retryFailedOrders (COD page) ---

    @Test
    void retryFailedOrders_processesConfirmedCodPage() {
        CustomerOrder codOrder = new CustomerOrder()
            .id(401L)
            .orderNumber("MNM-COD-PAGE")
            .userLogin("cod@example.com")
            .paymentMethod("cod")
            .paymentStatus(PaymentStatus.PAID)
            .status(OrderStatus.CONFIRMED)
            .zohoSalesOrderId("so-cod-page")
            .zohoSyncStatus(ZohoSyncStatus.PENDING);
        when(customerOrderRepository.findPaidOrdersForZohoRetry(eq(PaymentStatus.PAID), any(), eq(PageRequest.of(0, 200))))
            .thenReturn(Page.empty());
        when(
            customerOrderRepository.findConfirmedCodOrdersForZohoRetry(
                eq(List.of(ZohoSyncStatus.FAILED, ZohoSyncStatus.PENDING)),
                eq(PageRequest.of(0, 200))
            )
        ).thenReturn(new PageImpl<>(List.of(codOrder)));
        when(customerOrderRepository.findById(401L)).thenReturn(Optional.of(codOrder));
        when(booksClient.findContactByEmail("cod@example.com")).thenReturn(Map.of("contact_id", "cust-cod"));
        when(booksClient.createInvoiceFromSalesOrder("so-cod-page"))
            .thenReturn(Map.of("invoice", Map.of("invoice_id", "inv-cod-page")));
        when(booksClient.getInvoice("inv-cod-page"))
            .thenReturn(Map.of("invoice", Map.of("status", "sent", "balance", 99)));
        when(booksClient.createCustomerPayment(any())).thenReturn(Map.of("payment", Map.of("payment_id", "pay-cod")));

        Map<String, Object> result = service.retryFailedOrders();

        assertThat(result.get("success")).isEqualTo(1);
        assertThat(codOrder.getZohoSyncStatus()).isEqualTo(ZohoSyncStatus.SYNCED);
    }

    // --- pullInvoiceStatusOnly ---

    @Test
    void pullInvoiceStatusOnly_successUpdatesOrderAndSyncsEvents() {
        CustomerOrder order = new CustomerOrder()
            .id(501L)
            .status(OrderStatus.CONFIRMED)
            .zohoInvoiceId("inv-status");
        when(booksClient.getInvoice("inv-status"))
            .thenReturn(Map.of("invoice", Map.of("invoice_id", "inv-status", "status", "void")));

        ReflectionTestUtils.invokeMethod(service, "pullInvoiceStatusOnly", order);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(orderEventService).syncLifecycleEvents(order);
    }

    @Test
    void pullInvoiceStatusOnly_failureIsSwallowed() {
        CustomerOrder order = new CustomerOrder()
            .id(502L)
            .status(OrderStatus.CONFIRMED)
            .zohoInvoiceId("inv-fail");
        when(booksClient.getInvoice("inv-fail")).thenThrow(new RuntimeException("Zoho unavailable"));

        ReflectionTestUtils.invokeMethod(service, "pullInvoiceStatusOnly", order);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        verify(orderEventService, never()).syncLifecycleEvents(any());
    }

    @Test
    void pullInvoiceStatusOnly_blankInvoiceIdIsNoOp() {
        CustomerOrder order = new CustomerOrder().id(503L).status(OrderStatus.CONFIRMED);
        ReflectionTestUtils.invokeMethod(service, "pullInvoiceStatusOnly", order);
        verify(booksClient, never()).getInvoice(any());
    }

    // --- toBigDecimal / blankToBigDecimal ---

    @Test
    void toBigDecimal_coversNullNumberAndTextBranches() {
        assertThat((BigDecimal) ReflectionTestUtils.invokeMethod(service, "toBigDecimal", (Object) null))
            .isEqualByComparingTo("0");
        assertThat((BigDecimal) ReflectionTestUtils.invokeMethod(service, "toBigDecimal", new BigDecimal("3.25")))
            .isEqualByComparingTo("3.25");
        assertThat((BigDecimal) ReflectionTestUtils.invokeMethod(service, "toBigDecimal", 7))
            .isEqualByComparingTo("7");
        assertThat((BigDecimal) ReflectionTestUtils.invokeMethod(service, "toBigDecimal", "  42.10 "))
            .isEqualByComparingTo("42.10");
        assertThat((BigDecimal) ReflectionTestUtils.invokeMethod(service, "toBigDecimal", "   "))
            .isEqualByComparingTo("0");
    }

    @Test
    void blankToBigDecimal_returnsNullForBlankAndValueOtherwise() {
        assertThat((BigDecimal) ReflectionTestUtils.invokeMethod(service, "blankToBigDecimal", (Object) null)).isNull();
        assertThat((BigDecimal) ReflectionTestUtils.invokeMethod(service, "blankToBigDecimal", "")).isNull();
        assertThat((BigDecimal) ReflectionTestUtils.invokeMethod(service, "blankToBigDecimal", "19.99"))
            .isEqualByComparingTo("19.99");
    }

    // --- deactivateExistingForInactiveZohoItem (tail branches) ---

    @Test
    void deactivateExistingForInactiveZohoItem_deactivatesParentWhenLastVariantRemoved() {
        Product parent = new Product()
            .id(60L)
            .sku("PARENT-ONLY")
            .active(true)
            .itemStructure(ProductVariantSupport.STRUCTURE_VARIANTS)
            .variantsJson(
                """
                [{"sku":"VAR-LAST","size":"S","color":"Red","rate":1,"stock":1,"active":true,"zohoItemId":"zoho-last"}]
                """
            );
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        Map<String, Object> zohoItem = Map.of("item_id", "zoho-last", "sku", "VAR-LAST", "status", "inactive");
        ZohoSyncSummary.Builder builder = ZohoSyncSummary.builder();

        ReflectionTestUtils.invokeMethod(
            service,
            "deactivateExistingForInactiveZohoItem",
            zohoItem,
            "VAR-LAST",
            List.of(parent),
            builder,
            Optional.of(parent)
        );

        assertThat(parent.getActive()).isFalse();
        assertThat(parent.getZohoSyncError()).contains("No active variant");
    }

    @Test
    void deactivateExistingForInactiveZohoItem_keepsParentActiveWhenOtherVariantsRemain() {
        Product parent = new Product()
            .id(61L)
            .sku("PARENT-MULTI")
            .active(true)
            .itemStructure(ProductVariantSupport.STRUCTURE_VARIANTS)
            .variantsJson(
                """
                [{"sku":"VAR-A","size":"S","color":"Red","rate":1,"stock":1,"active":true,"zohoItemId":"zoho-a"},
                 {"sku":"VAR-B","size":"M","color":"Blue","rate":1,"stock":1,"active":true,"zohoItemId":"zoho-b"}]
                """
            );
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        Map<String, Object> zohoItem = Map.of("item_id", "zoho-a", "sku", "VAR-A", "status", "inactive");
        ZohoSyncSummary.Builder builder = ZohoSyncSummary.builder();

        ReflectionTestUtils.invokeMethod(
            service,
            "deactivateExistingForInactiveZohoItem",
            zohoItem,
            "VAR-A",
            List.of(parent),
            builder,
            Optional.of(parent)
        );

        assertThat(parent.getActive()).isTrue();
    }

    @Test
    void deactivateExistingForInactiveZohoItem_deactivatesBySkuWhenItemIdMissing() {
        Product parent = new Product()
            .id(62L)
            .sku("PARENT-SKU-ONLY")
            .active(true)
            .itemStructure(ProductVariantSupport.STRUCTURE_VARIANTS)
            .variantsJson(
                """
                [{"sku":"VAR-NO-ID","size":"S","color":"Red","rate":1,"stock":1,"active":true}]
                """
            );
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        Map<String, Object> zohoItem = Map.of("sku", "VAR-NO-ID", "status", "inactive");
        ZohoSyncSummary.Builder builder = ZohoSyncSummary.builder();

        ReflectionTestUtils.invokeMethod(
            service,
            "deactivateExistingForInactiveZohoItem",
            zohoItem,
            "VAR-NO-ID",
            List.of(parent),
            builder,
            Optional.of(parent)
        );

        assertThat(parent.getActive()).isFalse();
    }

    @Test
    void deactivateExistingForInactiveZohoItem_alreadyInactiveSimpleProductIsNoOp() {
        Product simple = new Product().id(63L).sku("OLD-SIMPLE").active(false).itemStructure("SIMPLE");
        when(productRepository.findOneByZohoItemId("zoho-old")).thenReturn(Optional.of(simple));
        Map<String, Object> zohoItem = Map.of("item_id", "zoho-old", "sku", "OLD-SIMPLE", "status", "inactive");
        ZohoSyncSummary.Builder builder = ZohoSyncSummary.builder();

        ReflectionTestUtils.invokeMethod(
            service,
            "deactivateExistingForInactiveZohoItem",
            zohoItem,
            "OLD-SIMPLE",
            new ArrayList<>(),
            builder,
            Optional.empty()
        );

        assertThat(simple.getActive()).isFalse();
        verify(productRepository, never()).save(simple);
    }

    // --- pullInventory (first page) ---

    @Test
    void pullInventory_firstPageUpdatesStockAndStopsWhenNoMorePages() {
        Product product = new Product().id(1L).sku("SKU-PULL").zohoItemId("zoho-pull").stock(2);
        when(productRepository.findAll()).thenReturn(List.of());
        when(productRepository.findOneByZohoItemId("zoho-pull")).thenReturn(Optional.of(product));
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(booksClient.listItems(1))
            .thenReturn(new ZohoBooksClient.ItemPage(List.of(Map.of("item_id", "zoho-pull", "sku", "SKU-PULL", "stock_on_hand", 15)), false));

        Map<String, Object> result = service.pullInventory();

        assertThat(result.get("success")).isEqualTo(1);
        assertThat(product.getStock()).isEqualTo(15);
        verify(booksClient, never()).listItems(2);
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

    private static RestClientResponseException invalidContactNameLookupError() {
        return new RestClientResponseException(
            "bad name",
            400,
            "Bad Request",
            null,
            "{\"message\":\"Invalid value passed for contact_name_contains\"}".getBytes(),
            null
        );
    }
}
