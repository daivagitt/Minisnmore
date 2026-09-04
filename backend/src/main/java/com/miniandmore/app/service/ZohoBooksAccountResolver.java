package com.miniandmore.app.service;

import com.miniandmore.app.client.ZohoBooksClient;
import com.miniandmore.app.config.ApplicationProperties;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;

/** Resolves Zoho Books org accounts and locations with in-memory caching. */
@Service
public class ZohoBooksAccountResolver {

    private static final Logger LOG = LoggerFactory.getLogger(ZohoBooksAccountResolver.class);

    private final ZohoBooksClient booksClient;
    private final ApplicationProperties applicationProperties;
    private volatile String cachedLocationId;
    private volatile String cachedInventoryAccountId;
    private volatile String cachedAdjustmentAccountId;
    private volatile boolean configuredInventoryAccountRejected;
    private volatile boolean configuredLocationRejected;
    private volatile boolean configuredAdjustmentAccountRejected;

    public ZohoBooksAccountResolver(ZohoBooksClient booksClient, ApplicationProperties applicationProperties) {
        this.booksClient = booksClient;
        this.applicationProperties = applicationProperties;
    }

    public boolean isStockAdjustmentEnabled() {
        return applicationProperties.getZoho().isSyncProductStock() && resolveInventoryAccountId().isPresent();
    }

    public Optional<String> resolveInventoryAccountId() {
        String configured = StringUtils.trimToNull(applicationProperties.getZoho().getInventoryAccountId());
        if (configured != null && !configuredInventoryAccountRejected) {
            if (orgContainsId(() -> booksClient.listAssetAccounts(), "account_id", configured)) {
                return Optional.of(configured);
            }
            LOG.warn("Configured Zoho inventory account {} is not in this org; looking up Inventory Asset", configured);
            configuredInventoryAccountRejected = true;
        }
        if (!applicationProperties.getZoho().isSyncProductStock() && configured == null) {
            return Optional.empty();
        }
        return lookupInventoryAccountFromChart();
    }

    public Optional<String> resolveAdjustmentAccountId() {
        String configured = StringUtils.trimToNull(applicationProperties.getZoho().getInventoryAdjustmentAccountId());
        if (configured != null && !configuredAdjustmentAccountRejected) {
            if (orgContainsId(() -> booksClient.listExpenseAccounts(), "account_id", configured)) {
                return Optional.of(configured);
            }
            LOG.warn("Configured Zoho adjustment account {} is not in this org; looking up Cost of Goods Sold", configured);
            configuredAdjustmentAccountRejected = true;
        }
        if (StringUtils.isNotBlank(cachedAdjustmentAccountId)) {
            return Optional.of(cachedAdjustmentAccountId);
        }
        synchronized (this) {
            if (StringUtils.isNotBlank(cachedAdjustmentAccountId)) {
                return Optional.of(cachedAdjustmentAccountId);
            }
            try {
                List<Map<String, Object>> accounts = booksClient.listExpenseAccounts();
                String cogsMatch = null;
                String fallback = null;
                for (Map<String, Object> account : accounts) {
                    String id = stringValue(account.get("account_id"));
                    if (StringUtils.isBlank(id)) {
                        continue;
                    }
                    if (fallback == null) {
                        fallback = id;
                    }
                    String name = StringUtils.defaultString(stringValue(account.get("account_name"))).toLowerCase();
                    if (name.contains("cost of goods") || name.contains("cogs") || name.contains("inventory adjustment")) {
                        cogsMatch = id;
                        break;
                    }
                }
                String resolved = cogsMatch != null ? cogsMatch : fallback;
                if (resolved != null) {
                    cachedAdjustmentAccountId = resolved;
                    return Optional.of(resolved);
                }
            } catch (RuntimeException e) {
                LOG.warn("Could not list Zoho Books expense accounts for stock adjustment: {}", e.getMessage());
            }
            return Optional.empty();
        }
    }

