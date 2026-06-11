package com.miniandmore.app.util;

import java.util.regex.Pattern;

/**
 * Validates storefront navigation targets (banner / hero carousel CTAs).
 */
public final class InternalLinkValidator {

    private static final Pattern UNSAFE_SCHEME = Pattern.compile("^[a-z][a-z0-9+.-]*:", Pattern.CASE_INSENSITIVE);

    private static final Pattern INTERNAL_PATH = Pattern.compile("^/[^\\s]*$");

    private InternalLinkValidator() {}

    public static boolean isValid(String value) {
        if (value == null || value.isBlank()) {
            return true;
        }
        String trimmed = value.trim();
        if (UNSAFE_SCHEME.matcher(trimmed).find() || trimmed.startsWith("//")) {
            return false;
        }
        return INTERNAL_PATH.matcher(trimmed).matches() && !trimmed.startsWith("//");
    }
}
