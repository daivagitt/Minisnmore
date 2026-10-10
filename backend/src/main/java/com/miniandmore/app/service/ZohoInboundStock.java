package com.miniandmore.app.service;

/**
 * Shared rules for applying Zoho {@code stock_on_hand} to the store catalog. Pull paths, webhooks,
 * and variant matrix updates must not silently clamp negative quantities to zero.
 */
public final class ZohoInboundStock {

    private ZohoInboundStock() {}

    public static boolean isNegative(int stock) {
        return stock < 0;
    }

    public static String negativeStockMessage(int stock) {
        return "Negative Zoho stock (" + stock + "); store stock was not updated";
    }
}
