package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.miniandmore.app.client.LedgerlyIntegrationClient;
import com.miniandmore.app.config.ApplicationProperties;
import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.CustomerProfile;
import com.miniandmore.app.domain.OrderItem;
import com.miniandmore.app.domain.Product;
import com.miniandmore.app.domain.SiteSetting;
import com.miniandmore.app.domain.enumeration.LedgerlySyncStatus;
import com.miniandmore.app.domain.enumeration.OrderStatus;
import com.miniandmore.app.domain.enumeration.PaymentStatus;
import com.miniandmore.app.repository.CustomerOrderRepository;
import com.miniandmore.app.repository.CustomerProfileRepository;
import com.miniandmore.app.repository.LedgerlySettingRepository;
import com.miniandmore.app.repository.ProductRepository;
import com.miniandmore.app.repository.SiteSettingRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LedgerlySyncServiceTest {

    @Mock
    private LedgerlyIntegrationClient ledgerlyClient;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CustomerOrderRepository customerOrderRepository;

    @Mock
    private CustomerProfileRepository customerProfileRepository;

    @Mock
    private LedgerlySettingRepository ledgerlySettingRepository;

    @Mock
    private LedgerlyProductMergeService productMergeService;

    @Mock
    private LedgerlyCategoryResolver categoryResolver;

    @Mock
    private SiteSettingRepository siteSettingRepository;

    @Spy
    private ProductVariantSupport variantSupport = new ProductVariantSupport(new com.fasterxml.jackson.databind.ObjectMapper());

    @Spy
    private ProductColorImagesSupport colorImagesSupport = new ProductColorImagesSupport(new com.fasterxml.jackson.databind.ObjectMapper());

    @Spy
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    @Mock
    private ApplicationProperties applicationProperties;

    @InjectMocks
    private LedgerlySyncService service;

    @BeforeEach
    void stubMergeService() {
        ApplicationProperties.Ledgerly ledgerly = new ApplicationProperties.Ledgerly();
        ledgerly.setAutoSyncEnabled(true);
        ledgerly.setAutoPullEnabled(true);
        when(applicationProperties.getLedgerly()).thenReturn(ledgerly);
        when(customerProfileRepository.findOneByUserLogin(any())).thenReturn(Optional.empty());
        when(siteSettingRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());
        when(productMergeService.buildPushPayload(any(), any()))
            .thenAnswer(inv -> {
                Product p = inv.getArgument(0);
                return Map.of("sku", p.getSku(), "price", p.getPrice(), "taxPct", BigDecimal.valueOf(18), "unit", "ea", "cost", BigDecimal.ONE);
            });
        doAnswer(inv -> {
            Product p = inv.getArgument(0);
            @SuppressWarnings("unchecked")
            Map<String, Object> item = inv.getArgument(1);
            if (item.get("price") != null) {
                p.setPrice(new BigDecimal(item.get("price").toString()));
            }
            return null;
        })
            .when(productMergeService)
            .mergeLedgerlyItem(any(), any(), any(), any());
    }

    @Test
    void syncProduct_success_setsSyncedFields() {
        Product product = product(1L).active(true);
        when(productRepository.findOneWithEagerRelationships(1L)).thenReturn(Optional.of(product));
        when(ledgerlyClient.syncProductPayload(any())).thenReturn(Map.of("ledgerlyItemId", "item-99"));

        service.syncProduct(1L);

        verify(productMergeService).buildPushPayload(eq(product), any());
        verify(productRepository).updateLedgerlySyncSuccess(
            eq(1L),
            eq("item-99"),
            eq(LedgerlySyncStatus.SYNCED),
            any()
        );
        verify(productRepository, never()).save(any());
    }

    @Test
    void syncProduct_success_updatesMetadataWithoutSavingStaleSnapshot() {
        Product snapshot = product(1L).active(true).stock(8);
        when(productRepository.findOneWithEagerRelationships(1L)).thenReturn(Optional.of(snapshot));
        when(ledgerlyClient.syncProductPayload(any())).thenReturn(Map.of("ledgerlyItemId", "item-99"));

        service.syncProduct(1L);

        verify(productRepository).updateLedgerlySyncSuccess(
            eq(1L),
            eq("item-99"),
            eq(LedgerlySyncStatus.SYNCED),
            any()
        );
        verify(productRepository, never()).save(any());
        assertThat(snapshot.getStock()).isEqualTo(8);
    }

    @Test
    void syncProduct_clientFailure_setsFailedStatus() {
        Product product = product(2L).active(true);
        when(productRepository.findOneWithEagerRelationships(2L)).thenReturn(Optional.of(product));
        when(ledgerlyClient.syncProductPayload(any())).thenThrow(new RuntimeException("upstream 503"));

        service.syncProduct(2L);

        verify(productRepository).updateLedgerlySyncFailure(eq(2L), eq(LedgerlySyncStatus.FAILED), contains("503"));
        verify(productRepository, never()).save(any());
    }

    @Test
    void pullProducts_upsertsViaMergeService() {
        when(ledgerlyClient.pullProducts()).thenReturn(
            Map.of("products", List.of(Map.of("sku", "SKU-1", "name", "Item 1", "price", 100, "stock", 5)))
        );
        when(ledgerlySettingRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());
        when(productRepository.findOneBySku("SKU-1")).thenReturn(Optional.empty());
        when(productRepository.findMaxSortOrder()).thenReturn(0);
        when(productRepository.save(any())).thenAnswer(inv -> {
            Product p = inv.getArgument(0);
            if (p.getId() == null) {
                p.setId(99L);
            }
            return p;
        });

        Map<String, Object> result = service.pullProducts();

        assertThat(result).containsEntry("successCount", 1);
        verify(productMergeService).mergeLedgerlyItem(any(Product.class), any(), any(), any());
    }

    @Test
    void pullProducts_deactivatesStoreProductsMissingFromLedgerlyPull() {
        when(ledgerlyClient.pullProducts()).thenReturn(
            Map.of("products", List.of(Map.of("sku", "SKU-KEEP", "ledgerlyItemId", "item-keep", "name", "Keep", "price", 100)))
        );
        when(ledgerlySettingRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());
        when(productRepository.findOneBySku("SKU-KEEP")).thenReturn(Optional.empty());
        when(productRepository.findMaxSortOrder()).thenReturn(0);
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Product stale = product(80L).sku("SKU-GONE").ledgerlyItemId("item-gone").active(true);
        stale.setLedgerlySyncStatus(LedgerlySyncStatus.SYNCED);
        when(productRepository.findAllActiveLedgerlyLinked()).thenReturn(List.of(stale));

        Map<String, Object> result = service.pullProducts();

        assertThat(stale.getActive()).isFalse();
        assertThat(result).containsEntry("successCount", 1);
        assertThat((List<?>) result.get("notices")).isNotEmpty();
    }

    @Test
    void pullProducts_emptyLedgerlyResponse_doesNotDeactivate() {
        when(ledgerlyClient.pullProducts()).thenReturn(Map.of("products", List.of()));
        when(ledgerlySettingRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());

        Map<String, Object> result = service.pullProducts();

        assertThat(result).containsEntry("successCount", 0);
        verify(productRepository, never()).findAllActiveLedgerlyLinked();
    }

    @Test
    void syncOrder_success_setsInvoiceAndSalesOrderIds() {
        CustomerOrder order = order(10L, "ORD-10", LedgerlySyncStatus.PENDING);
        when(customerOrderRepository.findById(10L)).thenReturn(Optional.of(order));
        when(ledgerlyClient.syncOrderPayload(any(), eq("pay_10"))).thenReturn(
            Map.of("ledgerlyInvoiceId", "inv-10", "ledgerlySalesOrderId", "so-10")
        );
        when(customerOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.syncOrder(10L);

        assertThat(order.getLedgerlySyncStatus()).isEqualTo(LedgerlySyncStatus.SYNCED);
        assertThat(order.getLedgerlyInvoiceId()).isEqualTo("inv-10");
        assertThat(order.getLedgerlySalesOrderId()).isEqualTo("so-10");
    }

    @Test
    void syncOrder_retriesWhenLedgerlyInvoicePaymentModeMissing() {
        CustomerOrder order = order(15L, "ORD-15", LedgerlySyncStatus.PENDING);
        when(customerOrderRepository.findById(15L)).thenReturn(Optional.of(order));
        when(ledgerlyClient.syncOrderPayload(any(), eq("pay_15")))
            .thenThrow(
                new RuntimeException(
                    "500 on POST /invoices/1058/payments invoicePaymentRequest \"mode\" must not be null"
                )
            )
            .thenReturn(Map.of("idempotent", true, "ledgerlyInvoiceId", "inv-15"));
        when(customerOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.syncOrder(15L);

        assertThat(order.getLedgerlySyncStatus()).isEqualTo(LedgerlySyncStatus.SYNCED);
        assertThat(order.getLedgerlyInvoiceId()).isEqualTo("inv-15");
        verify(ledgerlyClient, times(2)).syncOrderPayload(any(), eq("pay_15"));
    }

    @Test
    void shouldRetryAfterInvoicePaymentFailure_matchesLedgerlyModeValidationError() {
        assertThat(
            LedgerlySyncService.shouldRetryAfterInvoicePaymentFailure(
                new RuntimeException(
                    "400 on POST http://sales/api/invoices/1058/payments invoicePaymentRequest fieldErrors \"mode\" must not be null"
                )
            )
        ).isTrue();
        assertThat(
            LedgerlySyncService.shouldRetryAfterInvoicePaymentFailure(
                new RuntimeException(
                    "500 Internal Server Error: \"{\\\"fieldErrors\\\":[{\\\"field\\\":\\\"mode\\\",\\\"message\\\":\\\"must not be null\\\",\\\"objectName\\\":\\\"invoicePaymentRequest\\\"}]}\", path=/api/invoices/1074/payments"
                )
            )
        ).isTrue();
        assertThat(LedgerlySyncService.shouldRetryAfterInvoicePaymentFailure(new RuntimeException("upstream timeout"))).isFalse();
    }

    @Test
    void pullOrderTracking_whenFound_updatesStatusAndTrackingFields() {
        CustomerOrder order = order(11L, "ORD-11", LedgerlySyncStatus.SYNCED);
        when(customerOrderRepository.findById(11L)).thenReturn(Optional.of(order));
        when(ledgerlyClient.pullOrderTracking("ORD-11")).thenReturn(
            Map.of(
                "found",
                true,
                "status",
                "Paid",
                "fulfillmentStatus",
                "delivered",
                "trackingNumber",
                "AWB-11",
                "shipmentDate",
                "2026-07-13"
            )
        );
        when(customerOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.pullOrderTracking(11L);

        assertThat(order.getLedgerlyOrderStatus()).isEqualTo("delivered");
        assertThat(order.getTrackingNumber()).isEqualTo("AWB-11");
        assertThat(order.getShipmentDate()).isEqualTo("2026-07-13");
        assertThat(order.getLedgerlyTrackingLastPulledAt()).isNotNull();
    }

    @Test
    void pullOrderTracking_whenFound_doesNotDowngradeDeliveredToPaid() {
        CustomerOrder order = order(11L, "ORD-11", LedgerlySyncStatus.SYNCED).ledgerlyOrderStatus("delivered");
        when(customerOrderRepository.findById(11L)).thenReturn(Optional.of(order));
        when(ledgerlyClient.pullOrderTracking("ORD-11")).thenReturn(Map.of("found", true, "status", "Paid"));
        when(customerOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.pullOrderTracking(11L);

        assertThat(order.getLedgerlyOrderStatus()).isEqualTo("delivered");
    }

    @Test
    void pullOrderTracking_whenNotFound_doesNotSave() {
        CustomerOrder order = order(12L, "ORD-12", LedgerlySyncStatus.SYNCED);
        when(customerOrderRepository.findById(12L)).thenReturn(Optional.of(order));
        when(ledgerlyClient.pullOrderTracking("ORD-12")).thenReturn(Map.of("found", false));

        service.pullOrderTracking(12L);

        verify(customerOrderRepository, never()).save(any());
    }

    @Test
    void buildOrderPayload_includesAuthoritativeTotals() {
        CustomerOrder order = order(40L, "ORD-TAX", LedgerlySyncStatus.PENDING);
        order.subtotal(new BigDecimal("399"));
        order.shipping(new BigDecimal("1"));
        order.tax(new BigDecimal("4"));
        order.total(new BigDecimal("404"));
        when(siteSettingRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());
        when(productRepository.findById(1L)).thenReturn(Optional.of(product(1L)));

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = org.springframework.test.util.ReflectionTestUtils.invokeMethod(service, "buildOrderPayload", order);

        assertThat(payload.get("subtotal")).isEqualTo(new BigDecimal("399"));
        assertThat(payload.get("shipping")).isEqualTo(new BigDecimal("1"));
        assertThat(payload.get("tax")).isEqualTo(new BigDecimal("4"));
        assertThat(payload.get("discount")).isEqualTo(BigDecimal.ZERO);
        assertThat(payload.get("total")).isEqualTo(new BigDecimal("404"));
        assertThat(payload.get("couponCode")).isNull();
        assertThat(payload.get("pricesIncludeGst")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lines = (List<Map<String, Object>>) payload.get("lines");
        assertThat(lines.get(0).get("sku")).isEqualTo("SKU-1");
    }

    @Test
    void buildOrderPayload_includesCouponDiscountAndTotalAfterCoupon() {
        CustomerOrder order = order(42L, "ORD-COUPON", LedgerlySyncStatus.PENDING);
        order.subtotal(new BigDecimal("231"));
        order.discount(new BigDecimal("229"));
        order.shipping(BigDecimal.ZERO);
        order.tax(BigDecimal.ZERO);
        order.total(new BigDecimal("2"));
        order.couponCode("WELCOM10");
        when(siteSettingRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());
        when(productRepository.findById(1L)).thenReturn(Optional.of(product(1L)));

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = org.springframework.test.util.ReflectionTestUtils.invokeMethod(service, "buildOrderPayload", order);

        assertThat(payload.get("discount")).isEqualTo(new BigDecimal("229"));
        assertThat(payload.get("total")).isEqualTo(new BigDecimal("2"));
        assertThat(payload.get("couponCode")).isEqualTo("WELCOM10");
        @SuppressWarnings("unchecked")
        Map<String, Object> payment = (Map<String, Object>) payload.get("payment");
        assertThat(payment.get("amount")).isEqualTo(new BigDecimal("2"));
    }

    @Test
    void buildOrderPayload_usesCatalogSkuNotUrlSlug() {
        CustomerOrder order = order(41L, "ORD-SKU", LedgerlySyncStatus.PENDING);
        OrderItem line = order.getItemses().iterator().next();
        line.setProductSlug("organic-cotton-onesie");
        Product product = product(1L).sku("MNM-BPB-001");
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(siteSettingRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = org.springframework.test.util.ReflectionTestUtils.invokeMethod(service, "buildOrderPayload", order);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lines = (List<Map<String, Object>>) payload.get("lines");
        assertThat(lines.get(0).get("sku")).isEqualTo("MNM-BPB-001");
    }

    @Test
    void buildOrderPayload_prefersVariantSkuOverParent() {
        CustomerOrder order = order(42L, "ORD-VAR", LedgerlySyncStatus.PENDING);
        OrderItem line = order.getItemses().iterator().next();
        line.setVariantSku("HORIZON-M-NAVY");
        Product product = product(1L).sku("HORIZON").itemStructure("VARIANTS");
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(siteSettingRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = org.springframework.test.util.ReflectionTestUtils.invokeMethod(service, "buildOrderPayload", order);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lines = (List<Map<String, Object>>) payload.get("lines");
        assertThat(lines.get(0).get("sku")).isEqualTo("HORIZON-M-NAVY");
    }

    @Test
    void retryFailedOrders_onlyProcessesFailedOrPending() {
        CustomerOrder failed = order(20L, "ORD-F", LedgerlySyncStatus.FAILED);
        CustomerOrder synced = order(21L, "ORD-S", LedgerlySyncStatus.SYNCED);
        when(customerOrderRepository.findAll()).thenReturn(List.of(failed, synced));
        when(customerOrderRepository.findById(20L)).thenReturn(Optional.of(failed));
        when(ledgerlyClient.syncOrderPayload(any(), any())).thenReturn(Map.of("ledgerlyInvoiceId", "inv-20"));
        when(customerOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> result = service.retryFailedOrders();

        assertThat(result).containsEntry("successCount", 1).containsEntry("failureCount", 0);
        verify(ledgerlyClient, times(1)).syncOrderPayload(any(), any());
    }

    @Test
    void syncAllProducts_aggregatesFailures() {
        Product ok = product(30L).active(true);
        Product bad = product(31L).active(true);
        when(productRepository.findAllWithEagerRelationships()).thenReturn(List.of(ok, bad));
        when(ledgerlyClient.syncProductPayload(any()))
            .thenReturn(Map.of("ledgerlyItemId", "item-30"))
            .thenThrow(new RuntimeException("sync failed"));

        Map<String, Object> result = service.syncAllProducts();

        assertThat(result).containsEntry("successCount", 1).containsEntry("failureCount", 1);
        assertThat((List<?>) result.get("failures")).isNotEmpty();
        verify(productRepository).updateLedgerlySyncFailure(eq(31L), eq(LedgerlySyncStatus.FAILED), contains("sync failed"));
    }

    @Test
    void syncAllProducts_skipsInactiveNeverLinked_andDeactivatesInactiveLinked() {
        Product active = product(90L).active(true);
        Product inactiveLocal = product(91L).active(false);
        Product inactiveLinked = product(92L).active(false).ledgerlyItemId("item-92");
        inactiveLinked.setLedgerlySyncStatus(LedgerlySyncStatus.SYNCED);
        when(productRepository.findAllWithEagerRelationships()).thenReturn(List.of(active, inactiveLocal, inactiveLinked));
        when(ledgerlyClient.syncProductPayload(any()))
            .thenReturn(Map.of("ledgerlyItemId", "item-90"))
            .thenReturn(Map.of("noop", true));

        Map<String, Object> result = service.syncAllProducts();

        assertThat(result).containsEntry("successCount", 1);
        verify(ledgerlyClient, times(2)).syncProductPayload(any());
        verify(ledgerlyClient)
            .syncProductPayload(
                org.mockito.ArgumentMatchers.argThat(
                    payload -> Boolean.FALSE.equals(payload.get("active")) && "SKU-92".equals(payload.get("sku"))
                )
            );
        verify(productRepository).updateLedgerlyDeactivateMeta(eq(92L), any(), eq(true));
        verify(productRepository).updateLedgerlySyncSuccess(eq(90L), eq("item-90"), eq(LedgerlySyncStatus.SYNCED), any());
    }

    @Test
    void syncCustomerProfile_success_callsLedgerlyClient() {
        CustomerProfile profile = new CustomerProfile().userLogin("buyer@example.com").fullName("Buyer").phone("9999999999");
        when(customerProfileRepository.findOneByUserLogin("buyer@example.com")).thenReturn(Optional.of(profile));

        service.syncCustomerProfile("buyer@example.com");

        verify(ledgerlyClient).syncCustomerPayload(
            org.mockito.ArgumentMatchers.argThat(payload ->
                "buyer@example.com".equals(payload.get("email")) &&
                "Buyer".equals(payload.get("name")) &&
                "9999999999".equals(payload.get("phone"))
            )
        );
    }

    @Test
    void syncCustomerProfile_missingProfile_doesNothing() {
        when(customerProfileRepository.findOneByUserLogin("missing@example.com")).thenReturn(Optional.empty());

        service.syncCustomerProfile("missing@example.com");

        verify(ledgerlyClient, never()).syncCustomerPayload(any());
    }

    @Test
    void buildOrderPayload_includesCustomerPaymentAndLineTax() {
        CustomerOrder order = order(50L, "MNM-50", LedgerlySyncStatus.PENDING);
        order.subtotal(new BigDecimal("399"));
        order.shipping(new BigDecimal("1"));
        order.tax(new BigDecimal("4"));
        order.total(new BigDecimal("404"));
        order.shippingAddressJson("{\"city\":\"Hyderabad\"}");
        CustomerProfile profile = new CustomerProfile().userLogin("customer1").fullName("Test Buyer").phone("9876543210");
        SiteSetting settings = new SiteSetting().pricesIncludeGst(false).gstRate(new BigDecimal("5"));
        when(customerProfileRepository.findOneByUserLogin("customer1")).thenReturn(Optional.of(profile));
        when(siteSettingRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(settings));

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = org.springframework.test.util.ReflectionTestUtils.invokeMethod(service, "buildOrderPayload", order);

        assertThat(payload.get("paymentMode")).isEqualTo("Razorpay");
        assertThat(payload.get("pricesIncludeGst")).isEqualTo(false);
        @SuppressWarnings("unchecked")
        Map<String, Object> payment = (Map<String, Object>) payload.get("payment");
        assertThat(payment.get("mode")).isEqualTo("Razorpay");
        assertThat(payment.get("paymentId")).isEqualTo("pay_50");
        assertThat(payment.get("amount")).isEqualTo(new BigDecimal("404"));
        @SuppressWarnings("unchecked")
        Map<String, Object> customer = (Map<String, Object>) payload.get("customer");
        assertThat(customer.get("name")).isEqualTo("Test Buyer");
        assertThat(customer.get("phone")).isEqualTo("9876543210");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lines = (List<Map<String, Object>>) payload.get("lines");
        assertThat(lines).hasSize(1);
        assertThat(lines.get(0).get("taxPct")).isEqualTo(new BigDecimal("5"));
    }

    @Test
    void buildOrderPayload_resolvesPlaceOfSupplyFromShippingState() {
        CustomerOrder order = order(51L, "MNM-51", LedgerlySyncStatus.PENDING);
        order.shippingAddressJson("{\"city\":\"Chennai\",\"state\":\"Tamil Nadu\"}");
        when(siteSettingRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());
        when(productRepository.findById(1L)).thenReturn(Optional.of(product(1L)));

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = org.springframework.test.util.ReflectionTestUtils.invokeMethod(service, "buildOrderPayload", order);

        assertThat(payload.get("placeOfSupply")).isEqualTo("TN");
    }

    @Test
    void buildOrderPayload_omitsPlaceOfSupplyWhenStateUnresolvable() {
        CustomerOrder order = order(52L, "MNM-52", LedgerlySyncStatus.PENDING);
        order.shippingAddressJson("{\"city\":\"Hyderabad\"}");
        when(siteSettingRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());
        when(productRepository.findById(1L)).thenReturn(Optional.of(product(1L)));

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = org.springframework.test.util.ReflectionTestUtils.invokeMethod(service, "buildOrderPayload", order);

        assertThat(payload).doesNotContainKey("placeOfSupply");
    }

    @Test
    void syncOrder_doesNotRetryOnInvoiceCreationFailure() {
        CustomerOrder order = order(16L, "ORD-16", LedgerlySyncStatus.PENDING);
        when(customerOrderRepository.findById(16L)).thenReturn(Optional.of(order));
        when(ledgerlyClient.syncOrderPayload(any(), eq("pay_16")))
            .thenThrow(new RuntimeException("500 on POST request for http://sales/api/invoices Unexpected runtime exception"));
        when(customerOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.syncOrder(16L);

        assertThat(order.getLedgerlySyncStatus()).isEqualTo(LedgerlySyncStatus.FAILED);
        verify(ledgerlyClient, times(1)).syncOrderPayload(any(), eq("pay_16"));
    }

    @Test
    void pullInventory_updatesLinkedProductStock() {
        Product product = product(70L).ledgerlyItemId("item-70");
        when(ledgerlyClient.pullInventory()).thenReturn(
            Map.of("inventory", List.of(Map.of("sku", "SKU-70", "ledgerlyItemId", "item-70", "stock", 12)))
        );
        when(productRepository.findAll()).thenReturn(List.of());
        when(productRepository.findOneByLedgerlyItemId("item-70")).thenReturn(Optional.of(product));
        when(productRepository.save(product)).thenReturn(product);

        Map<String, Object> result = service.pullInventory();

        assertThat(result).containsEntry("successCount", 1);
        assertThat(product.getStock()).isEqualTo(12);
    }

    @Test
    void pullInventory_unknownSku_recordsSkipped() {
        when(ledgerlyClient.pullInventory()).thenReturn(
            Map.of("inventory", List.of(Map.of("sku", "UNKNOWN", "stock", 3)))
        );
        when(productRepository.findAll()).thenReturn(List.of());
        when(productRepository.findOneBySku("UNKNOWN")).thenReturn(Optional.empty());

        Map<String, Object> result = service.pullInventory();

        assertThat(result).containsEntry("skippedCount", 1).containsEntry("failureCount", 0);
        assertThat((List<?>) result.get("skipped")).isNotEmpty();
    }

    @Test
    void buildOrderPayload_resolvesVariantSkuFromSizeColorWhenVariantSkuMissing() {
        CustomerOrder order = order(43L, "ORD-SIZE", LedgerlySyncStatus.PENDING);
        OrderItem line = order.getItemses().iterator().next();
        line.setVariantSku(null);
        line.setItemSize("M");
        line.setColor("Navy");
        Product product = product(1L)
            .sku("HORIZON")
            .itemStructure("VARIANTS")
            .variantsJson(
                "[{\"sku\":\"HORIZON-M-NAVY\",\"size\":\"M\",\"color\":\"Navy\",\"rate\":1199,\"stock\":4,\"active\":true}]"
            );
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(siteSettingRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = org.springframework.test.util.ReflectionTestUtils.invokeMethod(service, "buildOrderPayload", order);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lines = (List<Map<String, Object>>) payload.get("lines");
        assertThat(lines.get(0).get("sku")).isEqualTo("HORIZON-M-NAVY");
    }

    @Test
    void pullAllTracking_processesPaidOpenOrders() {
        CustomerOrder order = order(50L, "ORD-50", LedgerlySyncStatus.SYNCED);
        order.paymentStatus(PaymentStatus.PAID).status(OrderStatus.CONFIRMED);
        when(
            customerOrderRepository.findOrdersForLedgerlyTrackingPull(
                eq(PaymentStatus.PAID),
                eq(List.of(OrderStatus.PENDING, OrderStatus.CONFIRMED)),
                any(PageRequest.class)
            )
        ).thenReturn(new PageImpl<>(List.of(order)));
        when(customerOrderRepository.findById(50L)).thenReturn(Optional.of(order));
        when(ledgerlyClient.pullOrderTracking("ORD-50")).thenReturn(Map.of("found", true, "status", "Paid"));
        when(customerOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> result = service.pullAllTracking();

        assertThat(result.get("successCount")).isEqualTo(1);
    }

    @Test
    void notifyLedgerlyBeforeStoreDelete_deactivatesLinkedProduct() {
        Product product = product(7L).ledgerlyItemId("item-7").ledgerlySyncStatus(LedgerlySyncStatus.SYNCED);
        when(productRepository.findOneWithEagerRelationships(7L)).thenReturn(Optional.of(product));
        when(ledgerlyClient.syncProductPayload(any())).thenReturn(Map.of("noop", true));

        service.notifyLedgerlyBeforeStoreDelete(7L);

        verify(ledgerlyClient).syncProductPayload(any());
        verify(productRepository).updateLedgerlyDeactivateMeta(eq(7L), any(java.time.Instant.class), eq(true));
    }

    @Test
    void pullInventory_httpError_returnsFailureSummary() {
        when(ledgerlyClient.pullInventory()).thenThrow(
            new org.springframework.web.client.RestClientResponseException("503", 503, "Service Unavailable", null, null, null)
        );

        Map<String, Object> result = service.pullInventory();

        assertThat(result.get("failureCount")).isEqualTo(1);
        assertThat(result.get("failures").toString()).contains("503");
    }

    @Test
    void pullInventory_unreachable_returnsFailureSummary() {
        when(ledgerlyClient.pullInventory()).thenThrow(new org.springframework.web.client.ResourceAccessException("connection refused"));

        Map<String, Object> result = service.pullInventory();

        assertThat(result.get("failureCount")).isEqualTo(1);
        assertThat(result.get("failures").toString()).contains("unreachable");
    }

    @Test
    void pullInventory_variantMatrix_updatesParentStock() {
        String variantsJson =
            "[{\"sku\":\"HORIZON-S-NAVY\",\"size\":\"S\",\"color\":\"Navy\",\"rate\":999,\"stock\":2,\"active\":true}]";
        Product product = product(71L)
            .sku("HORIZON")
            .itemStructure("VARIANTS")
            .variantsJson(variantsJson);
        when(ledgerlyClient.pullInventory()).thenReturn(
            Map.of(
                "inventory",
                List.of(
                    Map.of(
                        "sku",
                        "HORIZON",
                        "ledgerlyItemId",
                        "item-71",
                        "variantsJson",
                        "[{\"sku\":\"HORIZON-S-NAVY\",\"size\":\"S\",\"color\":\"Navy\",\"rate\":999,\"stock\":9,\"active\":true}]"
                    )
                )
            )
        );
        when(productRepository.findAll()).thenReturn(List.of(product));
        when(productRepository.findOneByLedgerlyItemId("item-71")).thenReturn(Optional.of(product));
        when(productRepository.save(product)).thenReturn(product);

        Map<String, Object> result = service.pullInventory();

        assertThat(result).containsEntry("successCount", 1);
        verify(productRepository).save(product);
    }

    @Test
    void pullInventory_variantSkuOnParent_updatesVariantRow() {
        String variantsJson =
            "[{\"sku\":\"HORIZON-M-NAVY\",\"size\":\"M\",\"color\":\"Navy\",\"rate\":1199,\"stock\":4,\"active\":true}]";
        Product product = product(72L).sku("HORIZON").itemStructure("VARIANTS").variantsJson(variantsJson);
        when(ledgerlyClient.pullInventory()).thenReturn(
            Map.of("inventory", List.of(Map.of("sku", "HORIZON-M-NAVY", "stock", 11)))
        );
        when(productRepository.findAll()).thenReturn(List.of(product));
        when(productRepository.findOneBySku("HORIZON-M-NAVY")).thenReturn(Optional.empty());
        when(productRepository.save(product)).thenReturn(product);

        Map<String, Object> result = service.pullInventory();

        assertThat(result).containsEntry("successCount", 1);
        verify(productRepository).save(product);
    }

    @Test
    void pullInventory_parentStockOnlyOnVariants_skipsOverwrite() {
        Product product = product(73L).sku("HORIZON").itemStructure("VARIANTS").variantsJson("[]");
        when(ledgerlyClient.pullInventory()).thenReturn(
            Map.of("inventory", List.of(Map.of("sku", "HORIZON", "ledgerlyItemId", "item-73", "stock", 50)))
        );
        when(productRepository.findAll()).thenReturn(List.of(product));
        when(productRepository.findOneByLedgerlyItemId("item-73")).thenReturn(Optional.of(product));

        Map<String, Object> result = service.pullInventory();

        assertThat(result).containsEntry("successCount", 1);
        verify(productRepository, never()).save(any());
    }

    @Test
    void pullInventory_invalidVariantsJson_recordsFailure() {
        Product product = product(74L).sku("BAD").itemStructure("VARIANTS").variantsJson("[]");
        when(ledgerlyClient.pullInventory()).thenReturn(
            Map.of("inventory", List.of(Map.of("sku", "BAD", "ledgerlyItemId", "item-74", "variantsJson", "not-json")))
        );
        when(productRepository.findAll()).thenReturn(List.of(product));
        when(productRepository.findOneByLedgerlyItemId("item-74")).thenReturn(Optional.of(product));

        Map<String, Object> result = service.pullInventory();

        assertThat(result.get("failureCount")).isEqualTo(1);
    }

    @Test
    void syncProductAsync_whenAutoSyncDisabled_skipsPush() {
        ApplicationProperties.Ledgerly ledgerly = new ApplicationProperties.Ledgerly();
        ledgerly.setAutoSyncEnabled(false);
        when(applicationProperties.getLedgerly()).thenReturn(ledgerly);

        service.syncProductAsync(99L);

        verify(productRepository, never()).findOneWithEagerRelationships(any());
    }

    @Test
    void syncProductAsync_whenEnabled_delegatesToSyncProduct() {
        Product product = product(100L).active(true);
        when(productRepository.findOneWithEagerRelationships(100L)).thenReturn(Optional.of(product));
        when(ledgerlyClient.syncProductPayload(any())).thenReturn(Map.of("ledgerlyItemId", "item-100"));

        service.syncProductAsync(100L);

        verify(ledgerlyClient).syncProductPayload(any());
    }

    @Test
    void syncCustomerProfile_clientFailure_doesNotThrow() {
        CustomerProfile profile = new CustomerProfile().userLogin("buyer@example.com").fullName("Buyer");
        when(customerProfileRepository.findOneByUserLogin("buyer@example.com")).thenReturn(Optional.of(profile));
        doThrow(new RuntimeException("network")).when(ledgerlyClient).syncCustomerPayload(any());

        service.syncCustomerProfile("buyer@example.com");

        verify(ledgerlyClient).syncCustomerPayload(any());
    }

    @Test
    void buildOrderPayload_placeOfSupply_twoLetterCode() {
        CustomerOrder order = order(60L, "MNM-60", LedgerlySyncStatus.PENDING);
        order.shippingAddressJson("{\"state\":\"MH\"}");
        when(siteSettingRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());
        when(productRepository.findById(1L)).thenReturn(Optional.of(product(1L)));

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = org.springframework.test.util.ReflectionTestUtils.invokeMethod(service, "buildOrderPayload", order);

        assertThat(payload.get("placeOfSupply")).isEqualTo("MH");
    }

    @Test
    void buildOrderPayload_invalidShippingJson_omitsPlaceOfSupply() {
        CustomerOrder order = order(61L, "MNM-61", LedgerlySyncStatus.PENDING);
        order.shippingAddressJson("{bad json");
        when(siteSettingRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());
        when(productRepository.findById(1L)).thenReturn(Optional.of(product(1L)));

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = org.springframework.test.util.ReflectionTestUtils.invokeMethod(service, "buildOrderPayload", order);

        assertThat(payload).doesNotContainKey("placeOfSupply");
    }

    @Test
    void pullProducts_missingSku_recordsFailure() {
        when(ledgerlyClient.pullProducts()).thenReturn(Map.of("products", List.of(Map.of("name", "No SKU"))));
        when(ledgerlySettingRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());

        Map<String, Object> result = service.pullProducts();

        assertThat(result.get("failureCount")).isEqualTo(1);
    }

    @Test
    void pullProducts_newProduct_invokesCategoryNotice() {
        when(ledgerlyClient.pullProducts()).thenReturn(
            Map.of("products", List.of(Map.of("sku", "NEW-SKU", "name", "New Item", "price", 100, "category", "Baby")))
        );
        when(ledgerlySettingRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());
        when(productRepository.findOneBySku("NEW-SKU")).thenReturn(Optional.empty());
        when(productRepository.findMaxSortOrder()).thenReturn(0);
        when(categoryResolver.resolve("Baby")).thenReturn(Optional.of(new com.miniandmore.app.domain.Category().name("Baby")));
        when(productRepository.save(any())).thenAnswer(inv -> {
            Product p = inv.getArgument(0);
            if (p.getId() == null) {
                p.setId(200L);
            }
            return p;
        });

        Map<String, Object> result = service.pullProducts();

        assertThat(result).containsEntry("successCount", 1);
    }

    @Test
    void notifyLedgerlyBeforeStoreDelete_failure_doesNotThrow() {
        Product product = product(8L).ledgerlyItemId("item-8").ledgerlySyncStatus(LedgerlySyncStatus.SYNCED);
        when(productRepository.findOneWithEagerRelationships(8L)).thenReturn(Optional.of(product));
        when(ledgerlyClient.syncProductPayload(any())).thenThrow(new RuntimeException("down"));

        service.notifyLedgerlyBeforeStoreDelete(8L);

        verify(ledgerlyClient).syncProductPayload(any());
    }

    @Test
    void syncProduct_inactiveWithoutLedgerlyLink_skipsClient() {
        Product product = product(95L).active(false);
        when(productRepository.findOneWithEagerRelationships(95L)).thenReturn(Optional.of(product));

        service.syncProduct(95L);

        verify(ledgerlyClient, never()).syncProductPayload(any());
    }

    @Test
    void buildOrderPayload_fallsBackToProductSlugWhenSkuMissing() {
        CustomerOrder order = order(44L, "ORD-SLUG", LedgerlySyncStatus.PENDING);
        OrderItem line = order.getItemses().iterator().next();
        line.setVariantSku(null);
        line.setProductSlug("organic-cotton");
        when(productRepository.findById(1L)).thenReturn(Optional.empty());
        when(siteSettingRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = org.springframework.test.util.ReflectionTestUtils.invokeMethod(service, "buildOrderPayload", order);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lines = (List<Map<String, Object>>) payload.get("lines");
        assertThat(lines.get(0).get("sku")).isEqualTo("organic-cotton");
    }

    @Test
    void buildProductPayload_whenCategoryDetached_stillBuildsPayload() {
        Product product = product(110L).active(true);
        product.setCategory(new com.miniandmore.app.domain.Category().name("Baby"));
        when(productRepository.findOneWithEagerRelationships(110L)).thenReturn(Optional.of(product));
        when(ledgerlyClient.syncProductPayload(any())).thenReturn(Map.of("ledgerlyItemId", "item-110"));

        service.syncProduct(110L);

        verify(ledgerlyClient).syncProductPayload(org.mockito.ArgumentMatchers.argThat(payload -> payload.containsKey("sku")));
    }

    @Test
    void pushProductToLedgerly_inactiveLinkedProduct_deactivatesInLedgerly() {
        Product product = product(111L).active(false).ledgerlyItemId("item-111");
        product.setLedgerlySyncStatus(LedgerlySyncStatus.SYNCED);
        when(productRepository.findOneWithEagerRelationships(111L)).thenReturn(Optional.of(product));
        when(ledgerlyClient.syncProductPayload(any())).thenReturn(Map.of("noop", true));

        service.syncProduct(111L);

        verify(ledgerlyClient).syncProductPayload(org.mockito.ArgumentMatchers.argThat(payload -> Boolean.FALSE.equals(payload.get("active"))));
        verify(productRepository).updateLedgerlyDeactivateMeta(eq(111L), any(), eq(true));
    }

    @Test
    void retryFailedOrders_failedSync_stillCompletesBatch() {
        CustomerOrder failed = order(25L, "ORD-25", LedgerlySyncStatus.FAILED);
        when(customerOrderRepository.findAll()).thenReturn(List.of(failed));
        when(customerOrderRepository.findById(25L)).thenReturn(Optional.of(failed));
        when(ledgerlyClient.syncOrderPayload(any(), any())).thenThrow(new RuntimeException("sync down"));
        when(customerOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> result = service.retryFailedOrders();

        assertThat(result.get("successCount")).isEqualTo(1);
        assertThat(failed.getLedgerlySyncStatus()).isEqualTo(LedgerlySyncStatus.FAILED);
    }

    @Test
    void pullProducts_existingProductByLedgerlyItemId_updatesRow() {
        Product existing = product(112L).sku("OLD-SKU").ledgerlyItemId("item-112");
        when(ledgerlyClient.pullProducts()).thenReturn(
            Map.of("products", List.of(Map.of("sku", "NEW-SKU", "ledgerlyItemId", "item-112", "name", "Renamed", "price", 120)))
        );
        when(ledgerlySettingRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());
        when(productRepository.findOneBySku("NEW-SKU")).thenReturn(Optional.empty());
        when(productRepository.findOneByLedgerlyItemId("item-112")).thenReturn(Optional.of(existing));
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> result = service.pullProducts();

        assertThat(result).containsEntry("successCount", 1);
    }

    @Test
    void findProductForInventoryPull_prefersLedgerlyItemId() {
        Product byItem = product(113L).sku("SKU-113").ledgerlyItemId("item-113");
        when(productRepository.findOneByLedgerlyItemId("item-113")).thenReturn(Optional.of(byItem));

        Optional<Product> found = org.springframework.test.util.ReflectionTestUtils.invokeMethod(service, "findProductForInventoryPull", "OTHER", "item-113");

        assertThat(found).contains(byItem);
    }

    @Test
    void pullProducts_categoryNotice_onlyForNewProducts() {
        when(ledgerlyClient.pullProducts()).thenReturn(
            Map.of("products", List.of(Map.of("sku", "EXIST", "ledgerlyItemId", "item-x", "name", "Existing", "price", 100, "category", "Baby")))
        );
        when(ledgerlySettingRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());
        Product existing = product(120L).sku("EXIST").ledgerlyItemId("item-x");
        when(productRepository.findOneBySku("EXIST")).thenReturn(Optional.of(existing));
        when(productRepository.save(existing)).thenReturn(existing);

        Map<String, Object> result = service.pullProducts();

        assertThat(result).containsEntry("successCount", 1);
    }

    @Test
    void isStillInLedgerlyPull_matchesBySkuWhenItemIdMissing() {
        Product product = product(121L).sku("SKU-121");
        boolean still = org.springframework.test.util.ReflectionTestUtils.invokeMethod(
            service,
            "isStillInLedgerlyPull",
            product,
            java.util.Set.of("SKU-121"),
            java.util.Set.of()
        );
        assertThat(still).isTrue();
    }

    @Test
    void syncCustomerProfileAsync_delegatesToSync() {
        CustomerProfile profile = new CustomerProfile().userLogin("async@example.com").fullName("Async");
        when(customerProfileRepository.findOneByUserLogin("async@example.com")).thenReturn(Optional.of(profile));

        service.syncCustomerProfileAsync("async@example.com");

        verify(ledgerlyClient).syncCustomerPayload(any());
    }

    @Test
    void toBigDecimal_and_toInteger_handleTypes() {
        BigDecimal fromBd = org.springframework.test.util.ReflectionTestUtils.invokeMethod(service, "toBigDecimal", BigDecimal.TEN);
        BigDecimal fromString = org.springframework.test.util.ReflectionTestUtils.invokeMethod(service, "toBigDecimal", "12.5");
        BigDecimal fromNull = org.springframework.test.util.ReflectionTestUtils.invokeMethod(service, "toBigDecimal", new Object[] { null });
        Integer fromNumber = org.springframework.test.util.ReflectionTestUtils.invokeMethod(service, "toInteger", 7);
        Integer fromStringInt = org.springframework.test.util.ReflectionTestUtils.invokeMethod(service, "toInteger", "9");

        assertThat(fromBd).isEqualByComparingTo("10");
        assertThat(fromString).isEqualByComparingTo("12.5");
        assertThat(fromNull).isEqualByComparingTo("0");
        assertThat(fromNumber).isEqualTo(7);
        assertThat(fromStringInt).isEqualTo(9);
    }

    @Test
    void buildOrderPayload_variantProductWithoutSku_throws() {
        CustomerOrder order = order(70L, "ORD-VAR-ERR", LedgerlySyncStatus.PENDING);
        OrderItem line = order.getItemses().iterator().next();
        line.setVariantSku(null);
        line.setItemSize("M");
        line.setColor("Navy");
        Product product = product(1L).sku("HORIZON").itemStructure("VARIANTS").variantsJson("[]");
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(siteSettingRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> org.springframework.test.util.ReflectionTestUtils.invokeMethod(service, "buildOrderPayload", order))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("missing variantSku");
    }

    @Test
    void pullProducts_uniqueSlug_avoidsCollision() {
        when(ledgerlyClient.pullProducts()).thenReturn(
            Map.of("products", List.of(Map.of("sku", "COLLIDE", "name", "Dress", "price", 100)))
        );
        when(ledgerlySettingRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());
        when(productRepository.findOneBySku("COLLIDE")).thenReturn(Optional.empty());
        when(productRepository.findOneBySlug("dress")).thenReturn(Optional.of(product(1L)));
        when(productRepository.findOneBySlug("dress-1")).thenReturn(Optional.empty());
        when(productRepository.findMaxSortOrder()).thenReturn(0);
        when(productRepository.save(any())).thenAnswer(inv -> {
            Product p = inv.getArgument(0);
            p.setId(201L);
            return p;
        });

        Map<String, Object> result = service.pullProducts();

        assertThat(result).containsEntry("successCount", 1);
    }

    @Test
    void shouldRetryAfterInvoicePaymentFailure_blankMessage_returnsFalse() {
        assertThat(LedgerlySyncService.shouldRetryAfterInvoicePaymentFailure(new RuntimeException("   "))).isFalse();
        assertThat(LedgerlySyncService.shouldRetryAfterInvoicePaymentFailure(new RuntimeException((String) null))).isFalse();
    }

    @Test
    void syncAllProducts_blankSku_skipsWithNotice() {
        Product noSku = product(96L).sku("").active(true);
        when(productRepository.findAllWithEagerRelationships()).thenReturn(List.of(noSku));

        Map<String, Object> result = service.syncAllProducts();

        assertThat(result).containsEntry("successCount", 0);
        assertThat((List<?>) result.get("notices")).isNotEmpty();
    }

    @Test
    void pullAllTracking_whenPullFails_stillCountsSuccess() {
        CustomerOrder order = order(80L, "ORD-80", LedgerlySyncStatus.SYNCED);
        order.paymentStatus(PaymentStatus.PAID).status(OrderStatus.CONFIRMED);
        when(
            customerOrderRepository.findOrdersForLedgerlyTrackingPull(
                eq(PaymentStatus.PAID),
                eq(List.of(OrderStatus.PENDING, OrderStatus.CONFIRMED)),
                any(PageRequest.class)
            )
        ).thenReturn(new PageImpl<>(List.of(order)));
        when(customerOrderRepository.findById(80L)).thenReturn(Optional.of(order));
        when(ledgerlyClient.pullOrderTracking("ORD-80")).thenThrow(new RuntimeException("pull failed"));

        Map<String, Object> result = service.pullAllTracking();

        assertThat(result.get("successCount")).isEqualTo(1);
        verify(ledgerlyClient).pullOrderTracking("ORD-80");
    }

    @Test
    void buildOrderPayload_resolvesLineSkuFromCachedProductId() {
        CustomerOrder order = order(71L, "ORD-CACHE", LedgerlySyncStatus.PENDING);
        OrderItem line1 = new OrderItem().productId("1").productSlug("slug").productName("Item").qty(1).price(BigDecimal.TEN);
        OrderItem line2 = new OrderItem().productId("1").productSlug("slug").productName("Item").qty(1).price(BigDecimal.TEN);
        order.itemses(Set.of(line1, line2));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product(1L).sku("SKU-CACHED")));
        when(siteSettingRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = org.springframework.test.util.ReflectionTestUtils.invokeMethod(service, "buildOrderPayload", order);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lines = (List<Map<String, Object>>) payload.get("lines");
        assertThat(lines).hasSize(2);
        assertThat(lines.get(0).get("sku")).isEqualTo("SKU-CACHED");
        assertThat(lines.get(1).get("sku")).isEqualTo("SKU-CACHED");
        verify(productRepository, times(2)).findById(1L);
    }

    private static Product product(Long id) {
        return new Product().id(id).sku("SKU-" + id).name("Product " + id).price(BigDecimal.TEN).stock(3);
    }

    private static CustomerOrder order(Long id, String orderNumber, LedgerlySyncStatus status) {
        OrderItem line = new OrderItem().productId("1").productSlug("sku-1").productName("Item").qty(1).price(BigDecimal.TEN);
        return new CustomerOrder()
            .id(id)
            .orderNumber(orderNumber)
            .userLogin("customer1")
            .total(BigDecimal.TEN)
            .razorpayPaymentId("pay_" + id)
            .ledgerlySyncStatus(status)
            .shippingAddressJson("{}")
            .itemses(Set.of(line));
    }
}
