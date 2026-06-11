package com.miniandmore.app.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ApplicationPropertiesZohoTest {

    @Test
    void zohoNestedProperties_roundTrip() {
        ApplicationProperties properties = new ApplicationProperties();
        ApplicationProperties.Zoho zoho = properties.getZoho();
        zoho.setSyncProductImages(true);
        zoho.setDefaultLocationName("Warehouse");
        zoho.setPullProductsAutoCreateCategory(true);

        ApplicationProperties.Zoho.OAuth oauth = zoho.getOauth();
        oauth.setClientId("client");
        oauth.setClientSecret("secret");
        oauth.setHomepageUrl("https://app.example.com");
        oauth.setRedirectUris(java.util.List.of("https://app.example.com/callback"));
        oauth.setOrgId("org-1");

        ApplicationProperties.Zoho.OrderTracking tracking = zoho.getOrderTracking();
        tracking.setFixedDelayMs(900_000L);

        ApplicationProperties.Zoho.OrderRetry retry = zoho.getOrderRetry();
        retry.setFixedDelayMs(600_000L);

        assertThat(zoho.isSyncProductImages()).isTrue();
        assertThat(oauth.getClientId()).isEqualTo("client");
        assertThat(oauth.getRedirectUris()).containsExactly("https://app.example.com/callback");
        assertThat(tracking.getFixedDelayMs()).isEqualTo(900_000L);
        assertThat(retry.getFixedDelayMs()).isEqualTo(600_000L);

        oauth.setScopes("scope1");
        oauth.setJavascriptDomains(java.util.List.of("http://localhost:5173"));
        oauth.setDefaultDataCenter("us");
        assertThat(oauth.getScopes()).isEqualTo("scope1");
        assertThat(oauth.getDefaultDataCenter()).isEqualTo("us");

        ApplicationProperties.Inventory inventory = properties.getInventory();
        inventory.getReservationRelease().setFixedDelayMs(120_000L);
        assertThat(inventory.getReservationRelease().getFixedDelayMs()).isEqualTo(120_000L);
    }
}
