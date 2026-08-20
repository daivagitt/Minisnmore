package com.miniandmore.app.service;

import com.miniandmore.app.client.ZohoBooksClient;
import com.miniandmore.app.config.ApplicationProperties;
import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.CustomerProfile;
import com.miniandmore.app.domain.OrderItem;
import com.miniandmore.app.domain.Product;
import com.miniandmore.app.domain.enumeration.ZohoSyncStatus;
import com.miniandmore.app.domain.enumeration.OrderStatus;
import com.miniandmore.app.domain.enumeration.PaymentStatus;
import com.miniandmore.app.repository.CustomerOrderRepository;
import com.miniandmore.app.repository.CustomerProfileRepository;
import com.miniandmore.app.repository.OrderItemRepository;
import com.miniandmore.app.repository.ProductRepository;
import com.miniandmore.app.repository.ZohoSettingRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Orchestrates Zoho Books sync: product push/pull, inventory pull, order (sales order + invoice)
 * push, and order status pull. Talks to the real Zoho Books API v3 via {@link ZohoBooksClient}.
 */
@Service
@Transactional
public class ZohoSyncService {

    private static final Logger LOG = LoggerFactory.getLogger(ZohoSyncService.class);
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$", Pattern.CASE_INSENSITIVE);
    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");
    private static final String COD_PAYMENT_TERMS_LABEL = "Cash on Delivery";
    private static final String COD_FEE_LINE_NAME = "COD handling fee";

    private final ZohoBooksClient booksClient;
    private final ObjectMapper objectMapper;
    private final ApplicationProperties applicationProperties;
    private final ZohoItemImageSync itemImageSync;
    private final ZohoProductFieldMapper productFieldMapper;
    private final ProductDescriptionCodec descriptionCodec;
    private final ProductVariantSupport variantSupport;
    private final ProductRepository productRepository;
    private final ZohoSettingRepository zohoSettingRepository;
    private final CustomerOrderRepository customerOrderRepository;
    private final CustomerProfileRepository customerProfileRepository;
    private final OrderItemRepository orderItemRepository;
    private volatile String cachedLocationId;
    private volatile String cachedInventoryAccountId;
    private volatile String cachedAdjustmentAccountId;
    private volatile boolean configuredInventoryAccountRejected;
    private volatile boolean configuredLocationRejected;
    private volatile boolean configuredAdjustmentAccountRejected;

    public ZohoSyncService(
        ZohoBooksClient booksClient,
        ObjectMapper objectMapper,
        ApplicationProperties applicationProperties,
        ZohoItemImageSync itemImageSync,
        ZohoProductFieldMapper productFieldMapper,
        ProductDescriptionCodec descriptionCodec,
        ProductVariantSupport variantSupport,
        ProductRepository productRepository,
        ZohoSettingRepository zohoSettingRepository,
        CustomerOrderRepository customerOrderRepository,
        CustomerProfileRepository customerProfileRepository,
        OrderItemRepository orderItemRepository
    ) {
        this.booksClient = booksClient;
        this.objectMapper = objectMapper;
        this.applicationProperties = applicationProperties;
        this.itemImageSync = itemImageSync;
        this.productFieldMapper = productFieldMapper;
        this.descriptionCodec = descriptionCodec;
        this.variantSupport = variantSupport;
        this.productRepository = productRepository;
        this.zohoSettingRepository = zohoSettingRepository;
        this.customerOrderRepository = customerOrderRepository;
        this.customerProfileRepository = customerProfileRepository;
        this.orderItemRepository = orderItemRepository;
    }

    public Map<String, Object> testConnection() {
        return booksClient.testConnection();
    }

    @Async
    public void syncProductAsync(Long productId) {
        if (!applicationProperties.getZoho().isAutoSyncEnabled()) {
            LOG.debug("Skipping async Zoho product sync for {} — auto-sync disabled", productId);
            return;
        }
        syncProduct(productId);
    }

    @Async
    public void syncAllProductsAsync() {
        syncAllProducts();
    }

    @Async
    public void pullProductsAsync() {
        pullProducts();
    }

    public void syncProduct(Long productId) {
        productRepository
            .findOneWithEagerRelationships(productId)
            .ifPresent(product -> {
                try {
                    pushProductToZoho(product);
                } catch (Exception e) {
                    LOG.warn("Zoho product sync failed for {}: {}", productId, e.getMessage());
                    productRepository.updateZohoSyncFailure(productId, ZohoSyncStatus.FAILED, e.getMessage());
                }
            });
    }

    public void deactivateProduct(Long productId) {
        productRepository
            .findOneWithEagerRelationships(productId)
            .ifPresent(product -> {
                try {
                    if (isZohoLinked(product)) {
                        deactivateInZoho(product);
                    }
                } catch (Exception e) {
                    LOG.warn("Zoho deactivation failed for {}: {}", productId, e.getMessage());
                }
            });
    }

    public Map<String, Object> syncAllProducts() {
        ZohoSyncSummary.Builder builder = ZohoSyncSummary.builder();
        for (Product product : productRepository.findAllWithEagerRelationships()) {
            try {
                if (StringUtils.isBlank(product.getSku())) {
                    builder.notice(product.getName(), "Skipped — SKU required");
                    continue;
                }
                if (!Boolean.TRUE.equals(product.getActive())) {
                    if (isZohoLinked(product)) {
                        deactivateInZoho(product);
                        builder.notice(product.getSku(), "Deactivated in Zoho");
                    }
                    continue;
                }
                pushProductToZoho(product);
                builder.success();
            } catch (Exception e) {
                productRepository.updateZohoSyncFailure(product.getId(), ZohoSyncStatus.FAILED, e.getMessage());
                builder.failure(product.getId(), product.getName(), e.getMessage());
            }
        }
        return builder.build().toMap();
    }

    private void pushProductToZoho(Product product) {
        if (StringUtils.isBlank(product.getSku())) {
            throw new IllegalStateException("SKU is required for Zoho sync");
        }
        if (!Boolean.TRUE.equals(product.getActive())) {
            if (isZohoLinked(product)) {
                deactivateInZoho(product);
            }
            return;
        }
        if (variantSupport.isVariants(product)) {
            pushVariantMatrixToZoho(product);
            return;
        }
        Map<String, Object> existing = StringUtils.isNotBlank(product.getZohoItemId())
            ? null
            : booksClient.findItemBySku(product.getSku());
        String itemId = StringUtils.isNotBlank(product.getZohoItemId())
            ? product.getZohoItemId()
            : existing != null
                ? stringValue(existing.get("item_id"))
                : null;

        boolean openingStock = StringUtils.isBlank(itemId);
        Optional<String> inventoryAccountId = resolveInventoryAccountId();
        boolean trackInventory = applicationProperties.getZoho().isSyncProductStock() && inventoryAccountId.isPresent();
        Optional<String> locationId = openingStock && trackInventory ? resolveLocationId() : Optional.empty();
        Map<String, Object> payload = buildItemPayload(product, openingStock, inventoryAccountId, locationId.orElse(null));
        Map<String, Object> response = persistZohoItem(
            openingStock,
            itemId,
            payload,
            inventoryAccountId,
            trackInventory,
            accountId -> {
                boolean track = accountId.isPresent();
                String loc = openingStock && track ? resolveLocationId().orElse(null) : null;
                return buildItemPayload(product, openingStock, accountId, loc);
            }
        );
        @SuppressWarnings("unchecked")
        Map<String, Object> item = (Map<String, Object>) response.getOrDefault("item", response);
        String newItemId = stringValue(item.get("item_id"));
        if (StringUtils.isBlank(newItemId)) {
            newItemId = itemId;
        }
        if (StringUtils.isBlank(newItemId)) {
            throw new IllegalStateException("Zoho did not return an item id");
        }
        try {
            itemImageSync.syncProductImage(newItemId, product);
        } catch (Exception e) {
            LOG.warn("Zoho image push failed for product {} ({}): {}", product.getId(), product.getName(), e.getMessage());
        }
        syncZohoStockAfterPush(product, newItemId);
        productRepository.updateZohoSyncSuccess(product.getId(), newItemId, ZohoSyncStatus.SYNCED, Instant.now());
    }

    /**
     * Zoho Books has no parent matrix item — each sellable variant SKU is its own inventory/sales item.
     */
    private void pushVariantMatrixToZoho(Product product) {
        List<ProductVariantSupport.VariantRow> rows = variantSupport.parseProduct(product);
        int pushed = 0;
        for (ProductVariantSupport.VariantRow row : rows) {
            if (!row.active() || StringUtils.isBlank(row.sku())) {
                continue;
            }
            String zohoItemId = pushVariantRowToZoho(product, row);
            variantSupport.setVariantZohoItemId(product, row.sku(), zohoItemId);
            pushed++;
        }
        if (pushed == 0) {
            throw new IllegalStateException("No active variant SKUs for Zoho sync");
        }
        productRepository.save(product);
        String parentItemId = StringUtils.trimToNull(product.getZohoItemId());
        productRepository.updateZohoSyncSuccess(product.getId(), parentItemId, ZohoSyncStatus.SYNCED, Instant.now());
    }

