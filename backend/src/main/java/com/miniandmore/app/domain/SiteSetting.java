package com.miniandmore.app.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * A SiteSetting.
 */
@Entity
@Table(name = "site_setting")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class SiteSetting implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    @Column(name = "id")
    private Long id;

    @NotNull
    @Size(min = 1, max = 160)
    @Column(name = "brand_name", length = 160, nullable = false)
    private String brandName;

    @Size(max = 180)
    @Column(name = "care_label", length = 180)
    private String careLabel;

    @Size(max = 180)
    @Column(name = "contact_email", length = 180)
    private String contactEmail;

    @Size(max = 40)
    @Column(name = "contact_phone", length = 40)
    private String contactPhone;

    @Size(max = 120)
    @Column(name = "ecatalogue_label", length = 120)
    private String ecatalogueLabel;

    @Size(max = 600)
    @Column(name = "ecatalogue_url", length = 600)
    private String ecatalogueUrl;

    @Size(max = 600)
    @Column(name = "facebook_url", length = 600)
    private String facebookUrl;

    @Size(max = 260)
    @Column(name = "footer_copyright", length = 260)
    private String footerCopyright;

    @Size(max = 260)
    @Column(name = "footer_tagline", length = 260)
    private String footerTagline;

    @Size(max = 600)
    @Column(name = "instagram_url", length = 600)
    private String instagramUrl;

    @Size(max = 180)
    @Column(name = "legal_entity_name", length = 180)
    private String legalEntityName;

    @Size(max = 600)
    @Column(name = "logo_horizontal_url", length = 600)
    private String logoHorizontalUrl;

    @Size(max = 600)
    @Column(name = "logo_url", length = 600)
    private String logoUrl;

    @Size(max = 600)
    @Column(name = "invoice_logo_url", length = 600)
    private String invoiceLogoUrl;

    @Size(max = 240)
    @Column(name = "invoice_website", length = 240)
    private String invoiceWebsite;

    @Size(max = 40)
    @Column(name = "invoice_gstin", length = 40)
    private String invoiceGstin;

    @Size(max = 1000)
    @Column(name = "invoice_sold_by_address", length = 1000)
    private String invoiceSoldByAddress;

    @Size(max = 1000)
    @Column(name = "invoice_dispatch_from_address", length = 1000)
    private String invoiceDispatchFromAddress;

    @Size(max = 1000)
    @Column(name = "invoice_registered_office_address", length = 1000)
    private String invoiceRegisteredOfficeAddress;

    @Size(max = 40)
    @Column(name = "invoice_cin", length = 40)
    private String invoiceCin;

    @Size(max = 600)
    @Column(name = "invoice_qr_url", length = 600)
    private String invoiceQrUrl;

    @Size(max = 500)
    @Column(name = "invoice_footer_note", length = 500)
    private String invoiceFooterNote;

    @Size(max = 100)
    @Column(name = "invoice_seller_state", length = 100)
    private String invoiceSellerState;

    @Size(max = 600)
    @Column(name = "twitter_url", length = 600)
    private String twitterUrl;

    @Size(max = 60)
    @Column(name = "whatsapp_number", length = 60)
    private String whatsappNumber;

    @Size(max = 600)
    @Column(name = "youtube_url", length = 600)
    private String youtubeUrl;

    @NotNull
    @DecimalMin(value = "0")
    @DecimalMax(value = "100")
    @Digits(integer = 3, fraction = 2)
    @Column(name = "gst_rate")
    private BigDecimal gstRate;

    @NotNull
    @Column(name = "prices_include_gst")
    private Boolean pricesIncludeGst;

    @NotNull
    @Min(0)
    @Column(name = "shipping_standard_inr")
    private Integer shippingStandardInr;

    @NotNull
    @Min(0)
    @Column(name = "shipping_express_inr")
    private Integer shippingExpressInr;

    @NotNull
    @Min(0)
    @Column(name = "free_shipping_min_inr")
    private Integer freeShippingMinInr;

    @NotNull
    @Min(0)
    @Max(10000)
    @Column(name = "cod_fee_inr")
    private Integer codFeeInr;

    public static final int DEFAULT_DELIVERY_ETA_BUFFER_DAYS = 3;
    public static final int MAX_DELIVERY_ETA_BUFFER_DAYS = 30;

    @NotNull
    @Min(0)
    @Max(MAX_DELIVERY_ETA_BUFFER_DAYS)
    @Column(name = "delivery_eta_buffer_days")
    private Integer deliveryEtaBufferDays;

    @NotNull
    @DecimalMin(value = "0")
    @DecimalMax(value = "100")
    @Digits(integer = 3, fraction = 2)
    @Column(name = "store_discount_percent")
    private BigDecimal storeDiscountPercent;

    @Size(max = 260)
    @Column(name = "subscribe_hero_title", length = 260)
    private String subscribeHeroTitle;

    @Size(max = 600)
    @Column(name = "subscribe_hero_subtitle", length = 600)
    private String subscribeHeroSubtitle;

    @Size(max = 600)
    @Column(name = "subscribe_testimonial", length = 600)
    private String subscribeTestimonial;

    @Column(name = "subscribe_badges_json", columnDefinition = "text")
    private String subscribeBadgesJson;

    @Column(name = "subscribe_plans_json", columnDefinition = "text")
    private String subscribePlansJson;

    @Column(name = "subscribe_features_json", columnDefinition = "text")
    private String subscribeFeaturesJson;

    @Column(name = "about_page_json", columnDefinition = "text")
    private String aboutPageJson;

    @Column(name = "gift_page_json", columnDefinition = "text")
    private String giftPageJson;

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public Long getId() {
        return this.id;
    }

    public SiteSetting id(Long id) {
        this.setId(id);
        return this;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getBrandName() {
        return this.brandName;
    }

    public SiteSetting brandName(String brandName) {
        this.setBrandName(brandName);
        return this;
    }

    public void setBrandName(String brandName) {
        this.brandName = brandName;
    }

    public String getCareLabel() {
        return this.careLabel;
    }

    public SiteSetting careLabel(String careLabel) {
        this.setCareLabel(careLabel);
        return this;
    }

    public void setCareLabel(String careLabel) {
        this.careLabel = careLabel;
    }

    public String getContactEmail() {
        return this.contactEmail;
    }

    public SiteSetting contactEmail(String contactEmail) {
        this.setContactEmail(contactEmail);
        return this;
    }

    public void setContactEmail(String contactEmail) {
        this.contactEmail = contactEmail;
    }

    public String getContactPhone() {
        return this.contactPhone;
    }

    public SiteSetting contactPhone(String contactPhone) {
        this.setContactPhone(contactPhone);
        return this;
    }

    public void setContactPhone(String contactPhone) {
        this.contactPhone = contactPhone;
    }

    public String getEcatalogueLabel() {
        return this.ecatalogueLabel;
    }

    public SiteSetting ecatalogueLabel(String ecatalogueLabel) {
        this.setEcatalogueLabel(ecatalogueLabel);
        return this;
    }

    public void setEcatalogueLabel(String ecatalogueLabel) {
        this.ecatalogueLabel = ecatalogueLabel;
    }

    public String getEcatalogueUrl() {
        return this.ecatalogueUrl;
    }

    public SiteSetting ecatalogueUrl(String ecatalogueUrl) {
        this.setEcatalogueUrl(ecatalogueUrl);
        return this;
    }

    public void setEcatalogueUrl(String ecatalogueUrl) {
        this.ecatalogueUrl = ecatalogueUrl;
    }

    public String getFacebookUrl() {
        return this.facebookUrl;
    }

    public SiteSetting facebookUrl(String facebookUrl) {
        this.setFacebookUrl(facebookUrl);
        return this;
    }

    public void setFacebookUrl(String facebookUrl) {
        this.facebookUrl = facebookUrl;
    }

    public String getFooterCopyright() {
        return this.footerCopyright;
    }

    public SiteSetting footerCopyright(String footerCopyright) {
        this.setFooterCopyright(footerCopyright);
        return this;
    }

    public void setFooterCopyright(String footerCopyright) {
        this.footerCopyright = footerCopyright;
    }

    public String getFooterTagline() {
        return this.footerTagline;
    }

    public SiteSetting footerTagline(String footerTagline) {
        this.setFooterTagline(footerTagline);
        return this;
    }

    public void setFooterTagline(String footerTagline) {
        this.footerTagline = footerTagline;
    }

    public String getInstagramUrl() {
        return this.instagramUrl;
    }

    public SiteSetting instagramUrl(String instagramUrl) {
        this.setInstagramUrl(instagramUrl);
        return this;
    }

    public void setInstagramUrl(String instagramUrl) {
        this.instagramUrl = instagramUrl;
    }

    public String getLegalEntityName() {
        return this.legalEntityName;
    }

    public SiteSetting legalEntityName(String legalEntityName) {
        this.setLegalEntityName(legalEntityName);
        return this;
    }

    public void setLegalEntityName(String legalEntityName) {
        this.legalEntityName = legalEntityName;
    }

    public String getLogoHorizontalUrl() {
        return this.logoHorizontalUrl;
    }

    public SiteSetting logoHorizontalUrl(String logoHorizontalUrl) {
        this.setLogoHorizontalUrl(logoHorizontalUrl);
        return this;
    }

    public void setLogoHorizontalUrl(String logoHorizontalUrl) {
        this.logoHorizontalUrl = logoHorizontalUrl;
    }

    public String getLogoUrl() {
        return this.logoUrl;
    }

    public SiteSetting logoUrl(String logoUrl) {
        this.setLogoUrl(logoUrl);
        return this;
    }

    public void setLogoUrl(String logoUrl) {
        this.logoUrl = logoUrl;
    }

    public String getInvoiceLogoUrl() {
        return this.invoiceLogoUrl;
    }

    public SiteSetting invoiceLogoUrl(String invoiceLogoUrl) {
        this.setInvoiceLogoUrl(invoiceLogoUrl);
        return this;
    }

    public void setInvoiceLogoUrl(String invoiceLogoUrl) {
        this.invoiceLogoUrl = invoiceLogoUrl;
    }

    public String getInvoiceWebsite() {
        return this.invoiceWebsite;
    }

    public SiteSetting invoiceWebsite(String invoiceWebsite) {
        this.setInvoiceWebsite(invoiceWebsite);
        return this;
    }

    public void setInvoiceWebsite(String invoiceWebsite) {
        this.invoiceWebsite = invoiceWebsite;
    }

    public String getInvoiceGstin() {
        return this.invoiceGstin;
    }

    public SiteSetting invoiceGstin(String invoiceGstin) {
        this.setInvoiceGstin(invoiceGstin);
        return this;
    }

    public void setInvoiceGstin(String invoiceGstin) {
        this.invoiceGstin = invoiceGstin;
    }

    public String getInvoiceSoldByAddress() {
        return this.invoiceSoldByAddress;
    }

    public SiteSetting invoiceSoldByAddress(String invoiceSoldByAddress) {
        this.setInvoiceSoldByAddress(invoiceSoldByAddress);
        return this;
    }

    public void setInvoiceSoldByAddress(String invoiceSoldByAddress) {
        this.invoiceSoldByAddress = invoiceSoldByAddress;
    }

    public String getInvoiceDispatchFromAddress() {
        return this.invoiceDispatchFromAddress;
    }

    public SiteSetting invoiceDispatchFromAddress(String invoiceDispatchFromAddress) {
        this.setInvoiceDispatchFromAddress(invoiceDispatchFromAddress);
        return this;
    }

    public void setInvoiceDispatchFromAddress(String invoiceDispatchFromAddress) {
        this.invoiceDispatchFromAddress = invoiceDispatchFromAddress;
    }

    public String getInvoiceRegisteredOfficeAddress() {
        return this.invoiceRegisteredOfficeAddress;
    }

    public SiteSetting invoiceRegisteredOfficeAddress(String invoiceRegisteredOfficeAddress) {
        this.setInvoiceRegisteredOfficeAddress(invoiceRegisteredOfficeAddress);
        return this;
    }

    public void setInvoiceRegisteredOfficeAddress(String invoiceRegisteredOfficeAddress) {
        this.invoiceRegisteredOfficeAddress = invoiceRegisteredOfficeAddress;
    }

    public String getInvoiceCin() {
        return this.invoiceCin;
    }

    public SiteSetting invoiceCin(String invoiceCin) {
        this.setInvoiceCin(invoiceCin);
        return this;
    }

    public void setInvoiceCin(String invoiceCin) {
        this.invoiceCin = invoiceCin;
    }

    public String getInvoiceQrUrl() {
        return this.invoiceQrUrl;
    }

    public SiteSetting invoiceQrUrl(String invoiceQrUrl) {
        this.setInvoiceQrUrl(invoiceQrUrl);
        return this;
    }

    public void setInvoiceQrUrl(String invoiceQrUrl) {
        this.invoiceQrUrl = invoiceQrUrl;
    }

    public String getInvoiceFooterNote() {
        return this.invoiceFooterNote;
    }

    public SiteSetting invoiceFooterNote(String invoiceFooterNote) {
        this.setInvoiceFooterNote(invoiceFooterNote);
        return this;
    }

    public void setInvoiceFooterNote(String invoiceFooterNote) {
        this.invoiceFooterNote = invoiceFooterNote;
    }

    public String getInvoiceSellerState() {
        return this.invoiceSellerState;
    }

    public SiteSetting invoiceSellerState(String invoiceSellerState) {
        this.setInvoiceSellerState(invoiceSellerState);
        return this;
    }

    public void setInvoiceSellerState(String invoiceSellerState) {
        this.invoiceSellerState = invoiceSellerState;
    }

    public String getTwitterUrl() {
        return this.twitterUrl;
    }

    public SiteSetting twitterUrl(String twitterUrl) {
        this.setTwitterUrl(twitterUrl);
        return this;
    }

    public void setTwitterUrl(String twitterUrl) {
        this.twitterUrl = twitterUrl;
    }

    public String getWhatsappNumber() {
        return this.whatsappNumber;
    }

    public SiteSetting whatsappNumber(String whatsappNumber) {
        this.setWhatsappNumber(whatsappNumber);
        return this;
    }

    public void setWhatsappNumber(String whatsappNumber) {
        this.whatsappNumber = whatsappNumber;
    }

    public String getYoutubeUrl() {
        return this.youtubeUrl;
    }

    public SiteSetting youtubeUrl(String youtubeUrl) {
        this.setYoutubeUrl(youtubeUrl);
        return this;
    }

    public void setYoutubeUrl(String youtubeUrl) {
        this.youtubeUrl = youtubeUrl;
    }

    public BigDecimal getGstRate() {
        return this.gstRate;
    }

    public SiteSetting gstRate(BigDecimal gstRate) {
        this.setGstRate(gstRate);
        return this;
    }

    public void setGstRate(BigDecimal gstRate) {
        this.gstRate = gstRate;
    }

    public Boolean getPricesIncludeGst() {
        return this.pricesIncludeGst;
    }

    public SiteSetting pricesIncludeGst(Boolean pricesIncludeGst) {
        this.setPricesIncludeGst(pricesIncludeGst);
        return this;
    }

    public void setPricesIncludeGst(Boolean pricesIncludeGst) {
        this.pricesIncludeGst = pricesIncludeGst;
    }

    public Integer getShippingStandardInr() {
        return this.shippingStandardInr;
    }

    public SiteSetting shippingStandardInr(Integer shippingStandardInr) {
        this.setShippingStandardInr(shippingStandardInr);
        return this;
    }

    public void setShippingStandardInr(Integer shippingStandardInr) {
        this.shippingStandardInr = shippingStandardInr;
    }

    public Integer getShippingExpressInr() {
        return this.shippingExpressInr;
    }

    public SiteSetting shippingExpressInr(Integer shippingExpressInr) {
        this.setShippingExpressInr(shippingExpressInr);
        return this;
    }

    public void setShippingExpressInr(Integer shippingExpressInr) {
        this.shippingExpressInr = shippingExpressInr;
    }

    public Integer getFreeShippingMinInr() {
        return this.freeShippingMinInr;
    }

    public SiteSetting freeShippingMinInr(Integer freeShippingMinInr) {
        this.setFreeShippingMinInr(freeShippingMinInr);
        return this;
    }

    public void setFreeShippingMinInr(Integer freeShippingMinInr) {
        this.freeShippingMinInr = freeShippingMinInr;
    }

    public Integer getCodFeeInr() {
        return this.codFeeInr;
    }

    public SiteSetting codFeeInr(Integer codFeeInr) {
        this.setCodFeeInr(codFeeInr);
        return this;
    }

    public void setCodFeeInr(Integer codFeeInr) {
        this.codFeeInr = codFeeInr;
    }

    public Integer getDeliveryEtaBufferDays() {
        return this.deliveryEtaBufferDays;
    }

    public SiteSetting deliveryEtaBufferDays(Integer deliveryEtaBufferDays) {
        this.setDeliveryEtaBufferDays(deliveryEtaBufferDays);
        return this;
    }

    public void setDeliveryEtaBufferDays(Integer deliveryEtaBufferDays) {
        this.deliveryEtaBufferDays = deliveryEtaBufferDays;
    }

    public BigDecimal getStoreDiscountPercent() {
        return this.storeDiscountPercent;
    }

    public SiteSetting storeDiscountPercent(BigDecimal storeDiscountPercent) {
        this.setStoreDiscountPercent(storeDiscountPercent);
        return this;
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

    public String getGiftPageJson() {
        return giftPageJson;
    }

    public void setGiftPageJson(String giftPageJson) {
        this.giftPageJson = giftPageJson;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SiteSetting)) {
            return false;
        }
        return getId() != null && getId().equals(((SiteSetting) o).getId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "SiteSetting{" +
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
