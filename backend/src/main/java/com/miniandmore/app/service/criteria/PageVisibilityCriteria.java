package com.miniandmore.app.service.criteria;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import java.util.Optional;
import org.springdoc.core.annotations.ParameterObject;
import tech.jhipster.service.Criteria;
import tech.jhipster.service.filter.*;

/**
 * Criteria class for the {@link com.miniandmore.app.domain.PageVisibility} entity. This class is used
 * in {@link com.miniandmore.app.web.rest.PageVisibilityResource} to receive all the possible filtering options from
 * the Http GET request parameters.
 * For example the following could be a valid request:
 * {@code /page-visibilities?id.greaterThan=5&attr1.contains=something&attr2.specified=false}
 * As Spring is unable to properly convert the types, unless specific {@link Filter} class are used, we need to use
 * fix type specific filters.
 */
@ParameterObject
@SuppressWarnings("common-java:DuplicatedBlocks")
public class PageVisibilityCriteria implements Serializable, Criteria {

    @Serial
    private static final long serialVersionUID = 1L;

    private LongFilter id;

    private StringFilter pageKey;

    private BooleanFilter visible;

    private Boolean distinct;

    public PageVisibilityCriteria() {}

    public PageVisibilityCriteria(PageVisibilityCriteria other) {
        this.id = other.optionalId().map(LongFilter::copy).orElse(null);
        this.pageKey = other.optionalPageKey().map(StringFilter::copy).orElse(null);
        this.visible = other.optionalVisible().map(BooleanFilter::copy).orElse(null);
        this.distinct = other.distinct;
    }

    @Override
    public PageVisibilityCriteria copy() {
        return new PageVisibilityCriteria(this);
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

    public StringFilter getPageKey() {
        return pageKey;
    }

    public Optional<StringFilter> optionalPageKey() {
        return Optional.ofNullable(pageKey);
    }

    public StringFilter pageKey() {
        if (pageKey == null) {
            setPageKey(new StringFilter());
        }
        return pageKey;
    }

    public void setPageKey(StringFilter pageKey) {
        this.pageKey = pageKey;
    }

    public BooleanFilter getVisible() {
        return visible;
    }

    public Optional<BooleanFilter> optionalVisible() {
        return Optional.ofNullable(visible);
    }

    public BooleanFilter visible() {
        if (visible == null) {
            setVisible(new BooleanFilter());
        }
        return visible;
    }

    public void setVisible(BooleanFilter visible) {
        this.visible = visible;
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
        final PageVisibilityCriteria that = (PageVisibilityCriteria) o;
        return (
            Objects.equals(id, that.id) &&
            Objects.equals(pageKey, that.pageKey) &&
            Objects.equals(visible, that.visible) &&
            Objects.equals(distinct, that.distinct)
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, pageKey, visible, distinct);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "PageVisibilityCriteria{" +
            optionalId().map(f -> "id=" + f + ", ").orElse("") +
            optionalPageKey().map(f -> "pageKey=" + f + ", ").orElse("") +
            optionalVisible().map(f -> "visible=" + f + ", ").orElse("") +
            optionalDistinct().map(f -> "distinct=" + f + ", ").orElse("") +
        "}";
    }
}
