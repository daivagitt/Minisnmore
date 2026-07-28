package com.miniandmore.app.web.rest.admin;

import com.miniandmore.app.domain.LedgerlySetting;
import com.miniandmore.app.domain.enumeration.LedgerlySyncStatus;
import com.miniandmore.app.repository.CustomerOrderRepository;
import com.miniandmore.app.repository.LedgerlySettingRepository;
import com.miniandmore.app.repository.ProductRepository;
import com.miniandmore.app.service.LedgerlySyncService;
import com.miniandmore.app.client.LedgerlyIntegrationClient;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/admin/ledgerly")
public class LedgerlyAdminResource {

    private final LedgerlySettingRepository settingRepository;
    private final LedgerlySyncService syncService;
    private final LedgerlyIntegrationClient ledgerlyClient;
    private final CustomerOrderRepository customerOrderRepository;
    private final ProductRepository productRepository;

    public LedgerlyAdminResource(
        LedgerlySettingRepository settingRepository,
        LedgerlySyncService syncService,
        LedgerlyIntegrationClient ledgerlyClient,
        CustomerOrderRepository customerOrderRepository,
        ProductRepository productRepository
    ) {
        this.settingRepository = settingRepository;
        this.syncService = syncService;
        this.ledgerlyClient = ledgerlyClient;
        this.customerOrderRepository = customerOrderRepository;
        this.productRepository = productRepository;
    }

    @GetMapping("/integration-status")
    public Map<String, Object> integrationStatus() {
        Optional<LedgerlySetting> setting = settingRepository.findFirstByOrderByIdAsc();
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("serverConfigured", setting.map(s -> s.getApiToken() != null && !s.getApiToken().isBlank()).orElse(false));
        status.put("connected", setting.map(LedgerlySetting::getConnected).orElse(false));
        status.put("pullProductsAutoCreateCategory", setting.map(LedgerlySetting::getPullProductsAutoCreateCategory).orElse(false));
        return status;
    }

