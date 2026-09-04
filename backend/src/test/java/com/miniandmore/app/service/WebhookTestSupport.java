package com.miniandmore.app.service;

/** Shared helpers for webhook handler unit tests. */
final class WebhookTestSupport {

    private WebhookTestSupport() {}

    static String hmacSha256Hex(String secret, String body) {
        return WebhookHmacSupport.hmacSha256Hex(secret, body);
    }
}
