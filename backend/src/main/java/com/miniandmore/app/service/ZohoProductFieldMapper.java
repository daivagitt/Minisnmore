package com.miniandmore.app.service;

import com.miniandmore.app.client.ZohoBooksClient;
import com.miniandmore.app.config.ApplicationProperties;
import com.miniandmore.app.domain.Category;
import com.miniandmore.app.domain.Product;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

/**
 * Maps storefront product merchandising fields (fabric, sizes, age range, category, MRP) to Zoho Books item
 * fields. Zoho India orgs expose MRP via {@code label_rate}; custom attributes use {@code custom_fields}.
 */
@Component
public class ZohoProductFieldMapper {

    private final ZohoBooksClient booksClient;
    private final ApplicationProperties applicationProperties;
    private final ProductDescriptionCodec descriptionCodec;
    private final LedgerlyCategoryResolver categoryResolver;
    private FieldIndex cachedIndex;
    private String cachedScope;
    private Instant cacheExpiresAt = Instant.MIN;

    public ZohoProductFieldMapper(
        ZohoBooksClient booksClient,
        ApplicationProperties applicationProperties,
        ProductDescriptionCodec descriptionCodec,
        LedgerlyCategoryResolver categoryResolver
    ) {
        this.booksClient = booksClient;
        this.applicationProperties = applicationProperties;
        this.descriptionCodec = descriptionCodec;
        this.categoryResolver = categoryResolver;
    }

    public void enrichPushPayload(Product product, Map<String, Object> payload) {
        if (product == null || payload == null) {
            return;
        }
        FieldIndex fields = resolveFields();
        if (product.getCompareAtPrice() != null && product.getCompareAtPrice().compareTo(BigDecimal.ZERO) > 0) {
            payload.put(fields.mrpApiName(), formatDecimal(product.getCompareAtPrice()));
        }
        if (StringUtils.isNotBlank(product.getHsn())) {
            payload.put("hsn_or_sac", product.getHsn().trim());
        }
        String nativeCategoryId = product.getCategory() == null
            ? null
            : StringUtils.trimToNull(product.getCategory().getZohoCategoryId());
        if (nativeCategoryId != null) {
            payload.put("category_id", nativeCategoryId);
        }
        // Setting tax_id on the item itself means Zoho applies it (and computes CGST+SGST or IGST
        // from place_of_supply) on every sales order/invoice line automatically, no per-order wiring needed.
        String gstTaxId = StringUtils.trimToNull(applicationProperties.getZoho().getGstTaxId());
        if (gstTaxId != null) {
            payload.put("tax_id", gstTaxId);
        }
        List<Map<String, Object>> customFields = new ArrayList<>();
        fields
            .fabricType()
            .ifPresent(def -> addCustomField(customFields, def, textOrNull(product.getFabricType())));
        fields
            .sizes()
            .ifPresent(def -> {
                List<String> sizes = descriptionCodec.parseDelimitedOrJson(product.getSizesJson());
                if (!sizes.isEmpty()) {
                    addCustomField(customFields, def, String.join(", ", sizes));
                }
            });
        fields.ageMin().ifPresent(def -> addCustomField(customFields, def, formatAgeMinForPush(product, fields)));
        fields
            .ageMax()
            .ifPresent(def -> {
                if (product.getAgeMaxMonths() != null) {
                    addCustomField(customFields, def, String.valueOf(product.getAgeMaxMonths()));
                }
            });
        fields
            .category()
            .ifPresent(def -> {
                String categoryName = categoryName(product);
                if (nativeCategoryId == null && categoryName != null) {
                    addCustomField(customFields, def, categoryName);
                }
            });
        if (!customFields.isEmpty()) {
            payload.put("custom_fields", customFields);
        }
    }

