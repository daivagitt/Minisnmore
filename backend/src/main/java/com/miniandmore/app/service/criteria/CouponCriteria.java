package com.miniandmore.app.service.criteria;

import com.miniandmore.app.domain.enumeration.DiscountType;
import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import java.util.Optional;
import org.springdoc.core.annotations.ParameterObject;
import tech.jhipster.service.Criteria;
import tech.jhipster.service.filter.*;

/**
 * Criteria class for the {@link com.miniandmore.app.domain.Coupon} entity. This class is used
 * in {@link com.miniandmore.app.web.rest.CouponResource} to receive all the possible filtering options from
 * the Http GET request parameters.
 * For example the following could be a valid request:
 * {@code /coupons?id.greaterThan=5&attr1.contains=something&attr2.specified=false}
 * As Spring is unable to properly convert the types, unless specific {@link Filter} class are used, we need to use
 * fix type specific filters.
 */
@ParameterObject
@SuppressWarnings("common-java:DuplicatedBlocks")
public class CouponCriteria implements Serializable, Criteria {

    /**
     * Class for filtering DiscountType
     */
    public static class DiscountTypeFilter extends Filter<DiscountType> implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        public DiscountTypeFilter() {}

        public DiscountTypeFilter(DiscountTypeFilter filter) {
            super(filter);
        }

        @Override
        public DiscountTypeFilter copy() {
            return new DiscountTypeFilter(this);
        }
    }

    @Serial
    private static final long serialVersionUID = 1L;

    private LongFilter id;

    private StringFilter code;

    private DiscountTypeFilter discountType;

    private BigDecimalFilter discountValue;

    private BigDecimalFilter minOrder;

    private BigDecimalFilter maxDiscount;

    private InstantFilter expiresAt;

    private BooleanFilter active;

    private Boolean distinct;

    public CouponCriteria() {}

    public CouponCriteria(CouponCriteria other) {
        this.id = other.optionalId().map(LongFilter::copy).orElse(null);
        this.code = other.optionalCode().map(StringFilter::copy).orElse(null);
        this.discountType = other.optionalDiscountType().map(DiscountTypeFilter::copy).orElse(null);
        this.discountValue = other.optionalDiscountValue().map(BigDecimalFilter::copy).orElse(null);
        this.minOrder = other.optionalMinOrder().map(BigDecimalFilter::copy).orElse(null);
        this.maxDiscount = other.optionalMaxDiscount().map(BigDecimalFilter::copy).orElse(null);
        this.expiresAt = other.optionalExpiresAt().map(InstantFilter::copy).orElse(null);
        this.active = other.optionalActive().map(BooleanFilter::copy).orElse(null);
        this.distinct = other.distinct;
    }

    @Override
    public CouponCriteria copy() {
        return new CouponCriteria(this);
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

    public StringFilter getCode() {
        return code;
    }

    public Optional<StringFilter> optionalCode() {
        return Optional.ofNullable(code);
    }

    public StringFilter code() {
        if (code == null) {
            setCode(new StringFilter());
        }
        return code;
    }

    public void setCode(StringFilter code) {
        this.code = code;
    }

    public DiscountTypeFilter getDiscountType() {
        return discountType;
    }

    public Optional<DiscountTypeFilter> optionalDiscountType() {
        return Optional.ofNullable(discountType);
    }

    public DiscountTypeFilter discountType() {
        if (discountType == null) {
            setDiscountType(new DiscountTypeFilter());
        }
        return discountType;
    }

    public void setDiscountType(DiscountTypeFilter discountType) {
        this.discountType = discountType;
    }

    public BigDecimalFilter getDiscountValue() {
        return discountValue;
    }

    public Optional<BigDecimalFilter> optionalDiscountValue() {
        return Optional.ofNullable(discountValue);
    }

    public BigDecimalFilter discountValue() {
        if (discountValue == null) {
            setDiscountValue(new BigDecimalFilter());
        }
        return discountValue;
    }

    public void setDiscountValue(BigDecimalFilter discountValue) {
        this.discountValue = discountValue;
    }

    public BigDecimalFilter getMinOrder() {
        return minOrder;
    }

    public Optional<BigDecimalFilter> optionalMinOrder() {
        return Optional.ofNullable(minOrder);
    }

    public BigDecimalFilter minOrder() {
        if (minOrder == null) {
            setMinOrder(new BigDecimalFilter());
        }
        return minOrder;
    }

    public void setMinOrder(BigDecimalFilter minOrder) {
        this.minOrder = minOrder;
    }

    public BigDecimalFilter getMaxDiscount() {
        return maxDiscount;
    }

    public Optional<BigDecimalFilter> optionalMaxDiscount() {
        return Optional.ofNullable(maxDiscount);
    }

    public BigDecimalFilter maxDiscount() {
        if (maxDiscount == null) {
            setMaxDiscount(new BigDecimalFilter());
        }
        return maxDiscount;
    }

    public void setMaxDiscount(BigDecimalFilter maxDiscount) {
        this.maxDiscount = maxDiscount;
    }

    public InstantFilter getExpiresAt() {
        return expiresAt;
    }

    public Optional<InstantFilter> optionalExpiresAt() {
        return Optional.ofNullable(expiresAt);
    }

    public InstantFilter expiresAt() {
        if (expiresAt == null) {
            setExpiresAt(new InstantFilter());
        }
        return expiresAt;
    }

    public void setExpiresAt(InstantFilter expiresAt) {
        this.expiresAt = expiresAt;
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
        final CouponCriteria that = (CouponCriteria) o;
        return (
            Objects.equals(id, that.id) &&
            Objects.equals(code, that.code) &&
            Objects.equals(discountType, that.discountType) &&
            Objects.equals(discountValue, that.discountValue) &&
            Objects.equals(minOrder, that.minOrder) &&
            Objects.equals(maxDiscount, that.maxDiscount) &&
            Objects.equals(expiresAt, that.expiresAt) &&
            Objects.equals(active, that.active) &&
            Objects.equals(distinct, that.distinct)
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, code, discountType, discountValue, minOrder, maxDiscount, expiresAt, active, distinct);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "CouponCriteria{" +
            optionalId().map(f -> "id=" + f + ", ").orElse("") +
            optionalCode().map(f -> "code=" + f + ", ").orElse("") +
            optionalDiscountType().map(f -> "discountType=" + f + ", ").orElse("") +
            optionalDiscountValue().map(f -> "discountValue=" + f + ", ").orElse("") +
            optionalMinOrder().map(f -> "minOrder=" + f + ", ").orElse("") +
            optionalMaxDiscount().map(f -> "maxDiscount=" + f + ", ").orElse("") +
            optionalExpiresAt().map(f -> "expiresAt=" + f + ", ").orElse("") +
            optionalActive().map(f -> "active=" + f + ", ").orElse("") +
            optionalDistinct().map(f -> "distinct=" + f + ", ").orElse("") +
        "}";
    }
}
