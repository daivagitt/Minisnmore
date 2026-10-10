package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.OrderItem;
import com.miniandmore.app.domain.Product;
import com.miniandmore.app.repository.OrderItemRepository;
import com.miniandmore.app.repository.ProductRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ZohoStockReconcileServiceBranchTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private ZohoBooksAccountResolver accountResolver;

    @Mock
    private ZohoCatalogStockAdjuster stockAdjuster;

    private ProductVariantSupport variantSupport;
    private ZohoStockReconcileService service;

    @BeforeEach
    void setUp() {
        variantSupport = StorePricingTestSupport.variantSupport(new com.fasterxml.jackson.databind.ObjectMapper());
        service = new ZohoStockReconcileService(productRepository, orderItemRepository, variantSupport, accountResolver, stockAdjuster);
        lenient().when(accountResolver.isStockAdjustmentEnabled()).thenReturn(true);
    }

    @Test
    void reconcileProductStock_skipsWhenZohoStockSyncDisabled() {
        when(accountResolver.isStockAdjustmentEnabled()).thenReturn(false);
        Product product = new Product().id(1L).sku("SKU-1").zohoItemId("zoho-1").stock(3);

        @SuppressWarnings("unchecked")
        Map<String, Object> result = ReflectionTestUtils.invokeMethod(service, "reconcileProductStock", product, null, null);

        assertThat(result.get("skipped")).isEqualTo(true);
    }

    @Test
    void reconcileProductStock_simpleProduct_successAndFailure() {
        Product product = new Product().id(2L).sku("SIMPLE").zohoItemId("zoho-2").stock(5).price(BigDecimal.TEN);
        when(stockAdjuster.adjustItemQuantity(eq("zoho-2"), anyInt(), any(), any(), any())).thenReturn(true);

        @SuppressWarnings("unchecked")
        Map<String, Object> ok = ReflectionTestUtils.invokeMethod(service, "reconcileProductStock", product, null, null);
        assertThat(ok.get("ok")).isEqualTo(true);

        when(stockAdjuster.adjustItemQuantity(eq("zoho-2"), anyInt(), any(), any(), any())).thenReturn(false);
        @SuppressWarnings("unchecked")
        Map<String, Object> fail = ReflectionTestUtils.invokeMethod(service, "reconcileProductStock", product, null, null);
        assertThat(fail.get("error")).isEqualTo("Zoho stock reconcile failed");
    }

    @Test
    void reconcileProductStock_simpleProductWithoutZohoId_returnsError() {
        Product product = new Product().id(3L).sku("UNLINKED").stock(1);
        @SuppressWarnings("unchecked")
        Map<String, Object> result = ReflectionTestUtils.invokeMethod(service, "reconcileProductStock", product, null, null);
        assertThat(result.get("error")).isEqualTo("Product not linked in Zoho");
    }

    @Test
    void reconcileProductStock_variantMatrix_filtersSkuAndCountsFailures() {
        String variants =
            """
            [{"sku":"VAR-A","size":"S","color":"Red","rate":10,"stock":2,"active":true,"zohoItemId":"zoho-a"},
             {"sku":"VAR-B","size":"M","color":"Blue","rate":10,"stock":4,"active":true,"zohoItemId":"zoho-b"},
             {"sku":"VAR-OFF","size":"L","color":"Green","rate":10,"stock":1,"active":false,"zohoItemId":"zoho-off"}]
            """;
        Product product = new Product()
            .id(4L)
            .sku("MATRIX")
            .itemStructure(ProductVariantSupport.STRUCTURE_VARIANTS)
            .variantsJson(variants);
        when(stockAdjuster.adjustItemQuantity(eq("zoho-a"), anyInt(), any(), any(), any())).thenReturn(true);
        when(stockAdjuster.adjustItemQuantity(eq("zoho-b"), anyInt(), any(), any(), any())).thenReturn(false);

        @SuppressWarnings("unchecked")
        Map<String, Object> all = ReflectionTestUtils.invokeMethod(service, "reconcileProductStock", product, null, null);
        assertThat(all.get("ok")).isEqualTo(false);
        assertThat(all.get("failedItems")).isEqualTo(1);

        when(stockAdjuster.adjustItemQuantity(eq("zoho-a"), eq(9), any(), any(), any())).thenReturn(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> oneSku = ReflectionTestUtils.invokeMethod(service, "reconcileProductStock", product, "VAR-A", 9);
        assertThat(oneSku.get("ok")).isEqualTo(true);

        @SuppressWarnings("unchecked")
        Map<String, Object> missingSku = ReflectionTestUtils.invokeMethod(service, "reconcileProductStock", product, "MISSING", 1);
        assertThat(missingSku.get("error")).isEqualTo("Variant not linked in Zoho");
    }

    @Test
    void reconcileOrderProducts_aggregatesLinesAndSkipsInvalidProductIds() {
        CustomerOrder order = new CustomerOrder().id(50L).orderNumber("MNM-50");
        OrderItem line = new OrderItem().productId("10").qty(2);
        OrderItem bad = new OrderItem().productId("x").qty(1);
        Product product = new Product().id(10L).sku("P10").zohoItemId("zoho-10").stock(1).price(BigDecimal.ONE);
        when(orderItemRepository.findAllByOrderId(50L)).thenReturn(List.of(line, bad));
        when(productRepository.findOneWithEagerRelationships(10L)).thenReturn(Optional.of(product));
        when(stockAdjuster.adjustItemQuantity(eq("zoho-10"), anyInt(), any(), any(), any())).thenReturn(true);

        assertThat(service.reconcileOrderProducts(order, false)).isTrue();
    }
}
