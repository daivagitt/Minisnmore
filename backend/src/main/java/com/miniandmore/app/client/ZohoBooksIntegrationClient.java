package com.miniandmore.app.client;

import com.miniandmore.app.service.ZohoExchangeInvoiceService;
import com.miniandmore.app.service.ZohoSyncService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
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
    private final ZohoExchangeInvoiceService exchangeInvoiceService;

    public ZohoBooksIntegrationClient(ZohoSyncService syncService, ZohoExchangeInvoiceService exchangeInvoiceService) {
        this.syncService = syncService;
        this.exchangeInvoiceService = exchangeInvoiceService;
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

    public Map<String, Object> syncProductStockOnly(Long productId) {
        return syncService.syncProductStockOnly(productId);
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

    public Optional<String> createCreditNote(Long orderId, String returnNumber, Map<Long, Integer> returnedQtyByOrderItemId) {
        return syncService.createCreditNote(orderId, returnNumber, returnedQtyByOrderItemId);
    }

    public Optional<String> createCreditNote(
        Long orderId,
        String returnNumber,
        Map<Long, Integer> returnedQtyByOrderItemId,
        boolean linkToSourceInvoice
    ) {
        return syncService.createCreditNote(orderId, returnNumber, returnedQtyByOrderItemId, linkToSourceInvoice);
    }

    /** Overload kept for parity with {@code LedgerlyIntegrationClient}; amount is currently unused by Zoho credit notes. */
    public Optional<String> createCreditNote(Long orderId, String returnNumber, BigDecimal amount) {
        return syncService.createCreditNote(orderId, returnNumber);
    }

    public Optional<String> createExchangeReplacementInvoice(
        Long orderId,
        String returnNumber,
        Long orderItemId,
        String replacementSku,
        String replacementSize,
        String replacementColor,
        BigDecimal replacementAmount,
        int quantity,
        List<String> creditNoteIds
    ) {
        return exchangeInvoiceService.createReplacementInvoice(
            orderId,
            returnNumber,
            orderItemId,
            replacementSku,
            replacementSize,
            replacementColor,
            replacementAmount,
            quantity,
            creditNoteIds
        );
    }
}
