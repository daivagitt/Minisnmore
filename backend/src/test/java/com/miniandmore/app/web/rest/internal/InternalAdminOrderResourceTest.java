package com.miniandmore.app.web.rest.internal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.client.LedgerlyIntegrationClient;
import com.miniandmore.app.config.ApplicationProperties;
import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.enumeration.LedgerlySyncStatus;
import com.miniandmore.app.domain.enumeration.OrderStatus;
import com.miniandmore.app.domain.enumeration.PackageType;
import com.miniandmore.app.domain.enumeration.PaymentStatus;
import com.miniandmore.app.domain.enumeration.ZohoSyncStatus;
import com.miniandmore.app.repository.*;
import com.miniandmore.app.domain.enumeration.ReturnLogisticsMode;
import com.miniandmore.app.service.LedgerlySyncService;
import com.miniandmore.app.service.OrderCancellationService;
import com.miniandmore.app.service.OrderEventService;
import com.miniandmore.app.service.OrderPackageService;
import com.miniandmore.app.service.OrderShipmentService;
import com.miniandmore.app.service.ZohoSyncService;
import com.miniandmore.app.web.rest.v1.AccountOrderDataAccess;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(InternalAdminOrderResource.class)
@AutoConfigureMockMvc(addFilters = false)
@SuppressWarnings({ "unchecked", "rawtypes" })
@Import({
    InternalAdminOrderResourceTest.DataAccessConfig.class,
    com.miniandmore.app.web.rest.errors.ResponseStatusExceptionHandler.class,
})
class InternalAdminOrderResourceTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationProperties applicationProperties;

    @MockitoBean
    private CouponRepository couponRepository;

    @MockitoBean
    private CustomerProfileRepository customerProfileRepository;

    @MockitoBean
    private CustomerOrderRepository customerOrderRepository;

    @MockitoBean
    private OrderItemRepository orderItemRepository;

    @MockitoBean
    private ProductRepository productRepository;

    @MockitoBean
    private SiteSettingRepository siteSettingRepository;

    @MockitoBean
    private LedgerlyIntegrationClient ledgerlyIntegrationClient;

    @MockitoBean
    private LedgerlySyncService ledgerlySyncService;

    @MockitoBean
    private ZohoSyncService zohoSyncService;

    @MockitoBean
    private OrderEventService orderEventService;

    @MockitoBean
    private InternalAccountOrderResource accountOrders;

    @MockitoBean
    private OrderShipmentService orderShipmentService;

    @MockitoBean
    private OrderPackageService orderPackageService;

    @MockitoBean
    private OrderCancellationService orderCancellationService;

    @MockitoBean
    private StoreAddressRepository storeAddressRepository;

    private CustomerOrder codOrder() {
        return new CustomerOrder()
            .id(900L)
            .orderNumber("MNM-COD1")
            .paymentMethod("cod")
            .paymentStatus(PaymentStatus.PENDING)
            .total(BigDecimal.valueOf(1148))
            .codFee(BigDecimal.valueOf(49))
            .placedAt(Instant.parse("2026-08-01T10:00:00Z"))
            .shippingAddressJson("{\"full_name\":\"Jane Doe\",\"phone\":\"9876543210\",\"city\":\"Bengaluru\"}");
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void codPending_listsPendingCodOrders() throws Exception {
        when(customerOrderRepository.findCodOrdersByPaymentStatus(PaymentStatus.PENDING)).thenReturn(List.of(codOrder()));

        mockMvc
            .perform(get("/internal/v1/admin/orders/cod-pending"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.orders[0].order_number").value("MNM-COD1"))
            .andExpect(jsonPath("$.data.orders[0].full_name").value("Jane Doe"))
            .andExpect(jsonPath("$.data.orders[0].phone").value("9876543210"))
            .andExpect(jsonPath("$.data.orders[0].coupon_code").value(""));
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void orderedLogins_returnsDistinctPlacedOrderCustomerLogins() throws Exception {
        when(
            customerOrderRepository.findDistinctUserLoginsWithStatusNotIn(
                List.of(OrderStatus.CANCELLED, OrderStatus.PENDING)
            )
        )
            .thenReturn(List.of("today.customer", "week.customer"));

        mockMvc
            .perform(get("/internal/v1/admin/orders/ordered-logins"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.logins[0]").value("today.customer"))
            .andExpect(jsonPath("$.data.logins[1]").value("week.customer"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void listOrders_returnsSyncVisibilityForRequestedRange() throws Exception {
        CustomerOrder order = codOrder()
            .status(OrderStatus.CONFIRMED)
            .paymentStatus(PaymentStatus.PAID)
            .couponCode("WELCOME10")
            .zohoSyncStatus(ZohoSyncStatus.FAILED)
            .zohoSyncError("Zoho unavailable")
            .ledgerlySyncStatus(LedgerlySyncStatus.SYNCED)
            .ledgerlyLastSyncedAt(Instant.parse("2026-08-01T10:05:00Z"));
        when(customerOrderRepository.findAll(any(Specification.class), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(order), PageRequest.of(0, 10), 1));

        mockMvc
            .perform(
                get("/internal/v1/admin/orders")
                    .param("from", "2026-08-01T00:00:00Z")
                    .param("to", "2026-08-08T00:00:00Z")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.orders[0].order_number").value("MNM-COD1"))
            .andExpect(jsonPath("$.data.orders[0].coupon_code").value("WELCOME10"))
            .andExpect(jsonPath("$.data.orders[0].tracking_status").value("confirmed"))
            .andExpect(jsonPath("$.data.orders[0].payment_status").value("paid"))
            .andExpect(jsonPath("$.data.orders[0].zoho_sync_status").value("failed"))
            .andExpect(jsonPath("$.data.orders[0].zoho_sync_error").value("Zoho unavailable"))
            .andExpect(jsonPath("$.data.orders[0].zoho_sync_eligible").value(true))
            .andExpect(jsonPath("$.data.orders[0].ledgerly_sync_status").value("synced"))
            .andExpect(jsonPath("$.data.orders[0].ledgerly_sync_eligible").value(true))
            .andExpect(jsonPath("$.data.page").value(1))
            .andExpect(jsonPath("$.data.pageSize").value(10))
            .andExpect(jsonPath("$.data.totalItems").value(1))
            .andExpect(jsonPath("$.data.totalPages").value(1))
            .andExpect(jsonPath("$.data.express_cancellation_count").value(0));
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void listOrders_includesExpressCancellationCount() throws Exception {
        when(customerOrderRepository.findAll(any(Specification.class), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));
        when(orderShipmentService.countExpressCancellations()).thenReturn(4L);

        mockMvc
            .perform(get("/internal/v1/admin/orders"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.express_cancellation_count").value(4));
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void listOrders_cancelledRefundedOrderWithZohoDocs_staysSyncEligible() throws Exception {
        CustomerOrder order = codOrder()
            .paymentMethod("online")
            .status(OrderStatus.CANCELLED)
            .paymentStatus(PaymentStatus.REFUNDED)
            .zohoSalesOrderId("so-cancel")
            .zohoInvoiceId("inv-cancel")
            .zohoSyncStatus(ZohoSyncStatus.FAILED)
            .zohoSyncError("Could not void paid invoice");
        when(customerOrderRepository.findAll(any(Specification.class), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(order), PageRequest.of(0, 10), 1));

        mockMvc
            .perform(get("/internal/v1/admin/orders"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.orders[0].status").value("cancelled"))
            .andExpect(jsonPath("$.data.orders[0].payment_status").value("refunded"))
            .andExpect(jsonPath("$.data.orders[0].zoho_sync_eligible").value(true));
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void listOrders_paginatesAllOrdersWhenNoDateRange() throws Exception {
        CustomerOrder order = codOrder().status(OrderStatus.CONFIRMED).paymentStatus(PaymentStatus.PAID);
        when(customerOrderRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(
            new PageImpl<>(List.of(order), PageRequest.of(1, 10), 20)
        );

        mockMvc
            .perform(get("/internal/v1/admin/orders").param("page", "2").param("size", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.orders[0].order_number").value("MNM-COD1"))
            .andExpect(jsonPath("$.data.page").value(2))
            .andExpect(jsonPath("$.data.totalItems").value(20))
            .andExpect(jsonPath("$.data.totalPages").value(2));
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void listOrders_appliesStatusAndSearchFilters() throws Exception {
        CustomerOrder order = codOrder().status(OrderStatus.CONFIRMED).paymentStatus(PaymentStatus.PAID);
        when(customerOrderRepository.findAll(any(Specification.class), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(order), PageRequest.of(0, 10), 1));

        mockMvc
            .perform(
                get("/internal/v1/admin/orders")
                    .param("q", "MNM-COD")
                    .param("status", "confirmed")
                    .param("paymentStatus", "paid")
                    .param("paymentMethod", "cod")
                    .param("zohoSyncStatus", "attention")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.orders[0].order_number").value("MNM-COD1"))
            .andExpect(jsonPath("$.data.totalItems").value(1));
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void listOrders_rejectsInvalidStatusFilter() throws Exception {
        mockMvc
            .perform(get("/internal/v1/admin/orders").param("status", "nope"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void listOrders_rejectsRangeLongerThan31Days() throws Exception {
        mockMvc
            .perform(
                get("/internal/v1/admin/orders")
                    .param("from", "2026-08-01T00:00:00Z")
                    .param("to", "2026-09-02T00:00:00Z")
            )
            .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void listOrders_capsPageSizeAndClampsPageToOneWhenEmpty() throws Exception {
        when(customerOrderRepository.findAll(any(Specification.class), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 50), 0));

        mockMvc
            .perform(get("/internal/v1/admin/orders").param("page", "0").param("size", "999"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.page").value(1))
            .andExpect(jsonPath("$.data.pageSize").value(50))
            .andExpect(jsonPath("$.data.totalPages").value(1));
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void listOrders_rejectsInvalidZohoFilter() throws Exception {
        mockMvc
            .perform(get("/internal/v1/admin/orders").param("zohoSyncStatus", "nope"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void listOrders_rejectsPartialRange() throws Exception {
        mockMvc
            .perform(get("/internal/v1/admin/orders").param("from", "2026-08-01T00:00:00Z"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void listOrders_rejectsInvalidRange() throws Exception {
        mockMvc
            .perform(
                get("/internal/v1/admin/orders")
                    .param("from", "2026-08-08T00:00:00Z")
                    .param("to", "2026-08-01T00:00:00Z")
            )
            .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void markCodCollected_marksPaidAndSyncsZoho() throws Exception {
        when(customerOrderRepository.findOneByOrderNumber("MNM-COD1")).thenReturn(Optional.of(codOrder()));
        when(customerOrderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc
            .perform(post("/internal/v1/admin/orders/MNM-COD1/mark-cod-collected"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.paymentStatus").value("paid"));

        verify(zohoSyncService).syncOrder(900L);
        verify(zohoSyncService).pullOrderTracking(900L);
        verify(ledgerlySyncService, never()).syncOrder(any());
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void markCodCollected_rejectsNonCodOrder() throws Exception {
        CustomerOrder online = codOrder().paymentMethod("online");
        when(customerOrderRepository.findOneByOrderNumber("MNM-COD1")).thenReturn(Optional.of(online));

        mockMvc.perform(post("/internal/v1/admin/orders/MNM-COD1/mark-cod-collected")).andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void markCodCollected_rejectsCancelledOrder() throws Exception {
        CustomerOrder cancelled = codOrder().status(OrderStatus.CANCELLED);
        when(customerOrderRepository.findOneByOrderNumber("MNM-COD1")).thenReturn(Optional.of(cancelled));

        mockMvc.perform(post("/internal/v1/admin/orders/MNM-COD1/mark-cod-collected")).andExpect(status().isConflict());

        verify(customerOrderRepository, never()).save(any(CustomerOrder.class));
        verify(zohoSyncService, never()).syncOrder(any());
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void markCodCollected_rejectsNonPendingCodOrder() throws Exception {
        CustomerOrder failed = codOrder().status(OrderStatus.CONFIRMED).paymentStatus(PaymentStatus.FAILED);
        when(customerOrderRepository.findOneByOrderNumber("MNM-COD1")).thenReturn(Optional.of(failed));

        mockMvc.perform(post("/internal/v1/admin/orders/MNM-COD1/mark-cod-collected")).andExpect(status().isConflict());

        verify(customerOrderRepository, never()).save(any(CustomerOrder.class));
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void getOrder_returnsInvoicePayloadForExistingOrder() throws Exception {
        CustomerOrder order = codOrder().status(OrderStatus.FULFILLED).paymentStatus(PaymentStatus.PAID);
        when(customerOrderRepository.findOneByOrderNumber("MNM-COD1")).thenReturn(Optional.of(order));
        when(accountOrders.orderDetail(order)).thenReturn(java.util.Map.of("order_number", "MNM-COD1", "status", "fulfilled"));

        mockMvc
            .perform(get("/internal/v1/admin/orders/MNM-COD1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.order.order_number").value("MNM-COD1"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void bookShipment_returnsBookedOrderSummary() throws Exception {
        CustomerOrder booked = codOrder()
            .status(OrderStatus.CONFIRMED)
            .paymentStatus(PaymentStatus.PAID)
            .trackingNumber("XB-1")
            .trackingCarrier("XpressBees")
            .trackingUrl("https://track/XB-1")
            .shipmentLabelUrl("https://label/XB-1")
            .shipmentLogisticsMode(ReturnLogisticsMode.XPRESSBEES)
            .shipmentDate("2026-09-10");
        when(
            orderShipmentService.bookShipment(
                "MNM-COD1",
                ReturnLogisticsMode.XPRESSBEES,
                null,
                null,
                null,
                null,
                false,
                null,
                null,
                null,
                null,
                null
            )
        ).thenReturn(booked);

        mockMvc
            .perform(
                post("/internal/v1/admin/orders/MNM-COD1/book-shipment")
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .content("{\"mode\":\"xpressbees\"}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.order.order_number").value("MNM-COD1"))
            .andExpect(jsonPath("$.data.order.tracking_number").value("XB-1"))
            .andExpect(jsonPath("$.data.order.shipment_label_url").value("https://label/XB-1"))
            .andExpect(jsonPath("$.data.order.can_book_shipment").value(false));
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void packageRates_returnsChargeableWeightAndCouriers() throws Exception {
        when(orderPackageService.previewRates("MNM-COD1", 500, 10, 10, 10)).thenReturn(
            java.util.Map.of(
                "actual_weight_grams",
                500,
                "volumetric_weight_grams",
                200,
                "chargeable_weight_grams",
                500,
                "preferred_courier_name",
                "Surface Xpressbees 0.5 K.G",
                "couriers",
                java.util.List.of()
            )
        );

        mockMvc
            .perform(
                post("/internal/v1/admin/orders/MNM-COD1/package-rates")
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .content("{\"weight_grams\":500,\"length_cm\":10,\"breadth_cm\":10,\"height_cm\":10}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.chargeable_weight_grams").value(500))
            .andExpect(jsonPath("$.data.preferred_courier_name").value("Surface Xpressbees 0.5 K.G"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void completePackage_returnsHandWrappedOrder() throws Exception {
        CustomerOrder wrapped = codOrder()
            .status(OrderStatus.HAND_WRAPPED)
            .paymentStatus(PaymentStatus.PAID)
            .packageWeightGrams(500)
            .packageLengthCm(12)
            .packageBreadthCm(10)
            .packageHeightCm(8)
            .packageChargeableWeightGrams(500);
        when(
            orderPackageService.completePackage("MNM-COD1", 500, 12, 10, 8, "1", "Surface Xpressbees 0.5 K.G", null, null, null)
        ).thenReturn(wrapped);

        mockMvc
            .perform(
                post("/internal/v1/admin/orders/MNM-COD1/complete-package")
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .content(
                        "{\"weight_grams\":500,\"length_cm\":12,\"breadth_cm\":10,\"height_cm\":8,\"courier_id\":\"1\",\"courier_name\":\"Surface Xpressbees 0.5 K.G\"}"
                    )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.order.order_number").value("MNM-COD1"))
            .andExpect(jsonPath("$.data.order.status").value("hand_wrapped"))
            .andExpect(jsonPath("$.data.order.package_weight_grams").value(500))
            .andExpect(jsonPath("$.data.order.package_length_cm").value(12))
            .andExpect(jsonPath("$.data.order.package_slip_number").value("PKG-00900"))
            .andExpect(jsonPath("$.data.order.package_type").value("custom"))
            .andExpect(jsonPath("$.data.order.shipment_order_number").value("SHP-00900"))
            .andExpect(jsonPath("$.data.order.shipping_address.full_name").value("Jane Doe"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void completePackage_acceptsZohoEnvelopeType() throws Exception {
        CustomerOrder wrapped = codOrder()
            .status(OrderStatus.HAND_WRAPPED)
            .paymentStatus(PaymentStatus.PAID)
            .packageWeightGrams(250)
            .packageLengthCm(32)
            .packageBreadthCm(24)
            .packageHeightCm(2)
            .packageType(PackageType.ENVELOPE);
        when(
            orderPackageService.completePackage("MNM-COD1", 250, 32, 24, 2, null, null, "envelope", null, null)
        ).thenReturn(wrapped);

        mockMvc
            .perform(
                post("/internal/v1/admin/orders/MNM-COD1/complete-package")
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .content("{\"weight_grams\":250,\"length_cm\":32,\"breadth_cm\":24,\"height_cm\":2,\"package_type\":\"envelope\"}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.order.package_type").value("envelope"))
            .andExpect(jsonPath("$.data.order.package_height_cm").value(2));
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void getOrder_pullsZohoTrackingWhenStale() throws Exception {
        CustomerOrder order = codOrder().status(OrderStatus.CONFIRMED).zohoSalesOrderId("so-1");
        when(customerOrderRepository.findOneByOrderNumber("MNM-COD1")).thenReturn(Optional.of(order));
        when(accountOrders.orderDetail(any(CustomerOrder.class)))
            .thenReturn(java.util.Map.of("order_number", "MNM-COD1", "status", "confirmed"));

        mockMvc.perform(get("/internal/v1/admin/orders/MNM-COD1")).andExpect(status().isOk());

        verify(zohoSyncService).pullOrderTracking(900L);
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void getOrder_skipsZohoPullWhenTrackingDisabled() throws Exception {
        applicationProperties.getZoho().getOrderTracking().setEnabled(false);
        try {
            CustomerOrder order = codOrder().status(OrderStatus.CONFIRMED).zohoSalesOrderId("so-1");
            when(customerOrderRepository.findOneByOrderNumber("MNM-COD1")).thenReturn(Optional.of(order));
            when(accountOrders.orderDetail(order)).thenReturn(java.util.Map.of("order_number", "MNM-COD1", "status", "confirmed"));

            mockMvc.perform(get("/internal/v1/admin/orders/MNM-COD1")).andExpect(status().isOk());

            verify(zohoSyncService, never()).pullOrderTracking(any());
        } finally {
            applicationProperties.getZoho().getOrderTracking().setEnabled(true);
        }
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void cancelOrder_beforeShipment_cancelsWithoutTouchingCarrier() throws Exception {
        CustomerOrder cancelled = codOrder().status(OrderStatus.CANCELLED).paymentStatus(PaymentStatus.REFUNDED);
        when(orderCancellationService.cancelOrderFromAdmin(eq("MNM-COD1"), eq("Item is out of stock"), any(), eq(null)))
            .thenReturn(cancelled);

        mockMvc
            .perform(
                post("/internal/v1/admin/orders/MNM-COD1/cancel")
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .content("{\"reason\":\"Item is out of stock\"}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.order.order_number").value("MNM-COD1"))
            .andExpect(jsonPath("$.data.order.status").value("cancelled"));

        verify(orderShipmentService, never()).cancelShipment(any(), any(), any(), any());
        verify(orderCancellationService).cancelOrderFromAdmin(eq("MNM-COD1"), eq("Item is out of stock"), any(), eq(null));
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void cancelOrder_withBookedLabel_delegatesToCancellationService() throws Exception {
        CustomerOrder cancelled = codOrder().status(OrderStatus.CANCELLED);
        when(orderCancellationService.cancelOrderFromAdmin(
            eq("MNM-COD1"),
            eq("Unable to fulfil this order"),
            any(),
            eq(null)
        )).thenReturn(cancelled);

        mockMvc
            .perform(
                post("/internal/v1/admin/orders/MNM-COD1/cancel")
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .content("{\"reason\":\"Unable to fulfil this order\"}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.order.status").value("cancelled"));

        verify(orderCancellationService).cancelOrderFromAdmin(
            eq("MNM-COD1"),
            eq("Unable to fulfil this order"),
            any(),
            eq(null)
        );
        verify(orderShipmentService, never()).cancelShipment(any(), any(), any(), any());
    }

    @TestConfiguration
    static class DataAccessConfig {

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        ApplicationProperties applicationProperties() {
            return new ApplicationProperties();
        }

        @Bean
        AccountOrderDataAccess accountOrderDataAccess(
            CouponRepository couponRepository,
            CustomerProfileRepository customerProfileRepository,
            CustomerOrderRepository customerOrderRepository,
            OrderItemRepository orderItemRepository,
            ProductRepository productRepository,
            SiteSettingRepository siteSettingRepository,
            LedgerlyIntegrationClient ledgerlyIntegrationClient
        ) {
            return new AccountOrderDataAccess(
                couponRepository,
                customerProfileRepository,
                customerOrderRepository,
                orderItemRepository,
                productRepository,
                siteSettingRepository,
                ledgerlyIntegrationClient
            );
        }
    }
}
