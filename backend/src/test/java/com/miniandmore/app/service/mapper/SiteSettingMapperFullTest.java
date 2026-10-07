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
            .invoiceLogoUrl("https://cdn/invoice-logo.png")
            .invoiceWebsite("https://minisnmore.example")
            .invoiceGstin("29ABCDE1234F1Z5")
            .invoiceSoldByAddress("Seller address")
            .invoiceDispatchFromAddress("Dispatch address")
            .invoiceRegisteredOfficeAddress("Registered office")
            .invoiceCin("U12345KA2026PTC123456")
            .invoiceQrUrl("https://cdn/invoice-qr.png")
            .invoiceFooterNote("Thank you for shopping")
            .invoiceSellerState("Karnataka")
            .twitterUrl("https://twitter.com/m")
            .whatsappNumber("919876543210")
            .youtubeUrl("https://youtube.com/m")
            .gstRate(new BigDecimal("5"))
            .pricesIncludeGst(true)
            .shippingStandardInr(99)
            .shippingExpressInr(150)
            .freeShippingMinInr(2500)
            .codFeeInr(149)
            .deliveryEtaBufferDays(3)
            .returnWindowDays(3)
            .refundMinBusinessDays(2)
            .refundMaxBusinessDays(5);
        entity.setSubscribeHeroTitle("Subscribe");
        entity.setSubscribeHeroSubtitle("Get updates");
        entity.setSubscribeTestimonial("Love it!");
        entity.setSubscribeBadgesJson("[\"Badge\"]");
        entity.setSubscribePlansJson("[{\"name\":\"Basic\"}]");
        entity.setSubscribeFeaturesJson("[\"Feature\"]");
        entity.setAboutPageJson("{\"hero_title\":\"About us\"}");
        entity.setGiftPageJson("{\"hero_title\":\"Gift journey\"}");

        SiteSettingDTO dto = mapper.toDto(entity);
        SiteSetting roundTrip = mapper.toEntity(dto);
        SiteSetting copy = mapper.toEntity(dto);
        SiteSettingDTO patch = new SiteSettingDTO();
        patch.setBrandName("Updated Brand");
        mapper.partialUpdate(copy, patch);

        assertThat(dto.getBrandName()).isEqualTo("Mini & More");
        assertThat(dto.getInvoiceLogoUrl()).isEqualTo("https://cdn/invoice-logo.png");
        assertThat(dto.getInvoiceGstin()).isEqualTo("29ABCDE1234F1Z5");
        assertThat(dto.getInvoiceDispatchFromAddress()).isEqualTo("Dispatch address");
        assertThat(roundTrip.getInvoiceRegisteredOfficeAddress()).isEqualTo("Registered office");
        assertThat(roundTrip.getInvoiceQrUrl()).isEqualTo("https://cdn/invoice-qr.png");
        assertThat(roundTrip.getInvoiceSellerState()).isEqualTo("Karnataka");
        assertThat(dto.getSubscribeHeroTitle()).isEqualTo("Subscribe");
        assertThat(dto.getAboutPageJson()).isEqualTo("{\"hero_title\":\"About us\"}");
        assertThat(roundTrip.getAboutPageJson()).isEqualTo("{\"hero_title\":\"About us\"}");
        assertThat(dto.getGiftPageJson()).isEqualTo("{\"hero_title\":\"Gift journey\"}");
        assertThat(roundTrip.getGiftPageJson()).isEqualTo("{\"hero_title\":\"Gift journey\"}");
        assertThat(dto.getFreeShippingMinInr()).isEqualTo(2500);
        assertThat(roundTrip.getCodFeeInr()).isEqualTo(149);
        assertThat(roundTrip.getDeliveryEtaBufferDays()).isEqualTo(3);
        assertThat(roundTrip.getReturnWindowDays()).isEqualTo(3);
        assertThat(roundTrip.getRefundMinBusinessDays()).isEqualTo(2);
        assertThat(roundTrip.getRefundMaxBusinessDays()).isEqualTo(5);
        assertThat(roundTrip.getContactEmail()).isEqualTo("hello@example.com");
        assertThat(copy.getBrandName()).isEqualTo("Updated Brand");
        assertThat(mapper.toDto(List.of(entity))).hasSize(1);
    }
}
