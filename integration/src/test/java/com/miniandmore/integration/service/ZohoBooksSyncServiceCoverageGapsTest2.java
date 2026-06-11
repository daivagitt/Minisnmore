package com.miniandmore.integration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.miniandmore.integration.config.ApplicationProperties;
import com.miniandmore.integration.domain.Category;
import com.miniandmore.integration.domain.CustomerOrder;
import com.miniandmore.integration.domain.Product;
import com.miniandmore.integration.domain.ZohoSetting;
import com.miniandmore.integration.domain.enumeration.OrderStatus;
import com.miniandmore.integration.domain.enumeration.PaymentStatus;
import com.miniandmore.integration.repository.CategoryRepository;
import com.miniandmore.integration.repository.CustomerOrderRepository;
import com.miniandmore.integration.repository.OrderItemRepository;
import com.miniandmore.integration.repository.ProductRepository;
import com.miniandmore.integration.repository.ZohoSettingRepository;
import java.lang.reflect.Method;
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
class ZohoBooksSyncServiceCoverageGapsTest2 {

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

    private ApplicationProperties props;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private ZohoSetting setting;

    @BeforeEach
    void setUp() {
        lenient().doNothing().when(zohoItemCustomFields).enrichItemPayload(any(), any(), any());
        lenient().when(zohoItemCustomFields.usesCustomFieldsForMetadata(any())).thenReturn(false);
        props = new ApplicationProperties();
        ReflectionTestUtils.setField(service, "applicationProperties", props);
        ReflectionTestUtils.setField(service, "objectMapper", objectMapper);
        ReflectionTestUtils.setField(service, "self", selfProvider);
        ReflectionTestUtils.setField(service, "bulkSyncProgressTracker", bulkSyncProgressTracker);
        when(selfProvider.getObject()).thenReturn(service);
        setting = connected();
        when(zohoSettingRepository.findActiveOrLatest()).thenReturn(Optional.of(setting));
    }

    @Test
    void syncAllProducts_noCatalogueNoticesWhenStockSyncDisabled() {
        props.getZoho().setSyncProductStock(false);
        when(productRepository.findAllEligibleForZohoProductSync()).thenReturn(List.of());

        assertThat(service.syncAllProducts().notices()).isEmpty();
    }

    @Test
    void pullProductsFromZoho_stopsOnEmptyPage() {
        when(zohoBooksClient.get(any(), eq("/items"), any())).thenReturn(objectMapper.createObjectNode().putArray("items"));
        when(productRepository.findMaxSortOrder()).thenReturn(0);

        assertThat(service.pullProductsFromZoho().success()).isZero();
    }

    @Test
    void pullInventoryForProduct_trackInventoryDisabled() throws Exception {
        Product product = product(1L);
        product.setZohoItemId("z-1");
        ObjectNode itemResponse = objectMapper.createObjectNode();
        itemResponse.putObject("item").put("track_inventory", false);
        when(zohoBooksClient.get(setting, "/items/z-1", Map.of())).thenReturn(itemResponse);

        assertThatThrownBy(() -> invoke("pullInventoryForProduct", product)).hasCauseInstanceOf(ZohoBooksException.class);
    }

