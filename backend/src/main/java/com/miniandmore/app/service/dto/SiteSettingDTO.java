package com.miniandmore.app.service.dto;

import com.miniandmore.app.domain.SiteSetting;
import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * A DTO for the {@link com.miniandmore.app.domain.SiteSetting} entity.
 */
@SuppressWarnings("common-java:DuplicatedBlocks")
public class SiteSettingDTO implements Serializable {

    private Long id;

    @NotNull
    @Size(min = 1, max = 160)
    private String brandName;

    @Size(max = 180)
    private String careLabel;

    @Size(max = 180)
    private String contactEmail;

    @Size(max = 40)
    private String contactPhone;

    @Size(max = 120)
    private String ecatalogueLabel;

    @Size(max = 600)
    private String ecatalogueUrl;

    @Size(max = 600)
    private String facebookUrl;

    @Size(max = 260)
    private String footerCopyright;

    @Size(max = 260)
    private String footerTagline;

    @Size(max = 600)
    private String instagramUrl;

    @Size(max = 180)
    private String legalEntityName;

    @Size(max = 600)
    private String logoHorizontalUrl;

    @Size(max = 600)
    private String logoUrl;

    @Size(max = 600)
    private String invoiceLogoUrl;

    @Size(max = 240)
    private String invoiceWebsite;

    @Size(max = 40)
    private String invoiceGstin;

    @Size(max = 1000)
    private String invoiceSoldByAddress;

    @Size(max = 1000)
    private String invoiceDispatchFromAddress;

    @Size(max = 1000)
    private String invoiceRegisteredOfficeAddress;

    @Size(max = 40)
    private String invoiceCin;

    @Size(max = 600)
    private String invoiceQrUrl;

    @Size(max = 500)
    private String invoiceFooterNote;

    @Size(max = 100)
    private String invoiceSellerState;

    @Size(max = 600)
    private String twitterUrl;

    @Size(max = 60)
    private String whatsappNumber;

    @Size(max = 600)
    private String youtubeUrl;

    @DecimalMin(value = "0")
    @DecimalMax(value = "100")
    @Digits(integer = 3, fraction = 2)
    private BigDecimal gstRate;

    private Boolean pricesIncludeGst;

    @Min(0)
    private Integer shippingStandardInr;

    @Min(0)
    private Integer shippingExpressInr;

    @Min(0)
    private Integer freeShippingMinInr;

    @Min(0)
    @Max(10000)
    private Integer codFeeInr;

    @Min(0)
    @Max(SiteSetting.MAX_DELIVERY_ETA_BUFFER_DAYS)
    private Integer deliveryEtaBufferDays;

    @NotNull
    @DecimalMin(value = "0")
    @DecimalMax(value = "100")
    @Digits(integer = 3, fraction = 2)
    private BigDecimal storeDiscountPercent;

    @Size(max = 260)
    private String subscribeHeroTitle;

    @Size(max = 600)
    private String subscribeHeroSubtitle;

    @Size(max = 600)
    private String subscribeTestimonial;

    private String subscribeBadgesJson;

    private String subscribePlansJson;

    private String subscribeFeaturesJson;

    private String aboutPageJson;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getBrandName() {
        return brandName;
    }

    public void setBrandName(String brandName) {
        this.brandName = brandName;
    }

    public String getCareLabel() {
        return careLabel;
    }

