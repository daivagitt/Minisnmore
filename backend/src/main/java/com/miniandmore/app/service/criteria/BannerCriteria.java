package com.miniandmore.app.service.criteria;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import java.util.Optional;
import org.springdoc.core.annotations.ParameterObject;
import tech.jhipster.service.Criteria;
import tech.jhipster.service.filter.*;

/**
 * Criteria class for the {@link com.miniandmore.app.domain.Banner} entity. This class is used
 * in {@link com.miniandmore.app.web.rest.BannerResource} to receive all the possible filtering options from
 * the Http GET request parameters.
 * For example the following could be a valid request:
 * {@code /banners?id.greaterThan=5&attr1.contains=something&attr2.specified=false}
 * As Spring is unable to properly convert the types, unless specific {@link Filter} class are used, we need to use
 * fix type specific filters.
 */
@ParameterObject
@SuppressWarnings("common-java:DuplicatedBlocks")
public class BannerCriteria implements Serializable, Criteria {

    @Serial
    private static final long serialVersionUID = 1L;

    private LongFilter id;

    private StringFilter title;

    private StringFilter subtitle;

    private StringFilter imageUrl;

    private StringFilter linkUrl;

    private StringFilter ctaLabel;

    private IntegerFilter sortOrder;

    private BooleanFilter active;

    private Boolean distinct;

    public BannerCriteria() {}

    public BannerCriteria(BannerCriteria other) {
        this.id = other.optionalId().map(LongFilter::copy).orElse(null);
        this.title = other.optionalTitle().map(StringFilter::copy).orElse(null);
        this.subtitle = other.optionalSubtitle().map(StringFilter::copy).orElse(null);
        this.imageUrl = other.optionalImageUrl().map(StringFilter::copy).orElse(null);
        this.linkUrl = other.optionalLinkUrl().map(StringFilter::copy).orElse(null);
        this.ctaLabel = other.optionalCtaLabel().map(StringFilter::copy).orElse(null);
        this.sortOrder = other.optionalSortOrder().map(IntegerFilter::copy).orElse(null);
        this.active = other.optionalActive().map(BooleanFilter::copy).orElse(null);
        this.distinct = other.distinct;
    }

    @Override
    public BannerCriteria copy() {
        return new BannerCriteria(this);
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

    public StringFilter getTitle() {
        return title;
    }

    public Optional<StringFilter> optionalTitle() {
        return Optional.ofNullable(title);
    }

    public StringFilter title() {
        if (title == null) {
            setTitle(new StringFilter());
        }
        return title;
    }

    public void setTitle(StringFilter title) {
        this.title = title;
    }

    public StringFilter getSubtitle() {
        return subtitle;
    }

    public Optional<StringFilter> optionalSubtitle() {
        return Optional.ofNullable(subtitle);
    }

    public StringFilter subtitle() {
        if (subtitle == null) {
            setSubtitle(new StringFilter());
        }
        return subtitle;
    }

    public void setSubtitle(StringFilter subtitle) {
        this.subtitle = subtitle;
    }

    public StringFilter getImageUrl() {
        return imageUrl;
    }

    public Optional<StringFilter> optionalImageUrl() {
        return Optional.ofNullable(imageUrl);
    }

    public StringFilter imageUrl() {
        if (imageUrl == null) {
            setImageUrl(new StringFilter());
        }
        return imageUrl;
    }

    public void setImageUrl(StringFilter imageUrl) {
        this.imageUrl = imageUrl;
    }

    public StringFilter getLinkUrl() {
        return linkUrl;
    }

    public Optional<StringFilter> optionalLinkUrl() {
        return Optional.ofNullable(linkUrl);
    }

    public StringFilter linkUrl() {
        if (linkUrl == null) {
            setLinkUrl(new StringFilter());
        }
        return linkUrl;
    }

    public void setLinkUrl(StringFilter linkUrl) {
        this.linkUrl = linkUrl;
    }

    public StringFilter getCtaLabel() {
        return ctaLabel;
    }

    public Optional<StringFilter> optionalCtaLabel() {
        return Optional.ofNullable(ctaLabel);
    }

    public StringFilter ctaLabel() {
        if (ctaLabel == null) {
            setCtaLabel(new StringFilter());
        }
        return ctaLabel;
    }

    public void setCtaLabel(StringFilter ctaLabel) {
        this.ctaLabel = ctaLabel;
    }

    public IntegerFilter getSortOrder() {
        return sortOrder;
    }

    public Optional<IntegerFilter> optionalSortOrder() {
        return Optional.ofNullable(sortOrder);
    }

    public IntegerFilter sortOrder() {
        if (sortOrder == null) {
            setSortOrder(new IntegerFilter());
        }
        return sortOrder;
    }

    public void setSortOrder(IntegerFilter sortOrder) {
        this.sortOrder = sortOrder;
    }

    public BooleanFilter getActive() {
        return active;
    }

    public Optional<BooleanFilter> optionalActive() {
        return Optional.ofNullable(active);
    }

    public BooleanFilter active() {
        if (active == null) {
            setActive(new BooleanFilter());
        }
        return active;
    }

    public void setActive(BooleanFilter active) {
        this.active = active;
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
        final BannerCriteria that = (BannerCriteria) o;
        return (
            Objects.equals(id, that.id) &&
            Objects.equals(title, that.title) &&
            Objects.equals(subtitle, that.subtitle) &&
            Objects.equals(imageUrl, that.imageUrl) &&
            Objects.equals(linkUrl, that.linkUrl) &&
            Objects.equals(ctaLabel, that.ctaLabel) &&
            Objects.equals(sortOrder, that.sortOrder) &&
            Objects.equals(active, that.active) &&
            Objects.equals(distinct, that.distinct)
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, title, subtitle, imageUrl, linkUrl, ctaLabel, sortOrder, active, distinct);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "BannerCriteria{" +
            optionalId().map(f -> "id=" + f + ", ").orElse("") +
            optionalTitle().map(f -> "title=" + f + ", ").orElse("") +
            optionalSubtitle().map(f -> "subtitle=" + f + ", ").orElse("") +
            optionalImageUrl().map(f -> "imageUrl=" + f + ", ").orElse("") +
            optionalLinkUrl().map(f -> "linkUrl=" + f + ", ").orElse("") +
            optionalCtaLabel().map(f -> "ctaLabel=" + f + ", ").orElse("") +
            optionalSortOrder().map(f -> "sortOrder=" + f + ", ").orElse("") +
            optionalActive().map(f -> "active=" + f + ", ").orElse("") +
            optionalDistinct().map(f -> "distinct=" + f + ", ").orElse("") +
        "}";
    }
}