    public Optional<String> applyPulledItem(Product product, Map<String, Object> item, boolean autoCreateCategory) {
        if (product == null || item == null || item.isEmpty()) {
            return Optional.empty();
        }
        FieldIndex fields = resolveFields();
        BigDecimal mrp = readMrp(item, fields);
        if (mrp != null && mrp.compareTo(BigDecimal.ZERO) > 0) {
            product.setCompareAtPrice(mrp);
        }
        Map<String, String> valuesByApiName = indexCustomFieldValues(item);
        fields
            .fabricType()
            .flatMap(def -> readCustomValue(valuesByApiName, def))
            .ifPresent(product::setFabricType);
        fields
            .sizes()
            .flatMap(def -> readCustomValue(valuesByApiName, def))
            .map(descriptionCodec::parseDelimitedOrJson)
            .filter(list -> !list.isEmpty())
            .ifPresent(sizes -> product.setSizesJson(descriptionCodec.toJsonList(sizes)));
        fields
            .ageMin()
            .flatMap(def -> readCustomValue(valuesByApiName, def))
            .flatMap(this::parseInteger)
            .ifPresent(product::setAgeMinMonths);
        fields
            .ageMax()
            .flatMap(def -> readCustomValue(valuesByApiName, def))
            .flatMap(this::parseInteger)
            .ifPresent(product::setAgeMaxMonths);
        applyMissingCatalogFromItemLabels(product, item);
        Optional<String> customCategory = fields.category().flatMap(def -> readCustomValue(valuesByApiName, def));
        String nativeCategoryId = nativeCategoryId(item).orElse(null);
        Optional<String> categoryNotice = customCategory
            .or(() -> nativeCategoryName(item))
            .flatMap(name -> categoryResolver.resolve(name, autoCreateCategory))
            .map(category -> {
                if (nativeCategoryId != null && StringUtils.isBlank(category.getZohoCategoryId())) {
                    category.setZohoCategoryId(nativeCategoryId);
                }
                product.setCategory(category);
                return "Assigned category " + category.getName();
            });
        return categoryNotice;
    }

    private static Optional<String> nativeCategoryId(Map<String, Object> item) {
        Object category = item.get("category");
        String categoryId = stringValue(item.get("category_id"));
        if (StringUtils.isBlank(categoryId) && category instanceof Map<?, ?> categoryMap) {
            categoryId = stringValue(categoryMap.get("category_id"));
        }
        return Optional.ofNullable(StringUtils.trimToNull(categoryId));
    }

    private static Optional<String> nativeCategoryName(Map<String, Object> item) {
        Object category = item.get("category");
        String categoryName = stringValue(item.get("category_name"));
        if (StringUtils.isBlank(categoryName) && category instanceof Map<?, ?> categoryMap) {
            categoryName = firstNonBlank(
                stringValue(categoryMap.get("name")),
                stringValue(categoryMap.get("category_name"))
            );
        } else if (StringUtils.isBlank(categoryName)) {
            categoryName = stringValue(category);
        }
        return Optional.ofNullable(StringUtils.trimToNull(categoryName));
    }

    private static String categoryName(Product product) {
        Category category = product.getCategory();
        if (category == null || StringUtils.isBlank(category.getName())) {
            return null;
        }
        return category.getName().trim();
    }

    private synchronized FieldIndex resolveFields() {
        String scope = booksClient.itemFieldCacheScope();
        if (cachedIndex != null && java.util.Objects.equals(scope, cachedScope) && Instant.now().isBefore(cacheExpiresAt)) {
            return cachedIndex;
        }
        // Never treat discovery failure as a successful, field-less sync. Leave expired/other-org
        // data unused and propagate the failure so pull cursors and push status remain retryable.
        cachedIndex = buildIndex(booksClient.listItemFields());
        cachedScope = scope;
        cacheExpiresAt = Instant.now().plusSeconds(300);
        return cachedIndex;
    }

    /** Force the next product to rediscover field ids after Zoho rejects a cached definition. */
    public synchronized void invalidateFieldCache() {
        cachedIndex = null;
        cachedScope = null;
        cacheExpiresAt = Instant.MIN;
    }

    FieldIndex buildIndex(List<Map<String, Object>> rawFields) {
        ApplicationProperties.Zoho.ProductFields config = applicationProperties.getZoho().getProductFields();
        FieldDef fabricType = findField(
            rawFields,
            config.fabricTypeLabels(),
            List.of("fabric_type"),
            true
        );
        FieldDef sizes = findField(rawFields, config.sizesLabels(), List.of("sizes"), true);
        FieldDef ageMin = findField(rawFields, config.ageMinLabels(), List.of("age"), true);
        FieldDef ageMax = findField(rawFields, config.ageMaxLabels(), List.of("age_max", "age_max_months"), true);
        if (ageMin != null && ageMax != null && ageMin.fieldId().equals(ageMax.fieldId())) {
            ageMax = null;
        }
        FieldDef category = findField(rawFields, config.categoryLabels(), List.of("category"), true);
        String mrpApiName = StringUtils.defaultIfBlank(config.getMrpApiName(), "label_rate");
        return new FieldIndex(fabricType, sizes, ageMin, ageMax, category, mrpApiName);
    }

