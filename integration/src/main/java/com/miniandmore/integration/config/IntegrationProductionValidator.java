package com.miniandmore.integration.config;

import java.util.Arrays;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
public class IntegrationProductionValidator {

    public IntegrationProductionValidator(Environment environment, ApplicationProperties applicationProperties) {
        if (!Arrays.asList(environment.getActiveProfiles()).contains("prod")) {
            return;
        }
        if (StringUtils.isBlank(applicationProperties.getInternalApiKey())) {
            throw new IllegalStateException("application.internal-api-key must be set in production");
        }
        ApplicationProperties.Zoho.OAuth oauth = applicationProperties.getZoho().getOauth();
        if (oauth == null) {
            throw new IllegalStateException("application.zoho.oauth must be configured in production");
        }
        assertHttpsOrigin("application.zoho.oauth.homepage-url", oauth.getHomepageUrl());
        List<String> redirectUris = oauth.getRedirectUris();
        if (redirectUris != null) {
            for (String uri : redirectUris) {
                assertHttpsOrigin("application.zoho.oauth.redirect-uris", uri);
            }
        }
        List<String> javascriptDomains = oauth.getJavascriptDomains();
        if (javascriptDomains != null) {
            for (String domain : javascriptDomains) {
                assertHttpsOrigin("application.zoho.oauth.javascript-domains", domain);
            }
        }
    }

    private static void assertHttpsOrigin(String property, String value) {
        String trimmed = StringUtils.trimToEmpty(value);
        if (trimmed.isEmpty()) {
            return;
        }
        if (!trimmed.startsWith("https://")) {
            throw new IllegalStateException(property + " must use https in production: " + trimmed);
        }
        if (trimmed.contains("localhost") || trimmed.contains("127.0.0.1")) {
            throw new IllegalStateException(property + " must not use localhost in production: " + trimmed);
        }
    }
}
