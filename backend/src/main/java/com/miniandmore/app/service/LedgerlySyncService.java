package com.miniandmore.app.service;

import com.miniandmore.app.client.LedgerlyIntegrationClient;
import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.CustomerProfile;
import com.miniandmore.app.domain.LedgerlySetting;
import com.miniandmore.app.domain.OrderItem;
import com.miniandmore.app.domain.Product;
import com.miniandmore.app.domain.enumeration.LedgerlySyncStatus;
import com.miniandmore.app.repository.CustomerOrderRepository;
import com.miniandmore.app.repository.CustomerProfileRepository;
import com.miniandmore.app.repository.LedgerlySettingRepository;
import com.miniandmore.app.repository.ProductRepository;
import com.miniandmore.app.repository.SiteSettingRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class LedgerlySyncService {

    private static final Logger LOG = LoggerFactory.getLogger(LedgerlySyncService.class);

    private final LedgerlyIntegrationClient ledgerlyClient;
    private final ProductRepository productRepository;
    private final CustomerOrderRepository customerOrderRepository;
    private final CustomerProfileRepository customerProfileRepository;
    private final LedgerlySettingRepository ledgerlySettingRepository;
    private final SiteSettingRepository siteSettingRepository;
    private final LedgerlyProductMergeService productMergeService;
    private final LedgerlyCategoryResolver categoryResolver;

    public LedgerlySyncService(
        LedgerlyIntegrationClient ledgerlyClient,
        ProductRepository productRepository,
        CustomerOrderRepository customerOrderRepository,
        CustomerProfileRepository customerProfileRepository,
        LedgerlySettingRepository ledgerlySettingRepository,
        SiteSettingRepository siteSettingRepository,
        LedgerlyProductMergeService productMergeService,
        LedgerlyCategoryResolver categoryResolver
    ) {
        this.ledgerlyClient = ledgerlyClient;
        this.productRepository = productRepository;
        this.customerOrderRepository = customerOrderRepository;
        this.customerProfileRepository = customerProfileRepository;
        this.ledgerlySettingRepository = ledgerlySettingRepository;
        this.siteSettingRepository = siteSettingRepository;
        this.productMergeService = productMergeService;
        this.categoryResolver = categoryResolver;
    }

    @Async
    public void syncProductAsync(Long productId) {
        syncProduct(productId);
    }

    public void syncProduct(Long productId) {
        productRepository
            .findById(productId)
            .ifPresent(product -> {
                try {
                    pushProductToLedgerly(product);
                } catch (Exception e) {
                    LOG.warn("Ledgerly product sync failed for {}: {}", productId, e.getMessage());
                    product.setLedgerlySyncStatus(LedgerlySyncStatus.FAILED);
                    product.setLedgerlySyncError(e.getMessage());
                    productRepository.save(product);
                }
            });
    }

    /**
     * When the store admin deletes a product, mark the linked Ledgerly item inactive before the
     * local row is removed (historical invoices keep the Ledgerly item).
     */
    public void notifyLedgerlyBeforeStoreDelete(Long productId) {
        productRepository
            .findById(productId)
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
                        order.setLedgerlyOrderStatus(String.valueOf(response.getOrDefault("status", "")));
                        order.setLedgerlyTrackingLastPulledAt(Instant.now());
                        customerOrderRepository.save(order);
                    }
                } catch (Exception e) {
                    LOG.warn("Ledgerly tracking pull failed for {}: {}", orderId, e.getMessage());
                }
            });
    }

    public Map<String, Object> syncAllProducts() {
        LedgerlySyncSummary.Builder builder = LedgerlySyncSummary.builder();
        for (Product product : productRepository.findAll()) {
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
                product.setLedgerlySyncStatus(LedgerlySyncStatus.FAILED);
                product.setLedgerlySyncError(e.getMessage());
                productRepository.save(product);
                builder.failure(product.getId(), product.getName(), e.getMessage());
            }
        }
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
        Map<String, Object> response = ledgerlyClient.pullInventory();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> levels = (List<Map<String, Object>>) response.getOrDefault("inventory", List.of());
        LedgerlySyncSummary.Builder builder = LedgerlySyncSummary.builder();
        for (Map<String, Object> level : levels) {
            String sku = stringValue(level.get("sku"));
            String ledgerlyItemId = stringValue(level.get("ledgerlyItemId"));
            Optional<Product> product = findProductForInventoryPull(sku, ledgerlyItemId);
            if (product.isEmpty()) {
                String ref = sku != null ? sku : ledgerlyItemId;
                builder.failure(ref, "No linked store product for SKU or Ledgerly item id");
                continue;
            }
            try {
                Product p = product.orElseThrow();
                p.setStock(toInteger(level.get("stock")));
                productRepository.save(p);
                builder.success();
            } catch (Exception e) {
                builder.failure(product.orElseThrow().getSku(), e.getMessage());
            }
        }
        return builder.build().toMap();
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
        for (CustomerOrder order : customerOrderRepository.findAll()) {
            try {
                pullOrderTracking(order.getId());
                builder.success();
            } catch (Exception e) {
                builder.orderFailure(order.getOrderNumber(), e.getMessage());
            }
        }
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
        String categoryName = product.getCategory() != null ? product.getCategory().getName() : null;
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
        Map<String, Object> response = ledgerlyClient.syncProductPayload(buildProductPayload(product));
        product.setLedgerlyItemId(String.valueOf(response.getOrDefault("ledgerlyItemId", "")));
        product.setLedgerlySyncStatus(LedgerlySyncStatus.SYNCED);
        product.setLedgerlyLastSyncedAt(Instant.now());
        product.setLedgerlySyncError(null);
        productRepository.save(product);
    }

    private void deactivateInLedgerly(Product product) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sku", product.getSku());
        payload.put("active", false);
        if (StringUtils.isNotBlank(product.getLedgerlyItemId())) {
            payload.put("ledgerlyItemId", product.getLedgerlyItemId());
        }
        Map<String, Object> response = ledgerlyClient.syncProductPayload(payload);
        product.setLedgerlyLastSyncedAt(Instant.now());
        product.setLedgerlySyncError(null);
        if (Boolean.TRUE.equals(response.get("noop"))) {
            product.setLedgerlyItemId(null);
        }
        productRepository.save(product);
    }

    private static boolean isLedgerlyLinked(Product product) {
        return StringUtils.isNotBlank(product.getLedgerlyItemId()) || product.getLedgerlySyncStatus() == LedgerlySyncStatus.SYNCED;
    }

    private Map<String, Object> buildOrderPayload(CustomerOrder order) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("externalRef", order.getOrderNumber());
        payload.put("subtotal", order.getSubtotal());
        payload.put("discount", order.getDiscount());
        payload.put("shipping", order.getShipping());
        payload.put("tax", order.getTax());
        payload.put("donation", order.getDonation());
        payload.put("total", order.getTotal());
        payload.put("pricesIncludeGst", pricesIncludeGst());
        payload.put("paymentId", order.getRazorpayPaymentId());
        payload.put("paymentMode", "Razorpay");
        payload.put("currency", "INR");
        Map<String, Object> payment = new LinkedHashMap<>();
        payment.put("mode", "Razorpay");
        payment.put("paymentId", order.getRazorpayPaymentId());
        payment.put("reference", order.getRazorpayPaymentId());
        payment.put("amount", order.getTotal());
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
     * Ledgerly items are keyed by catalog SKU; order lines store URL slug separately.
     */
    private String resolveLineSku(OrderItem item, Map<Long, String> skuByProductId) {
        String productId = item.getProductId();
        if (productId != null && !productId.isBlank()) {
            try {
                long id = Long.parseLong(productId.trim());
                String cached = skuByProductId.get(id);
                if (cached != null) {
                    return cached;
                }
                Optional<Product> product = productRepository.findById(id);
                if (product.isPresent()) {
                    String sku = product.orElseThrow().getSku();
                    if (sku != null && !sku.isBlank()) {
                        skuByProductId.put(id, sku);
                        return sku;
                    }
                }
            } catch (NumberFormatException ignored) {
                // fall through to slug / product id
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
}