    public void setCareLabel(String careLabel) {
        this.careLabel = careLabel;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public void setContactEmail(String contactEmail) {
        this.contactEmail = contactEmail;
    }

    public String getContactPhone() {
        return contactPhone;
    }

    public void setContactPhone(String contactPhone) {
        this.contactPhone = contactPhone;
    }

    public String getEcatalogueLabel() {
        return ecatalogueLabel;
    }

    public void setEcatalogueLabel(String ecatalogueLabel) {
        this.ecatalogueLabel = ecatalogueLabel;
    }

    public String getEcatalogueUrl() {
        return ecatalogueUrl;
    }

    public void setEcatalogueUrl(String ecatalogueUrl) {
        this.ecatalogueUrl = ecatalogueUrl;
    }

    public String getFacebookUrl() {
        return facebookUrl;
    }

    public void setFacebookUrl(String facebookUrl) {
        this.facebookUrl = facebookUrl;
    }

    public String getFooterCopyright() {
        return footerCopyright;
    }

    public void setFooterCopyright(String footerCopyright) {
        this.footerCopyright = footerCopyright;
    }

    public String getFooterTagline() {
        return footerTagline;
    }

    public void setFooterTagline(String footerTagline) {
        this.footerTagline = footerTagline;
    }

    public String getInstagramUrl() {
        return instagramUrl;
    }

    public void setInstagramUrl(String instagramUrl) {
        this.instagramUrl = instagramUrl;
    }

    public String getLegalEntityName() {
        return legalEntityName;
    }

    public void setLegalEntityName(String legalEntityName) {
        this.legalEntityName = legalEntityName;
    }

    public String getLogoHorizontalUrl() {
        return logoHorizontalUrl;
    }

    public void setLogoHorizontalUrl(String logoHorizontalUrl) {
        this.logoHorizontalUrl = logoHorizontalUrl;
    }

    public String getLogoUrl() {
        return logoUrl;
    }

    public void setLogoUrl(String logoUrl) {
        this.logoUrl = logoUrl;
    }

    public String getInvoiceLogoUrl() {
        return invoiceLogoUrl;
    }

    public void setInvoiceLogoUrl(String invoiceLogoUrl) {
        this.invoiceLogoUrl = invoiceLogoUrl;
    }

    public String getInvoiceWebsite() {
        return invoiceWebsite;
    }

    public void setInvoiceWebsite(String invoiceWebsite) {
        this.invoiceWebsite = invoiceWebsite;
    }

    public String getInvoiceGstin() {
        return invoiceGstin;
    }

    public void setInvoiceGstin(String invoiceGstin) {
        this.invoiceGstin = invoiceGstin;
    }

    public String getInvoiceSoldByAddress() {
        return invoiceSoldByAddress;
    }

    public void setInvoiceSoldByAddress(String invoiceSoldByAddress) {
        this.invoiceSoldByAddress = invoiceSoldByAddress;
    }

    public String getInvoiceDispatchFromAddress() {
        return invoiceDispatchFromAddress;
    }

    public void setInvoiceDispatchFromAddress(String invoiceDispatchFromAddress) {
        this.invoiceDispatchFromAddress = invoiceDispatchFromAddress;
    }

    public String getInvoiceRegisteredOfficeAddress() {
        return invoiceRegisteredOfficeAddress;
    }

    public void setInvoiceRegisteredOfficeAddress(String invoiceRegisteredOfficeAddress) {
        this.invoiceRegisteredOfficeAddress = invoiceRegisteredOfficeAddress;
    }

    public String getInvoiceCin() {
        return invoiceCin;
    }

    public void setInvoiceCin(String invoiceCin) {
        this.invoiceCin = invoiceCin;
    }

    public String getInvoiceQrUrl() {
        return invoiceQrUrl;
    }

    public void setInvoiceQrUrl(String invoiceQrUrl) {
        this.invoiceQrUrl = invoiceQrUrl;
    }

    public String getInvoiceFooterNote() {
        return invoiceFooterNote;
    }

    public void setInvoiceFooterNote(String invoiceFooterNote) {
        this.invoiceFooterNote = invoiceFooterNote;
    }

    public String getInvoiceSellerState() {
        return invoiceSellerState;
    }

    public void setInvoiceSellerState(String invoiceSellerState) {
        this.invoiceSellerState = invoiceSellerState;
    }

    public String getTwitterUrl() {
        return twitterUrl;
    }

    public void setTwitterUrl(String twitterUrl) {
        this.twitterUrl = twitterUrl;
    }

    public String getWhatsappNumber() {
        return whatsappNumber;
    }

    public void setWhatsappNumber(String whatsappNumber) {
        this.whatsappNumber = whatsappNumber;
    }

    public String getYoutubeUrl() {
        return youtubeUrl;
    }

    public void setYoutubeUrl(String youtubeUrl) {
        this.youtubeUrl = youtubeUrl;
    }

    public BigDecimal getGstRate() {
        return gstRate;
    }

    public void setGstRate(BigDecimal gstRate) {
        this.gstRate = gstRate;
    }

    public Boolean getPricesIncludeGst() {
        return pricesIncludeGst;
    }

    public void setPricesIncludeGst(Boolean pricesIncludeGst) {
        this.pricesIncludeGst = pricesIncludeGst;
    }

    public Integer getShippingStandardInr() {
        return shippingStandardInr;
    }

    public void setShippingStandardInr(Integer shippingStandardInr) {
        this.shippingStandardInr = shippingStandardInr;
    }

    public Integer getShippingExpressInr() {
        return shippingExpressInr;
    }

    public void setShippingExpressInr(Integer shippingExpressInr) {
        this.shippingExpressInr = shippingExpressInr;
    }

    public Integer getFreeShippingMinInr() {
        return freeShippingMinInr;
    }

    public void setFreeShippingMinInr(Integer freeShippingMinInr) {
        this.freeShippingMinInr = freeShippingMinInr;
    }

    public Integer getCodFeeInr() {
        return codFeeInr;
    }

    public void setCodFeeInr(Integer codFeeInr) {
        this.codFeeInr = codFeeInr;
    }

    public Integer getDeliveryEtaBufferDays() {
        return deliveryEtaBufferDays;
    }

    public void setDeliveryEtaBufferDays(Integer deliveryEtaBufferDays) {
        this.deliveryEtaBufferDays = deliveryEtaBufferDays;
    }

    public BigDecimal getStoreDiscountPercent() {
        return storeDiscountPercent;
    }

    public void setStoreDiscountPercent(BigDecimal storeDiscountPercent) {
        this.storeDiscountPercent = storeDiscountPercent;
    }

    public String getSubscribeHeroTitle() {
        return subscribeHeroTitle;
    }

    public void setSubscribeHeroTitle(String subscribeHeroTitle) {
        this.subscribeHeroTitle = subscribeHeroTitle;
    }

    public String getSubscribeHeroSubtitle() {
        return subscribeHeroSubtitle;
    }

    public void setSubscribeHeroSubtitle(String subscribeHeroSubtitle) {
        this.subscribeHeroSubtitle = subscribeHeroSubtitle;
    }

    public String getSubscribeTestimonial() {
        return subscribeTestimonial;
    }

    public void setSubscribeTestimonial(String subscribeTestimonial) {
        this.subscribeTestimonial = subscribeTestimonial;
    }

    public String getSubscribeBadgesJson() {
        return subscribeBadgesJson;
    }

    public void setSubscribeBadgesJson(String subscribeBadgesJson) {
        this.subscribeBadgesJson = subscribeBadgesJson;
    }

    public String getSubscribePlansJson() {
        return subscribePlansJson;
    }

    public void setSubscribePlansJson(String subscribePlansJson) {
        this.subscribePlansJson = subscribePlansJson;
    }

    public String getSubscribeFeaturesJson() {
        return subscribeFeaturesJson;
    }

    public void setSubscribeFeaturesJson(String subscribeFeaturesJson) {
        this.subscribeFeaturesJson = subscribeFeaturesJson;
    }

    public String getAboutPageJson() {
        return aboutPageJson;
    }

    public void setAboutPageJson(String aboutPageJson) {
        this.aboutPageJson = aboutPageJson;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SiteSettingDTO)) {
            return false;
        }

