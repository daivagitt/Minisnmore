package com.miniandmore.app.web.rest.admin;

import com.miniandmore.app.domain.LedgerlySetting;
import com.miniandmore.app.repository.CustomerOrderRepository;
import com.miniandmore.app.repository.LedgerlySettingRepository;
import com.miniandmore.app.repository.ProductRepository;
import com.miniandmore.app.service.LedgerlySyncService;
import com.miniandmore.app.client.LedgerlyIntegrationClient;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
        return ledgerlyClient.testConnection();
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
                return Map.<String, Object>of("ok", true, "orderNumber", orderNumber);
            })
            .orElse(Map.of("ok", false, "orderNumber", orderNumber, "error", "not found"));
    }

    @PostMapping("/pull-order-tracking")
    public Map<String, Object> pullOrderTracking() {
        return syncService.pullAllTracking();
    }
}
