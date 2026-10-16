package com.miniandmore.commerce.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "application", ignoreUnknownFields = false)
public class ApplicationProperties {

    /**
     * Base URL of the catalog service (direct, not via gateway). Dev: http://127.0.0.1:8084
     */
    private String catalogBaseUrl = "http://127.0.0.1:8084";

    public String getCatalogBaseUrl() {
        return catalogBaseUrl;
    }

    public void setCatalogBaseUrl(String catalogBaseUrl) {
        this.catalogBaseUrl = catalogBaseUrl;
    }
}