    private static FieldDef findField(
        List<Map<String, Object>> rawFields,
        List<String> preferredLabels,
        List<String> apiSuffixes,
        boolean customOnly
    ) {
        for (Map<String, Object> raw : rawFields) {
            if (!isActive(raw)) {
                continue;
            }
            boolean isCustom = Boolean.TRUE.equals(raw.get("is_custom_field"));
            if (customOnly && !isCustom) {
                continue;
            }
            String label = normalizedLabel(raw);
            String apiName = stringValue(raw.get("api_name"));
            if (matchesLabel(label, preferredLabels) || matchesApiSuffix(apiName, apiSuffixes)) {
                // Item update/create payloads require the custom-field id when Zoho exposes both
                // the generic settings field id and the entity-specific customfield_id.
                String fieldId = firstNonBlank(stringValue(raw.get("customfield_id")), stringValue(raw.get("field_id")));
                if (StringUtils.isBlank(fieldId)) {
                    continue;
                }
                return new FieldDef(
                    fieldId,
                    apiName,
                    label,
                    Boolean.TRUE.equals(raw.get("is_mandatory"))
                );
            }
        }
        return null;
    }

    private static boolean isActive(Map<String, Object> raw) {
        Object active = raw.get("is_active");
        if (active instanceof Boolean flag) {
            return flag;
        }
        String status = stringValue(raw.get("status"));
        return status == null || "active".equalsIgnoreCase(status);
    }

    private static String normalizedLabel(Map<String, Object> raw) {
        return firstNonBlank(
            stringValue(raw.get("label")),
            stringValue(raw.get("field_name_formatted")),
            stringValue(raw.get("field_name"))
        );
    }

