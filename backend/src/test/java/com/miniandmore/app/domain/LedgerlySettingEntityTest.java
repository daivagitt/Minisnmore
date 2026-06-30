package com.miniandmore.app.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LedgerlySettingEntityTest {

    @Test
    void equalsAndHashCode_useId() {
        LedgerlySetting setting = new LedgerlySetting();
        setting.setId(1L);
        setting.setApiBaseUrl("https://ledgerly.example/api");
        setting.setApiToken("token");
        setting.setOrgId("org-1");
        setting.setConnected(true);
        setting.setPullProductsAutoCreateCategory(true);

        LedgerlySetting sameId = new LedgerlySetting();
        sameId.setId(1L);
        LedgerlySetting otherId = new LedgerlySetting();
        otherId.setId(2L);

        assertThat(setting).isEqualTo(sameId);
        assertThat(setting).isNotEqualTo(otherId);
        assertThat(setting.getApiBaseUrl()).isEqualTo("https://ledgerly.example/api");
    }
}