    @GetMapping("/test")
    public Map<String, Object> testConnection() {
        try {
            Map<String, Object> remote = ledgerlyClient.testConnection();
            Map<String, Object> result = new LinkedHashMap<>();
            if (remote != null) {
                result.putAll(remote);
            }
            boolean connected =
                Boolean.TRUE.equals(result.get("connected")) ||
                Boolean.TRUE.equals(result.get("ok")) ||
                "ok".equalsIgnoreCase(String.valueOf(result.get("status")));
            if (!result.containsKey("connected")) {
                result.put("connected", connected || !result.isEmpty());
            }
            if (!result.containsKey("ok")) {
                result.put("ok", Boolean.TRUE.equals(result.get("connected")));
            }
            if (Boolean.TRUE.equals(result.get("connected"))) {
                markConnected(true);
            }
            return result;
        } catch (RestClientResponseException ex) {
            markConnected(false);
            int status = ex.getStatusCode().value();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("ok", false);
            result.put("connected", false);
            result.put("status", status);
            if (status == 401 || status == 403) {
                result.put(
                    "message",
                    "Ledgerly rejected the API token (HTTP " +
                    status +
                    "). Generate a new developer token in Ledgerly and paste it under API & Webhooks, then save and test again."
                );
                result.put("error", "unauthorized");
            } else {
                result.put(
                    "message",
                    "Ledgerly returned HTTP " + status + ". Check the API Base URL and token, then try again."
                );
                result.put("error", "http_" + status);
            }
            return result;
        } catch (ResourceAccessException ex) {
            markConnected(false);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("ok", false);
            result.put("connected", false);
            result.put("error", "unreachable");
            result.put(
                "message",
                "Could not reach Ledgerly at the configured API Base URL. Verify the URL (host must resolve) and network access, then try again."
            );
            return result;
        } catch (Exception ex) {
            markConnected(false);
            throw new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "Ledgerly connection test failed: " + (ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName())
            );
        }
    }

    private void markConnected(boolean connected) {
        settingRepository
            .findFirstByOrderByIdAsc()
            .ifPresent(setting -> {
                if (!java.util.Objects.equals(setting.getConnected(), connected)) {
                    setting.setConnected(connected);
                    settingRepository.save(setting);
                }
            });
    }

    @GetMapping("/settings")
    public ResponseEntity<Map<String, Object>> getSettings() {
        return settingRepository
            .findFirstByOrderByIdAsc()
            .map(s -> {
                Map<String, Object> dto = new LinkedHashMap<>();
                dto.put("apiBaseUrl", s.getApiBaseUrl());
                dto.put("orgId", s.getOrgId());
                dto.put("connected", s.getConnected());
                dto.put("pullProductsAutoCreateCategory", s.getPullProductsAutoCreateCategory());
                dto.put("hasToken", s.getApiToken() != null && !s.getApiToken().isBlank());
                dto.put("hasWebhookSecret", s.getWebhookSecret() != null && !s.getWebhookSecret().isBlank());
                return ResponseEntity.ok(dto);
            })
            .orElse(ResponseEntity.ok(Map.of()));
    }

    @PutMapping("/settings")
    public Map<String, Object> saveSettings(@RequestBody Map<String, Object> body) {
        LedgerlySetting setting = settingRepository.findFirstByOrderByIdAsc().orElseGet(LedgerlySetting::new);
        if (body.get("apiBaseUrl") != null) {
            setting.setApiBaseUrl(body.get("apiBaseUrl").toString());
        }
        if (body.get("api_base_url") != null) {
            setting.setApiBaseUrl(body.get("api_base_url").toString());
        }
        Object tokenVal = body.get("apiToken") != null ? body.get("apiToken") : body.get("api_token");
        if (tokenVal != null && !tokenVal.toString().isBlank()) {
            setting.setApiToken(tokenVal.toString());
        }
        if (body.get("orgId") != null) {
            setting.setOrgId(body.get("orgId").toString());
        }
        if (body.get("org_id") != null) {
            setting.setOrgId(body.get("org_id").toString());
        }
        if (body.get("connected") != null) {
            setting.setConnected(Boolean.parseBoolean(body.get("connected").toString()));
        }
        if (body.get("pullProductsAutoCreateCategory") != null) {
            setting.setPullProductsAutoCreateCategory(Boolean.parseBoolean(body.get("pullProductsAutoCreateCategory").toString()));
        }
        Object webhookSecretVal = body.get("webhookSecret") != null ? body.get("webhookSecret") : body.get("webhook_secret");
        if (webhookSecretVal != null && !webhookSecretVal.toString().isBlank()) {
            setting.setWebhookSecret(webhookSecretVal.toString());
        }
        settingRepository.save(setting);
        return Map.of("ok", true);
    }

    @PutMapping("/pull-products-auto-create-category")
    public Map<String, Object> setPullProductsAutoCreateCategory(@RequestBody Map<String, Boolean> body) {
        LedgerlySetting setting = settingRepository.findFirstByOrderByIdAsc().orElseGet(LedgerlySetting::new);
        setting.setPullProductsAutoCreateCategory(Boolean.TRUE.equals(body.get("enabled")));
        settingRepository.save(setting);
        return integrationStatus();
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
                result.put("ledgerlyItemId", product.getLedgerlyItemId());
                result.put("ledgerlySyncStatus", product.getLedgerlySyncStatus());
                result.put("ledgerlyLastSyncedAt", product.getLedgerlyLastSyncedAt());
                result.put("ledgerlySyncError", product.getLedgerlySyncError());
                return result;
            })
            .orElse(Map.of("ok", false, "productId", id, "error", "not found"));
    }

    @PostMapping("/pull-products")
    public Map<String, Object> pullProducts() {
        return syncService.pullProducts();
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
                        result.put("orderNumber", orderNumber);
                        LedgerlySyncStatus status = refreshed.getLedgerlySyncStatus();
                        result.put("ledgerlySyncStatus", status != null ? status.name() : null);
                        result.put("ledgerlySyncError", StringUtils.defaultString(refreshed.getLedgerlySyncError()));
                        result.put("ok", status == LedgerlySyncStatus.SYNCED);
                        return result;
                    })
                    .orElse(Map.of("ok", false, "orderNumber", orderNumber, "error", "not found after sync"));
            })
            .orElse(Map.of("ok", false, "orderNumber", orderNumber, "error", "not found"));
    }

    @PostMapping("/pull-order-tracking")
    public Map<String, Object> pullOrderTracking() {
        return syncService.pullAllTracking();
    }
}
