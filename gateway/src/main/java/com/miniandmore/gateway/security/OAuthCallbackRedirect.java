package com.miniandmore.gateway.security;

import com.miniandmore.gateway.config.ApplicationProperties;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

final class OAuthCallbackRedirect {

    private OAuthCallbackRedirect() {}

    static String callbackUrl(ApplicationProperties applicationProperties, String key, String value) {
        var base = applicationProperties.getOAuth2().getFrontendCallbackUrl();
        var separator = base.contains("?") ? "&" : "?";
        return base + separator + key + "=" + encode(value);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
