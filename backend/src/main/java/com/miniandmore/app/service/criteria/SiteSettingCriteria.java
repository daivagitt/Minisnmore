package com.miniandmore.app.service.criteria;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import java.util.Optional;
import org.springdoc.core.annotations.ParameterObject;
import tech.jhipster.service.Criteria;
import tech.jhipster.service.filter.*;

/**
 * Criteria class for the {@link com.miniandmore.app.domain.SiteSetting} entity. This class is used
 * in {@link com.miniandmore.app.web.rest.SiteSettingResource} to receive all the possible filtering options from
 * the Http GET request parameters.
 * For example the following could be a valid request:
 * {@code /site-settings?id.greaterThan=5&attr1.contains=something&attr2.specified=false}
 * As Spring is unable to properly convert the types, unless specific {@link Filter} class are used, we need to use
 * fix type specific filters.
 */
@ParameterObject
@SuppressWarnings("common-java:DuplicatedBlocks")
public class SiteSettingCriteria implements Serializable, Criteria {

    @Serial
    private static final long serialVersionUID = 1L;

    private LongFilter id;

    private StringFilter brandName;

    private StringFilter careLabel;

    private StringFilter contactEmail;

    private StringFilter contactPhone;

    private StringFilter ecatalogueLabel;

    private StringFilter ecatalogueUrl;

    private StringFilter facebookUrl;

    private StringFilter footerCopyright;

    private StringFilter footerTagline;

    private StringFilter instagramUrl;

    private StringFilter legalEntityName;

    private StringFilter logoHorizontalUrl;

    private StringFilter logoUrl;

    private StringFilter twitterUrl;

    private StringFilter whatsappNumber;

    private StringFilter youtubeUrl;

    private Boolean distinct;

    public SiteSettingCriteria() {}

    public SiteSettingCriteria(SiteSettingCriteria other) {
        this.id = other.optionalId().map(LongFilter::copy).orElse(null);
        this.brandName = other.optionalBrandName().map(StringFilter::copy).orElse(null);
        this.careLabel = other.optionalCareLabel().map(StringFilter::copy).orElse(null);
        this.contactEmail = other.optionalContactEmail().map(StringFilter::copy).orElse(null);
        this.contactPhone = other.optionalContactPhone().map(StringFilter::copy).orElse(null);
        this.ecatalogueLabel = other.optionalEcatalogueLabel().map(StringFilter::copy).orElse(null);
        this.ecatalogueUrl = other.optionalEcatalogueUrl().map(StringFilter::copy).orElse(null);
        this.facebookUrl = other.optionalFacebookUrl().map(StringFilter::copy).orElse(null);
        this.footerCopyright = other.optionalFooterCopyright().map(StringFilter::copy).orElse(null);
        this.footerTagline = other.optionalFooterTagline().map(StringFilter::copy).orElse(null);
        this.instagramUrl = other.optionalInstagramUrl().map(StringFilter::copy).orElse(null);
        this.legalEntityName = other.optionalLegalEntityName().map(StringFilter::copy).orElse(null);
        this.logoHorizontalUrl = other.optionalLogoHorizontalUrl().map(StringFilter::copy).orElse(null);
        this.logoUrl = other.optionalLogoUrl().map(StringFilter::copy).orElse(null);
        this.twitterUrl = other.optionalTwitterUrl().map(StringFilter::copy).orElse(null);
        this.whatsappNumber = other.optionalWhatsappNumber().map(StringFilter::copy).orElse(null);
        this.youtubeUrl = other.optionalYoutubeUrl().map(StringFilter::copy).orElse(null);
        this.distinct = other.distinct;
    }

    @Override
    public SiteSettingCriteria copy() {
        return new SiteSettingCriteria(this);
    }

    public LongFilter getId() {
        return id;
    }

    public Optional<LongFilter> optionalId() {
        return Optional.ofNullable(id);
    }

