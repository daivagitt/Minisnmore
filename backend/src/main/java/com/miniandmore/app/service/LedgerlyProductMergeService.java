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

    /** Storefront UAT price = selling price minus this discount (compare-at / Ledgerly rate). */
    private static final BigDecimal STORE_UAT_DISCOUNT_MULTIPLIER = new BigDecimal("0.80");

    private static final BigDecimal ESTIMATED_COST_RATIO = new BigDecimal("0.60");
    private final ProductDescriptionCodec descriptionCodec;
    private final LedgerlyItemImageSync itemImageSync;
    private final ProductVariantSupport variantSupport;

    public LedgerlyProductMergeService(
        ProductDescriptionCodec descriptionCodec,
        LedgerlyItemImageSync itemImageSync,
        ProductVariantSupport variantSupport
    ) {
        this.descriptionCodec = descriptionCodec;
        this.itemImageSync = itemImageSync;
        this.variantSupport = variantSupport;
    }

    public void mergeLedgerlyItem(Product product, Map<String, Object> item, CategoryResolver categoryResolver, NoticeCallback notice) {
        BigDecimal ledgerlySellingRate = firstPrice(item);
        if (item.get("stock") != null) {
            product.setStock(toInteger(item.get("stock")));
        }
        String ledgerlyItemId = stringValue(item.get("ledgerlyItemId"));
        if (ledgerlyItemId != null && !ledgerlyItemId.isBlank()) {
            product.setLedgerlyItemId(ledgerlyItemId);
        }

        ProductDescriptionCodec.Decoded decoded = null;
        String description = stringValue(item.get("description"));
        if (description != null) {
            decoded = descriptionCodec.decode(description);
            descriptionCodec.applyDecoded(product, decoded);
        }

        BigDecimal sellingPrice = ledgerlySellingRate;
        if (decoded != null && decoded.compareAtPrice() != null && decoded.compareAtPrice().compareTo(BigDecimal.ZERO) > 0) {
            sellingPrice = decoded.compareAtPrice();
        }
        if (sellingPrice != null && sellingPrice.compareTo(BigDecimal.ZERO) > 0) {
            product.setCompareAtPrice(sellingPrice);
            product.setPrice(storeUatPrice(sellingPrice));
        }

        String fabric = firstNonBlank(stringValue(item.get("fabricType")), stringValue(item.get("fabric")));
        if (fabric != null) {
            product.setFabricType(fabric);
        }

        applyAgeFromItem(product, item, decoded);

        List<String> sizes = descriptionCodec.parseDelimitedOrJson(stringValue(item.get("sizes")));
        if (!sizes.isEmpty()) {
            product.setSizesJson(descriptionCodec.toJsonList(sizes));
        }

        List<String> colors = descriptionCodec.parseDelimitedOrJson(stringValue(item.get("colors")));
        if (!colors.isEmpty()) {
            product.setColorsJson(descriptionCodec.toJsonList(colors));
        }

        applyVariantsFromItem(product, item);

        String categoryName = firstNonBlank(stringValue(item.get("category")), descriptionCodec.categoryNameFrom(decoded));
        if (categoryName != null) {
            categoryResolver.resolve(categoryName).ifPresent(category -> {
                product.setCategory(category);
                if (notice != null) {
                    notice.onNotice(product.getSku(), "Assigned category " + category.getName());
                }
            });
        }

        String imageUrl = stringValue(item.get("imageUrl"));
        if (imageUrl != null && !imageUrl.isBlank()) {
            itemImageSync.importItemImage(product, imageUrl);
        }

        product.setLedgerlySyncStatus(LedgerlySyncStatus.SYNCED);
        product.setLedgerlyLastSyncedAt(Instant.now());
        product.setLedgerlySyncError(null);
    }

    private void applyVariantsFromItem(Product product, Map<String, Object> item) {
        String itemStructure = firstNonBlank(stringValue(item.get("itemStructure")), stringValue(item.get("item_structure")));
        Object variantsRaw = item.containsKey("variantsJson") ? item.get("variantsJson") : item.get("variants_json");
        if (itemStructure == null && variantsRaw == null) {
            return;
        }
        String variantsJson = variantSupport.coerceVariantsJson(variantsRaw);
        if (itemStructure == null) {
            itemStructure = variantsJson != null && !variantsJson.isBlank() && !"[]".equals(variantsJson.trim())
                ? ProductVariantSupport.STRUCTURE_VARIANTS
                : ProductVariantSupport.STRUCTURE_SIMPLE;
        }
        if (!variantSupport.applyMatrixToProduct(product, itemStructure, variantsJson)) {
            // Keep previously merged parent fields; do not wipe matrix on bad payload.
            if (product.getItemStructure() == null || product.getItemStructure().isBlank()) {
                product.setItemStructure(itemStructure);
            }
        }
    }

    private BigDecimal firstPrice(Map<String, Object> item) {
        if (item.get("price") != null) {
            return toBigDecimal(item.get("price"));
        }
        if (item.get("rate") != null) {
            return toBigDecimal(item.get("rate"));
        }
        return null;
    }

    public Map<String, Object> buildPushPayload(Product product, String categoryName) {
        java.util.LinkedHashMap<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("sku", product.getSku() != null ? product.getSku() : "P-" + product.getId());
        payload.put("name", product.getName());
        BigDecimal sellingPrice = storeSellingPrice(product);
        payload.put("price", sellingPrice);
        payload.put("stock", product.getStock());
        payload.put("unit", "ea");

        BigDecimal cost = sellingPrice.multiply(ESTIMATED_COST_RATIO).setScale(2, RoundingMode.HALF_UP);
        payload.put("cost", cost);

        String description = product.getDescription();
        if (description != null && !description.isBlank()) {
            payload.put("description", description.trim());
        }

        if (categoryName != null && !categoryName.isBlank()) {
            payload.put("category", categoryName.trim());
        }
        List<String> sizes = descriptionCodec.parseDelimitedOrJson(product.getSizesJson());
        if (!sizes.isEmpty()) {
            payload.put("sizes", String.join(", ", sizes));
        }
        List<String> colors = descriptionCodec.parseDelimitedOrJson(product.getColorsJson());
        if (!colors.isEmpty()) {
            payload.put("colors", String.join(", ", colors));
        }
        if (product.getFabricType() != null && !product.getFabricType().isBlank()) {
            payload.put("fabricType", product.getFabricType().trim());
        }
        if (product.getAgeMinMonths() != null) {
            payload.put("ageMinMonths", product.getAgeMinMonths());
        }
        if (product.getAgeMaxMonths() != null) {
            payload.put("ageMaxMonths", product.getAgeMaxMonths());
        }

        String firstImage = itemImageSync.firstImageUrl(product.getImagesJson());
        if (firstImage != null) {
            payload.put("imageUrl", firstImage);
        }
        payload.put("active", Boolean.TRUE.equals(product.getActive()));
        String itemStructure = product.getItemStructure() != null ? product.getItemStructure() : ProductVariantSupport.STRUCTURE_SIMPLE;
        payload.put("itemStructure", itemStructure);
        if (product.getVariantsJson() != null && !product.getVariantsJson().isBlank()) {
            payload.put("variantsJson", product.getVariantsJson());
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

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
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

    private void applyAgeFromItem(Product product, Map<String, Object> item, ProductDescriptionCodec.Decoded decoded) {
        Integer min = intValue(item.get("ageMinMonths"));
        Integer max = intValue(item.get("ageMaxMonths"));
        if (min != null) {
            product.setAgeMinMonths(min);
        }
        if (max != null) {
            product.setAgeMaxMonths(max);
        }
        if (min == null && max == null && decoded != null) {
            if (decoded.ageMinMonths() != null) {
                product.setAgeMinMonths(decoded.ageMinMonths());
            }
            if (decoded.ageMaxMonths() != null) {
                product.setAgeMaxMonths(decoded.ageMaxMonths());
            }
        }
    }

    private Integer intValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        String text = value.toString().trim();
        if (text.isEmpty()) {
            return null;
        }
        return new BigDecimal(text).intValue();
    }

    /** Compare-at in the store is the real selling price synced to Ledgerly as item rate. */
    BigDecimal storeSellingPrice(Product product) {
        if (product.getCompareAtPrice() != null && product.getCompareAtPrice().compareTo(BigDecimal.ZERO) > 0) {
            return product.getCompareAtPrice();
        }
        if (product.getPrice() != null && product.getPrice().compareTo(BigDecimal.ZERO) > 0) {
            return product.getPrice();
        }
        return BigDecimal.ZERO;
    }

    /** UAT storefront price applies a fixed 20% discount to the selling price. */
    BigDecimal storeUatPrice(BigDecimal sellingPrice) {
        if (sellingPrice == null || sellingPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return sellingPrice.multiply(STORE_UAT_DISCOUNT_MULTIPLIER).setScale(2, RoundingMode.HALF_UP);
    }
}
