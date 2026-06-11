package com.miniandmore.integration.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.miniandmore.integration.config.ApplicationProperties;
import com.miniandmore.integration.domain.Product;
import com.miniandmore.integration.domain.ZohoSetting;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Pushes store {@link Product#getStock()} to Zoho Books: inventory item type on create/update,
 * {@code initial_stock} on create, and quantity inventory adjustments when on-hand differs.
 */
@Component
public class ZohoStockSync {

    private static final Logger LOG = LoggerFactory.getLogger(ZohoStockSync.class);
    private static final String ADJUSTMENT_REASON = "Synced from Minis and More store";

    private final ObjectMapper objectMapper;
    private final ApplicationProperties applicationProperties;
    private final ZohoBooksClient zohoBooksClient;
    private volatile String cachedLocationId;
    private volatile String cachedInventoryAccountId;

    public ZohoStockSync(ObjectMapper objectMapper, ApplicationProperties applicationProperties, ZohoBooksClient zohoBooksClient) {
        this.objectMapper = objectMapper;
        this.applicationProperties = applicationProperties;
        this.zohoBooksClient = zohoBooksClient;
    }

    /**
     * @param openingStock {@code true} for POST /items ({@code initial_stock}); updates rely on {@link #reconcileStock}
     */
    public void enrichItemPayload(ObjectNode payload, ZohoSetting setting, Product product, boolean openingStock) {
        if (!applicationProperties.getZoho().isSyncProductStock()) {
            return;
        }
        if (product.getStock() == null) {
            return;
        }
        enrichInventoryItemType(payload, setting);
        if (!"inventory".equals(payload.path("item_type").asText(null))) {
            return;
        }
        if (!openingStock) {
            return;
        }
        Optional<String> locationId = resolveLocationId(setting);
        if (locationId.isEmpty()) {
            LOG.warn(
                "Skipping Zoho opening stock for product {} ({}): no location_id. Set ZOHO_DEFAULT_LOCATION_ID or enable Locations in Zoho Books.",
                product.getId(),
                product.getName()
            );
            return;
        }
        ObjectNode location = objectMapper.createObjectNode();
        location.put("location_id", locationId.orElseThrow());
        location.put("initial_stock", String.valueOf(Math.max(0, product.getStock())));
        location.put("initial_stock_rate", formatRate(product.getPrice()));
        ArrayNode locations = objectMapper.createArrayNode();
        locations.add(location);
        payload.set("locations", locations);
    }

    /**
     * Sets on-hand stock to the store quantity via inventory adjustment when it differs from Zoho.
     */
    public void reconcileStock(ZohoSetting setting, String zohoItemId, Product product, JsonNode zohoItem) {
        if (!applicationProperties.getZoho().isSyncProductStock()) {
            return;
        }
        if (StringUtils.isBlank(zohoItemId) || product.getStock() == null) {
            return;
        }
        Optional<String> locationId = resolveLocationId(setting);
        if (locationId.isEmpty()) {
            return;
        }
        int target = Math.max(0, product.getStock());
        int current = readZohoStock(zohoItem, locationId.orElseThrow());
        int delta = target - current;
        if (delta == 0) {
            return;
        }
        try {
            postQuantityAdjustment(setting, zohoItemId, locationId.orElseThrow(), delta);
            LOG.debug("Adjusted Zoho stock for item {} by {} (now {})", zohoItemId, delta, target);
        } catch (RuntimeException e) {
            LOG.warn(
                "Zoho stock adjustment failed for product {} ({}), item {}: {}",
                product.getId(),
                product.getName(),
                zohoItemId,
                e.getMessage()
            );
        }
    }

    void enrichInventoryItemType(ObjectNode payload, ZohoSetting setting) {
        Optional<String> accountId = resolveInventoryAccountId(setting);
        if (accountId.isEmpty()) {
            LOG.warn(
                "Zoho inventory account could not be resolved; item will sync without inventory tracking. " +
                "Set ZOHO_INVENTORY_ACCOUNT_ID or check Zoho Books chart of accounts."
            );
            return;
        }
        payload.put("item_type", "inventory");
        payload.put("inventory_account_id", accountId.orElseThrow());
    }

    Optional<String> resolveLocationId(ZohoSetting setting) {
        String configured = StringUtils.trimToNull(applicationProperties.getZoho().getDefaultLocationId());
        if (configured != null) {
            return Optional.of(configured);
        }
        if (StringUtils.isNotBlank(cachedLocationId)) {
            return Optional.of(cachedLocationId);
        }
        synchronized (this) {
            if (StringUtils.isNotBlank(cachedLocationId)) {
                return Optional.of(cachedLocationId);
            }
            try {
                JsonNode response = zohoBooksClient.get(setting, "/locations", Map.of());
                JsonNode locations = response != null ? response.path("locations") : null;
                if (locations == null || !locations.isArray() || locations.isEmpty()) {
                    return Optional.empty();
                }
                String preferredName = StringUtils.trimToEmpty(applicationProperties.getZoho().getDefaultLocationName());
                String nameMatch = null;
                String primary = null;
                String fallback = null;
                for (JsonNode node : locations) {
                    if (!node.hasNonNull("location_id")) {
                        continue;
                    }
                    String id = node.path("location_id").asText();
                    if (fallback == null) {
                        fallback = id;
                    }
                    if (StringUtils.isNotBlank(preferredName)) {
                        String locationName = node.path("location_name").asText("");
                        if (locationName.equalsIgnoreCase(preferredName) || locationName.toLowerCase().contains(preferredName.toLowerCase())) {
                            nameMatch = id;
                            break;
                        }
                    }
                    if (node.has("is_primary") && node.path("is_primary").asBoolean()) {
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
            return Optional.empty();
        }
    }

    Optional<String> resolveInventoryAccountId(ZohoSetting setting) {
        String configured = StringUtils.trimToNull(applicationProperties.getZoho().getInventoryAccountId());
        if (configured != null) {
            return Optional.of(configured);
        }
        if (StringUtils.isNotBlank(cachedInventoryAccountId)) {
            return Optional.of(cachedInventoryAccountId);
        }
        synchronized (this) {
            if (StringUtils.isNotBlank(cachedInventoryAccountId)) {
                return Optional.of(cachedInventoryAccountId);
            }
            try {
                JsonNode response = zohoBooksClient.get(
                    setting,
                    "/chartofaccounts",
                    Map.of("filter_by", "AccountType.Asset")
                );
                JsonNode accounts = response != null ? response.path("chartofaccounts") : null;
                if (accounts == null || !accounts.isArray()) {
                    return Optional.empty();
                }
                String inventoryMatch = null;
                String stockMatch = null;
                String fallback = null;
                for (JsonNode account : accounts) {
                    if (!account.hasNonNull("account_id")) {
                        continue;
                    }
                    String id = account.path("account_id").asText();
                    String name = account.path("account_name").asText("").toLowerCase();
                    if (fallback == null) {
                        fallback = id;
                    }
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

    private void postQuantityAdjustment(ZohoSetting setting, String zohoItemId, String locationId, int delta) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("date", LocalDate.now().toString());
        payload.put("reason", ADJUSTMENT_REASON);
        payload.put("adjustment_type", "quantity");
        payload.put("location_id", locationId);
        ObjectNode lineItem = objectMapper.createObjectNode();
        lineItem.put("item_id", zohoItemId);
        lineItem.put("quantity_adjusted", String.valueOf(delta));
        lineItem.put("location_id", locationId);
        ArrayNode lineItems = objectMapper.createArrayNode();
        lineItems.add(lineItem);
        payload.set("line_items", lineItems);
        zohoBooksClient.post(setting, "/inventoryadjustments", Map.of(), payload);
    }

    static int readZohoStock(JsonNode item, String locationId) {
        if (item == null || item.isMissingNode()) {
            return 0;
        }
        List<String> topLevel = List.of("stock_on_hand", "available_stock", "actual_available_stock", "quantity");
        for (String field : topLevel) {
            if (item.hasNonNull(field) && item.path(field).canConvertToInt()) {
                return Math.max(0, item.path(field).asInt());
            }
        }
        JsonNode locations = item.path("locations");
        if (locations.isArray()) {
            Integer atLocation = null;
            Integer first = null;
            for (JsonNode location : locations) {
                if (!location.has("location_stock_on_hand")) {
                    continue;
                }
                String value = location.path("location_stock_on_hand").asText("");
                if (StringUtils.isBlank(value)) {
                    continue;
                }
                try {
                    int stock = Math.max(0, (int) Math.round(Double.parseDouble(value.trim())));
                    if (first == null) {
                        first = stock;
                    }
                    if (locationId != null && locationId.equals(location.path("location_id").asText())) {
                        atLocation = stock;
                        break;
                    }
                } catch (NumberFormatException ignored) {
                    // skip non-numeric
                }
            }
            if (atLocation != null) {
                return atLocation;
            }
            if (first != null) {
                return first;
            }
        }
        return 0;
    }

    private static String formatRate(BigDecimal price) {
        if (price == null) {
            return "0";
        }
        return price.stripTrailingZeros().toPlainString();
    }
}