    public Optional<String> resolveLocationId() {
        if (StringUtils.isNotBlank(cachedLocationId)) {
            return Optional.of(cachedLocationId);
        }
        synchronized (this) {
            if (StringUtils.isNotBlank(cachedLocationId)) {
                return Optional.of(cachedLocationId);
            }
            String configured = StringUtils.trimToNull(applicationProperties.getZoho().getDefaultLocationId());
            try {
                List<Map<String, Object>> locations = booksClient.listLocations();
                if (configured != null && !configuredLocationRejected) {
                    if (locations == null || locations.isEmpty() || containsNodeId(locations, "location_id", configured)) {
                        cachedLocationId = configured;
                        return Optional.of(configured);
                    }
                    LOG.warn("Configured Zoho location {} is not in this org; looking up Head Office / primary location", configured);
                    configuredLocationRejected = true;
                }
                if (locations == null || locations.isEmpty()) {
                    return Optional.empty();
                }
                String preferredName = StringUtils.trimToEmpty(applicationProperties.getZoho().getDefaultLocationName());
                String nameMatch = null;
                String primary = null;
                String fallback = null;
                for (Map<String, Object> node : locations) {
                    String id = stringValue(node.get("location_id"));
                    if (StringUtils.isBlank(id)) {
                        continue;
                    }
                    if (fallback == null) {
                        fallback = id;
                    }
                    if (StringUtils.isNotBlank(preferredName)) {
                        String locationName = StringUtils.defaultString(stringValue(node.get("location_name")));
                        if (
                            locationName.equalsIgnoreCase(preferredName) ||
                            locationName.toLowerCase().contains(preferredName.toLowerCase())
                        ) {
                            nameMatch = id;
                            break;
                        }
                    }
                    Object isPrimary = node.get("is_primary");
                    if (isPrimary instanceof Boolean primaryFlag && primaryFlag) {
                        primary = id;
                    } else if (isPrimary != null && Boolean.parseBoolean(isPrimary.toString())) {
                        primary = id;
                    }
                }
                String resolved = nameMatch != null ? nameMatch : (primary != null ? primary : fallback);
                if (resolved != null) {
                    cachedLocationId = resolved;
                    return Optional.of(resolved);
                }
            } catch (RuntimeException e) {
                LOG.warn("Could not list Zoho Books locations: {}", e.getMessage());
            }
            if (configured != null && !configuredLocationRejected) {
                cachedLocationId = configured;
                return Optional.of(configured);
            }
            return Optional.empty();
        }
    }

    public static boolean isInvalidAdjustmentAccount(RestClientResponseException exception) {
        String body = StringUtils.defaultString(exception.getResponseBodyAsString()).toLowerCase();
        if (exception.getStatusCode().value() != 400) {
            return false;
        }
        return (body.contains("account") && body.contains("does not exist")) || body.contains("2035");
    }

    public static boolean isInvalidLocation(RestClientResponseException exception) {
        String body = StringUtils.defaultString(exception.getResponseBodyAsString()).toLowerCase();
        if (exception.getStatusCode().value() != 400) {
            return false;
        }
        return body.contains("location") &&
            (body.contains("does not exist") ||
                body.contains("mandatory") ||
                body.contains("invalid") ||
                body.contains("select"));
    }

    void markConfiguredInventoryAccountRejected() {
        configuredInventoryAccountRejected = true;
        cachedInventoryAccountId = null;
    }

    void markConfiguredAdjustmentAccountRejected() {
        configuredAdjustmentAccountRejected = true;
        cachedAdjustmentAccountId = null;
    }

    void markConfiguredLocationRejected() {
        configuredLocationRejected = true;
        cachedLocationId = null;
    }

    private Optional<String> lookupInventoryAccountFromChart() {
        if (StringUtils.isNotBlank(cachedInventoryAccountId)) {
            return Optional.of(cachedInventoryAccountId);
        }
        synchronized (this) {
            if (StringUtils.isNotBlank(cachedInventoryAccountId)) {
                return Optional.of(cachedInventoryAccountId);
            }
            try {
                List<Map<String, Object>> accounts = booksClient.listAssetAccounts();
                String inventoryMatch = null;
                String stockMatch = null;
                String fallback = null;
                for (Map<String, Object> account : accounts) {
                    String id = stringValue(account.get("account_id"));
                    if (StringUtils.isBlank(id)) {
                        continue;
                    }
                    if (fallback == null) {
                        fallback = id;
                    }
                    String name = StringUtils.defaultString(stringValue(account.get("account_name"))).toLowerCase();
                    if (name.contains("inventory")) {
                        inventoryMatch = id;
                    } else if (name.contains("stock")) {
                        stockMatch = id;
                    }
                }
                String resolved = inventoryMatch != null ? inventoryMatch : (stockMatch != null ? stockMatch : fallback);
                if (resolved != null) {
                    cachedInventoryAccountId = resolved;
                    return Optional.of(resolved);
                }
            } catch (RuntimeException e) {
                LOG.warn("Could not list Zoho Books chart of accounts for inventory: {}", e.getMessage());
            }
            return Optional.empty();
        }
    }

    private boolean orgContainsId(java.util.function.Supplier<List<Map<String, Object>>> loader, String idKey, String id) {
        try {
            List<Map<String, Object>> rows = loader.get();
            if (rows == null || rows.isEmpty()) {
                return true;
            }
            for (Map<String, Object> row : rows) {
                if (id.equals(stringValue(row.get(idKey)))) {
                    return true;
                }
            }
            return false;
        } catch (RuntimeException e) {
            LOG.debug("Could not verify Zoho {} {}: {}", idKey, id, e.getMessage());
            return true;
        }
    }

    private static boolean containsNodeId(List<Map<String, Object>> rows, String idKey, String id) {
        for (Map<String, Object> row : rows) {
            if (id.equals(stringValue(row.get(idKey)))) {
                return true;
            }
        }
        return false;
    }

    private static String stringValue(Object value) {
        return value == null ? null : value.toString();
    }
}
