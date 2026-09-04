package com.miniandmore.app.service;

import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.OrderItem;
import com.miniandmore.app.domain.Product;
import com.miniandmore.app.repository.OrderItemRepository;
import com.miniandmore.app.repository.ProductRepository;
import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Reconciles store catalog stock to Zoho {@code stock_on_hand} without pushing product metadata.
 * Used by stock webhooks, admin retries, and post-invoice correction.
 */
@Service
public class ZohoStockReconcileService {

    private static final Logger LOG = LoggerFactory.getLogger(ZohoStockReconcileService.class);

    private final ProductRepository productRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductVariantSupport variantSupport;
    private final ZohoBooksAccountResolver accountResolver;
    private final ZohoCatalogStockAdjuster stockAdjuster;

    public ZohoStockReconcileService(
        ProductRepository productRepository,
        OrderItemRepository orderItemRepository,
        ProductVariantSupport variantSupport,
        ZohoBooksAccountResolver accountResolver,
        ZohoCatalogStockAdjuster stockAdjuster
    ) {
        this.productRepository = productRepository;
        this.orderItemRepository = orderItemRepository;
        this.variantSupport = variantSupport;
        this.accountResolver = accountResolver;
        this.stockAdjuster = stockAdjuster;
    }

    public Map<String, Object> syncProductStockOnly(Long productId) {
        return productRepository
            .findOneWithEagerRelationships(productId)
            .map(product -> reconcileProductStock(product, null, null))
            .orElse(Map.of("ok", false, "error", "Product not found", "productId", productId));
    }

    public Map<String, Object> reconcileStockFromWebhook(String sku, Long productId, int stock) {
        Optional<Product> product = resolveProductForStockReconcile(sku, productId);
        if (product.isEmpty()) {
            return Map.of("ok", false, "error", "Product not found", "sku", StringUtils.defaultString(sku), "productId", productId);
        }
        Product resolved = product.orElseThrow();
        if (variantSupport.isVariants(resolved) && StringUtils.isBlank(sku)) {
            return Map.of("ok", false, "error", "Variant products require sku in stock webhook", "productId", resolved.getId());
        }
        return reconcileProductStock(resolved, sku, stock);
    }

    public void reconcileOrderProducts(CustomerOrder order) {
        if (!accountResolver.isStockAdjustmentEnabled() || order == null) {
            return;
        }
        Set<Long> productIds = new LinkedHashSet<>();
        for (OrderItem item : orderItemRepository.findAllByOrderId(order.getId())) {
            if (StringUtils.isBlank(item.getProductId())) {
                continue;
            }
            try {
                productIds.add(Long.parseLong(item.getProductId().trim()));
            } catch (NumberFormatException ignored) {
                // skip malformed product id
            }
        }
        for (Long productId : productIds) {
            try {
                syncProductStockOnly(productId);
            } catch (RuntimeException e) {
                LOG.warn(
                    "Post-document stock reconcile failed for product {} on order {}: {}",
                    productId,
                    order.getOrderNumber(),
                    e.getMessage()
                );
            }
        }
    }

    private Map<String, Object> reconcileProductStock(Product product, String variantSkuHint, Integer stockOverride) {
        if (!accountResolver.isStockAdjustmentEnabled()) {
            return Map.of("ok", false, "skipped", true, "reason", "Zoho stock sync disabled");
        }
        int reconciled = 0;
        int failed = 0;
        if (variantSupport.isVariants(product)) {
            String wantSku = StringUtils.trimToNull(variantSkuHint);
            for (ProductVariantSupport.VariantRow row : variantSupport.parseProduct(product)) {
                if (!row.active()) {
                    continue;
                }
                if (wantSku != null && !wantSku.equalsIgnoreCase(StringUtils.trimToEmpty(row.sku()))) {
                    continue;
                }
                String zohoItemId = StringUtils.trimToNull(row.zohoItemId());
                if (zohoItemId == null) {
                    continue;
                }
                int desired = stockOverride != null && wantSku != null ? stockOverride : Math.max(0, row.stock());
                if (
                    stockAdjuster.adjustItemQuantity(
                        zohoItemId,
                        desired,
                        variantSupport.variantSellingPrice(row),
                        "STORE-STOCK-" + product.getId() + "-" + row.sku(),
                        "variant " + row.sku() + " (" + product.getSku() + ")"
                    )
                ) {
                    reconciled++;
                } else {
                    failed++;
                }
            }
            if (wantSku != null && reconciled == 0) {
                String error = failed > 0 ? "Zoho stock reconcile failed" : "Variant not linked in Zoho";
                return Map.of("ok", false, "error", error, "sku", wantSku, "productId", product.getId());
            }
            if (failed > 0) {
                return Map.of("ok", false, "error", "Zoho stock reconcile failed", "productId", product.getId(), "failedItems", failed);
            }
        } else {
            String zohoItemId = StringUtils.trimToNull(product.getZohoItemId());
            if (zohoItemId == null) {
                return Map.of("ok", false, "error", "Product not linked in Zoho", "productId", product.getId());
            }
            int desired = stockOverride != null ? stockOverride : Math.max(0, Optional.ofNullable(product.getStock()).orElse(0));
            BigDecimal unitPrice = product.getPrice() != null && product.getPrice().compareTo(BigDecimal.ZERO) > 0
                ? product.getPrice()
                : BigDecimal.ZERO;
            if (
                !stockAdjuster.adjustItemQuantity(
                    zohoItemId,
                    desired,
                    unitPrice,
                    "STORE-STOCK-" + product.getId(),
                    "product " + product.getId() + " (" + product.getSku() + ")"
                )
            ) {
                return Map.of("ok", false, "error", "Zoho stock reconcile failed", "productId", product.getId());
            }
            reconciled = 1;
        }
        return Map.of("ok", true, "productId", product.getId(), "reconciledItems", reconciled);
    }

    private Optional<Product> resolveProductForStockReconcile(String sku, Long productId) {
        if (productId != null) {
            return productRepository.findOneWithEagerRelationships(productId);
        }
        String trimmedSku = StringUtils.trimToNull(sku);
        if (trimmedSku == null) {
            return Optional.empty();
        }
        Optional<Product> direct = productRepository.findOneBySku(trimmedSku);
        if (direct.isPresent()) {
            return direct;
        }
        return productRepository.findOneActiveByVariantSku(trimmedSku);
    }
}
