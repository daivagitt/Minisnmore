package com.miniandmore.app.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Shared helpers for Zoho Books inbound webhook handlers. */
public final class ZohoWebhookSupport {

    private static final Pattern UNRESOLVED_PLACEHOLDER = Pattern.compile("^\\$\\{[A-Za-z0-9_.]+\\}$");

    private ZohoWebhookSupport() {}

    @SuppressWarnings("unchecked")
    public static Map<String, Object> eventData(Map<String, Object> payload) {
        Object data = payload.get("data");
        if (data instanceof Map<?, ?> map) {
            Map<String, Object> merged = new LinkedHashMap<>(payload);
            merged.putAll((Map<String, Object>) map);
            return merged;
        }
        return payload;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> mergeNestedPayload(Map<String, Object> data, String... nestedKeys) {
        for (String key : nestedKeys) {
            Object nested = data.get(key);
            if (nested instanceof Map<?, ?> map) {
                Map<String, Object> merged = new LinkedHashMap<>(data);
                merged.putAll((Map<String, Object>) map);
                return merged;
            }
        }
        return data;
    }

    public static String explicitEventId(Map<String, Object>... sources) {
        for (Map<String, Object> source : sources) {
            if (source == null) {
                continue;
            }
            String explicit = firstNonBlank(
                stringValue(source.get("eventId")),
                stringValue(source.get("event_id"))
            );
            if (explicit != null) {
                return explicit;
            }
        }
        return null;
    }

    public static String explicitEventIdIncludingLegacyId(Map<String, Object>... sources) {
        for (Map<String, Object> source : sources) {
            if (source == null) {
                continue;
            }
            String explicit = firstNonBlank(
                stringValue(source.get("eventId")),
                stringValue(source.get("event_id")),
                stringValue(source.get("id"))
            );
            if (explicit != null) {
                return explicit;
            }
        }
        return null;
    }

    public static Map<String, Object> duplicateEventResult(String eventId) {
        return Map.of("ok", true, "duplicate", true, "eventId", eventId);
    }

    public static Map<String, Object> disabledWebhookResult() {
        return Map.of("ok", true, "skipped", true, "reason", "inbound webhook disabled");
    }

    public static void verifySecretOrSignature(
        String configuredSecret,
        String rawBody,
        String signature,
        String sharedSecret,
        String notConfiguredMessage,
        String missingAuthMessage,
        String invalidSignatureMessage
    ) {
        String secret = StringUtils.trimToNull(configuredSecret);
        if (secret == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, notConfiguredMessage);
        }
        if (StringUtils.equals(secret, StringUtils.trimToNull(sharedSecret))) {
            return;
        }
        if (StringUtils.isBlank(signature)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, missingAuthMessage);
        }
        if (!WebhookHmacSupport.signaturesMatch(secret, rawBody, signature)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, invalidSignatureMessage);
        }
    }

    public static String firstNonBlank(String... values) {
        for (String value : values) {
            String trimmed = StringUtils.trimToNull(blankIfUnresolvedPlaceholder(value));
            if (trimmed != null) {
                return trimmed;
            }
        }
        return null;
    }

    public static String stringValue(Object value) {
        return blankIfUnresolvedPlaceholder(value == null ? null : value.toString());
    }

    public static boolean isUnresolvedPlaceholder(String value) {
        String trimmed = StringUtils.trimToNull(value);
        return trimmed != null && UNRESOLVED_PLACEHOLDER.matcher(trimmed).matches();
    }

    public static void blankUnresolvedPlaceholders(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (entry.getValue() instanceof String text && (isUnresolvedPlaceholder(text) || StringUtils.isBlank(text))) {
                entry.setValue(null);
            }
        }
    }

    private static String blankIfUnresolvedPlaceholder(String value) {
        return isUnresolvedPlaceholder(value) ? null : value;
    }

    public static String normalizeStatusToken(String status) {
        String trimmed = StringUtils.trimToNull(status);
        return trimmed == null ? "unknown" : trimmed.toLowerCase();
    }
}
