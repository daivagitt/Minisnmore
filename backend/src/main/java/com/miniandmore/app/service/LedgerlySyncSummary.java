package com.miniandmore.app.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record LedgerlySyncSummary(int successCount, int failureCount, List<Map<String, Object>> failures, List<Map<String, Object>> notices) {
    public LedgerlySyncSummary {
        failures = failures == null ? List.of() : List.copyOf(failures);
        notices = notices == null ? List.of() : List.copyOf(notices);
    }

    public static LedgerlySyncSummary empty() {
        return new LedgerlySyncSummary(0, 0, List.of(), List.of());
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("successCount", successCount);
        map.put("failureCount", failureCount);
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
        private final List<Map<String, Object>> failures = new ArrayList<>();
        private final List<Map<String, Object>> notices = new ArrayList<>();

        public Builder success() {
            successCount++;
            return this;
        }

        public Builder failure(String message) {
            failureCount++;
            failures.add(Map.of("message", message));
            return this;
        }

        public Builder failure(String sku, String message) {
            failureCount++;
            failures.add(Map.of("sku", sku, "message", message));
            return this;
        }

        public Builder orderFailure(String orderNumber, String message) {
            failureCount++;
            failures.add(Map.of("orderNumber", orderNumber, "message", message));
            return this;
        }

        public Builder failure(Long id, String name, String message) {
            failureCount++;
            Map<String, Object> entry = new LinkedHashMap<>();
            if (id != null) {
                entry.put("id", id);
            }
            if (name != null) {
                entry.put("productName", name);
            }
            entry.put("message", message);
            failures.add(entry);
            return this;
        }

        public Builder notice(String message) {
            notices.add(Map.of("message", message));
            return this;
        }

        public Builder notice(String sku, String message) {
            Map<String, Object> entry = new LinkedHashMap<>();
            if (sku != null) {
                entry.put("sku", sku);
            }
            entry.put("message", message);
            notices.add(entry);
            return this;
        }

        public LedgerlySyncSummary build() {
            return new LedgerlySyncSummary(successCount, failureCount, failures, notices);
        }
    }
}
