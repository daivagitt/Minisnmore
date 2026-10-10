package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.client.LedgerlyIntegrationClient;
import com.miniandmore.app.config.ApplicationProperties;
import com.miniandmore.app.domain.Product;
import com.miniandmore.app.repository.CustomerOrderRepository;
import com.miniandmore.app.repository.CustomerProfileRepository;
import com.miniandmore.app.repository.LedgerlySettingRepository;
import com.miniandmore.app.repository.ProductRepository;
import com.miniandmore.app.repository.SiteSettingRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class LedgerlySyncServicePullInventoryDecisionMatrixTest {

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
    private SiteSettingRepository siteSettingRepository;

    @Mock
    private LedgerlyProductMergeService productMergeService;

    @Mock
    private LedgerlyCategoryResolver categoryResolver;

    @Mock
    private ProductVariantSupport variantSupport;

    @Mock
    private ProductColorImagesSupport colorImagesSupport;

    @Mock
    private ApplicationProperties applicationProperties;

    private LedgerlySyncService service;

    @BeforeEach
    void createService() {
        service = new LedgerlySyncService(
            ledgerlyClient,
            productRepository,
            customerOrderRepository,
            customerProfileRepository,
            ledgerlySettingRepository,
            siteSettingRepository,
            productMergeService,
            categoryResolver,
            variantSupport,
            colorImagesSupport,
            applicationProperties,
            new ObjectMapper()
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("directParentRows")
    void pullInventory_coversDirectParentDecisionMatrix(
        String label,
        Product parent,
        Map<String, Object> level,
        boolean variants,
        String coercedMatrix,
        boolean matrixApplied,
        int successes,
        int failures,
        boolean saved
    ) {
        when(ledgerlyClient.pullInventory()).thenReturn(Map.of("inventory", List.of(level)));
        when(productRepository.findAll()).thenReturn(List.of(parent));
        String itemId = level.get("ledgerlyItemId") == null ? null : level.get("ledgerlyItemId").toString();
        String sku = level.get("sku") == null ? null : level.get("sku").toString();
        if (itemId != null) {
            when(productRepository.findOneByLedgerlyItemId(itemId)).thenReturn(Optional.of(parent));
        } else if (sku != null) {
            when(productRepository.findOneBySku(sku)).thenReturn(Optional.of(parent));
        }
        when(variantSupport.isVariants(parent)).thenReturn(variants);
        if (level.containsKey("variantsJson") || level.containsKey("variants_json")) {
            when(variantSupport.coerceVariantsJson(any())).thenReturn(coercedMatrix);
            if (coercedMatrix != null) {
                when(variantSupport.applyMatrixToProduct(parent, ProductVariantSupport.STRUCTURE_VARIANTS, coercedMatrix))
                    .thenReturn(matrixApplied);
            }
        }

        Map<String, Object> result = service.pullInventory();

        assertThat(result)
            .as(label)
            .containsEntry("successCount", successes)
            .containsEntry("failureCount", failures);
        if (saved) {
            verify(productRepository).save(parent);
        } else {
            verify(productRepository, never()).save(parent);
        }
    }

    private static Stream<Arguments> directParentRows() {
        Product simple = product("SIMPLE");
        Product variantWithoutStock = product("VAR-NO-STOCK");
        Product variantStockOnly = product("VAR-STOCK");
        Product invalidBySku = product("BAD-SKU");
        Product invalidByItemId = product("BAD-ID");
        return Stream.of(
            Arguments.of(
                "simple string stock is converted and saved",
                simple,
                Map.of("sku", "SIMPLE", "stock", "7.9"),
                false,
                null,
                false,
                1,
                0,
                true
            ),
            Arguments.of(
                "variant parent without matrix or stock is saved without destructive overwrite",
                variantWithoutStock,
                Map.of("sku", "VAR-NO-STOCK"),
                true,
                null,
                false,
                1,
                0,
                true
            ),
            Arguments.of(
                "variant parent stock-only row is accepted without save",
                variantStockOnly,
                Map.of("sku", "VAR-STOCK", "stock", 99),
                true,
                null,
                false,
                1,
                0,
                false
            ),
            Arguments.of(
                "uncoercible matrix reports SKU",
                invalidBySku,
                Map.of("sku", "BAD-SKU", "variantsJson", "bad"),
                true,
                null,
                false,
                0,
                1,
                false
            ),
            Arguments.of(
                "rejected matrix reports item id when SKU absent",
                invalidByItemId,
                Map.of("ledgerlyItemId", "item-bad", "variants_json", "[]"),
                true,
                "[]",
                false,
                0,
                1,
                false
            )
        );
    }

    @Test
    void pullInventory_variantLookupUpdateFailure_isMeaningfullyReported() {
        Product variantParent = product("PARENT");
        Map<String, Object> level = Map.of("sku", "PARENT-S-RED", "stock", 4);
        when(ledgerlyClient.pullInventory()).thenReturn(Map.of("inventory", List.of(level)));
        when(productRepository.findAll()).thenReturn(List.of(variantParent));
        when(variantSupport.isVariants(variantParent)).thenReturn(true);
        when(productRepository.findOneBySku("PARENT-S-RED")).thenReturn(Optional.empty());
        when(variantSupport.findProductContainingVariantSku(List.of(variantParent), "PARENT-S-RED"))
            .thenReturn(Optional.of(variantParent));
        when(variantSupport.updateVariantStockBySku(variantParent, "PARENT-S-RED", 4)).thenReturn(false);

        Map<String, Object> result = service.pullInventory();

        assertThat(result).containsEntry("failureCount", 1).containsEntry("successCount", 0);
        assertThat(result.get("failures").toString()).contains("Variant row not found").contains("PARENT");
        verify(productRepository, never()).save(any());
    }

    @Test
    void pullInventory_nullSkuVariantCandidate_fallsThroughToItemIdSkip() {
        Product candidate = product("PARENT");
        Map<String, Object> level = Map.of("ledgerlyItemId", "missing-id", "stock", 2);
        when(ledgerlyClient.pullInventory()).thenReturn(Map.of("inventory", List.of(level)));
        when(productRepository.findAll()).thenReturn(List.of(candidate));
        when(variantSupport.isVariants(candidate)).thenReturn(true);
        when(productRepository.findOneByLedgerlyItemId("missing-id")).thenReturn(Optional.empty());
        when(variantSupport.findProductContainingVariantSku(List.of(candidate), null)).thenReturn(Optional.of(candidate));

        Map<String, Object> result = service.pullInventory();

        assertThat(result).containsEntry("skippedCount", 1).containsEntry("failureCount", 0);
        assertThat(result.get("skipped").toString()).contains("missing-id");
        verify(variantSupport, never()).updateVariantStockBySku(any(), any(), anyInt());
    }

    @Test
    void pullInventory_saveException_usesItemIdAsFailureReference() {
        Product parent = product("PARENT");
        Map<String, Object> level = Map.of("ledgerlyItemId", "item-throw", "stock", 3);
        when(ledgerlyClient.pullInventory()).thenReturn(Map.of("inventory", List.of(level)));
        when(productRepository.findAll()).thenReturn(List.of());
        when(productRepository.findOneByLedgerlyItemId("item-throw")).thenReturn(Optional.of(parent));
        when(variantSupport.isVariants(parent)).thenReturn(false);
        when(productRepository.save(parent)).thenThrow(new IllegalStateException("database rejected stock"));

        Map<String, Object> result = service.pullInventory();

        assertThat(result).containsEntry("failureCount", 1).containsEntry("successCount", 0);
        assertThat(result.get("failures").toString()).contains("item-throw").contains("database rejected stock");
    }

    @Test
    void pullInventory_saveException_prefersSkuAsFailureReference() {
        Product parent = product("THROW-SKU");
        Map<String, Object> level = Map.of("sku", "THROW-SKU", "ledgerlyItemId", "item-throw", "stock", 3);
        when(ledgerlyClient.pullInventory()).thenReturn(Map.of("inventory", List.of(level)));
        when(productRepository.findAll()).thenReturn(List.of());
        when(productRepository.findOneByLedgerlyItemId("item-throw")).thenReturn(Optional.of(parent));
        when(variantSupport.isVariants(parent)).thenReturn(false);
        when(productRepository.save(parent)).thenThrow(new IllegalStateException("database rejected stock"));

        Map<String, Object> result = service.pullInventory();

        assertThat(result).containsEntry("failureCount", 1).containsEntry("successCount", 0);
        assertThat(result.get("failures").toString()).contains("THROW-SKU").doesNotContain("sku=item-throw");
    }

    @ParameterizedTest(name = "itemId={0}, sku={1}")
    @MethodSource("lookupRows")
    void findProductForInventoryPull_blankAndFallbackMatrix(
        String itemId,
        String sku,
        Optional<Product> byItem,
        Optional<Product> bySku,
        Optional<Product> expected
    ) {
        if (itemId != null && !itemId.isBlank()) {
            when(productRepository.findOneByLedgerlyItemId(itemId)).thenReturn(byItem);
        }
        if (sku != null && !sku.isBlank() && byItem.isEmpty()) {
            when(productRepository.findOneBySku(sku)).thenReturn(bySku);
        }

        Optional<Product> result = ReflectionTestUtils.invokeMethod(service, "findProductForInventoryPull", sku, itemId);

        assertThat(result).isEqualTo(expected);
    }

    private static Stream<Arguments> lookupRows() {
        Product byId = product("BY-ID");
        Product bySku = product("BY-SKU");
        return Stream.of(
            Arguments.of("item-1", "SKU", Optional.of(byId), Optional.of(bySku), Optional.of(byId)),
            Arguments.of("item-missing", "SKU", Optional.empty(), Optional.of(bySku), Optional.of(bySku)),
            Arguments.of(" ", "SKU", Optional.empty(), Optional.of(bySku), Optional.of(bySku)),
            Arguments.of(null, "", Optional.empty(), Optional.empty(), Optional.empty()),
            Arguments.of(null, null, Optional.empty(), Optional.empty(), Optional.empty())
        );
    }

    private static Product product(String sku) {
        return new Product().sku(sku).price(BigDecimal.TEN).stock(1);
    }
}
