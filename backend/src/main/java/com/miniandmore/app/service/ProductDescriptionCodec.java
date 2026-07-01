package com.miniandmore.app.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.domain.Product;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Encodes storefront merchandising fields into Ledgerly item descriptions and decodes on pull.
 * Prefix lines are stripped from the customer-facing description body.
 */
@Component
public class ProductDescriptionCodec {

    private static final Pattern LINE_PREFIX = Pattern.compile(
        "^(Category|Fabric|Sizes|Colors|Age|Story|Badge|CompareAt):\\s*(.*)$",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern AGE_RANGE = Pattern.compile("(\\d+)\\s*-\\s*(\\d+)\\s*months?", Pattern.CASE_INSENSITIVE);

    private final ObjectMapper objectMapper;

    public ProductDescriptionCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public record Decoded(
        String categoryName,
        String fabric,
        List<String> sizes,
        List<String> colors,
        Integer ageMinMonths,
        Integer ageMaxMonths,
        String story,
        String badge,
        BigDecimal compareAtPrice,
        String bodyDescription
    ) {}

    public String encode(Product product, String categoryName) {
        List<String> lines = new ArrayList<>();
        if (categoryName != null && !categoryName.isBlank()) {
            lines.add("Category: " + categoryName.trim());
        }
        if (product.getFabricType() != null && !product.getFabricType().isBlank()) {
            lines.add("Fabric: " + product.getFabricType().trim());
        }
        List<String> sizes = parseJsonList(product.getSizesJson());
        if (!sizes.isEmpty()) {
            lines.add("Sizes: " + String.join(", ", sizes));
        }
        List<String> colors = parseJsonList(product.getColorsJson());
        if (!colors.isEmpty()) {
            lines.add("Colors: " + String.join(", ", colors));
        }
        if (product.getAgeMinMonths() != null && product.getAgeMaxMonths() != null) {
            lines.add("Age: " + product.getAgeMinMonths() + "-" + product.getAgeMaxMonths() + " months");
        }
        if (product.getStory() != null && !product.getStory().isBlank()) {
            lines.add("Story: " + product.getStory().trim());
        }
        if (product.getBadge() != null && !product.getBadge().isBlank()) {
            lines.add("Badge: " + product.getBadge().trim());
        }
        if (product.getCompareAtPrice() != null && product.getCompareAtPrice().compareTo(BigDecimal.ZERO) > 0) {
            lines.add("CompareAt: " + product.getCompareAtPrice().stripTrailingZeros().toPlainString());
        }
        String body = product.getDescription() != null ? product.getDescription().trim() : "";
        if (lines.isEmpty()) {
            return body.isEmpty() ? null : body;
        }
        if (body.isEmpty()) {
            return String.join("\n", lines);
        }
        return String.join("\n", lines) + "\n\n" + body;
    }

    public Decoded decode(String rawDescription) {
        if (rawDescription == null || rawDescription.isBlank()) {
            return new Decoded(null, null, List.of(), List.of(), null, null, null, null, null, null);
        }
        Map<String, String> meta = new LinkedHashMap<>();
        List<String> bodyLines = new ArrayList<>();
        for (String line : rawDescription.split("\\r?\\n")) {
            Matcher matcher = LINE_PREFIX.matcher(line.trim());
            if (matcher.matches()) {
                meta.put(matcher.group(1).toLowerCase(Locale.ROOT), matcher.group(2).trim());
            } else {
                bodyLines.add(line);
            }
        }
        String body = String.join("\n", bodyLines).trim();
        if (body.isEmpty()) {
            body = null;
        }

        List<String> sizes = splitList(meta.get("sizes"));
        List<String> colors = splitList(meta.get("colors"));
        Integer ageMin = null;
        Integer ageMax = null;
        String ageRaw = meta.get("age");
        if (ageRaw != null) {
            Matcher ageMatcher = AGE_RANGE.matcher(ageRaw);
            if (ageMatcher.find()) {
                ageMin = Integer.parseInt(ageMatcher.group(1));
                ageMax = Integer.parseInt(ageMatcher.group(2));
            }
        }
        BigDecimal compareAt = null;
        String compareRaw = meta.get("compareat");
        if (compareRaw != null && !compareRaw.isBlank()) {
            try {
                compareAt = new BigDecimal(compareRaw);
            } catch (NumberFormatException ignored) {
                // ignore invalid compare-at
            }
        }

        return new Decoded(
            meta.get("category"),
            meta.get("fabric"),
            sizes,
            colors,
            ageMin,
            ageMax,
            meta.get("story"),
            meta.get("badge"),
            compareAt,
            body
        );
    }

    public void applyDecoded(Product product, Decoded decoded) {
        if (decoded == null) {
            return;
        }
        if (decoded.fabric() != null && !decoded.fabric().isBlank()) {
            product.setFabricType(decoded.fabric());
        }
        if (!decoded.sizes().isEmpty()) {
            product.setSizesJson(writeJsonList(decoded.sizes()));
        }
        if (!decoded.colors().isEmpty()) {
            product.setColorsJson(writeJsonList(decoded.colors()));
        }
        if (decoded.ageMinMonths() != null) {
            product.setAgeMinMonths(decoded.ageMinMonths());
        }
        if (decoded.ageMaxMonths() != null) {
            product.setAgeMaxMonths(decoded.ageMaxMonths());
        }
        if (decoded.story() != null && !decoded.story().isBlank()) {
            product.setStory(decoded.story());
        }
        if (decoded.badge() != null && !decoded.badge().isBlank()) {
            product.setBadge(decoded.badge());
        }
        if (decoded.compareAtPrice() != null) {
            product.setCompareAtPrice(decoded.compareAtPrice());
        }
        if (decoded.bodyDescription() != null) {
            product.setDescription(decoded.bodyDescription());
        } else if (decoded.categoryName() == null && decoded.fabric() == null && decoded.story() == null) {
            // description was only metadata lines — clear stale body if everything parsed out
        }
    }

    public String categoryNameFrom(Decoded decoded) {
        return decoded != null ? decoded.categoryName() : null;
    }

    /** Comma-separated text or JSON array from Ledgerly item columns. */
    public List<String> parseDelimitedOrJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith("[")) {
            return parseJsonList(trimmed);
        }
        return splitList(trimmed);
    }

    public String toJsonList(List<String> values) {
        return writeJsonList(values != null ? values : List.of());
    }

    private List<String> parseJsonList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<String> list = objectMapper.readValue(json, new TypeReference<List<String>>() {});
            return list != null ? list : List.of();
        } catch (Exception e) {
            return List.of();
        }
    }

    private String writeJsonList(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (Exception e) {
            return "[]";
        }
    }

    private List<String> splitList(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        List<String> parts = new ArrayList<>();
        for (String part : value.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                parts.add(trimmed);
            }
        }
        return parts;
    }
}
