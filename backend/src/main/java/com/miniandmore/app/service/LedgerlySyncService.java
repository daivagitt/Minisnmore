package com.miniandmore.app.service;

import com.miniandmore.app.config.ApplicationProperties;
import com.miniandmore.app.client.LedgerlyIntegrationClient;
import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.CustomerProfile;
import com.miniandmore.app.domain.LedgerlySetting;
import com.miniandmore.app.domain.OrderItem;
import com.miniandmore.app.domain.Product;
import com.miniandmore.app.domain.enumeration.LedgerlySyncStatus;
import com.miniandmore.app.domain.enumeration.OrderStatus;
import com.miniandmore.app.domain.enumeration.PaymentStatus;
import com.miniandmore.app.repository.CustomerOrderRepository;
import com.miniandmore.app.repository.CustomerProfileRepository;
import com.miniandmore.app.repository.LedgerlySettingRepository;
import com.miniandmore.app.repository.ProductRepository;
import com.miniandmore.app.repository.SiteSettingRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.client.RestClientResponseException;

@Service
@Transactional
public class LedgerlySyncService {

    private static final Logger LOG = LoggerFactory.getLogger(LedgerlySyncService.class);

    /**
     * GST place-of-supply codes Ledgerly (Zoho Books) expects for the "in" (India) edition.
     * Keyed by normalized (lowercase, no spaces/punctuation) state/UT name; a few common aliases
     * (old names, alternate spellings) are included since checkout state is free-text.
     */
    private static final Map<String, String> INDIA_PLACE_OF_SUPPLY_CODES = buildIndiaPlaceOfSupplyCodes();

    private static Map<String, String> buildIndiaPlaceOfSupplyCodes() {
        Map<String, String> codes = new HashMap<>();
        codes.put("andamanandnicobarislands", "AN");
        codes.put("andhrapradesh", "AP");
        codes.put("arunachalpradesh", "AR");
        codes.put("assam", "AS");
        codes.put("bihar", "BR");
        codes.put("chandigarh", "CH");
        codes.put("chhattisgarh", "CT");
        codes.put("dadraandnagarhaveliaanddamananddiu", "DN");
        codes.put("dadraandnagarhaveli", "DN");
        codes.put("damananddiu", "DD");
        codes.put("delhi", "DL");
        codes.put("newdelhi", "DL");
        codes.put("goa", "GA");
        codes.put("gujarat", "GJ");
        codes.put("haryana", "HR");
        codes.put("himachalpradesh", "HP");
        codes.put("jammuandkashmir", "JK");
        codes.put("jharkhand", "JH");
        codes.put("karnataka", "KA");
        codes.put("kerala", "KL");
        codes.put("ladakh", "LA");
        codes.put("lakshadweep", "LD");
        codes.put("madhyapradesh", "MP");
        codes.put("maharashtra", "MH");
        codes.put("manipur", "MN");
        codes.put("meghalaya", "ML");
        codes.put("mizoram", "MZ");
        codes.put("nagaland", "NL");
        codes.put("odisha", "OR");
        codes.put("orissa", "OR");
        codes.put("puducherry", "PY");
        codes.put("pondicherry", "PY");
        codes.put("punjab", "PB");
        codes.put("rajasthan", "RJ");
        codes.put("sikkim", "SK");
        codes.put("tamilnadu", "TN");
        codes.put("telangana", "TG");
        codes.put("tripura", "TR");
        codes.put("uttarpradesh", "UP");
        codes.put("uttarakhand", "UT");
        codes.put("uttaranchal", "UT");
        codes.put("westbengal", "WB");
        return Collections.unmodifiableMap(codes);
    }

    private final LedgerlyIntegrationClient ledgerlyClient;
    private final ProductRepository productRepository;
    private final CustomerOrderRepository customerOrderRepository;
    private final CustomerProfileRepository customerProfileRepository;
    private final LedgerlySettingRepository ledgerlySettingRepository;
    private final SiteSettingRepository siteSettingRepository;
    private final LedgerlyProductMergeService productMergeService;
    private final LedgerlyCategoryResolver categoryResolver;
    private final ProductVariantSupport variantSupport;

