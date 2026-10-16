package com.miniandmore.commerce.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ApplicationPropertiesTest {

    @Test
    void defaultCatalogBaseUrl() {
        ApplicationProperties props = new ApplicationProperties();
        assertThat(props.getCatalogBaseUrl()).isEqualTo("http://127.0.0.1:8084");
    }

    @Test
    void setCatalogBaseUrl() {
        ApplicationProperties props = new ApplicationProperties();
        props.setCatalogBaseUrl("http://catalog:8084");
        assertThat(props.getCatalogBaseUrl()).isEqualTo("http://catalog:8084");
    }
}
