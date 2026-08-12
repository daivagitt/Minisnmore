package com.miniandmore.app.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ZohoSettingEntityTest {

    @Test
    void fluentSetters_equals_hashCode_andToString() {
        ZohoSetting setting = new ZohoSetting()
            .id(1L)
            .orgId("org-1")
            .clientId("client")
            .clientSecret("secret")
            .refreshToken("refresh")
            .dataCenter("in")
            .connected(true)
            .pullProductsAutoCreateCategory(true);

        ZohoSetting sameId = new ZohoSetting().id(1L);
        ZohoSetting otherId = new ZohoSetting().id(2L);

        assertThat(setting.getOrgId()).isEqualTo("org-1");
        assertThat(setting.getConnected()).isTrue();
        assertThat(setting).isEqualTo(sameId);
        assertThat(setting).isNotEqualTo(otherId);
        assertThat(setting).isNotEqualTo("not-an-entity");
        assertThat(setting.hashCode()).isEqualTo(sameId.hashCode());
        assertThat(setting.toString()).contains("org-1").contains("connected");
    }
}
