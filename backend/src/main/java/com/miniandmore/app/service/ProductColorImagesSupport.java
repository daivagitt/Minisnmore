package com.miniandmore.app.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.miniandmore.app.domain.Product;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Keeps {@code color_images_json} (Ledgerly gallery map) in sync with gallery URLs embedded in {@code colors_json}.
 * Print swatches ({@code swatchUrl}) stay in {@code colors_json} only — never in the Ledgerly gallery map.
 */
@Component
public class ProductColorImagesSupport {

    static final int MAX_IMAGES_PER_COLOR = 10;

    private final ObjectMapper objectMapper;

    public ProductColorImagesSupport(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** Normalize colours JSON and derive {@code color_images_json} from gallery URLs before persist. */
    public void syncProductColorImages(Product product) {
        if (product == null) {
            return;
        }
        Map<String, List<String>> existingGallery = parseMap(product.getColorImagesJson());
        String normalizedColors = normalizeColorsJson(product.getColorsJson());
        Map<String, List<String>> gallery = galleryMapFromColorsJson(normalizedColors);
        if (gallery.isEmpty() && !existingGallery.isEmpty()) {
            normalizedColors = applyGalleryMapToColorsJson(normalizedColors, existingGallery);
            gallery = galleryMapFromColorsJson(normalizedColors);
            if (gallery.isEmpty()) {
                normalizedColors = realignColorNamesToGalleryKeys(normalizedColors, existingGallery);
                gallery = galleryMapFromColorsJson(normalizedColors);
            }
            if (gallery.isEmpty()) {
                gallery = existingGallery;
            }
        }
        product.setColorsJson(normalizedColors);
        if (gallery.isEmpty()) {
            product.setColorImagesJson(null);
        } else {
            product.setColorImagesJson(serializeMap(gallery));
        }
    }

    /** Add a lifestyle/gallery URL for one print/colour without dropping other colours. */
    public void appendGalleryUrl(Product product, String colorName, String url) {
        if (product == null) {
            return;
        }
        String color = colorName == null ? "" : colorName.trim();
        String image = url == null ? "" : url.trim();
        if (color.isEmpty() || image.isEmpty()) {
            return;
        }
        List<ColorEntry> entries = parseColorEntries(product.getColorsJson());
        boolean found = false;
        List<ColorEntry> next = new ArrayList<>();
        for (ColorEntry entry : entries) {
            if (entry.name().trim().equalsIgnoreCase(color)) {
                found = true;
                List<String> urls = new ArrayList<>(entry.galleryUrls());
                if (!urls.contains(image)) {
                    urls.add(image);
                }
                if (urls.size() > MAX_IMAGES_PER_COLOR) {
                    urls = new ArrayList<>(urls.subList(0, MAX_IMAGES_PER_COLOR));
                }
                next.add(new ColorEntry(entry.name(), entry.swatchUrl(), uniqueUrls(urls)));
            } else {
                next.add(entry);
            }
        }
        if (!found) {
            next.add(new ColorEntry(color, null, List.of(image)));
        }
        product.setColorsJson(writeColorEntries(next));
        syncProductColorImages(product);
    }

    /**
     * Seed a colour gallery only when it has no photos yet. Same-colour / different-size Zoho SKUs
     * must not dump every size's photo into one mixed colour gallery.
     */
    public void seedGalleryUrlIfEmpty(Product product, String colorName, String url) {
        if (product == null) {
            return;
        }
        String color = colorName == null ? "" : colorName.trim();
        String image = url == null ? "" : url.trim();
        if (color.isEmpty() || image.isEmpty()) {
            return;
        }
        for (ColorEntry entry : parseColorEntries(product.getColorsJson())) {
            if (entry.name().trim().equalsIgnoreCase(color) && !entry.galleryUrls().isEmpty()) {
                return;
            }
        }
        appendGalleryUrl(product, color, image);
    }

    /**
     * Replace one colour's gallery. Empty list clears that colour so same-colour size SKUs
     * are not mixed into one map (sync must not restore the old mixed {@code color_images_json}).
     */
    public void replaceColorGallery(Product product, String colorName, List<String> urls) {
        if (product == null) {
            return;
        }
        String color = colorName == null ? "" : colorName.trim();
        if (color.isEmpty()) {
            return;
        }
        List<String> nextUrls = uniqueUrls(urls == null ? List.of() : urls);
        List<ColorEntry> entries = parseColorEntries(product.getColorsJson());
        boolean found = false;
        List<ColorEntry> next = new ArrayList<>();
        for (ColorEntry entry : entries) {
            if (entry.name().trim().equalsIgnoreCase(color)) {
                found = true;
                next.add(new ColorEntry(entry.name(), entry.swatchUrl(), nextUrls));
            } else {
                next.add(entry);
            }
        }
        if (!found && !nextUrls.isEmpty()) {
            next.add(new ColorEntry(color, null, nextUrls));
        }
        product.setColorsJson(writeColorEntries(next));
        Map<String, List<String>> gallery = parseMap(product.getColorImagesJson());
        String staleKey = null;
        for (String key : gallery.keySet()) {
            if (key != null && key.trim().equalsIgnoreCase(color)) {
                staleKey = key;
                break;
            }
        }
        if (staleKey != null) {
            gallery.remove(staleKey);
        }
        if (!nextUrls.isEmpty()) {
            gallery.put(color, nextUrls);
        }
        product.setColorImagesJson(gallery.isEmpty() ? null : serializeMap(gallery));
        syncProductColorImages(product);
    }

    /** Combined colour gallery from {@code colors_json} and {@code color_images_json}. */
    public List<String> galleryUrlsForColor(Product product, String colorName) {
        if (product == null || colorName == null || colorName.isBlank()) {
            return List.of();
        }
        String want = colorName.trim();
        LinkedHashSet<String> urls = new LinkedHashSet<>();
        for (ColorEntry entry : parseColorEntries(product.getColorsJson())) {
            if (entry.name() != null && want.equalsIgnoreCase(entry.name().trim())) {
                urls.addAll(uniqueUrls(entry.galleryUrls()));
            }
        }
        Map<String, List<String>> fromMap = parseMap(product.getColorImagesJson());
        for (Map.Entry<String, List<String>> entry : fromMap.entrySet()) {
            if (entry.getKey() != null && want.equalsIgnoreCase(entry.getKey().trim())) {
                urls.addAll(uniqueUrls(entry.getValue()));
            }
        }
        return new ArrayList<>(urls);
    }

    /** Drop SKU photos that were wrongly appended onto the parent {@code images_json} gallery. */
    public void stripUrlsFromImagesJson(Product product, java.util.Collection<String> urlsToRemove) {
        if (product == null || urlsToRemove == null || urlsToRemove.isEmpty()) {
            return;
        }
        Set<String> remove = new LinkedHashSet<>();
        for (String url : urlsToRemove) {
            if (url != null && !url.isBlank()) {
                remove.add(url.trim());
            }
        }
        if (remove.isEmpty() || isImagesJsonEmpty(product.getImagesJson())) {
            return;
        }
        List<String> kept = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(product.getImagesJson());
            if (root == null || !root.isArray()) {
                return;
            }
            for (JsonNode node : root) {
                if (node == null || !node.isTextual()) {
                    continue;
                }
                String url = node.asText().trim();
                if (url.isEmpty() || remove.contains(url)) {
                    continue;
                }
                kept.add(url);
            }
        } catch (Exception ignored) {
            return;
        }
        product.setImagesJson(kept.isEmpty() ? null : serializeStringList(kept));
    }

