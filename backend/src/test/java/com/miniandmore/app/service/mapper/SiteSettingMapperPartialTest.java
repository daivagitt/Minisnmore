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
        patch.setAboutPageJson("{\"hero_title\":\"About\"}");
        patch.setGiftPageJson("{\"hero_title\":\"Gift\"}");
        patch.setInvoiceLogoUrl("https://cdn/inv-logo.png");
        patch.setInvoiceWebsite("https://shop.example");
        patch.setInvoiceGstin("29ABCDE1234F1Z5");
        patch.setInvoiceSoldByAddress("Sold by");
        patch.setInvoiceDispatchFromAddress("Dispatch");
        patch.setInvoiceRegisteredOfficeAddress("Registered");
        patch.setInvoiceCin("U12345KA2026PTC000001");
        patch.setInvoiceQrUrl("https://cdn/qr.png");
        patch.setInvoiceFooterNote("Thanks");
        patch.setInvoiceSellerState("Karnataka");
        patch.setCodFeeInr(99);
        patch.setDeliveryEtaBufferDays(2);
        patch.setReturnWindowDays(7);
        patch.setRefundMinBusinessDays(3);
        patch.setRefundMaxBusinessDays(10);

        mapper.partialUpdate(entity, patch);

        assertThat(entity.getContactEmail()).isEqualTo("a@b.com");
        assertThat(entity.getSubscribeHeroTitle()).isEqualTo("Hero");
        assertThat(entity.getAboutPageJson()).isEqualTo("{\"hero_title\":\"About\"}");
        assertThat(entity.getGiftPageJson()).isEqualTo("{\"hero_title\":\"Gift\"}");
        assertThat(entity.getGstRate()).isEqualByComparingTo("18");
        assertThat(entity.getInvoiceGstin()).isEqualTo("29ABCDE1234F1Z5");
        assertThat(entity.getCodFeeInr()).isEqualTo(99);
        assertThat(entity.getRefundMaxBusinessDays()).isEqualTo(10);
        mapper.partialUpdate(entity, null);
    }
}
