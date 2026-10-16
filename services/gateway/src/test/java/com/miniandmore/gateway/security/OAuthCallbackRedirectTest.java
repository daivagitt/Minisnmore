package com.miniandmore.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.miniandmore.gateway.config.ApplicationProperties;
import org.junit.jupiter.api.Test;

class OAuthCallbackRedirectTest {

    @Test
    void callbackUrl_buildsRedirect() {
        ApplicationProperties properties = new ApplicationProperties();
        properties.getOAuth2().setFrontendCallbackUrl("http://localhost:5173/auth/callback");

        String url = OAuthCallbackRedirect.callbackUrl(properties, "token", "jwt-value");

        assertThat(url).contains("http://localhost:5173/auth/callback");
        assertThat(url).contains("token=jwt-value");
    }
}