    /** When {@code images_json} is empty, copy unique per-colour gallery URLs for shop cards / Ledgerly hero. */
    public void ensureProductImagesFromColorGalleries(Product product) {
        if (product == null || !isImagesJsonEmpty(product.getImagesJson())) {
            return;
        }
        List<String> fromColors = collectGalleryUrlsFromColorsJson(product.getColorsJson());
        if (fromColors.isEmpty()) {
            List<String> swatches = collectSwatchUrlsFromColorsJson(product.getColorsJson());
            if (swatches.isEmpty()) {
                return;
            }
            fromColors = List.of(swatches.get(0));
        }
        if (fromColors.isEmpty()) {
            return;
        }
        product.setImagesJson(serializeStringList(fromColors));
    }

    public List<String> collectSwatchUrlsFromColorsJson(String colorsJson) {
        List<String> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (ColorEntry entry : parseColorEntries(colorsJson)) {
            String swatch = entry.swatchUrl();
            if (swatch == null || swatch.isBlank()) {
                continue;
            }
            String url = swatch.trim();
            if (seen.add(url)) {
                out.add(url);
            }
        }
        return out;
    }

    public List<String> collectGalleryUrlsFromColorsJson(String colorsJson) {
        List<String> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (ColorEntry entry : parseColorEntries(colorsJson)) {
            for (String url : entry.galleryUrls()) {
                if (seen.add(url)) {
                    out.add(url);
                }
            }
        }
        return out;
    }

