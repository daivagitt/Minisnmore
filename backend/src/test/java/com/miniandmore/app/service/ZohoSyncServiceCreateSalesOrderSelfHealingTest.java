package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.client.ZohoBooksClient;
import com.miniandmore.app.config.ApplicationProperties;
import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.OrderItem;
import com.miniandmore.app.domain.Product;
import com.miniandmore.app.domain.enumeration.PaymentStatus;
import com.miniandmore.app.repository.CustomerOrderRepository;
import com.miniandmore.app.repository.CustomerProfileRepository;
import com.miniandmore.app.repository.OrderItemRepository;
import com.miniandmore.app.repository.ProductRepository;
import com.miniandmore.app.repository.ZohoSettingRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientResponseException;

/**
 * Exhaustive branch coverage for {@link ZohoSyncService#createSalesOrderSelfHealing} retry tree.
 */
@ExtendWith(MockitoExtension.class)
class ZohoSyncServiceCreateSalesOrderSelfHealingTest {

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
        zoho.setSyncProductStock(false);
        lenient().when(applicationProperties.getZoho()).thenReturn(zoho);
        lenient().when(productFieldMapper.applyPulledItem(any(), any(), anyBoolean())).thenReturn(Optional.empty());
    }

    private CustomerOrder orderWithLine() {
        Product product = new Product().id(10L).sku("SKU-10").zohoItemId("zoho-10").active(true);
        OrderItem line = new OrderItem().id(1L).productId("10").productName("Dress").qty(1).price(BigDecimal.TEN);
        CustomerOrder order = new CustomerOrder()
            .id(301L)
            .orderNumber("MNM-SO-301")
            .paymentStatus(PaymentStatus.PAID)
            .shippingAddressJson(
                "{\"full_name\":\"Jane\",\"line1\":\"12 MG\",\"city\":\"Bengaluru\",\"state\":\"Karnataka\",\"pincode\":\"560001\"}"
            );
        order.addItems(line);
        when(orderItemRepository.findAllByOrderId(301L)).thenReturn(List.of(line));
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        return order;
    }

    @Test
    void createSalesOrderSelfHealing_successOnFirstAttempt() {
        CustomerOrder order = orderWithLine();
        when(booksClient.createSalesOrder(any())).thenReturn(Map.of("salesorder", Map.of("salesorder_id", "so-1")));

        @SuppressWarnings("unchecked")
        Map<String, Object> result = ReflectionTestUtils.invokeMethod(
            service,
            "createSalesOrderSelfHealing",
            order,
            "cust-1",
            true,
            true,
            true,
            false
        );

        assertThat(result).containsKey("salesorder");
        verify(booksClient, times(1)).createSalesOrder(any());
    }

    @Test
    void createSalesOrderSelfHealing_invalidCustomField_returnsExistingSalesOrderByReference() {
        CustomerOrder order = orderWithLine();
        RestClientResponseException customField = zohoBody(400, "{\"code\":120100,\"message\":\"custom field\"}");
        when(booksClient.createSalesOrder(any())).thenThrow(customField);
        when(booksClient.findSalesOrderByReferenceNumber("MNM-SO-301"))
            .thenReturn(Map.of("salesorder_id", "so-existing", "reference_number", "MNM-SO-301"));

        @SuppressWarnings("unchecked")
        Map<String, Object> result = ReflectionTestUtils.invokeMethod(
            service,
            "createSalesOrderSelfHealing",
            order,
            "cust-1",
            true,
            true,
            true,
            false
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> salesOrder = (Map<String, Object>) result.get("salesorder");
        assertThat(salesOrder.get("salesorder_id")).isEqualTo("so-existing");
        verify(booksClient, times(1)).createSalesOrder(any());
    }

    @Test
    void createSalesOrderSelfHealing_invalidCustomField_retriesWithoutChannelField() {
        CustomerOrder order = orderWithLine();
        RestClientResponseException customField = zohoBody(400, "{\"code\":120100}");
        when(booksClient.createSalesOrder(any()))
            .thenThrow(customField)
            .thenReturn(Map.of("salesorder", Map.of("salesorder_id", "so-retry")));
        when(booksClient.findSalesOrderByReferenceNumber("MNM-SO-301")).thenReturn(null);

        @SuppressWarnings("unchecked")
        Map<String, Object> result = ReflectionTestUtils.invokeMethod(
            service,
            "createSalesOrderSelfHealing",
            order,
            "cust-1",
            true,
            true,
            true,
            false
        );

        assertThat(result.get("salesorder")).isNotNull();
        verify(booksClient, times(2)).createSalesOrder(any());
    }

    @Test
    void createSalesOrderSelfHealing_invalidGstTreatment_retriesWithoutGstField() {
        CustomerOrder order = orderWithLine();
        RestClientResponseException gst = zohoBody(400, "{\"code\":8,\"message\":\"invalid gst_treatment\"}");
        when(booksClient.createSalesOrder(any()))
            .thenThrow(gst)
            .thenReturn(Map.of("salesorder", Map.of("salesorder_id", "so-gst")));
        when(booksClient.findSalesOrderByReferenceNumber("MNM-SO-301")).thenReturn(null);

        @SuppressWarnings("unchecked")
        Map<String, Object> result = ReflectionTestUtils.invokeMethod(
            service,
            "createSalesOrderSelfHealing",
            order,
            "cust-1",
            true,
            true,
            false,
            false
        );

        assertThat(result.get("salesorder")).isNotNull();
        verify(booksClient, times(2)).createSalesOrder(any());
    }

    @Test
    void createSalesOrderSelfHealing_invalidPlaceOfSupply_retriesWithoutPlaceOfSupply() {
        CustomerOrder order = orderWithLine();
        RestClientResponseException pos = zohoBody(400, "{\"code\":8,\"message\":\"invalid place_of_supply\"}");
        when(booksClient.createSalesOrder(any()))
            .thenThrow(pos)
            .thenReturn(Map.of("salesorder", Map.of("salesorder_id", "so-pos")));
        when(booksClient.findSalesOrderByReferenceNumber("MNM-SO-301")).thenReturn(null);

        @SuppressWarnings("unchecked")
        Map<String, Object> result = ReflectionTestUtils.invokeMethod(
            service,
            "createSalesOrderSelfHealing",
            order,
            "cust-1",
            true,
            true,
            true,
            false
        );

        assertThat(result.get("salesorder")).isNotNull();
        verify(booksClient, times(2)).createSalesOrder(any());
    }

    @Test
    void createSalesOrderSelfHealing_unavailableSalesOrderItem_repairsAndRetries() {
        CustomerOrder order = orderWithLine();
        Product product = new Product().id(10L).sku("SKU-10").zohoItemId("zoho-10").active(true);
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        lenient().when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(productRepository.updateZohoSyncSuccess(any(), any(), any(), any())).thenReturn(1);

        RestClientResponseException unavailable = zohoBody(400, "{\"code\":2006,\"message\":\"salesorder item missing\"}");
        when(booksClient.createSalesOrder(any()))
            .thenThrow(unavailable)
            .thenReturn(Map.of("salesorder", Map.of("salesorder_id", "so-repair")));
        lenient().when(booksClient.findItemBySku("SKU-10")).thenReturn(Map.of("item_id", "zoho-10"));
        lenient().when(booksClient.updateItem(any(), any())).thenReturn(Map.of("item", Map.of("item_id", "zoho-10")));

        @SuppressWarnings("unchecked")
        Map<String, Object> result = ReflectionTestUtils.invokeMethod(
            service,
            "createSalesOrderSelfHealing",
            order,
            "cust-1",
            false,
            false,
            false,
            false
        );

        assertThat(result.get("salesorder")).isNotNull();
        verify(booksClient, times(2)).createSalesOrder(any());
    }

    @Test
    void createSalesOrderSelfHealing_invalidGstTreatment_returnsExistingSalesOrderByReference() {
        CustomerOrder order = orderWithLine();
        RestClientResponseException gst = zohoBody(400, "{\"code\":8,\"message\":\"invalid gst_treatment\"}");
        when(booksClient.createSalesOrder(any())).thenThrow(gst);
        when(booksClient.findSalesOrderByReferenceNumber("MNM-SO-301"))
            .thenReturn(Map.of("salesorder_id", "so-gst-existing"));

        @SuppressWarnings("unchecked")
        Map<String, Object> result = ReflectionTestUtils.invokeMethod(
            service,
            "createSalesOrderSelfHealing",
            order,
            "cust-1",
            true,
            true,
            false,
            false
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> salesOrder = (Map<String, Object>) result.get("salesorder");
        assertThat(salesOrder.get("salesorder_id")).isEqualTo("so-gst-existing");
        verify(booksClient, times(1)).createSalesOrder(any());
    }

    @Test
    void createSalesOrderSelfHealing_unavailableItemAfterRepairRethrows() {
        CustomerOrder order = orderWithLine();
        RestClientResponseException unavailable = zohoBody(400, "{\"code\":2006,\"message\":\"salesorder item missing\"}");
        when(booksClient.createSalesOrder(any())).thenThrow(unavailable);

        assertThatThrownBy(() ->
            ReflectionTestUtils.invokeMethod(
                service,
                "createSalesOrderSelfHealing",
                order,
                "cust-1",
                false,
                false,
                false,
                true
            )
        ).isInstanceOf(RestClientResponseException.class);
    }

    @Test
    void createSalesOrderSelfHealing_nonRecoverableErrorRethrows() {
        CustomerOrder order = orderWithLine();
        RestClientResponseException fatal = zohoBody(500, "{\"code\":9999}");
        when(booksClient.createSalesOrder(any())).thenThrow(fatal);

        assertThatThrownBy(() ->
            ReflectionTestUtils.invokeMethod(
                service,
                "createSalesOrderSelfHealing",
                order,
                "cust-1",
                true,
                true,
                false,
                true
            )
        ).isInstanceOf(RestClientResponseException.class);
    }

    @Test
    void createSalesOrderSelfHealing_codOrder_includesCodNotesInPayload() {
        CustomerOrder order = orderWithLine();
        order.setPaymentMethod("cod");
        order.setCodFee(BigDecimal.valueOf(49));
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        when(booksClient.createSalesOrder(payloadCaptor.capture()))
            .thenReturn(Map.of("salesorder", Map.of("salesorder_id", "so-cod")));

        ReflectionTestUtils.invokeMethod(service, "createSalesOrderSelfHealing", order, "cust-1", false, true, false, false);

        Map<String, Object> payload = payloadCaptor.getValue();
        assertThat(payload.get("notes")).asString().contains("Cash on Delivery");
        assertThat(payload.get("payment_terms_label")).isNotNull();
    }

    private static RestClientResponseException zohoBody(int status, String json) {
        return new RestClientResponseException("zoho", status, "Error", null, json.getBytes(), null);
    }
}