    public LongFilter id() {
        if (id == null) {
            setId(new LongFilter());
        }
        return id;
    }

    public void setId(LongFilter id) {
        this.id = id;
    }

    public StringFilter getBrandName() {
        return brandName;
    }

    public Optional<StringFilter> optionalBrandName() {
        return Optional.ofNullable(brandName);
    }

    public StringFilter brandName() {
        if (brandName == null) {
            setBrandName(new StringFilter());
        }
        return brandName;
    }

    public void setBrandName(StringFilter brandName) {
        this.brandName = brandName;
    }

    public StringFilter getCareLabel() {
        return careLabel;
    }

    public Optional<StringFilter> optionalCareLabel() {
        return Optional.ofNullable(careLabel);
    }

    public StringFilter careLabel() {
        if (careLabel == null) {
            setCareLabel(new StringFilter());
        }
        return careLabel;
    }

    public void setCareLabel(StringFilter careLabel) {
        this.careLabel = careLabel;
    }

    public StringFilter getContactEmail() {
        return contactEmail;
    }

    public Optional<StringFilter> optionalContactEmail() {
        return Optional.ofNullable(contactEmail);
    }

    public StringFilter contactEmail() {
        if (contactEmail == null) {
            setContactEmail(new StringFilter());
        }
        return contactEmail;
    }

    public void setContactEmail(StringFilter contactEmail) {
        this.contactEmail = contactEmail;
    }

    public StringFilter getContactPhone() {
        return contactPhone;
    }

    public Optional<StringFilter> optionalContactPhone() {
        return Optional.ofNullable(contactPhone);
    }

    public StringFilter contactPhone() {
        if (contactPhone == null) {
            setContactPhone(new StringFilter());
        }
        return contactPhone;
    }

    public void setContactPhone(StringFilter contactPhone) {
        this.contactPhone = contactPhone;
    }

    public StringFilter getEcatalogueLabel() {
        return ecatalogueLabel;
    }

    public Optional<StringFilter> optionalEcatalogueLabel() {
        return Optional.ofNullable(ecatalogueLabel);
    }

    public StringFilter ecatalogueLabel() {
        if (ecatalogueLabel == null) {
            setEcatalogueLabel(new StringFilter());
        }
        return ecatalogueLabel;
    }

    public void setEcatalogueLabel(StringFilter ecatalogueLabel) {
        this.ecatalogueLabel = ecatalogueLabel;
    }

    public StringFilter getEcatalogueUrl() {
        return ecatalogueUrl;
    }

    public Optional<StringFilter> optionalEcatalogueUrl() {
        return Optional.ofNullable(ecatalogueUrl);
    }

    public StringFilter ecatalogueUrl() {
        if (ecatalogueUrl == null) {
            setEcatalogueUrl(new StringFilter());
        }
        return ecatalogueUrl;
    }

    public void setEcatalogueUrl(StringFilter ecatalogueUrl) {
        this.ecatalogueUrl = ecatalogueUrl;
    }

    public StringFilter getFacebookUrl() {
        return facebookUrl;
    }

    public Optional<StringFilter> optionalFacebookUrl() {
        return Optional.ofNullable(facebookUrl);
    }

    public StringFilter facebookUrl() {
        if (facebookUrl == null) {
            setFacebookUrl(new StringFilter());
        }
        return facebookUrl;
    }

    public void setFacebookUrl(StringFilter facebookUrl) {
        this.facebookUrl = facebookUrl;
    }

    public StringFilter getFooterCopyright() {
        return footerCopyright;
    }

    public Optional<StringFilter> optionalFooterCopyright() {
        return Optional.ofNullable(footerCopyright);
    }

    public StringFilter footerCopyright() {
        if (footerCopyright == null) {
            setFooterCopyright(new StringFilter());
        }
        return footerCopyright;
    }

    public void setFooterCopyright(StringFilter footerCopyright) {
        this.footerCopyright = footerCopyright;
    }

