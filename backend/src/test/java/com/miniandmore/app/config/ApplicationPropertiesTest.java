package com.miniandmore.app.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ApplicationPropertiesTest {

    @Test
    void nestedProperties_haveDefaultsAndSetters() {
        ApplicationProperties properties = new ApplicationProperties();

        assertThat(properties.getLiquibase().getAsyncStart()).isTrue();
        properties.getLiquibase().setAsyncStart(false);
        assertThat(properties.getLiquibase().getAsyncStart()).isFalse();

        assertThat(properties.getStorage().getS3().getRegion()).isEqualTo("us-east-1");
        properties.getStorage().getS3().setBucket("bucket");
        assertThat(properties.getStorage().getS3().getBucket()).isEqualTo("bucket");

        assertThat(properties.getZoho()).isNotNull();
        assertThat(properties.getInventory()).isNotNull();
    }

    @Test
    void zohoOAuthProperties_roundTrip() {
        ApplicationProperties properties = new ApplicationProperties();
        var oauth = properties.getZoho().getOauth();
        oauth.setClientId("client");
        oauth.setClientSecret("secret");
        oauth.setHomepageUrl("https://example.com");
        oauth.setScopes("ZohoBooks.fullaccess.all");

        assertThat(properties.getZoho().getOauth().getClientId()).isEqualTo("client");
        assertThat(properties.getZoho().getOauth().getClientSecret()).isEqualTo("secret");
        assertThat(properties.getZoho().getOauth().getHomepageUrl()).isEqualTo("https://example.com");
        assertThat(properties.getZoho().getOauth().getScopes()).isEqualTo("ZohoBooks.fullaccess.all");
    }
}
