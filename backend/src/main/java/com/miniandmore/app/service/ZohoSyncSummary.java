package com.miniandmore.app.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Matches the {@code ZohoSyncSummary}/{@code ZohoSyncFailure} shape already expected by
 * {@code src/lib/backend-data.ts} on the frontend ({@code success}/{@code failed}, entries carry
 * {@code productId}/{@code productName}/{@code message}).
 */
public record ZohoSyncSummary(
    int successCount,
    int failureCount,
    int skippedCount,
    List<Map<String, Object>> failures,
    List<Map<String, Object>> notices,
    List<Map<String, Object>> skipped
) {
    public ZohoSyncSummary {
        failures = failures == null ? List.of() : List.copyOf(failures);
        notices = notices == null ? List.of() : List.copyOf(notices);
        skipped = skipped == null ? List.of() : List.copyOf(skipped);
    }

    public static ZohoSyncSummary empty() {
        return new ZohoSyncSummary(0, 0, 0, List.of(), List.of(), List.of());
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("success", successCount);
        map.put("failed", failureCount);
        map.put("skipped", skippedCount);
        map.put("failures", failures);
        map.put("notices", notices);
        return map;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private int successCount;
        private int failureCount;
        private int skippedCount;
        private final List<Map<String, Object>> failures = new ArrayList<>();
        private final List<Map<String, Object>> notices = new ArrayList<>();
        private final List<Map<String, Object>> skipped = new ArrayList<>();
        private final Map<Long, String> productFailures = new LinkedHashMap<>();

        public Builder success() {
            successCount++;
            return this;
        }

        public Builder failure(String message) {
            failureCount++;
            failures.add(entry(null, null, null, message));
            return this;
        }

        public Builder failure(String sku, String message) {
            failureCount++;
            failures.add(entry(null, sku, null, message));
            return this;
        }

        public Builder orderFailure(String orderNumber, String message) {
            failureCount++;
            failures.add(entry(null, null, orderNumber, message));
            return this;
        }

        public Builder failure(Long id, String name, String message) {
            failureCount++;
            failures.add(entry(id, name, null, message));
            if (id != null) productFailures.putIfAbsent(id, message);
            return this;
        }

        public String failureForProduct(Long id) {
            return productFailures.get(id);
        }

        public Builder skipped(String sku, String message) {
            skippedCount++;
            skipped.add(entry(null, sku, null, message));
            return this;
        }

        public Builder notice(String message) {
            notices.add(entry(null, null, null, message));
            return this;
        }

        public Builder notice(String sku, String message) {
            notices.add(entry(null, sku, null, message));
            return this;
        }

        private Map<String, Object> entry(Long id, String productName, String orderNumber, String message) {
            Map<String, Object> entry = new LinkedHashMap<>();
            if (id != null) {
                entry.put("productId", id);
            }
            if (productName != null) {
                entry.put("productName", productName);
            }
            if (orderNumber != null) {
                entry.put("orderNumber", orderNumber);
            }
            entry.put("message", message);
            return entry;
        }

        public ZohoSyncSummary build() {
            return new ZohoSyncSummary(successCount, failureCount, skippedCount, failures, notices, skipped);
        }
    }
}
