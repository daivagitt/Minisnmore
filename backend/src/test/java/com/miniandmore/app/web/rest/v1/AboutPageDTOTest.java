package com.miniandmore.app.web.rest.v1;

import static org.assertj.core.api.Assertions.assertThat;

import com.miniandmore.app.service.dto.SiteSettingDTO;
import org.junit.jupiter.api.Test;

class AboutPageDTOTest {

    @Test
    void from_nullSettings_returnsEmptyDto() {
        AboutPageDTO dto = AboutPageDTO.from(null);

        assertThat(dto.getAboutPageJson()).isNull();
    }

    @Test
    void from_siteSettings_mapsAboutPageJson() {
        SiteSettingDTO settings = new SiteSettingDTO();
        settings.setAboutPageJson("{\"hero_title\":\"Hello\"}");

        AboutPageDTO dto = AboutPageDTO.from(settings);

        assertThat(dto.getAboutPageJson()).isEqualTo("{\"hero_title\":\"Hello\"}");
    }

    @Test
    void gettersAndSetters_roundTrip() {
        AboutPageDTO dto = new AboutPageDTO();
        dto.setAboutPageJson("{}");

        assertThat(dto.getAboutPageJson()).isEqualTo("{}");
    }
}
