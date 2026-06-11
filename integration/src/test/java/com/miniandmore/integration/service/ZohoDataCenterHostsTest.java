package com.miniandmore.integration.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ZohoDataCenterHostsTest {

    @Test
    void normalize_blankDefaultsToIndia() {
        assertThat(ZohoDataCenterHosts.normalize(null)).isEqualTo("in");
        assertThat(ZohoDataCenterHosts.normalize("  ")).isEqualTo("in");
    }

    @Test
    void normalize_aliasesUsAndAu() {
        assertThat(ZohoDataCenterHosts.normalize("US")).isEqualTo("com");
        assertThat(ZohoDataCenterHosts.normalize("au")).isEqualTo("com.au");
    }

    @Test
    void normalize_unsupportedFallsBackToIndia() {
        assertThat(ZohoDataCenterHosts.normalize("invalid")).isEqualTo("in");
    }

    @Test
    void isSupported_knownRegions() {
        assertThat(ZohoDataCenterHosts.isSupported("in")).isTrue();
        assertThat(ZohoDataCenterHosts.isSupported("com")).isTrue();
        assertThat(ZohoDataCenterHosts.isSupported("eu")).isTrue();
        assertThat(ZohoDataCenterHosts.isSupported("com.au")).isTrue();
        assertThat(ZohoDataCenterHosts.isSupported("jp")).isTrue();
        assertThat(ZohoDataCenterHosts.isSupported("ca")).isTrue();
        assertThat(ZohoDataCenterHosts.isSupported("sa")).isTrue();
        assertThat(ZohoDataCenterHosts.isSupported("xx")).isFalse();
    }

    @Test
    void hostsAndUrls_forEachRegion() {
        assertThat(ZohoDataCenterHosts.accountsHost("in")).isEqualTo("accounts.zoho.in");
        assertThat(ZohoDataCenterHosts.accountsBaseUrl("com")).isEqualTo("https://accounts.zoho.com");
        assertThat(ZohoDataCenterHosts.authorizationUrl("eu")).contains("accounts.zoho.eu/oauth/v2/auth");
        assertThat(ZohoDataCenterHosts.apiConsoleUrl("com.au")).isEqualTo("https://api-console.zoho.com.au/");
        assertThat(ZohoDataCenterHosts.booksApiDomainSuffix("ca")).isEqualTo("ca");
        assertThat(ZohoDataCenterHosts.booksApiBaseUrl("in")).isEqualTo("https://www.zohoapis.in/books/v3");
    }
}