    private String pushVariantRowToZoho(Product product, ProductVariantSupport.VariantRow row) {
        String variantSku = row.sku().trim();
        Map<String, Object> existing = StringUtils.isNotBlank(row.zohoItemId()) ? null : booksClient.findItemBySku(variantSku);
        String itemId = StringUtils.isNotBlank(row.zohoItemId())
            ? row.zohoItemId().trim()
            : existing != null
                ? stringValue(existing.get("item_id"))
                : null;

        boolean openingStock = StringUtils.isBlank(itemId);
        Optional<String> inventoryAccountId = resolveInventoryAccountId();
        boolean trackInventory = applicationProperties.getZoho().isSyncProductStock() && inventoryAccountId.isPresent();
        Optional<String> locationId = openingStock && trackInventory ? resolveLocationId() : Optional.empty();
        Map<String, Object> payload = buildVariantItemPayload(product, row, openingStock, inventoryAccountId, locationId.orElse(null));
        Map<String, Object> response = persistZohoItem(
            openingStock,
            itemId,
            payload,
            inventoryAccountId,
            trackInventory,
            accountId -> {
                boolean track = accountId.isPresent();
                String loc = openingStock && track ? resolveLocationId().orElse(null) : null;
                return buildVariantItemPayload(product, row, openingStock, accountId, loc);
            }
        );
        @SuppressWarnings("unchecked")
        Map<String, Object> item = (Map<String, Object>) response.getOrDefault("item", response);
        String newItemId = stringValue(item.get("item_id"));
        if (StringUtils.isBlank(newItemId)) {
            newItemId = itemId;
        }
        if (StringUtils.isBlank(newItemId)) {
            throw new IllegalStateException("Zoho did not return an item id for variant " + variantSku);
        }
        try {
            itemImageSync.syncVariantImage(newItemId, product, variantSku);
        } catch (Exception e) {
            LOG.warn("Zoho image push failed for variant {} ({}): {}", variantSku, product.getName(), e.getMessage());
        }
        syncZohoVariantStockAfterPush(product, row, newItemId);
        return newItemId;
    }

    private Map<String, Object> buildVariantItemPayload(
        Product product,
        ProductVariantSupport.VariantRow row,
        boolean openingStock,
        Optional<String> inventoryAccountId,
        String locationId
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        String size = StringUtils.trimToEmpty(row.size());
        String color = StringUtils.trimToEmpty(row.color());
        String variantName = product.getName();
        if (!size.isEmpty() || !color.isEmpty()) {
            variantName = product.getName() + " — " + String.join(" / ", List.of(size, color).stream().filter(StringUtils::isNotBlank).toList());
        }
        payload.put("name", variantName);
        payload.put("sku", row.sku());
        BigDecimal sellingPrice = variantSupport.variantSellingPrice(row);
        payload.put("rate", sellingPrice);
        payload.put("product_type", "goods");
        payload.put("unit", "pcs");
        putVariantAttributes(payload, product, row);
        if (inventoryAccountId.isPresent()) {
            payload.put("item_type", "inventory");
            payload.put("inventory_account_id", inventoryAccountId.orElseThrow());
        } else {
            payload.put("item_type", "sales");
        }
        if (StringUtils.isNotBlank(product.getDescription())) {
            payload.put("description", product.getDescription().trim());
        }
        if (openingStock && inventoryAccountId.isPresent() && StringUtils.isNotBlank(locationId)) {
            Map<String, Object> location = new LinkedHashMap<>();
            location.put("location_id", locationId);
            location.put("initial_stock", String.valueOf(Math.max(0, row.stock())));
            location.put("initial_stock_rate", formatRate(sellingPrice));
            payload.put("locations", List.of(location));
        }
        if (row.mrp() != null && row.mrp().compareTo(BigDecimal.ZERO) > 0) {
            payload.put("label_rate", formatRate(row.mrp()));
        }
        productFieldMapper.enrichPushPayload(product, payload);
        return payload;
    }

    /**
     * Zoho Inventory item groups use Size/Color attributes. Books-only orgs often ignore extra
     * fields; if they reject them we retry as a plain item. {@code group_id} is sent only when the
     * parent {@code zohoItemId} is a group id (not a variant item id).
     */
    private void putVariantAttributes(Map<String, Object> payload, Product product, ProductVariantSupport.VariantRow row) {
        String size = StringUtils.trimToNull(row.size());
        String color = StringUtils.trimToNull(row.color());
        if (size != null) {
            payload.put("attribute_name1", "Size");
            payload.put("attribute_option_name1", size);
        }
        if (color != null) {
            payload.put("attribute_name2", "Color");
            payload.put("attribute_option_name2", color);
        }
        zohoGroupId(product).ifPresent(groupId -> payload.put("group_id", groupId));
    }

    /** Parent {@code zohoItemId} is a group id when it is not itself one of the variant item ids. */
    private Optional<String> zohoGroupId(Product product) {
        if (!variantSupport.isVariants(product)) {
            return Optional.empty();
        }
        String parentId = StringUtils.trimToNull(product.getZohoItemId());
        if (parentId == null) {
            return Optional.empty();
        }
        for (ProductVariantSupport.VariantRow row : variantSupport.parseProduct(product)) {
            if (parentId.equals(StringUtils.trimToNull(row.zohoItemId()))) {
                return Optional.empty();
            }
        }
        return Optional.of(parentId);
    }

    private Map<String, Object> persistZohoItem(
        boolean openingStock,
        String itemId,
        Map<String, Object> payload,
        Optional<String> inventoryAccountId,
        boolean trackInventory,
        java.util.function.Function<Optional<String>, Map<String, Object>> rebuildPayload
    ) {
        try {
            return saveZohoItem(openingStock, itemId, payload);
        } catch (RestClientResponseException e) {
            if (trackInventory && isInvalidInventoryAccount(e)) {
                LOG.warn(
                    "Zoho inventory account {} does not exist in this org; looking up Inventory Asset",
                    inventoryAccountId.orElse("")
                );
                configuredInventoryAccountRejected = true;
                cachedInventoryAccountId = null;
                Optional<String> replacement = resolveInventoryAccountId();
                if (replacement.isPresent() && !replacement.orElseThrow().equals(inventoryAccountId.orElse(null))) {
                    return saveZohoItem(openingStock, itemId, rebuildPayload.apply(replacement));
                }
                LOG.warn("Syncing as a sales item without inventory tracking (no valid inventory account in this Zoho org)");
                return saveZohoItem(openingStock, itemId, rebuildPayload.apply(Optional.empty()));
            }
            if (trackInventory && isInventoryTrackingDisabled(e)) {
                LOG.warn("Zoho inventory tracking is disabled for org; syncing as a sales item without stock");
                return saveZohoItem(openingStock, itemId, rebuildPayload.apply(Optional.empty()));
            }
            throw e;
        }
    }

    private Map<String, Object> saveZohoItem(boolean openingStock, String itemId, Map<String, Object> payload) {
        try {
            return openingStock ? booksClient.createItem(payload) : booksClient.updateItem(itemId, payload);
        } catch (RestClientResponseException e) {
            if (!openingStock && isItemNotFound(e)) {
                // The stored zohoItemId points at an item that no longer exists in this Zoho org
                // (deleted in Zoho, org changed, or the id was never actually created). Re-create it
                // instead of failing the whole sync; stock is reconciled separately after push, so a
                // fresh create without initial-stock fields is safe.
                LOG.warn("Zoho item {} no longer exists; re-creating instead of updating: {}", itemId, e.getMessage());
                return booksClient.createItem(payload);
            }
            if (
                e.getStatusCode().value() == 400 &&
                hasExtraVariantFields(payload) &&
                !isInventoryTrackingDisabled(e) &&
                !isInvalidInventoryAccount(e)
            ) {
                LOG.warn("Zoho rejected variant group/attribute fields; retrying as a plain item: {}", e.getMessage());
                Map<String, Object> plain = new LinkedHashMap<>(payload);
                stripExtraVariantFields(plain);
                return openingStock ? booksClient.createItem(plain) : booksClient.updateItem(itemId, plain);
            }
            throw e;
        }
    }

    private static boolean isItemNotFound(RestClientResponseException exception) {
        String body = StringUtils.defaultString(exception.getResponseBodyAsString()).toLowerCase();
        if (exception.getStatusCode().value() != 400) {
            return false;
        }
        return body.contains("\"code\":2006") || (body.contains("cannot be edited") && body.contains("does not exist"));
    }

    private static boolean hasExtraVariantFields(Map<String, Object> payload) {
        return payload.containsKey("group_id") ||
            payload.containsKey("attribute_name1") ||
            payload.containsKey("attribute_name2") ||
            payload.containsKey("attribute_option_name1") ||
            payload.containsKey("attribute_option_name2");
    }

    private static void stripExtraVariantFields(Map<String, Object> payload) {
        payload.remove("group_id");
        payload.remove("attribute_name1");
        payload.remove("attribute_name2");
        payload.remove("attribute_option_name1");
        payload.remove("attribute_option_name2");
    }

    private void syncZohoVariantStockAfterPush(Product product, ProductVariantSupport.VariantRow row, String zohoItemId) {
        syncZohoItemQuantity(
            zohoItemId,
            Math.max(0, row.stock()),
            variantSupport.variantSellingPrice(row),
            "STORE-VAR-" + product.getId() + "-" + row.sku(),
            "variant " + row.sku() + " (" + product.getSku() + ")"
        );
    }

    private void deactivateInZoho(Product product) {
        if (variantSupport.isVariants(product)) {
            for (ProductVariantSupport.VariantRow row : variantSupport.parseProduct(product)) {
                if (StringUtils.isNotBlank(row.zohoItemId())) {
                    booksClient.markItemInactive(row.zohoItemId().trim());
                }
            }
        }
        if (StringUtils.isNotBlank(product.getZohoItemId()) && zohoGroupId(product).isEmpty()) {
            booksClient.markItemInactive(product.getZohoItemId());
        }
        productRepository.updateZohoSyncFailure(product.getId(), ZohoSyncStatus.SYNCED, null);
    }

    private static boolean isZohoLinked(Product product) {
        return StringUtils.isNotBlank(product.getZohoItemId()) || product.getZohoSyncStatus() == ZohoSyncStatus.SYNCED;
    }

