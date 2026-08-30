package com.miniandmore.app.service;

import com.miniandmore.app.client.ZohoBooksClient;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;

/** Absolute Zoho inventory adjustments for catalog stock changes. */
@Service
public class ZohoCatalogStockAdjuster {

    private static final Logger LOG = LoggerFactory.getLogger(ZohoCatalogStockAdjuster.class);
    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");
    private static final ConcurrentHashMap<String, Object> ITEM_LOCKS = new ConcurrentHashMap<>();

    private final ZohoBooksClient booksClient;
    private final ZohoBooksAccountResolver accountResolver;

    public ZohoCatalogStockAdjuster(ZohoBooksClient booksClient, ZohoBooksAccountResolver accountResolver) {
        this.booksClient = booksClient;
        this.accountResolver = accountResolver;
    }

    public boolean adjustItemQuantity(
        String zohoItemId,
        int desiredStock,
        BigDecimal unitPriceHint,
        String referencePrefix,
        String logLabel
    ) {
        if (!accountResolver.isStockAdjustmentEnabled() || StringUtils.isBlank(zohoItemId)) {
            return true;
        }
        Object lock = ITEM_LOCKS.computeIfAbsent(zohoItemId.trim(), ignored -> new Object());
        synchronized (lock) {
            return adjustItemQuantityUnderLock(zohoItemId, desiredStock, unitPriceHint, referencePrefix, logLabel);
        }
    }

    private boolean adjustItemQuantityUnderLock(
        String zohoItemId,
        int desiredStock,
        BigDecimal unitPriceHint,
        String referencePrefix,
        String logLabel
    ) {
        try {
            Map<String, Object> item = getZohoItemMap(zohoItemId);
            if (item.isEmpty()) {
                return false;
            }
            if (!isInventoryItem(item)) {
                item = enableInventoryTrackingOnItem(zohoItemId, desiredStock, unitPriceHint, item, logLabel);
                if (!isInventoryItem(item)) {
                    LOG.warn("Skipping Zoho stock sync for {}: item is not inventory-tracked", logLabel);
                    return false;
                }
            }
            int currentStock = toInteger(item.get("stock_on_hand"));
            int delta = desiredStock - currentStock;
            if (delta == 0) {
                return true;
            }
            BigDecimal unitPrice = unitPriceHint != null && unitPriceHint.compareTo(BigDecimal.ZERO) > 0
                ? unitPriceHint
                : toBigDecimal(item.get("rate"));
            createQuantityAdjustment(zohoItemId, delta, unitPrice, referencePrefix, logLabel);
            return true;
        } catch (RuntimeException e) {
            LOG.warn("Zoho stock sync failed for {}: {}", logLabel, e.getMessage());
            return false;
        }
    }

    private Map<String, Object> getZohoItemMap(String zohoItemId) {
        Map<String, Object> response = booksClient.getItem(zohoItemId);
        if (response == null || response.isEmpty()) {
            return Map.of();
        }
        Object nested = response.get("item");
        if (nested instanceof Map<?, ?> itemMap) {
            @SuppressWarnings("unchecked")
            Map<String, Object> typed = (Map<String, Object>) itemMap;
            return typed;
        }
        return response;
    }

