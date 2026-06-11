package com.miniandmore.integration.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ZohoBooksSyncServiceSchedulersTest {

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
    }

    @Test
    void pullOrderTrackingOnSchedule_disabledSkipsWork() {
        ReflectionTestUtils.setField(service, "orderTrackingSchedulerEnabled", false);
        service.pullOrderTrackingOnSchedule();
        verify(zohoBooksClient, never()).warmAccessToken(any());
    }

    @Test
    void pullOrderTrackingOnSchedule_runsWhenEnabled() {
        ReflectionTestUtils.setField(service, "orderTrackingSchedulerEnabled", true);
        ZohoSetting setting = connected();
        when(zohoSettingRepository.findActiveOrLatest()).thenReturn(Optional.of(setting));
        when(customerOrderRepository.findOrdersForZohoTrackingPull(any(), any(), any())).thenReturn(new PageImpl<>(List.of()));

        service.pullOrderTrackingOnSchedule();

        verify(zohoBooksClient).warmAccessToken(setting);
    }

    @Test
    void pullOrderTrackingOnSchedule_oauthDeniedOnWarmAccessToken() {
        ReflectionTestUtils.setField(service, "orderTrackingSchedulerEnabled", true);
        ZohoSetting setting = connected();
        when(zohoSettingRepository.findActiveOrLatest()).thenReturn(Optional.of(setting));
        doThrow(new ZohoBooksException("Zoho token refresh failed: Access Denied invalid_grant"))
            .when(zohoBooksClient)
            .warmAccessToken(setting);

        service.pullOrderTrackingOnSchedule();

        verify(customerOrderRepository, never()).findOrdersForZohoTrackingPull(any(), any(), any());
    }

    @Test
    void retryPendingPaidOrdersOnSchedule_delegates() {
        when(zohoSettingRepository.findActiveOrLatest()).thenReturn(Optional.empty());
        service.retryPendingPaidOrdersOnSchedule();
        verify(selfProvider).getObject();
    }

    @Test
    void pullOrderTrackingFromZoho_oauthBrokenSkipsRemaining() {
        ZohoSetting setting = connected();
        when(zohoSettingRepository.findActiveOrLatest()).thenReturn(Optional.of(setting));
        CustomerOrder order1 = order(1L);
        CustomerOrder order2 = order(2L);
        order1.setZohoSalesOrderId("so-1");
        order2.setZohoSalesOrderId("so-2");
        when(customerOrderRepository.findOrdersForZohoTrackingPull(any(), any(), any())).thenReturn(new PageImpl<>(List.of(order1, order2)));
        when(customerOrderRepository.findById(1L)).thenReturn(Optional.of(order1));
        doThrow(new ZohoBooksException("token refresh failed: Access Denied invalid_grant"))
            .when(zohoBooksClient)
            .get(any(), any(), any());

        service.pullOrderTrackingFromZoho();

        verify(customerOrderRepository, never()).findById(2L);
    }

    @Test
    void pullOrderTracking_enrichesFromDeliveryChallansAndCustomFields() {
        ZohoSetting setting = connected();
        when(zohoSettingRepository.findActiveOrLatest()).thenReturn(Optional.of(setting));
        CustomerOrder order = order(70L);
        order.setZohoSalesOrderId("so-70");
        when(customerOrderRepository.findById(70L)).thenReturn(Optional.of(order));

        ObjectNode salesOrder = objectMapper.createObjectNode();
        salesOrder.put("status", "open");
        var subStatuses = salesOrder.putArray("sub_statuses");
        subStatuses.addObject().put("display_name", "Shipped");
        var customFields = salesOrder.putArray("custom_fields");
        customFields.addObject().put("label", "AWB Number").put("value", "TRACK1");
        when(zohoBooksClient.get(setting, "/salesorders/so-70", Map.of())).thenReturn(objectMapper.createObjectNode().set("salesorder", salesOrder));

        ObjectNode challan = objectMapper.createObjectNode();
        challan.put("status", "delivered");
        challan.put("tracking_number", "TN-1");
        challan.put("shipment_date", "2026-06-02");
        when(zohoBooksClient.get(setting, "/deliverychallans", Map.of("salesorder_id", "so-70")))
            .thenReturn(objectMapper.createObjectNode().set("deliverychallans", objectMapper.createArrayNode().add(challan)));
        when(customerOrderRepository.save(order)).thenReturn(order);

        service.pullOrderTracking(70L);

        verify(customerOrderRepository).save(order);
    }

    private static ZohoSetting connected() {
        ZohoSetting s = new ZohoSetting();
        s.setConnected(true);
        s.setRefreshToken("r");
        s.setClientId("c");
        s.setClientSecret("s");
        s.setOrgId("o");
        s.setDataCenter("in");
        return s;
    }

    private static CustomerOrder order(Long id) {
        CustomerOrder order = new CustomerOrder();
        order.setId(id);
        order.setUserLogin("u");
        order.setOrderNumber("O-" + id);
        order.setStatus(OrderStatus.CONFIRMED);
        order.setPaymentStatus(PaymentStatus.PAID);
        order.setSubtotal(BigDecimal.TEN);
        order.setDiscount(BigDecimal.ZERO);
        order.setShipping(BigDecimal.ZERO);
        order.setTax(BigDecimal.ZERO);
        order.setDonation(BigDecimal.ZERO);
        order.setTotal(BigDecimal.TEN);
        order.setShippingAddressJson("{}");
        return order;
    }
}