    private final ProductColorImagesSupport colorImagesSupport;
    private final ApplicationProperties applicationProperties;
    private final ObjectMapper objectMapper;

    public LedgerlySyncService(
        LedgerlyIntegrationClient ledgerlyClient,
        ProductRepository productRepository,
        CustomerOrderRepository customerOrderRepository,
        CustomerProfileRepository customerProfileRepository,
        LedgerlySettingRepository ledgerlySettingRepository,
        SiteSettingRepository siteSettingRepository,
        LedgerlyProductMergeService productMergeService,
        LedgerlyCategoryResolver categoryResolver,
        ProductVariantSupport variantSupport,
        ProductColorImagesSupport colorImagesSupport,
        ApplicationProperties applicationProperties,
        ObjectMapper objectMapper
    ) {
        this.ledgerlyClient = ledgerlyClient;
        this.productRepository = productRepository;
        this.customerOrderRepository = customerOrderRepository;
        this.customerProfileRepository = customerProfileRepository;
        this.ledgerlySettingRepository = ledgerlySettingRepository;
        this.siteSettingRepository = siteSettingRepository;
        this.productMergeService = productMergeService;
        this.categoryResolver = categoryResolver;
        this.variantSupport = variantSupport;
        this.colorImagesSupport = colorImagesSupport;
        this.applicationProperties = applicationProperties;
        this.objectMapper = objectMapper;
    }

    @Async
    public void syncProductAsync(Long productId) {
        if (!applicationProperties.getLedgerly().isAutoSyncEnabled()) {
            LOG.debug("Skipping async Ledgerly product sync for {} — auto-sync disabled", productId);
            return;
        }
        syncProduct(productId);
    }

    public void syncProduct(Long productId) {
        productRepository
            .findOneWithEagerRelationships(productId)
            .ifPresent(product -> {
                try {
                    pushProductToLedgerly(product);
                } catch (Exception e) {
                    LOG.warn("Ledgerly product sync failed for {}: {}", productId, e.getMessage());
                    // Bulk UPDATE — never save the in-memory snapshot (it can be stale vs concurrent
                    // stock/matrix writes from checkout reservation).
                    productRepository.updateLedgerlySyncFailure(productId, LedgerlySyncStatus.FAILED, e.getMessage());
                }
            });
    }

    /**
     * When the store admin deletes a product, mark the linked Ledgerly item inactive before the
     * local row is removed (historical invoices keep the Ledgerly item).
     */
    public void notifyLedgerlyBeforeStoreDelete(Long productId) {
        productRepository
            .findOneWithEagerRelationships(productId)
            .ifPresent(product -> {
                try {
                    if (isLedgerlyLinked(product)) {
                        deactivateInLedgerly(product);
                    }
                } catch (Exception e) {
                    LOG.warn("Ledgerly deactivation before store delete failed for {}: {}", productId, e.getMessage());
                }
            });
    }

    @Async
    public void syncCustomerProfileAsync(String userLogin) {
        syncCustomerProfile(userLogin);
    }

    public void syncCustomerProfile(String userLogin) {
        customerProfileRepository
            .findOneByUserLogin(userLogin)
            .ifPresent(profile -> {
                try {
                    ledgerlyClient.syncCustomerPayload(buildCustomerPayload(userLogin, profile));
                } catch (Exception e) {
                    LOG.warn("Ledgerly customer sync failed for {}: {}", userLogin, e.getMessage());
                }
            });
    }

