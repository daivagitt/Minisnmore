package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.client.ZohoBooksClient;
import com.miniandmore.app.config.ApplicationProperties;
import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.OrderItem;
import com.miniandmore.app.domain.Product;
import com.miniandmore.app.repository.CustomerOrderRepository;
import com.miniandmore.app.repository.CustomerProfileRepository;
import com.miniandmore.app.repository.OrderItemRepository;
import com.miniandmore.app.repository.ProductRepository;
import com.miniandmore.app.repository.ZohoSettingRepository;
import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ZohoSyncServiceOrderLineBranchTest {

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
    private ZohoSyncService service;

    @BeforeEach
    void setUp() {
        ProductVariantSupport variantSupport = StorePricingTestSupport.variantSupport(objectMapper);
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
        ApplicationProperties.Zoho zoho = new ApplicationProperties.Zoho();
        zoho.setGstTaxId("tax-gst");
        lenient().when(applicationProperties.getZoho()).thenReturn(zoho);
        lenient().when(productFieldMapper.applyPulledItem(any(), any(), anyBoolean())).thenReturn(Optional.empty());
        lenient().when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void zohoOrderLineSku_prefersVariantSkuThenMatrixThenParentSku() {
        Product variants = new Product()
            .id(10L)
            .sku("PARENT")
            .itemStructure(ProductVariantSupport.STRUCTURE_VARIANTS)
            .variantsJson(
                """
                [{"sku":"PARENT-S","size":"S","color":"Red","rate":10,"stock":1,"active":true}]
                """
            );
        OrderItem withVariantSku = new OrderItem().variantSku("EXPLICIT");
        String explicit = ReflectionTestUtils.invokeMethod(service, "zohoOrderLineSku", withVariantSku, variants);
        assertThat(explicit).isEqualTo("EXPLICIT");

        OrderItem matrix = new OrderItem().itemSize("S").color("Red");
        String resolved = ReflectionTestUtils.invokeMethod(service, "zohoOrderLineSku", matrix, variants);
        assertThat(resolved).isEqualTo("PARENT-S");

        Product simple = new Product().sku(" SIMPLE ");
        String simpleSku = ReflectionTestUtils.invokeMethod(service, "zohoOrderLineSku", new OrderItem(), simple);
        assertThat(simpleSku).isEqualTo("SIMPLE");
    }

    @Test
    void buildOrderLineItems_includesCodFeeLineWithGstTax() {
        OrderItem item = new OrderItem().productId("1").productName("Dress").qty(1).price(BigDecimal.TEN);
        CustomerOrder order = new CustomerOrder().paymentMethod("cod").codFee(new BigDecimal("40"));
        order.setItemses(new LinkedHashSet<>(Set.of(item)));
        Product product = new Product().id(1L).sku("D-1").zohoItemId("z-1");
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lines = ReflectionTestUtils.invokeMethod(service, "buildOrderLineItems", order);

        assertThat(lines).hasSize(2);
        assertThat(lines.get(1).get("name")).asString().contains("COD");
        assertThat(lines.get(1).get("tax_id")).isEqualTo("tax-gst");
    }

    @Test
    void buildOrderLineItems_throwsWhenZohoItemMissingForLine() {
        OrderItem item = new OrderItem().productId("99").productName("Ghost").qty(1).price(BigDecimal.ONE);
        CustomerOrder order = new CustomerOrder();
        order.setItemses(new LinkedHashSet<>(Set.of(item)));
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service, "buildOrderLineItems", order))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Zoho item is required");
    }

    @Test
    void resolveZohoItemIdForOrderLine_variantWithoutResolvableSku_returnsEmpty() {
        Product parent = new Product()
            .id(20L)
            .sku("VAR-P")
            .itemStructure(ProductVariantSupport.STRUCTURE_VARIANTS)
            .variantsJson("[]");
        OrderItem item = new OrderItem().productId("20").itemSize("S").color("Blue");
        when(productRepository.findById(20L)).thenReturn(Optional.of(parent));

        Optional<String> itemId = ReflectionTestUtils.invokeMethod(service, "resolveZohoItemIdForOrderLine", item);

        assertThat(itemId).isEmpty();
    }

    @Test
    void resolveZohoItemIdForOrderLine_backfillsSimpleProductZohoItemId() {
        Product product = new Product().id(30L).sku("SIM-30");
        OrderItem item = new OrderItem().productId("30");
        when(productRepository.findById(30L)).thenReturn(Optional.of(product));
        when(booksClient.findItemBySku("SIM-30")).thenReturn(Map.of("item_id", "z-30"));

        Optional<String> itemId = ReflectionTestUtils.invokeMethod(service, "resolveZohoItemIdForOrderLine", item);

        assertThat(itemId).contains("z-30");
        assertThat(product.getZohoItemId()).isEqualTo("z-30");
    }
}
