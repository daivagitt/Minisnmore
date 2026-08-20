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
        return parsed.baseName() != null && parsed.size() != null && parsed.color() != null;
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
        return parsed.size();
    }

    /** Color / print option: named Color attribute, else remaining attribute, else name suffix. */
    public static String color(Map<String, Object> item) {
        String fromAttrs = optionForRole(item, AttributeRole.COLOR);
        if (fromAttrs != null) {
            return fromAttrs;
        }
        ParsedName parsed = parseNameSuffix(itemName(item));
        return parsed.color();
    }

    public static String parentDisplayName(Map<String, Object> item) {
        String group = groupName(item);
        if (group != null) {
            return group;
        }
        ParsedName parsed = parseNameSuffix(itemName(item));
        if (parsed.baseName() != null) {
            return parsed.baseName();
        }
        return itemName(item);
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
     * Mini's push format: {@code Horizon Set — S / Navy}. Also accepts {@code Horizon Set-S-Navy}.
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
            return new ParsedName(blankToNull(base), blankToNull(left), blankToNull(right));
        }
        return new ParsedName(blankToNull(base), blankToNull(rest), null);
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