    public void syncOrder(Long orderId) {
        customerOrderRepository
            .findById(orderId)
            .ifPresent(order -> {
                try {
                    Map<String, Object> response = syncOrderPayloadWithRetry(order);
                    order.setLedgerlyInvoiceId(String.valueOf(response.getOrDefault("ledgerlyInvoiceId", "")));
                    order.setLedgerlySalesOrderId(String.valueOf(response.getOrDefault("ledgerlySalesOrderId", order.getOrderNumber())));
                    order.setLedgerlySyncStatus(LedgerlySyncStatus.SYNCED);
                    order.setLedgerlyLastSyncedAt(Instant.now());
                    order.setLedgerlySyncError(null);
                    customerOrderRepository.save(order);
                } catch (Exception e) {
                    LOG.warn("Ledgerly order sync failed for {}: {}", orderId, e.getMessage());
                    order.setLedgerlySyncStatus(LedgerlySyncStatus.FAILED);
                    order.setLedgerlySyncError(e.getMessage());
                    customerOrderRepository.save(order);
                }
            });
    }

    /**
     * Ledgerly creates the invoice on the first sync call but can fail applying Razorpay payment
     * (sales invoice payment {@code mode} null). An immediate idempotent retry returns the invoice id.
     */
    private Map<String, Object> syncOrderPayloadWithRetry(CustomerOrder order) {
        Map<String, Object> payload = buildOrderPayload(order);
        String idempotencyKey = order.getRazorpayPaymentId();
        try {
            return ledgerlyClient.syncOrderPayload(payload, idempotencyKey);
        } catch (Exception first) {
            if (!shouldRetryAfterInvoicePaymentFailure(first)) {
                throw first;
            }
            LOG.info(
                "Ledgerly order {} invoice payment step failed; retrying idempotent sync (orderId={})",
                order.getOrderNumber(),
                order.getId()
            );
            return ledgerlyClient.syncOrderPayload(payload, idempotencyKey);
        }
    }