    private Map<String, Object> enableInventoryTrackingOnItem(
        String zohoItemId,
        int desiredStock,
        BigDecimal unitPriceHint,
        Map<String, Object> currentItem,
        String logLabel
    ) {
        Optional<String> inventoryAccountId = accountResolver.resolveInventoryAccountId();
        Optional<String> locationId = accountResolver.resolveLocationId();
        if (inventoryAccountId.isEmpty()) {
            return currentItem;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", currentItem.getOrDefault("name", "Item"));
        if (currentItem.get("sku") != null) {
            payload.put("sku", currentItem.get("sku"));
        }
        BigDecimal rate = unitPriceHint != null && unitPriceHint.compareTo(BigDecimal.ZERO) > 0
            ? unitPriceHint
            : toBigDecimal(currentItem.get("rate"));
        payload.put("rate", rate);
        payload.put("product_type", "goods");
        String currentUnit = StringUtils.trimToNull(stringValue(currentItem.get("unit")));
        if (currentUnit != null) {
            payload.put("unit", currentUnit);
        }
        payload.put("item_type", "inventory");
        payload.put("inventory_account_id", inventoryAccountId.orElseThrow());
        if (locationId.isPresent()) {
            Map<String, Object> location = new LinkedHashMap<>();
            location.put("location_id", locationId.orElseThrow());
            location.put("initial_stock", String.valueOf(Math.max(0, desiredStock)));
            location.put("initial_stock_rate", formatRate(rate));
            payload.put("locations", List.of(location));
        }
        try {
            booksClient.updateItem(zohoItemId, payload);
            Map<String, Object> refreshed = getZohoItemMap(zohoItemId);
            return refreshed.isEmpty() ? currentItem : refreshed;
        } catch (RuntimeException e) {
            LOG.warn("Could not enable Zoho inventory tracking for {}: {}", logLabel, e.getMessage());
            return currentItem;
        }
    }

    private void createQuantityAdjustment(
        String zohoItemId,
        int delta,
        BigDecimal unitPrice,
        String referencePrefix,
        String logLabel
    ) {
        Optional<String> adjustmentAccountId = accountResolver.resolveAdjustmentAccountId();
        if (adjustmentAccountId.isEmpty()) {
            LOG.warn("Skipping Zoho stock sync for {}: no adjustment account. Set ZOHO_INVENTORY_ADJUSTMENT_ACCOUNT_ID.", logLabel);
            return;
        }
        try {
            booksClient.createInventoryAdjustment(
                buildQuantityAdjustmentPayload(zohoItemId, delta, unitPrice, adjustmentAccountId.orElseThrow(), referencePrefix)
            );
            LOG.debug("Adjusted Zoho stock for {} by {}", logLabel, delta);
        } catch (RestClientResponseException e) {
            if (ZohoBooksAccountResolver.isInvalidAdjustmentAccount(e)) {
                LOG.warn(
                    "Zoho adjustment account {} does not exist in this org; looking up Cost of Goods Sold",
                    adjustmentAccountId.orElse("")
                );
                accountResolver.markConfiguredAdjustmentAccountRejected();
                Optional<String> replacement = accountResolver.resolveAdjustmentAccountId();
                if (replacement.isPresent() && !replacement.orElseThrow().equals(adjustmentAccountId.orElse(null))) {
                    booksClient.createInventoryAdjustment(
                        buildQuantityAdjustmentPayload(zohoItemId, delta, unitPrice, replacement.orElseThrow(), referencePrefix)
                    );
                    return;
                }
            }
            if (ZohoBooksAccountResolver.isInvalidLocation(e)) {
                LOG.warn("Zoho location for stock adjustment is invalid; looking up org locations");
                accountResolver.markConfiguredLocationRejected();
                booksClient.createInventoryAdjustment(
                    buildQuantityAdjustmentPayload(
                        zohoItemId,
                        delta,
                        unitPrice,
                        accountResolver.resolveAdjustmentAccountId().orElse(adjustmentAccountId.orElseThrow()),
                        referencePrefix
                    )
                );
                return;
            }
            throw e;
        }
    }

    private Map<String, Object> buildQuantityAdjustmentPayload(
        String zohoItemId,
        int delta,
        BigDecimal unitPrice,
        String adjustmentAccountId,
        String referencePrefix
    ) {
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("item_id", zohoItemId);
        line.put("quantity_adjusted", delta);
        if (unitPrice != null && unitPrice.compareTo(BigDecimal.ZERO) > 0) {
            line.put("price", unitPrice);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("date", LocalDate.now(INDIA).toString());
        payload.put("reason", "Store catalog sync");
        payload.put("adjustment_type", "quantity");
        payload.put("adjustment_account_id", adjustmentAccountId);
        payload.put("reference_number", truncatedReferenceNumber(referencePrefix));
        accountResolver.resolveLocationId().ifPresent(locationId -> payload.put("location_id", locationId));
        payload.put("line_items", List.of(line));
        return payload;
    }

    private static String truncatedReferenceNumber(String referencePrefix) {
        String safePrefix = StringUtils.defaultString(referencePrefix);
        if (safePrefix.length() <= 50) {
            return safePrefix;
        }
        String suffix = "-" + Integer.toHexString(safePrefix.hashCode());
        int maxPrefix = 50 - suffix.length();
        return safePrefix.substring(0, Math.max(0, maxPrefix)) + suffix;
    }

    private static boolean isInventoryItem(Map<String, Object> item) {
        if (item == null || item.isEmpty()) {
            return false;
        }
        String itemType = StringUtils.defaultString(stringValue(item.get("item_type"))).toLowerCase();
        if ("inventory".equals(itemType)) {
            return true;
        }
        Object trackInventory = item.get("track_inventory");
        return trackInventory instanceof Boolean flag && flag;
    }

    private static String formatRate(BigDecimal price) {
        if (price == null) {
            return "0";
        }
        return price.stripTrailingZeros().toPlainString();
    }

    private static String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private static BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        String text = value.toString().trim();
        if (text.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(text);
    }

    private static int toInteger(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        String text = value.toString().trim();
        if (text.isEmpty()) {
            return 0;
        }
        return Integer.parseInt(text);
    }
}