    private static boolean matchesLabel(String label, List<String> preferredLabels) {
        if (StringUtils.isBlank(label) || preferredLabels == null) {
            return false;
        }
        String normalized = label.trim().toLowerCase(Locale.ROOT);
        for (String preferred : preferredLabels) {
            if (preferred != null && normalized.equals(preferred.trim().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesApiSuffix(String apiName, List<String> suffixes) {
        if (StringUtils.isBlank(apiName) || suffixes == null) {
            return false;
        }
        String lower = apiName.toLowerCase(Locale.ROOT);
        for (String suffix : suffixes) {
            if (suffix != null && lower.endsWith(suffix.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String formatAgeMinForPush(Product product, FieldIndex fields) {
        if (product.getAgeMinMonths() != null) {
            return String.valueOf(product.getAgeMinMonths());
        }
        if (product.getAgeMaxMonths() != null) {
            return String.valueOf(product.getAgeMaxMonths());
        }
        if (fields.ageMin().isPresent() && fields.ageMin().orElseThrow().mandatory()) {
            return "0";
        }
        return null;
    }

    private static void addCustomField(List<Map<String, Object>> customFields, FieldDef field, String value) {
        if (StringUtils.isBlank(value)) {
            return;
        }
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("customfield_id", field.fieldId());
        entry.put("value", value.trim());
        customFields.add(entry);
    }

    private static BigDecimal readMrp(Map<String, Object> item, FieldIndex fields) {
        Object direct = item.get(fields.mrpApiName());
        if (direct == null && !"label_rate".equals(fields.mrpApiName())) {
            direct = item.get("label_rate");
        }
        if (direct == null) {
            direct = item.get("mrp");
        }
        return parseDecimal(direct);
    }

    private static Map<String, String> indexCustomFieldValues(Map<String, Object> item) {
        Map<String, String> values = new LinkedHashMap<>();
        Object raw = item.get("custom_fields");
        if (!(raw instanceof List<?> entries)) {
            return values;
        }
        for (Object entryObj : entries) {
            if (!(entryObj instanceof Map<?, ?> entry)) {
                continue;
            }
            String value = stringValue(entry.get("value"));
            if (StringUtils.isBlank(value)) {
                continue;
            }
            String apiName = stringValue(entry.get("api_name"));
            if (StringUtils.isNotBlank(apiName)) {
                values.put(apiName, value.trim());
            }
            String fieldId = firstNonBlank(stringValue(entry.get("customfield_id")), stringValue(entry.get("field_id")));
            if (StringUtils.isNotBlank(fieldId)) {
                values.put(fieldId, value.trim());
            }
            String label = firstNonBlank(
                stringValue(entry.get("label")),
                stringValue(entry.get("field_name_formatted")),
                stringValue(entry.get("field_name"))
            );
            if (StringUtils.isNotBlank(label)) {
                values.put(label.trim(), value.trim());
                values.put(label.trim().toLowerCase(Locale.ROOT), value.trim());
            }
        }
        return values;
    }

    private static Optional<String> readCustomValue(Map<String, String> valuesByApiName, FieldDef field) {
        String byApi = valuesByApiName.get(field.apiName());
        if (StringUtils.isNotBlank(byApi)) {
            return Optional.of(byApi);
        }
        String byId = valuesByApiName.get(field.fieldId());
        if (StringUtils.isNotBlank(byId)) {
            return Optional.of(byId);
        }
        if (StringUtils.isNotBlank(field.label())) {
            String byLabel = valuesByApiName.get(field.label());
            if (StringUtils.isNotBlank(byLabel)) {
                return Optional.of(byLabel);
            }
            String byLabelLower = valuesByApiName.get(field.label().trim().toLowerCase(Locale.ROOT));
            if (StringUtils.isNotBlank(byLabelLower)) {
                return Optional.of(byLabelLower);
            }
        }
        return Optional.empty();
    }

    /** When Zoho item-field metadata is missing, still read age/fabric/sizes from the item payload labels. */
    private void applyMissingCatalogFromItemLabels(Product product, Map<String, Object> item) {
        Object raw = item.get("custom_fields");
        if (!(raw instanceof List<?> entries)) {
            return;
        }
        String fabric = null;
        String sizes = null;
        Integer ageMin = null;
        Integer ageMax = null;
        for (Object entryObj : entries) {
            if (!(entryObj instanceof Map<?, ?> entry)) {
                continue;
            }
            String value = stringValue(entry.get("value"));
            if (StringUtils.isBlank(value)) {
                continue;
            }
            String label = firstNonBlank(
                stringValue(entry.get("label")),
                stringValue(entry.get("field_name_formatted")),
                stringValue(entry.get("field_name")),
                stringValue(entry.get("api_name"))
            );
            if (StringUtils.isBlank(label)) {
                continue;
            }
            String token = label.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "");
            if (token.contains("agemax") || (token.contains("age") && token.contains("max"))) {
                ageMax = parseInteger(value).orElse(ageMax);
            } else if (token.equals("age") || token.contains("agemin") || token.equals("ageminmonths")) {
                ageMin = parseInteger(value).orElse(ageMin);
            } else if (token.contains("fabric")) {
                fabric = value.trim();
            } else if (token.equals("sizes") || token.equals("size")) {
                sizes = value.trim();
            }
        }
        if (StringUtils.isBlank(product.getFabricType()) && StringUtils.isNotBlank(fabric)) {
            product.setFabricType(fabric);
        }
        if (product.getAgeMinMonths() == null && ageMin != null) {
            product.setAgeMinMonths(ageMin);
        }
        if (product.getAgeMaxMonths() == null && ageMax != null) {
            product.setAgeMaxMonths(ageMax);
        }
        if (StringUtils.isBlank(product.getSizesJson()) && StringUtils.isNotBlank(sizes)) {
            List<String> parsed = descriptionCodec.parseDelimitedOrJson(sizes);
            if (!parsed.isEmpty()) {
                product.setSizesJson(descriptionCodec.toJsonList(parsed));
            }
        }
    }

    private Optional<Integer> parseInteger(String raw) {
        if (StringUtils.isBlank(raw)) {
            return Optional.empty();
        }
        String trimmed = raw.trim();
        int dash = trimmed.indexOf('-');
        if (dash > 0) {
            trimmed = trimmed.substring(0, dash).trim();
        }
        try {
            return Optional.of(new BigDecimal(trimmed).intValue());
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static BigDecimal parseDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        String text = value.toString().trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String formatDecimal(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private static String textOrNull(String value) {
        return StringUtils.isBlank(value) ? null : value.trim();
    }

    private static String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.isNotBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }

    record FieldDef(String fieldId, String apiName, String label, boolean mandatory) {
    }

    record FieldIndex(
        FieldDef fabricTypeField,
        FieldDef sizesField,
        FieldDef ageMinField,
        FieldDef ageMaxField,
        FieldDef categoryField,
        String mrpApiName
    ) {
        Optional<FieldDef> fabricType() {
            return Optional.ofNullable(fabricTypeField);
        }

        Optional<FieldDef> sizes() {
            return Optional.ofNullable(sizesField);
        }

        Optional<FieldDef> ageMin() {
            return Optional.ofNullable(ageMinField);
        }

        Optional<FieldDef> ageMax() {
            return Optional.ofNullable(ageMaxField);
        }

        Optional<FieldDef> category() {
            return Optional.ofNullable(categoryField);
        }

        static FieldIndex empty(String mrpApiName) {
            return new FieldIndex(null, null, null, null, null, StringUtils.defaultIfBlank(mrpApiName, "label_rate"));
        }
    }
}
