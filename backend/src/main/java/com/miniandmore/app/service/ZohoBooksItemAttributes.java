package com.miniandmore.app.service;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;

/**
 * Reads Zoho Books / Inventory item fields for simple items vs grouped variants.
 *
 * <p>Zoho Books has no parent SKU. Each sellable SKU is its own {@code item_id}. When the Inventory
 * add-on is enabled, related variants share {@code group_id} / {@code group_name} and expose
 * {@code attribute_name1..3} + {@code attribute_option_name1..3} (typically Size and Color).
 */
public final class ZohoBooksItemAttributes {

    /** {@code Horizon Set-S-Navy} / {@code Horizon Set-3-6 M-Ivory} — size token required so hyphenated product names stay simple. */
    private static final Pattern HYPHEN_SIZE_COLOR = Pattern.compile(
        "^(?i)(.+)-(XXL|XL|[SML]|\\d{1,2}-\\d{1,2}\\s*M)-(.+)$"
    );

    /**
     * Mini MN catalog: {@code Baby Swaddle - Aqua - COMMON SIZE} (spaces around hyphens, color then size).
     * Size token is last so multi-word prints like {@code Pink Buds} stay intact.
     */
    private static final Pattern SPACED_COLOR_THEN_SIZE = Pattern.compile(
        "^(?i)(.+?)\\s+-\\s+(.+?)\\s+-\\s+(COMMON\\s+SIZE|NEWBORN|XXL|XL|[SML]|\\d{1,2}\\s*-\\s*\\d{1,2}\\s*M)$"
    );

    private static final Pattern SIZE_TOKEN = Pattern.compile(
        "^(?i)(COMMON\\s+SIZE|NEWBORN|XXL|XL|[SML]|\\d{1,2}\\s*-\\s*\\d{1,2}\\s*M)$"
    );

    private ZohoBooksItemAttributes() {}

    public static String groupId(Map<String, Object> item) {
        return firstNonBlank(stringValue(item, "group_id"), stringValue(item, "itemgroup_id"));
    }

    public static String groupName(Map<String, Object> item) {
        return firstNonBlank(stringValue(item, "group_name"), stringValue(item, "itemgroup_name"));
    }

    public static boolean isGrouped(Map<String, Object> item) {
        return StringUtils.isNotBlank(groupId(item)) || StringUtils.isNotBlank(groupName(item));
    }

    /**
     * Zoho Books item {@code status} is {@code active}/{@code inactive}; some payloads also send
     * boolean {@code is_active}. Missing status is treated as active (list already filters Active).
     */
    public static boolean isActive(Map<String, Object> item) {
        if (item == null || item.isEmpty()) {
            return true;
        }
        Object flag = item.get("is_active");
        if (flag instanceof Boolean bool) {
            return bool;
        }
        if (flag != null) {
            String text = flag.toString().trim();
            if ("true".equalsIgnoreCase(text) || "false".equalsIgnoreCase(text)) {
                return Boolean.parseBoolean(text);
            }
        }
        String status = stringValue(item, "status");
        return status == null || "active".equalsIgnoreCase(status);
    }

    /**
     * True when this Zoho item is one sellable SKU of a parent (item group, Size×Color attributes,
     * or Mini's {@code Name — Size / Color} item name).
     */
    public static boolean isVariantSku(Map<String, Object> item) {
        if (item == null || item.isEmpty()) {
            return false;
        }
        if (StringUtils.isNotBlank(groupId(item))) {
            return true;
        }
        String namedSize = namedOption(item, AttributeRole.SIZE);
        String namedColor = namedOption(item, AttributeRole.COLOR);
        if (namedSize != null && namedColor != null) {
            return true;
        }
        ParsedName parsed = parseNameSuffix(itemName(item));
        if (parsed.baseName() != null && parsed.size() != null && parsed.color() != null) {
            return true;
        }
        return pipeDescriptionSize(item) != null && pipeDescriptionColor(item) != null;
    }

    public static boolean hasVariantNameSuffix(String name) {
        ParsedName parsed = parseNameSuffix(name);
        return parsed.baseName() != null && parsed.size() != null;
    }

