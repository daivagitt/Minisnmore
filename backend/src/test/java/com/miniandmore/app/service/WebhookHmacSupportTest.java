package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WebhookHmacSupportTest {

    @Test
    void hmacSha256Hex_isDeterministic() {
        String first = WebhookHmacSupport.hmacSha256Hex("secret", "payload");
        String second = WebhookHmacSupport.hmacSha256Hex("secret", "payload");

        assertThat(first).isEqualTo(second);
        assertThat(first).hasSize(64);
    }

    @Test
    void normalizeProvidedSignature_stripsSha256Prefix() {
        assertThat(WebhookHmacSupport.normalizeProvidedSignature("sha256=abc123")).isEqualTo("abc123");
        assertThat(WebhookHmacSupport.normalizeProvidedSignature("SHA256=def456")).isEqualTo("def456");
        assertThat(WebhookHmacSupport.normalizeProvidedSignature("  raw  ")).isEqualTo("raw");
        assertThat(WebhookHmacSupport.normalizeProvidedSignature(null)).isNull();
    }

    @Test
    void constantTimeEquals_handlesNullAndLengthMismatch() {
        assertThat(WebhookHmacSupport.constantTimeEquals(null, "abc")).isFalse();
        assertThat(WebhookHmacSupport.constantTimeEquals("abc", null)).isFalse();
        assertThat(WebhookHmacSupport.constantTimeEquals("abc", "abcd")).isFalse();
    }

    @Test
    void constantTimeEquals_isCaseInsensitive() {
        assertThat(WebhookHmacSupport.constantTimeEquals("AbC123", "aBc123")).isTrue();
        assertThat(WebhookHmacSupport.constantTimeEquals("abc", "abd")).isFalse();
    }

    @Test
    void signaturesMatch_validSignature_returnsTrue() {
        String secret = "webhook-secret";
        String payload = "{\"id\":\"evt-1\"}";
        String expected = WebhookHmacSupport.hmacSha256Hex(secret, payload);

        assertThat(WebhookHmacSupport.signaturesMatch(secret, payload, "sha256=" + expected)).isTrue();
    }

    @Test
    void signaturesMatch_blankSecretOrSignature_returnsFalse() {
        assertThat(WebhookHmacSupport.signaturesMatch("", "payload", "sig")).isFalse();
        assertThat(WebhookHmacSupport.signaturesMatch("secret", "payload", " ")).isFalse();
    }
}
