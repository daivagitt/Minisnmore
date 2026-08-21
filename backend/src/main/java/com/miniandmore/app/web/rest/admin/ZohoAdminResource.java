package com.miniandmore.app.web.rest.admin;

import com.miniandmore.app.domain.ZohoSetting;
import com.miniandmore.app.domain.enumeration.ZohoSyncStatus;
import com.miniandmore.app.repository.CustomerOrderRepository;
import com.miniandmore.app.repository.ProductRepository;
import com.miniandmore.app.repository.ZohoSettingRepository;
import com.miniandmore.app.service.ZohoOAuthService;
import com.miniandmore.app.service.ZohoSyncService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/admin/zoho")
public class ZohoAdminResource {

    private final ZohoSettingRepository settingRepository;
    private final ZohoOAuthService oauthService;
    private final ZohoSyncService syncService;
    private final CustomerOrderRepository customerOrderRepository;
    private final ProductRepository productRepository;

    public ZohoAdminResource(
        ZohoSettingRepository settingRepository,
        ZohoOAuthService oauthService,
        ZohoSyncService syncService,
        CustomerOrderRepository customerOrderRepository,
        ProductRepository productRepository
    ) {
        this.settingRepository = settingRepository;
        this.oauthService = oauthService;
        this.syncService = syncService;
        this.customerOrderRepository = customerOrderRepository;
        this.productRepository = productRepository;
    }

    @GetMapping("/oauth-setup")
    public Map<String, Object> oauthSetup() {
        Map<String, Object> setup = oauthService.oauthSetup();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("clientType", "Server-based");
        result.put("apiConsoleUrl", "https://api-console.zoho." + normalizedDataCenter() + "/");
        result.put("homepageUrl", setup.get("homepageUrl"));
        result.put("redirectUris", setup.get("redirectUris"));
        result.put("javascriptDomains", setup.get("javascriptDomains"));
        result.put("scopes", setup.get("scopes"));
        result.put("primaryRedirectUri", adminRedirectUri());
        result.put(
            "authorizationUrlTemplate",
            oauthService.accountsHost(normalizedDataCenter()) + "/oauth/v2/auth?response_type=code&client_id={client_id}&scope={scope}&redirect_uri={redirect_uri}&access_type=offline"
        );
        result.put("dataCenter", normalizedDataCenter());
        return result;
    }

    @GetMapping("/integration-status")
    public Map<String, Object> integrationStatus() {
        var setting = settingRepository.findActiveOrLatest();
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("serverConfigured", oauthService.isServerConfigured());
        status.put("connected", setting.map(ZohoSetting::getConnected).orElse(false));
        status.put("dataCenter", normalizedDataCenter());
        status.put("organizationId", oauthService.orgId());
        status.put("pullProductsAutoCreateCategory", setting.map(ZohoSetting::getPullProductsAutoCreateCategory).orElse(false));
        return status;
    }

    @PutMapping("/pull-products-auto-create-category")
    public Map<String, Object> setPullProductsAutoCreateCategory(@RequestBody Map<String, Boolean> body) {
        ZohoSetting setting = settingRepository.findActiveOrLatest().orElseGet(ZohoSetting::new);
        if (setting.getConnected() == null) {
            setting.setConnected(false);
        }
        setting.setPullProductsAutoCreateCategory(Boolean.TRUE.equals(body.get("enabled")));
        settingRepository.save(setting);
        return integrationStatus();
    }

