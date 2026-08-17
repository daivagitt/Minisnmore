package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
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
import com.miniandmore.app.domain.enumeration.ZohoSyncStatus;
import com.miniandmore.app.repository.CustomerOrderRepository;
import com.miniandmore.app.repository.CustomerProfileRepository;
import com.miniandmore.app.repository.OrderItemRepository;
import com.miniandmore.app.repository.ProductRepository;
import com.miniandmore.app.repository.ZohoSettingRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
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

    private final ObjectMapper objectMapper = new ObjectMapper();

    private ZohoSyncService service;

    @BeforeEach
    void setUpZohoProperties() {
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
                orderItemRepository
            );
        ApplicationProperties.Zoho zoho = new ApplicationProperties.Zoho();
        zoho.setSyncProductStock(true);
        lenient().when(applicationProperties.getZoho()).thenReturn(zoho);
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
                BigDecimal.valueOf(499).equals(payload.get("rate"))
            )
        );
        verify(productRepository).updateZohoSyncSuccess(eq(1L), eq("zoho-1"), eq(ZohoSyncStatus.SYNCED), any());
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
        @SuppressWarnings("unchecked")
        Map<String, Object> shippingAddress = (Map<String, Object>) payload.get("shipping_address");
        @SuppressWarnings("unchecked")
        Map<String, Object> billingAddress = (Map<String, Object>) payload.get("billing_address");
        assertThat(shippingAddress)
            .containsEntry("attention", "Jane Doe")
            .containsEntry("address", "42 New Street")
            .containsEntry("street2", "Apt 5")
            .containsEntry("city", "Hyderabad")
            .containsEntry("state", "Telangana")
            .containsEntry("zip", "500001")
            .containsEntry("country", "India")
            .containsEntry("phone", "9876543210");
        assertThat(billingAddress).isEqualTo(shippingAddress);
        assertThat(payload.get("place_of_supply")).isEqualTo("TG");
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
        when(booksClient.createInvoiceFromSalesOrder("so-cod")).thenReturn(Map.of("invoice", Map.of("invoice_id", "inv-cod")));
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
        verify(booksClient, never()).createCustomerPayment(any());
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
        when(booksClient.listDeliveryChallans("4063993000000110007")).thenReturn(List.of());

        service.pullOrderTracking(41302L);

        verify(booksClient).getSalesOrder("4063993000000110007");
        verify(customerOrderRepository).save(argThat(saved -> "2026-08-14".equals(saved.getShipmentDate()) && "DTDC".equals(saved.getTrackingCarrier())));
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
                        "rate",
                        199,
                        "stock_on_hand",
                        -3
                    )
                )
            );
        when(booksClient.listItems(2)).thenReturn(List.of());
        when(productRepository.findOneBySku("NEG-STOCK")).thenReturn(Optional.empty());
        when(productRepository.findOneByZohoItemId("zoho-neg")).thenReturn(Optional.empty());
        when(productRepository.findMaxSortOrder()).thenReturn(0);
        when(productRepository.findAllByZohoItemIdIsNotNull()).thenReturn(List.of());
        when(productRepository.findAll()).thenReturn(List.of());
        when(productFieldMapper.applyPulledItem(any(), any(), eq(false))).thenReturn(Optional.empty());
        when(productRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Map<String, Object> result = service.pullProducts();

        verify(productRepository).save(argThat(product -> Integer.valueOf(0).equals(product.getStock())));
        org.junit.jupiter.api.Assertions.assertEquals(1, result.get("success"));
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
                orderItemRepository
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
        ProductVariantSupport realVariantSupport = new ProductVariantSupport(objectMapper);
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
                orderItemRepository
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
        verify(productRepository).save(argThat(saved ->
            saved.getVariantsJson().contains("zoho-s") && saved.getVariantsJson().contains("zoho-m")
        ));
        verify(productRepository).updateZohoSyncSuccess(eq(20L), any(), eq(ZohoSyncStatus.SYNCED), any());
    }

    @Test
    void pullProducts_mergesVariantSkuIntoParentMatrix() {
        ProductVariantSupport realVariantSupport = new ProductVariantSupport(objectMapper);
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
                orderItemRepository
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

        verify(productRepository).save(argThat(saved ->
            saved.getVariantsJson().contains("zoho-var") &&
            Integer.valueOf(5).equals(saved.getStock()) &&
            saved.getVariantsJson().contains("\"stock\":5")
        ));
    }

    @Test
    void syncOrder_variants_usesVariantZohoItemId() {
        ProductVariantSupport realVariantSupport = new ProductVariantSupport(objectMapper);
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
                orderItemRepository
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
}
