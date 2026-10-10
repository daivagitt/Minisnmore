package com.miniandmore.app.web.rest.admin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.miniandmore.app.client.LedgerlyIntegrationClient;
import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.LedgerlySetting;
import com.miniandmore.app.domain.Product;
import com.miniandmore.app.domain.enumeration.LedgerlySyncStatus;
import com.miniandmore.app.repository.CustomerOrderRepository;
import com.miniandmore.app.repository.LedgerlySettingRepository;
import com.miniandmore.app.repository.ProductRepository;
import com.miniandmore.app.service.LedgerlySyncService;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

@WebMvcTest(LedgerlyAdminResource.class)
@AutoConfigureMockMvc(addFilters = false)
class LedgerlyAdminResourceTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LedgerlySettingRepository settingRepository;

    @MockitoBean
    private LedgerlySyncService syncService;

    @MockitoBean
    private LedgerlyIntegrationClient ledgerlyClient;

    @MockitoBean
    private CustomerOrderRepository customerOrderRepository;

    @MockitoBean
    private ProductRepository productRepository;

    @Test
    void integrationStatus_whenNoSetting_returnsDefaults() throws Exception {
        when(settingRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());

        mockMvc
            .perform(get("/api/v1/admin/ledgerly/integration-status"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.serverConfigured").value(false))
            .andExpect(jsonPath("$.connected").value(false));
    }

    @Test
    void integrationStatus_whenConfigured_returnsSetting() throws Exception {
        LedgerlySetting setting = ledgerlySetting();
        setting.setApiToken("lt_test");
        setting.setConnected(true);
        setting.setPullProductsAutoCreateCategory(true);
        when(settingRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(setting));

        mockMvc
            .perform(get("/api/v1/admin/ledgerly/integration-status"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.serverConfigured").value(true))
            .andExpect(jsonPath("$.connected").value(true))
            .andExpect(jsonPath("$.pullProductsAutoCreateCategory").value(true));
    }

    @Test
    void testConnection_success_marksConnected() throws Exception {
        LedgerlySetting setting = ledgerlySetting();
        setting.setId(1L);
        setting.setConnected(false);
        when(settingRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(setting));
        when(ledgerlyClient.testConnection()).thenReturn(Map.of("status", "ok", "message", "ready"));
        when(settingRepository.save(any(LedgerlySetting.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc
            .perform(get("/api/v1/admin/ledgerly/test"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.connected").value(true))
            .andExpect(jsonPath("$.ok").value(true));

        verify(settingRepository).save(any(LedgerlySetting.class));
    }

    @Test
    void testConnection_unauthorized_returnsStructuredError() throws Exception {
        when(settingRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());
        when(ledgerlyClient.testConnection()).thenThrow(
            new RestClientResponseException("401", HttpStatus.UNAUTHORIZED.value(), "Unauthorized", null, null, null)
        );

        mockMvc
            .perform(get("/api/v1/admin/ledgerly/test"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ok").value(false))
            .andExpect(jsonPath("$.error").value("unauthorized"));
    }

    @Test
    void testConnection_unreachable_returnsStructuredError() throws Exception {
        when(settingRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());
        when(ledgerlyClient.testConnection()).thenThrow(new ResourceAccessException("timeout"));

        mockMvc
            .perform(get("/api/v1/admin/ledgerly/test"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ok").value(false))
            .andExpect(jsonPath("$.error").value("unreachable"));
    }

    @Test
    void getSettings_returnsDto() throws Exception {
        LedgerlySetting setting = ledgerlySetting();
        setting.setApiBaseUrl("https://ledgerly.test/api");
        setting.setOrgId("1001");
        setting.setConnected(true);
        setting.setApiToken("secret");
        setting.setWebhookSecret("whsec");
        when(settingRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(setting));

        mockMvc
            .perform(get("/api/v1/admin/ledgerly/settings"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.apiBaseUrl").value("https://ledgerly.test/api"))
            .andExpect(jsonPath("$.hasToken").value(true))
            .andExpect(jsonPath("$.hasWebhookSecret").value(true));
    }

    @Test
    void saveSettings_persistsFields() throws Exception {
        LedgerlySetting setting = new LedgerlySetting();
        when(settingRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(setting));
        when(settingRepository.save(any(LedgerlySetting.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc
            .perform(
                put("/api/v1/admin/ledgerly/settings")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"api_base_url\":\"https://ledgerly.test/api\",\"api_token\":\"lt_new\",\"org_id\":\"42\",\"connected\":true}"
                    )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ok").value(true));
    }

    @Test
    void syncOrderByNumber_whenSynced_returnsOk() throws Exception {
        CustomerOrder order = new CustomerOrder().id(5L).orderNumber("MNM-1").ledgerlySyncStatus(LedgerlySyncStatus.SYNCED);
        when(customerOrderRepository.findOneByOrderNumber("MNM-1")).thenReturn(Optional.of(order), Optional.of(order));

        mockMvc
            .perform(post("/api/v1/admin/ledgerly/sync-orders/by-number/MNM-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ok").value(true))
            .andExpect(jsonPath("$.ledgerlySyncStatus").value("SYNCED"));

        verify(syncService).syncOrder(5L);
    }

    @Test
    void syncProduct_returnsProductLedgerlyFields() throws Exception {
        Product product = new Product().id(9L).ledgerlyItemId("item-9").ledgerlySyncStatus(LedgerlySyncStatus.SYNCED);
        when(productRepository.findById(9L)).thenReturn(Optional.of(product));

        mockMvc
            .perform(post("/api/v1/admin/ledgerly/sync-products/9"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ledgerlyItemId").value("item-9"));

        verify(syncService).syncProduct(9L);
    }

    @Test
    void pullInventory_delegatesToSyncService() throws Exception {
        when(syncService.pullInventory()).thenReturn(Map.of("successCount", 1));

        mockMvc
            .perform(post("/api/v1/admin/ledgerly/pull-inventory"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.successCount").value(1));
    }

    @Test
    void pullOrderTracking_delegatesToSyncService() throws Exception {
        when(syncService.pullAllTracking()).thenReturn(Map.of("successCount", 2));

        mockMvc
            .perform(post("/api/v1/admin/ledgerly/pull-order-tracking"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.successCount").value(2));
    }

    @Test
    void syncProducts_delegatesToSyncService() throws Exception {
        when(syncService.syncAllProducts()).thenReturn(Map.of("successCount", 2));

        mockMvc
            .perform(post("/api/v1/admin/ledgerly/sync-products"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.successCount").value(2));
    }

    @Test
    void pullProducts_delegatesToSyncService() throws Exception {
        when(syncService.pullProducts()).thenReturn(Map.of("successCount", 3));

        mockMvc
            .perform(post("/api/v1/admin/ledgerly/pull-products"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.successCount").value(3));
    }

    @Test
    void retryOrders_delegatesToSyncService() throws Exception {
        when(syncService.retryFailedOrders()).thenReturn(Map.of("successCount", 1));

        mockMvc
            .perform(post("/api/v1/admin/ledgerly/retry-orders"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.successCount").value(1));
    }

    @Test
    void syncOrderByNumber_notFound_returnsError() throws Exception {
        when(customerOrderRepository.findOneByOrderNumber("MISSING")).thenReturn(Optional.empty());

        mockMvc
            .perform(post("/api/v1/admin/ledgerly/sync-orders/by-number/MISSING"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ok").value(false))
            .andExpect(jsonPath("$.error").value("not found"));
    }

    @Test
    void testConnection_forbidden_returnsUnauthorizedShape() throws Exception {
        when(settingRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());
        when(ledgerlyClient.testConnection()).thenThrow(
            new RestClientResponseException("403", HttpStatus.FORBIDDEN.value(), "Forbidden", null, null, null)
        );

        mockMvc
            .perform(get("/api/v1/admin/ledgerly/test"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.error").value("unauthorized"));
    }

    @Test
    void testConnection_statusOkWithoutExplicitFlags_marksConnected() throws Exception {
        LedgerlySetting setting = ledgerlySetting();
        setting.setId(2L);
        when(settingRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(setting));
        when(ledgerlyClient.testConnection()).thenReturn(Map.of("status", "ok"));
        when(settingRepository.save(any(LedgerlySetting.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc
            .perform(get("/api/v1/admin/ledgerly/test"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.connected").value(true))
            .andExpect(jsonPath("$.ok").value(true));

        verify(settingRepository).save(any(LedgerlySetting.class));
    }

    @Test
    void saveSettings_camelCaseFieldsAndPullProductsFlag() throws Exception {
        LedgerlySetting setting = new LedgerlySetting();
        when(settingRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(setting));
        when(settingRepository.save(any(LedgerlySetting.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc
            .perform(
                put("/api/v1/admin/ledgerly/settings")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"apiBaseUrl\":\"https://ledgerly.test/v2\",\"apiToken\":\"tok\",\"orgId\":\"7\",\"pullProductsAutoCreateCategory\":true}"
                    )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ok").value(true));
    }

    @Test
    void testConnection_http500_returnsStructuredError() throws Exception {
        when(settingRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());
        when(ledgerlyClient.testConnection()).thenThrow(
            new RestClientResponseException("500", HttpStatus.INTERNAL_SERVER_ERROR.value(), "Error", null, null, null)
        );

        mockMvc
            .perform(get("/api/v1/admin/ledgerly/test"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ok").value(false))
            .andExpect(jsonPath("$.error").value("http_500"));
    }

    @Test
    void testConnection_genericException_returnsBadGateway() throws Exception {
        when(settingRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());
        when(ledgerlyClient.testConnection()).thenThrow(new IllegalStateException("boom"));

        mockMvc.perform(get("/api/v1/admin/ledgerly/test")).andExpect(status().isBadGateway());
    }

    @Test
    void setPullProductsAutoCreateCategory_persistsFlag() throws Exception {
        LedgerlySetting setting = ledgerlySetting();
        when(settingRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(setting));
        when(settingRepository.save(any(LedgerlySetting.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc
            .perform(
                put("/api/v1/admin/ledgerly/pull-products-auto-create-category")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"enabled\":true}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pullProductsAutoCreateCategory").value(true));
    }

    @Test
    void saveSettings_acceptsSnakeCaseAndWebhookSecret() throws Exception {
        LedgerlySetting setting = new LedgerlySetting();
        when(settingRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(setting));
        when(settingRepository.save(any(LedgerlySetting.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc
            .perform(
                put("/api/v1/admin/ledgerly/settings")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"api_base_url\":\"https://ledgerly.test/api\",\"webhook_secret\":\"whsec_test\",\"org_id\":\"99\"}"
                    )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ok").value(true));
    }

    private static LedgerlySetting ledgerlySetting() {
        LedgerlySetting setting = new LedgerlySetting();
        setting.setConnected(false);
        setting.setPullProductsAutoCreateCategory(false);
        return setting;
    }
}
