package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.lenient;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.client.ZohoBooksClient;
import com.miniandmore.app.config.ApplicationProperties;
import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.OrderItem;
import com.miniandmore.app.domain.Product;
import com.miniandmore.app.domain.enumeration.OrderStatus;
import com.miniandmore.app.domain.enumeration.PaymentStatus;
import com.miniandmore.app.domain.enumeration.ZohoSyncStatus;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import com.miniandmore.app.repository.CustomerOrderRepository;
import com.miniandmore.app.repository.CustomerProfileRepository;
import com.miniandmore.app.repository.OrderItemRepository;
import com.miniandmore.app.repository.ProductRepository;
import com.miniandmore.app.repository.ZohoSettingRepository;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientResponseException;

@ExtendWith(MockitoExtension.class)
class ZohoSyncServiceGapTest {

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
    private ZohoBooksAccountResolver accountResolver;
    private ZohoCatalogStockAdjuster stockAdjuster;
    private ZohoStockReconcileService stockReconcileService;

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
        lenient().when(customerOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void syncOrder_entityDiscount_includesDiscountFieldsOnSalesOrder() {
        Product product = new Product().id(10L).sku("SKU-10").zohoItemId("zoho-item-10");
        OrderItem line = new OrderItem().productId("10").productName("Dress").qty(1).price(BigDecimal.valueOf(1000));
        CustomerOrder order = new CustomerOrder()
            .id(201L)
            .orderNumber("MNM-DISC")
            .userLogin("buyer@example.com")
            .paymentStatus(PaymentStatus.PAID)
            .status(OrderStatus.CONFIRMED)
            .discount(BigDecimal.valueOf(75))
            .shipping(BigDecimal.ZERO);
        order.addItems(line);
        line.setOrder(order);

        when(customerOrderRepository.findById(201L)).thenReturn(Optional.of(order));
        when(orderItemRepository.findAllByOrderId(201L)).thenReturn(List.of(line));
        when(booksClient.findContactByEmail("buyer@example.com")).thenReturn(Map.of("contact_id", "cust-1"));
        when(booksClient.createSalesOrder(any())).thenReturn(Map.of("salesorder", Map.of("salesorder_id", "so-disc")));
        when(booksClient.createInvoiceFromSalesOrder("so-disc")).thenReturn(Map.of("invoice", Map.of("invoice_id", "inv-disc")));
        when(booksClient.getInvoice("inv-disc")).thenReturn(Map.of("invoice", Map.of("status", "sent", "balance", 0)));
        when(booksClient.markInvoiceAsSent("inv-disc")).thenReturn(Map.of());
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));

