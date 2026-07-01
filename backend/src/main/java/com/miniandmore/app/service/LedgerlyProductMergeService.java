package com.miniandmore.app.service;

import com.miniandmore.app.domain.Category;
import com.miniandmore.app.domain.Product;
import com.miniandmore.app.domain.enumeration.LedgerlySyncStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Maps Ledgerly commerce item payloads onto store {@link Product} rows (pull + webhook).
 */
@Component
public class LedgerlyProductMergeService {

    private static final BigDecimal DEFAULT_TAX_PCT = BigDecimal.valueOf(5);

    private final ProductDescriptionCodec descriptionCodec;
    private final LedgerlyItemImageSync itemImageSync;

    public LedgerlyProductMergeService(ProductDescriptionCodec descriptionCodec, LedgerlyItemImageSync itemImageSync) {
        this.descriptionCodec = descriptionCodec;
        this.itemImageSync = itemImageSync;
    }

    public void mergeLedgerlyItem(Product product, Map<String, Object> item, CategoryResolver categoryResolver, NoticeCallback notice) {
        if (item.get("price") != null) {
            product.setPrice(toBigDecimal(item.get("price")));
        }
        if (item.get("stock") != null) {
            product.setStock(toInteger(item.get("stock")));
        }
        String ledgerlyItemId = stringValue(item.get("ledgerlyItemId"));
        if (ledgerlyItemId != null && !ledgerlyItemId.isBlank()) {
            product.setLedgerlyItemId(ledgerlyItemId);
        }

        if (item.get("cost") != null) {
            BigDecimal cost = toBigDecimal(item.get("cost"));
            if (cost.compareTo(BigDecimal.ZERO) > 0 && product.getCompareAtPrice() == null) {
                product.setCompareAtPrice(cost);
            }
        }

        String description = stringValue(item.get("description"));
        if (description != null) {
            ProductDescriptionCodec.Decoded decoded = descriptionCodec.decode(description);
            descriptionCodec.applyDecoded(product, decoded);
            String categoryName = descriptionCodec.categoryNameFrom(decoded);
            if (categoryName != null && !categoryName.isBlank()) {
                categoryResolver.resolve(categoryName).ifPresent(category -> {
                    product.setCategory(category);
                    if (notice != null) {
                        notice.onNotice(product.getSku(), "Assigned category " + category.getName());
                    }
                });
            }
        }

        String imageUrl = stringValue(item.get("imageUrl"));
        if (imageUrl != null && !imageUrl.isBlank()) {
            itemImageSync.importItemImage(product, imageUrl);
        }

        product.setLedgerlySyncStatus(LedgerlySyncStatus.SYNCED);
        product.setLedgerlyLastSyncedAt(Instant.now());
        product.setLedgerlySyncError(null);
    }

    public Map<String, Object> buildPushPayload(Product product, String categoryName) {
        java.util.LinkedHashMap<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("sku", product.getSku() != null ? product.getSku() : "P-" + product.getId());
        payload.put("name", product.getName());
        payload.put("price", product.getPrice());
        payload.put("stock", product.getStock());
        BigDecimal taxPct = product.getTaxPct() != null ? product.getTaxPct() : DEFAULT_TAX_PCT;
        payload.put("taxPct", taxPct);
        payload.put("unit", "ea");
        String hsn = product.getHsn() != null ? product.getHsn() : "";
        payload.put("hsn", hsn);

        BigDecimal cost = product.getCompareAtPrice();
        if (cost == null || cost.compareTo(BigDecimal.ZERO) <= 0) {
            if (product.getPrice() != null && product.getPrice().compareTo(BigDecimal.ZERO) > 0) {
                cost = product.getPrice().multiply(BigDecimal.valueOf(0.6)).setScale(2, RoundingMode.HALF_UP);
            } else {
                cost = BigDecimal.ZERO;
            }
        }
        payload.put("cost", cost);

        String encoded = descriptionCodec.encode(product, categoryName);
        if (encoded != null && !encoded.isBlank()) {
            payload.put("description", encoded);
        }

        String firstImage = itemImageSync.firstImageUrl(product.getImagesJson());
        if (firstImage != null) {
            payload.put("imageUrl", firstImage);
        }
        return payload;
    }

    @FunctionalInterface
    public interface CategoryResolver {
        Optional<Category> resolve(String categoryName);
    }

    @FunctionalInterface
    public interface NoticeCallback {
        void onNotice(String sku, String message);
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
}