    public StringFilter getFooterTagline() {
        return footerTagline;
    }

    public Optional<StringFilter> optionalFooterTagline() {
        return Optional.ofNullable(footerTagline);
    }

    public StringFilter footerTagline() {
        if (footerTagline == null) {
            setFooterTagline(new StringFilter());
        }
        return footerTagline;
    }

    public void setFooterTagline(StringFilter footerTagline) {
        this.footerTagline = footerTagline;
    }

    public StringFilter getInstagramUrl() {
        return instagramUrl;
    }

    public Optional<StringFilter> optionalInstagramUrl() {
        return Optional.ofNullable(instagramUrl);
    }

    public StringFilter instagramUrl() {
        if (instagramUrl == null) {
            setInstagramUrl(new StringFilter());
        }
        return instagramUrl;
    }

    public void setInstagramUrl(StringFilter instagramUrl) {
        this.instagramUrl = instagramUrl;
    }

    public StringFilter getLegalEntityName() {
        return legalEntityName;
    }

    public Optional<StringFilter> optionalLegalEntityName() {
        return Optional.ofNullable(legalEntityName);
    }

    public StringFilter legalEntityName() {
        if (legalEntityName == null) {
            setLegalEntityName(new StringFilter());
        }
        return legalEntityName;
    }

    public void setLegalEntityName(StringFilter legalEntityName) {
        this.legalEntityName = legalEntityName;
    }

    public StringFilter getLogoHorizontalUrl() {
        return logoHorizontalUrl;
    }

    public Optional<StringFilter> optionalLogoHorizontalUrl() {
        return Optional.ofNullable(logoHorizontalUrl);
    }

    public StringFilter logoHorizontalUrl() {
        if (logoHorizontalUrl == null) {
            setLogoHorizontalUrl(new StringFilter());
        }
        return logoHorizontalUrl;
    }

    public void setLogoHorizontalUrl(StringFilter logoHorizontalUrl) {
        this.logoHorizontalUrl = logoHorizontalUrl;
    }

    public StringFilter getLogoUrl() {
        return logoUrl;
    }

    public Optional<StringFilter> optionalLogoUrl() {
        return Optional.ofNullable(logoUrl);
    }

    public StringFilter logoUrl() {
        if (logoUrl == null) {
            setLogoUrl(new StringFilter());
        }
        return logoUrl;
    }

    public void setLogoUrl(StringFilter logoUrl) {
        this.logoUrl = logoUrl;
    }

    public StringFilter getTwitterUrl() {
        return twitterUrl;
    }

    public Optional<StringFilter> optionalTwitterUrl() {
        return Optional.ofNullable(twitterUrl);
    }

    public StringFilter twitterUrl() {
        if (twitterUrl == null) {
            setTwitterUrl(new StringFilter());
        }
        return twitterUrl;
    }

    public void setTwitterUrl(StringFilter twitterUrl) {
        this.twitterUrl = twitterUrl;
    }

    public StringFilter getWhatsappNumber() {
        return whatsappNumber;
    }

    public Optional<StringFilter> optionalWhatsappNumber() {
        return Optional.ofNullable(whatsappNumber);
    }

    public StringFilter whatsappNumber() {
        if (whatsappNumber == null) {
            setWhatsappNumber(new StringFilter());
        }
        return whatsappNumber;
    }

    public void setWhatsappNumber(StringFilter whatsappNumber) {
        this.whatsappNumber = whatsappNumber;
    }

    public StringFilter getYoutubeUrl() {
        return youtubeUrl;
    }

    public Optional<StringFilter> optionalYoutubeUrl() {
        return Optional.ofNullable(youtubeUrl);
    }

    public StringFilter youtubeUrl() {
        if (youtubeUrl == null) {
            setYoutubeUrl(new StringFilter());
        }
        return youtubeUrl;
    }

    public void setYoutubeUrl(StringFilter youtubeUrl) {
        this.youtubeUrl = youtubeUrl;
    }

