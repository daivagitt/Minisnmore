package com.miniandmore.app.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.miniandmore.app.domain.Product;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

/**
 * Parse / lookup / aggregate Ledgerly-style sparse size×color variant matrices stored on {@link Product}.
 */
@Component
public class ProductVariantSupport {

    public static final String STRUCTURE_VARIANTS = "VARIANTS";
    public static final String STRUCTURE_SIMPLE = "SIMPLE";

    private static final BigDecimal STORE_UAT_DISCOUNT_MULTIPLIER = new BigDecimal("0.80");

    private final ObjectMapper objectMapper;

    public ProductVariantSupport(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public boolean isVariants(Product product) {
        return product != null && isVariantsStructure(product.getItemStructure());
    }

    public boolean isVariantsStructure(String itemStructure) {
        return STRUCTURE_VARIANTS.equalsIgnoreCase(normalize(itemStructure));
    }

    public List<VariantRow> parse(String variantsJson) {
        return tryParse(variantsJson).orElse(List.of());
    }

    /**
     * Parse variants JSON. Empty input → empty success. Malformed non-blank input → empty Optional (parse failure).
     */
    public Optional<List<VariantRow>> tryParse(String variantsJson) {
        if (variantsJson == null || variantsJson.isBlank()) {
            return Optional.of(List.of());
        }
        try {
            String trimmed = variantsJson.trim();
            List<Map<String, Object>> raw;
            if (trimmed.startsWith("[")) {
                raw = objectMapper.readValue(trimmed, new TypeReference<List<Map<String, Object>>>() {});
            } else {
                String inner = objectMapper.readValue(trimmed, String.class);
                raw = objectMapper.readValue(inner, new TypeReference<List<Map<String, Object>>>() {});
            }
            List<VariantRow> rows = new ArrayList<>();
            for (Map<String, Object> map : raw) {
                if (map == null) {
                    continue;
                }
                rows.add(VariantRow.fromMap(map));
            }
            return Optional.of(rows);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /** Coerce Ledgerly payload field (String or already-decoded List/Map) to a JSON array string. */
    public String coerceVariantsJson(Object variantsRaw) {
        if (variantsRaw == null) {
            return null;
        }
        if (variantsRaw instanceof String s) {
            return s;
        }
        try {
            return objectMapper.writeValueAsString(variantsRaw);
        } catch (Exception e) {
            return null;
        }
    }

    public List<VariantRow> parseProduct(Product product) {
        return product == null ? List.of() : parse(product.getVariantsJson());
    }

    public String serialize(List<VariantRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return "[]";
        }
        List<Map<String, Object>> maps = new ArrayList<>();
        for (VariantRow row : rows) {
            maps.add(row.toMap());
        }
        try {
            return objectMapper.writeValueAsString(maps);
        } catch (Exception e) {
            return "[]";
        }
    }

    public Optional<VariantRow> findBySku(List<VariantRow> rows, String sku) {
        String want = normalize(sku);
        if (want == null) {
            return Optional.empty();
        }
        return rows
            .stream()
            .filter(r -> want.equalsIgnoreCase(normalize(r.sku())))
            .findFirst();
    }

    public Optional<VariantRow> findBySizeColor(List<VariantRow> rows, String size, String color) {
        String wantSize = normalizeKey(size);
        String wantColor = normalizeKey(color);
        return rows
            .stream()
            .filter(r -> Objects.equals(normalizeKey(r.size()), wantSize) && Objects.equals(normalizeKey(r.color()), wantColor))
            .findFirst();
    }

    public Optional<VariantRow> resolveSellable(Product product, String variantSku, String size, String color) {
        if (!isVariants(product)) {
            return Optional.empty();
        }
        List<VariantRow> rows = parseProduct(product);
        // If the client sent an explicit SKU, it must match a sellable variant row.
        // Do not fall back to size/color — that would accept the parent SKU with a matching combo.
        if (normalize(variantSku) != null) {
            return findBySku(rows, variantSku).filter(VariantRow::active);
        }
        return findBySizeColor(rows, size, color).filter(VariantRow::active);
    }

    /** Includes sizes from inactive rows too, so the storefront can render them as disabled instead of hiding them. */
    public List<String> sizes(List<VariantRow> rows) {
        Set<String> sizes = new LinkedHashSet<>();
        for (VariantRow row : rows) {
            String size = normalize(row.size());
            if (size != null) {
                sizes.add(row.size().trim());
            }
        }
        return new ArrayList<>(sizes);
    }

    public List<String> colors(List<VariantRow> rows) {
        Set<String> colors = new LinkedHashSet<>();
        for (VariantRow row : rows) {
            if (!row.active()) {
                continue;
            }
            String color = normalize(row.color());
            if (color != null) {
                colors.add(row.color().trim());
            }
        }
        return new ArrayList<>(colors);
    }

    public List<String> colorsForSize(List<VariantRow> rows, String size) {
        String wantSize = normalizeKey(size);
        Set<String> colors = new LinkedHashSet<>();
        for (VariantRow row : rows) {
            if (!row.active()) {
                continue;
            }
            if (!Objects.equals(normalizeKey(row.size()), wantSize)) {
                continue;
            }
            String color = normalize(row.color());
            if (color != null) {
                colors.add(row.color().trim());
            }
        }
        return new ArrayList<>(colors);
    }

    public Aggregates aggregates(List<VariantRow> rows) {
        BigDecimal minSelling = null;
        BigDecimal minMrp = null;
        BigDecimal minCost = null;
        int stockSum = 0;
        for (VariantRow row : rows) {
            if (!row.active()) {
                continue;
            }
            if (row.rate() != null && row.rate().compareTo(BigDecimal.ZERO) > 0) {
                minSelling = minSelling == null ? row.rate() : minSelling.min(row.rate());
            }
            if (row.mrp() != null && row.mrp().compareTo(BigDecimal.ZERO) > 0) {
                minMrp = minMrp == null ? row.mrp() : minMrp.min(row.mrp());
            }
            if (row.cost() != null && row.cost().compareTo(BigDecimal.ZERO) > 0) {
                minCost = minCost == null ? row.cost() : minCost.min(row.cost());
            }
            stockSum += Math.max(0, row.stock());
        }
        return new Aggregates(minSelling, minMrp, minCost, stockSum);
    }

    /** Variant selling price for checkout (rate is selling; legacy rows may only have mrp). */
    public BigDecimal variantSellingPrice(VariantRow row) {
        if (row == null) {
            return BigDecimal.ZERO;
        }
        if (row.rate() != null && row.rate().compareTo(BigDecimal.ZERO) > 0) {
            return row.rate();
        }
        if (row.mrp() != null && row.mrp().compareTo(BigDecimal.ZERO) > 0) {
            return storefrontPrice(row.mrp());
        }
        return BigDecimal.ZERO;
    }

    /** Storefront selling price after 20% off MRP, matching parent merge rules. */
    public BigDecimal storefrontPrice(BigDecimal rate) {
        if (rate == null || rate.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return rate.multiply(STORE_UAT_DISCOUNT_MULTIPLIER).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Apply a matrix onto the product: persist structure/json, rebuild sizes/colors, parent price/stock.
     * @return false if structure is VARIANTS but JSON was non-empty and failed to parse (product left unchanged for matrix fields)
     */
    public boolean applyMatrixToProduct(Product product, String itemStructure, String variantsJson) {
        String structure = normalize(itemStructure);
        if (structure == null) {
            structure = STRUCTURE_SIMPLE;
        }
        product.setItemStructure(structure.toUpperCase(Locale.ROOT));

        if (!isVariantsStructure(structure)) {
            product.setVariantsJson(null);
            return true;
        }

        String json = variantsJson != null ? variantsJson : product.getVariantsJson();
        Optional<List<VariantRow>> parsed = tryParse(json);
        if (parsed.isEmpty()) {
            return false;
        }
        List<VariantRow> rows = parsed.orElseThrow();
        if (rows.isEmpty() && json != null && !json.isBlank() && !"[]".equals(json.trim())) {
            return false;
        }

        List<String> sizeList = sizes(rows);
        List<String> colorList = colors(rows);
        String sizesJson;
        String mergedColors;
        try {
            sizesJson = objectMapper.writeValueAsString(sizeList);
            mergedColors = mergeColorsPreservingGalleries(
                product.getColorsJson(),
                product.getColorImagesJson(),
                colorList
            );
        } catch (Exception e) {
            return false;
        }
        product.setVariantsJson(serialize(rows));
        product.setSizesJson(sizesJson);
        product.setColorsJson(mergedColors);

        Aggregates agg = aggregates(rows);
        if (agg.minSelling() != null) {
            product.setPrice(agg.minSelling());
        }
        if (agg.minMrp() != null) {
            product.setCompareAtPrice(agg.minMrp());
        } else if (agg.minSelling() != null) {
            product.setCompareAtPrice(
                agg.minSelling().divide(STORE_UAT_DISCOUNT_MULTIPLIER, 2, RoundingMode.HALF_UP)
            );
        }
        product.setStock(agg.stockSum());
        return true;
    }

    /**
     * Matrix rebuild emits colour names; keep existing front/rear gallery URLs and swatches for those names.
     */
    String mergeColorsPreservingGalleries(String previousColorsJson, List<String> colorNames) throws Exception {
        return mergeColorsPreservingGalleries(previousColorsJson, null, colorNames);
    }

    String mergeColorsPreservingGalleries(String previousColorsJson, String colorImagesJson, List<String> colorNames)
        throws Exception {
        if (colorNames == null || colorNames.isEmpty()) {
            return previousColorsJson != null ? previousColorsJson : "[]";
        }
        Map<String, JsonNode> richByKey = indexRichColorNodes(previousColorsJson, colorImagesJson);
        if (richByKey.isEmpty()) {
            return objectMapper.writeValueAsString(colorNames);
        }
        ArrayNode out = objectMapper.createArrayNode();
        Set<String> seen = new LinkedHashSet<>();
        for (String rawName : colorNames) {
            String name = normalize(rawName);
            if (name == null || !seen.add(name.toLowerCase(Locale.ROOT))) {
                continue;
            }
            JsonNode previous = richByKey.get(name.toLowerCase(Locale.ROOT));
            if (previous != null && previous.isObject()) {
                ObjectNode copy = previous.deepCopy();
                copy.put("name", name);
                out.add(copy);
            } else {
                out.add(name);
            }
        }
        return objectMapper.writeValueAsString(out);
    }

    private Map<String, JsonNode> indexRichColorNodes(String colorsJson, String colorImagesJson) {
        Map<String, JsonNode> out = new LinkedHashMap<>();
        if (colorsJson != null && !colorsJson.isBlank()) {
            try {
                JsonNode root = objectMapper.readTree(colorsJson);
                if (root != null && root.isArray()) {
                    for (JsonNode node : root) {
                        if (node == null || !node.isObject()) {
                            continue;
                        }
                        String name = node.path("name").asText("");
                        if (name.isBlank()) {
                            continue;
                        }
                        boolean hasMedia =
                            node.hasNonNull("imageUrl") ||
                            node.hasNonNull("swatchUrl") ||
                            (node.has("imageUrls") && node.get("imageUrls").isArray() && node.get("imageUrls").size() > 0);
                        if (!hasMedia) {
                            continue;
                        }
                        out.putIfAbsent(name.trim().toLowerCase(Locale.ROOT), node);
                    }
                }
            } catch (Exception ignored) {
                // keep whatever was indexed
            }
        }
        if (colorImagesJson == null || colorImagesJson.isBlank()) {
            return out;
        }
        try {
            JsonNode root = objectMapper.readTree(colorImagesJson);
            if (root == null || !root.isObject()) {
                return out;
            }
            var fields = root.fields();
            while (fields.hasNext()) {
                var entry = fields.next();
                String name = entry.getKey();
                JsonNode urls = entry.getValue();
                if (name == null || name.isBlank() || urls == null || !urls.isArray() || urls.isEmpty()) {
                    continue;
                }
                String key = name.trim().toLowerCase(Locale.ROOT);
                if (out.containsKey(key)) {
                    continue;
                }
                ObjectNode node = objectMapper.createObjectNode();
                node.put("name", name.trim());
                node.set("imageUrls", urls.deepCopy());
                out.put(key, node);
            }
        } catch (Exception ignored) {
            return out;
        }
        return out;
    }

    /** Keep variant matrix colour labels in sync after print names are realigned to gallery keys. */
    public void renameVariantColors(Product product, Map<String, String> rename) {
        if (product == null || rename == null || rename.isEmpty() || !isVariants(product)) {
            return;
        }
        List<VariantRow> rows = new ArrayList<>(parseProduct(product));
        boolean changed = false;
        for (int i = 0; i < rows.size(); i++) {
            VariantRow row = rows.get(i);
            String nextColor = rename.getOrDefault(row.color(), row.color());
            if (!Objects.equals(row.color(), nextColor)) {
                rows.set(
                    i,
                    row.withColor(nextColor)
                );
                changed = true;
            }
        }
        if (changed) {
            product.setVariantsJson(serialize(rows));
        }
    }

    /**
     * Decrement stock for a variant SKU (or size+color) and re-aggregate parent stock.
     * @return false if the row could not be found or stock is insufficient
     */
    public boolean decrementVariantStock(Product product, String variantSku, String size, String color, int qty) {
        if (!isVariants(product) || qty <= 0) {
            return false;
        }
        List<VariantRow> rows = new ArrayList<>(parseProduct(product));
        Optional<VariantRow> match = findBySku(rows, variantSku);
        if (match.isEmpty()) {
            match = findBySizeColor(rows, size, color);
        }
        if (match.isEmpty() || !match.orElseThrow().active()) {
            return false;
        }
        VariantRow row = match.orElseThrow();
        if (row.stock() < qty) {
            return false;
        }
        int targetIndex = rows.indexOf(row);
        List<VariantRow> updated = replaceRowAt(
            rows,
            targetIndex,
            row.withStock(row.stock() - qty)
        );
        product.setVariantsJson(serialize(updated));
        product.setStock(aggregates(updated).stockSum());
        return true;
    }

    /**
     * Restore stock for a variant line (reservation release).
     */
    public boolean incrementVariantStock(Product product, String variantSku, String size, String color, int qty) {
        if (!isVariants(product) || qty <= 0) {
            return false;
        }
        List<VariantRow> rows = new ArrayList<>(parseProduct(product));
        Optional<VariantRow> match = findBySku(rows, variantSku);
        if (match.isEmpty()) {
            match = findBySizeColor(rows, size, color);
        }
        if (match.isEmpty()) {
            return false;
        }
        VariantRow row = match.orElseThrow();
        int targetIndex = rows.indexOf(row);
        List<VariantRow> updated = replaceRowAt(
            rows,
            targetIndex,
            row.withStock(row.stock() + qty)
        );
        product.setVariantsJson(serialize(updated));
        product.setStock(aggregates(updated).stockSum());
        return true;
    }

    public boolean updateVariantStockBySku(Product product, String variantSku, int stock) {
        if (!isVariants(product)) {
            return false;
        }
        List<VariantRow> rows = new ArrayList<>(parseProduct(product));
        Optional<VariantRow> match = findBySku(rows, variantSku);
        if (match.isEmpty()) {
            return false;
        }
        VariantRow row = match.orElseThrow();
        int targetIndex = rows.indexOf(row);
        List<VariantRow> updated = replaceRowAt(rows, targetIndex, row.withStock(Math.max(0, stock)));
        product.setVariantsJson(serialize(updated));
        product.setStock(aggregates(updated).stockSum());
        return true;
    }

    public Optional<String> zohoItemIdForSku(Product product, String variantSku) {
        if (!isVariants(product)) {
            return Optional.empty();
        }
        return findBySku(parseProduct(product), variantSku).map(VariantRow::zohoItemId).filter(id -> id != null && !id.isBlank());
    }

    public boolean setVariantZohoItemId(Product product, String variantSku, String zohoItemId) {
        if (!isVariants(product) || StringUtils.isBlank(variantSku) || StringUtils.isBlank(zohoItemId)) {
            return false;
        }
        List<VariantRow> rows = new ArrayList<>(parseProduct(product));
        Optional<VariantRow> match = findBySku(rows, variantSku);
        if (match.isEmpty()) {
            return false;
        }
        VariantRow row = match.orElseThrow();
        int targetIndex = rows.indexOf(row);
        List<VariantRow> updated = replaceRowAt(rows, targetIndex, row.withZohoItemId(zohoItemId.trim()));
        product.setVariantsJson(serialize(updated));
        return true;
    }

    /** Attach a pulled Zoho image to this SKU so same-colour sizes keep distinct photos. */
    public boolean setVariantImageUrl(Product product, String variantSku, String imageUrl) {
        if (product == null || StringUtils.isBlank(variantSku) || StringUtils.isBlank(imageUrl)) {
            return false;
        }
        List<String> merged = new ArrayList<>();
        merged.add(imageUrl.trim());
        Optional<VariantRow> existing = findBySku(parseProduct(product), variantSku);
        if (existing.isPresent() && existing.orElseThrow().imageUrls() != null) {
            for (String extra : existing.orElseThrow().imageUrls()) {
                if (StringUtils.isNotBlank(extra) && !extra.trim().equals(imageUrl.trim())) {
                    merged.add(extra.trim());
                }
            }
        }
        return setVariantImages(product, variantSku, merged);
    }

    /** Store this SKU's Zoho photos: first is the hero, the rest are extras (rear, lifestyle). */
    public boolean setVariantImages(Product product, String variantSku, List<String> imageUrls) {
        if (product == null || StringUtils.isBlank(variantSku) || imageUrls == null || imageUrls.isEmpty()) {
            return false;
        }
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String url : imageUrls) {
            if (StringUtils.isNotBlank(url)) {
                unique.add(url.trim());
            }
        }
        if (unique.isEmpty()) {
            return false;
        }
        List<VariantRow> rows = new ArrayList<>(parseProduct(product));
        Optional<VariantRow> match = findBySku(rows, variantSku);
        if (match.isEmpty()) {
            return false;
        }
        VariantRow row = match.orElseThrow();
        int targetIndex = rows.indexOf(row);
        List<String> ordered = new ArrayList<>(unique);
        String hero = ordered.get(0);
        List<String> extras = ordered.size() > 1 ? List.copyOf(ordered.subList(1, ordered.size())) : List.of();
        product.setVariantsJson(
            serialize(replaceRowAt(rows, targetIndex, row.withImageUrl(hero).withImageUrls(extras)))
        );
        return true;
    }

    /** Remove photos from a SKU after Zoho no longer has an item image. */
    public boolean clearVariantImages(Product product, String variantSku) {
        if (product == null || StringUtils.isBlank(variantSku)) {
            return false;
        }
        List<VariantRow> rows = new ArrayList<>(parseProduct(product));
        Optional<VariantRow> match = findBySku(rows, variantSku);
        if (match.isEmpty()) {
            return false;
        }
        VariantRow row = match.orElseThrow();
        int targetIndex = rows.indexOf(row);
        product.setVariantsJson(serialize(replaceRowAt(rows, targetIndex, row.withImageUrl(null).withImageUrls(List.of()))));
        return true;
    }

    /** Drop every SKU photo on a variant parent (Zoho/Ledgerly pull must not resurrect them). */
    public void clearAllVariantImages(Product product) {
        if (product == null || !isVariants(product)) {
            return;
        }
        List<VariantRow> rows = parseProduct(product);
        if (rows.isEmpty()) {
            return;
        }
        List<VariantRow> cleared = new ArrayList<>(rows.size());
        boolean changed = false;
        for (VariantRow row : rows) {
            if (StringUtils.isNotBlank(row.imageUrl()) || (row.imageUrls() != null && !row.imageUrls().isEmpty())) {
                changed = true;
                cleared.add(row.withImageUrl(null).withImageUrls(List.of()));
            } else {
                cleared.add(row);
            }
        }
        if (changed) {
            product.setVariantsJson(serialize(cleared));
        }
    }

    /**
     * Merge one Zoho Books item (matched by variant SKU) into the parent matrix.
     * @return false when the SKU is not on the matrix
     */
    public boolean mergePulledVariantFromZoho(
        Product product,
        String variantSku,
        BigDecimal sellingRate,
        BigDecimal mrp,
        Integer stock,
        String zohoItemId
    ) {
        return upsertPulledVariantFromZoho(product, variantSku, null, null, sellingRate, mrp, stock, zohoItemId, false);
    }

    /**
     * Update an existing matrix row or append a new one (Zoho Books grouped items / new SKUs).
     * @param addIfMissing when false, behaves like {@link #mergePulledVariantFromZoho}
     */
    public boolean upsertPulledVariantFromZoho(
        Product product,
        String variantSku,
        String size,
        String color,
        BigDecimal sellingRate,
        BigDecimal mrp,
        Integer stock,
        String zohoItemId,
        boolean addIfMissing
    ) {
        if (product == null || StringUtils.isBlank(variantSku)) {
            return false;
        }
        List<VariantRow> rows = new ArrayList<>(parseProduct(product));
        Optional<VariantRow> match = findBySku(rows, variantSku);
        if (match.isEmpty()) {
            if (!addIfMissing) {
                return false;
            }
            String id = "z-" + variantSku.trim().replaceAll("[^A-Za-z0-9]+", "-");
            rows.add(
                new VariantRow(
                    id,
                    variantSku.trim(),
                    normalize(size),
                    normalize(color),
                    sellingRate != null ? sellingRate : BigDecimal.ZERO,
                    mrp,
                    BigDecimal.ZERO,
                    stock != null ? Math.max(0, stock) : 0,
                    true,
                    null,
                    StringUtils.trimToNull(zohoItemId),
                    null,
                    List.of()
                )
            );
            return applyMatrixToProduct(product, STRUCTURE_VARIANTS, serialize(rows));
        }
        VariantRow row = match.orElseThrow();
        int targetIndex = rows.indexOf(row);
        BigDecimal nextRate = row.rate();
        if (sellingRate != null && sellingRate.compareTo(BigDecimal.ZERO) > 0) {
            nextRate = sellingRate;
        }
        BigDecimal nextMrp = row.mrp();
        if (mrp != null && mrp.compareTo(BigDecimal.ZERO) > 0) {
            nextMrp = mrp;
        }
        int nextStock = stock != null ? Math.max(0, stock) : row.stock();
        String nextZohoId = StringUtils.isNotBlank(zohoItemId) ? zohoItemId.trim() : row.zohoItemId();
        String nextSize = StringUtils.isNotBlank(size) ? size.trim() : row.size();
        String nextColor = StringUtils.isNotBlank(color) ? color.trim() : row.color();
        VariantRow updatedRow = row
            .withPricing(nextRate, nextMrp)
            .withStock(nextStock)
            .withZohoItemId(nextZohoId)
            .withSize(nextSize)
            .withColor(nextColor);
        List<VariantRow> updated = replaceRowAt(rows, targetIndex, updatedRow);
        return applyMatrixToProduct(product, STRUCTURE_VARIANTS, serialize(updated));
    }

    /** Whether any variant row on this parent still appears in the pulled Zoho SKU set. */
    public boolean anyVariantSkuInSet(Product product, Set<String> pulledSkus) {
        if (!isVariants(product) || pulledSkus == null || pulledSkus.isEmpty()) {
            return false;
        }
        for (VariantRow row : parseProduct(product)) {
            String sku = normalize(row.sku());
            if (sku != null && pulledSkus.contains(sku)) {
                return true;
            }
        }
        return false;
    }

    private static List<VariantRow> replaceRowAt(List<VariantRow> rows, int index, VariantRow replacement) {
        List<VariantRow> updated = new ArrayList<>(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            updated.add(i == index ? replacement : rows.get(i));
        }
        return updated;
    }

    public Optional<Product> findProductContainingVariantSku(List<Product> candidates, String variantSku) {
        String want = normalize(variantSku);
        if (want == null) {
            return Optional.empty();
        }
        for (Product product : candidates) {
            if (!isVariants(product)) {
                continue;
            }
            if (findBySku(parseProduct(product), want).isPresent()) {
                return Optional.of(product);
            }
        }
        return Optional.empty();
    }

    public static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String normalizeKey(String value) {
        String n = normalize(value);
        return n == null ? "" : n.toLowerCase(Locale.ROOT);
    }

    public record Aggregates(BigDecimal minSelling, BigDecimal minMrp, BigDecimal minCost, int stockSum) {}

    public record VariantRow(
        String id,
        String sku,
        String size,
        String color,
        BigDecimal rate,
        BigDecimal mrp,
        BigDecimal cost,
        int stock,
        boolean active,
        String swatchUrl,
        String zohoItemId,
        String imageUrl,
        List<String> imageUrls
    ) {
        public VariantRow {
            imageUrls = imageUrls == null ? List.of() : imageUrls;
        }

        VariantRow withColor(String nextColor) {
            return new VariantRow(id, sku, size, nextColor, rate, mrp, cost, stock, active, swatchUrl, zohoItemId, imageUrl, imageUrls);
        }

        VariantRow withSize(String nextSize) {
            return new VariantRow(id, sku, nextSize, color, rate, mrp, cost, stock, active, swatchUrl, zohoItemId, imageUrl, imageUrls);
        }

        VariantRow withStock(int nextStock) {
            return new VariantRow(id, sku, size, color, rate, mrp, cost, nextStock, active, swatchUrl, zohoItemId, imageUrl, imageUrls);
        }

        VariantRow withPricing(BigDecimal nextRate, BigDecimal nextMrp) {
            return new VariantRow(id, sku, size, color, nextRate, nextMrp, cost, stock, active, swatchUrl, zohoItemId, imageUrl, imageUrls);
        }

        VariantRow withZohoItemId(String nextZohoItemId) {
            return new VariantRow(id, sku, size, color, rate, mrp, cost, stock, active, swatchUrl, nextZohoItemId, imageUrl, imageUrls);
        }

        VariantRow withImageUrl(String nextImageUrl) {
            return new VariantRow(id, sku, size, color, rate, mrp, cost, stock, active, swatchUrl, zohoItemId, nextImageUrl, imageUrls);
        }

        VariantRow withImageUrls(List<String> nextImageUrls) {
            return new VariantRow(
                id,
                sku,
                size,
                color,
                rate,
                mrp,
                cost,
                stock,
                active,
                swatchUrl,
                zohoItemId,
                imageUrl,
                nextImageUrls == null ? List.of() : List.copyOf(nextImageUrls)
            );
        }

        static VariantRow fromMap(Map<String, Object> map) {
            BigDecimal rateRaw = toBigDecimal(map.get("rate"));
            BigDecimal mrpRaw = map.containsKey("mrp") ? toBigDecimal(map.get("mrp")) : null;
            BigDecimal priceRaw = map.get("price") != null ? toBigDecimal(map.get("price")) : null;
            boolean hasExplicitMrp = mrpRaw != null && mrpRaw.compareTo(BigDecimal.ZERO) > 0;
            boolean hasExplicitSelling = priceRaw != null && priceRaw.compareTo(BigDecimal.ZERO) > 0;
            boolean legacyRateIsMrp =
                rateRaw.compareTo(BigDecimal.ZERO) > 0 && !hasExplicitMrp && !hasExplicitSelling && !map.containsKey("mrp");

            BigDecimal rate;
            BigDecimal mrp;
            if (legacyRateIsMrp) {
                mrp = rateRaw;
                rate = rateRaw.multiply(STORE_UAT_DISCOUNT_MULTIPLIER).setScale(2, RoundingMode.HALF_UP);
            } else {
                rate = hasExplicitSelling ? priceRaw : rateRaw;
                mrp = hasExplicitMrp ? mrpRaw : null;
            }
            return new VariantRow(
                stringValue(map.get("id")),
                stringValue(map.get("sku")),
                stringValue(map.get("size")),
                stringValue(map.get("color")),
                rate,
                mrp,
                toBigDecimal(map.get("cost")),
                toInt(map.get("stock")),
                toBoolean(map.get("active"), true),
                swatchUrlValue(map),
                zohoItemIdValue(map),
                imageUrlValue(map),
                imageUrlsValue(map)
            );
        }

        Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            if (id != null) {
                map.put("id", id);
            }
            if (sku != null) {
                map.put("sku", sku);
            }
            if (size != null) {
                map.put("size", size);
            }
            if (color != null) {
                map.put("color", color);
            }
            map.put("rate", rate != null ? rate : BigDecimal.ZERO);
            if (mrp != null && mrp.compareTo(BigDecimal.ZERO) > 0) {
                map.put("mrp", mrp);
            }
            map.put("cost", cost != null ? cost : BigDecimal.ZERO);
            map.put("stock", stock);
            map.put("active", active);
            if (swatchUrl != null && !swatchUrl.isBlank()) {
                map.put("swatchUrl", swatchUrl.trim());
            }
            if (zohoItemId != null && !zohoItemId.isBlank()) {
                map.put("zohoItemId", zohoItemId.trim());
            }
            if (imageUrl != null && !imageUrl.isBlank()) {
                map.put("imageUrl", imageUrl.trim());
            }
            if (imageUrls != null && !imageUrls.isEmpty()) {
                List<String> extras = new ArrayList<>();
                for (String url : imageUrls) {
                    if (url != null && !url.isBlank() && (imageUrl == null || !imageUrl.trim().equals(url.trim()))) {
                        extras.add(url.trim());
                    }
                }
                if (!extras.isEmpty()) {
                    map.put("imageUrls", extras);
                }
            }
            return map;
        }

        private static String zohoItemIdValue(Map<String, Object> map) {
            String camel = stringValue(map.get("zohoItemId"));
            if (camel != null) {
                return camel;
            }
            return stringValue(map.get("zoho_item_id"));
        }

        private static String swatchUrlValue(Map<String, Object> map) {
            String camel = stringValue(map.get("swatchUrl"));
            if (camel != null) {
                return camel;
            }
            return stringValue(map.get("swatch_url"));
        }

        private static String imageUrlValue(Map<String, Object> map) {
            String camel = stringValue(map.get("imageUrl"));
            if (camel != null) {
                return camel;
            }
            return stringValue(map.get("image_url"));
        }

        private static List<String> imageUrlsValue(Map<String, Object> map) {
            Object raw = map.get("imageUrls");
            if (raw == null) {
                raw = map.get("image_urls");
            }
            if (!(raw instanceof List<?> list) || list.isEmpty()) {
                return List.of();
            }
            LinkedHashSet<String> unique = new LinkedHashSet<>();
            for (Object value : list) {
                String url = stringValue(value);
                if (url != null) {
                    unique.add(url);
                }
            }
            return unique.isEmpty() ? List.of() : List.copyOf(unique);
        }

        private static String stringValue(Object value) {
            return value == null ? null : value.toString().trim().isEmpty() ? null : value.toString().trim();
        }

        private static BigDecimal toBigDecimal(Object value) {
            if (value == null) {
                return BigDecimal.ZERO;
            }
            if (value instanceof BigDecimal bd) {
                return bd;
            }
            if (value instanceof Number number) {
                return BigDecimal.valueOf(number.doubleValue());
            }
            try {
                return new BigDecimal(value.toString().trim());
            } catch (Exception e) {
                return BigDecimal.ZERO;
            }
        }

        private static int toInt(Object value) {
            if (value == null) {
                return 0;
            }
            if (value instanceof Number number) {
                return number.intValue();
            }
            try {
                return new BigDecimal(value.toString().trim()).intValue();
            } catch (Exception e) {
                return 0;
            }
        }

        private static boolean toBoolean(Object value, boolean defaultValue) {
            if (value == null) {
                return defaultValue;
            }
            if (value instanceof Boolean b) {
                return b;
            }
            String text = value.toString().trim();
            if (text.isEmpty()) {
                return defaultValue;
            }
            return !"false".equalsIgnoreCase(text) && !"0".equals(text);
        }
    }
}