        service.syncOrder(201L);

        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(booksClient).createSalesOrder(payloadCaptor.capture());
        Map<String, Object> payload = payloadCaptor.getValue();
        assertThat(payload.get("discount_type")).isEqualTo("entity_level");
        assertThat(new BigDecimal(String.valueOf(payload.get("discount")))).isEqualByComparingTo("75");
        assertThat(payload.get("is_discount_before_tax")).isEqualTo(true);
    }

    @Test
    void syncOrder_codFeeWithGstTaxId_addsTaxIdToCodHandlingLine() {
        ApplicationProperties.Zoho zoho = new ApplicationProperties.Zoho();
        zoho.setGstTaxId("tax-gst-18");
        when(applicationProperties.getZoho()).thenReturn(zoho);

        Product product = new Product().id(10L).sku("SKU-10").zohoItemId("zoho-item-10");
        OrderItem line = new OrderItem().productId("10").productName("Dress").qty(1).price(BigDecimal.valueOf(1000));
        CustomerOrder order = new CustomerOrder()
            .id(202L)
            .orderNumber("MNM-COD-GST")
            .userLogin("buyer@example.com")
            .paymentMethod("cod")
            .paymentStatus(PaymentStatus.PENDING)
            .status(OrderStatus.CONFIRMED)
            .codFee(BigDecimal.valueOf(49))
            .shipping(BigDecimal.ZERO)
            .discount(BigDecimal.ZERO);
        order.addItems(line);
        line.setOrder(order);

        when(customerOrderRepository.findById(202L)).thenReturn(Optional.of(order));
        when(orderItemRepository.findAllByOrderId(202L)).thenReturn(List.of(line));
        when(booksClient.findContactByEmail("buyer@example.com")).thenReturn(Map.of("contact_id", "cust-1"));
        when(booksClient.createSalesOrder(any())).thenReturn(Map.of("salesorder", Map.of("salesorder_id", "so-cod-gst")));
        when(booksClient.createInvoiceFromSalesOrder("so-cod-gst")).thenReturn(Map.of("invoice", Map.of("invoice_id", "inv-cod-gst")));
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));

        service.syncOrder(202L);

        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(booksClient).createSalesOrder(payloadCaptor.capture());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lines = (List<Map<String, Object>>) payloadCaptor.getValue().get("line_items");
        assertThat(lines).anyMatch(row -> "COD handling fee".equals(row.get("name")) && "tax-gst-18".equals(row.get("tax_id")));
    }

    @Test
    void syncOrder_invalidEmailContact_skipsLookupAndCreatesContact() {
        Product product = new Product().id(10L).sku("SKU-10").zohoItemId("zoho-item-10");
        OrderItem line = new OrderItem().productId("10").productName("Dress").qty(1).price(BigDecimal.valueOf(500));
        CustomerOrder order = new CustomerOrder()
            .id(203L)
            .orderNumber("MNM-BAD-EMAIL")
            .userLogin("buyer@example.com")
            .paymentStatus(PaymentStatus.PAID)
            .status(OrderStatus.CONFIRMED)
            .shippingAddressJson("{\"full_name\":\"Jane\",\"email\":\"buyer@example.com\",\"phone\":\"9876543210\",\"line1\":\"L1\",\"city\":\"City\",\"state\":\"TG\",\"pincode\":\"500001\"}");
        order.addItems(line);
        line.setOrder(order);

        when(customerOrderRepository.findById(203L)).thenReturn(Optional.of(order));
        when(orderItemRepository.findAllByOrderId(203L)).thenReturn(List.of(line));
        when(booksClient.findContactByEmail("buyer@example.com"))
            .thenThrow(
                new RestClientResponseException(
                    "invalid email",
                    400,
                    "Bad Request",
                    null,
                    "{\"message\":\"Invalid value passed for email\"}".getBytes(),
                    null
                )
            );
        when(booksClient.findContactByName(any())).thenReturn(null);
        when(booksClient.createContact(any())).thenReturn(Map.of("contact", Map.of("contact_id", "cust-new")));
        when(booksClient.createSalesOrder(any())).thenReturn(Map.of("salesorder", Map.of("salesorder_id", "so-email")));
        when(booksClient.createInvoiceFromSalesOrder("so-email")).thenReturn(Map.of("invoice", Map.of("invoice_id", "inv-email")));
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));

        service.syncOrder(203L);

        verify(booksClient).createContact(any());
        verify(booksClient).findContactByEmail("buyer@example.com");
    }

    @Test
    void syncOrder_duplicateContactNameOnCreate_reusesExistingContact() {
        Product product = new Product().id(10L).sku("SKU-10").zohoItemId("zoho-item-10");
        OrderItem line = new OrderItem().productId("10").productName("Dress").qty(1).price(BigDecimal.valueOf(500));
        CustomerOrder order = new CustomerOrder()
            .id(204L)
            .orderNumber("MNM-DUP-CONTACT")
            .userLogin("buyer@example.com")
            .paymentStatus(PaymentStatus.PAID)
            .status(OrderStatus.CONFIRMED);
        order.addItems(line);
        line.setOrder(order);

        when(customerOrderRepository.findById(204L)).thenReturn(Optional.of(order));
        when(orderItemRepository.findAllByOrderId(204L)).thenReturn(List.of(line));
        when(booksClient.findContactByEmail("buyer@example.com")).thenReturn(null);
        when(booksClient.findContactByName(any())).thenReturn(null).thenReturn(Map.of("contact_id", "cust-dup"));
        when(booksClient.createContact(any()))
            .thenThrow(
                new RestClientResponseException(
                    "duplicate",
                    400,
                    "Bad Request",
                    null,
                    "{\"code\":3062,\"message\":\"already exists\"}".getBytes(),
                    null
                )
            );
        when(booksClient.createSalesOrder(any())).thenReturn(Map.of("salesorder", Map.of("salesorder_id", "so-dup")));
        when(booksClient.createInvoiceFromSalesOrder("so-dup")).thenReturn(Map.of("invoice", Map.of("invoice_id", "inv-dup")));
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));

        service.syncOrder(204L);

        verify(booksClient, org.mockito.Mockito.times(2)).findContactByName(any());
        ArgumentCaptor<Map<String, Object>> salesOrderCaptor = ArgumentCaptor.forClass(Map.class);
        verify(booksClient).createSalesOrder(salesOrderCaptor.capture());
        assertThat(salesOrderCaptor.getValue().get("customer_id")).isEqualTo("cust-dup");
    }

    @Test
    void syncOrder_variantLineWithoutVariantSku_resolvesSkuFromMatrix() {
        Product product = new Product()
            .id(20L)
            .sku("MATRIX")
            .zohoItemId("grp-matrix")
            .itemStructure(ProductVariantSupport.STRUCTURE_VARIANTS)
            .variantsJson(
                """
                [{"sku":"MATRIX-S-RED","size":"S","color":"Red","rate":999,"stock":2,"active":true,"zohoItemId":"zoho-s-red"}]
                """
            );
        OrderItem line = new OrderItem()
            .productId("20")
            .productName("Matrix Tee")
            .itemSize("S")
            .color("Red")
            .qty(1)
            .price(BigDecimal.valueOf(799));
        CustomerOrder order = new CustomerOrder()
            .id(205L)
            .orderNumber("MNM-VAR-SKU")
            .userLogin("buyer@example.com")
            .paymentStatus(PaymentStatus.PAID)
            .status(OrderStatus.CONFIRMED);
        order.addItems(line);
        line.setOrder(order);

        when(customerOrderRepository.findById(205L)).thenReturn(Optional.of(order));
        when(orderItemRepository.findAllByOrderId(205L)).thenReturn(List.of(line));
        when(booksClient.findContactByEmail("buyer@example.com")).thenReturn(Map.of("contact_id", "cust-1"));
        when(booksClient.createSalesOrder(any())).thenReturn(Map.of("salesorder", Map.of("salesorder_id", "so-var")));
        when(booksClient.createInvoiceFromSalesOrder("so-var")).thenReturn(Map.of("invoice", Map.of("invoice_id", "inv-var")));
        when(booksClient.getInvoice("inv-var")).thenReturn(Map.of("invoice", Map.of("status", "sent", "balance", 0)));
        when(booksClient.markInvoiceAsSent("inv-var")).thenReturn(Map.of());
        when(productRepository.findById(20L)).thenReturn(Optional.of(product));

        service.syncOrder(205L);

        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(booksClient).createSalesOrder(payloadCaptor.capture());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lines = (List<Map<String, Object>>) payloadCaptor.getValue().get("line_items");
        assertThat(lines.get(0).get("description")).isEqualTo("SKU: MATRIX-S-RED");
        assertThat(lines.get(0).get("item_id")).isEqualTo("zoho-s-red");
    }

    @Test
    void pullInventory_itemPageWithoutMorePages_stopsAfterFirstPage() {
        Product product = new Product().id(1L).sku("SKU-1").zohoItemId("zoho-1").stock(1);
        when(productRepository.findAll()).thenReturn(List.of());
        when(productRepository.findOneByZohoItemId("zoho-1")).thenReturn(Optional.of(product));
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(booksClient.listItems(1))
            .thenReturn(new ZohoBooksClient.ItemPage(List.of(Map.of("item_id", "zoho-1", "sku", "SKU-1", "stock_on_hand", 9)), false));

        Map<String, Object> result = service.pullInventory();

        assertThat(result.get("success")).isEqualTo(1);
        verify(booksClient, never()).listItems(2);
        assertThat(product.getStock()).isEqualTo(9);
    }

    @Test
    void pullProducts_reusesExistingVariantParentBySlugAndDeactivatesSimpleDuplicate() {
        Product existingParent = new Product()
            .id(90L)
            .sku("HORIZON-SET")
            .slug("horizon-set")
            .itemStructure(ProductVariantSupport.STRUCTURE_VARIANTS)
            .variantsJson("[]")
            .active(true);
        Product simpleDuplicate = new Product().id(91L).sku("HORIZON-S-NAVY").active(true).itemStructure("SIMPLE");
        Map<String, Object> variant = groupedZohoItem("zoho-s", "HORIZON-S-NAVY", "S", "Navy", 799, 3);

        when(zohoSettingRepository.findActiveOrLatest()).thenReturn(Optional.empty());
        when(booksClient.listItems(1)).thenReturn(List.of(variant));
        when(booksClient.listItems(2)).thenReturn(List.of());
        when(booksClient.getItem("zoho-s")).thenReturn(Map.of("item", variant));
        when(productRepository.findAll()).thenReturn(List.of(existingParent));
        when(productRepository.findAllByZohoItemIdIsNotNull()).thenReturn(List.of());
        lenient().when(productRepository.findMaxSortOrder()).thenReturn(10);
        lenient().when(productRepository.findOneBySku("HORIZON-SET")).thenReturn(Optional.of(existingParent));
        lenient().when(productRepository.findOneBySlug("horizon-set")).thenReturn(Optional.of(existingParent));
        when(productRepository.findOneBySku("HORIZON-S-NAVY")).thenReturn(Optional.of(simpleDuplicate));
        when(productRepository.findOneByZohoItemId(any())).thenReturn(Optional.empty());
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> result = service.pullProducts(true);

        assertThat(result.get("success")).isEqualTo(1);
        assertThat(simpleDuplicate.getActive()).isFalse();
        assertThat(existingParent.getVariantsJson()).contains("HORIZON-S-NAVY");
    }

    @Test
    void pullProduct_appliesMetadataOnlyDescriptionViaCodec() {
        Product product = new Product().id(800L).sku("META-SKU").zohoItemId("zoho-meta").active(true);
        ProductDescriptionCodec.Decoded decoded = new ProductDescriptionCodec.Decoded(
            "Category",
            "Cotton",
            List.of("S"),
            List.of("Blue"),
            null,
            null,
            "Story",
            null,
            null,
            null
        );
        when(productRepository.findById(800L)).thenReturn(Optional.of(product));
        when(zohoSettingRepository.findActiveOrLatest()).thenReturn(Optional.empty());
        when(productRepository.findAll()).thenReturn(List.of());
        when(booksClient.getItem("zoho-meta"))
            .thenReturn(
                Map.of(
                    "item",
                    Map.of(
                        "item_id",
                        "zoho-meta",
                        "sku",
                        "META-SKU",
                        "status",
                        "active",
                        "description",
                        "Fabric: Cotton",
                        "rate",
                        100,
                        "stock_on_hand",
                        1
                    )
                )
            );
        when(descriptionCodec.decode("Fabric: Cotton")).thenReturn(decoded);
        when(productRepository.findOneByZohoItemId("zoho-meta")).thenReturn(Optional.of(product));
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> result = service.pullProduct(800L);

        assertThat(result.get("success")).isEqualTo(1);
        assertThat(product.getDescription()).isNull();
        verify(descriptionCodec).applyDecoded(product, decoded);
    }

    @Test
    void syncOrder_twoLetterPlaceOfSupply_usesStateCodeDirectly() {
        Product product = new Product().id(10L).sku("SKU-10").zohoItemId("zoho-item-10");
        OrderItem line = new OrderItem().productId("10").productName("Dress").qty(1).price(BigDecimal.valueOf(500));
        CustomerOrder order = new CustomerOrder()
            .id(206L)
            .orderNumber("MNM-TG")
            .userLogin("buyer@example.com")
            .paymentStatus(PaymentStatus.PAID)
            .status(OrderStatus.CONFIRMED)
            .shippingAddressJson("{\"full_name\":\"Jane\",\"state\":\"TG\",\"line1\":\"L1\",\"city\":\"City\",\"pincode\":\"500001\"}");
        order.addItems(line);
        line.setOrder(order);

        when(customerOrderRepository.findById(206L)).thenReturn(Optional.of(order));
        when(orderItemRepository.findAllByOrderId(206L)).thenReturn(List.of(line));
        when(booksClient.findContactByEmail("buyer@example.com")).thenReturn(Map.of("contact_id", "cust-1"));
        when(booksClient.createSalesOrder(any())).thenReturn(Map.of("salesorder", Map.of("salesorder_id", "so-tg")));
        when(booksClient.createInvoiceFromSalesOrder("so-tg")).thenReturn(Map.of("invoice", Map.of("invoice_id", "inv-tg")));
        when(booksClient.getInvoice("inv-tg")).thenReturn(Map.of("invoice", Map.of("status", "sent", "balance", 0)));
        when(booksClient.markInvoiceAsSent("inv-tg")).thenReturn(Map.of());
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));

        service.syncOrder(206L);

        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(booksClient).createSalesOrder(payloadCaptor.capture());
        assertThat(payloadCaptor.getValue().get("place_of_supply")).isEqualTo("TG");
    }

    @Test
    void syncOrder_codWithOpenBalance_recordsCashCustomerPayment() {
        CustomerOrder order = new CustomerOrder()
            .id(208L)
            .orderNumber("MNM-COD-PAY")
            .userLogin("buyer@example.com")
            .paymentMethod("cod")
            .paymentStatus(PaymentStatus.PAID)
            .status(OrderStatus.CONFIRMED)
            .total(BigDecimal.valueOf(500))
            .zohoSalesOrderId("so-cod-pay");
        when(customerOrderRepository.findById(208L)).thenReturn(Optional.of(order));
        when(booksClient.findContactByEmail("buyer@example.com")).thenReturn(Map.of("contact_id", "cust-1"));
        when(booksClient.createInvoiceFromSalesOrder(eq("so-cod-pay"), any()))
            .thenReturn(Map.of("invoice", Map.of("invoice_id", "inv-cod-pay")));
        when(booksClient.getInvoice("inv-cod-pay"))
            .thenReturn(Map.of("invoice", Map.of("invoice_id", "inv-cod-pay", "status", "sent", "balance", 500)));
        when(booksClient.createCustomerPayment(any())).thenReturn(Map.of("payment", Map.of("payment_id", "pay-cod")));

        service.syncOrder(208L);

        assertThat(order.getZohoInvoiceId()).isEqualTo("inv-cod-pay");
        verify(booksClient).createCustomerPayment(org.mockito.ArgumentMatchers.argThat(payload -> "cash".equals(payload.get("payment_mode"))));
    }

    @Test
    void syncOrder_prepaidPaidInvoiceAlreadySettled_skipsCustomerPayment() {
        Product product = new Product().id(10L).sku("SKU-10").zohoItemId("zoho-item-10");
        OrderItem line = new OrderItem().productId("10").productName("Dress").qty(1).price(BigDecimal.valueOf(500));
        CustomerOrder order = new CustomerOrder()
            .id(207L)
            .orderNumber("MNM-PAID")
            .userLogin("buyer@example.com")
            .paymentStatus(PaymentStatus.PAID)
            .status(OrderStatus.CONFIRMED)
            .razorpayPaymentId("pay-1");
        order.addItems(line);
        line.setOrder(order);

        when(customerOrderRepository.findById(207L)).thenReturn(Optional.of(order));
        when(orderItemRepository.findAllByOrderId(207L)).thenReturn(List.of(line));
        when(booksClient.findContactByEmail("buyer@example.com")).thenReturn(Map.of("contact_id", "cust-1"));
        when(booksClient.createSalesOrder(any())).thenReturn(Map.of("salesorder", Map.of("salesorder_id", "so-paid")));
        when(booksClient.createInvoiceFromSalesOrder("so-paid")).thenReturn(Map.of("invoice", Map.of("invoice_id", "inv-paid")));
        when(booksClient.getInvoice("inv-paid")).thenReturn(Map.of("invoice", Map.of("status", "paid", "balance", 0)));
        when(booksClient.markInvoiceAsSent("inv-paid")).thenReturn(Map.of());
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));

        service.syncOrder(207L);

        verify(booksClient, never()).createCustomerPayment(any());
    }

    @Test
    void staticHelpers_coverFormatRateAndExceptionMessageNull() {
        String zeroRate = org.springframework.test.util.ReflectionTestUtils.invokeMethod(ZohoSyncService.class, "formatRate", (Object) null);
        assertThat(zeroRate).isEqualTo("0");
        String unknown = org.springframework.test.util.ReflectionTestUtils.invokeMethod(ZohoSyncService.class, "exceptionMessage", (Object) null);
        assertThat(unknown).isEqualTo("Unknown error");
    }

    @Test
    void markItemInactiveIfPresent_itemNotFoundInZoho_doesNotRethrow() {
        when(booksClient.markItemInactive("missing-item"))
            .thenThrow(
                new RestClientResponseException(
                    "missing",
                    400,
                    "Bad Request",
                    null,
                    "{\"code\":2006,\"message\":\"Item does not exist\"}".getBytes(),
                    null
                )
            );
        org.springframework.test.util.ReflectionTestUtils.invokeMethod(
            service,
            "markItemInactiveIfPresent",
            "missing-item",
            "variant OLD-VAR"
        );
        verify(booksClient).markItemInactive("missing-item");
    }

    @Test
    void isZohoLinked_variantMatrixWithLinkedSku_returnsTrue() {
        Product product = new Product()
            .itemStructure(ProductVariantSupport.STRUCTURE_VARIANTS)
            .variantsJson(
                """
                [{"sku":"LINK-S","size":"S","color":"Red","rate":10,"stock":1,"active":true,"zohoItemId":"zoho-link"}]
                """
            );
        boolean linked = org.springframework.test.util.ReflectionTestUtils.invokeMethod(service, "isZohoLinked", product);
        assertThat(linked).isTrue();
    }

    @Test
    void pullOrderTracking_emptySalesOrderResponse_returnsWithoutSavingChanges() {
        CustomerOrder order = new CustomerOrder()
            .id(9001L)
            .orderNumber("MNM-EMPTY-SO")
            .zohoSalesOrderId("so-empty");
        when(customerOrderRepository.findById(9001L)).thenReturn(Optional.of(order));
        when(booksClient.getSalesOrder("so-empty")).thenReturn(Map.of("salesorder", Map.of()));

        service.pullOrderTracking(9001L);

        verify(customerOrderRepository, never()).save(any());
        verify(orderEventService, never()).syncLifecycleEvents(any());
    }

    @Test
    void pullOrderTracking_salesOrderFetchFailure_doesNotPropagate() {
        CustomerOrder order = new CustomerOrder().id(9002L).zohoSalesOrderId("so-boom");
        when(customerOrderRepository.findById(9002L)).thenReturn(Optional.of(order));
        when(booksClient.getSalesOrder("so-boom")).thenThrow(new RuntimeException("Zoho unavailable"));

        service.pullOrderTracking(9002L);

        verify(customerOrderRepository, never()).save(any());
    }

    @Test
    void pullOrderTracking_withoutSalesOrder_invoiceStatusChange_syncsLifecycle() {
        CustomerOrder order = new CustomerOrder()
            .id(9003L)
            .orderNumber("MNM-INV-ONLY")
            .status(OrderStatus.CONFIRMED)
            .zohoInvoiceId("inv-only");
        when(customerOrderRepository.findById(9003L)).thenReturn(Optional.of(order));
        when(booksClient.getInvoice("inv-only"))
            .thenReturn(Map.of("invoice", Map.of("invoice_id", "inv-only", "status", "void")));
        when(customerOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.pullOrderTracking(9003L);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(orderEventService).syncLifecycleEvents(any(CustomerOrder.class));
        verify(customerOrderRepository).save(order);
    }

    @Test
    void pullAllTracking_processesEligibleOrders() {
        CustomerOrder order = new CustomerOrder().id(9010L).orderNumber("MNM-BATCH").zohoSalesOrderId("so-batch");
        when(
            customerOrderRepository.findOrdersForZohoTrackingPull(
                eq(PaymentStatus.PAID),
                any(),
                eq(OrderStatus.FULFILLED),
                eq(PageRequest.of(0, 50))
            )
        ).thenReturn(new PageImpl<>(List.of(order)));
        when(customerOrderRepository.findById(9010L)).thenReturn(Optional.of(order));
        when(booksClient.getSalesOrder("so-batch"))
            .thenReturn(Map.of("salesorder", Map.of("status", "invoiced", "tracking_number", "TRK-BATCH")));
        when(customerOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> result = service.pullAllTracking();

        assertThat(result.get("success")).isEqualTo(1);
        verify(booksClient).getSalesOrder("so-batch");
    }

    @Test
    void pullProducts_emptyCatalogPage_returnsZeroSuccess() {
        when(zohoSettingRepository.findActiveOrLatest()).thenReturn(Optional.empty());
        when(booksClient.listItems(1)).thenReturn(List.of());
        when(productRepository.findAll()).thenReturn(List.of());
        when(productRepository.findAllByZohoItemIdIsNotNull()).thenReturn(List.of());

        Map<String, Object> result = service.pullProducts(false);

        assertThat(result.get("success")).isEqualTo(0);
        verify(booksClient, never()).listItems(2);
    }

    @Test
    void pullProducts_createsVariantParentWhenCatalogHasNoMatch() {
        Map<String, Object> variant = groupedZohoItem("zoho-new-var", "NEW-S-RED", "S", "Red", 599, 2);
        variant.remove("group_name");

        when(zohoSettingRepository.findActiveOrLatest()).thenReturn(Optional.empty());
        when(booksClient.listItems(1)).thenReturn(List.of(variant));
        when(booksClient.listItems(2)).thenReturn(List.of());
        when(booksClient.getItem("zoho-new-var")).thenReturn(Map.of("item", variant));
        when(productRepository.findAll()).thenReturn(List.of());
        when(productRepository.findAllByZohoItemIdIsNotNull()).thenReturn(List.of());
        when(productRepository.findMaxSortOrder()).thenReturn(0);
        when(productRepository.findOneByZohoItemId(any())).thenReturn(Optional.empty());
        when(productRepository.findOneBySku(any())).thenReturn(Optional.empty());
        when(productRepository.findOneBySlug(any())).thenReturn(Optional.empty());
        when(productRepository.save(any())).thenAnswer(inv -> {
            Product saved = inv.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(501L);
            }
            return saved;
        });
        lenient().when(productFieldMapper.applyPulledItem(any(), any(), anyBoolean())).thenReturn(Optional.empty());

        Map<String, Object> result = service.pullProducts(true);

        assertThat(result.get("success")).isEqualTo(1);
        verify(productRepository, atLeastOnce()).save(argThat(p -> ProductVariantSupport.STRUCTURE_VARIANTS.equals(p.getItemStructure())));
    }

    @Test
    void syncProduct_variantUpdate_missingDestinationGroupAttributes_retriesWithExistingItemFields() {
        Product product = new Product()
            .id(9020L)
            .sku("MATRIX-PARENT")
            .name("Matrix Tee")
            .itemStructure(ProductVariantSupport.STRUCTURE_VARIANTS)
            .variantsJson(
                """
                [{"sku":"MATRIX-S-BLUE","size":"S","color":"Blue","rate":499,"stock":2,"active":true,"zohoItemId":"zoho-matrix-s"}]
                """
            )
            .active(true);
        when(productRepository.findOneWithEagerRelationships(9020L)).thenReturn(Optional.of(product));
        when(booksClient.getItem("zoho-matrix-s"))
            .thenReturn(
                Map.of(
                    "item",
                    Map.of(
                        "item_id",
                        "zoho-matrix-s",
                        "group_id",
                        "grp-matrix",
                        "attribute_name1",
                        "Size",
                        "attribute_option_name1",
                        "S",
                        "attribute_name2",
                        "Color",
                        "attribute_option_name2",
                        "Blue"
                    )
                )
            );
        when(booksClient.updateItem(eq("zoho-matrix-s"), any()))
            .thenThrow(
                new RestClientResponseException(
                    "missing attributes",
                    400,
                    "Bad Request",
                    null,
                    "{\"code\":700019,\"message\":\"destination group missing attributes\"}".getBytes(),
                    null
                )
            )
            .thenReturn(Map.of("item", Map.of("item_id", "zoho-matrix-s")));
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.syncProduct(9020L);

        verify(booksClient, org.mockito.Mockito.atLeast(2)).updateItem(eq("zoho-matrix-s"), any());
        verify(productRepository).updateZohoSyncSuccess(eq(9020L), any(), eq(ZohoSyncStatus.SYNCED), any());
    }

    @Test
    void fetchZohoItemDetail_blankItemId_returnsEmpty() {
        Optional<?> result = org.springframework.test.util.ReflectionTestUtils.invokeMethod(service, "fetchZohoItemDetail", "  ");
        assertThat(result).isEmpty();
    }

    @Test
    void fetchZohoItemDetailForSinglePull_identityMismatchThrows() {
        when(booksClient.getItem("zoho-a")).thenReturn(Map.of("item", Map.of("item_id", "zoho-b", "sku", "SKU-A")));
        assertThat(
            org.assertj.core.api.Assertions.catchThrowable(() ->
                org.springframework.test.util.ReflectionTestUtils.invokeMethod(service, "fetchZohoItemDetailForSinglePull", "zoho-a")
            )
        )
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("identity mismatch");
    }

    @Test
    void fetchZohoItemDetailBySkuForSinglePull_missingItemIdThrows() {
        when(booksClient.findItemBySku("SKU-MISSING")).thenReturn(Map.of("sku", "SKU-MISSING"));
        assertThat(
            org.assertj.core.api.Assertions.catchThrowable(() ->
                org.springframework.test.util.ReflectionTestUtils.invokeMethod(
                    service,
                    "fetchZohoItemDetailBySkuForSinglePull",
                    "SKU-MISSING"
                )
            )
        )
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("no item_id");
    }

    @Test
    void copyExistingGroupAttributes_mergesMissingGroupFields() {
        when(booksClient.getItem("zoho-copy")).thenReturn(
            Map.of(
                "item",
                Map.of(
                    "item_id",
                    "zoho-copy",
                    "group_id",
                    "grp-1",
                    "attribute_name1",
                    "Size",
                    "attribute_option_name1",
                    "M"
                )
            )
        );
        Map<String, Object> payload = new LinkedHashMap<>(Map.of("name", "Copied variant", "sku", "COPY-M"));
        @SuppressWarnings("unchecked")
        Map<String, Object> merged = org.springframework.test.util.ReflectionTestUtils.invokeMethod(
            service,
            "copyExistingGroupAttributes",
            "zoho-copy",
            payload
        );
        assertThat(merged).containsEntry("group_id", "grp-1").containsEntry("attribute_name1", "Size");
    }

    @Test
    void createItemSelfHealing_duplicateName_updatesExistingItem() {
        Map<String, Object> payload = Map.of("name", "Dup Tee", "sku", "DUP-SKU");
        when(booksClient.createItem(payload)).thenThrow(
            new RestClientResponseException(
                "duplicate",
                400,
                "Bad Request",
                null,
                "{\"code\":1001,\"message\":\"already exists\"}".getBytes(),
                null
            )
        );
        when(booksClient.findItemBySku("DUP-SKU")).thenReturn(Map.of("item_id", "existing-1", "sku", "DUP-SKU"));
        when(booksClient.updateItem(eq("existing-1"), eq(payload))).thenReturn(Map.of("item", Map.of("item_id", "existing-1")));

        @SuppressWarnings("unchecked")
        Map<String, Object> response = org.springframework.test.util.ReflectionTestUtils.invokeMethod(
            service,
            "createItemSelfHealing",
            payload
        );

        assertThat(response).containsKey("item");
        verify(booksClient).updateItem("existing-1", payload);
    }

    @Test
    void variantFieldHelpers_detectAndStripGroupedAttributes() {
        Map<String, Object> payload = new LinkedHashMap<>(Map.of("sku", "V-1", "group_id", "grp", "attribute_name1", "Size"));
        Boolean hasExtra = org.springframework.test.util.ReflectionTestUtils.invokeMethod(
            ZohoSyncService.class,
            "hasExtraVariantFields",
            payload
        );
        assertThat(hasExtra).isTrue();
        org.springframework.test.util.ReflectionTestUtils.invokeMethod(ZohoSyncService.class, "stripExtraVariantFields", payload);
        assertThat(payload).doesNotContainKeys("group_id", "attribute_name1");
    }

    @Test
    void copyIfMissing_skipsBlankStringValues() {
        Map<String, Object> source = Map.of("group_id", "  ");
        Map<String, Object> target = new LinkedHashMap<>();
        org.springframework.test.util.ReflectionTestUtils.invokeMethod(
            ZohoSyncService.class,
            "copyIfMissing",
            source,
            target,
            "group_id"
        );
        assertThat(target).isEmpty();
    }

    @Test
    void createItemSelfHealing_duplicateWithoutResolvableSku_rethrows() {
        Map<String, Object> payload = Map.of("name", "No Sku", "sku", " ");
        when(booksClient.createItem(payload)).thenThrow(
            new RestClientResponseException(
                "duplicate",
                400,
                "Bad Request",
                null,
                "{\"code\":1001,\"message\":\"already exists\"}".getBytes(),
                null
            )
        );

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
            org.springframework.test.util.ReflectionTestUtils.invokeMethod(service, "createItemSelfHealing", payload)
        ).isInstanceOf(RestClientResponseException.class);
    }

    @Test
    void isItemNotFound_detectsCannotBeEditedMessage() {
        RestClientResponseException notFound = new RestClientResponseException(
            "missing",
            400,
            "Bad Request",
            null,
            "{\"message\":\"This item cannot be edited because it does not exist\"}".getBytes(),
            null
        );
        Boolean itemNotFound = org.springframework.test.util.ReflectionTestUtils.invokeMethod(
            ZohoSyncService.class,
            "isItemNotFound",
            notFound
        );
        assertThat(itemNotFound).isTrue();
    }

    @Test
    void isDuplicateItemName_detectsBooksDuplicateCode() {
        RestClientResponseException duplicate = new RestClientResponseException(
            "dup",
            400,
            "Bad Request",
            null,
            "{\"code\":1001}".getBytes(),
            null
        );
        Boolean duplicateName = org.springframework.test.util.ReflectionTestUtils.invokeMethod(
            ZohoSyncService.class,
            "isDuplicateItemName",
            duplicate
        );
        assertThat(duplicateName).isTrue();
    }

    @Test
    void zohoRestErrorClassifiers_detectMessageOnlyPatterns() {
        RestClientResponseException customField = new RestClientResponseException(
            "cf",
            400,
            "Bad Request",
            null,
            "{\"message\":\"Custom field Color does not exist\"}".getBytes(),
            null
        );
        RestClientResponseException destinationGroup = new RestClientResponseException(
            "group",
            400,
            "Bad Request",
            null,
            "{\"message\":\"Destination group is missing attributes\"}".getBytes(),
            null
        );
        RestClientResponseException unsupportedVariant = new RestClientResponseException(
            "variant",
            400,
            "Bad Request",
            null,
            "{\"message\":\"Unsupported attribute_option_name value\"}".getBytes(),
            null
        );
        Boolean invalidCustomFields = org.springframework.test.util.ReflectionTestUtils.invokeMethod(
            ZohoSyncService.class,
            "isInvalidCustomFields",
            customField
        );
        Boolean destinationMissing = org.springframework.test.util.ReflectionTestUtils.invokeMethod(
            ZohoSyncService.class,
            "isDestinationGroupMissingAttributes",
            destinationGroup
        );
        Boolean unsupportedFields = org.springframework.test.util.ReflectionTestUtils.invokeMethod(
            ZohoSyncService.class,
            "isUnsupportedVariantFields",
            unsupportedVariant
        );
        assertThat(invalidCustomFields).isTrue();
        assertThat(destinationMissing).isTrue();
        assertThat(unsupportedFields).isTrue();
    }

    @Test
    void copyExistingGroupAttributes_skipsBlankAttributeValues() {
        when(booksClient.getItem("zoho-blank-attr")).thenReturn(
            Map.of(
                "item",
                Map.of(
                    "item_id",
                    "zoho-blank-attr",
                    "group_id",
                    "grp-1",
                    "attribute_name1",
                    "   ",
                    "attribute_option_name1",
                    "M"
                )
            )
        );
        Map<String, Object> payload = new LinkedHashMap<>(Map.of("sku", "BLANK-ATTR"));
        @SuppressWarnings("unchecked")
        Map<String, Object> merged = org.springframework.test.util.ReflectionTestUtils.invokeMethod(
            service,
            "copyExistingGroupAttributes",
            "zoho-blank-attr",
            payload
        );
        assertThat(merged).containsEntry("group_id", "grp-1").doesNotContainKey("attribute_name1");
    }

    @Test
    void zohoRestErrorClassifiers_detectItemAndFieldFailures() {
        RestClientResponseException notFound = new RestClientResponseException(
            "missing",
            400,
            "Bad Request",
            null,
            "{\"code\":2006}".getBytes(),
            null
        );
        RestClientResponseException customField = new RestClientResponseException(
            "cf",
            400,
            "Bad Request",
            null,
            "{\"code\":120100}".getBytes(),
            null
        );
        RestClientResponseException destinationGroup = new RestClientResponseException(
            "group",
            400,
            "Bad Request",
            null,
            "{\"code\":700019}".getBytes(),
            null
        );
        RestClientResponseException unsupportedVariant = new RestClientResponseException(
            "variant",
            400,
            "Bad Request",
            null,
            "{\"message\":\"invalid group_id\"}".getBytes(),
            null
        );
        RestClientResponseException benign = new RestClientResponseException(
            "other",
            500,
            "Error",
            null,
            "{}".getBytes(),
            null
        );
        Boolean itemNotFound = org.springframework.test.util.ReflectionTestUtils.invokeMethod(
            ZohoSyncService.class,
            "isItemNotFound",
            notFound
        );
        Boolean invalidCustomFields = org.springframework.test.util.ReflectionTestUtils.invokeMethod(
            ZohoSyncService.class,
            "isInvalidCustomFields",
            customField
        );
        Boolean destinationMissing = org.springframework.test.util.ReflectionTestUtils.invokeMethod(
            ZohoSyncService.class,
            "isDestinationGroupMissingAttributes",
            destinationGroup
        );
        Boolean unsupportedFields = org.springframework.test.util.ReflectionTestUtils.invokeMethod(
            ZohoSyncService.class,
            "isUnsupportedVariantFields",
            unsupportedVariant
        );
        Boolean benignNotFound = org.springframework.test.util.ReflectionTestUtils.invokeMethod(
            ZohoSyncService.class,
            "isItemNotFound",
            benign
        );
        assertThat(itemNotFound).isTrue();
        assertThat(invalidCustomFields).isTrue();
        assertThat(destinationMissing).isTrue();
        assertThat(unsupportedFields).isTrue();
        assertThat(benignNotFound).isFalse();
    }

    @Test
    void copyExistingGroupAttributes_blankItemId_returnsOriginalPayload() {
        Map<String, Object> payload = new LinkedHashMap<>(Map.of("sku", "ONLY-SKU"));
        @SuppressWarnings("unchecked")
        Map<String, Object> merged = org.springframework.test.util.ReflectionTestUtils.invokeMethod(
            service,
            "copyExistingGroupAttributes",
            "  ",
            payload
        );
        assertThat(merged).containsEntry("sku", "ONLY-SKU");
    }

    @Test
    void deactivateSimpleDuplicateForVariantSku_deactivatesSimpleProduct() {
        Product parent = new Product().id(1L).sku("PARENT-SKU");
        Product duplicate = new Product().id(2L).sku("CHILD-SKU").active(true);
        when(productRepository.findOneBySku("CHILD-SKU")).thenReturn(Optional.of(duplicate));
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        ZohoSyncSummary.Builder builder = ZohoSyncSummary.builder();

        Product deactivated = org.springframework.test.util.ReflectionTestUtils.invokeMethod(
            service,
            "deactivateSimpleDuplicateForVariantSku",
            parent,
            "CHILD-SKU",
            builder
        );

        assertThat(deactivated).isNotNull();
        assertThat(deactivated.getActive()).isFalse();
        assertThat(deactivated.getZohoSyncError()).contains("Superseded by Zoho item group");
    }

    @Test
    void mergeColorsJsonPreferringRich_blankParentReturnsDuplicateOnly() {
        String duplicate = "[{\"name\":\"Coral\",\"imageUrls\":[\"https://cdn.example.com/c.jpg\"]}]";
        String merged = org.springframework.test.util.ReflectionTestUtils.invokeMethod(
            service,
            "mergeColorsJsonPreferringRich",
            "  ",
            duplicate
        );
        assertThat(merged).contains("Coral").contains("c.jpg");
    }

    @Test
    void deactivateExistingForInactiveZohoItem_skipsWhenDirectMatchIsVariantParent() {
        Product parent = new Product()
            .id(88L)
            .sku("MATRIX-PARENT")
            .itemStructure(ProductVariantSupport.STRUCTURE_VARIANTS);
        when(productRepository.findOneBySku("MATRIX-PARENT")).thenReturn(Optional.of(parent));
        Map<String, Object> zohoItem = Map.of("item_id", "zoho-inactive", "sku", "MATRIX-PARENT", "status", "inactive");
        ZohoSyncSummary.Builder builder = ZohoSyncSummary.builder();
        org.springframework.test.util.ReflectionTestUtils.invokeMethod(
            service,
            "deactivateExistingForInactiveZohoItem",
            zohoItem,
            "MATRIX-PARENT",
            List.of(),
            builder,
            Optional.empty()
        );
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> notices = (List<Map<String, Object>>) builder.build().toMap().get("notices");
        assertThat(notices).anyMatch(n -> String.valueOf(n.get("message")).contains("parent SKU MATRIX-PARENT"));
        verify(productRepository, never()).save(parent);
    }

    @Test
    void deactivateExistingForInactiveZohoItem_deactivatesSimpleProduct() {
        Product simple = new Product().id(89L).sku("SIMPLE-SKU").active(true).itemStructure("SIMPLE");
        when(productRepository.findOneBySku("SIMPLE-SKU")).thenReturn(Optional.of(simple));
        when(productRepository.save(simple)).thenReturn(simple);
        Map<String, Object> zohoItem = Map.of("item_id", "zoho-simple", "sku", "SIMPLE-SKU", "status", "inactive");
        ZohoSyncSummary.Builder builder = ZohoSyncSummary.builder();
        org.springframework.test.util.ReflectionTestUtils.invokeMethod(
            service,
            "deactivateExistingForInactiveZohoItem",
            zohoItem,
            "SIMPLE-SKU",
            List.of(),
            builder,
            Optional.empty()
        );
        assertThat(simple.getActive()).isFalse();
        verify(productRepository).save(simple);
    }

    @Test
    void deactivateStaleZohoProducts_deactivatesSimpleProductMissingFromPullSet() {
        Product stale = new Product().id(91L).sku("STALE-SKU").zohoItemId("zoho-stale").active(true).itemStructure("SIMPLE");
        when(productRepository.findAllByZohoItemIdIsNotNull()).thenReturn(List.of(stale));
        when(productRepository.save(stale)).thenReturn(stale);
        ZohoSyncSummary.Builder builder = ZohoSyncSummary.builder();

        org.springframework.test.util.ReflectionTestUtils.invokeMethod(
            service,
            "deactivateStaleZohoProducts",
            java.util.Set.of("OTHER-SKU"),
            java.util.Set.of("other-item"),
            List.of(),
            builder
        );

        assertThat(stale.getActive()).isFalse();
        assertThat(stale.getZohoSyncError()).contains("No longer in Zoho catalog");
        verify(productRepository).save(stale);
    }

    @Test
    void deactivateStaleZohoProducts_deactivatesVariantParentWhenNoActiveVariantsRemain() {
        Product variantParent = new Product()
            .id(93L)
            .sku("EMPTY-MATRIX")
            .itemStructure(ProductVariantSupport.STRUCTURE_VARIANTS)
            .variantsJson(
                "[{\"sku\":\"GONE-VAR\",\"size\":\"S\",\"color\":\"Red\",\"rate\":1,\"stock\":0,\"active\":false,\"zohoItemId\":\"zoho-gone\"}]"
            )
            .active(true);
        when(productRepository.findAllByZohoItemIdIsNotNull()).thenReturn(List.of());
        when(productRepository.save(variantParent)).thenReturn(variantParent);
        ZohoSyncSummary.Builder builder = ZohoSyncSummary.builder();

        org.springframework.test.util.ReflectionTestUtils.invokeMethod(
            service,
            "deactivateStaleZohoProducts",
            java.util.Set.of("OTHER"),
            java.util.Set.of("other-id"),
            List.of(variantParent),
            builder
        );

        assertThat(variantParent.getActive()).isFalse();
        assertThat(variantParent.getZohoSyncError()).contains("No variant SKUs remain");
    }

    @Test
    void deactivateStaleZohoProducts_skipsVariantParentsStillLinked() {
        Product variantParent = new Product()
            .id(92L)
            .sku("MATRIX")
            .itemStructure(ProductVariantSupport.STRUCTURE_VARIANTS)
            .variantsJson(
                "[{\"sku\":\"VAR-LIVE\",\"size\":\"S\",\"color\":\"Red\",\"rate\":1,\"stock\":1,\"active\":true,\"zohoItemId\":\"zoho-var\"}]"
            )
            .active(true);
        when(productRepository.findAllByZohoItemIdIsNotNull()).thenReturn(List.of());
        ZohoSyncSummary.Builder builder = ZohoSyncSummary.builder();

        org.springframework.test.util.ReflectionTestUtils.invokeMethod(
            service,
            "deactivateStaleZohoProducts",
            java.util.Set.of("VAR-LIVE"),
            java.util.Set.of("zoho-var"),
            List.of(variantParent),
            builder
        );

        assertThat(variantParent.getActive()).isTrue();
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

}