    public Boolean getDistinct() {
        return distinct;
    }

    public Optional<Boolean> optionalDistinct() {
        return Optional.ofNullable(distinct);
    }

    public Boolean distinct() {
        if (distinct == null) {
            setDistinct(true);
        }
        return distinct;
    }

    public void setDistinct(Boolean distinct) {
        this.distinct = distinct;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final SiteSettingCriteria that = (SiteSettingCriteria) o;
        return (
            Objects.equals(id, that.id) &&
            Objects.equals(brandName, that.brandName) &&
            Objects.equals(careLabel, that.careLabel) &&
            Objects.equals(contactEmail, that.contactEmail) &&
            Objects.equals(contactPhone, that.contactPhone) &&
            Objects.equals(ecatalogueLabel, that.ecatalogueLabel) &&
            Objects.equals(ecatalogueUrl, that.ecatalogueUrl) &&
            Objects.equals(facebookUrl, that.facebookUrl) &&
            Objects.equals(footerCopyright, that.footerCopyright) &&
            Objects.equals(footerTagline, that.footerTagline) &&
            Objects.equals(instagramUrl, that.instagramUrl) &&
            Objects.equals(legalEntityName, that.legalEntityName) &&
            Objects.equals(logoHorizontalUrl, that.logoHorizontalUrl) &&
            Objects.equals(logoUrl, that.logoUrl) &&
            Objects.equals(twitterUrl, that.twitterUrl) &&
            Objects.equals(whatsappNumber, that.whatsappNumber) &&
            Objects.equals(youtubeUrl, that.youtubeUrl) &&
            Objects.equals(distinct, that.distinct)
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(
            id,
            brandName,
            careLabel,
            contactEmail,
            contactPhone,
            ecatalogueLabel,
            ecatalogueUrl,
            facebookUrl,
            footerCopyright,
            footerTagline,
            instagramUrl,
            legalEntityName,
            logoHorizontalUrl,
            logoUrl,
            twitterUrl,
            whatsappNumber,
            youtubeUrl,
            distinct
        );
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "SiteSettingCriteria{" +
            optionalId().map(f -> "id=" + f + ", ").orElse("") +
            optionalBrandName().map(f -> "brandName=" + f + ", ").orElse("") +
            optionalCareLabel().map(f -> "careLabel=" + f + ", ").orElse("") +
            optionalContactEmail().map(f -> "contactEmail=" + f + ", ").orElse("") +
            optionalContactPhone().map(f -> "contactPhone=" + f + ", ").orElse("") +
            optionalEcatalogueLabel().map(f -> "ecatalogueLabel=" + f + ", ").orElse("") +
            optionalEcatalogueUrl().map(f -> "ecatalogueUrl=" + f + ", ").orElse("") +
            optionalFacebookUrl().map(f -> "facebookUrl=" + f + ", ").orElse("") +
            optionalFooterCopyright().map(f -> "footerCopyright=" + f + ", ").orElse("") +
            optionalFooterTagline().map(f -> "footerTagline=" + f + ", ").orElse("") +
            optionalInstagramUrl().map(f -> "instagramUrl=" + f + ", ").orElse("") +
            optionalLegalEntityName().map(f -> "legalEntityName=" + f + ", ").orElse("") +
            optionalLogoHorizontalUrl().map(f -> "logoHorizontalUrl=" + f + ", ").orElse("") +
            optionalLogoUrl().map(f -> "logoUrl=" + f + ", ").orElse("") +
            optionalTwitterUrl().map(f -> "twitterUrl=" + f + ", ").orElse("") +
            optionalWhatsappNumber().map(f -> "whatsappNumber=" + f + ", ").orElse("") +
            optionalYoutubeUrl().map(f -> "youtubeUrl=" + f + ", ").orElse("") +
            optionalDistinct().map(f -> "distinct=" + f + ", ").orElse("") +
        "}";
    }
}
