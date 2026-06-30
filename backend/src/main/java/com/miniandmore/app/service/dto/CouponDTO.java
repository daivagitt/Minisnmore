package com.miniandmore.app.service.dto;

import com.miniandmore.app.domain.enumeration.DiscountType;
import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * A DTO for the {@link com.miniandmore.app.domain.Coupon} entity.
 */
@SuppressWarnings("common-java:DuplicatedBlocks")
public class CouponDTO implements Serializable {

    private Long id;

    @NotNull
    @Size(min = 1, max = 64)
    private String code;

    @NotNull
    private DiscountType discountType;

    @NotNull
    @DecimalMin(value = "0")
    private BigDecimal discountValue;

    @NotNull
    @DecimalMin(value = "0")
    private BigDecimal minOrder;

    @DecimalMin(value = "0")
    private BigDecimal maxDiscount;

    private Instant expiresAt;

    @NotNull
    private Boolean active;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public DiscountType getDiscountType() {
        return discountType;
    }

    public void setDiscountType(DiscountType discountType) {
        this.discountType = discountType;
    }

    public BigDecimal getDiscountValue() {
        return discountValue;
    }

    public void setDiscountValue(BigDecimal discountValue) {
        this.discountValue = discountValue;
    }

    public BigDecimal getMinOrder() {
        return minOrder;
    }

    public void setMinOrder(BigDecimal minOrder) {
        this.minOrder = minOrder;
    }

    public BigDecimal getMaxDiscount() {
        return maxDiscount;
    }

    public void setMaxDiscount(BigDecimal maxDiscount) {
        this.maxDiscount = maxDiscount;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CouponDTO)) {
            return false;
        }

        CouponDTO couponDTO = (CouponDTO) o;
        if (this.id == null) {
            return false;
        }
        return Objects.equals(this.id, couponDTO.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "CouponDTO{" +
            "id=" + getId() +
            ", code='" + getCode() + "'" +
            ", discountType='" + getDiscountType() + "'" +
            ", discountValue=" + getDiscountValue() +
            ", minOrder=" + getMinOrder() +
            ", maxDiscount=" + getMaxDiscount() +
            ", expiresAt='" + getExpiresAt() + "'" +
            ", active='" + getActive() + "'" +
            "}";
    }
}
