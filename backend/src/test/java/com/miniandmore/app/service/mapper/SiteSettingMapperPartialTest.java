package com.miniandmore.app.service.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.miniandmore.app.domain.SiteSetting;
import com.miniandmore.app.service.dto.SiteSettingDTO;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class SiteSettingMapperPartialTest {

    @Test
    void nullBranchesAndPartialUpdateEveryField() {
        SiteSettingMapper mapper = new SiteSettingMapperImpl();
        assertThat(mapper.toDto((SiteSetting) null)).isNull();
        assertThat(mapper.toEntity((SiteSettingDTO) null)).isNull();
        assertThat(mapper.toDto((List<SiteSetting>) null)).isNull();
        assertThat(mapper.toEntity((List<SiteSettingDTO>) null)).isNull();

        SiteSetting entity = new SiteSetting().brandName("Brand").gstRate(BigDecimal.ONE).pricesIncludeGst(true).shippingStandardInr(1).shippingExpressInr(1).freeShippingMinInr(1);
        SiteSettingDTO patch = new SiteSettingDTO();
        patch.setCareLabel("Care");
        patch.setContactEmail("a@b.com");
        patch.setContactPhone("99");
        patch.setEcatalogueLabel("Cat");
        patch.setEcatalogueUrl("url");
        patch.setFacebookUrl("fb");
        patch.setFooterCopyright("c");
        patch.setFooterTagline("t");
        patch.setInstagramUrl("ig");
        patch.setLegalEntityName("LLP");
        patch.setLogoHorizontalUrl("lh");
        patch.setLogoUrl("l");
        patch.setTwitterUrl("tw");
        patch.setWhatsappNumber("wa");
        patch.setYoutubeUrl("yt");
        patch.setGstRate(new BigDecimal("18"));
        patch.setPricesIncludeGst(false);
        patch.setShippingStandardInr(50);
        patch.setShippingExpressInr(80);
        patch.setFreeShippingMinInr(999);
        patch.setSubscribeHeroTitle("Hero");
        patch.setSubscribeHeroSubtitle("Sub");
        patch.setSubscribeTestimonial("Test");
        patch.setSubscribeBadgesJson("[]");
        patch.setSubscribePlansJson("[]");
        patch.setSubscribeFeaturesJson("[]");

        mapper.partialUpdate(entity, patch);

        assertThat(entity.getContactEmail()).isEqualTo("a@b.com");
        assertThat(entity.getSubscribeHeroTitle()).isEqualTo("Hero");
        assertThat(entity.getGstRate()).isEqualByComparingTo("18");
        mapper.partialUpdate(entity, null);
    }
}