    private Map<String, Object> buildItemPayload(
        Product product,
        boolean openingStock,
        Optional<String> inventoryAccountId,
        String locationId
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", product.getName());
        payload.put("sku", product.getSku());
        BigDecimal sellingPrice = product.getPrice() != null && product.getPrice().compareTo(BigDecimal.ZERO) > 0
            ? product.getPrice()
            : BigDecimal.ZERO;
        payload.put("rate", sellingPrice);
        payload.put("product_type", "goods");
        payload.put("unit", "pcs");
        if (inventoryAccountId.isPresent()) {
            payload.put("item_type", "inventory");
            payload.put("inventory_account_id", inventoryAccountId.orElseThrow());
        } else {
            payload.put("item_type", "sales");
        }
        if (StringUtils.isNotBlank(product.getDescription())) {
            payload.put("description", product.getDescription().trim());
        }
        if (openingStock && inventoryAccountId.isPresent() && product.getStock() != null && StringUtils.isNotBlank(locationId)) {
            Map<String, Object> location = new LinkedHashMap<>();
            location.put("location_id", locationId);
            location.put("initial_stock", String.valueOf(Math.max(0, product.getStock())));
            location.put("initial_stock_rate", formatRate(sellingPrice));
            payload.put("locations", List.of(location));
        } else if (
            openingStock &&
            inventoryAccountId.isPresent() &&
            applicationProperties.getZoho().isSyncProductStock() &&
            product.getStock() != null
        ) {
            LOG.warn(
                "Skipping Zoho opening stock for product {} ({}): no location_id. Set ZOHO_DEFAULT_LOCATION_ID or enable Locations in Zoho Books.",
                product.getId(),
                product.getName()
            );
        }
        productFieldMapper.enrichPushPayload(product, payload);
        return payload;
    }

    private void syncZohoStockAfterPush(Product product, String zohoItemId) {
        if (product.getStock() == null) {
            return;
        }
        BigDecimal unitPrice = product.getPrice() != null && product.getPrice().compareTo(BigDecimal.ZERO) > 0
            ? product.getPrice()
            : BigDecimal.ZERO;
        syncZohoItemQuantity(
            zohoItemId,
            Math.max(0, product.getStock()),
            unitPrice,
            "STORE-SYNC-" + product.getId(),
            "product " + product.getId() + " (" + product.getSku() + ")"
        );
    }

