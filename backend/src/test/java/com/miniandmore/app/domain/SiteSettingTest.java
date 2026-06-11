package com.miniandmore.app.domain;

import static com.miniandmore.app.domain.SiteSettingTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.miniandmore.app.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class SiteSettingTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(SiteSetting.class);
        SiteSetting siteSetting1 = getSiteSettingSample1();
        SiteSetting siteSetting2 = new SiteSetting();
        assertThat(siteSetting1).isNotEqualTo(siteSetting2);

        siteSetting2.setId(siteSetting1.getId());
        assertThat(siteSetting1).isEqualTo(siteSetting2);

        siteSetting2 = getSiteSettingSample2();
        assertThat(siteSetting1).isNotEqualTo(siteSetting2);
    }
}
