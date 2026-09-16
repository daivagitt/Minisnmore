package com.miniandmore.app.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.apache.commons.lang3.StringUtils;

/** Shared HMAC-SHA256 helpers for signed webhook payloads (hex and base64). */
public final class WebhookHmacSupport {

    private WebhookHmacSupport() {}

    public static String hmacSha256Hex(String secret, String payload) {
        return HexFormat.of().formatHex(hmacSha256(secret, payload));
    }

    public static String hmacSha256Base64(String secret, String payload) {
        return Base64.getEncoder().encodeToString(hmacSha256(secret, payload));
    }

    private static byte[] hmacSha256(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
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
        byte[] digest = hmacSha256(secret, rawBody);
        String provided = normalizeProvidedSignature(signatureHeader);
        if (constantTimeEquals(HexFormat.of().formatHex(digest), provided)) {
            return true;
        }
        byte[] expectedBase64 = Base64.getEncoder().encodeToString(digest).getBytes(StandardCharsets.UTF_8);
        byte[] providedBytes = provided.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expectedBase64, providedBytes);
    }
}
