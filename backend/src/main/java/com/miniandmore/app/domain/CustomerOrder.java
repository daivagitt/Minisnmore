package com.miniandmore.app.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.miniandmore.app.domain.enumeration.OrderStatus;
import com.miniandmore.app.domain.enumeration.PaymentStatus;
import com.miniandmore.app.domain.enumeration.ZohoSyncStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * A CustomerOrder.
 */
@Entity
@Table(name = "customer_order")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class CustomerOrder implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    @Column(name = "id")
    private Long id;

    @NotNull
    @Size(min = 1, max = 50)
    @Column(name = "user_login", length = 50, nullable = false)
    private String userLogin;

    @NotNull
    @Size(min = 3, max = 40)
    @Column(name = "order_number", length = 40, nullable = false, unique = true)
    private String orderNumber;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OrderStatus status;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false)
    private PaymentStatus paymentStatus;

    @Size(max = 120)
    @Column(name = "razorpay_order_id", length = 120)
    private String razorpayOrderId;

    @Size(max = 120)
    @Column(name = "razorpay_payment_id", length = 120)
    private String razorpayPaymentId;

    @Size(max = 500)
    @Column(name = "razorpay_signature", length = 500)
    private String razorpaySignature;

    @NotNull
    @DecimalMin(value = "0")
    @Column(name = "subtotal", precision = 21, scale = 2, nullable = false)
    private BigDecimal subtotal;

    @NotNull
    @DecimalMin(value = "0")
    @Column(name = "discount", precision = 21, scale = 2, nullable = false)
    private BigDecimal discount;

    @NotNull
    @DecimalMin(value = "0")
    @Column(name = "shipping", precision = 21, scale = 2, nullable = false)
    private BigDecimal shipping;

    @NotNull
    @DecimalMin(value = "0")
    @Column(name = "tax", precision = 21, scale = 2, nullable = false)
    private BigDecimal tax;

    @NotNull
    @DecimalMin(value = "0")
    @Column(name = "donation", precision = 21, scale = 2, nullable = false)
    private BigDecimal donation;

    @NotNull
    @DecimalMin(value = "0")
    @Column(name = "total", precision = 21, scale = 2, nullable = false)
    private BigDecimal total;

    @Size(max = 64)
    @Column(name = "coupon_code", length = 64)
    private String couponCode;

    @Column(name = "shipping_address_json", columnDefinition = "text", nullable = false)
    private String shippingAddressJson;

    @Size(max = 500)
    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "inventory_reserved")
    private Boolean inventoryReserved;

    @Column(name = "inventory_reserved_until")
    private Instant inventoryReservedUntil;

    @Column(name = "inventory_released_at")
    private Instant inventoryReleasedAt;

    @Size(max = 120)
    @Column(name = "ledgerly_sales_order_id", length = 120, unique = true)
    private String zohoSalesOrderId;

    @Size(max = 120)
    @Column(name = "ledgerly_invoice_id", length = 120)
    private String zohoInvoiceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "ledgerly_sync_status")
    private ZohoSyncStatus zohoSyncStatus;

    @Column(name = "ledgerly_last_synced_at")
    private Instant zohoLastSyncedAt;

    @Size(max = 1000)
    @Column(name = "ledgerly_sync_error", length = 1000)
    private String zohoSyncError;

    @Size(max = 40)
    @Column(name = "ledgerly_order_status", length = 40)
    private String zohoOrderStatus;

    @Size(max = 120)
    @Column(name = "tracking_number", length = 120)
    private String trackingNumber;

    @Size(max = 120)
    @Column(name = "tracking_carrier", length = 120)
    private String trackingCarrier;

    @Size(max = 20)
    @Column(name = "shipment_date", length = 20)
    private String shipmentDate;

    @Column(name = "ledgerly_tracking_last_pulled_at")
    private Instant zohoTrackingLastPulledAt;

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "order")
    @JsonIgnoreProperties(value = { "order" }, allowSetters = true)
    private Set<OrderItem> itemses = new HashSet<>();

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public Long getId() {
        return this.id;
    }

    public CustomerOrder id(Long id) {
        this.setId(id);
        return this;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getOrderNumber() {
        return this.orderNumber;
    }

    public CustomerOrder orderNumber(String orderNumber) {
        this.setOrderNumber(orderNumber);
        return this;
    }

    public void setOrderNumber(String orderNumber) {
        this.orderNumber = orderNumber;
    }

    public OrderStatus getStatus() {
        return this.status;
    }

    public CustomerOrder status(OrderStatus status) {
        this.setStatus(status);
        return this;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public PaymentStatus getPaymentStatus() {
        return this.paymentStatus;
    }

    public CustomerOrder paymentStatus(PaymentStatus paymentStatus) {
        this.setPaymentStatus(paymentStatus);
        return this;
    }

    public void setPaymentStatus(PaymentStatus paymentStatus) {
        this.paymentStatus = paymentStatus;
    }

    public String getRazorpayOrderId() {
        return this.razorpayOrderId;
    }

    public CustomerOrder razorpayOrderId(String razorpayOrderId) {
        this.setRazorpayOrderId(razorpayOrderId);
        return this;
    }

    public void setRazorpayOrderId(String razorpayOrderId) {
        this.razorpayOrderId = razorpayOrderId;
    }

    public String getRazorpayPaymentId() {
        return this.razorpayPaymentId;
    }

    public CustomerOrder razorpayPaymentId(String razorpayPaymentId) {
        this.setRazorpayPaymentId(razorpayPaymentId);
        return this;
    }

    public void setRazorpayPaymentId(String razorpayPaymentId) {
        this.razorpayPaymentId = razorpayPaymentId;
    }

    public String getRazorpaySignature() {
        return this.razorpaySignature;
    }

    public CustomerOrder razorpaySignature(String razorpaySignature) {
        this.setRazorpaySignature(razorpaySignature);
        return this;
    }

    public void setRazorpaySignature(String razorpaySignature) {
        this.razorpaySignature = razorpaySignature;
    }

    public BigDecimal getSubtotal() {
        return this.subtotal;
    }

    public CustomerOrder subtotal(BigDecimal subtotal) {
        this.setSubtotal(subtotal);
        return this;
    }

    public void setSubtotal(BigDecimal subtotal) {
        this.subtotal = subtotal;
    }

    public BigDecimal getDiscount() {
        return this.discount;
    }

    public CustomerOrder discount(BigDecimal discount) {
        this.setDiscount(discount);
        return this;
    }

    public void setDiscount(BigDecimal discount) {
        this.discount = discount;
    }

    public BigDecimal getShipping() {
        return this.shipping;
    }

    public CustomerOrder shipping(BigDecimal shipping) {
        this.setShipping(shipping);
        return this;
    }

    public void setShipping(BigDecimal shipping) {
        this.shipping = shipping;
    }

    public BigDecimal getTax() {
        return this.tax;
    }

    public CustomerOrder tax(BigDecimal tax) {
        this.setTax(tax);
        return this;
    }

    public void setTax(BigDecimal tax) {
        this.tax = tax;
    }

    public BigDecimal getDonation() {
        return this.donation;
    }

    public CustomerOrder donation(BigDecimal donation) {
        this.setDonation(donation);
        return this;
    }

    public void setDonation(BigDecimal donation) {
        this.donation = donation;
    }

    public BigDecimal getTotal() {
        return this.total;
    }

    public CustomerOrder total(BigDecimal total) {
        this.setTotal(total);
        return this;
    }

    public void setTotal(BigDecimal total) {
        this.total = total;
    }

    public String getCouponCode() {
        return this.couponCode;
    }

    public CustomerOrder couponCode(String couponCode) {
        this.setCouponCode(couponCode);
        return this;
    }

    public void setCouponCode(String couponCode) {
        this.couponCode = couponCode;
    }

    public String getShippingAddressJson() {
        return this.shippingAddressJson;
    }

    public CustomerOrder shippingAddressJson(String shippingAddressJson) {
        this.setShippingAddressJson(shippingAddressJson);
        return this;
    }

    public void setShippingAddressJson(String shippingAddressJson) {
        this.shippingAddressJson = shippingAddressJson;
    }

    public String getNotes() {
        return this.notes;
    }

    public CustomerOrder notes(String notes) {
        this.setNotes(notes);
        return this;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Boolean getInventoryReserved() {
        return this.inventoryReserved;
    }

    public CustomerOrder inventoryReserved(Boolean inventoryReserved) {
        this.setInventoryReserved(inventoryReserved);
        return this;
    }

    public void setInventoryReserved(Boolean inventoryReserved) {
        this.inventoryReserved = inventoryReserved;
    }

    public Instant getInventoryReservedUntil() {
        return this.inventoryReservedUntil;
    }

    public CustomerOrder inventoryReservedUntil(Instant inventoryReservedUntil) {
        this.setInventoryReservedUntil(inventoryReservedUntil);
        return this;
    }

    public void setInventoryReservedUntil(Instant inventoryReservedUntil) {
        this.inventoryReservedUntil = inventoryReservedUntil;
    }

    public Instant getInventoryReleasedAt() {
        return this.inventoryReleasedAt;
    }

    public CustomerOrder inventoryReleasedAt(Instant inventoryReleasedAt) {
        this.setInventoryReleasedAt(inventoryReleasedAt);
        return this;
    }

    public void setInventoryReleasedAt(Instant inventoryReleasedAt) {
        this.inventoryReleasedAt = inventoryReleasedAt;
    }

    public String getZohoSalesOrderId() {
        return this.zohoSalesOrderId;
    }

    public CustomerOrder zohoSalesOrderId(String zohoSalesOrderId) {
        this.setZohoSalesOrderId(zohoSalesOrderId);
        return this;
    }

    public void setZohoSalesOrderId(String zohoSalesOrderId) {
        this.zohoSalesOrderId = zohoSalesOrderId;
    }

    public String getZohoInvoiceId() {
        return this.zohoInvoiceId;
    }

    public CustomerOrder zohoInvoiceId(String zohoInvoiceId) {
        this.setZohoInvoiceId(zohoInvoiceId);
        return this;
    }

    public void setZohoInvoiceId(String zohoInvoiceId) {
        this.zohoInvoiceId = zohoInvoiceId;
    }

    public ZohoSyncStatus getZohoSyncStatus() {
        return this.zohoSyncStatus;
    }

    public CustomerOrder zohoSyncStatus(ZohoSyncStatus zohoSyncStatus) {
        this.setZohoSyncStatus(zohoSyncStatus);
        return this;
    }

    public void setZohoSyncStatus(ZohoSyncStatus zohoSyncStatus) {
        this.zohoSyncStatus = zohoSyncStatus;
    }

    public Instant getZohoLastSyncedAt() {
        return this.zohoLastSyncedAt;
    }

    public CustomerOrder zohoLastSyncedAt(Instant zohoLastSyncedAt) {
        this.setZohoLastSyncedAt(zohoLastSyncedAt);
        return this;
    }

    public void setZohoLastSyncedAt(Instant zohoLastSyncedAt) {
        this.zohoLastSyncedAt = zohoLastSyncedAt;
    }

    public String getZohoSyncError() {
        return this.zohoSyncError;
    }

    public CustomerOrder zohoSyncError(String zohoSyncError) {
        this.setZohoSyncError(zohoSyncError);
        return this;
    }

    public void setZohoSyncError(String zohoSyncError) {
        this.zohoSyncError = zohoSyncError;
    }

    public String getZohoOrderStatus() {
        return this.zohoOrderStatus;
    }

    public CustomerOrder zohoOrderStatus(String zohoOrderStatus) {
        this.setZohoOrderStatus(zohoOrderStatus);
        return this;
    }

    public void setZohoOrderStatus(String zohoOrderStatus) {
        this.zohoOrderStatus = zohoOrderStatus;
    }

    public String getTrackingNumber() {
        return this.trackingNumber;
    }

    public CustomerOrder trackingNumber(String trackingNumber) {
        this.setTrackingNumber(trackingNumber);
        return this;
    }

    public void setTrackingNumber(String trackingNumber) {
        this.trackingNumber = trackingNumber;
    }

    public String getTrackingCarrier() {
        return this.trackingCarrier;
    }

    public CustomerOrder trackingCarrier(String trackingCarrier) {
        this.setTrackingCarrier(trackingCarrier);
        return this;
    }

    public void setTrackingCarrier(String trackingCarrier) {
        this.trackingCarrier = trackingCarrier;
    }

    public String getShipmentDate() {
        return this.shipmentDate;
    }

    public CustomerOrder shipmentDate(String shipmentDate) {
        this.setShipmentDate(shipmentDate);
        return this;
    }

    public void setShipmentDate(String shipmentDate) {
        this.shipmentDate = shipmentDate;
    }

    public Instant getZohoTrackingLastPulledAt() {
        return this.zohoTrackingLastPulledAt;
    }

    public CustomerOrder zohoTrackingLastPulledAt(Instant zohoTrackingLastPulledAt) {
        this.setZohoTrackingLastPulledAt(zohoTrackingLastPulledAt);
        return this;
    }

    public void setZohoTrackingLastPulledAt(Instant zohoTrackingLastPulledAt) {
        this.zohoTrackingLastPulledAt = zohoTrackingLastPulledAt;
    }

    public Set<OrderItem> getItemses() {
        return this.itemses;
    }

    public void setItemses(Set<OrderItem> orderItems) {
        if (this.itemses != null) {
            this.itemses.forEach(i -> i.setOrder(null));
        }
        if (orderItems != null) {
            orderItems.forEach(i -> i.setOrder(this));
        }
        this.itemses = orderItems;
    }

    public CustomerOrder itemses(Set<OrderItem> orderItems) {
        this.setItemses(orderItems);
        return this;
    }

    public CustomerOrder addItems(OrderItem orderItem) {
        this.itemses.add(orderItem);
        orderItem.setOrder(this);
        return this;
    }

    public CustomerOrder removeItems(OrderItem orderItem) {
        this.itemses.remove(orderItem);
        orderItem.setOrder(null);
        return this;
    }

    public String getUserLogin() {
        return this.userLogin;
    }

    public void setUserLogin(String userLogin) {
        this.userLogin = userLogin;
    }

    public CustomerOrder userLogin(String userLogin) {
        this.setUserLogin(userLogin);
        return this;
    }


    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CustomerOrder)) {
            return false;
        }
        return getId() != null && getId().equals(((CustomerOrder) o).getId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "CustomerOrder{" +
            "id=" + getId() +
            ", orderNumber='" + getOrderNumber() + "'" +
            ", status='" + getStatus() + "'" +
            ", paymentStatus='" + getPaymentStatus() + "'" +
            ", subtotal=" + getSubtotal() +
            ", discount=" + getDiscount() +
            ", shipping=" + getShipping() +
            ", tax=" + getTax() +
            ", donation=" + getDonation() +
            ", total=" + getTotal() +
            ", couponCode='" + getCouponCode() + "'" +
            "}";
    }
}
