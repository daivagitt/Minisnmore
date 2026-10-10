package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.miniandmore.app.client.ZohoBooksClient;
import com.miniandmore.app.config.ApplicationProperties;
import com.miniandmore.app.domain.Product;
import com.miniandmore.app.repository.CustomerOrderRepository;
import com.miniandmore.app.repository.CustomerProfileRepository;
import com.miniandmore.app.repository.OrderItemRepository;
import com.miniandmore.app.repository.ProductRepository;
import com.miniandmore.app.repository.ZohoSettingRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClientResponseException;

/** Residual {@link ZohoSyncService#pullInventory} branch matrix. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ZohoSyncServicePullInventoryMatrixTest {

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

    @BeforeEach
    void setUp() {
        variantSupport = StorePricingTestSupport.variantSupport(objectMapper);
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
        lenient().when(applicationProperties.getZoho()).thenReturn(new ApplicationProperties.Zoho());
        lenient().when(productFieldMapper.applyPulledItem(any(), any(), anyBoolean())).thenReturn(Optional.empty());
        lenient().when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void pullInventory_skipsVariantParentWhenSkuMatchesParentGroupSku() {
        Product parent = new Product()
            .id(1L)
            .sku("PARENT-SKU")
            .itemStructure(ProductVariantSupport.STRUCTURE_VARIANTS)
            .variantsJson("[{\"sku\":\"CHILD\",\"size\":\"S\",\"color\":\"R\",\"rate\":1,\"stock\":1,\"active\":true}]");
        when(productRepository.findAll()).thenReturn(List.of(parent));
        when(productRepository.findOneByZohoItemId("z-parent")).thenReturn(Optional.of(parent));
        when(booksClient.listItems(1))
            .thenReturn(
                List.of(Map.of("item_id", "z-parent", "sku", "PARENT-SKU", "stock_on_hand", 5))
            );

        Map<String, Object> result = service.pullInventory();

        assertThat(result.get("skipped")).isEqualTo(1);
        assertThat(result.get("success")).isEqualTo(0);
    }

    @Test
    void pullInventory_updatesVariantRowOnParentByZohoItemId() {
        Product parent = new Product()
            .id(2L)
            .sku("VP-2")
            .itemStructure(ProductVariantSupport.STRUCTURE_VARIANTS)
            .variantsJson(
                """
                [{"sku":"VP-2-S","size":"S","color":"Blue","rate":1,"stock":1,"active":true,"zohoItemId":"z-child"}]
                """
            );
        when(productRepository.findAll()).thenReturn(List.of(parent));
        when(productRepository.findOneByZohoItemId("z-child")).thenReturn(Optional.of(parent));
        when(booksClient.listItems(1)).thenReturn(List.of(Map.of("item_id", "z-child", "sku", "VP-2-S", "stock_on_hand", 9)));

        Map<String, Object> result = service.pullInventory();

        assertThat(result.get("success")).isEqualTo(1);
        assertThat(parent.getVariantsJson()).contains("\"stock\":9");
    }

    @Test
    void pullInventory_variantIndexLookup_success() {
        Product parent = new Product()
            .id(3L)
            .sku("IDX-P")
            .itemStructure(ProductVariantSupport.STRUCTURE_VARIANTS)
            .variantsJson(
                """
                [{"sku":"IDX-SKU","size":"M","color":"G","rate":1,"stock":2,"active":true,"zohoItemId":"z-idx"}]
                """
            );
        when(productRepository.findAll()).thenReturn(List.of(parent));
        when(productRepository.findOneByZohoItemId("z-idx")).thenReturn(Optional.empty());
        when(booksClient.listItems(1)).thenReturn(List.of(Map.of("item_id", "z-idx", "sku", "IDX-SKU", "stock_on_hand", 4)));

        Map<String, Object> result = service.pullInventory();

        assertThat(result.get("success")).isEqualTo(1);
    }

    @Test
    void pullInventory_variantIndexLookup_failureWhenRowMissing() {
        Product parent = new Product()
            .id(4L)
            .sku("MISS-P")
            .itemStructure(ProductVariantSupport.STRUCTURE_VARIANTS)
            .variantsJson("[]");
        when(productRepository.findAll()).thenReturn(List.of(parent));
        when(productRepository.findOneByZohoItemId("z-miss")).thenReturn(Optional.empty());
        when(booksClient.listItems(1)).thenReturn(List.of(Map.of("item_id", "z-miss", "sku", "ORPHAN-SKU", "stock_on_hand", 1)));

        Map<String, Object> result = service.pullInventory();

        assertThat(result.get("failed")).isEqualTo(0);
        assertThat(result.get("skipped")).isEqualTo(1);
    }

    @Test
    void pullInventory_rejectsNegativeZohoStock() {
        Product product = new Product().id(9L).sku("NEG").zohoItemId("z-neg").stock(7);
        when(productRepository.findAll()).thenReturn(List.of(product));
        when(productRepository.findOneByZohoItemId("z-neg")).thenReturn(Optional.of(product));
        when(booksClient.listItems(1)).thenReturn(List.of(Map.of("item_id", "z-neg", "sku", "NEG", "stock_on_hand", -1)));

        Map<String, Object> result = service.pullInventory();

        assertThat(result.get("failed")).isEqualTo(1);
        assertThat(result.get("success")).isEqualTo(0);
        assertThat(product.getStock()).isEqualTo(7);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> failures = (List<Map<String, Object>>) result.get("failures");
        assertThat(failures).anySatisfy(f -> assertThat(String.valueOf(f.get("message"))).contains("Negative Zoho stock (-1)"));
    }

    @Test
    void pullInventory_skipsUnlinkedSku() {
        when(productRepository.findAll()).thenReturn(List.of());
        when(booksClient.listItems(1)).thenReturn(List.of(Map.of("item_id", "z-none", "sku", "NO-LINK", "stock_on_hand", 3)));

        Map<String, Object> result = service.pullInventory();

        assertThat(result.get("skipped")).isEqualTo(1);
    }

    @Test
    void pullInventory_skipsWhenOnlyItemIdWithoutSku() {
        when(productRepository.findAll()).thenReturn(List.of());
        when(booksClient.listItems(1)).thenReturn(List.of(Map.of("item_id", "only-id", "stock_on_hand", 2)));

        Map<String, Object> result = service.pullInventory();

        assertThat(result.get("skipped")).isEqualTo(1);
    }

    @Test
    void pullInventory_rowProcessingException_recordsFailure() {
        Product broken = new Product().id(5L).sku("BRK").stock(1);
        when(productRepository.findAll()).thenReturn(List.of());
        when(productRepository.findOneBySku("BRK")).thenReturn(Optional.of(broken));
        when(productRepository.save(any())).thenThrow(new RuntimeException("db locked"));
        when(booksClient.listItems(1)).thenReturn(List.of(Map.of("sku", "BRK", "stock_on_hand", 5)));

        Map<String, Object> result = service.pullInventory();

        assertThat(result.get("failed")).isEqualTo(1);
    }

    @Test
    void pullInventory_itemPageWithoutMorePages_stopsAfterFirstPage() {
        when(productRepository.findAll()).thenReturn(List.of());
        when(booksClient.listItems(1))
            .thenReturn(new ZohoBooksClient.ItemPage(List.of(Map.of("sku", "X", "stock_on_hand", 1)), false));

        Map<String, Object> result = service.pullInventory();

        assertThat(result.get("skipped")).isEqualTo(1);
    }

    @Test
    void pullInventory_httpErrorOnList_recordsFailure() {
        when(productRepository.findAll()).thenReturn(List.of());
        when(booksClient.listItems(1))
            .thenThrow(
                new RestClientResponseException("err", HttpStatus.TOO_MANY_REQUESTS.value(), "Too Many", null, null, null)
            );

        Map<String, Object> result = service.pullInventory();

        assertThat(result.get("failed")).isEqualTo(1);
    }
}
