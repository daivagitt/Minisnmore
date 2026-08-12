package com.miniandmore.app.client;

import com.miniandmore.app.service.ZohoSyncService;
import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Facade kept for call-site compatibility with {@code OrderReturnService} and
 * {@code AccountOrderDataAccess}. Delegates in-process to {@link ZohoSyncService} — Zoho sync now
 * runs inside catalog core, there is no separate integration microservice to call over HTTP.
 */
@Component
public class ZohoBooksIntegrationClient {

    private final ZohoSyncService syncService;

    public ZohoBooksIntegrationClient(ZohoSyncService syncService) {
        this.syncService = syncService;
    }

    public void syncOrder(Long orderId) {
        syncService.syncOrder(orderId);
    }

    public void syncProductAsync(Long productId) {
        syncService.syncProductAsync(productId);
    }

    public void syncProduct(Long productId) {
        syncService.syncProduct(productId);
    }

    public void deactivateProductInZoho(Long productId) {
        syncService.deactivateProduct(productId);
    }

    public void pullOrderTracking(Long orderId) {
        syncService.pullOrderTracking(orderId);
    }

    public Optional<String> createCreditNote(Long orderId, String returnNumber) {
        return syncService.createCreditNote(orderId, returnNumber);
    }

    /** Overload kept for parity with {@code LedgerlyIntegrationClient}; amount is currently unused by Zoho credit notes. */
    public Optional<String> createCreditNote(Long orderId, String returnNumber, BigDecimal amount) {
        return syncService.createCreditNote(orderId, returnNumber);
    }
}