    @Test
    void pullOrderTracking_voidStatusCancelsOrder() {
        CustomerOrder order = paidOrder(80L);
        order.setZohoSalesOrderId("so-80");
        when(customerOrderRepository.findById(80L)).thenReturn(Optional.of(order));
        ObjectNode salesOrder = objectMapper.createObjectNode();
        salesOrder.putObject("salesorder").put("status", "void");
        when(zohoBooksClient.get(any(), eq("/salesorders/so-80"), any())).thenReturn(salesOrder);
        when(zohoBooksClient.get(any(), eq("/deliverychallans"), any())).thenReturn(objectMapper.createObjectNode().putArray("deliverychallans"));
        when(customerOrderRepository.save(order)).thenReturn(order);

        service.pullOrderTracking(80L);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void pullOrderTracking_subStatusImpliesShipped() {
        CustomerOrder order = paidOrder(81L);
        order.setZohoSalesOrderId("so-81");
        when(customerOrderRepository.findById(81L)).thenReturn(Optional.of(order));
        ObjectNode salesOrder = objectMapper.createObjectNode();
        var so = salesOrder.putObject("salesorder");
        so.put("status", "open");
        so.putArray("sub_statuses").addObject().put("status_code", "in_transit");
        when(zohoBooksClient.get(any(), eq("/salesorders/so-81"), any())).thenReturn(salesOrder);
        when(zohoBooksClient.get(any(), eq("/deliverychallans"), any())).thenReturn(objectMapper.createObjectNode().putArray("deliverychallans"));
        when(customerOrderRepository.save(order)).thenReturn(order);

        service.pullOrderTracking(81L);

        assertThat(order.getShipmentDate()).isNotBlank();
    }

    @Test
    void pullOrderTracking_skipsBlankCustomFieldValues() {
        CustomerOrder order = paidOrder(82L);
        order.setZohoSalesOrderId("so-82");
        when(customerOrderRepository.findById(82L)).thenReturn(Optional.of(order));
        ObjectNode salesOrder = objectMapper.createObjectNode();
        var so = salesOrder.putObject("salesorder");
        so.put("status", "open");
        var fields = so.putArray("custom_fields");
        fields.addObject().put("label", "AWB").put("value", "");
        fields.addObject().put("label", "Tracking").put("value", "TN-82");
        when(zohoBooksClient.get(any(), eq("/salesorders/so-82"), any())).thenReturn(salesOrder);
        when(zohoBooksClient.get(any(), eq("/deliverychallans"), any())).thenReturn(objectMapper.createObjectNode().putArray("deliverychallans"));
        when(customerOrderRepository.save(order)).thenReturn(order);

        service.pullOrderTracking(82L);

        assertThat(order.getTrackingNumber()).isEqualTo("TN-82");
    }

    @Test
    void retryPendingPaidOrdersOnSchedule_logsWhenWorkDone() {
        CustomerOrder order = paidOrder(83L);
        order.setZohoSalesOrderId("so-83");
        order.setZohoInvoiceId("inv-83");
        when(zohoSettingRepository.findActiveOrLatest()).thenReturn(Optional.of(setting));
        when(customerOrderRepository.findPaidOrdersMissingZohoSalesOrder(any(), any())).thenReturn(new PageImpl<>(List.of(order)));
        when(customerOrderRepository.findOneWithEagerRelationships(83L)).thenReturn(Optional.of(order));
        when(orderItemRepository.findAllByOrderId(83L)).thenReturn(List.of());
        when(customerOrderRepository.save(order)).thenReturn(order);

        service.retryPendingPaidOrdersOnSchedule();
    }

    @Test
    void pullOrderTrackingOnSchedule_logsSummary() {
        ReflectionTestUtils.setField(service, "orderTrackingSchedulerEnabled", true);
        when(customerOrderRepository.findOrdersForZohoTrackingPull(any(), any(), any())).thenReturn(new PageImpl<>(List.of()));

        service.pullOrderTrackingOnSchedule();
    }

    @Test
    void resolveTrackingStatus_defaultPending() {
        CustomerOrder order = paidOrder(84L);
        order.setStatus(OrderStatus.PENDING);
        order.setPaymentStatus(PaymentStatus.PAID);
        assertThat(service.resolveTrackingStatus(order)).isEqualTo("pending");
    }

    @Test
    void reflection_moneyCategoryAndCustomFields() throws Exception {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("rate", "  ");
        assertThat(((Optional<?>) invoke("readMoney", node, "rate")).isEmpty()).isTrue();

        node.put("rate", "not-a-number");
        assertThat(((Optional<?>) invoke("readMoney", node, "rate")).isEmpty()).isTrue();

        Category existing = new Category();
        existing.setName("Kids");
        existing.setSlug("kids");
        Optional<?> partial = (Optional<?>) invoke("matchCategory", "Kidswear", List.of(existing));
        assertThat(partial).isPresent();

        when(categoryRepository.findOneBySlug("")).thenReturn(Optional.of(existing));
        String slug = (String) invoke("generateUniqueCategorySlug", "!!!");
        assertThat(slug).endsWith("-2");

        ObjectNode item = objectMapper.createObjectNode();
        var fields = item.putArray("custom_fields");
        fields.addObject().put("label", "Colors").put("selected_option", "Navy");
        assertThat(invoke("listFromCustomField", fields, false)).isEqualTo("Navy");

        assertThat(invokeStatic("matchesListCustomFieldHint", "", false)).isEqualTo(false);
        assertThat(invokeStatic("matchesListCustomFieldHint", "cf_size", true)).isEqualTo(true);

        ObjectNode pullItem = objectMapper.createObjectNode();
        pullItem.put("name", "No Id Item");
        assertThatThrownBy(() -> invoke("upsertProductFromZohoItem", setting, pullItem, 1)).hasCauseInstanceOf(ZohoBooksException.class);

        ObjectNode listItem = objectMapper.createObjectNode();
        listItem.put("name", "No Id");
        JsonNode resolved = (JsonNode) invoke("resolveZohoItemForPull", setting, listItem);
        assertThat(resolved.path("name").asText()).isEqualTo("No Id");
    }

    private Object invoke(String name, Object... args) throws Exception {
        Method method = findMethod(name, args);
        return method.invoke(service, args);
    }

    private static Object invokeStatic(String name, Object... args) throws Exception {
        Class<?>[] types = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) {
            if (args[i] instanceof String) {
                types[i] = String.class;
            } else if (args[i] instanceof Boolean) {
                types[i] = boolean.class;
            } else {
                types[i] = args[i].getClass();
            }
        }
        Method method = ZohoBooksSyncService.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(null, args);
    }

    private Method findMethod(String name, Object... args) throws NoSuchMethodException {
        for (Method method : service.getClass().getDeclaredMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != args.length) {
                continue;
            }
            Class<?>[] params = method.getParameterTypes();
            boolean match = true;
            for (int i = 0; i < params.length; i++) {
                if (args[i] == null) {
                    continue;
                }
                Class<?> argClass = args[i].getClass();
                if (params[i].isPrimitive() && argClass == Boolean.class && params[i] == boolean.class) {
                    continue;
                }
                if (!params[i].isAssignableFrom(argClass)) {
                    match = false;
                    break;
                }
            }
            if (match) {
                method.setAccessible(true);
                return method;
            }
        }
        throw new NoSuchMethodException(name);
    }

    private static ZohoSetting connected() {
        ZohoSetting s = new ZohoSetting();
        s.setConnected(true);
        s.setDataCenter("in");
        return s;
    }

    private static Product product(long id) {
        Product p = new Product();
        p.setId(id);
        p.setName("P");
        p.setSku("SKU-" + id);
        p.setSlug("p-" + id);
        p.setPrice(BigDecimal.ONE);
        p.setStock(1);
        p.setActive(true);
        return p;
    }

    private static CustomerOrder paidOrder(long id) {
        CustomerOrder order = new CustomerOrder();
        order.setId(id);
        order.setOrderNumber("O-" + id);
        order.setPaymentStatus(PaymentStatus.PAID);
        order.setStatus(OrderStatus.CONFIRMED);
        order.setSubtotal(BigDecimal.TEN);
        order.setTotal(BigDecimal.TEN);
        order.setShippingAddressJson("{}");
        return order;
    }
}
