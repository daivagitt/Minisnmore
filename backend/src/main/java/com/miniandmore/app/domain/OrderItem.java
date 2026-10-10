package com.miniandmore.app.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * A OrderItem.
 */
@Entity
@Table(name = "order_item")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class OrderItem implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    @Column(name = "id")
    private Long id;

    @NotNull
    @Size(max = 120)
    @Column(name = "product_id", length = 120, nullable = false)
    private String productId;

    @Size(max = 180)
    @Column(name = "product_slug", length = 180)
    private String productSlug;

    @NotNull
    @Size(min = 1, max = 200)
    @Column(name = "product_name", length = 200, nullable = false)
    private String productName;

    @Size(max = 600)
    @Column(name = "product_image", length = 600)
    private String productImage;

    @Size(max = 40)
    @Column(name = "item_size", length = 40)
    private String itemSize;

    @Size(max = 40)
    @Column(name = "color", length = 40)
    private String color;

    @Size(max = 120)
    @Column(name = "variant_sku", length = 120)
    private String variantSku;

    @NotNull
    @Min(value = 1)
    @Column(name = "qty", nullable = false)
    private Integer qty;

    @NotNull
    @DecimalMin(value = "0")
    @Column(name = "price", precision = 21, scale = 2, nullable = false)
    private BigDecimal price;

    @DecimalMin(value = "0")
    @Column(name = "mrp", precision = 21, scale = 2)
    private BigDecimal mrp;

    @Size(max = 20)
    @Column(name = "hsn", length = 20)
    private String hsn;

    @DecimalMin(value = "0")
    @DecimalMax(value = "100")
    @Column(name = "tax_pct", precision = 5, scale = 2)
    private BigDecimal taxPct;

    @NotNull
    @DecimalMin(value = "0")
    @Column(name = "discount_allocation", precision = 21, scale = 2, nullable = false)
    private BigDecimal discountAllocation = BigDecimal.ZERO;

    @ManyToOne(optional = false)
    @NotNull
    @JsonIgnoreProperties(value = { "itemses" }, allowSetters = true)
    private CustomerOrder order;

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public Long getId() {
        return this.id;
    }

    public OrderItem id(Long id) {
        this.setId(id);
        return this;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getProductId() {
        return this.productId;
    }

    public OrderItem productId(String productId) {
        this.setProductId(productId);
        return this;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public String getProductSlug() {
        return this.productSlug;
    }

    public OrderItem productSlug(String productSlug) {
        this.setProductSlug(productSlug);
        return this;
    }

    public void setProductSlug(String productSlug) {
        this.productSlug = productSlug;
    }

    public String getProductName() {
        return this.productName;
    }

    public OrderItem productName(String productName) {
        this.setProductName(productName);
        return this;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public String getProductImage() {
        return this.productImage;
    }

    public OrderItem productImage(String productImage) {
        this.setProductImage(productImage);
        return this;
    }

    public void setProductImage(String productImage) {
        this.productImage = productImage;
    }

    public String getItemSize() {
        return this.itemSize;
    }

    public OrderItem itemSize(String itemSize) {
        this.setItemSize(itemSize);
        return this;
    }

    public void setItemSize(String itemSize) {
        this.itemSize = itemSize;
    }

    public String getColor() {
        return this.color;
    }

    public OrderItem color(String color) {
        this.setColor(color);
        return this;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public String getVariantSku() {
        return this.variantSku;
    }

    public OrderItem variantSku(String variantSku) {
        this.setVariantSku(variantSku);
        return this;
    }

    public void setVariantSku(String variantSku) {
        this.variantSku = variantSku;
    }

    public Integer getQty() {
        return this.qty;
    }

    public OrderItem qty(Integer qty) {
        this.setQty(qty);
        return this;
    }

    public void setQty(Integer qty) {
        this.qty = qty;
    }

    public BigDecimal getPrice() {
        return this.price;
    }

    public OrderItem price(BigDecimal price) {
        this.setPrice(price);
        return this;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public BigDecimal getMrp() {
        return this.mrp;
    }

    public OrderItem mrp(BigDecimal mrp) {
        this.setMrp(mrp);
        return this;
    }

    public void setMrp(BigDecimal mrp) {
        this.mrp = mrp;
    }

    public String getHsn() {
        return this.hsn;
    }

    public OrderItem hsn(String hsn) {
        this.setHsn(hsn);
        return this;
    }

    public void setHsn(String hsn) {
        this.hsn = hsn;
    }

    public BigDecimal getTaxPct() {
        return this.taxPct;
    }

    public OrderItem taxPct(BigDecimal taxPct) {
        this.setTaxPct(taxPct);
        return this;
    }

    public void setTaxPct(BigDecimal taxPct) {
        this.taxPct = taxPct;
    }

    public BigDecimal getDiscountAllocation() {
        return this.discountAllocation;
    }

    public OrderItem discountAllocation(BigDecimal discountAllocation) {
        this.setDiscountAllocation(discountAllocation);
        return this;
    }

    public void setDiscountAllocation(BigDecimal discountAllocation) {
        this.discountAllocation = discountAllocation == null ? BigDecimal.ZERO : discountAllocation;
    }

    public CustomerOrder getOrder() {
        return this.order;
    }

    public void setOrder(CustomerOrder customerOrder) {
        this.order = customerOrder;
    }

    public OrderItem order(CustomerOrder customerOrder) {
        this.setOrder(customerOrder);
        return this;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof OrderItem)) {
            return false;
        }
        return getId() != null && getId().equals(((OrderItem) o).getId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "OrderItem{" +
            "id=" + getId() +
            ", productId='" + getProductId() + "'" +
            ", productSlug='" + getProductSlug() + "'" +
            ", productName='" + getProductName() + "'" +
            ", productImage='" + getProductImage() + "'" +
            ", itemSize='" + getItemSize() + "'" +
            ", color='" + getColor() + "'" +
            ", variantSku='" + getVariantSku() + "'" +
            ", qty=" + getQty() +
            ", price=" + getPrice() +
            ", mrp=" + getMrp() +
            ", hsn='" + getHsn() + "'" +
            ", taxPct=" + getTaxPct() +
            ", discountAllocation=" + getDiscountAllocation() +
            "}";
    }
}
