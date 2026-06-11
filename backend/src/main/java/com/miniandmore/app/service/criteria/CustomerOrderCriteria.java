package com.miniandmore.app.service.criteria;

import com.miniandmore.app.domain.enumeration.OrderStatus;
import com.miniandmore.app.domain.enumeration.PaymentStatus;
import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import java.util.Optional;
import org.springdoc.core.annotations.ParameterObject;
import tech.jhipster.service.Criteria;
import tech.jhipster.service.filter.*;

/**
 * Criteria class for the {@link com.miniandmore.app.domain.CustomerOrder} entity. This class is used
 * in {@link com.miniandmore.app.web.rest.CustomerOrderResource} to receive all the possible filtering options from
 * the Http GET request parameters.
 * For example the following could be a valid request:
 * {@code /customer-orders?id.greaterThan=5&attr1.contains=something&attr2.specified=false}
 * As Spring is unable to properly convert the types, unless specific {@link Filter} class are used, we need to use
 * fix type specific filters.
 */
@ParameterObject
@SuppressWarnings("common-java:DuplicatedBlocks")
public class CustomerOrderCriteria implements Serializable, Criteria {

    /**
     * Class for filtering OrderStatus
     */
    public static class OrderStatusFilter extends Filter<OrderStatus> {

        public OrderStatusFilter() {}

        public OrderStatusFilter(OrderStatusFilter filter) {
            super(filter);
        }

        @Override
        public OrderStatusFilter copy() {
            return new OrderStatusFilter(this);
        }
    }

    /**
     * Class for filtering PaymentStatus
     */
    public static class PaymentStatusFilter extends Filter<PaymentStatus> {

        public PaymentStatusFilter() {}

        public PaymentStatusFilter(PaymentStatusFilter filter) {
            super(filter);
        }

        @Override
        public PaymentStatusFilter copy() {
            return new PaymentStatusFilter(this);
        }
    }

    @Serial
    private static final long serialVersionUID = 1L;

    private LongFilter id;

    private StringFilter userLogin;

    private StringFilter orderNumber;

    private OrderStatusFilter status;

    private PaymentStatusFilter paymentStatus;

    private StringFilter razorpayOrderId;

    private StringFilter razorpayPaymentId;

    private StringFilter razorpaySignature;

    private BigDecimalFilter subtotal;

    private BigDecimalFilter discount;

    private BigDecimalFilter shipping;

    private BigDecimalFilter tax;

    private BigDecimalFilter donation;

    private BigDecimalFilter total;

    private StringFilter couponCode;

    private StringFilter notes;

    private LongFilter itemsId;

    private Boolean distinct;

    public CustomerOrderCriteria() {}

    public CustomerOrderCriteria(CustomerOrderCriteria other) {
        this.id = other.optionalId().map(LongFilter::copy).orElse(null);
        this.userLogin = other.optionalUserLogin().map(StringFilter::copy).orElse(null);
        this.orderNumber = other.optionalOrderNumber().map(StringFilter::copy).orElse(null);
        this.status = other.optionalStatus().map(OrderStatusFilter::copy).orElse(null);
        this.paymentStatus = other.optionalPaymentStatus().map(PaymentStatusFilter::copy).orElse(null);
        this.razorpayOrderId = other.optionalRazorpayOrderId().map(StringFilter::copy).orElse(null);
        this.razorpayPaymentId = other.optionalRazorpayPaymentId().map(StringFilter::copy).orElse(null);
        this.razorpaySignature = other.optionalRazorpaySignature().map(StringFilter::copy).orElse(null);
        this.subtotal = other.optionalSubtotal().map(BigDecimalFilter::copy).orElse(null);
        this.discount = other.optionalDiscount().map(BigDecimalFilter::copy).orElse(null);
        this.shipping = other.optionalShipping().map(BigDecimalFilter::copy).orElse(null);
        this.tax = other.optionalTax().map(BigDecimalFilter::copy).orElse(null);
        this.donation = other.optionalDonation().map(BigDecimalFilter::copy).orElse(null);
        this.total = other.optionalTotal().map(BigDecimalFilter::copy).orElse(null);
        this.couponCode = other.optionalCouponCode().map(StringFilter::copy).orElse(null);
        this.notes = other.optionalNotes().map(StringFilter::copy).orElse(null);
        this.itemsId = other.optionalItemsId().map(LongFilter::copy).orElse(null);
        this.distinct = other.distinct;
    }