    /**
     * Zoho ignores stock on item create/update after the item exists. Opening stock is only
     * accepted on first create (and only with a valid location). Later qty changes must go
     * through inventory adjustments, and sales items must be converted to inventory first.
     */
    private void syncZohoItemQuantity(
        String zohoItemId,
        int desiredStock,
        BigDecimal unitPriceHint,
        String referencePrefix,
        String logLabel
    ) {
        if (!shouldAdjustZohoInventory() || StringUtils.isBlank(zohoItemId)) {
            return;
        }
        try {
            Map<String, Object> item = getZohoItemMap(zohoItemId);
            if (item.isEmpty()) {
                return;
            }
            if (!isInventoryItem(item)) {
                item = enableInventoryTrackingOnItem(zohoItemId, desiredStock, unitPriceHint, item, logLabel);
                if (!isInventoryItem(item)) {
                    LOG.warn("Skipping Zoho stock sync for {}: item is not inventory-tracked", logLabel);
                    return;
                }
            }
            int currentStock = toInteger(item.get("stock_on_hand"));
            int delta = desiredStock - currentStock;
            if (delta == 0) {
                return;
            }
            BigDecimal unitPrice = unitPriceHint != null && unitPriceHint.compareTo(BigDecimal.ZERO) > 0
                ? unitPriceHint
                : toBigDecimal(item.get("rate"));
            createQuantityAdjustment(zohoItemId, delta, unitPrice, referencePrefix, logLabel);
        } catch (RuntimeException e) {
            LOG.warn("Zoho stock sync failed for {}: {}", logLabel, e.getMessage());
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
        Optional<String> inventoryAccountId = resolveInventoryAccountId();
        Optional<String> locationId = resolveLocationId();
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
        Optional<String> adjustmentAccountId = resolveAdjustmentAccountId();
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
            if (isInvalidAdjustmentAccount(e)) {
                LOG.warn(
                    "Zoho adjustment account {} does not exist in this org; looking up Cost of Goods Sold",
                    adjustmentAccountId.orElse("")
                );
                configuredAdjustmentAccountRejected = true;
                cachedAdjustmentAccountId = null;
                Optional<String> replacement = resolveAdjustmentAccountId();
                if (replacement.isPresent() && !replacement.orElseThrow().equals(adjustmentAccountId.orElse(null))) {
                    booksClient.createInventoryAdjustment(
                        buildQuantityAdjustmentPayload(zohoItemId, delta, unitPrice, replacement.orElseThrow(), referencePrefix)
                    );
                    return;
                }
            }
            if (isInvalidLocation(e)) {
                LOG.warn("Zoho location for stock adjustment is invalid; looking up org locations");
                configuredLocationRejected = true;
                cachedLocationId = null;
                booksClient.createInventoryAdjustment(
                    buildQuantityAdjustmentPayload(
                        zohoItemId,
                        delta,
                        unitPrice,
                        resolveAdjustmentAccountId().orElse(adjustmentAccountId.orElseThrow()),
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
        payload.put("date", java.time.LocalDate.now(java.time.ZoneId.of("Asia/Kolkata")).toString());
        payload.put("reason", "Store catalog sync");
        payload.put("adjustment_type", "quantity");
        payload.put("adjustment_account_id", adjustmentAccountId);
        payload.put("reference_number", truncatedReferenceNumber(referencePrefix));
        resolveLocationId().ifPresent(locationId -> payload.put("location_id", locationId));
        payload.put("line_items", List.of(line));
        return payload;
    }

    /**
     * Zoho rejects {@code reference_number} over 50 characters. Long variant SKUs (e.g.
     * {@code STORE-VAR-<productId>-<sku>}) can blow past that once the millisecond suffix is
     * appended, so trim the prefix rather than the timestamp — the timestamp is what keeps
     * concurrent adjustments unique.
     */
    private static String truncatedReferenceNumber(String referencePrefix) {
        String suffix = "-" + System.currentTimeMillis();
        int maxPrefixLen = 50 - suffix.length();
        String safePrefix = StringUtils.defaultString(referencePrefix);
        if (safePrefix.length() > maxPrefixLen) {
            safePrefix = safePrefix.substring(0, Math.max(0, maxPrefixLen));
        }
        return safePrefix + suffix;
    }

    private static boolean isInventoryItem(Map<String, Object> item) {
        if (item == null || item.isEmpty()) {
            return false;
        }
        String itemType = StringUtils.defaultString(stringValueStatic(item.get("item_type"))).toLowerCase();
        if ("inventory".equals(itemType)) {
            return true;
        }
        Object trackInventory = item.get("track_inventory");
        return trackInventory instanceof Boolean flag && flag;
    }

    private static String stringValueStatic(Object value) {
        return value == null ? null : value.toString();
    }

    private Optional<String> resolveAdjustmentAccountId() {
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

    private Optional<String> resolveInventoryAccountId() {
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

    private static boolean isInventoryTrackingDisabled(RestClientResponseException exception) {
        String body = StringUtils.defaultString(exception.getResponseBodyAsString()).toLowerCase();
        return exception.getStatusCode().value() == 400 && body.contains("inventory tracking") && body.contains("2024");
    }

    private static boolean isInvalidInventoryAccount(RestClientResponseException exception) {
        String body = StringUtils.defaultString(exception.getResponseBodyAsString()).toLowerCase();
        if (exception.getStatusCode().value() != 400) {
            return false;
        }
        return body.contains("2035") ||
            (body.contains("inventory tracking") && body.contains("does not exist"));
    }

    private static boolean isInvalidAdjustmentAccount(RestClientResponseException exception) {
        String body = StringUtils.defaultString(exception.getResponseBodyAsString()).toLowerCase();
        if (exception.getStatusCode().value() != 400) {
            return false;
        }
        return (body.contains("account") && body.contains("does not exist")) || body.contains("2035");
    }

    private static boolean isInvalidLocation(RestClientResponseException exception) {
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

    private Optional<String> resolveLocationId() {
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

    private boolean containsNodeId(List<Map<String, Object>> rows, String idKey, String id) {
        for (Map<String, Object> row : rows) {
            if (id.equals(stringValue(row.get(idKey)))) {
                return true;
            }
        }
        return false;
    }

    private static String formatRate(BigDecimal price) {
        if (price == null) {
            return "0";
        }
        return price.stripTrailingZeros().toPlainString();
    }

    public Map<String, Object> pullProducts() {
        ZohoSyncSummary.Builder builder = ZohoSyncSummary.builder();
        boolean autoCreateCategory = zohoSettingRepository
            .findActiveOrLatest()
            .map(setting -> Boolean.TRUE.equals(setting.getPullProductsAutoCreateCategory()))
            .orElse(applicationProperties.getZoho().isPullProductsAutoCreateCategory());
        Set<String> pulledSkus = new HashSet<>();
        List<Product> variantParents = new ArrayList<>(
            productRepository.findAll().stream().filter(variantSupport::isVariants).toList()
        );
        int page = 1;
        List<Map<String, Object>> items;
        do {
            items = booksClient.listItems(page);
            for (Map<String, Object> item : items) {
                String sku = stringValue(item.get("sku"));
                if (StringUtils.isBlank(sku)) {
                    builder.notice("missing-sku", "Zoho item has no SKU — skipped");
                    continue;
                }
                pulledSkus.add(sku.trim());
                try {
                    upsertPulledProduct(item, autoCreateCategory, builder, variantParents);
                } catch (Exception e) {
                    builder.failure(sku, e.getMessage());
                }
            }
            page++;
        } while (!items.isEmpty());
        if (!pulledSkus.isEmpty()) {
            deactivateStaleZohoProducts(pulledSkus, variantParents, builder);
        }
        finishVariantPhotoSharing(variantParents);
        return builder.build().toMap();
    }

    /**
     * Each variant SKU is imported with {@code shareEmptySizes=false} since sibling sizes of the
     * same colour may land on a later page / later item. Once every pulled item has been merged,
     * fill any size still missing its own photo from a same-colour sibling that has one.
     */
    private void finishVariantPhotoSharing(List<Product> variantParents) {
        if (variantParents == null || variantParents.isEmpty()) {
            return;
        }
        Set<Long> seen = new HashSet<>();
        for (Product parent : variantParents) {
            if (parent == null || parent.getId() == null || !seen.add(parent.getId())) {
                continue;
            }
            try {
                itemImageSync.applySameColorPhotoSharing(parent);
                productRepository.save(parent);
            } catch (Exception e) {
                LOG.warn("Could not finish variant photo sharing after Zoho pull for product {} ({}): {}", parent.getId(), parent.getSku(), e.getMessage());
            }
        }
    }

    private void upsertPulledProduct(
        Map<String, Object> item,
        boolean autoCreateCategory,
        ZohoSyncSummary.Builder builder,
        List<Product> variantParents
    ) {
        String sku = stringValue(item.get("sku"));
        Map<String, Object> zohoItemPreview = resolveZohoItemDetail(item, stringValue(item.get("item_id")));
        Optional<Product> variantParent = findVariantParentForZohoItem(zohoItemPreview, sku, variantParents);
        if (variantParent.isEmpty() && ZohoBooksItemAttributes.isVariantSku(zohoItemPreview)) {
            variantParent = Optional.of(createVariantParentFromZohoGroup(zohoItemPreview, sku, variantParents));
        }
        if (variantParent.isPresent()) {
            mergePulledZohoVariantItem(variantParent.orElseThrow(), zohoItemPreview, sku, autoCreateCategory, builder);
            return;
        }
        String itemId = stringValue(item.get("item_id"));
        Product product = productRepository
            .findOneBySku(sku)
            .or(() -> StringUtils.isNotBlank(itemId) ? productRepository.findOneByZohoItemId(itemId) : Optional.empty())
            .orElseGet(Product::new);

        boolean created = product.getId() == null;
        if (created) {
            product.setSlug(uniqueSlug(sku, stringValue(item.get("name"))));
            product.setName(stringValue(item.get("name")) != null ? stringValue(item.get("name")) : sku);
            product.setSku(sku);
            product.setCurrency("INR");
            product.setSortOrder(productRepository.findMaxSortOrder() + 1);
            product.setFeatured(false);
            product.setTrending(false);
            product.setNewArrival(false);
            product.setBestSeller(false);
            product.setStock(0);
            product.setPrice(BigDecimal.ZERO);
        }
        product.setActive(true);
        if (StringUtils.isNotBlank(itemId)) {
            product.setZohoItemId(itemId);
        }
        String name = stringValue(item.get("name"));
        if (StringUtils.isNotBlank(name)) {
            product.setName(name.trim());
        }
        Map<String, Object> zohoItem = zohoItemPreview;
        Object rate = zohoItem.get("rate");
        if (rate != null && !variantSupport.isVariants(product)) {
            product.setPrice(toBigDecimal(rate));
        }
        Object stockOnHand = zohoItem.get("stock_on_hand");
        if (stockOnHand != null && !variantSupport.isVariants(product)) {
            product.setStock(nonNegativeStock(stockOnHand));
        }
        applyPulledDescription(product, stringValue(zohoItem.get("description")));
        productFieldMapper.applyPulledItem(product, zohoItem, autoCreateCategory).ifPresent(notice -> builder.notice(sku, notice));
        if (StringUtils.isNotBlank(itemId) && itemImageSync.isEnabled()) {
            try {
                itemImageSync.importZohoItemImage(product, itemId, zohoItem);
            } catch (Exception e) {
                LOG.warn("Zoho image pull failed for {} ({}): {}", sku, itemId, e.getMessage());
            }
        }
        product.setZohoSyncStatus(ZohoSyncStatus.SYNCED);
        product.setZohoLastSyncedAt(Instant.now());
        product.setZohoSyncError(null);
        productRepository.save(product);
        if (created) {
            builder.notice(sku, "Created from Zoho pull");
        }
        builder.success();
    }

    private void mergePulledZohoVariantItem(
        Product product,
        Map<String, Object> item,
        String variantSku,
        boolean autoCreateCategory,
        ZohoSyncSummary.Builder builder
    ) {
        String itemId = stringValue(item.get("item_id"));
        Map<String, Object> zohoItem = item;
        BigDecimal rate = zohoItem.get("rate") != null ? toBigDecimal(zohoItem.get("rate")) : null;
        BigDecimal mrp = zohoItem.get("label_rate") != null ? toBigDecimal(zohoItem.get("label_rate")) : null;
        Integer stock = zohoItem.get("stock_on_hand") != null ? nonNegativeStock(zohoItem.get("stock_on_hand")) : null;
        String size = ZohoBooksItemAttributes.size(zohoItem);
        String color = ZohoBooksItemAttributes.color(zohoItem);
        if (!variantSupport.upsertPulledVariantFromZoho(
                product,
                variantSku,
                size,
                color,
                rate,
                mrp,
                stock,
                itemId,
                true
            )
        ) {
            builder.failure(variantSku, "Could not merge Zoho variant into parent " + product.getSku());
            return;
        }
        String groupId = ZohoBooksItemAttributes.groupId(zohoItem);
        if (StringUtils.isNotBlank(groupId) && StringUtils.isBlank(product.getZohoItemId())) {
            product.setZohoItemId(groupId);
        }
        String groupName = ZohoBooksItemAttributes.parentDisplayName(zohoItem);
        if (StringUtils.isNotBlank(groupName)) {
            if (StringUtils.isBlank(product.getName()) || ZohoBooksItemAttributes.hasVariantNameSuffix(product.getName())) {
                product.setName(groupName);
            }
        }
        deactivateSimpleDuplicateForVariantSku(product, variantSku, builder);
        productFieldMapper.applyPulledItem(product, zohoItem, autoCreateCategory).ifPresent(notice ->
            builder.notice(variantSku, notice)
        );
        if (StringUtils.isNotBlank(itemId)) {
            variantSupport.clearVariantImages(product, variantSku);
            if (itemImageSync.isEnabled()) {
                try {
                    itemImageSync.importZohoVariantColorImage(product, color, itemId, zohoItem, false);
                } catch (Exception e) {
                    LOG.warn("Zoho image pull failed for variant {} ({}): {}", variantSku, itemId, e.getMessage());
                }
            }
        }
        product.setZohoSyncStatus(ZohoSyncStatus.SYNCED);
        product.setZohoLastSyncedAt(Instant.now());
        product.setZohoSyncError(null);
        productRepository.save(product);
        builder.success();
    }

    /**
     * Zoho {@code GET /items} list responses omit {@code custom_fields}; detail fetch is required for sizes/colors/MRP.
     */
    private Map<String, Object> resolveZohoItemDetail(Map<String, Object> item, String itemId) {
        if (StringUtils.isBlank(itemId)) {
            return item;
        }
        try {
            Map<String, Object> detailResponse = booksClient.getItem(itemId);
            @SuppressWarnings("unchecked")
            Map<String, Object> detailItem = (Map<String, Object>) detailResponse.getOrDefault("item", detailResponse);
            if (detailItem != null && !detailItem.isEmpty()) {
                return detailItem;
            }
        } catch (Exception e) {
            LOG.debug("Could not fetch Zoho item detail for {}: {}", itemId, e.getMessage());
        }
        return item;
    }

    private Optional<Product> findVariantParentForZohoItem(
        Map<String, Object> zohoItem,
        String sku,
        List<Product> variantParents
    ) {
        Optional<Product> bySku = variantSupport.findProductContainingVariantSku(variantParents, sku);
        if (bySku.isPresent()) {
            return bySku;
        }
        String groupId = ZohoBooksItemAttributes.groupId(zohoItem);
        if (StringUtils.isNotBlank(groupId)) {
            for (Product parent : variantParents) {
                if (groupId.equals(StringUtils.trimToNull(parent.getZohoItemId()))) {
                    return Optional.of(parent);
                }
            }
            Optional<Product> byGroupId = productRepository.findOneByZohoItemId(groupId);
            if (byGroupId.isPresent()) {
                Product parent = byGroupId.orElseThrow();
                if (!variantParents.contains(parent)) {
                    variantParents.add(parent);
                }
                return Optional.of(parent);
            }
        }
        if (!ZohoBooksItemAttributes.isVariantSku(zohoItem)) {
            return Optional.empty();
        }
        String parentName = ZohoBooksItemAttributes.parentDisplayName(zohoItem);
        if (StringUtils.isNotBlank(parentName)) {
            for (Product parent : variantParents) {
                if (parentName.equalsIgnoreCase(StringUtils.trimToEmpty(parent.getName()))) {
                    return Optional.of(parent);
                }
            }
        }
        return Optional.empty();
    }

    private Product createVariantParentFromZohoGroup(
        Map<String, Object> zohoItem,
        String firstVariantSku,
        List<Product> variantParents
    ) {
        String groupId = ZohoBooksItemAttributes.groupId(zohoItem);
        String displayName = ZohoBooksItemAttributes.parentDisplayName(zohoItem);
        if (StringUtils.isBlank(displayName)) {
            displayName = firstVariantSku;
        }
        Product product = new Product();
        product.setSku(uniqueGroupSku(displayName, groupId, firstVariantSku));
        product.setName(displayName);
        product.setSlug(uniqueSlug(product.getSku(), displayName));
        product.setCurrency("INR");
        product.setSortOrder(productRepository.findMaxSortOrder() + 1);
        product.setFeatured(false);
        product.setTrending(false);
        product.setNewArrival(false);
        product.setBestSeller(false);
        product.setStock(0);
        product.setPrice(BigDecimal.ZERO);
        product.setActive(true);
        product.setItemStructure(ProductVariantSupport.STRUCTURE_VARIANTS);
        product.setVariantsJson("[]");
        if (StringUtils.isNotBlank(groupId)) {
            product.setZohoItemId(groupId);
        }
        applyPulledDescription(product, stringValue(zohoItem.get("description")));
        Product saved = productRepository.save(product);
        variantParents.add(saved);
        return saved;
    }

    private String uniqueGroupSku(String displayName, String groupId, String fallbackSku) {
        String base = slugify(displayName != null ? displayName : fallbackSku).toUpperCase(java.util.Locale.ROOT);
        if (StringUtils.isBlank(base)) {
            base = "GROUP";
        }
        String candidate = base;
        int suffix = 1;
        while (skuTakenByAnotherProduct(candidate)) {
            candidate = base + "-" + suffix++;
            if (suffix > 50 && StringUtils.isNotBlank(groupId)) {
                return "G-" + groupId;
            }
        }
        return candidate;
    }

    private boolean skuTakenByAnotherProduct(String sku) {
        return productRepository.findOneBySku(sku).isPresent();
    }

    private Product deactivateSimpleDuplicateForVariantSku(Product parent, String variantSku, ZohoSyncSummary.Builder builder) {
        Optional<Product> existing = productRepository.findOneBySku(variantSku);
        if (existing.isEmpty()) {
            return null;
        }
        Product duplicate = existing.orElseThrow();
        if (duplicate.getId() != null && parent.getId() != null && duplicate.getId().equals(parent.getId())) {
            return null;
        }
        if (variantSupport.isVariants(duplicate)) {
            return null;
        }
        if (!Boolean.TRUE.equals(duplicate.getActive())) {
            return duplicate;
        }
        duplicate.setActive(false);
        duplicate.setZohoSyncError("Superseded by Zoho item group on " + parent.getSku());
        productRepository.save(duplicate);
        builder.notice(variantSku, "Deactivated simple product — now a variant of " + parent.getSku());
        return duplicate;
    }

    private String mergeJsonUrlArrays(String first, String second) {
        LinkedHashSet<String> urls = new LinkedHashSet<>();
        urls.addAll(parseJsonStringArray(first));
        urls.addAll(parseJsonStringArray(second));
        if (urls.isEmpty()) {
            return first != null ? first : second;
        }
        try {
            return objectMapper.writeValueAsString(urls);
        } catch (Exception e) {
            return first;
        }
    }

    private List<String> parseJsonStringArray(String json) {
        List<String> out = new ArrayList<>();
        if (StringUtils.isBlank(json)) {
            return out;
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root == null || !root.isArray()) {
                return out;
            }
            for (JsonNode node : root) {
                if (node != null && node.isTextual() && StringUtils.isNotBlank(node.asText())) {
                    out.add(node.asText().trim());
                }
            }
        } catch (Exception ignored) {
            return out;
        }
        return out;
    }

    private String mergeColorImageMaps(String first, String second) {
        LinkedHashMap<String, List<String>> merged = new LinkedHashMap<>();
        merged.putAll(parseJsonStringListMap(first));
        for (Map.Entry<String, List<String>> entry : parseJsonStringListMap(second).entrySet()) {
            merged.merge(entry.getKey(), entry.getValue(), (a, b) -> {
                LinkedHashSet<String> urls = new LinkedHashSet<>(a);
                urls.addAll(b);
                return new ArrayList<>(urls);
            });
        }
        if (merged.isEmpty()) {
            return first != null ? first : second;
        }
        try {
            return objectMapper.writeValueAsString(merged);
        } catch (Exception e) {
            return first;
        }
    }

    private Map<String, List<String>> parseJsonStringListMap(String json) {
        LinkedHashMap<String, List<String>> out = new LinkedHashMap<>();
        if (StringUtils.isBlank(json)) {
            return out;
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root == null || !root.isObject()) {
                return out;
            }
            var fields = root.fields();
            while (fields.hasNext()) {
                var entry = fields.next();
                if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null || !entry.getValue().isArray()) {
                    continue;
                }
                List<String> urls = new ArrayList<>();
                for (JsonNode node : entry.getValue()) {
                    if (node != null && node.isTextual() && StringUtils.isNotBlank(node.asText())) {
                        urls.add(node.asText().trim());
                    }
                }
                if (!urls.isEmpty()) {
                    out.put(entry.getKey().trim(), urls);
                }
            }
        } catch (Exception ignored) {
            return out;
        }
        return out;
    }

    private String mergeColorsJsonPreferringRich(String parentJson, String duplicateJson) {
        if (StringUtils.isBlank(duplicateJson)) {
            return parentJson;
        }
        if (StringUtils.isBlank(parentJson)) {
            return duplicateJson;
        }
        try {
            JsonNode parentRoot = objectMapper.readTree(parentJson);
            JsonNode duplicateRoot = objectMapper.readTree(duplicateJson);
            if (duplicateRoot == null || !duplicateRoot.isArray()) {
                return parentJson;
            }
            LinkedHashMap<String, JsonNode> byName = new LinkedHashMap<>();
            indexColorNodes(parentRoot, byName, false);
            indexColorNodes(duplicateRoot, byName, true);
            return objectMapper.writeValueAsString(byName.values());
        } catch (Exception e) {
            return parentJson;
        }
    }

    private void indexColorNodes(JsonNode root, LinkedHashMap<String, JsonNode> byName, boolean preferRich) {
        if (root == null || !root.isArray()) {
            return;
        }
        for (JsonNode node : root) {
            if (node == null) {
                continue;
            }
            String name;
            boolean rich = node.isObject();
            if (rich) {
                name = node.path("name").asText("");
            } else if (node.isTextual()) {
                name = node.asText();
            } else {
                continue;
            }
            if (StringUtils.isBlank(name)) {
                continue;
            }
            String key = name.trim().toLowerCase(java.util.Locale.ROOT);
            JsonNode existing = byName.get(key);
            if (existing == null || (preferRich && rich && !existing.isObject())) {
                byName.put(key, node);
            } else if (preferRich && rich && existing.isObject()) {
                byName.put(key, node);
            }
        }
    }

    private void applyPulledDescription(Product product, String rawDescription) {
        if (StringUtils.isBlank(rawDescription)) {
            return;
        }
        ProductDescriptionCodec.Decoded decoded = descriptionCodec.decode(rawDescription);
        descriptionCodec.applyDecoded(product, decoded);
        if (decoded.bodyDescription() != null && !decoded.bodyDescription().isBlank()) {
            product.setDescription(decoded.bodyDescription().trim());
        } else if (
            decoded.categoryName() != null ||
            decoded.fabric() != null ||
            !decoded.sizes().isEmpty() ||
            !decoded.colors().isEmpty() ||
            decoded.story() != null
        ) {
            product.setDescription(null);
        } else {
            product.setDescription(rawDescription.trim());
        }
    }

    private void deactivateStaleZohoProducts(
        Set<String> pulledSkus,
        List<Product> variantParents,
        ZohoSyncSummary.Builder builder
    ) {
        for (Product product : productRepository.findAllByZohoItemIdIsNotNull()) {
            String sku = StringUtils.trimToNull(product.getSku());
            if (sku == null || pulledSkus.contains(sku) || !Boolean.TRUE.equals(product.getActive())) {
                continue;
            }
            if (variantSupport.isVariants(product)) {
                continue;
            }
            product.setActive(false);
            product.setZohoSyncError("No longer in Zoho catalog (deactivated on pull)");
            productRepository.save(product);
            builder.notice(sku, "Deactivated — removed from Zoho");
        }
        for (Product product : variantParents) {
            if (!Boolean.TRUE.equals(product.getActive())) {
                continue;
            }
            if (variantSupport.anyVariantSkuInSet(product, pulledSkus)) {
                continue;
            }
            String sku = StringUtils.trimToNull(product.getSku());
            product.setActive(false);
            product.setZohoSyncError("No variant SKUs remain in Zoho catalog (deactivated on pull)");
            productRepository.save(product);
            if (sku != null) {
                builder.notice(sku, "Deactivated — variant SKUs removed from Zoho");
            }
        }
    }

    public Map<String, Object> pullInventory() {
        ZohoSyncSummary.Builder builder = ZohoSyncSummary.builder();
        List<Product> variantParents = productRepository
            .findAll()
            .stream()
            .filter(variantSupport::isVariants)
            .toList();
        try {
            int page = 1;
            List<Map<String, Object>> items;
            do {
                items = booksClient.listItems(page);
                for (Map<String, Object> item : items) {
                    String sku = stringValue(item.get("sku"));
                    String itemId = stringValue(item.get("item_id"));
                    try {
                        Optional<Product> product = StringUtils.isNotBlank(itemId)
                            ? productRepository.findOneByZohoItemId(itemId)
                            : Optional.empty();
                        if (product.isEmpty() && StringUtils.isNotBlank(sku)) {
                            product = productRepository.findOneBySku(sku);
                        }
                        if (product.isPresent()) {
                            Product p = product.orElseThrow();
                            if (variantSupport.isVariants(p) && sku != null && sku.equals(StringUtils.trimToNull(p.getSku()))) {
                                builder.success();
                                continue;
                            }
                            p.setStock(nonNegativeStock(item.get("stock_on_hand")));
                            productRepository.save(p);
                            builder.success();
                        } else if (sku != null) {
                            Optional<Product> byVariant = variantSupport.findProductContainingVariantSku(variantParents, sku);
                            if (byVariant.isPresent()) {
                                Product p = byVariant.orElseThrow();
                                if (variantSupport.updateVariantStockBySku(p, sku, nonNegativeStock(item.get("stock_on_hand")))) {
                                    productRepository.save(p);
                                    builder.success();
                                } else {
                                    builder.failure(sku, "Variant row not found on parent " + p.getSku());
                                }
                            } else {
                                builder.skipped(sku, "No linked store product for SKU or Zoho item id");
                            }
                        } else {
                            builder.skipped(itemId, "No linked store product for SKU or Zoho item id");
                        }
                    } catch (Exception e) {
                        builder.failure(sku != null ? sku : itemId, e.getMessage());
                    }
                }
                page++;
            } while (!items.isEmpty());
        } catch (RestClientResponseException ex) {
            String message = "Zoho inventory pull failed (HTTP " + ex.getStatusCode().value() + "): " + StringUtils.defaultString(ex.getStatusText());
            LOG.warn(message);
            return ZohoSyncSummary.builder().failure(message).build().toMap();
        } catch (ResourceAccessException ex) {
            String message = "Zoho unreachable: " + ex.getMessage();
            LOG.warn(message);
            return ZohoSyncSummary.builder().failure(message).build().toMap();
        }
        return builder.build().toMap();
    }

    public void syncOrder(Long orderId) {
        customerOrderRepository
            .findById(orderId)
            .ifPresent(order -> {
                try {
                    String customerId = resolveOrCreateContact(order);
                    String salesOrderId = order.getZohoSalesOrderId();
                    if (StringUtils.isBlank(salesOrderId)) {
                        Map<String, Object> salesOrderResponse = booksClient.createSalesOrder(buildSalesOrderPayload(order, customerId));
                        @SuppressWarnings("unchecked")
                        Map<String, Object> salesOrder = (Map<String, Object>) salesOrderResponse.getOrDefault(
                            "salesorder",
                            salesOrderResponse
                        );
                        salesOrderId = stringValue(salesOrder.get("salesorder_id"));
                        order.setZohoSalesOrderId(salesOrderId);
                        applySalesOrderAddresses(salesOrderId, order);
                    }
                    if (StringUtils.isBlank(order.getZohoInvoiceId()) && StringUtils.isNotBlank(salesOrderId)) {
                        Map<String, Object> invoiceResponse = booksClient.createInvoiceFromSalesOrder(salesOrderId);
                        @SuppressWarnings("unchecked")
                        Map<String, Object> invoice = (Map<String, Object>) invoiceResponse.getOrDefault("invoice", invoiceResponse);
                        String invoiceId = stringValue(invoice.get("invoice_id"));
                        if (StringUtils.isNotBlank(invoiceId)) {
                            order.setZohoInvoiceId(invoiceId);
                            finalizeInvoiceForInventory(invoiceId);
                        }
                    }
                    if (isCodOrder(order) && order.getPaymentStatus() == PaymentStatus.PAID) {
                        recordCodPaymentIfNeeded(order, customerId);
                    }
                    order.setZohoSyncStatus(ZohoSyncStatus.SYNCED);
                    order.setZohoLastSyncedAt(Instant.now());
                    order.setZohoSyncError(null);
                    customerOrderRepository.save(order);
                } catch (Exception e) {
                    LOG.warn("Zoho order sync failed for {}: {}", orderId, e.getMessage());
                    order.setZohoSyncStatus(ZohoSyncStatus.FAILED);
                    order.setZohoSyncError(e.getMessage());
                    customerOrderRepository.save(order);
                }
            });
    }

    private String resolveOrCreateContact(CustomerOrder order) {
        JsonNode shippingAddress = parseShippingAddress(order.getShippingAddressJson());
        String login = StringUtils.trimToNull(order.getUserLogin());
        CustomerProfile profile = login != null ? customerProfileRepository.findOneByUserLogin(login).orElse(null) : null;

        String email = firstValidEmail(textFromJson(shippingAddress, "email"), login);
        if (email != null) {
            try {
                Map<String, Object> existing = booksClient.findContactByEmail(email);
                if (existing != null) {
                    String contactId = stringValue(existing.get("contact_id"));
                    if (StringUtils.isNotBlank(contactId)) {
                        return contactId;
                    }
                }
            } catch (RestClientResponseException e) {
                if (!isInvalidEmailError(e)) {
                    throw e;
                }
                LOG.debug("Skipping Zoho contact lookup for invalid email {}: {}", email, e.getMessage());
            }
        }

        String contactName = resolveZohoContactName(shippingAddress, profile, email, login, order.getOrderNumber());
        Map<String, Object> existingByName = findContactByNameIfAllowed(contactName);
        if (existingByName != null) {
            return stringValue(existingByName.get("contact_id"));
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("contact_name", contactName);
        payload.put("contact_type", "customer");
        Map<String, Object> contactPerson = new LinkedHashMap<>();
        String contactPersonName = firstNonBlank(
            validEmail(textFromJson(shippingAddress, "full_name")) == null ? textFromJson(shippingAddress, "full_name") : null,
            contactName
        );
        contactPerson.put("first_name", contactPersonName);
        if (email != null) {
            contactPerson.put("email", email);
        }
        String phone = firstNonBlank(textFromJson(shippingAddress, "phone"), profile != null ? profile.getPhone() : null);
        if (StringUtils.isNotBlank(phone)) {
            contactPerson.put("phone", phone);
        }
        payload.put("contact_persons", List.of(contactPerson));
        try {
            Map<String, Object> response = booksClient.createContact(payload);
            @SuppressWarnings("unchecked")
            Map<String, Object> contact = (Map<String, Object>) response.getOrDefault("contact", response);
            return stringValue(contact.get("contact_id"));
        } catch (RestClientResponseException e) {
            if (isDuplicateContactNameError(e)) {
                Map<String, Object> duplicate = findContactByNameIfAllowed(contactName);
                if (duplicate != null) {
                    return stringValue(duplicate.get("contact_id"));
                }
            }
            throw e;
        }
    }

    private Map<String, Object> buildSalesOrderPayload(CustomerOrder order, String customerId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("customer_id", customerId);
        payload.put("reference_number", order.getOrderNumber());
        payload.put("line_items", buildOrderLineItems(order, null));
        BigDecimal shipping = moneyOrZero(order.getShipping());
        if (shipping.compareTo(BigDecimal.ZERO) > 0) {
            payload.put("shipping_charge", shipping);
        }
        BigDecimal discount = moneyOrZero(order.getDiscount());
        if (discount.compareTo(BigDecimal.ZERO) > 0) {
            payload.put("discount", discount);
            payload.put("discount_type", "entity_level");
            payload.put("is_discount_before_tax", false);
        }
        if (isCodOrder(order)) {
            payload.put("payment_terms_label", COD_PAYMENT_TERMS_LABEL);
            payload.put("notes", "Payment method: Cash on Delivery (COD). Collect payment on delivery.");
        }
        return payload;
    }

    private void applySalesOrderAddresses(String salesOrderId, CustomerOrder order) {
        if (StringUtils.isBlank(salesOrderId)) {
            return;
        }
        Map<String, Object> zohoAddress = buildZohoAddress(parseShippingAddress(order.getShippingAddressJson()));
        if (zohoAddress == null || zohoAddress.isEmpty()) {
            return;
        }
        booksClient.updateSalesOrderBillingAddress(salesOrderId, zohoAddress);
        booksClient.updateSalesOrderShippingAddress(salesOrderId, zohoAddress);
    }

    /**
     * Maps checkout shipping address JSON to Zoho Books address fields. Applied as one-off order
     * addresses after sales order creation so they do not overwrite the customer's saved profile.
     */
    private Map<String, Object> buildZohoAddress(JsonNode shippingAddress) {
        if (shippingAddress == null || shippingAddress.isMissingNode()) {
            return null;
        }
        String line1 = textFromJson(shippingAddress, "line1");
        String city = textFromJson(shippingAddress, "city");
        String state = textFromJson(shippingAddress, "state");
        String pincode = textFromJson(shippingAddress, "pincode");
        if (StringUtils.isAllBlank(line1, city, state, pincode)) {
            return null;
        }
        Map<String, Object> address = new LinkedHashMap<>();
        String fullName = textFromJson(shippingAddress, "full_name");
        if (StringUtils.isNotBlank(fullName)) {
            address.put("attention", fullName);
        }
        if (StringUtils.isNotBlank(line1)) {
            address.put("address", line1);
        }
        String line2 = textFromJson(shippingAddress, "line2");
        if (StringUtils.isNotBlank(line2)) {
            address.put("street2", line2);
        }
        if (StringUtils.isNotBlank(city)) {
            address.put("city", city);
        }
        if (StringUtils.isNotBlank(state)) {
            address.put("state", state);
        }
        if (StringUtils.isNotBlank(pincode)) {
            address.put("zip", pincode);
        }
        address.put("country", "India");
        String phone = textFromJson(shippingAddress, "phone");
        if (StringUtils.isNotBlank(phone)) {
            address.put("phone", phone);
        }
        return address;
    }

    private String resolvePlaceOfSupply(String state) {
        if (StringUtils.isBlank(state)) {
            return null;
        }
        String trimmed = state.trim();
        if (trimmed.length() == 2 && IndiaPlaceOfSupplyCodes.containsValue(trimmed.toUpperCase(java.util.Locale.ROOT))) {
            return trimmed.toUpperCase(java.util.Locale.ROOT);
        }
        String normalized = trimmed.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z]", "");
        return IndiaPlaceOfSupplyCodes.lookup(normalized);
    }

    private List<Map<String, Object>> buildOrderLineItems(CustomerOrder order, Map<String, Map<String, Object>> invoiceLinesByItemId) {
        List<Map<String, Object>> lineItems = new ArrayList<>();
        List<OrderItem> items = orderItemRepository.findAllByOrderId(order.getId());
        if (items.isEmpty()) {
            items = new ArrayList<>(order.getItemses());
        }
        for (OrderItem item : items) {
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("name", item.getProductName());
            line.put("rate", item.getPrice());
            line.put("quantity", item.getQty());
            Optional<String> zohoItemId = resolveZohoItemId(item);
            zohoItemId.ifPresent(id -> line.put("item_id", id));
            if (invoiceLinesByItemId != null && zohoItemId.isPresent()) {
                Map<String, Object> invoiceLine = invoiceLinesByItemId.get(zohoItemId.orElseThrow());
                if (invoiceLine != null) {
                    String invoiceItemId = firstNonBlank(
                        stringValue(invoiceLine.get("line_item_id")),
                        stringValue(invoiceLine.get("invoice_item_id"))
                    );
                    if (StringUtils.isNotBlank(invoiceItemId)) {
                        line.put("invoice_item_id", invoiceItemId);
                    }
                }
            }
            lineItems.add(line);
        }
        BigDecimal codFee = moneyOrZero(order.getCodFee());
        if (codFee.compareTo(BigDecimal.ZERO) > 0) {
            Map<String, Object> codLine = new LinkedHashMap<>();
            codLine.put("name", COD_FEE_LINE_NAME);
            codLine.put("rate", codFee);
            codLine.put("quantity", 1);
            lineItems.add(codLine);
        }
        return lineItems;
    }

    private void recordCodPaymentIfNeeded(CustomerOrder order, String customerId) {
        String invoiceId = StringUtils.trimToNull(order.getZohoInvoiceId());
        if (invoiceId == null) {
            return;
        }
        if (isInvoiceFullyPaid(invoiceId)) {
            LOG.debug("Zoho invoice {} already paid for COD order {}", invoiceId, order.getOrderNumber());
            return;
        }
        BigDecimal amount = moneyOrZero(order.getTotal());
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("customer_id", customerId);
        payload.put("payment_mode", "cash");
        payload.put("amount", amount);
        payload.put("date", LocalDate.now(INDIA).toString());
        payload.put("reference_number", order.getOrderNumber());
        payload.put("description", COD_PAYMENT_TERMS_LABEL);
        Map<String, Object> invoicePayment = new LinkedHashMap<>();
        invoicePayment.put("invoice_id", invoiceId);
        invoicePayment.put("amount_applied", amount);
        payload.put("invoices", List.of(invoicePayment));
        try {
            booksClient.createCustomerPayment(payload);
            LOG.debug("Recorded Zoho cash payment for COD order {}", order.getOrderNumber());
        } catch (RuntimeException e) {
            LOG.warn("Could not record Zoho COD payment for {}: {}", order.getOrderNumber(), e.getMessage());
        }
    }

    private boolean isInvoiceFullyPaid(String invoiceId) {
        try {
            Map<String, Object> response = booksClient.getInvoice(invoiceId);
            @SuppressWarnings("unchecked")
            Map<String, Object> invoice = (Map<String, Object>) response.getOrDefault("invoice", response);
            String status = StringUtils.lowerCase(StringUtils.trimToEmpty(stringValue(invoice.get("status"))));
            if ("paid".equals(status)) {
                return true;
            }
            BigDecimal balance = toBigDecimal(invoice.get("balance"));
            return balance.compareTo(BigDecimal.ZERO) <= 0;
        } catch (RuntimeException e) {
            LOG.debug("Could not read Zoho invoice {} payment status: {}", invoiceId, e.getMessage());
            return false;
        }
    }

    public static boolean isCodOrder(CustomerOrder order) {
        return "cod".equalsIgnoreCase(StringUtils.trimToEmpty(order.getPaymentMethod()));
    }

    private static BigDecimal moneyOrZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value.setScale(2, RoundingMode.HALF_UP);
    }

    private void finalizeInvoiceForInventory(String invoiceId) {
        if (!shouldAdjustZohoInventory() || StringUtils.isBlank(invoiceId)) {
            return;
        }
        try {
            booksClient.markInvoiceAsSent(invoiceId);
            LOG.debug("Marked Zoho invoice {} as sent for inventory deduction", invoiceId);
        } catch (RuntimeException e) {
            LOG.warn("Could not mark Zoho invoice {} as sent (stock may not decrement): {}", invoiceId, e.getMessage());
        }
    }

    private boolean shouldAdjustZohoInventory() {
        return applicationProperties.getZoho().isSyncProductStock() && resolveInventoryAccountId().isPresent();
    }

    private Optional<String> resolveZohoItemId(OrderItem item) {
        Product product = resolveOrderProduct(item).orElse(null);
        String variantSku = StringUtils.trimToNull(item.getVariantSku());
        if (product != null && variantSupport.isVariants(product)) {
            String lineSku = variantSku;
            if (lineSku == null) {
                Optional<ProductVariantSupport.VariantRow> resolved = variantSupport.resolveSellable(
                    product,
                    null,
                    item.getItemSize(),
                    item.getColor()
                );
                if (resolved.isPresent()) {
                    lineSku = resolved.orElseThrow().sku();
                }
            }
            if (StringUtils.isNotBlank(lineSku)) {
                Optional<String> cached = variantSupport.zohoItemIdForSku(product, lineSku);
                if (cached.isPresent()) {
                    return cached;
                }
                try {
                    Map<String, Object> zohoItem = booksClient.findItemBySku(lineSku);
                    if (zohoItem != null) {
                        String itemId = stringValue(zohoItem.get("item_id"));
                        if (StringUtils.isNotBlank(itemId)) {
                            variantSupport.setVariantZohoItemId(product, lineSku, itemId);
                            productRepository.save(product);
                            return Optional.of(itemId.trim());
                        }
                    }
                } catch (RuntimeException e) {
                    LOG.warn("Zoho item lookup failed for variant SKU {}: {}", lineSku, e.getMessage());
                }
            }
            LOG.warn(
                "No Zoho item found for VARIANTS order line (parent={}, variantSku={}, size={}, color={})",
                product.getSku(),
                variantSku,
                item.getItemSize(),
                item.getColor()
            );
            return Optional.empty();
        }
        if (product != null && StringUtils.isNotBlank(product.getZohoItemId())) {
            return Optional.of(product.getZohoItemId().trim());
        }
        String sku = resolveLineSku(item, product);
        if (StringUtils.isBlank(sku)) {
            return Optional.empty();
        }
        try {
            Map<String, Object> zohoItem = booksClient.findItemBySku(sku);
            if (zohoItem == null) {
                LOG.warn("No Zoho item found for order line SKU {} ({})", sku, item.getProductName());
                return Optional.empty();
            }
            String itemId = stringValue(zohoItem.get("item_id"));
            if (StringUtils.isBlank(itemId)) {
                return Optional.empty();
            }
            if (product != null && StringUtils.isBlank(product.getZohoItemId()) && !variantSupport.isVariants(product)) {
                product.setZohoItemId(itemId);
                productRepository.save(product);
            }
            return Optional.of(itemId);
        } catch (RuntimeException e) {
            LOG.warn("Zoho item lookup failed for SKU {}: {}", sku, e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<Product> resolveOrderProduct(OrderItem item) {
        String productId = StringUtils.trimToNull(item.getProductId());
        if (productId == null) {
            return Optional.empty();
        }
        try {
            return productRepository.findById(Long.parseLong(productId));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private String resolveLineSku(OrderItem item, Product product) {
        String variantSku = StringUtils.trimToNull(item.getVariantSku());
        if (variantSku != null) {
            return variantSku;
        }
        if (product != null) {
            if (variantSupport.isVariants(product)) {
                Optional<ProductVariantSupport.VariantRow> variant = variantSupport.resolveSellable(
                    product,
                    null,
                    item.getItemSize(),
                    item.getColor()
                );
                if (variant.isPresent()) {
                    return variant.orElseThrow().sku();
                }
                throw new IllegalStateException(
                    "Order line missing variantSku for VARIANTS product " + product.getSku()
                );
            }
            if (StringUtils.isNotBlank(product.getSku())) {
                return product.getSku().trim();
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Map<String, Object>> indexInvoiceLinesByItemId(String invoiceId) {
        Map<String, Map<String, Object>> indexed = new LinkedHashMap<>();
        if (StringUtils.isBlank(invoiceId)) {
            return indexed;
        }
        try {
            Map<String, Object> response = booksClient.getInvoice(invoiceId);
            Map<String, Object> invoice = (Map<String, Object>) response.getOrDefault("invoice", response);
            Object rawLines = invoice.get("line_items");
            if (!(rawLines instanceof List<?> lines)) {
                return indexed;
            }
            for (Object lineObj : lines) {
                if (!(lineObj instanceof Map<?, ?> line)) {
                    continue;
                }
                String itemId = stringValue(line.get("item_id"));
                if (StringUtils.isNotBlank(itemId)) {
                    indexed.put(itemId, (Map<String, Object>) line);
                }
            }
        } catch (RuntimeException e) {
            LOG.warn("Could not load Zoho invoice {} for credit note line mapping: {}", invoiceId, e.getMessage());
        }
        return indexed;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.isNotBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String resolveZohoContactName(
        JsonNode shippingAddress,
        CustomerProfile profile,
        String email,
        String login,
        String orderNumber
    ) {
        String rawFullName = textFromJson(shippingAddress, "full_name");
        String preferredName = validEmail(rawFullName) == null ? rawFullName : null;
        return firstNonBlank(preferredName, profile != null ? profile.getFullName() : null, orderNumber, email, login);
    }

    /**
     * Zoho rejects some values in {@code contact_name_contains} (e.g. emails with {@code @}).
     */
    private Map<String, Object> findContactByNameIfAllowed(String contactName) {
        if (StringUtils.isBlank(contactName) || validEmail(contactName) != null) {
            return null;
        }
        try {
            return booksClient.findContactByName(contactName);
        } catch (RestClientResponseException e) {
            if (isInvalidContactNameLookupError(e)) {
                LOG.debug("Skipping Zoho contact name lookup for {}: {}", contactName, e.getMessage());
                return null;
            }
            throw e;
        }
    }

    private static String firstValidEmail(String... candidates) {
        for (String candidate : candidates) {
            String valid = validEmail(candidate);
            if (valid != null) {
                return valid;
            }
        }
        return null;
    }

    private static String validEmail(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        String trimmed = value.trim();
        return EMAIL_PATTERN.matcher(trimmed).matches() ? trimmed : null;
    }

    private static boolean isDuplicateContactNameError(RestClientResponseException exception) {
        String body = StringUtils.defaultString(exception.getResponseBodyAsString()).toLowerCase();
        return body.contains("3062") && body.contains("already exists");
    }

    private static boolean isInvalidEmailError(RestClientResponseException exception) {
        String body = StringUtils.defaultString(exception.getResponseBodyAsString()).toLowerCase();
        return body.contains("invalid value passed for email");
    }

    private static boolean isInvalidContactNameLookupError(RestClientResponseException exception) {
        String body = StringUtils.defaultString(exception.getResponseBodyAsString()).toLowerCase();
        return body.contains("invalid value passed for contact_name_contains");
    }

    private JsonNode parseShippingAddress(String shippingAddressJson) {
        if (StringUtils.isBlank(shippingAddressJson)) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(shippingAddressJson);
        } catch (Exception e) {
            LOG.debug("Could not parse shipping address JSON for Zoho contact: {}", e.getMessage());
            return objectMapper.createObjectNode();
        }
    }

    private static String textFromJson(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || !node.hasNonNull(field)) {
            return null;
        }
        String value = node.path(field).asText();
        return StringUtils.isBlank(value) ? null : value.trim();
    }

    public Map<String, Object> retryFailedOrders() {
        ZohoSyncSummary.Builder builder = ZohoSyncSummary.builder();
        Page<CustomerOrder> paidPage = customerOrderRepository.findPaidOrdersForZohoRetry(
            PaymentStatus.PAID,
            List.of(ZohoSyncStatus.FAILED, ZohoSyncStatus.PENDING),
            PageRequest.of(0, 200)
        );
        for (CustomerOrder order : paidPage.getContent()) {
            try {
                syncOrder(order.getId());
                builder.success();
            } catch (Exception e) {
                builder.orderFailure(order.getOrderNumber(), e.getMessage());
            }
        }
        Page<CustomerOrder> codPage = customerOrderRepository.findConfirmedCodOrdersForZohoRetry(
            List.of(ZohoSyncStatus.FAILED, ZohoSyncStatus.PENDING),
            PageRequest.of(0, 200)
        );
        for (CustomerOrder order : codPage.getContent()) {
            try {
                syncOrder(order.getId());
                builder.success();
            } catch (Exception e) {
                builder.orderFailure(order.getOrderNumber(), e.getMessage());
            }
        }
        return builder.build().toMap();
    }

    /**
     * Pulls shipment date, carrier, tracking number, and fulfillment status from the linked Zoho
     * Books sales order (and delivery challans when present).
     */
    public void pullOrderTracking(Long orderId) {
        customerOrderRepository
            .findById(orderId)
            .ifPresent(order -> {
                String salesOrderId = StringUtils.trimToNull(order.getZohoSalesOrderId());
                if (salesOrderId == null) {
                    pullInvoiceStatusOnly(order);
                    return;
                }
                try {
                    Map<String, Object> response = booksClient.getSalesOrder(salesOrderId);
                    @SuppressWarnings("unchecked")
                    Map<String, Object> salesOrder = (Map<String, Object>) response.getOrDefault("salesorder", response);
                    if (salesOrder == null || salesOrder.isEmpty()) {
                        LOG.warn("Zoho sales order {} not found for order {}", salesOrderId, orderId);
                        return;
                    }
                    ZohoOrderStatus.applySalesOrderTracking(order, salesOrder);
                    try {
                        ZohoOrderStatus.applyDeliveryChallans(order, booksClient.listDeliveryChallans(salesOrderId));
                    } catch (RuntimeException e) {
                        LOG.debug("Could not fetch Zoho delivery challans for order {}: {}", orderId, e.getMessage());
                    }
                    order.setZohoTrackingLastPulledAt(Instant.now());
                    customerOrderRepository.save(order);
                } catch (Exception e) {
                    LOG.warn("Zoho order tracking pull failed for {}: {}", orderId, e.getMessage());
                }
            });
    }

    private void pullInvoiceStatusOnly(CustomerOrder order) {
        if (StringUtils.isBlank(order.getZohoInvoiceId())) {
            return;
        }
        try {
            Map<String, Object> response = booksClient.getInvoice(order.getZohoInvoiceId());
            @SuppressWarnings("unchecked")
            Map<String, Object> invoice = (Map<String, Object>) response.getOrDefault("invoice", response);
            String status = stringValue(invoice.get("status"));
            if (StringUtils.isNotBlank(status)) {
                order.setZohoOrderStatus(status);
            }
            order.setZohoTrackingLastPulledAt(Instant.now());
            customerOrderRepository.save(order);
        } catch (Exception e) {
            LOG.warn("Zoho invoice status pull failed for {}: {}", order.getId(), e.getMessage());
        }
    }

    public Map<String, Object> pullAllTracking() {
        ZohoSyncSummary.Builder builder = ZohoSyncSummary.builder();
        Page<CustomerOrder> page = customerOrderRepository.findOrdersForZohoTrackingPull(
            PaymentStatus.PAID,
            List.of(OrderStatus.PENDING, OrderStatus.CONFIRMED),
            PageRequest.of(0, 50)
        );
        for (CustomerOrder order : page.getContent()) {
            try {
                pullOrderTracking(order.getId());
                builder.success();
            } catch (Exception e) {
                builder.orderFailure(order.getOrderNumber(), e.getMessage());
            }
        }
        builder.notice("batch", "Pulled Zoho shipment status for " + page.getNumberOfElements() + " open paid orders (max 50)");
        return builder.build().toMap();
    }

    public Optional<String> createCreditNote(Long orderId, String returnNumber) {
        Optional<CustomerOrder> orderOpt = customerOrderRepository.findById(orderId);
        if (orderOpt.isEmpty()) {
            return Optional.empty();
        }
        CustomerOrder order = orderOpt.orElseThrow();
        String customerId = resolveOrCreateContact(order);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("customer_id", customerId);
        payload.put("reference_number", returnNumber);
        String invoiceId = StringUtils.trimToNull(order.getZohoInvoiceId());
        if (invoiceId != null) {
            payload.put("invoice_id", invoiceId);
        }
        Map<String, Map<String, Object>> invoiceLinesByItemId = indexInvoiceLinesByItemId(invoiceId);
        payload.put("line_items", buildOrderLineItems(order, invoiceLinesByItemId));
        Map<String, Object> response = booksClient.createCreditNote(payload);
        @SuppressWarnings("unchecked")
        Map<String, Object> creditNote = (Map<String, Object>) response.getOrDefault("creditnote", response);
        String creditNoteId = stringValue(creditNote.get("creditnote_id"));
        if (StringUtils.isNotBlank(creditNoteId) && shouldAdjustZohoInventory()) {
            try {
                booksClient.markCreditNoteAsOpen(creditNoteId);
                LOG.debug("Opened Zoho credit note {} for inventory return", creditNoteId);
            } catch (RuntimeException e) {
                LOG.warn("Could not open Zoho credit note {} (stock may not increment): {}", creditNoteId, e.getMessage());
            }
        }
        return Optional.ofNullable(creditNoteId).filter(StringUtils::isNotBlank);
    }

    private static final java.util.regex.Pattern NON_SLUG = java.util.regex.Pattern.compile("[^a-z0-9]+");

    private String uniqueSlug(String sku, String name) {
        String base = slugify(name != null && !name.isBlank() ? name : sku);
        String candidate = base;
        int suffix = 1;
        while (productRepository.findOneBySlug(candidate).isPresent()) {
            candidate = base + "-" + suffix++;
        }
        return candidate;
    }

    private static String slugify(String value) {
        if (value == null || value.isBlank()) {
            return "item";
        }
        String normalized = NON_SLUG.matcher(value.toLowerCase(java.util.Locale.ROOT).trim()).replaceAll("-");
        normalized = normalized.replaceAll("^-+|-+$", "");
        return normalized.isBlank() ? "item" : normalized;
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        return new BigDecimal(value.toString());
    }

    private int toInteger(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return new BigDecimal(value.toString()).intValue();
    }

    private int nonNegativeStock(Object value) {
        return Math.max(0, toInteger(value));
    }
}
