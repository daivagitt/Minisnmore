package com.miniandmore.integration.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ZohoDataCenterHostsFullTest {

    @Test
    void allRegions_haveDistinctHosts() {
        for (String dc : new String[] { "in", "com", "eu", "com.au", "jp", "ca", "sa" }) {
            assertThat(ZohoDataCenterHosts.accountsHost(dc)).contains("zoho");
            assertThat(ZohoDataCenterHosts.apiConsoleUrl(dc)).startsWith("https://");
            assertThat(ZohoDataCenterHosts.booksApiBaseUrl(dc)).contains("/books/v3");
        }
    }

    @Test
    void booksApiDomainSuffix_caOnly() {
        assertThat(ZohoDataCenterHosts.booksApiDomainSuffix("ca")).isEqualTo("ca");
        assertThat(ZohoDataCenterHosts.booksApiDomainSuffix("com")).isEqualTo("com");
    }
}