    @Override
    public CustomerOrderCriteria copy() {
        return new CustomerOrderCriteria(this);
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

    public StringFilter getUserLogin() {
        return userLogin;
    }

    public Optional<StringFilter> optionalUserLogin() {
        return Optional.ofNullable(userLogin);
    }

    public StringFilter userLogin() {
        if (userLogin == null) {
            setUserLogin(new StringFilter());
        }
        return userLogin;
    }

    public void setUserLogin(StringFilter userLogin) {
        this.userLogin = userLogin;
    }

    public StringFilter getOrderNumber() {
        return orderNumber;
    }

    public Optional<StringFilter> optionalOrderNumber() {
        return Optional.ofNullable(orderNumber);
    }

    public StringFilter orderNumber() {
        if (orderNumber == null) {
            setOrderNumber(new StringFilter());
        }
        return orderNumber;
    }

    public void setOrderNumber(StringFilter orderNumber) {
        this.orderNumber = orderNumber;
    }

    public OrderStatusFilter getStatus() {
        return status;
    }

    public Optional<OrderStatusFilter> optionalStatus() {
        return Optional.ofNullable(status);
    }

    public OrderStatusFilter status() {
        if (status == null) {
            setStatus(new OrderStatusFilter());
        }
        return status;
    }

    public void setStatus(OrderStatusFilter status) {
        this.status = status;
    }

    public PaymentStatusFilter getPaymentStatus() {
        return paymentStatus;
    }

    public Optional<PaymentStatusFilter> optionalPaymentStatus() {
        return Optional.ofNullable(paymentStatus);
    }

    public PaymentStatusFilter paymentStatus() {
        if (paymentStatus == null) {
            setPaymentStatus(new PaymentStatusFilter());
        }
        return paymentStatus;
    }

    public void setPaymentStatus(PaymentStatusFilter paymentStatus) {
        this.paymentStatus = paymentStatus;
    }

    public StringFilter getRazorpayOrderId() {
        return razorpayOrderId;
    }

    public Optional<StringFilter> optionalRazorpayOrderId() {
        return Optional.ofNullable(razorpayOrderId);
    }

    public StringFilter razorpayOrderId() {
        if (razorpayOrderId == null) {
            setRazorpayOrderId(new StringFilter());
        }
        return razorpayOrderId;
    }

    public void setRazorpayOrderId(StringFilter razorpayOrderId) {
        this.razorpayOrderId = razorpayOrderId;
    }

    public StringFilter getRazorpayPaymentId() {
        return razorpayPaymentId;
    }

    public Optional<StringFilter> optionalRazorpayPaymentId() {
        return Optional.ofNullable(razorpayPaymentId);
    }

    public StringFilter razorpayPaymentId() {
        if (razorpayPaymentId == null) {
            setRazorpayPaymentId(new StringFilter());
        }
        return razorpayPaymentId;
    }

    public void setRazorpayPaymentId(StringFilter razorpayPaymentId) {
        this.razorpayPaymentId = razorpayPaymentId;
    }

    public StringFilter getRazorpaySignature() {
        return razorpaySignature;
    }

    public Optional<StringFilter> optionalRazorpaySignature() {
        return Optional.ofNullable(razorpaySignature);
    }

    public StringFilter razorpaySignature() {
        if (razorpaySignature == null) {
            setRazorpaySignature(new StringFilter());
        }
        return razorpaySignature;
    }

    public void setRazorpaySignature(StringFilter razorpaySignature) {
        this.razorpaySignature = razorpaySignature;
    }

    public BigDecimalFilter getSubtotal() {
        return subtotal;
    }

    public Optional<BigDecimalFilter> optionalSubtotal() {
        return Optional.ofNullable(subtotal);
    }

    public BigDecimalFilter subtotal() {
        if (subtotal == null) {
            setSubtotal(new BigDecimalFilter());
        }
        return subtotal;
    }

    public void setSubtotal(BigDecimalFilter subtotal) {
        this.subtotal = subtotal;
    }

    public BigDecimalFilter getDiscount() {
        return discount;
    }

    public Optional<BigDecimalFilter> optionalDiscount() {
        return Optional.ofNullable(discount);
    }

    public BigDecimalFilter discount() {
        if (discount == null) {
            setDiscount(new BigDecimalFilter());
        }
        return discount;
    }

    public void setDiscount(BigDecimalFilter discount) {
        this.discount = discount;
    }

    public BigDecimalFilter getShipping() {
        return shipping;
    }

    public Optional<BigDecimalFilter> optionalShipping() {
        return Optional.ofNullable(shipping);
    }

    public BigDecimalFilter shipping() {
        if (shipping == null) {
            setShipping(new BigDecimalFilter());
        }
        return shipping;
    }

    public void setShipping(BigDecimalFilter shipping) {
        this.shipping = shipping;
    }

    public BigDecimalFilter getTax() {
        return tax;
    }

    public Optional<BigDecimalFilter> optionalTax() {
        return Optional.ofNullable(tax);
    }

    public BigDecimalFilter tax() {
        if (tax == null) {
            setTax(new BigDecimalFilter());
        }
        return tax;
    }

    public void setTax(BigDecimalFilter tax) {
        this.tax = tax;
    }

    public BigDecimalFilter getDonation() {
        return donation;
    }

    public Optional<BigDecimalFilter> optionalDonation() {
        return Optional.ofNullable(donation);
    }

    public BigDecimalFilter donation() {
        if (donation == null) {
            setDonation(new BigDecimalFilter());
        }
        return donation;
    }

    public void setDonation(BigDecimalFilter donation) {
        this.donation = donation;
    }

    public BigDecimalFilter getTotal() {
        return total;
    }

    public Optional<BigDecimalFilter> optionalTotal() {
        return Optional.ofNullable(total);
    }

    public BigDecimalFilter total() {
        if (total == null) {
            setTotal(new BigDecimalFilter());
        }
        return total;
    }

    public void setTotal(BigDecimalFilter total) {
        this.total = total;
    }

    public StringFilter getCouponCode() {
        return couponCode;
    }

    public Optional<StringFilter> optionalCouponCode() {
        return Optional.ofNullable(couponCode);
    }

    public StringFilter couponCode() {
        if (couponCode == null) {
            setCouponCode(new StringFilter());
        }
        return couponCode;
    }

    public void setCouponCode(StringFilter couponCode) {
        this.couponCode = couponCode;
    }

    public StringFilter getNotes() {
        return notes;
    }

    public Optional<StringFilter> optionalNotes() {
        return Optional.ofNullable(notes);
    }

    public StringFilter notes() {
        if (notes == null) {
            setNotes(new StringFilter());
        }
        return notes;
    }

    public void setNotes(StringFilter notes) {
        this.notes = notes;
    }

    public LongFilter getItemsId() {
        return itemsId;
    }

    public Optional<LongFilter> optionalItemsId() {
        return Optional.ofNullable(itemsId);
    }

    public LongFilter itemsId() {
        if (itemsId == null) {
            setItemsId(new LongFilter());
        }
        return itemsId;
    }

    public void setItemsId(LongFilter itemsId) {
        this.itemsId = itemsId;
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
        final CustomerOrderCriteria that = (CustomerOrderCriteria) o;
        return (
            Objects.equals(id, that.id) &&
            Objects.equals(userLogin, that.userLogin) &&
            Objects.equals(orderNumber, that.orderNumber) &&
            Objects.equals(status, that.status) &&
            Objects.equals(paymentStatus, that.paymentStatus) &&
            Objects.equals(razorpayOrderId, that.razorpayOrderId) &&
            Objects.equals(razorpayPaymentId, that.razorpayPaymentId) &&
            Objects.equals(razorpaySignature, that.razorpaySignature) &&
            Objects.equals(subtotal, that.subtotal) &&
            Objects.equals(discount, that.discount) &&
            Objects.equals(shipping, that.shipping) &&
            Objects.equals(tax, that.tax) &&
            Objects.equals(donation, that.donation) &&
            Objects.equals(total, that.total) &&
            Objects.equals(couponCode, that.couponCode) &&
            Objects.equals(notes, that.notes) &&
            Objects.equals(itemsId, that.itemsId) &&
            Objects.equals(distinct, that.distinct)
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(
            id,
            userLogin,
            orderNumber,
            status,
            paymentStatus,
            razorpayOrderId,
            razorpayPaymentId,
            razorpaySignature,
            subtotal,
            discount,
            shipping,
            tax,
            donation,
            total,
            couponCode,
            notes,
            itemsId,
            distinct
        );
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "CustomerOrderCriteria{" +
            optionalId().map(f -> "id=" + f + ", ").orElse("") +
            optionalUserLogin().map(f -> "userLogin=" + f + ", ").orElse("") +
            optionalOrderNumber().map(f -> "orderNumber=" + f + ", ").orElse("") +
            optionalStatus().map(f -> "status=" + f + ", ").orElse("") +
            optionalPaymentStatus().map(f -> "paymentStatus=" + f + ", ").orElse("") +
            (optionalRazorpayOrderId().isPresent() ? "razorpayOrderId=[REDACTED], " : "") +
            (optionalRazorpayPaymentId().isPresent() ? "razorpayPaymentId=[REDACTED], " : "") +
            (optionalRazorpaySignature().isPresent() ? "razorpaySignature=[REDACTED], " : "") +
            optionalSubtotal().map(f -> "subtotal=" + f + ", ").orElse("") +
            optionalDiscount().map(f -> "discount=" + f + ", ").orElse("") +
            optionalShipping().map(f -> "shipping=" + f + ", ").orElse("") +
            optionalTax().map(f -> "tax=" + f + ", ").orElse("") +
            optionalDonation().map(f -> "donation=" + f + ", ").orElse("") +
            optionalTotal().map(f -> "total=" + f + ", ").orElse("") +
            optionalCouponCode().map(f -> "couponCode=" + f + ", ").orElse("") +
            (optionalNotes().isPresent() ? "notes=[REDACTED], " : "") +
            optionalItemsId().map(f -> "itemsId=" + f + ", ").orElse("") +
            optionalDistinct().map(f -> "distinct=" + f + ", ").orElse("") +
        "}";
    }
}
