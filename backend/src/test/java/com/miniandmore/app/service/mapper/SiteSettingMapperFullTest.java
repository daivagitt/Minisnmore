package com.miniandmore.app.service.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.miniandmore.app.domain.SiteSetting;
import com.miniandmore.app.service.dto.SiteSettingDTO;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class SiteSettingMapperFullTest {

    @Test
    void siteSettingMapper_roundTripsAllCommerceAndSubscribeFields() {
        SiteSettingMapper mapper = new SiteSettingMapperImpl();
        SiteSetting entity = new SiteSetting()
            .id(1L)
            .brandName("Mini & More")
            .careLabel("Hand wash")
            .contactEmail("hello@example.com")
            .contactPhone("9876543210")
            .ecatalogueLabel("Catalogue")
            .ecatalogueUrl("https://example.com/cat")
            .facebookUrl("https://facebook.com/m")
            .footerCopyright("2026")
            .footerTagline("For little ones")
            .instagramUrl("https://instagram.com/m")
            .legalEntityName("Mini LLP")
            .logoHorizontalUrl("https://cdn/logo-h.png")
            .logoUrl("https://cdn/logo.png")
            .twitterUrl("https://twitter.com/m")
            .whatsappNumber("919876543210")
            .youtubeUrl("https://youtube.com/m")
            .gstRate(new BigDecimal("5"))
            .pricesIncludeGst(true)
            .shippingStandardInr(99)
            .shippingExpressInr(150)
            .freeShippingMinInr(2500);
        entity.setSubscribeHeroTitle("Subscribe");
        entity.setSubscribeHeroSubtitle("Get updates");
        entity.setSubscribeTestimonial("Love it!");
        entity.setSubscribeBadgesJson("[\"Badge\"]");
        entity.setSubscribePlansJson("[{\"name\":\"Basic\"}]");
        entity.setSubscribeFeaturesJson("[\"Feature\"]");

        SiteSettingDTO dto = mapper.toDto(entity);
        SiteSetting roundTrip = mapper.toEntity(dto);
        SiteSetting copy = mapper.toEntity(dto);
        SiteSettingDTO patch = new SiteSettingDTO();
        patch.setBrandName("Updated Brand");
        mapper.partialUpdate(copy, patch);

        assertThat(dto.getBrandName()).isEqualTo("Mini & More");
        assertThat(dto.getSubscribeHeroTitle()).isEqualTo("Subscribe");
        assertThat(dto.getFreeShippingMinInr()).isEqualTo(2500);
        assertThat(roundTrip.getContactEmail()).isEqualTo("hello@example.com");
        assertThat(copy.getBrandName()).isEqualTo("Updated Brand");
        assertThat(mapper.toDto(List.of(entity))).hasSize(1);
    }
}
