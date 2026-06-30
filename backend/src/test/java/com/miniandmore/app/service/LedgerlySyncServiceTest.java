package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.miniandmore.app.client.LedgerlyIntegrationClient;
import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.OrderItem;
import com.miniandmore.app.domain.Product;
import com.miniandmore.app.domain.enumeration.LedgerlySyncStatus;
import com.miniandmore.app.repository.CustomerOrderRepository;
import com.miniandmore.app.repository.ProductRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LedgerlySyncServiceTest {

    @Mock
    private LedgerlyIntegrationClient ledgerlyClient;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CustomerOrderRepository customerOrderRepository;

    @Mock
    private LedgerlyItemImageSync itemImageSync;

    @InjectMocks
    private LedgerlySyncService service;

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
