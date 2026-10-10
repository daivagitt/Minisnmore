package com.miniandmore.app.web.rest.admin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.Product;
import com.miniandmore.app.domain.ZohoSetting;
import com.miniandmore.app.domain.enumeration.ZohoSyncStatus;
import com.miniandmore.app.repository.CustomerOrderRepository;
import com.miniandmore.app.repository.ProductRepository;
import com.miniandmore.app.repository.ZohoSettingRepository;
import com.miniandmore.app.service.ZohoOAuthService;
import com.miniandmore.app.service.ZohoSettingsService;
import com.miniandmore.app.service.ZohoSyncService;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
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

@WebMvcTest(ZohoAdminResource.class)
@AutoConfigureMockMvc(addFilters = false)
class ZohoAdminResourceTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ZohoSettingRepository settingRepository;

    @MockitoBean
    private ZohoOAuthService oauthService;

    @MockitoBean
    private ZohoSyncService syncService;

    @MockitoBean
    private ZohoSettingsService zohoSettingsService;

    @MockitoBean
    private CustomerOrderRepository customerOrderRepository;

    @MockitoBean
    private ProductRepository productRepository;

    @Test
    void integrationStatus_whenNoSetting_returnsDefaults() throws Exception {
        when(oauthService.isServerConfigured()).thenReturn(true);
        when(oauthService.orgId()).thenReturn("env-org");
        when(oauthService.dataCenter()).thenReturn("in");
        when(settingRepository.findActiveOrLatest()).thenReturn(Optional.empty());

        mockMvc
            .perform(get("/api/v1/admin/zoho/integration-status"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.serverConfigured").value(true))
            .andExpect(jsonPath("$.connected").value(false))
            .andExpect(jsonPath("$.dataCenter").value("in"))
            .andExpect(jsonPath("$.organizationId").value("env-org"))
            .andExpect(jsonPath("$.pullProductsAutoCreateCategory").value(false));
    }

    @Test
    void integrationStatus_whenConnected_returnsStoredFields() throws Exception {
        ZohoSetting setting = zohoSetting();
        setting.setConnected(true);
        setting.setPullProductsAutoCreateCategory(true);
        setting.setLastProductPullAt(Instant.parse("2026-05-01T10:00:00Z"));
        when(oauthService.isServerConfigured()).thenReturn(true);
        when(oauthService.orgId()).thenReturn("org-42");
        when(oauthService.dataCenter()).thenReturn("eu");
        when(settingRepository.findActiveOrLatest()).thenReturn(Optional.of(setting));

        mockMvc
            .perform(get("/api/v1/admin/zoho/integration-status"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.connected").value(true))
            .andExpect(jsonPath("$.pullProductsAutoCreateCategory").value(true))
            .andExpect(jsonPath("$.lastProductPullAt").value("2026-05-01T10:00:00Z"));
    }

    @Test
    void testConnection_success_marksConnectedAndReturnsOrganizationName() throws Exception {
        ZohoSetting setting = zohoSetting();
        setting.setId(1L);
        setting.setConnected(false);
        when(settingRepository.findActiveOrLatest()).thenReturn(Optional.of(setting));
        when(settingRepository.save(any(ZohoSetting.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(syncService.testConnection()).thenReturn(
            Map.of("organizations", List.of(Map.of("name", "Mini and More")))
        );

        mockMvc
            .perform(get("/api/v1/admin/zoho/test"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.connected").value(true))
            .andExpect(jsonPath("$.message").value("Connected to Zoho Books"))
            .andExpect(jsonPath("$.organizationName").value("Mini and More"));

        verify(settingRepository).save(any(ZohoSetting.class));
    }

    @Test
    void testConnection_unauthorized_returnsStructuredErrorAndMarksDisconnected() throws Exception {
        ZohoSetting setting = zohoSetting();
        setting.setId(2L);
        setting.setConnected(true);
        when(settingRepository.findActiveOrLatest()).thenReturn(Optional.of(setting));
        when(settingRepository.save(any(ZohoSetting.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(syncService.testConnection()).thenThrow(
            new RestClientResponseException("401", HttpStatus.UNAUTHORIZED.value(), "Unauthorized", null, null, null)
        );

        mockMvc
            .perform(get("/api/v1/admin/zoho/test"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.connected").value(false))
            .andExpect(jsonPath("$.organizationName").doesNotExist())
            .andExpect(jsonPath("$.message").value("Zoho rejected the credentials (HTTP 401). Reconnect or check the org ID."));
    }

    @Test
    void testConnection_unreachable_returnsNetworkMessage() throws Exception {
        when(settingRepository.findActiveOrLatest()).thenReturn(Optional.empty());
        when(syncService.testConnection()).thenThrow(new ResourceAccessException("timeout"));

        mockMvc
            .perform(get("/api/v1/admin/zoho/test"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.connected").value(false))
            .andExpect(jsonPath("$.message").value("Could not reach Zoho. Check network access."));
    }

    @Test
    void testConnection_illegalState_returnsMessageWithoutBadGateway() throws Exception {
        when(settingRepository.findActiveOrLatest()).thenReturn(Optional.empty());
        when(syncService.testConnection()).thenThrow(new IllegalStateException("Zoho is not connected"));

        mockMvc
            .perform(get("/api/v1/admin/zoho/test"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.connected").value(false))
            .andExpect(jsonPath("$.message").value("Zoho is not connected"));
    }

    @Test
    void testConnection_unexpectedException_returnsBadGateway() throws Exception {
        when(settingRepository.findActiveOrLatest()).thenReturn(Optional.empty());
        when(syncService.testConnection()).thenThrow(new RuntimeException("boom"));

        mockMvc.perform(get("/api/v1/admin/zoho/test")).andExpect(status().isBadGateway());
    }

    @Test
    void connectUrl_whenClientMissing_returnsBadRequest() throws Exception {
        when(oauthService.oauthSetup()).thenReturn(oauthSetup());
        when(oauthService.buildConnectUrl("http://localhost:5173/admin/zoho"))
            .thenThrow(new IllegalStateException("Zoho client id is not configured"));

        mockMvc
            .perform(get("/api/v1/admin/zoho/connect-url"))
            .andExpect(status().isBadRequest())
            .andExpect(content().string("Zoho client id is not configured"));
    }

    @Test
    void completeOAuth_missingCode_returnsBadRequest() throws Exception {
        mockMvc
            .perform(
                post("/api/v1/admin/zoho/oauth/complete")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}")
            )
            .andExpect(status().isBadRequest())
            .andExpect(content().string("Missing authorization code"));

        verify(oauthService, never()).completeOAuth(any(), any());
    }

    @Test
    void completeOAuth_success_returnsIntegrationStatus() throws Exception {
        when(oauthService.oauthSetup()).thenReturn(oauthSetup());
        when(oauthService.isServerConfigured()).thenReturn(true);
        when(oauthService.orgId()).thenReturn("org-1");
        when(oauthService.dataCenter()).thenReturn("in");
        when(settingRepository.findActiveOrLatest()).thenReturn(Optional.of(zohoSetting().connected(true)));

        mockMvc
            .perform(
                post("/api/v1/admin/zoho/oauth/complete")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"code\":\"auth-code\"}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.connected").value(true));

        verify(oauthService).completeOAuth("auth-code", "http://localhost:5173/admin/zoho");
    }

    @Test
    void syncProducts_delegatesToSyncService() throws Exception {
        when(syncService.syncAllProducts()).thenReturn(Map.of("successCount", 2));

        mockMvc
            .perform(post("/api/v1/admin/zoho/sync-products"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.successCount").value(2));
    }

    @Test
    void pullProducts_delegatesFullFlagToSyncService() throws Exception {
        when(syncService.pullProducts(true)).thenReturn(Map.of("successCount", 5));

        mockMvc
            .perform(post("/api/v1/admin/zoho/pull-products").param("full", "true"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.successCount").value(5));

        verify(syncService).pullProducts(true);
    }

    @Test
    void pullProductVariant_withoutIdentifiers_delegatesNullsToSyncService() throws Exception {
        Map<String, Object> failure = new LinkedHashMap<>();
        failure.put("failureCount", 1);
        failure.put("message", "Zoho item id or SKU is required before pulling from Zoho");
        when(syncService.pullProductVariant(eq(7L), isNull(), isNull())).thenReturn(failure);

        mockMvc
            .perform(
                post("/api/v1/admin/zoho/pull-products/7/variants")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.failureCount").value(1));

        verify(syncService).pullProductVariant(7L, null, null);
    }

    @Test
    void pullProductVariant_withSku_delegatesToSyncService() throws Exception {
        when(syncService.pullProductVariant(7L, "SKU-RED", null)).thenReturn(Map.of("successCount", 1));

        mockMvc
            .perform(
                post("/api/v1/admin/zoho/pull-products/7/variants")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"sku\":\"SKU-RED\"}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.successCount").value(1));
    }

    @Test
    void pullItemGroup_delegatesParentIdToSyncService() throws Exception {
        when(syncService.pullProductGroup("3624052000000394593")).thenReturn(Map.of("success", 2));

        mockMvc
            .perform(post("/api/v1/admin/zoho/pull-item-groups/3624052000000394593"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(2));

        verify(syncService).pullProductGroup("3624052000000394593");
    }

    @Test
    void pullItemGroup_rejectsNonNumericParentId() throws Exception {
        mockMvc.perform(post("/api/v1/admin/zoho/pull-item-groups/not-a-group")).andExpect(status().isBadRequest());
        verify(syncService, never()).pullProductGroup(any());
    }

    @Test
    void syncOrderByNumber_whenMissing_returnsNotFoundPayload() throws Exception {
        when(customerOrderRepository.findOneByOrderNumber("MISSING")).thenReturn(Optional.empty());

        mockMvc
            .perform(post("/api/v1/admin/zoho/sync-orders/by-number/MISSING"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.orderNumber").value("MISSING"))
            .andExpect(jsonPath("$.error").value("not found"));

        verify(syncService, never()).syncOrder(any());
    }

    @Test
    void syncOrderByNumber_whenFound_syncsAndReturnsOrderFields() throws Exception {
        CustomerOrder order = new CustomerOrder()
            .id(15L)
            .orderNumber("MNM-15")
            .zohoSalesOrderId("so-15")
            .zohoInvoiceId("inv-15")
            .zohoSyncStatus(ZohoSyncStatus.SYNCED)
            .zohoSyncError(null)
            .zohoLastSyncedAt(Instant.parse("2026-05-01T12:00:00Z"));
        when(customerOrderRepository.findOneByOrderNumber("MNM-15")).thenReturn(Optional.of(order), Optional.of(order));

        mockMvc
            .perform(post("/api/v1/admin/zoho/sync-orders/by-number/MNM-15"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(15))
            .andExpect(jsonPath("$.zohoSalesOrderId").value("so-15"))
            .andExpect(jsonPath("$.zohoSyncStatus").value("SYNCED"));

        verify(syncService).syncOrder(15L);
    }

    @Test
    void oauthSetup_returnsConsoleAndAuthTemplate() throws Exception {
        when(oauthService.oauthSetup()).thenReturn(oauthSetup());
        when(oauthService.dataCenter()).thenReturn("in");
        when(oauthService.accountsHost("in")).thenReturn("https://accounts.zoho.in");

        mockMvc
            .perform(get("/api/v1/admin/zoho/oauth-setup"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.clientType").value("Server-based"))
            .andExpect(jsonPath("$.apiConsoleUrl").value("https://api-console.zoho.in/"))
            .andExpect(jsonPath("$.primaryRedirectUri").value("http://localhost:5173/admin/zoho"))
            .andExpect(jsonPath("$.authorizationUrlTemplate").value(org.hamcrest.Matchers.containsString("accounts.zoho.in")));
    }

    @Test
    void testConnection_forbidden_returns403Message() throws Exception {
        when(settingRepository.findActiveOrLatest()).thenReturn(Optional.empty());
        when(syncService.testConnection()).thenThrow(
            new RestClientResponseException("403", HttpStatus.FORBIDDEN.value(), "Forbidden", null, null, null)
        );

        mockMvc
            .perform(get("/api/v1/admin/zoho/test"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.connected").value(false))
            .andExpect(jsonPath("$.message").value("Zoho rejected the credentials (HTTP 403). Reconnect or check the org ID."));
    }

    @Test
    void testConnection_otherHttpError_returnsGenericMessage() throws Exception {
        when(settingRepository.findActiveOrLatest()).thenReturn(Optional.empty());
        when(syncService.testConnection()).thenThrow(
            new RestClientResponseException("500", HttpStatus.INTERNAL_SERVER_ERROR.value(), "Error", null, null, null)
        );

        mockMvc
            .perform(get("/api/v1/admin/zoho/test"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("Zoho returned HTTP 500. Check settings and try again."));
    }

    @Test
    void syncProduct_whenFound_returnsSyncFields() throws Exception {
        Product product = new Product()
            .id(9L)
            .zohoItemId("zoho-9")
            .zohoSyncStatus(ZohoSyncStatus.SYNCED)
            .variantsJson("[]")
            .itemStructure("SIMPLE");
        when(productRepository.findById(9L)).thenReturn(Optional.of(product));

        mockMvc
            .perform(post("/api/v1/admin/zoho/sync-products/9"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(9))
            .andExpect(jsonPath("$.zohoItemId").value("zoho-9"))
            .andExpect(jsonPath("$.zohoSyncStatus").value("SYNCED"));

        verify(syncService).syncProduct(9L);
    }

    @Test
    void syncProduct_whenMissing_returnsNotFoundPayload() throws Exception {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        mockMvc
            .perform(post("/api/v1/admin/zoho/sync-products/99"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.error").value("not found"));
    }

    @Test
    void syncStock_delegatesToSyncService() throws Exception {
        when(syncService.syncProductStockOnly(3L)).thenReturn(Map.of("ok", true));

        mockMvc
            .perform(post("/api/v1/admin/zoho/sync-stock/3"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ok").value(true));
    }

    @Test
    void pullProducts_rateLimited_returnsBadGateway() throws Exception {
        when(syncService.pullProducts(false)).thenThrow(
            new RestClientResponseException("429", HttpStatus.TOO_MANY_REQUESTS.value(), "Too Many", null, null, null)
        );

        mockMvc
            .perform(post("/api/v1/admin/zoho/pull-products"))
            .andExpect(status().isBadGateway())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("rate limit")));
    }

    @Test
    void pullProducts_unreachable_returnsBadGateway() throws Exception {
        when(syncService.pullProducts(false)).thenThrow(new ResourceAccessException("timeout"));

        mockMvc
            .perform(post("/api/v1/admin/zoho/pull-products"))
            .andExpect(status().isBadGateway())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Could not reach Zoho")));
    }

    @Test
    void pullProduct_httpError_returnsBadGateway() throws Exception {
        when(syncService.pullProduct(5L)).thenThrow(
            new RestClientResponseException("502", HttpStatus.BAD_GATEWAY.value(), "Bad Gateway", null, null, null)
        );

        mockMvc
            .perform(post("/api/v1/admin/zoho/pull-products/5"))
            .andExpect(status().isBadGateway())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("HTTP 502")));
    }

    @Test
    void pullExternalProduct_delegatesSkuAndItemId() throws Exception {
        when(syncService.pullExternalProduct("SKU-X", "zoho-x")).thenReturn(Map.of("successCount", 1));

        mockMvc
            .perform(
                post("/api/v1/admin/zoho/pull-product")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"sku\":\"SKU-X\",\"zohoItemId\":\"zoho-x\"}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.successCount").value(1));
    }

    @Test
    void pullInventory_delegatesToSyncService() throws Exception {
        when(syncService.pullInventory()).thenReturn(Map.of("updated", 4));

        mockMvc
            .perform(post("/api/v1/admin/zoho/pull-inventory"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.updated").value(4));
    }

    @Test
    void retryOrders_delegatesToSyncService() throws Exception {
        when(syncService.retryFailedOrders()).thenReturn(Map.of("retried", 2));

        mockMvc
            .perform(post("/api/v1/admin/zoho/retry-orders"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.retried").value(2));
    }

    @Test
    void pullOrderTracking_delegatesToSyncService() throws Exception {
        when(syncService.pullAllTracking()).thenReturn(Map.of("pulled", 1));

        mockMvc
            .perform(post("/api/v1/admin/zoho/pull-order-tracking"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pulled").value(1));
    }

    @Test
    void pullItemGroup_rateLimited_returnsBadGateway() throws Exception {
        when(syncService.pullProductGroup("123")).thenThrow(
            new RestClientResponseException("429", HttpStatus.TOO_MANY_REQUESTS.value(), "Too Many", null, null, null)
        );

        mockMvc
            .perform(post("/api/v1/admin/zoho/pull-item-groups/123"))
            .andExpect(status().isBadGateway())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("rate limit")));
    }

    @Test
    void completeOAuth_illegalState_returnsBadRequest() throws Exception {
        when(oauthService.oauthSetup()).thenReturn(oauthSetup());
        doThrow(new IllegalStateException("invalid state")).when(oauthService).completeOAuth(any(), any());

        mockMvc
            .perform(
                post("/api/v1/admin/zoho/oauth/complete")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"code\":\"bad\"}")
            )
            .andExpect(status().isBadRequest())
            .andExpect(content().string("invalid state"));
    }

    @Test
    void connectUrl_returnsAuthorizationUrl() throws Exception {
        when(oauthService.oauthSetup()).thenReturn(oauthSetup());
        when(oauthService.buildConnectUrl("http://localhost:5173/admin/zoho")).thenReturn("https://accounts.zoho.in/oauth/v2/auth?demo=1");

        mockMvc
            .perform(get("/api/v1/admin/zoho/connect-url"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.url").value("https://accounts.zoho.in/oauth/v2/auth?demo=1"));
    }

    @Test
    void connectUrl_illegalState_returnsBadRequest() throws Exception {
        when(oauthService.oauthSetup()).thenReturn(oauthSetup());
        when(oauthService.buildConnectUrl(any())).thenThrow(new IllegalStateException("OAuth client id missing"));

        mockMvc
            .perform(get("/api/v1/admin/zoho/connect-url"))
            .andExpect(status().isBadRequest())
            .andExpect(content().string("OAuth client id missing"));
    }

    @Test
    void setPullProductsAutoCreateCategory_persistsFlagAndReturnsStatus() throws Exception {
        ZohoSetting setting = zohoSetting();
        when(settingRepository.findActiveOrLatest()).thenReturn(Optional.of(setting));
        when(settingRepository.save(any(ZohoSetting.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(oauthService.isServerConfigured()).thenReturn(false);
        when(oauthService.orgId()).thenReturn(null);
        when(oauthService.dataCenter()).thenReturn("in");

        mockMvc
            .perform(
                put("/api/v1/admin/zoho/pull-products-auto-create-category")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"enabled\":true}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pullProductsAutoCreateCategory").value(true));
    }

    @Test
    void setPullProductsAutoCreateCategory_newSettingDefaultsConnectedFalse() throws Exception {
        when(settingRepository.findActiveOrLatest()).thenReturn(Optional.empty());
        when(settingRepository.save(any(ZohoSetting.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(oauthService.isServerConfigured()).thenReturn(true);
        when(oauthService.orgId()).thenReturn("org-1");
        when(oauthService.dataCenter()).thenReturn("in");

        mockMvc
            .perform(
                put("/api/v1/admin/zoho/pull-products-auto-create-category")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"enabled\":false}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.connected").value(false));
    }

    @Test
    void pullProduct_rateLimited_returnsBadGateway() throws Exception {
        when(syncService.pullProduct(6L)).thenThrow(
            new RestClientResponseException("429", HttpStatus.TOO_MANY_REQUESTS.value(), "Too Many", null, null, null)
        );

        mockMvc
            .perform(post("/api/v1/admin/zoho/pull-products/6"))
            .andExpect(status().isBadGateway())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("rate limit")));
    }

    @Test
    void pullProduct_unreachable_returnsBadGateway() throws Exception {
        when(syncService.pullProduct(7L)).thenThrow(new ResourceAccessException("timeout"));

        mockMvc
            .perform(post("/api/v1/admin/zoho/pull-products/7"))
            .andExpect(status().isBadGateway())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Could not reach Zoho")));
    }

    @Test
    void pullProductVariant_delegatesSkuAndItemId() throws Exception {
        when(syncService.pullProductVariant(8L, "SKU-V", "zoho-v")).thenReturn(Map.of("success", 1));

        mockMvc
            .perform(
                post("/api/v1/admin/zoho/pull-products/8/variants")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"sku\":\"SKU-V\",\"zohoItemId\":\"zoho-v\"}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(1));
    }

    @Test
    void pullProductVariant_rateLimited_returnsBadGateway() throws Exception {
        when(syncService.pullProductVariant(9L, "SKU-X", null)).thenThrow(
            new RestClientResponseException("429", HttpStatus.TOO_MANY_REQUESTS.value(), "Too Many", null, null, null)
        );

        mockMvc
            .perform(
                post("/api/v1/admin/zoho/pull-products/9/variants")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"sku\":\"SKU-X\"}")
            )
            .andExpect(status().isBadGateway())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("rate limit")));
    }

    @Test
    void pullExternalProduct_rateLimited_returnsBadGateway() throws Exception {
        when(syncService.pullExternalProduct("SKU-Y", "zoho-y")).thenThrow(
            new RestClientResponseException("429", HttpStatus.TOO_MANY_REQUESTS.value(), "Too Many", null, null, null)
        );

        mockMvc
            .perform(
                post("/api/v1/admin/zoho/pull-product")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"sku\":\"SKU-Y\",\"zohoItemId\":\"zoho-y\"}")
            )
            .andExpect(status().isBadGateway())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("rate limit")));
    }

    @Test
    void pullExternalProduct_unreachable_returnsBadGateway() throws Exception {
        when(syncService.pullExternalProduct(null, "zoho-z")).thenThrow(new ResourceAccessException("timeout"));

        mockMvc
            .perform(
                post("/api/v1/admin/zoho/pull-product")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"zohoItemId\":\"zoho-z\"}")
            )
            .andExpect(status().isBadGateway())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Could not reach Zoho")));
    }

    @Test
    void pullItemGroup_unreachable_returnsBadGateway() throws Exception {
        when(syncService.pullProductGroup("456")).thenThrow(new ResourceAccessException("timeout"));

        mockMvc
            .perform(post("/api/v1/admin/zoho/pull-item-groups/456"))
            .andExpect(status().isBadGateway())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Could not reach Zoho")));
    }

    private static Map<String, Object> oauthSetup() {
        Map<String, Object> setup = new LinkedHashMap<>();
        setup.put("homepageUrl", "http://localhost:5173");
        setup.put("redirectUris", List.of("http://localhost:5173", "http://localhost:5173/admin/zoho"));
        setup.put("javascriptDomains", List.of("http://localhost:5173"));
        setup.put("scopes", "ZohoBooks.fullaccess.all");
        return setup;
    }

    private static ZohoSetting zohoSetting() {
        ZohoSetting setting = new ZohoSetting();
        setting.setConnected(false);
        setting.setPullProductsAutoCreateCategory(false);
        return setting;
    }
}
