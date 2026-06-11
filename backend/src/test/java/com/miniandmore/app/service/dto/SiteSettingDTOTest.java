package com.miniandmore.app.service.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.miniandmore.app.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class SiteSettingDTOTest {

    @Test
    void dtoEqualsVerifier() throws Exception {
        TestUtil.equalsVerifier(SiteSettingDTO.class);
        SiteSettingDTO siteSettingDTO1 = new SiteSettingDTO();
        siteSettingDTO1.setId(1L);
        SiteSettingDTO siteSettingDTO2 = new SiteSettingDTO();
        assertThat(siteSettingDTO1).isNotEqualTo(siteSettingDTO2);
        siteSettingDTO2.setId(siteSettingDTO1.getId());
        assertThat(siteSettingDTO1).isEqualTo(siteSettingDTO2);
        siteSettingDTO2.setId(2L);
        assertThat(siteSettingDTO1).isNotEqualTo(siteSettingDTO2);
        siteSettingDTO1.setId(null);
        assertThat(siteSettingDTO1).isNotEqualTo(siteSettingDTO2);
    }
}