    static boolean shouldRetryAfterInvoicePaymentFailure(Exception error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) {
            return false;
        }
        if (!message.contains("invoicePaymentRequest") || !message.contains("/invoices/")) {
            return false;
        }
        // Ledgerly invoice payment step rejects null mode; upstream errors embed escaped JSON.
        return message.contains("mode") && message.contains("must not be null");
    }

    public void pullOrderTracking(Long orderId) {
        customerOrderRepository
            .findById(orderId)
            .ifPresent(order -> {
                try {
                    Map<String, Object> response = ledgerlyClient.pullOrderTracking(order.getOrderNumber());
                    if (Boolean.TRUE.equals(response.get("found"))) {
                        String incoming = LedgerlyOrderStatus.resolveFromPullResponse(response);
                        if (incoming != null && LedgerlyOrderStatus.shouldReplace(order.getLedgerlyOrderStatus(), incoming)) {
                            order.setLedgerlyOrderStatus(incoming);
                        }
                        LedgerlyOrderStatus.applyTrackingFields(order, response);
                        order.setLedgerlyTrackingLastPulledAt(Instant.now());
                        customerOrderRepository.save(order);
                    }
                } catch (Exception e) {
                    LOG.warn("Ledgerly tracking pull failed for {}: {}", orderId, e.getMessage());
                }
            });
    }

    private static final int SYNC_PAGE_SIZE = 200;

    public Map<String, Object> syncAllProducts() {
        LedgerlySyncSummary.Builder builder = LedgerlySyncSummary.builder();
        // Eager-fetch category: sync success/failure UPDATEs clear the persistence context
        // (clearAutomatically), which would otherwise leave lazy Category proxies unusable.
        int pageNumber = 0;
        Page<Product> page;
        do {
            page = productRepository.findAllWithEagerRelationships(PageRequest.of(pageNumber, SYNC_PAGE_SIZE, Sort.by("id")));
            for (Product product : page.getContent()) {
                try {
                    if (StringUtils.isBlank(product.getSku())) {
                        builder.notice(product.getName(), "Skipped — SKU required");
                        continue;
                    }
                    if (!Boolean.TRUE.equals(product.getActive())) {
                        if (isLedgerlyLinked(product)) {
                            deactivateInLedgerly(product);
                            builder.notice(product.getSku(), "Deactivated in Ledgerly");
                        }
                        continue;
                    }
                    pushProductToLedgerly(product);
                    builder.success();
                } catch (Exception e) {
                    productRepository.updateLedgerlySyncFailure(product.getId(), LedgerlySyncStatus.FAILED, e.getMessage());
                    builder.failure(product.getId(), product.getName(), e.getMessage());
                }
            }
            pageNumber++;
        } while (page.hasNext());
        return builder.build().toMap();
    }

    public Map<String, Object> pullProducts() {
        Map<String, Object> response = ledgerlyClient.pullProducts();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> products = (List<Map<String, Object>>) response.getOrDefault("products", List.of());
        boolean autoCreateCategory = ledgerlySettingRepository
            .findFirstByOrderByIdAsc()
            .map(LedgerlySetting::getPullProductsAutoCreateCategory)
            .orElse(false);
        LedgerlySyncSummary.Builder builder = LedgerlySyncSummary.builder();
        Set<String> pulledSkus = new HashSet<>();
        Set<String> pulledLedgerlyItemIds = new HashSet<>();
        for (Map<String, Object> item : products) {
            String sku = stringValue(item.get("sku"));
            if (sku != null && !sku.isBlank()) {
                pulledSkus.add(sku.trim());
            }
            String ledgerlyItemId = stringValue(item.get("ledgerlyItemId"));
            if (ledgerlyItemId != null && !ledgerlyItemId.isBlank()) {
                pulledLedgerlyItemIds.add(ledgerlyItemId.trim());
            }
            try {
                upsertPulledProduct(item, autoCreateCategory, builder);
            } catch (Exception e) {
                builder.failure(stringValue(item.get("sku")), e.getMessage());
            }
        }
        if (!products.isEmpty()) {
            deactivateStaleLedgerlyProducts(pulledSkus, pulledLedgerlyItemIds, builder);
        }
        return builder.build().toMap();
    }

    public Map<String, Object> pullInventory() {
        final Map<String, Object> response;
        try {
            response = ledgerlyClient.pullInventory();
        } catch (RestClientResponseException ex) {
            String message =
                "Ledgerly inventory pull failed (HTTP " + ex.getStatusCode().value() + "): " + StringUtils.defaultString(ex.getStatusText());
            LOG.warn(message);
            return LedgerlySyncSummary.builder().failure(message).build().toMap();
        } catch (ResourceAccessException ex) {
            String message = "Ledgerly integration unreachable: " + ex.getMessage();
            LOG.warn(message);
            return LedgerlySyncSummary.builder().failure(message).build().toMap();
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> levels = (List<Map<String, Object>>) response.getOrDefault("inventory", List.of());
        LedgerlySyncSummary.Builder builder = LedgerlySyncSummary.builder();
        List<Product> variantProducts = productRepository
            .findAll()
            .stream()
            .filter(variantSupport::isVariants)
            .toList();
        for (Map<String, Object> level : levels) {
            String sku = stringValue(level.get("sku"));
            String ledgerlyItemId = stringValue(level.get("ledgerlyItemId"));
            Object variantsRaw = level.containsKey("variantsJson") ? level.get("variantsJson") : level.get("variants_json");
            try {
                Optional<Product> parent = findProductForInventoryPull(sku, ledgerlyItemId);
                if (parent.isPresent()) {
                    Product p = parent.orElseThrow();
                    if (variantSupport.isVariants(p) && variantsRaw != null) {
                        String variantsJson = variantSupport.coerceVariantsJson(variantsRaw);
                        if (variantsJson == null || !variantSupport.applyMatrixToProduct(p, ProductVariantSupport.STRUCTURE_VARIANTS, variantsJson)) {
                            builder.failure(sku != null ? sku : ledgerlyItemId, "Invalid variantsJson on inventory level");
                            continue;
                        }
                    } else if (variantSupport.isVariants(p) && level.get("stock") != null) {
                        // Parent-level stock only — keep matrix; re-aggregate would wipe rates. Skip stock overwrite.
                        builder.success();
                        continue;
                    } else {
                        p.setStock(toInteger(level.get("stock")));
                    }
                    productRepository.save(p);
                    builder.success();
                    continue;
                }
                Optional<Product> byVariant = variantSupport.findProductContainingVariantSku(variantProducts, sku);
                if (byVariant.isPresent() && sku != null) {
                    Product p = byVariant.orElseThrow();
                    if (variantSupport.updateVariantStockBySku(p, sku, toInteger(level.get("stock")))) {
                        productRepository.save(p);
                        builder.success();
                    } else {
                        builder.failure(sku, "Variant row not found on parent " + p.getSku());
                    }
                    continue;
                }
                String ref = sku != null ? sku : ledgerlyItemId;
                builder.skipped(ref, "No linked store product for SKU or Ledgerly item id");
            } catch (Exception e) {
                builder.failure(sku != null ? sku : ledgerlyItemId, e.getMessage());
            }
        }
        LedgerlySyncSummary summary = builder.build();
        LOG.info(
            "Ledgerly inventory pull finished: updated={}, failed={}, skippedUnlinked={}",
            summary.successCount(),
            summary.failureCount(),
            summary.skippedCount()
        );
        return summary.toMap();
    }

    public Map<String, Object> retryFailedOrders() {
        List<CustomerOrder> orders = customerOrderRepository
            .findAll()
            .stream()
            .filter(o -> o.getLedgerlySyncStatus() == LedgerlySyncStatus.FAILED || o.getLedgerlySyncStatus() == LedgerlySyncStatus.PENDING)
            .toList();
        LedgerlySyncSummary.Builder builder = LedgerlySyncSummary.builder();
        for (CustomerOrder order : orders) {
            try {
                syncOrder(order.getId());
                builder.success();
            } catch (Exception e) {
                builder.orderFailure(order.getOrderNumber(), e.getMessage());
            }
        }
        return builder.build().toMap();
    }

    public Map<String, Object> pullAllTracking() {
        LedgerlySyncSummary.Builder builder = LedgerlySyncSummary.builder();
        Page<CustomerOrder> page = customerOrderRepository.findOrdersForLedgerlyTrackingPull(
            PaymentStatus.PAID,
            List.of(OrderStatus.PENDING, OrderStatus.CONFIRMED, OrderStatus.HAND_WRAPPED),
            PageRequest.of(0, 3)
        );
        for (CustomerOrder order : page.getContent()) {
            try {
                pullOrderTracking(order.getId());
                builder.success();
            } catch (Exception e) {
                builder.orderFailure(order.getOrderNumber(), e.getMessage());
            }
        }
        builder.notice("batch", "Pulled tracking for " + page.getNumberOfElements() + " open paid orders (max 3)");
        return builder.build().toMap();
    }

    private void upsertPulledProduct(Map<String, Object> item, boolean autoCreateCategory, LedgerlySyncSummary.Builder builder) {
        String sku = stringValue(item.get("sku"));
        if (sku == null || sku.isBlank()) {
            builder.failure("missing-sku", "Ledgerly item has no SKU");
            return;
        }
        String ledgerlyItemId = stringValue(item.get("ledgerlyItemId"));
        Product product = productRepository
            .findOneBySku(sku)
            .or(() -> ledgerlyItemId != null ? productRepository.findOneByLedgerlyItemId(ledgerlyItemId) : Optional.empty())
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
            product.setActive(true);
            if (product.getStock() == null) {
                product.setStock(0);
            }
            if (product.getPrice() == null) {
                product.setPrice(BigDecimal.ZERO);
            }
            // description / fabric remain optional when Ledgerly omits them
        }

        productMergeService.mergeLedgerlyItem(
            product,
            item,
            categoryName -> categoryResolver.resolve(categoryName),
            (noticeSku, message) -> {
                if (created) {
                    builder.notice(noticeSku, message);
                }
            }
        );

        product.setActive(true);
        productRepository.save(product);
        builder.success();
    }

    /**
     * Ledgerly pull is the catalogue source of truth for linked items. Store products that were
     * previously synced but no longer appear in Ledgerly are hidden from the storefront.
     */
    private void deactivateStaleLedgerlyProducts(
        Set<String> pulledSkus,
        Set<String> pulledLedgerlyItemIds,
        LedgerlySyncSummary.Builder builder
    ) {
        for (Product product : productRepository.findAllActiveLedgerlyLinked()) {
            if (!isStillInLedgerlyPull(product, pulledSkus, pulledLedgerlyItemIds)) {
                product.setActive(false);
                product.setLedgerlySyncError("No longer in Ledgerly catalog (deactivated on pull)");
                productRepository.save(product);
                String label = StringUtils.defaultIfBlank(StringUtils.trimToNull(product.getSku()), product.getLedgerlyItemId());
                builder.notice(label, "Deactivated — removed from Ledgerly");
            }
        }
    }

    private static boolean isStillInLedgerlyPull(Product product, Set<String> pulledSkus, Set<String> pulledLedgerlyItemIds) {
        String itemId = StringUtils.trimToNull(product.getLedgerlyItemId());
        if (itemId != null && pulledLedgerlyItemIds.contains(itemId)) {
            return true;
        }
        String sku = StringUtils.trimToNull(product.getSku());
        return sku != null && pulledSkus.contains(sku);
    }

    private Optional<Product> findProductForInventoryPull(String sku, String ledgerlyItemId) {
        if (ledgerlyItemId != null && !ledgerlyItemId.isBlank()) {
            Optional<Product> byItemId = productRepository.findOneByLedgerlyItemId(ledgerlyItemId);
            if (byItemId.isPresent()) {
                return byItemId;
            }
        }
        if (sku != null && !sku.isBlank()) {
            return productRepository.findOneBySku(sku);
        }
        return Optional.empty();
    }

    private String uniqueSlug(String sku, String name) {
        String base = LedgerlyCategoryResolver.slugify(name != null && !name.isBlank() ? name : sku);
        String candidate = base;
        int suffix = 1;
        while (productRepository.findOneBySlug(candidate).isPresent()) {
            candidate = base + "-" + suffix++;
        }
        return candidate;
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

    private Map<String, Object> buildProductPayload(Product product) {
        colorImagesSupport.syncProductColorImages(product);
        String categoryName = null;
        if (product.getCategory() != null) {
            try {
                categoryName = product.getCategory().getName();
            } catch (RuntimeException ex) {
                // Detached / lazy Category after a prior Modifying UPDATE cleared the session.
                LOG.debug("Category name unavailable for product {}: {}", product.getId(), ex.getMessage());
            }
        }
        return productMergeService.buildPushPayload(product, categoryName);
    }

    /**
     * Push one catalog row to Ledgerly: active products upsert item data; inactive linked products
     * send {@code active:false} so pull-deactivated rows are not recreated in Ledgerly.
     */
    private void pushProductToLedgerly(Product product) {
        if (StringUtils.isBlank(product.getSku())) {
            throw new IllegalStateException("SKU is required for Ledgerly sync");
        }
        if (!Boolean.TRUE.equals(product.getActive())) {
            if (isLedgerlyLinked(product)) {
                deactivateInLedgerly(product);
            }
            return;
        }
        Long productId = product.getId();
        Map<String, Object> response = ledgerlyClient.syncProductPayload(buildProductPayload(product));
        applyLedgerlyPushSuccess(productId, response);
    }

    private void deactivateInLedgerly(Product product) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sku", product.getSku());
        payload.put("active", false);
        if (StringUtils.isNotBlank(product.getLedgerlyItemId())) {
            payload.put("ledgerlyItemId", product.getLedgerlyItemId());
        }
        Long productId = product.getId();
        Map<String, Object> response = ledgerlyClient.syncProductPayload(payload);
        if (productId == null) {
            return;
        }
        productRepository.updateLedgerlyDeactivateMeta(
            productId,
            Instant.now(),
            Boolean.TRUE.equals(response.get("noop"))
        );
    }

    private void applyLedgerlyPushSuccess(Long productId, Map<String, Object> response) {
        if (productId == null) {
            return;
        }
        // Bulk UPDATE only touches Ledgerly metadata columns so concurrent stock/matrix changes survive.
        productRepository.updateLedgerlySyncSuccess(
            productId,
            String.valueOf(response.getOrDefault("ledgerlyItemId", "")),
            LedgerlySyncStatus.SYNCED,
            Instant.now()
        );
    }

    private static boolean isLedgerlyLinked(Product product) {
        return StringUtils.isNotBlank(product.getLedgerlyItemId()) || product.getLedgerlySyncStatus() == LedgerlySyncStatus.SYNCED;
    }

    private Map<String, Object> buildOrderPayload(CustomerOrder order) {
        Map<String, Object> payload = new LinkedHashMap<>();
        BigDecimal discount = moneyOrZero(order.getDiscount());
        BigDecimal total = moneyOrZero(order.getTotal());
        payload.put("externalRef", order.getOrderNumber());
        payload.put("subtotal", order.getSubtotal());
        payload.put("discount", discount);
        payload.put("shipping", order.getShipping());
        payload.put("tax", order.getTax());
        payload.put("donation", order.getDonation());
        payload.put("total", total);
        payload.put("couponCode", StringUtils.trimToNull(order.getCouponCode()));
        payload.put("pricesIncludeGst", pricesIncludeGst());
        payload.put("paymentId", order.getRazorpayPaymentId());
        payload.put("paymentMode", "Razorpay");
        payload.put("currency", "INR");
        String placeOfSupply = resolvePlaceOfSupply(order.getShippingAddressJson());
        if (placeOfSupply != null) {
            payload.put("placeOfSupply", placeOfSupply);
        } else {
            LOG.warn(
                "Ledgerly order {} has no resolvable place of supply from shipping address; Ledgerly will reject the sales order",
                order.getOrderNumber()
            );
        }
        Map<String, Object> payment = new LinkedHashMap<>();
        payment.put("mode", "Razorpay");
        payment.put("paymentId", order.getRazorpayPaymentId());
        payment.put("reference", order.getRazorpayPaymentId());
        payment.put("amount", total);
        payload.put("payment", payment);
        Map<String, Object> customer = buildCustomerPayload(order);
        payload.put("customer", customer);
        BigDecimal lineTaxPct = pricesIncludeGst() ? BigDecimal.ZERO : siteGstRate();
        List<Map<String, Object>> lines = new ArrayList<>();
        Map<Long, String> skuByProductId = new HashMap<>();
        for (OrderItem item : order.getItemses()) {
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("sku", resolveLineSku(item, skuByProductId));
            line.put("qty", item.getQty());
            line.put("rate", item.getPrice());
            line.put("taxPct", lineTaxPct);
            line.put("description", item.getProductName());
            lines.add(line);
        }
        payload.put("lines", lines);
        return payload;
    }

    /**
     * Ledgerly order lines must use variant SKU for VARIANTS parents; never the parent SKU.
     */
    private String resolveLineSku(OrderItem item, Map<Long, String> skuByProductId) {
        String variantSku = StringUtils.trimToNull(item.getVariantSku());
        if (variantSku != null) {
            return variantSku;
        }
        String productId = item.getProductId();
        Product product = null;
        if (productId != null && !productId.isBlank()) {
            try {
                long id = Long.parseLong(productId.trim());
                Optional<Product> found = productRepository.findById(id);
                if (found.isPresent()) {
                    product = found.orElseThrow();
                    if (variantSupport.isVariants(product)) {
                        Optional<ProductVariantSupport.VariantRow> bySizeColor = variantSupport.resolveSellable(
                            product,
                            null,
                            item.getItemSize(),
                            item.getColor()
                        );
                        if (bySizeColor.isPresent()) {
                            return bySizeColor.orElseThrow().sku();
                        }
                        throw new IllegalStateException(
                            "Order line missing variantSku for VARIANTS product " + product.getSku()
                        );
                    }
                    String cached = skuByProductId.get(id);
                    if (cached != null) {
                        return cached;
                    }
                    String sku = product.getSku();
                    if (sku != null && !sku.isBlank()) {
                        skuByProductId.put(id, sku);
                        return sku;
                    }
                }
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        String slug = item.getProductSlug();
        if (slug != null && !slug.isBlank()) {
            return slug;
        }
        return productId;
    }

    private boolean pricesIncludeGst() {
        return siteSettingRepository
            .findFirstByOrderByIdAsc()
            .map(settings -> Boolean.TRUE.equals(settings.getPricesIncludeGst()))
            .orElse(true);
    }

    private BigDecimal siteGstRate() {
        return siteSettingRepository
            .findFirstByOrderByIdAsc()
            .map(settings -> settings.getGstRate() != null ? settings.getGstRate() : BigDecimal.valueOf(5))
            .orElse(BigDecimal.valueOf(5));
    }

    private Map<String, Object> buildCustomerPayload(CustomerOrder order) {
        CustomerProfile profile = customerProfileRepository.findOneByUserLogin(order.getUserLogin()).orElse(null);
        return buildCustomerPayload(order.getUserLogin(), profile, order.getShippingAddressJson());
    }

    private Map<String, Object> buildCustomerPayload(String userLogin, CustomerProfile profile) {
        return buildCustomerPayload(userLogin, profile, null);
    }

    private Map<String, Object> buildCustomerPayload(String userLogin, CustomerProfile profile, String shippingAddressJson) {
        Map<String, Object> customer = new LinkedHashMap<>();
        String name = profile != null && profile.getFullName() != null && !profile.getFullName().isBlank()
            ? profile.getFullName()
            : userLogin;
        customer.put("name", name);
        customer.put("email", userLogin);
        if (profile != null && profile.getPhone() != null && !profile.getPhone().isBlank()) {
            customer.put("phone", profile.getPhone());
        }
        if (shippingAddressJson != null && !shippingAddressJson.isBlank()) {
            customer.put("shippingAddress", shippingAddressJson);
        }
        return customer;
    }

    /**
     * Ledgerly's sales-order API requires placeOfSupply (the GST state code of the delivery
     * address) on every order; it does not fall back to the customer's saved state. We only have
     * a free-text "state" inside the stored shippingAddressJson, so normalize it against the
     * standard GST state/UT code table.
     */
    private String resolvePlaceOfSupply(String shippingAddressJson) {
        if (StringUtils.isBlank(shippingAddressJson)) {
            return null;
        }
        String state;
        try {
            JsonNode node = objectMapper.readTree(shippingAddressJson);
            state = node.path("state").asText(null);
        } catch (Exception e) {
            LOG.warn("Unable to parse shipping address JSON for place of supply: {}", e.getMessage());
            return null;
        }
        return placeOfSupplyCode(state);
    }

    private static String placeOfSupplyCode(String state) {
        if (StringUtils.isBlank(state)) {
            return null;
        }
        String trimmed = state.trim();
        if (trimmed.length() == 2 && INDIA_PLACE_OF_SUPPLY_CODES.containsValue(trimmed.toUpperCase(Locale.ROOT))) {
            return trimmed.toUpperCase(Locale.ROOT);
        }
        String normalized = trimmed.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
        return INDIA_PLACE_OF_SUPPLY_CODES.get(normalized);
    }

    private static BigDecimal moneyOrZero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
