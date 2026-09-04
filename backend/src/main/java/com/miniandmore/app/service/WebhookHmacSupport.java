package com.miniandmore.app.service;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.apache.commons.lang3.StringUtils;

/** Shared HMAC-SHA256 hex helpers for signed webhook payloads. */
public final class WebhookHmacSupport {

    private WebhookHmacSupport() {}

    public static String hmacSha256Hex(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC unavailable", e);
        }
    }

    public static String normalizeProvidedSignature(String signature) {
        if (signature == null) {
            return null;
        }
        String provided = signature.trim();
        if (provided.regionMatches(true, 0, "sha256=", 0, 7)) {
            return provided.substring(7);
        }
        return provided;
    }

    public static boolean constantTimeEquals(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        String a = left.toLowerCase();
        String b = right.toLowerCase();
        if (a.length() != b.length()) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < a.length(); i++) {
            diff |= a.charAt(i) ^ b.charAt(i);
        }
        return diff == 0;
    }

    public static boolean signaturesMatch(String secret, String rawBody, String signatureHeader) {
        if (StringUtils.isBlank(secret) || StringUtils.isBlank(signatureHeader)) {
            return false;
        }
        String expected = hmacSha256Hex(secret, rawBody);
        String provided = normalizeProvidedSignature(signatureHeader);
        return constantTimeEquals(expected, provided);
    }
}
