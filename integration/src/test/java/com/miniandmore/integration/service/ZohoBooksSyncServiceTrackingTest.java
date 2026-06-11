package com.miniandmore.integration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.miniandmore.integration.config.ApplicationProperties;
import com.miniandmore.integration.domain.CustomerOrder;
import com.miniandmore.integration.domain.ZohoSetting;
import com.miniandmore.integration.domain.enumeration.OrderStatus;
import com.miniandmore.integration.domain.enumeration.PaymentStatus;
import com.miniandmore.integration.repository.CategoryRepository;
import com.miniandmore.integration.repository.CustomerOrderRepository;
import com.miniandmore.integration.repository.OrderItemRepository;
import com.miniandmore.integration.repository.ProductRepository;
import com.miniandmore.integration.repository.ZohoSettingRepository;
import java.math.BigDecimal;
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
class ZohoBooksSyncServiceTrackingTest {

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
    private ZohoSetting setting;

    @BeforeEach
    void setUp() {
        lenient().doNothing().when(zohoItemCustomFields).enrichItemPayload(any(), any(), any());
        lenient().when(zohoItemCustomFields.usesCustomFieldsForMetadata(any())).thenReturn(false);
        ReflectionTestUtils.setField(service, "applicationProperties", new ApplicationProperties());
        ReflectionTestUtils.setField(service, "objectMapper", objectMapper);
        ReflectionTestUtils.setField(service, "self", selfProvider);
        ReflectionTestUtils.setField(service, "bulkSyncProgressTracker", bulkSyncProgressTracker);
        when(selfProvider.getObject()).thenReturn(service);
        setting = new ZohoSetting();
        setting.setConnected(true);
        setting.setDataCenter("in");
        when(zohoSettingRepository.findActiveOrLatest()).thenReturn(Optional.of(setting));
    }

    @Test
    void pullOrderTracking_enrichesFromSalesOrderAndChallans() {
        CustomerOrder order = paidOrder(400L);
        order.setZohoSalesOrderId("so-400");
        order.setStatus(OrderStatus.PENDING);
        when(customerOrderRepository.findById(400L)).thenReturn(Optional.of(order));

        ObjectNode salesOrder = objectMapper.createObjectNode();
        salesOrder.put("status", "open");
        salesOrder.put("shipment_date", "2026-02-01");
        salesOrder.put("delivery_method", "BlueDart");
        var customFields = salesOrder.putArray("custom_fields");
        customFields.addObject().put("label", "AWB Number").put("value", "AWB-99");
        customFields.addObject().put("label", "Courier").put("value", "Delhivery");
        var subStatuses = salesOrder.putArray("sub_statuses");
        subStatuses.addObject().put("display_name", "Shipped");

        ObjectNode soResponse = objectMapper.createObjectNode();
        soResponse.set("salesorder", salesOrder);
        when(zohoBooksClient.get(any(), eq("/salesorders/so-400"), any())).thenReturn(soResponse);

        ObjectNode challans = objectMapper.createObjectNode();
        var list = challans.putArray("deliverychallans");
        list.addObject().put("status", "delivered").put("tracking_number", "TN-1").put("delivery_method", "DTDC");
        when(zohoBooksClient.get(any(), eq("/deliverychallans"), any())).thenReturn(challans);
        when(customerOrderRepository.save(order)).thenReturn(order);

        CustomerOrder updated = service.pullOrderTracking(400L);

        assertThat(updated.getTrackingNumber()).isEqualTo("TN-1");
        assertThat(updated.getTrackingCarrier()).isEqualTo("DTDC");
        assertThat(updated.getShipmentDate()).isEqualTo("2026-02-01");
        assertThat(updated.getZohoOrderStatus()).isEqualTo("open");
        assertThat(service.resolveTrackingStatus(updated)).isEqualTo("delivered");
    }

    @Test
    void resolveTrackingStatus_variants() {
        CustomerOrder shipped = paidOrder(401L);
        shipped.setTrackingNumber("1");
        assertThat(service.resolveTrackingStatus(shipped)).isEqualTo("shipped");

        CustomerOrder delivered = paidOrder(402L);
        delivered.setStatus(OrderStatus.FULFILLED);
        assertThat(service.resolveTrackingStatus(delivered)).isEqualTo("delivered");

        CustomerOrder closed = paidOrder(403L);
        closed.setZohoOrderStatus("closed");
        assertThat(service.resolveTrackingStatus(closed)).isEqualTo("delivered");
    }

    private static CustomerOrder paidOrder(long id) {
        CustomerOrder order = new CustomerOrder();
        order.setId(id);
        order.setPaymentStatus(PaymentStatus.PAID);
        order.setStatus(OrderStatus.PENDING);
        order.setUserLogin("user");
        order.setOrderNumber("O-" + id);
        order.setSubtotal(BigDecimal.TEN);
        order.setTotal(BigDecimal.TEN);
        order.setShippingAddressJson("{\"full_name\":\"Test User\",\"email\":\"u@example.com\"}");
        return order;
    }
}