    public static String itemName(Map<String, Object> item) {
        return firstNonBlank(stringValue(item, "name"), stringValue(item, "item_name"));
    }

    /** Size option: named Size attribute, else first non-color attribute, else name suffix. */
    public static String size(Map<String, Object> item) {
        String fromAttrs = optionForRole(item, AttributeRole.SIZE);
        if (fromAttrs != null) {
            return fromAttrs;
        }
        ParsedName parsed = parseNameSuffix(itemName(item));
        if (parsed.size() != null) {
            return parsed.size();
        }
        return pipeDescriptionSize(item);
    }

    /** Color / print option: named Color attribute, else remaining attribute, else name suffix. */
    public static String color(Map<String, Object> item) {
        String fromAttrs = optionForRole(item, AttributeRole.COLOR);
        if (fromAttrs != null) {
            return fromAttrs;
        }
        ParsedName parsed = parseNameSuffix(itemName(item));
        if (parsed.color() != null) {
            return parsed.color();
        }
        return pipeDescriptionColor(item);
    }

    /**
     * Parent title for admin/storefront. Zoho sometimes stores the full variant label as
     * {@code group_name} (e.g. {@code Book Style Tying Jabla — 0-3 M / Aqua}) when each size×print
     * is its own singleton group — strip that suffix so siblings still merge under one parent.
     */
    public static String parentDisplayName(Map<String, Object> item) {
        String group = groupName(item);
        if (group != null) {
            ParsedName fromGroup = parseNameSuffix(group);
            if (fromGroup.baseName() != null) {
                return fromGroup.baseName();
            }
            return group;
        }
        ParsedName parsed = parseNameSuffix(itemName(item));
        if (parsed.baseName() != null) {
            return parsed.baseName();
        }
        String fromDesc = pipeDescriptionBase(item);
        if (fromDesc != null) {
            return fromDesc;
        }
        return itemName(item);
    }

    /** Strip {@code — Size / Color} (or MN spaced) suffix; returns original when none. */
    public static String stripVariantSuffix(String name) {
        ParsedName parsed = parseNameSuffix(name);
        if (parsed.baseName() != null) {
            return parsed.baseName();
        }
        return StringUtils.trimToNull(name);
    }

    private static String namedOption(Map<String, Object> item, AttributeRole want) {
        if (item == null) {
            return null;
        }
        for (int i = 1; i <= 3; i++) {
            String name = stringValue(item, "attribute_name" + i);
            String option = stringValue(item, "attribute_option_name" + i);
            if (option == null) {
                continue;
            }
            if (classify(name) == want) {
                return option;
            }
        }
        return null;
    }

