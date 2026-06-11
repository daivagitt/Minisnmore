package com.miniandmore.app.service.criteria;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import java.util.Optional;
import org.springdoc.core.annotations.ParameterObject;
import tech.jhipster.service.Criteria;
import tech.jhipster.service.filter.*;

/**
 * Criteria class for the {@link com.miniandmore.app.domain.OrderItem} entity. This class is used
 * in {@link com.miniandmore.app.web.rest.OrderItemResource} to receive all the possible filtering options from
 * the Http GET request parameters.
 * For example the following could be a valid request:
 * {@code /order-items?id.greaterThan=5&attr1.contains=something&attr2.specified=false}
 * As Spring is unable to properly convert the types, unless specific {@link Filter} class are used, we need to use
 * fix type specific filters.
 */
@ParameterObject
@SuppressWarnings("common-java:DuplicatedBlocks")
public class OrderItemCriteria implements Serializable, Criteria {

    @Serial
    private static final long serialVersionUID = 1L;

    private LongFilter id;

    private StringFilter productId;

    private StringFilter productSlug;

    private StringFilter productName;

    private StringFilter productImage;

    private StringFilter itemSize;

    private StringFilter color;

    private IntegerFilter qty;

    private BigDecimalFilter price;

    private LongFilter orderId;

    private Boolean distinct;

    public OrderItemCriteria() {}

    public OrderItemCriteria(OrderItemCriteria other) {
        this.id = other.optionalId().map(LongFilter::copy).orElse(null);
        this.productId = other.optionalProductId().map(StringFilter::copy).orElse(null);
        this.productSlug = other.optionalProductSlug().map(StringFilter::copy).orElse(null);
        this.productName = other.optionalProductName().map(StringFilter::copy).orElse(null);
        this.productImage = other.optionalProductImage().map(StringFilter::copy).orElse(null);
        this.itemSize = other.optionalItemSize().map(StringFilter::copy).orElse(null);
        this.color = other.optionalColor().map(StringFilter::copy).orElse(null);
        this.qty = other.optionalQty().map(IntegerFilter::copy).orElse(null);
        this.price = other.optionalPrice().map(BigDecimalFilter::copy).orElse(null);
        this.orderId = other.optionalOrderId().map(LongFilter::copy).orElse(null);
        this.distinct = other.distinct;
    }

    @Override
    public OrderItemCriteria copy() {
        return new OrderItemCriteria(this);
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

    public StringFilter getProductId() {
        return productId;
    }

    public Optional<StringFilter> optionalProductId() {
        return Optional.ofNullable(productId);
    }

    public StringFilter productId() {
        if (productId == null) {
            setProductId(new StringFilter());
        }
        return productId;
    }

    public void setProductId(StringFilter productId) {
        this.productId = productId;
    }

    public StringFilter getProductSlug() {
        return productSlug;
    }

    public Optional<StringFilter> optionalProductSlug() {
        return Optional.ofNullable(productSlug);
    }

    public StringFilter productSlug() {
        if (productSlug == null) {
            setProductSlug(new StringFilter());
        }
        return productSlug;
    }

    public void setProductSlug(StringFilter productSlug) {
        this.productSlug = productSlug;
    }

    public StringFilter getProductName() {
        return productName;
    }

    public Optional<StringFilter> optionalProductName() {
        return Optional.ofNullable(productName);
    }

    public StringFilter productName() {
        if (productName == null) {
            setProductName(new StringFilter());
        }
        return productName;
    }

    public void setProductName(StringFilter productName) {
        this.productName = productName;
    }

    public StringFilter getProductImage() {
        return productImage;
    }

    public Optional<StringFilter> optionalProductImage() {
        return Optional.ofNullable(productImage);
    }

    public StringFilter productImage() {
        if (productImage == null) {
            setProductImage(new StringFilter());
        }
        return productImage;
    }

    public void setProductImage(StringFilter productImage) {
        this.productImage = productImage;
    }

    public StringFilter getItemSize() {
        return itemSize;
    }

    public Optional<StringFilter> optionalItemSize() {
        return Optional.ofNullable(itemSize);
    }

    public StringFilter itemSize() {
        if (itemSize == null) {
            setItemSize(new StringFilter());
        }
        return itemSize;
    }

    public void setItemSize(StringFilter itemSize) {
        this.itemSize = itemSize;
    }

    public StringFilter getColor() {
        return color;
    }

    public Optional<StringFilter> optionalColor() {
        return Optional.ofNullable(color);
    }

    public StringFilter color() {
        if (color == null) {
            setColor(new StringFilter());
        }
        return color;
    }

    public void setColor(StringFilter color) {
        this.color = color;
    }

    public IntegerFilter getQty() {
        return qty;
    }

    public Optional<IntegerFilter> optionalQty() {
        return Optional.ofNullable(qty);
    }

    public IntegerFilter qty() {
        if (qty == null) {
            setQty(new IntegerFilter());
        }
        return qty;
    }

    public void setQty(IntegerFilter qty) {
        this.qty = qty;
    }

    public BigDecimalFilter getPrice() {
        return price;
    }

    public Optional<BigDecimalFilter> optionalPrice() {
        return Optional.ofNullable(price);
    }

    public BigDecimalFilter price() {
        if (price == null) {
            setPrice(new BigDecimalFilter());
        }
        return price;
    }

    public void setPrice(BigDecimalFilter price) {
        this.price = price;
    }

    public LongFilter getOrderId() {
        return orderId;
    }

    public Optional<LongFilter> optionalOrderId() {
        return Optional.ofNullable(orderId);
    }

    public LongFilter orderId() {
        if (orderId == null) {
            setOrderId(new LongFilter());
        }
        return orderId;
    }

    public void setOrderId(LongFilter orderId) {
        this.orderId = orderId;
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
        final OrderItemCriteria that = (OrderItemCriteria) o;
        return (
            Objects.equals(id, that.id) &&
            Objects.equals(productId, that.productId) &&
            Objects.equals(productSlug, that.productSlug) &&
            Objects.equals(productName, that.productName) &&
            Objects.equals(productImage, that.productImage) &&
            Objects.equals(itemSize, that.itemSize) &&
            Objects.equals(color, that.color) &&
            Objects.equals(qty, that.qty) &&
            Objects.equals(price, that.price) &&
            Objects.equals(orderId, that.orderId) &&
            Objects.equals(distinct, that.distinct)
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, productId, productSlug, productName, productImage, itemSize, color, qty, price, orderId, distinct);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "OrderItemCriteria{" +
            optionalId().map(f -> "id=" + f + ", ").orElse("") +
            optionalProductId().map(f -> "productId=" + f + ", ").orElse("") +
            optionalProductSlug().map(f -> "productSlug=" + f + ", ").orElse("") +
            optionalProductName().map(f -> "productName=" + f + ", ").orElse("") +
            optionalProductImage().map(f -> "productImage=" + f + ", ").orElse("") +
            optionalItemSize().map(f -> "itemSize=" + f + ", ").orElse("") +
            optionalColor().map(f -> "color=" + f + ", ").orElse("") +
            optionalQty().map(f -> "qty=" + f + ", ").orElse("") +
            optionalPrice().map(f -> "price=" + f + ", ").orElse("") +
            optionalOrderId().map(f -> "orderId=" + f + ", ").orElse("") +
            optionalDistinct().map(f -> "distinct=" + f + ", ").orElse("") +
        "}";
    }
}
