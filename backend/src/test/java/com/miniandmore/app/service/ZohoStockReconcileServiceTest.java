package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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

@ExtendWith(MockitoExtension.class)
class ZohoStockReconcileServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private ProductVariantSupport variantSupport;

    @Mock
    private ZohoBooksAccountResolver accountResolver;

    @Mock
    private ZohoCatalogStockAdjuster stockAdjuster;

    private ZohoStockReconcileService service;

    @BeforeEach
    void setUp() {
        service =
            new ZohoStockReconcileService(
                productRepository,
                orderItemRepository,
                variantSupport,
                accountResolver,
                stockAdjuster
            );
    }

    @Test
    void reconcileStockFromWebhook_variantProductWithoutSku_rejectsPayload() {
        Product product = new Product().id(9L).sku("PARENT-9").itemStructure(ProductVariantSupport.STRUCTURE_VARIANTS);
        when(productRepository.findOneWithEagerRelationships(9L)).thenReturn(Optional.of(product));
        when(variantSupport.isVariants(product)).thenReturn(true);

        Map<String, Object> result = service.reconcileStockFromWebhook(null, 9L, 12);

        assertThat(result).containsEntry("ok", false).containsEntry("error", "Variant products require sku in stock webhook");
    }

    @Test
    void syncProductStockOnly_missingProduct_returnsError() {
        when(productRepository.findOneWithEagerRelationships(404L)).thenReturn(Optional.empty());

        assertThat(service.syncProductStockOnly(404L))
            .containsEntry("ok", false)
            .containsEntry("error", "Product not found")
            .containsEntry("productId", 404L);
    }

    @Test
    void syncProductStockOnly_whenStockSyncDisabled_returnsSkipped() {
        Product product = new Product().id(1L).sku("SKU-1").zohoItemId("zoho-1").stock(3);
        when(productRepository.findOneWithEagerRelationships(1L)).thenReturn(Optional.of(product));
        when(accountResolver.isStockAdjustmentEnabled()).thenReturn(false);

        assertThat(service.syncProductStockOnly(1L))
            .containsEntry("ok", false)
            .containsEntry("skipped", true)
            .containsEntry("reason", "Zoho stock sync disabled");
    }

    @Test
    void syncProductStockOnly_simpleProduct_adjustsZohoStock() {
        Product product = new Product().id(2L).sku("SKU-2").zohoItemId("zoho-2").stock(4).price(BigDecimal.valueOf(199));
        when(productRepository.findOneWithEagerRelationships(2L)).thenReturn(Optional.of(product));
        when(accountResolver.isStockAdjustmentEnabled()).thenReturn(true);
        when(variantSupport.isVariants(product)).thenReturn(false);
        when(stockAdjuster.adjustItemQuantity(eq("zoho-2"), eq(4), eq(BigDecimal.valueOf(199)), any(), any())).thenReturn(true);

        assertThat(service.syncProductStockOnly(2L)).containsEntry("ok", true).containsEntry("reconciledItems", 1);
    }

    @Test
    void reconcileStockFromWebhook_resolvesProductBySku() {
        Product product = new Product().id(3L).sku("SKU-3").zohoItemId("zoho-3").stock(2);
        when(productRepository.findOneBySku("SKU-3")).thenReturn(Optional.of(product));
        when(accountResolver.isStockAdjustmentEnabled()).thenReturn(true);
        when(variantSupport.isVariants(product)).thenReturn(false);
        when(stockAdjuster.adjustItemQuantity(eq("zoho-3"), eq(9), any(), any(), any())).thenReturn(true);

        assertThat(service.reconcileStockFromWebhook("SKU-3", null, 9)).containsEntry("ok", true);
    }

    @Test
    void reconcileOrderProducts_failOnError_throwsWhenReconcileFails() {
        CustomerOrder order = new CustomerOrder().id(10L).orderNumber("MNM-10");
        OrderItem line = new OrderItem().productId("5");
        when(accountResolver.isStockAdjustmentEnabled()).thenReturn(true);
        when(orderItemRepository.findAllByOrderId(10L)).thenReturn(List.of(line));
        when(productRepository.findOneWithEagerRelationships(5L)).thenReturn(Optional.of(new Product().id(5L).sku("SKU-5")));
        when(accountResolver.isStockAdjustmentEnabled()).thenReturn(true);
        when(variantSupport.isVariants(any())).thenReturn(false);

        assertThatThrownBy(() -> service.reconcileOrderProducts(order, true))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Zoho stock reconcile failed");
    }

    @Test
    void reconcileOrderProducts_nullOrder_isNoOp() {
        assertThat(service.reconcileOrderProducts(null, false)).isTrue();
        verify(orderItemRepository, never()).findAllByOrderId(any());
    }

    @Test
    void reconcileOrderProducts_success_returnsTrue() {
        CustomerOrder order = new CustomerOrder().id(11L).orderNumber("MNM-11");
        OrderItem line = new OrderItem().productId("6");
        Product product = new Product().id(6L).sku("SKU-6").zohoItemId("zoho-6").stock(1).price(BigDecimal.TEN);
        when(accountResolver.isStockAdjustmentEnabled()).thenReturn(true);
        when(orderItemRepository.findAllByOrderId(11L)).thenReturn(List.of(line));
        when(productRepository.findOneWithEagerRelationships(6L)).thenReturn(Optional.of(product));
        when(variantSupport.isVariants(product)).thenReturn(false);
        when(stockAdjuster.adjustItemQuantity(eq("zoho-6"), eq(1), eq(BigDecimal.TEN), any(), any())).thenReturn(true);

        assertThat(service.reconcileOrderProducts(order, false)).isTrue();
    }

    @Test
    void reconcileStockFromWebhook_unknownProductId_returnsNotFound() {
        when(productRepository.findOneWithEagerRelationships(999L)).thenReturn(Optional.empty());

        assertThat(service.reconcileStockFromWebhook("MISSING", 999L, 1))
            .containsEntry("ok", false)
            .containsEntry("error", "Product not found");
    }

    @Test
    void reconcileOrderProducts_skipsBlankAndMalformedProductIds() {
        CustomerOrder order = new CustomerOrder().id(12L).orderNumber("MNM-12");
        OrderItem blank = new OrderItem().productId("  ");
        OrderItem bad = new OrderItem().productId("not-a-number");
        OrderItem good = new OrderItem().productId("7");
        Product product = new Product().id(7L).sku("SKU-7").zohoItemId("zoho-7").stock(2).price(BigDecimal.ONE);
        when(accountResolver.isStockAdjustmentEnabled()).thenReturn(true);
        when(orderItemRepository.findAllByOrderId(12L)).thenReturn(List.of(blank, bad, good));
        when(productRepository.findOneWithEagerRelationships(7L)).thenReturn(Optional.of(product));
        when(variantSupport.isVariants(product)).thenReturn(false);
        when(stockAdjuster.adjustItemQuantity(eq("zoho-7"), eq(2), eq(BigDecimal.ONE), any(), any())).thenReturn(true);

        assertThat(service.reconcileOrderProducts(order, false)).isTrue();
        verify(productRepository).findOneWithEagerRelationships(7L);
    }

    @Test
    void syncProductStockOnly_simpleProductWithoutZohoLink_returnsError() {
        Product product = new Product().id(8L).sku("SKU-8").stock(1);
        when(productRepository.findOneWithEagerRelationships(8L)).thenReturn(Optional.of(product));
        when(accountResolver.isStockAdjustmentEnabled()).thenReturn(true);
        when(variantSupport.isVariants(product)).thenReturn(false);

        assertThat(service.syncProductStockOnly(8L))
            .containsEntry("ok", false)
            .containsEntry("error", "Product not linked in Zoho");
    }

    @Test
    void reconcileStockFromWebhook_variantSkuAdjustFailure_returnsError() {
        Product product = new Product().id(9L).sku("VP-9").itemStructure(ProductVariantSupport.STRUCTURE_VARIANTS);
        ProductVariantSupport.VariantRow row = new ProductVariantSupport.VariantRow(
            "r1",
            "CHILD-9",
            "S",
            "Red",
            BigDecimal.TEN,
            null,
            null,
            3,
            true,
            null,
            "zoho-child",
            null,
            List.of()
        );
        when(productRepository.findOneBySku("CHILD-9")).thenReturn(Optional.of(product));
        when(accountResolver.isStockAdjustmentEnabled()).thenReturn(true);
        when(variantSupport.isVariants(product)).thenReturn(true);
        when(variantSupport.parseProduct(product)).thenReturn(List.of(row));
        when(variantSupport.variantSellingPrice(row)).thenReturn(BigDecimal.TEN);
        when(stockAdjuster.adjustItemQuantity(eq("zoho-child"), eq(5), eq(BigDecimal.TEN), any(), any())).thenReturn(false);

        assertThat(service.reconcileStockFromWebhook("CHILD-9", null, 5))
            .containsEntry("ok", false)
            .containsEntry("error", "Zoho stock reconcile failed");
    }
}
