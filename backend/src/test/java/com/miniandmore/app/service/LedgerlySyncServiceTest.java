package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.miniandmore.app.client.LedgerlyIntegrationClient;
import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.OrderItem;
import com.miniandmore.app.domain.Product;
import com.miniandmore.app.domain.enumeration.LedgerlySyncStatus;
import com.miniandmore.app.repository.CategoryRepository;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
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

    @InjectMocks
    private LedgerlySyncService service;

    @BeforeEach
    void stubMergeService() {
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
        Product product = product(1L);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(ledgerlyClient.syncProductPayload(any())).thenReturn(Map.of("ledgerlyItemId", "item-99"));
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.syncProduct(1L);

        assertThat(product.getLedgerlySyncStatus()).isEqualTo(LedgerlySyncStatus.SYNCED);
        assertThat(product.getLedgerlyItemId()).isEqualTo("item-99");
        assertThat(product.getLedgerlySyncError()).isNull();
        assertThat(product.getLedgerlyLastSyncedAt()).isNotNull();
        verify(productMergeService).buildPushPayload(eq(product), any());
    }

    @Test
    void syncProduct_clientFailure_setsFailedStatus() {
        Product product = product(2L);
        when(productRepository.findById(2L)).thenReturn(Optional.of(product));
        when(ledgerlyClient.syncProductPayload(any())).thenThrow(new RuntimeException("upstream 503"));
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.syncProduct(2L);

        assertThat(product.getLedgerlySyncStatus()).isEqualTo(LedgerlySyncStatus.FAILED);
        assertThat(product.getLedgerlySyncError()).contains("503");
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
    void pullOrderTracking_whenFound_updatesStatus() {
        CustomerOrder order = order(11L, "ORD-11", LedgerlySyncStatus.SYNCED);
        when(customerOrderRepository.findById(11L)).thenReturn(Optional.of(order));
        when(ledgerlyClient.pullOrderTracking("ORD-11")).thenReturn(Map.of("found", true, "status", "delivered"));
        when(customerOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.pullOrderTracking(11L);

        assertThat(order.getLedgerlyOrderStatus()).isEqualTo("delivered");
        assertThat(order.getLedgerlyTrackingLastPulledAt()).isNotNull();
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

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = org.springframework.test.util.ReflectionTestUtils.invokeMethod(service, "buildOrderPayload", order);

        assertThat(payload.get("subtotal")).isEqualTo(new BigDecimal("399"));
        assertThat(payload.get("shipping")).isEqualTo(new BigDecimal("1"));
        assertThat(payload.get("tax")).isEqualTo(new BigDecimal("4"));
        assertThat(payload.get("total")).isEqualTo(new BigDecimal("404"));
        assertThat(payload.get("pricesIncludeGst")).isEqualTo(true);
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
        Product ok = product(30L);
        Product bad = product(31L);
        when(productRepository.findAll()).thenReturn(List.of(ok, bad));
        when(productRepository.findById(30L)).thenReturn(Optional.of(ok));
        when(productRepository.findById(31L)).thenReturn(Optional.of(bad));
        when(ledgerlyClient.syncProductPayload(any()))
            .thenReturn(Map.of("ledgerlyItemId", "item-30"))
            .thenThrow(new RuntimeException("sync failed"));
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> result = service.syncAllProducts();

        assertThat(result).containsEntry("successCount", 2).containsEntry("failureCount", 0);
        assertThat((List<?>) result.get("failures")).isEmpty();
        assertThat(bad.getLedgerlySyncStatus()).isEqualTo(LedgerlySyncStatus.FAILED);
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
