package com.miniandmore.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Properties specific to Gateway.
 * <p>
 * Properties are configured in the {@code application.yml} file.
 * See {@link tech.jhipster.config.JHipsterProperties} for a good example.
 */
@ConfigurationProperties(prefix = "application", ignoreUnknownFields = false)
public class ApplicationProperties {

    private final OAuth2 oauth2 = new OAuth2();

    /** When true, new registrations are activated immediately (useful when SMTP is unreliable on UAT). */
    private boolean registerAutoActivate = false;

    public OAuth2 getOAuth2() {
        return oauth2;
    }

    public boolean isRegisterAutoActivate() {
        return registerAutoActivate;
    }

    public void setRegisterAutoActivate(boolean registerAutoActivate) {
        this.registerAutoActivate = registerAutoActivate;
    }

    public static class OAuth2 {

        private String frontendCallbackUrl = "http://localhost:5173/auth/callback";

        public String getFrontendCallbackUrl() {
            return frontendCallbackUrl;
        }

        public void setFrontendCallbackUrl(String frontendCallbackUrl) {
            this.frontendCallbackUrl = frontendCallbackUrl;
        }
    }
}
