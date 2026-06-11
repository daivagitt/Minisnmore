package com.miniandmore.integration.web;

import com.miniandmore.integration.domain.CustomerOrder;
import com.miniandmore.integration.repository.CustomerOrderRepository;
import com.miniandmore.integration.service.ZohoBooksException;
import com.miniandmore.integration.service.ZohoBooksSyncService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * Service-to-service triggers from catalog core (shared Postgres, no user JWT).
 */
@RestController
@Validated
@RequestMapping("/internal/v1/zoho/triggers")
public class ZohoTriggerResource {

    private final ZohoBooksSyncService zohoBooksSyncService;
    private final CustomerOrderRepository customerOrderRepository;

    @Value("${application.internal-api-key:}")
    private String internalApiKey;

    public ZohoTriggerResource(ZohoBooksSyncService zohoBooksSyncService, CustomerOrderRepository customerOrderRepository) {
        this.zohoBooksSyncService = zohoBooksSyncService;
        this.customerOrderRepository = customerOrderRepository;
    }

    @PostMapping("/sync-orders/{id}")
    public ApiResponse<Map<String, Object>> syncOrder(@RequestHeader(value = "X-Internal-Api-Key", required = false) String key, @PathVariable Long id) {
        verifyKey(key);
        try {
            zohoBooksSyncService.syncOrder(id);
            return ApiResponse.ok(Map.of("ok", true));
        } catch (ZohoBooksException e) {
            return ApiResponse.ok(Map.of("ok", false, "error", e.getMessage()));
        }
    }

    @PostMapping("/sync-products/{id}/async")
    public ApiResponse<Map<String, Boolean>> syncProductAsync(@RequestHeader(value = "X-Internal-Api-Key", required = false) String key, @PathVariable Long id) {
        verifyKey(key);
        zohoBooksSyncService.syncProductAsync(id);
        return ApiResponse.ok(Map.of("ok", true));
    }

    @PostMapping("/sync-products/{id}")
    public ApiResponse<Map<String, Object>> syncProduct(@RequestHeader(value = "X-Internal-Api-Key", required = false) String key, @PathVariable Long id) {
        verifyKey(key);
        try {
            zohoBooksSyncService.syncProduct(id);
            return ApiResponse.ok(Map.of("ok", true));
        } catch (ZohoBooksException e) {
            return ApiResponse.ok(Map.of("ok", false, "error", e.getMessage()));
        }
    }

    @PostMapping("/deactivate-products/{id}")
    public ApiResponse<Map<String, Boolean>> deactivateProduct(
        @RequestHeader(value = "X-Internal-Api-Key", required = false) String key,
        @PathVariable Long id
    ) {
        verifyKey(key);
        zohoBooksSyncService.deactivateZohoItemForProduct(id);
        return ApiResponse.ok(Map.of("ok", true));
    }

    @PostMapping("/pull-order-tracking/{id}")
    public ApiResponse<Map<String, Boolean>> pullOrderTracking(
        @RequestHeader(value = "X-Internal-Api-Key", required = false) String key,
        @PathVariable Long id
    ) {
        verifyKey(key);
        zohoBooksSyncService.pullOrderTracking(id);
        return ApiResponse.ok(Map.of("ok", true));
    }

    @PostMapping("/credit-notes")
    public Map<String, String> creditNote(
        @RequestHeader(value = "X-Internal-Api-Key", required = false) String key,
        @RequestBody @Valid CreditNoteRequest request
    ) {
        verifyKey(key);
        CustomerOrder order = customerOrderRepository
            .findById(request.orderId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        Optional<String> creditNoteId = zohoBooksSyncService.createCreditNoteForReturnedOrder(order, request.returnNumber());
        return Map.of("zohoCreditNoteId", creditNoteId.orElse(""));
    }

    private void verifyKey(String key) {
        if (internalApiKey == null || internalApiKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Internal API key is not configured");
        }
        if (
            key == null ||
            !MessageDigest.isEqual(
                internalApiKey.getBytes(StandardCharsets.UTF_8),
                key.getBytes(StandardCharsets.UTF_8)
            )
        ) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid internal API key");
        }
    }

    public record CreditNoteRequest(@NotNull Long orderId, String returnNumber) {}
}
