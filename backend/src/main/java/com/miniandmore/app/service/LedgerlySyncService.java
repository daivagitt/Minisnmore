package com.miniandmore.app.service;

import com.miniandmore.app.client.LedgerlyIntegrationClient;
import com.miniandmore.app.domain.Category;
import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.LedgerlySetting;
import com.miniandmore.app.domain.OrderItem;
import com.miniandmore.app.domain.Product;
import com.miniandmore.app.domain.enumeration.LedgerlySyncStatus;
import com.miniandmore.app.repository.CategoryRepository;
import com.miniandmore.app.repository.CustomerOrderRepository;
import com.miniandmore.app.repository.LedgerlySettingRepository;
import com.miniandmore.app.repository.ProductRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class LedgerlySyncService {

    private static final Logger LOG = LoggerFactory.getLogger(LedgerlySyncService.class);
    private static final Pattern CATEGORY_PREFIX = Pattern.compile("^Category:\\s*(.+?)(?:\\r?\\n|$)", Pattern.CASE_INSENSITIVE);

    private final LedgerlyIntegrationClient ledgerlyClient;
    private final ProductRepository productRepository;
    private final CustomerOrderRepository customerOrderRepository;
    private final LedgerlySettingRepository ledgerlySettingRepository;
    private final CategoryRepository categoryRepository;

    public LedgerlySyncService(
        LedgerlyIntegrationClient ledgerlyClient,
        ProductRepository productRepository,
        CustomerOrderRepository customerOrderRepository,
        LedgerlySettingRepository ledgerlySettingRepository,
        CategoryRepository categoryRepository
    ) {
        this.ledgerlyClient = ledgerlyClient;
        this.productRepository = productRepository;
        this.customerOrderRepository = customerOrderRepository;
        this.ledgerlySettingRepository = ledgerlySettingRepository;
        this.categoryRepository = categoryRepository;
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
                    Map<String, Object> response = ledgerlyClient.syncProductPayload(buildProductPayload(product));
                    product.setLedgerlyItemId(String.valueOf(response.getOrDefault("ledgerlyItemId", "")));
                    product.setLedgerlySyncStatus(LedgerlySyncStatus.SYNCED);
                    product.setLedgerlyLastSyncedAt(Instant.now());
                    product.setLedgerlySyncError(null);
                    productRepository.save(product);
                } catch (Exception e) {
                    LOG.warn("Ledgerly product sync failed for {}: {}", productId, e.getMessage());
                    product.setLedgerlySyncStatus(LedgerlySyncStatus.FAILED);
                    product.setLedgerlySyncError(e.getMessage());
                    productRepository.save(product);
                }
            });
    }

    public void syncOrder(Long orderId) {
        customerOrderRepository
            .findById(orderId)
            .ifPresent(order -> {
                try {
                    Map<String, Object> response = ledgerlyClient.syncOrderPayload(
                        buildOrderPayload(order),
                        order.getRazorpayPaymentId()
                    );
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
                syncProduct(product.getId());
                builder.success();
            } catch (Exception e) {
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
        for (Map<String, Object> item : products) {
            try {
                upsertPulledProduct(item, autoCreateCategory, builder);
            } catch (Exception e) {
                builder.failure(stringValue(item.get("sku")), e.getMessage());
            }
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

        if (item.get("price") != null) {
            product.setPrice(toBigDecimal(item.get("price")));
        }
        if (item.get("stock") != null) {
            product.setStock(toInteger(item.get("stock")));
        }
        if (ledgerlyItemId != null && !ledgerlyItemId.isBlank()) {
            product.setLedgerlyItemId(ledgerlyItemId);
        }
        product.setLedgerlySyncStatus(LedgerlySyncStatus.SYNCED);
        product.setLedgerlyLastSyncedAt(Instant.now());
        product.setLedgerlySyncError(null);

        String description = stringValue(item.get("description"));
        if (description != null) {
            product.setDescription(description);
            resolveCategory(description, autoCreateCategory).ifPresent(category -> {
                product.setCategory(category);
                if (created) {
                    builder.notice(sku, "Assigned category " + category.getName());
                }
            });
        }

        productRepository.save(product);
        builder.success();
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

    private Optional<Category> resolveCategory(String description, boolean autoCreateCategory) {
        Matcher matcher = CATEGORY_PREFIX.matcher(description.trim());
        if (!matcher.find()) {
            return Optional.empty();
        }
        String categoryName = matcher.group(1).trim();
        if (categoryName.isEmpty()) {
            return Optional.empty();
        }
        String slug = slugify(categoryName);
        Optional<Category> existing = categoryRepository.findOneBySlug(slug);
        if (existing.isPresent()) {
            return existing;
        }
        if (!autoCreateCategory) {
            return Optional.empty();
        }
        Category category = new Category();
        category.setName(categoryName);
        category.setSlug(uniqueCategorySlug(slug));
        category.setSortOrder(categoryRepository.findAll().size());
        category.setActive(true);
        return Optional.of(categoryRepository.save(category));
    }

    private String uniqueSlug(String sku, String name) {
        String base = slugify(name != null && !name.isBlank() ? name : sku);
        String candidate = base;
        int suffix = 1;
        while (productRepository.findOneBySlug(candidate).isPresent()) {
            candidate = base + "-" + suffix++;
        }
        return candidate;
    }

    private String uniqueCategorySlug(String base) {
        String candidate = base;
        int suffix = 1;
        while (categoryRepository.findOneBySlug(candidate).isPresent()) {
            candidate = base + "-" + suffix++;
        }
        return candidate;
    }

    private String slugify(String input) {
        String slug = input.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        return slug.isBlank() ? "item" : slug;
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
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sku", product.getSku() != null ? product.getSku() : "P-" + product.getId());
        payload.put("name", product.getName());
        payload.put("price", product.getPrice());
        payload.put("stock", product.getStock());
        payload.put("taxPct", BigDecimal.valueOf(5));
        if (product.getDescription() != null) {
            payload.put("description", product.getDescription());
        }
        return payload;
    }

    private Map<String, Object> buildOrderPayload(CustomerOrder order) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("externalRef", order.getOrderNumber());
        payload.put("total", order.getTotal());
        payload.put("paymentId", order.getRazorpayPaymentId());
        payload.put("currency", "INR");
        Map<String, Object> customer = new LinkedHashMap<>();
        customer.put("name", order.getUserLogin());
        customer.put("email", order.getUserLogin());
        customer.put("shippingAddress", order.getShippingAddressJson());
        payload.put("customer", customer);
        List<Map<String, Object>> lines = new ArrayList<>();
        for (OrderItem item : order.getItemses()) {
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("sku", item.getProductSlug() != null ? item.getProductSlug() : item.getProductId());
            line.put("qty", item.getQty());
            line.put("rate", item.getPrice());
            line.put("taxPct", BigDecimal.valueOf(5));
            line.put("description", item.getProductName());
            lines.add(line);
        }
        payload.put("lines", lines);
        return payload;
    }
}