        SiteSettingDTO siteSettingDTO = (SiteSettingDTO) o;
        if (this.id == null) {
            return false;
        }
        return Objects.equals(this.id, siteSettingDTO.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "SiteSettingDTO{" +
            "id=" + getId() +
            ", brandName='" + getBrandName() + "'" +
            ", careLabel='" + getCareLabel() + "'" +
            ", contactEmail='" + getContactEmail() + "'" +
            ", contactPhone='" + getContactPhone() + "'" +
            ", ecatalogueLabel='" + getEcatalogueLabel() + "'" +
            ", ecatalogueUrl='" + getEcatalogueUrl() + "'" +
            ", facebookUrl='" + getFacebookUrl() + "'" +
            ", footerCopyright='" + getFooterCopyright() + "'" +
            ", footerTagline='" + getFooterTagline() + "'" +
            ", instagramUrl='" + getInstagramUrl() + "'" +
            ", legalEntityName='" + getLegalEntityName() + "'" +
            ", logoHorizontalUrl='" + getLogoHorizontalUrl() + "'" +
            ", logoUrl='" + getLogoUrl() + "'" +
            ", invoiceLogoUrl='" + getInvoiceLogoUrl() + "'" +
            ", twitterUrl='" + getTwitterUrl() + "'" +
            ", whatsappNumber='" + getWhatsappNumber() + "'" +
            ", youtubeUrl='" + getYoutubeUrl() + "'" +
            "}";
    }
}
