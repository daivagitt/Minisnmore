package com.miniandmore.app.web.rest.internal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.client.LedgerlyIntegrationClient;
import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.enumeration.LedgerlySyncStatus;
import com.miniandmore.app.domain.enumeration.OrderStatus;
import com.miniandmore.app.domain.enumeration.PaymentStatus;
import com.miniandmore.app.domain.enumeration.ZohoSyncStatus;
import com.miniandmore.app.repository.*;
import com.miniandmore.app.service.LedgerlySyncService;
import com.miniandmore.app.service.OrderEventService;
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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(InternalAdminOrderResource.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({
    InternalAdminOrderResourceTest.DataAccessConfig.class,
    com.miniandmore.app.web.rest.errors.ResponseStatusExceptionHandler.class,
})
class InternalAdminOrderResourceTest {

    @Autowired
    private MockMvc mockMvc;

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
            .andExpect(jsonPath("$.data.orders[0].phone").value("9876543210"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void listOrders_returnsSyncVisibilityForRequestedRange() throws Exception {
        Instant from = Instant.parse("2026-08-01T00:00:00Z");
        Instant to = Instant.parse("2026-08-08T00:00:00Z");
        CustomerOrder order = codOrder()
            .status(OrderStatus.CONFIRMED)
            .paymentStatus(PaymentStatus.PAID)
            .zohoSyncStatus(ZohoSyncStatus.FAILED)
            .zohoSyncError("Zoho unavailable")
            .ledgerlySyncStatus(LedgerlySyncStatus.SYNCED)
            .ledgerlyLastSyncedAt(Instant.parse("2026-08-01T10:05:00Z"));
        when(customerOrderRepository.findAllByPlacedAtGreaterThanEqualAndPlacedAtLessThanOrderByPlacedAtDesc(from, to))
            .thenReturn(List.of(order));

        mockMvc
            .perform(
                get("/internal/v1/admin/orders")
                    .param("from", "2026-08-01T00:00:00Z")
                    .param("to", "2026-08-08T00:00:00Z")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.orders[0].order_number").value("MNM-COD1"))
            .andExpect(jsonPath("$.data.orders[0].payment_status").value("paid"))
            .andExpect(jsonPath("$.data.orders[0].zoho_sync_status").value("failed"))
            .andExpect(jsonPath("$.data.orders[0].zoho_sync_error").value("Zoho unavailable"))
            .andExpect(jsonPath("$.data.orders[0].zoho_sync_eligible").value(true))
            .andExpect(jsonPath("$.data.orders[0].ledgerly_sync_status").value("synced"))
            .andExpect(jsonPath("$.data.orders[0].ledgerly_sync_eligible").value(true));
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
    void markCodCollected_marksPaidAndSyncsLedgerly() throws Exception {
        when(customerOrderRepository.findOneByOrderNumber("MNM-COD1")).thenReturn(Optional.of(codOrder()));
        when(customerOrderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc
            .perform(post("/internal/v1/admin/orders/MNM-COD1/mark-cod-collected"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.paymentStatus").value("paid"));

        verify(ledgerlySyncService).syncOrder(900L);
        verify(zohoSyncService).syncOrder(900L);
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void markCodCollected_rejectsNonCodOrder() throws Exception {
        CustomerOrder online = codOrder().paymentMethod("online");
        when(customerOrderRepository.findOneByOrderNumber("MNM-COD1")).thenReturn(Optional.of(online));

        mockMvc.perform(post("/internal/v1/admin/orders/MNM-COD1/mark-cod-collected")).andExpect(status().isConflict());
    }

    @TestConfiguration
    static class DataAccessConfig {

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
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