    /**
     * Canonical gallery URLs for a simple product: card/primary photos from {@code images_json}, then
     * every colour gallery from {@code colors_json}. {@code color_images_json} is intentionally omitted
     * because {@link #syncProductColorImages} keeps it aligned with {@code colors_json}.
     */
    public List<String> allSimpleProductGallerySourceUrls(Product product) {
        if (product == null) {
            return List.of();
        }
        List<String> urls = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String url : parseImagesJsonUrls(product.getImagesJson())) {
            if (seen.add(url)) {
                urls.add(url);
            }
        }
        for (String url : collectGalleryUrlsFromColorsJson(product.getColorsJson())) {
            if (seen.add(url)) {
                urls.add(url);
            }
        }
        return urls;
    }

    private List<String> parseImagesJsonUrls(String imagesJson) {
        if (imagesJson == null || imagesJson.isBlank()) {
            return List.of();
        }
        try {
            List<String> raw = objectMapper.readValue(imagesJson, new TypeReference<List<String>>() {});
            List<String> urls = new ArrayList<>();
            for (String value : raw) {
                if (value != null) {
                    String trimmed = value.trim();
                    if (!trimmed.isEmpty()) {
                        urls.add(trimmed);
                    }
                }
            }
            return urls;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    public boolean isImagesJsonEmpty(String imagesJson) {
        if (imagesJson == null || imagesJson.isBlank()) {
            return true;
        }
        try {
            JsonNode root = objectMapper.readTree(imagesJson);
            if (!root.isArray() || root.isEmpty()) {
                return true;
            }
            for (JsonNode node : root) {
                if (node.isTextual() && !node.asText().trim().isEmpty()) {
                    return false;
                }
            }
            return true;
        } catch (Exception ignored) {
            return true;
        }
    }

    private String serializeStringList(List<String> urls) {
        try {
            return objectMapper.writeValueAsString(urls == null ? List.of() : urls);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to serialize images JSON", e);
        }
    }

    /** Colour name → main gallery URLs (excludes {@code swatchUrl}). */
    public Map<String, List<String>> galleryMapFromColorsJson(String colorsJson) {
        LinkedHashMap<String, List<String>> out = new LinkedHashMap<>();
        for (ColorEntry entry : parseColorEntries(colorsJson)) {
            if (!entry.galleryUrls().isEmpty()) {
                out.put(entry.name(), entry.galleryUrls());
            }
        }
        return out;
    }

    /**
     * When the variant matrix uses generic slots ({@code Print 1…}) but {@code color_images_json} uses real
     * print names, rename colours to gallery keys in stable order so storefront and Ledgerly stay linked.
     */
    public String realignColorNamesToGalleryKeys(String colorsJson, Map<String, List<String>> galleryMap) {
        if (galleryMap == null || galleryMap.isEmpty()) {
            return normalizeColorsJson(colorsJson);
        }
        List<ColorEntry> entries = parseColorEntries(colorsJson);
        List<String> galleryKeys = new ArrayList<>(galleryMap.keySet());
        if (entries.isEmpty()) {
            List<ColorEntry> fromMap = new ArrayList<>();
            for (String key : galleryKeys) {
                List<String> urls = uniqueUrls(galleryMap.get(key));
                if (!key.isBlank() && !urls.isEmpty()) {
                    fromMap.add(new ColorEntry(key, null, urls));
                }
            }
            return writeColorEntries(fromMap);
        }
        if (entries.size() != galleryKeys.size()) {
            return applyGalleryMapToColorsJson(colorsJson, galleryMap);
        }
        if (hasGalleryKeyOverlap(entries, galleryMap)) {
            return applyGalleryMapToColorsJson(colorsJson, galleryMap);
        }
        List<ColorEntry> realigned = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            String newName = galleryKeys.get(i);
            ColorEntry old = entries.get(i);
            List<String> urls = uniqueUrls(galleryMap.get(newName));
            realigned.add(new ColorEntry(newName, old.swatchUrl(), urls));
        }
        return writeColorEntries(realigned);
    }

    /** Positional old→new colour renames after {@link #realignColorNamesToGalleryKeys}. */
    public Map<String, String> buildPositionalColorRename(String colorsBefore, String colorsAfter) {
        List<ColorEntry> before = parseColorEntries(colorsBefore);
        List<ColorEntry> after = parseColorEntries(colorsAfter);
        LinkedHashMap<String, String> out = new LinkedHashMap<>();
        if (before.size() != after.size()) {
            return out;
        }
        for (int i = 0; i < before.size(); i++) {
            String oldName = before.get(i).name();
            String newName = after.get(i).name();
            if (!oldName.equals(newName)) {
                out.put(oldName, newName);
            }
        }
        return out;
    }

    private boolean hasGalleryKeyOverlap(List<ColorEntry> entries, Map<String, List<String>> galleryMap) {
        Set<String> galleryKeys = new LinkedHashSet<>();
        for (String key : galleryMap.keySet()) {
            if (key != null && !key.isBlank()) {
                galleryKeys.add(key.trim().toLowerCase(Locale.ROOT));
            }
        }
        for (ColorEntry entry : entries) {
            if (galleryKeys.contains(entry.name().trim().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    public String applyGalleryMapToColorsJson(String colorsJson, Map<String, List<String>> galleryMap) {
        if (galleryMap == null || galleryMap.isEmpty()) {
            return normalizeColorsJson(colorsJson);
        }
        List<ColorEntry> entries = parseColorEntries(colorsJson);
        if (entries.isEmpty()) {
            List<ColorEntry> fromMap = new ArrayList<>();
            for (Map.Entry<String, List<String>> entry : galleryMap.entrySet()) {
                String name = entry.getKey() == null ? "" : entry.getKey().trim();
                if (name.isEmpty()) {
                    continue;
                }
                fromMap.add(new ColorEntry(name, null, uniqueUrls(entry.getValue())));
            }
            return writeColorEntries(fromMap);
        }
        List<ColorEntry> merged = new ArrayList<>();
        for (ColorEntry entry : entries) {
            List<String> urls = galleryMapLookup(galleryMap, entry.name());
            if (urls != null && !urls.isEmpty()) {
                merged.add(new ColorEntry(entry.name(), entry.swatchUrl(), uniqueUrls(urls)));
            } else {
                merged.add(entry);
            }
        }
        return writeColorEntries(merged);
    }

    /**
     * Admin save: matrix/Zoho print names are frozen; restore lifestyle gallery URLs from the admin
     * payload by name, then by slot index. Swatches are frozen separately via
     * {@link #freezeSwatchesFromStored}.
     */
    public String mergeFrozenColorMedia(String matrixColorsJson, String adminColorsJson) {
        String galleriesOnly = mergeRichColorMetadata(matrixColorsJson, stripSwatchesFromColorsJson(adminColorsJson));
        List<ColorEntry> admin = parseColorEntries(stripSwatchesFromColorsJson(adminColorsJson));
        List<ColorEntry> merged = parseColorEntries(galleriesOnly);
        if (admin.isEmpty() || merged.isEmpty()) {
            return galleriesOnly;
        }
        List<ColorEntry> out = new ArrayList<>();
        for (int i = 0; i < merged.size(); i++) {
            ColorEntry entry = merged.get(i);
            if (!entry.galleryUrls().isEmpty()) {
                out.add(entry);
                continue;
            }
            ColorEntry adminAtSlot = i < admin.size() ? admin.get(i) : null;
            if (adminAtSlot != null && !adminAtSlot.galleryUrls().isEmpty()) {
                out.add(new ColorEntry(entry.name(), entry.swatchUrl(), adminAtSlot.galleryUrls()));
            } else {
                out.add(entry);
            }
        }
        return writeColorEntries(out);
    }

    /**
     * Admin save: print swatches are frozen after Zoho pull — restore {@code swatchUrl} from stored
     * {@code colors_json} by print name, then by slot index when Zoho renamed a print.
     */
    public String freezeSwatchesFromStored(String currentColorsJson, String frozenColorsJson) {
        List<ColorEntry> frozen = parseColorEntries(frozenColorsJson);
        List<ColorEntry> current = parseColorEntries(currentColorsJson);
        if (frozen.isEmpty() || current.isEmpty()) {
            return normalizeColorsJson(currentColorsJson);
        }
        LinkedHashMap<String, String> swatchByKey = new LinkedHashMap<>();
        List<String> swatchByPosition = new ArrayList<>();
        for (ColorEntry entry : frozen) {
            String swatch = entry.swatchUrl();
            if (swatch == null || swatch.isBlank()) {
                swatchByPosition.add(null);
                continue;
            }
            swatchByPosition.add(swatch.trim());
            String key = entry.name().trim().toLowerCase(Locale.ROOT);
            if (!key.isEmpty()) {
                swatchByKey.putIfAbsent(key, swatch.trim());
            }
        }
        List<ColorEntry> out = new ArrayList<>();
        for (int i = 0; i < current.size(); i++) {
            ColorEntry entry = current.get(i);
            String key = entry.name().trim().toLowerCase(Locale.ROOT);
            String swatch = swatchByKey.get(key);
            if ((swatch == null || swatch.isBlank()) && i < swatchByPosition.size()) {
                swatch = swatchByPosition.get(i);
            }
            out.add(new ColorEntry(entry.name(), swatch, entry.galleryUrls()));
        }
        return writeColorEntries(out);
    }

    /**
     * After the variant matrix rebuilds {@code colors_json} as plain print names, restore swatch and
     * gallery URLs from the admin payload so print uploads are not lost on Save.
     */
    public String mergeRichColorMetadata(String matrixColorsJson, String richColorsJson) {
        List<ColorEntry> rich = parseColorEntries(richColorsJson);
        if (rich.isEmpty()) {
            return normalizeColorsJson(matrixColorsJson);
        }
        LinkedHashMap<String, ColorEntry> richByKey = new LinkedHashMap<>();
        for (ColorEntry entry : rich) {
            String key = entry.name().trim().toLowerCase(Locale.ROOT);
            if (!key.isEmpty() && !richByKey.containsKey(key)) {
                richByKey.put(key, entry);
            }
        }
        List<ColorEntry> matrixEntries = parseColorEntries(matrixColorsJson);
        if (matrixEntries.isEmpty()) {
            return writeColorEntries(rich);
        }
        List<ColorEntry> merged = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (ColorEntry matrixEntry : matrixEntries) {
            String name = matrixEntry.name().trim();
            if (name.isEmpty()) {
                continue;
            }
            String key = name.toLowerCase(Locale.ROOT);
            if (!seen.add(key)) {
                continue;
            }
            ColorEntry prev = richByKey.get(key);
            if (prev != null) {
                merged.add(new ColorEntry(name, prev.swatchUrl(), prev.galleryUrls()));
            } else {
                merged.add(new ColorEntry(name, null, List.of()));
            }
        }
        return writeColorEntries(merged);
    }

    private List<String> galleryMapLookup(Map<String, List<String>> galleryMap, String colorName) {
        if (galleryMap == null || galleryMap.isEmpty()) {
            return List.of();
        }
        List<String> exact = galleryMap.get(colorName);
        if (exact != null && !exact.isEmpty()) {
            return exact;
        }
        String key = colorName.trim().toLowerCase(Locale.ROOT);
        for (Map.Entry<String, List<String>> entry : galleryMap.entrySet()) {
            if (entry.getKey() != null && entry.getKey().trim().toLowerCase(Locale.ROOT).equals(key) && !entry.getValue().isEmpty()) {
                return entry.getValue();
            }
        }
        return List.of();
    }

    public String normalizeColorsJson(String colorsJson) {
        return writeColorEntries(parseColorEntries(colorsJson));
    }

    private String stripSwatchesFromColorsJson(String colorsJson) {
        List<ColorEntry> entries = parseColorEntries(colorsJson);
        if (entries.isEmpty()) {
            return colorsJson;
        }
        List<ColorEntry> stripped = new ArrayList<>();
        for (ColorEntry entry : entries) {
            stripped.add(new ColorEntry(entry.name(), null, entry.galleryUrls()));
        }
        return writeColorEntries(stripped);
    }

    public String serializeMap(Map<String, List<String>> map) {
        try {
            return objectMapper.writeValueAsString(map == null ? Map.of() : map);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to serialize colour images map", e);
        }
    }

    public Map<String, List<String>> parseMap(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return sanitizeMap(objectMapper.readValue(json, new TypeReference<Map<String, List<String>>>() {}));
        } catch (Exception ignored) {
            return new LinkedHashMap<>();
        }
    }

    public Map<String, List<String>> coerceMap(Object raw) {
        if (raw == null) {
            return new LinkedHashMap<>();
        }
        if (raw instanceof Map<?, ?> map) {
            LinkedHashMap<String, List<String>> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = entry.getKey() == null ? "" : entry.getKey().toString().trim();
                if (key.isEmpty()) {
                    continue;
                }
                List<String> urls = coerceUrlList(entry.getValue());
                if (!urls.isEmpty()) {
                    out.put(key, urls);
                }
            }
            return out;
        }
        if (raw instanceof String text) {
            return parseMap(text);
        }
        try {
            return sanitizeMap(objectMapper.convertValue(raw, new TypeReference<Map<String, List<String>>>() {}));
        } catch (Exception ignored) {
            return new LinkedHashMap<>();
        }
    }

    private List<String> coerceUrlList(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object item : list) {
                if (item == null) {
                    continue;
                }
                String url = item.toString().trim();
                if (!url.isEmpty()) {
                    out.add(url);
                }
            }
            return uniqueUrls(out);
        }
        String single = value.toString().trim();
        return single.isEmpty() ? List.of() : List.of(single);
    }

    private Map<String, List<String>> sanitizeMap(Map<String, List<String>> map) {
        LinkedHashMap<String, List<String>> out = new LinkedHashMap<>();
        if (map == null) {
            return out;
        }
        for (Map.Entry<String, List<String>> entry : map.entrySet()) {
            String key = entry.getKey() == null ? "" : entry.getKey().trim();
            if (key.isEmpty()) {
                continue;
            }
            List<String> urls = uniqueUrls(entry.getValue());
            if (!urls.isEmpty()) {
                out.put(key, urls);
            }
        }
        return out;
    }

    private List<ColorEntry> parseColorEntries(String colorsJson) {
        if (colorsJson == null || colorsJson.isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(colorsJson);
            if (!root.isArray()) {
                return List.of();
            }
            List<ColorEntry> out = new ArrayList<>();
            Set<String> seen = new LinkedHashSet<>();
            for (JsonNode node : root) {
                ColorEntry entry = parseColorEntry(node);
                if (entry == null || entry.name().isBlank()) {
                    continue;
                }
                String key = entry.name().trim().toLowerCase(Locale.ROOT);
                if (!seen.add(key)) {
                    continue;
                }
                out.add(entry);
            }
            return out;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private ColorEntry parseColorEntry(JsonNode node) {
        if (node.isTextual()) {
            String name = node.asText().trim();
            return name.isEmpty() ? null : new ColorEntry(name, null, List.of());
        }
        if (!node.isObject()) {
            return null;
        }
        String name = textOrNull(node.get("name"));
        if (name == null || name.isBlank()) {
            return null;
        }
        String swatchUrl = textOrNull(node.get("swatchUrl"));
        return new ColorEntry(name.trim(), swatchUrl, parseGalleryUrls(node, swatchUrl));
    }

    private List<String> parseGalleryUrls(JsonNode node, String swatchUrl) {
        List<String> urls = new ArrayList<>();
        JsonNode imageUrls = node.get("imageUrls");
        if (imageUrls != null && imageUrls.isArray()) {
            for (JsonNode urlNode : imageUrls) {
                String url = textOrNull(urlNode);
                if (url != null) {
                    urls.add(url);
                }
            }
        }
        String imageUrl = textOrNull(node.get("imageUrl"));
        if (imageUrl != null && urls.isEmpty()) {
            urls.add(imageUrl);
        } else if (imageUrl != null && !urls.contains(imageUrl)) {
            urls.add(imageUrl);
        }
        List<String> unique = uniqueUrls(urls);
        if (swatchUrl != null && !swatchUrl.isBlank()) {
            unique = unique.stream().filter(url -> !url.equals(swatchUrl.trim())).toList();
        }
        if (unique.size() > MAX_IMAGES_PER_COLOR) {
            return unique.subList(0, MAX_IMAGES_PER_COLOR);
        }
        return unique;
    }

    private String writeColorEntries(List<ColorEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return "[]";
        }
        ArrayNode array = objectMapper.createArrayNode();
        boolean hasRich = entries
            .stream()
            .anyMatch(entry -> (entry.swatchUrl() != null && !entry.swatchUrl().isBlank()) || !entry.galleryUrls().isEmpty());
        for (ColorEntry entry : entries) {
            if (!hasRich) {
                array.add(entry.name());
                continue;
            }
            ObjectNode obj = objectMapper.createObjectNode();
            obj.put("name", entry.name());
            if (entry.swatchUrl() != null && !entry.swatchUrl().isBlank()) {
                obj.put("swatchUrl", entry.swatchUrl().trim());
            }
            List<String> gallery = entry.galleryUrls();
            if (gallery.size() == 1) {
                obj.put("imageUrl", gallery.get(0));
            } else if (gallery.size() > 1) {
                ArrayNode urls = obj.putArray("imageUrls");
                gallery.forEach(urls::add);
            }
            array.add(obj);
        }
        try {
            return objectMapper.writeValueAsString(array);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to serialize colours JSON", e);
        }
    }

    private List<String> uniqueUrls(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String value : raw) {
            if (value == null) {
                continue;
            }
            String url = value.trim();
            if (url.isEmpty() || !seen.add(url)) {
                continue;
            }
            out.add(url);
        }
        if (out.size() > MAX_IMAGES_PER_COLOR) {
            return out.subList(0, MAX_IMAGES_PER_COLOR);
        }
        return out;
    }

    private String textOrNull(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        String text = node.asText().trim();
        return text.isEmpty() ? null : text;
    }

    private record ColorEntry(String name, String swatchUrl, List<String> galleryUrls) {}
}
