package com.miniandmore.app.domain;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public class SiteSettingTestSamples {

    private static final Random random = new Random();
    private static final AtomicLong longCount = new AtomicLong(random.nextInt() + (2L * Integer.MAX_VALUE));

    public static SiteSetting getSiteSettingSample1() {
        return new SiteSetting()
            .id(1L)
            .brandName("brandName1")
            .careLabel("careLabel1")
            .contactEmail("contactEmail1")
            .contactPhone("contactPhone1")
            .ecatalogueLabel("ecatalogueLabel1")
            .ecatalogueUrl("ecatalogueUrl1")
            .facebookUrl("facebookUrl1")
            .footerCopyright("footerCopyright1")
            .footerTagline("footerTagline1")
            .instagramUrl("instagramUrl1")
            .legalEntityName("legalEntityName1")
            .logoHorizontalUrl("logoHorizontalUrl1")
            .logoUrl("logoUrl1")
            .twitterUrl("twitterUrl1")
            .whatsappNumber("whatsappNumber1")
            .youtubeUrl("youtubeUrl1");
    }

    public static SiteSetting getSiteSettingSample2() {
        return new SiteSetting()
            .id(2L)
            .brandName("brandName2")
            .careLabel("careLabel2")
            .contactEmail("contactEmail2")
            .contactPhone("contactPhone2")
            .ecatalogueLabel("ecatalogueLabel2")
            .ecatalogueUrl("ecatalogueUrl2")
            .facebookUrl("facebookUrl2")
            .footerCopyright("footerCopyright2")
            .footerTagline("footerTagline2")
            .instagramUrl("instagramUrl2")
            .legalEntityName("legalEntityName2")
            .logoHorizontalUrl("logoHorizontalUrl2")
            .logoUrl("logoUrl2")
            .twitterUrl("twitterUrl2")
            .whatsappNumber("whatsappNumber2")
            .youtubeUrl("youtubeUrl2");
    }

    public static SiteSetting getSiteSettingRandomSampleGenerator() {
        return new SiteSetting()
            .id(longCount.incrementAndGet())
            .brandName(UUID.randomUUID().toString())
            .careLabel(UUID.randomUUID().toString())
            .contactEmail(UUID.randomUUID().toString())
            .contactPhone(UUID.randomUUID().toString())
            .ecatalogueLabel(UUID.randomUUID().toString())
            .ecatalogueUrl(UUID.randomUUID().toString())
            .facebookUrl(UUID.randomUUID().toString())
            .footerCopyright(UUID.randomUUID().toString())
            .footerTagline(UUID.randomUUID().toString())
            .instagramUrl(UUID.randomUUID().toString())
            .legalEntityName(UUID.randomUUID().toString())
            .logoHorizontalUrl(UUID.randomUUID().toString())
            .logoUrl(UUID.randomUUID().toString())
            .twitterUrl(UUID.randomUUID().toString())
            .whatsappNumber(UUID.randomUUID().toString())
            .youtubeUrl(UUID.randomUUID().toString());
    }
}
