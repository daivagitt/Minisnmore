package com.miniandmore.app.service.criteria;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import java.util.Optional;
import org.springdoc.core.annotations.ParameterObject;
import tech.jhipster.service.Criteria;
import tech.jhipster.service.filter.*;

/**
 * Criteria class for the {@link com.miniandmore.app.domain.HomeSection} entity. This class is used
 * in {@link com.miniandmore.app.web.rest.HomeSectionResource} to receive all the possible filtering options from
 * the Http GET request parameters.
 * For example the following could be a valid request:
 * {@code /home-sections?id.greaterThan=5&attr1.contains=something&attr2.specified=false}
 * As Spring is unable to properly convert the types, unless specific {@link Filter} class are used, we need to use
 * fix type specific filters.
 */
@ParameterObject
@SuppressWarnings("common-java:DuplicatedBlocks")
public class HomeSectionCriteria implements Serializable, Criteria {

    @Serial
    private static final long serialVersionUID = 1L;

    private LongFilter id;

    private StringFilter sectionKey;

    private StringFilter title;

    private StringFilter subtitle;

    private BooleanFilter enabled;

    private IntegerFilter sortOrder;

    private Boolean distinct;

    public HomeSectionCriteria() {}

    public HomeSectionCriteria(HomeSectionCriteria other) {
        this.id = other.optionalId().map(LongFilter::copy).orElse(null);
        this.sectionKey = other.optionalSectionKey().map(StringFilter::copy).orElse(null);
        this.title = other.optionalTitle().map(StringFilter::copy).orElse(null);
        this.subtitle = other.optionalSubtitle().map(StringFilter::copy).orElse(null);
        this.enabled = other.optionalEnabled().map(BooleanFilter::copy).orElse(null);
        this.sortOrder = other.optionalSortOrder().map(IntegerFilter::copy).orElse(null);
        this.distinct = other.distinct;
    }

    @Override
    public HomeSectionCriteria copy() {
        return new HomeSectionCriteria(this);
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

    public StringFilter getSectionKey() {
        return sectionKey;
    }

    public Optional<StringFilter> optionalSectionKey() {
        return Optional.ofNullable(sectionKey);
    }

    public StringFilter sectionKey() {
        if (sectionKey == null) {
            setSectionKey(new StringFilter());
        }
        return sectionKey;
    }

    public void setSectionKey(StringFilter sectionKey) {
        this.sectionKey = sectionKey;
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

    public BooleanFilter getEnabled() {
        return enabled;
    }

    public Optional<BooleanFilter> optionalEnabled() {
        return Optional.ofNullable(enabled);
    }

    public BooleanFilter enabled() {
        if (enabled == null) {
            setEnabled(new BooleanFilter());
        }
        return enabled;
    }

    public void setEnabled(BooleanFilter enabled) {
        this.enabled = enabled;
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
        final HomeSectionCriteria that = (HomeSectionCriteria) o;
        return (
            Objects.equals(id, that.id) &&
            Objects.equals(sectionKey, that.sectionKey) &&
            Objects.equals(title, that.title) &&
            Objects.equals(subtitle, that.subtitle) &&
            Objects.equals(enabled, that.enabled) &&
            Objects.equals(sortOrder, that.sortOrder) &&
            Objects.equals(distinct, that.distinct)
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, sectionKey, title, subtitle, enabled, sortOrder, distinct);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "HomeSectionCriteria{" +
            optionalId().map(f -> "id=" + f + ", ").orElse("") +
            optionalSectionKey().map(f -> "sectionKey=" + f + ", ").orElse("") +
            optionalTitle().map(f -> "title=" + f + ", ").orElse("") +
            optionalSubtitle().map(f -> "subtitle=" + f + ", ").orElse("") +
            optionalEnabled().map(f -> "enabled=" + f + ", ").orElse("") +
            optionalSortOrder().map(f -> "sortOrder=" + f + ", ").orElse("") +
            optionalDistinct().map(f -> "distinct=" + f + ", ").orElse("") +
        "}";
    }
}
