package com.miniandmore.app.service.dto;

import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * A DTO for the {@link com.miniandmore.app.domain.OrderItem} entity.
 */
@SuppressWarnings("common-java:DuplicatedBlocks")
public class OrderItemDTO implements Serializable {

    private Long id;

    @NotNull
    @Size(max = 120)
    private String productId;

    @Size(max = 180)
    private String productSlug;

    @NotNull
    @Size(min = 1, max = 200)
    private String productName;

    @Size(max = 600)
    private String productImage;

    @Size(max = 40)
    private String itemSize;

    @Size(max = 40)
    private String color;

    @Size(max = 120)
    private String variantSku;

    @NotNull
    @Min(value = 1)
    private Integer qty;

    @NotNull
    @DecimalMin(value = "0")
    private BigDecimal price;

    @DecimalMin(value = "0")
    private BigDecimal mrp;

    @Size(max = 20)
    private String hsn;

    @DecimalMin(value = "0")
    @DecimalMax(value = "100")
    private BigDecimal taxPct;

    @NotNull
    private CustomerOrderDTO order;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public String getProductSlug() {
        return productSlug;
    }

    public void setProductSlug(String productSlug) {
        this.productSlug = productSlug;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public String getProductImage() {
        return productImage;
    }

    public void setProductImage(String productImage) {
        this.productImage = productImage;
    }

    public String getItemSize() {
        return itemSize;
    }

    public void setItemSize(String itemSize) {
        this.itemSize = itemSize;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public String getVariantSku() {
        return variantSku;
    }

    public void setVariantSku(String variantSku) {
        this.variantSku = variantSku;
    }

    public Integer getQty() {
        return qty;
    }

    public void setQty(Integer qty) {
        this.qty = qty;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public BigDecimal getMrp() {
        return mrp;
    }

    public void setMrp(BigDecimal mrp) {
        this.mrp = mrp;
    }

    public String getHsn() {
        return hsn;
    }

    public void setHsn(String hsn) {
        this.hsn = hsn;
    }

    public BigDecimal getTaxPct() {
        return taxPct;
    }

    public void setTaxPct(BigDecimal taxPct) {
        this.taxPct = taxPct;
    }

    public CustomerOrderDTO getOrder() {
        return order;
    }

    public void setOrder(CustomerOrderDTO order) {
        this.order = order;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof OrderItemDTO)) {
            return false;
        }

        OrderItemDTO orderItemDTO = (OrderItemDTO) o;
        if (this.id == null) {
            return false;
        }
        return Objects.equals(this.id, orderItemDTO.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "OrderItemDTO{" +
            "id=" + getId() +
            ", productId='" + getProductId() + "'" +
            ", productSlug='" + getProductSlug() + "'" +
            ", productName='" + getProductName() + "'" +
            ", productImage='" + getProductImage() + "'" +
            ", itemSize='" + getItemSize() + "'" +
            ", color='" + getColor() + "'" +
            ", qty=" + getQty() +
            ", price=" + getPrice() +
            ", mrp=" + getMrp() +
            ", hsn='" + getHsn() + "'" +
            ", taxPct=" + getTaxPct() +
            ", order=" + getOrder() +
            "}";
    }
}
