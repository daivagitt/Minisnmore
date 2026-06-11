package com.miniandmore.app.web.rest.v1;

import static org.assertj.core.api.Assertions.assertThat;

import com.miniandmore.app.service.dto.SiteSettingDTO;
import org.junit.jupiter.api.Test;

class SubscribePageDTOTest {

    @Test
    void from_nullSettings_returnsEmptyDto() {
        SubscribePageDTO dto = SubscribePageDTO.from(null);

        assertThat(dto.getHeroTitle()).isNull();
        assertThat(dto.getHeroSubtitle()).isNull();
    }

    @Test
    void from_siteSettings_mapsSubscribeFields() {
        SiteSettingDTO settings = new SiteSettingDTO();
        settings.setSubscribeHeroTitle("Join us");
        settings.setSubscribeHeroSubtitle("Monthly plans");
        settings.setSubscribeTestimonial("Love it");
        settings.setSubscribeBadgesJson("[\"badge\"]");
        settings.setSubscribePlansJson("[\"plan\"]");
        settings.setSubscribeFeaturesJson("[\"feature\"]");

        SubscribePageDTO dto = SubscribePageDTO.from(settings);

        assertThat(dto.getHeroTitle()).isEqualTo("Join us");
        assertThat(dto.getHeroSubtitle()).isEqualTo("Monthly plans");
        assertThat(dto.getTestimonial()).isEqualTo("Love it");
        assertThat(dto.getBadgesJson()).isEqualTo("[\"badge\"]");
        assertThat(dto.getPlansJson()).isEqualTo("[\"plan\"]");
        assertThat(dto.getFeaturesJson()).isEqualTo("[\"feature\"]");
    }

    @Test
    void gettersAndSetters_roundTrip() {
        SubscribePageDTO dto = new SubscribePageDTO();
        dto.setHeroTitle("Title");
        dto.setHeroSubtitle("Subtitle");
        dto.setTestimonial("Quote");
        dto.setBadgesJson("[]");
        dto.setPlansJson("[]");
        dto.setFeaturesJson("[]");

        assertThat(dto.getHeroTitle()).isEqualTo("Title");
        assertThat(dto.getHeroSubtitle()).isEqualTo("Subtitle");
        assertThat(dto.getTestimonial()).isEqualTo("Quote");
    }
}