    @GetMapping("/connect-url")
    public Map<String, Object> connectUrl() {
        try {
            return Map.of("url", oauthService.buildConnectUrl(adminRedirectUri()));
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @PostMapping("/oauth/complete")
    public Map<String, Object> completeOAuth(@RequestBody Map<String, String> body) {
        String code = body.get("code");
        if (StringUtils.isBlank(code)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing authorization code");
        }
        try {
            oauthService.completeOAuth(code, adminRedirectUri());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
        return integrationStatus();
    }

    @GetMapping("/test")
    public Map<String, Object> testConnection() {
        try {
            Map<String, Object> remote = syncService.testConnection();
            markConnected(true);
            String orgName = null;
            Object orgs = remote != null ? remote.get("organizations") : null;
            if (orgs instanceof java.util.List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?> org) {
                Object name = org.get("name");
                orgName = name != null ? name.toString() : null;
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("connected", true);
            result.put("message", "Connected to Zoho Books");
            result.put("organizationName", orgName);
            return result;
        } catch (RestClientResponseException ex) {
            markConnected(false);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("connected", false);
            result.put("organizationName", null);
            int status = ex.getStatusCode().value();
            if (status == 401 || status == 403) {
                result.put("message", "Zoho rejected the credentials (HTTP " + status + "). Reconnect or check the org ID.");
            } else {
                result.put("message", "Zoho returned HTTP " + status + ". Check settings and try again.");
            }
            return result;
        } catch (ResourceAccessException ex) {
            markConnected(false);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("connected", false);
            result.put("organizationName", null);
            result.put("message", "Could not reach Zoho. Check network access.");
            return result;
        } catch (IllegalStateException ex) {
            markConnected(false);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("connected", false);
            result.put("organizationName", null);
            result.put("message", ex.getMessage());
            return result;
        } catch (Exception ex) {
            markConnected(false);
            throw new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "Zoho connection test failed: " + (ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName())
            );
        }
    }

    private void markConnected(boolean connected) {
        settingRepository
            .findActiveOrLatest()
            .ifPresent(setting -> {
                if (!java.util.Objects.equals(setting.getConnected(), connected)) {
                    setting.setConnected(connected);
                    settingRepository.save(setting);
                }
            });
    }

    @PostMapping("/sync-products")
    public Map<String, Object> syncProducts() {
        return syncService.syncAllProducts();
    }

    @PostMapping("/sync-products/{id}")
    public Map<String, Object> syncProduct(@PathVariable Long id) {
        syncService.syncProduct(id);
        return productRepository
            .findById(id)
            .map(product -> {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("id", id);
                result.put("zohoItemId", product.getZohoItemId());
                result.put("zohoSyncStatus", product.getZohoSyncStatus());
                result.put("zohoSyncError", product.getZohoSyncError());
                result.put("zohoLastSyncedAt", product.getZohoLastSyncedAt());
                result.put("variantsJson", product.getVariantsJson());
                result.put("itemStructure", product.getItemStructure());
                return result;
            })
            .orElse(Map.of("id", id, "error", "not found"));
    }

    @PostMapping("/pull-products")
    public Map<String, Object> pullProducts() {
        try {
            return syncService.pullProducts();
        } catch (RestClientResponseException ex) {
            int status = ex.getStatusCode().value();
            String message = status == 429
                ? "Zoho API rate limit reached. Products already processed were saved; retry once the limit resets."
                : "Zoho pull failed: HTTP " + status + ".";
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, message, ex);
        } catch (ResourceAccessException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not reach Zoho. Check network access.", ex);
        }
    }

    @PostMapping("/pull-inventory")
    public Map<String, Object> pullInventory() {
        return syncService.pullInventory();
    }

    @PostMapping("/retry-orders")
    public Map<String, Object> retryOrders() {
        return syncService.retryFailedOrders();
    }

    @PostMapping("/sync-orders/by-number/{orderNumber}")
    public Map<String, Object> syncOrderByNumber(@PathVariable String orderNumber) {
        return customerOrderRepository
            .findOneByOrderNumber(orderNumber)
            .map(order -> {
                syncService.syncOrder(order.getId());
                return customerOrderRepository
                    .findOneByOrderNumber(orderNumber)
                    .map(refreshed -> {
                        Map<String, Object> result = new LinkedHashMap<>();
                        result.put("id", refreshed.getId());
                        result.put("orderNumber", orderNumber);
                        result.put("zohoSalesOrderId", refreshed.getZohoSalesOrderId());
                        result.put("zohoInvoiceId", refreshed.getZohoInvoiceId());
                        ZohoSyncStatus status = refreshed.getZohoSyncStatus();
                        result.put("zohoSyncStatus", status != null ? status.name() : null);
                        result.put("zohoSyncError", StringUtils.defaultString(refreshed.getZohoSyncError()));
                        result.put("zohoLastSyncedAt", refreshed.getZohoLastSyncedAt());
                        return result;
                    })
                    .orElse(Map.of("orderNumber", orderNumber, "error", "not found after sync"));
            })
            .orElse(Map.of("orderNumber", orderNumber, "error", "not found"));
    }

    @PostMapping("/pull-order-tracking")
    public Map<String, Object> pullOrderTracking() {
        return syncService.pullAllTracking();
    }

    private String normalizedDataCenter() {
        return oauthService.dataCenter();
    }

    private String adminRedirectUri() {
        var setup = oauthService.oauthSetup();
        @SuppressWarnings("unchecked")
        var redirectUris = (java.util.List<String>) setup.get("redirectUris");
        if (redirectUris != null && !redirectUris.isEmpty()) {
            return redirectUris.get(redirectUris.size() - 1);
        }
        return setup.get("homepageUrl") + "/admin/zoho";
    }
}
