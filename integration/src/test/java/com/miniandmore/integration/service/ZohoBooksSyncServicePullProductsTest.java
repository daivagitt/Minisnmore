package com.miniandmore.integration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.miniandmore.integration.config.ApplicationProperties;
import com.miniandmore.integration.domain.CustomerOrder;
import com.miniandmore.integration.domain.Product;
import com.miniandmore.integration.domain.ZohoSetting;
import com.miniandmore.integration.repository.CategoryRepository;
import com.miniandmore.integration.repository.CustomerOrderRepository;
import com.miniandmore.integration.repository.OrderItemRepository;
import com.miniandmore.integration.repository.ProductRepository;
import com.miniandmore.integration.repository.ZohoSettingRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ZohoBooksSyncServicePullProductsTest {

    @Mock
    private ZohoSettingRepository zohoSettingRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CustomerOrderRepository customerOrderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private ZohoBooksClient zohoBooksClient;

    @Mock
    private ZohoItemImageSync zohoItemImageSync;

    @Mock
    private ZohoItemCustomFields zohoItemCustomFields;

    @Mock
    private ZohoStockSync zohoStockSync;

    @Mock
    private ObjectProvider<ZohoBooksSyncService> selfProvider;

    
    private final ZohoBulkSyncProgressTracker bulkSyncProgressTracker = new ZohoBulkSyncProgressTracker();

    @InjectMocks
    private ZohoBooksSyncService service;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        lenient().doNothing().when(zohoItemCustomFields).enrichItemPayload(any(), any(), any());
        lenient().when(zohoItemCustomFields.usesCustomFieldsForMetadata(any())).thenReturn(false);
        ReflectionTestUtils.setField(service, "applicationProperties", new ApplicationProperties());
        ReflectionTestUtils.setField(service, "objectMapper", objectMapper);
        ReflectionTestUtils.setField(service, "self", selfProvider);
        ReflectionTestUtils.setField(service, "bulkSyncProgressTracker", bulkSyncProgressTracker);
        when(selfProvider.getObject()).thenReturn(service);
        ZohoSetting setting = new ZohoSetting();
        setting.setConnected(true);
        setting.setDataCenter("in");
        when(zohoSettingRepository.findActiveOrLatest()).thenReturn(Optional.of(setting));
        when(productRepository.findMaxSortOrder()).thenReturn(0);
        when(productRepository.findOneBySlug(any())).thenReturn(Optional.empty());
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(zohoItemImageSync.importZohoItemImage(any(), any(), any())).thenReturn(Optional.of("[\"https://cdn.example.com/imported.jpg\"]"));
        when(zohoItemImageSync.parseImageUrls(any())).thenReturn(List.of());
    }

    @Test
    void pullProductsFromZoho_paginatesUntilEmpty() {
        ObjectNode page1 = objectMapper.createObjectNode();
        var items1 = page1.putArray("items");
        ObjectNode item = items1.addObject();
        item.put("item_id", "z-page1");
        item.put("name", "Page Item");
        item.put("sku", "P1");
        item.put("rate", 1);
        when(zohoBooksClient.get(any(), eq("/items"), any())).thenReturn(page1, objectMapper.createObjectNode().putArray("items"));
        when(zohoBooksClient.get(any(), eq("/items/z-page1"), any())).thenReturn(objectMapper.createObjectNode().set("item", item));

        ZohoBooksSyncService.SyncSummary summary = service.pullProductsFromZoho();
        assertThat(summary.success()).isEqualTo(1);
    }

    @Test
    void pullProductsFromZoho_importsPage() {
        ObjectNode page = objectMapper.createObjectNode();
        var items = page.putArray("items");
        ObjectNode item = items.addObject();
        item.put("item_id", "z-new");
        item.put("name", "Imported Dress");
        item.put("sku", "IMP-1");
        item.put("rate", 10);
        when(zohoBooksClient.get(any(), eq("/items"), any())).thenReturn(page, objectMapper.createObjectNode().putArray("items"));
        when(zohoBooksClient.get(any(), eq("/items/z-new"), any())).thenReturn(objectMapper.createObjectNode().set("item", item));

        ZohoBooksSyncService.SyncSummary summary = service.pullProductsFromZoho();

        assertThat(summary.success()).isEqualTo(1);
        verify(zohoItemImageSync).importZohoItemImage(any(), eq("z-new"), any());
    }

    @Test
    void pullProductsFromZoho_stripsDuplicateCategoryMetadataFromDescription() {
        ObjectNode page = objectMapper.createObjectNode();
        ObjectNode item = page.putArray("items").addObject();
        item.put("item_id", "z-dup");
        item.put("name", "Little Dress");
        item.put("sku", "LD-1");
        item.put("rate", 10);
        item.put(
            "description",
            "Soft organic cotton\nCategory: little\nCategory: little\nCategory: little\nSizes: 0-3M\nColors: Pink"
        );
        when(zohoBooksClient.get(any(), eq("/items"), any())).thenReturn(page, objectMapper.createObjectNode().putArray("items"));
        when(zohoBooksClient.get(any(), eq("/items/z-dup"), any())).thenReturn(objectMapper.createObjectNode().set("item", item));
        when(productRepository.findOneByZohoItemId("z-dup")).thenReturn(Optional.empty());
        when(categoryRepository.findAllByActiveTrueOrderBySortOrderAsc()).thenReturn(List.of());

        ZohoBooksSyncService.SyncSummary summary = service.pullProductsFromZoho();

        assertThat(summary.success()).isEqualTo(1);
        org.mockito.ArgumentCaptor<Product> saved = org.mockito.ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(saved.capture());
        assertThat(saved.getValue().getDescription()).isEqualTo("Soft organic cotton");
    }

    @Test
    void pullProductsFromZoho_inactiveZohoItemMarksAdminInactive() {
        Product existing = new Product();
        existing.setId(88L);
        existing.setName("Old Vest");
        existing.setSku("VEST-1");
        existing.setZohoItemId("z-inactive");
        existing.setActive(true);
        existing.setPrice(new BigDecimal("10"));
        existing.setStock(1);
        existing.setSortOrder(1);

        ObjectNode page = objectMapper.createObjectNode();
        ObjectNode item = page.putArray("items").addObject();
        item.put("item_id", "z-inactive");
        item.put("name", "Old Vest");
        item.put("sku", "VEST-1");
        item.put("status", "inactive");
        item.put("rate", 10);
        when(zohoBooksClient.get(any(), eq("/items"), any())).thenReturn(page, objectMapper.createObjectNode().putArray("items"));
        when(zohoBooksClient.get(any(), eq("/items/z-inactive"), any())).thenReturn(objectMapper.createObjectNode().set("item", item));
        when(productRepository.findOneByZohoItemId("z-inactive")).thenReturn(Optional.of(existing));
        when(productRepository.findAllByZohoItemIdIsNotNull()).thenReturn(List.of(existing));

        ZohoBooksSyncService.SyncSummary summary = service.pullProductsFromZoho();

        assertThat(summary.success()).isEqualTo(1);
        assertThat(existing.getActive()).isFalse();
    }

    @Test
    void syncProductAsync_swallowsFailures() {
        when(productRepository.findOneWithEagerRelationships(99L)).thenReturn(Optional.empty());
        service.syncProductAsync(99L);
    }

    @Test
    void createCreditNoteForReturnedOrder_success() {
        ZohoSetting setting = new ZohoSetting();
        setting.setConnected(true);
        when(zohoSettingRepository.findActiveOrLatest()).thenReturn(Optional.of(setting));

        CustomerOrder order = new CustomerOrder();
        order.setId(500L);
        order.setZohoInvoiceId("inv-500");
        ObjectNode invoiceResponse = objectMapper.createObjectNode();
        invoiceResponse.putObject("invoice").put("customer_id", "cust-500");
        when(zohoBooksClient.get(any(), eq("/invoices/inv-500"), any())).thenReturn(invoiceResponse);

        Product product = new Product();
        product.setId(1L);
        product.setZohoItemId("z-1");
        product.setPrice(BigDecimal.TEN);
        com.miniandmore.integration.domain.OrderItem line = new com.miniandmore.integration.domain.OrderItem();
        line.setProductId("1");
        line.setQty(1);
        line.setPrice(BigDecimal.TEN);
        when(orderItemRepository.findAllByOrderId(500L)).thenReturn(List.of(line));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        ObjectNode creditResponse = objectMapper.createObjectNode();
        creditResponse.putObject("creditnote").put("creditnote_id", "cn-1");
        when(zohoBooksClient.post(any(), eq("/creditnotes"), any(), any())).thenReturn(creditResponse);

        assertThat(service.createCreditNoteForReturnedOrder(order, "R-500")).contains("cn-1");
    }
}
