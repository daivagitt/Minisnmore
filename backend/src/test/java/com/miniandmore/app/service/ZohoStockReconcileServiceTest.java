package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.miniandmore.app.domain.Product;
import com.miniandmore.app.repository.OrderItemRepository;
import com.miniandmore.app.repository.ProductRepository;
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
}