    private static String optionForRole(Map<String, Object> item, AttributeRole want) {
        String size = null;
        String color = null;
        java.util.ArrayList<String> unlabeled = new java.util.ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            String name = stringValue(item, "attribute_name" + i);
            String option = stringValue(item, "attribute_option_name" + i);
            if (option == null) {
                continue;
            }
            AttributeRole role = classify(name);
            if (role == AttributeRole.SIZE && size == null) {
                size = option;
            } else if (role == AttributeRole.COLOR && color == null) {
                color = option;
            } else {
                unlabeled.add(option);
            }
        }
        if (want == AttributeRole.SIZE) {
            if (size != null) {
                return size;
            }
            return unlabeled.isEmpty() ? null : unlabeled.get(0);
        }
        if (color != null) {
            return color;
        }
        if (size != null) {
            return unlabeled.isEmpty() ? null : unlabeled.get(0);
        }
        return unlabeled.size() > 1 ? unlabeled.get(1) : null;
    }

    private static AttributeRole classify(String attributeName) {
        if (attributeName == null) {
            return AttributeRole.UNKNOWN;
        }
        String n = attributeName.trim().toLowerCase(Locale.ROOT);
        if (n.contains("size") || n.equals("sz")) {
            return AttributeRole.SIZE;
        }
        if (n.contains("color") || n.contains("colour") || n.contains("print") || n.contains("pattern")) {
            return AttributeRole.COLOR;
        }
        return AttributeRole.UNKNOWN;
    }

    /**
     * Mini's push format: {@code Horizon Set — S / Navy}. Also accepts {@code Horizon Set-S-Navy}
     * and MN spaced form {@code Baby Swaddle - Aqua - COMMON SIZE}.
     */
    static ParsedName parseNameSuffix(String name) {
        if (name == null || name.isBlank()) {
            return ParsedName.EMPTY;
        }
        String trimmed = name.trim();
        int em = trimmed.indexOf(" — ");
        if (em > 0) {
            String base = trimmed.substring(0, em).trim();
            String rest = trimmed.substring(em + 3).trim();
            return splitOptions(base, rest);
        }
        Matcher spaced = SPACED_COLOR_THEN_SIZE.matcher(trimmed);
        if (spaced.matches()) {
            return new ParsedName(
                blankToNull(spaced.group(1)),
                blankToNull(spaced.group(3)),
                blankToNull(spaced.group(2))
            );
        }
        Matcher hyphen = HYPHEN_SIZE_COLOR.matcher(trimmed);
        if (hyphen.matches()) {
            return new ParsedName(
                blankToNull(hyphen.group(1)),
                blankToNull(hyphen.group(2)),
                blankToNull(hyphen.group(3))
            );
        }
        return ParsedName.EMPTY;
    }

    private static ParsedName splitOptions(String base, String rest) {
        int slash = rest.indexOf(" / ");
        if (slash >= 0) {
            String left = rest.substring(0, slash).trim();
            String right = rest.substring(slash + 3).trim();
            // Em-dash form is usually Size / Color; COMMON SIZE / Brown matches that.
            // If left looks like a print and right looks like a size, swap.
            if (!looksLikeSize(left) && looksLikeSize(right)) {
                return new ParsedName(blankToNull(base), blankToNull(right), blankToNull(left));
            }
            return new ParsedName(blankToNull(base), blankToNull(left), blankToNull(right));
        }
        return new ParsedName(blankToNull(base), blankToNull(rest), null);
    }

    private static boolean looksLikeSize(String value) {
        return value != null && SIZE_TOKEN.matcher(value.trim()).matches();
    }

    /** Zoho MN descriptions: {@code Baby Swaddle | Age: COMMON SIZE | Print: Aqua}. */
    private static String pipeDescriptionSize(Map<String, Object> item) {
        return pipeDescriptionField(item, "age");
    }

    private static String pipeDescriptionColor(Map<String, Object> item) {
        return firstNonBlank(pipeDescriptionField(item, "print"), pipeDescriptionField(item, "color"));
    }

    private static String pipeDescriptionBase(Map<String, Object> item) {
        String desc = stringValue(item, "description");
        if (desc == null) {
            return null;
        }
        String first = desc.split("\\|", 2)[0].trim();
        return StringUtils.isNotBlank(first) && !first.contains(":") ? first : null;
    }

    private static String pipeDescriptionField(Map<String, Object> item, String label) {
        String desc = stringValue(item, "description");
        if (desc == null || label == null) {
            return null;
        }
        String want = label.trim().toLowerCase(Locale.ROOT) + ":";
        for (String part : desc.split("\\|")) {
            String trimmed = part.trim();
            if (trimmed.toLowerCase(Locale.ROOT).startsWith(want)) {
                String value = trimmed.substring(want.length()).trim();
                return value.isEmpty() ? null : value;
            }
        }
        return null;
    }

    private static String stringValue(Map<String, Object> item, String key) {
        if (item == null || key == null) {
            return null;
        }
        Object value = item.get(key);
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.isNotBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private static String blankToNull(String value) {
        return StringUtils.isBlank(value) ? null : value.trim();
    }

    private enum AttributeRole {
        SIZE,
        COLOR,
        UNKNOWN,
    }

    record ParsedName(String baseName, String size, String color) {
        static final ParsedName EMPTY = new ParsedName(null, null, null);
    }
}
