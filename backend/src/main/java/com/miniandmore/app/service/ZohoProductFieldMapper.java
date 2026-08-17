package com.miniandmore.app.service;

import com.miniandmore.app.client.ZohoBooksClient;
import com.miniandmore.app.config.ApplicationProperties;
import com.miniandmore.app.domain.Category;
import com.miniandmore.app.domain.Product;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Maps storefront product merchandising fields (fabric, sizes, age range, category, MRP) to Zoho Books item
 * fields. Zoho India orgs expose MRP via {@code label_rate}; custom attributes use {@code custom_fields}.
 */
@Component
public class ZohoProductFieldMapper {

    private static final Logger LOG = LoggerFactory.getLogger(ZohoProductFieldMapper.class);

    private final ZohoBooksClient booksClient;
    private final ApplicationProperties applicationProperties;
    private final ProductDescriptionCodec descriptionCodec;
    private final LedgerlyCategoryResolver categoryResolver;
    private volatile FieldIndex cachedIndex;

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
                if (categoryName != null) {
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
        Optional<String> categoryNotice = fields
            .category()
            .flatMap(def -> readCustomValue(valuesByApiName, def))
            .flatMap(name -> categoryResolver.resolve(name, autoCreateCategory))
            .map(category -> {
                product.setCategory(category);
                return "Assigned category " + category.getName();
            });
        return categoryNotice;
    }

    private static String categoryName(Product product) {
        Category category = product.getCategory();
        if (category == null || StringUtils.isBlank(category.getName())) {
            return null;
        }
        return category.getName().trim();
    }

    private FieldIndex resolveFields() {
        FieldIndex existing = cachedIndex;
        if (existing != null) {
            return existing;
        }
        synchronized (this) {
            if (cachedIndex != null) {
                return cachedIndex;
            }
            try {
                cachedIndex = buildIndex(booksClient.listItemFields());
            } catch (RuntimeException e) {
                LOG.warn("Could not load Zoho item field definitions: {}", e.getMessage());
                cachedIndex = FieldIndex.empty(applicationProperties.getZoho().getProductFields().getMrpApiName());
            }
            return cachedIndex;
        }
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
                String fieldId = firstNonBlank(stringValue(raw.get("field_id")), stringValue(raw.get("customfield_id")));
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
        }
        return values;
    }

    private static Optional<String> readCustomValue(Map<String, String> valuesByApiName, FieldDef field) {
        String byApi = valuesByApiName.get(field.apiName());
        if (StringUtils.isNotBlank(byApi)) {
            return Optional.of(byApi);
        }
        String byId = valuesByApiName.get(field.fieldId());
        return StringUtils.isNotBlank(byId) ? Optional.of(byId) : Optional.empty();
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
