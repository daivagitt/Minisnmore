package com.miniandmore.integration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.miniandmore.integration.config.ApplicationProperties;
import com.miniandmore.integration.domain.CustomerOrder;
import com.miniandmore.integration.domain.Product;
import com.miniandmore.integration.domain.ZohoSetting;
import com.miniandmore.integration.domain.enumeration.PaymentStatus;
import com.miniandmore.integration.domain.enumeration.ZohoSyncStatus;
import com.miniandmore.integration.repository.CategoryRepository;
import com.miniandmore.integration.repository.CustomerOrderRepository;
import com.miniandmore.integration.repository.OrderItemRepository;
import com.miniandmore.integration.repository.ProductRepository;
import com.miniandmore.integration.repository.ZohoSettingRepository;
import java.math.BigDecimal;
import java.util.List;
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
class ZohoBooksSyncServiceFailurePathsTest {

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
    }

    @Test
    void syncOrder_unpaidMarksFailed() {
        CustomerOrder order = new CustomerOrder();
        order.setId(1L);
        order.setPaymentStatus(PaymentStatus.PENDING);
        when(customerOrderRepository.findOneWithEagerRelationships(1L)).thenReturn(Optional.of(order));
        when(customerOrderRepository.save(order)).thenReturn(order);

        assertThatThrownBy(() -> service.syncOrder(1L)).isInstanceOf(ZohoBooksException.class);
        assertThat(order.getZohoSyncStatus()).isEqualTo(ZohoSyncStatus.FAILED);
    }

    @Test
    void pullProductsFromZoho_countsItemFailures() {
        ObjectNode page = objectMapper.createObjectNode();
        page.putArray("items").addObject().put("item_id", "z-bad");
        when(zohoBooksClient.get(any(), any(), any())).thenReturn(page, objectMapper.createObjectNode());
        when(productRepository.findMaxSortOrder()).thenReturn(0);

        ZohoBooksSyncService.SyncSummary summary = service.pullProductsFromZoho();

        assertThat(summary.failed()).isEqualTo(1);
    }

    @Test
    void syncProduct_missingSkuMarksFailed() {
        Product product = new Product();
        product.setId(2L);
        product.setName("No SKU");
        when(productRepository.findOneWithEagerRelationships(2L)).thenReturn(Optional.of(product));
        when(productRepository.save(product)).thenReturn(product);

        assertThatThrownBy(() -> service.syncProduct(2L)).isInstanceOf(ZohoBooksException.class);
        assertThat(product.getZohoSyncStatus()).isEqualTo(ZohoSyncStatus.FAILED);
    }

}
