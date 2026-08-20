package com.miniandmore.app.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SpringDocConfigurationTest {

    @Test
    void toPublicApiPath_rewritesInternalPrefix() {
        assertThat(SpringDocConfiguration.toPublicApiPath("/internal/v1/orders")).isEqualTo("/api/v1/orders");
        assertThat(SpringDocConfiguration.toPublicApiPath("/internal/v1/admin/products")).isEqualTo("/api/v1/admin/products");
    }

    @Test
    void toPublicApiPath_leavesPublicPathsUntouched() {
        assertThat(SpringDocConfiguration.toPublicApiPath("/api/v1/admin/zoho/test")).isEqualTo("/api/v1/admin/zoho/test");
    }
}
