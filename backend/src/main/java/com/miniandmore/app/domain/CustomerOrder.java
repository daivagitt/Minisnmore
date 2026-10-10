package com.miniandmore.app.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.miniandmore.app.domain.enumeration.OrderStatus;
import com.miniandmore.app.domain.enumeration.PackageType;
import com.miniandmore.app.domain.enumeration.PaymentStatus;
import com.miniandmore.app.domain.enumeration.LedgerlySyncStatus;
import com.miniandmore.app.domain.enumeration.ReturnLogisticsMode;
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

    @NotNull
    @Size(min = 1, max = 12)
    @Column(name = "payment_method", length = 12, nullable = false)
    private String paymentMethod;

    @NotNull
    @DecimalMin(value = "0")
    @Column(name = "cod_fee", precision = 21, scale = 2, nullable = false)
    private BigDecimal codFee;

    @Column(name = "shipping_address_json", columnDefinition = "text", nullable = false)
    private String shippingAddressJson;

    @Size(max = 500)
    @Column(name = "notes", length = 500)
    private String notes;

    @Size(max = 500)
    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    @Column(name = "inventory_reserved")
    private Boolean inventoryReserved;

    @Column(name = "inventory_reserved_until")
    private Instant inventoryReservedUntil;

    @Column(name = "inventory_released_at")
    private Instant inventoryReleasedAt;

    @Column(name = "placed_at")
    private Instant placedAt;

    @Size(max = 120)
    @Column(name = "zoho_sales_order_id", length = 120, unique = true)
    private String zohoSalesOrderId;

    @Size(max = 120)
    @Column(name = "zoho_invoice_id", length = 120)
    private String zohoInvoiceId;

    @Size(max = 120)
    @Column(name = "zoho_credit_note_id", length = 120)
    private String zohoCreditNoteId;

    @Size(max = 600)
    @Column(name = "zoho_invoice_pdf_url", length = 600)
    private String zohoInvoicePdfUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "zoho_sync_status")
    private ZohoSyncStatus zohoSyncStatus;

    @Column(name = "zoho_last_synced_at")
    private Instant zohoLastSyncedAt;

    @Size(max = 1000)
    @Column(name = "zoho_sync_error", length = 1000)
    private String zohoSyncError;

    @Size(max = 40)
    @Column(name = "zoho_order_status", length = 40)
    private String zohoOrderStatus;

    @Column(name = "zoho_tracking_last_pulled_at")
    private Instant zohoTrackingLastPulledAt;

    @Size(max = 120)
    @Column(name = "ledgerly_sales_order_id", length = 120, unique = true)
    private String ledgerlySalesOrderId;

    @Size(max = 120)
    @Column(name = "ledgerly_invoice_id", length = 120)
    private String ledgerlyInvoiceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "ledgerly_sync_status")
    private LedgerlySyncStatus ledgerlySyncStatus;

    @Column(name = "ledgerly_last_synced_at")
    private Instant ledgerlyLastSyncedAt;

    @Size(max = 1000)
    @Column(name = "ledgerly_sync_error", length = 1000)
    private String ledgerlySyncError;

    @Size(max = 40)
    @Column(name = "ledgerly_order_status", length = 40)
    private String ledgerlyOrderStatus;

    @Size(max = 120)
    @Column(name = "tracking_number", length = 120)
    private String trackingNumber;

    @Size(max = 120)
    @Column(name = "tracking_carrier", length = 120)
    private String trackingCarrier;

    @Size(max = 20)
    @Column(name = "shipment_date", length = 20)
    private String shipmentDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "shipment_logistics_mode", length = 16)
    private ReturnLogisticsMode shipmentLogisticsMode;

    @Size(max = 500)
    @Column(name = "tracking_url", length = 500)
    private String trackingUrl;

    @Size(max = 500)
    @Column(name = "shipment_label_url", length = 500)
    private String shipmentLabelUrl;

    @Size(max = 120)
    @Column(name = "shipment_provider_request_id", length = 120)
    private String shipmentProviderRequestId;

    @Size(max = 80)
    @Column(name = "shipment_carrier_status", length = 80)
    private String shipmentCarrierStatus;

    @Column(name = "shipment_delivered_at")
    private Instant shipmentDeliveredAt;

    @Column(name = "shipment_cancelled_at")
    private Instant shipmentCancelledAt;

    @Size(max = 50)
    @Column(name = "shipment_cancelled_by", length = 50)
    private String shipmentCancelledBy;

    @Size(max = 500)
    @Column(name = "shipment_cancellation_reason", length = 500)
    private String shipmentCancellationReason;

    @Column(name = "package_weight_grams")
    private Integer packageWeightGrams;

    @Column(name = "package_length_cm")
    private Integer packageLengthCm;

    @Column(name = "package_breadth_cm")
    private Integer packageBreadthCm;

    @Column(name = "package_height_cm")
    private Integer packageHeightCm;

    @Column(name = "package_chargeable_weight_grams")
    private Integer packageChargeableWeightGrams;

    @Size(max = 40)
    @Column(name = "package_courier_id", length = 40)
    private String packageCourierId;

    @Size(max = 120)
    @Column(name = "package_courier_name", length = 120)
    private String packageCourierName;

    @Enumerated(EnumType.STRING)
    @Column(name = "package_type", length = 20)
    private PackageType packageType;

    @Column(name = "package_box_id")
    private Long packageBoxId;

    @Column(name = "packed_at")
    private Instant packedAt;

    @Column(name = "ledgerly_tracking_last_pulled_at")
    private Instant ledgerlyTrackingLastPulledAt;

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

    public String getPaymentMethod() {
        return this.paymentMethod;
    }

    public CustomerOrder paymentMethod(String paymentMethod) {
        this.setPaymentMethod(paymentMethod);
        return this;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public BigDecimal getCodFee() {
        return this.codFee;
    }

    public CustomerOrder codFee(BigDecimal codFee) {
        this.setCodFee(codFee);
        return this;
    }

    public void setCodFee(BigDecimal codFee) {
        this.codFee = codFee;
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

    public String getCancellationReason() {
        return this.cancellationReason;
    }

    public CustomerOrder cancellationReason(String cancellationReason) {
        this.setCancellationReason(cancellationReason);
        return this;
    }

    public void setCancellationReason(String cancellationReason) {
        this.cancellationReason = cancellationReason;
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

    public Instant getPlacedAt() {
        return this.placedAt;
    }

    public CustomerOrder placedAt(Instant placedAt) {
        this.setPlacedAt(placedAt);
        return this;
    }

    public void setPlacedAt(Instant placedAt) {
        this.placedAt = placedAt;
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

    public String getZohoCreditNoteId() {
        return this.zohoCreditNoteId;
    }

    public CustomerOrder zohoCreditNoteId(String zohoCreditNoteId) {
        this.setZohoCreditNoteId(zohoCreditNoteId);
        return this;
    }

    public void setZohoCreditNoteId(String zohoCreditNoteId) {
        this.zohoCreditNoteId = zohoCreditNoteId;
    }

    public String getZohoInvoicePdfUrl() {
        return this.zohoInvoicePdfUrl;
    }

    public CustomerOrder zohoInvoicePdfUrl(String zohoInvoicePdfUrl) {
        this.setZohoInvoicePdfUrl(zohoInvoicePdfUrl);
        return this;
    }

    public void setZohoInvoicePdfUrl(String zohoInvoicePdfUrl) {
        this.zohoInvoicePdfUrl = zohoInvoicePdfUrl;
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

    public String getLedgerlySalesOrderId() {
        return this.ledgerlySalesOrderId;
    }

    public CustomerOrder ledgerlySalesOrderId(String ledgerlySalesOrderId) {
        this.setLedgerlySalesOrderId(ledgerlySalesOrderId);
        return this;
    }

    public void setLedgerlySalesOrderId(String ledgerlySalesOrderId) {
        this.ledgerlySalesOrderId = ledgerlySalesOrderId;
    }

    public String getLedgerlyInvoiceId() {
        return this.ledgerlyInvoiceId;
    }

    public CustomerOrder ledgerlyInvoiceId(String ledgerlyInvoiceId) {
        this.setLedgerlyInvoiceId(ledgerlyInvoiceId);
        return this;
    }

    public void setLedgerlyInvoiceId(String ledgerlyInvoiceId) {
        this.ledgerlyInvoiceId = ledgerlyInvoiceId;
    }

    public LedgerlySyncStatus getLedgerlySyncStatus() {
        return this.ledgerlySyncStatus;
    }

    public CustomerOrder ledgerlySyncStatus(LedgerlySyncStatus ledgerlySyncStatus) {
        this.setLedgerlySyncStatus(ledgerlySyncStatus);
        return this;
    }

    public void setLedgerlySyncStatus(LedgerlySyncStatus ledgerlySyncStatus) {
        this.ledgerlySyncStatus = ledgerlySyncStatus;
    }

    public Instant getLedgerlyLastSyncedAt() {
        return this.ledgerlyLastSyncedAt;
    }

    public CustomerOrder ledgerlyLastSyncedAt(Instant ledgerlyLastSyncedAt) {
        this.setLedgerlyLastSyncedAt(ledgerlyLastSyncedAt);
        return this;
    }

    public void setLedgerlyLastSyncedAt(Instant ledgerlyLastSyncedAt) {
        this.ledgerlyLastSyncedAt = ledgerlyLastSyncedAt;
    }

    public String getLedgerlySyncError() {
        return this.ledgerlySyncError;
    }

    public CustomerOrder ledgerlySyncError(String ledgerlySyncError) {
        this.setLedgerlySyncError(ledgerlySyncError);
        return this;
    }

    public void setLedgerlySyncError(String ledgerlySyncError) {
        this.ledgerlySyncError = ledgerlySyncError;
    }

    public String getLedgerlyOrderStatus() {
        return this.ledgerlyOrderStatus;
    }

    public CustomerOrder ledgerlyOrderStatus(String ledgerlyOrderStatus) {
        this.setLedgerlyOrderStatus(ledgerlyOrderStatus);
        return this;
    }

    public void setLedgerlyOrderStatus(String ledgerlyOrderStatus) {
        this.ledgerlyOrderStatus = ledgerlyOrderStatus;
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

    public ReturnLogisticsMode getShipmentLogisticsMode() {
        return this.shipmentLogisticsMode;
    }

    public CustomerOrder shipmentLogisticsMode(ReturnLogisticsMode shipmentLogisticsMode) {
        this.setShipmentLogisticsMode(shipmentLogisticsMode);
        return this;
    }

    public void setShipmentLogisticsMode(ReturnLogisticsMode shipmentLogisticsMode) {
        this.shipmentLogisticsMode = shipmentLogisticsMode;
    }

    public String getTrackingUrl() {
        return this.trackingUrl;
    }

    public CustomerOrder trackingUrl(String trackingUrl) {
        this.setTrackingUrl(trackingUrl);
        return this;
    }

    public void setTrackingUrl(String trackingUrl) {
        this.trackingUrl = trackingUrl;
    }

    public String getShipmentLabelUrl() {
        return this.shipmentLabelUrl;
    }

    public CustomerOrder shipmentLabelUrl(String shipmentLabelUrl) {
        this.setShipmentLabelUrl(shipmentLabelUrl);
        return this;
    }

    public void setShipmentLabelUrl(String shipmentLabelUrl) {
        this.shipmentLabelUrl = shipmentLabelUrl;
    }

    public String getShipmentProviderRequestId() {
        return this.shipmentProviderRequestId;
    }

    public CustomerOrder shipmentProviderRequestId(String shipmentProviderRequestId) {
        this.setShipmentProviderRequestId(shipmentProviderRequestId);
        return this;
    }

    public void setShipmentProviderRequestId(String shipmentProviderRequestId) {
        this.shipmentProviderRequestId = shipmentProviderRequestId;
    }

    public String getShipmentCarrierStatus() {
        return shipmentCarrierStatus;
    }

    public CustomerOrder shipmentCarrierStatus(String shipmentCarrierStatus) {
        this.setShipmentCarrierStatus(shipmentCarrierStatus);
        return this;
    }

    public void setShipmentCarrierStatus(String shipmentCarrierStatus) {
        this.shipmentCarrierStatus = shipmentCarrierStatus;
    }

    public Instant getShipmentDeliveredAt() {
        return shipmentDeliveredAt;
    }

    public CustomerOrder shipmentDeliveredAt(Instant shipmentDeliveredAt) {
        this.setShipmentDeliveredAt(shipmentDeliveredAt);
        return this;
    }

    public void setShipmentDeliveredAt(Instant shipmentDeliveredAt) {
        this.shipmentDeliveredAt = shipmentDeliveredAt;
    }

    public Instant getShipmentCancelledAt() {
        return shipmentCancelledAt;
    }

    public void setShipmentCancelledAt(Instant shipmentCancelledAt) {
        this.shipmentCancelledAt = shipmentCancelledAt;
    }

    public String getShipmentCancelledBy() {
        return shipmentCancelledBy;
    }

    public void setShipmentCancelledBy(String shipmentCancelledBy) {
        this.shipmentCancelledBy = shipmentCancelledBy;
    }

    public String getShipmentCancellationReason() {
        return shipmentCancellationReason;
    }

    public void setShipmentCancellationReason(String shipmentCancellationReason) {
        this.shipmentCancellationReason = shipmentCancellationReason;
    }

    public Integer getPackageWeightGrams() {
        return packageWeightGrams;
    }

    public CustomerOrder packageWeightGrams(Integer packageWeightGrams) {
        this.setPackageWeightGrams(packageWeightGrams);
        return this;
    }

    public void setPackageWeightGrams(Integer packageWeightGrams) {
        this.packageWeightGrams = packageWeightGrams;
    }

    public Integer getPackageLengthCm() {
        return packageLengthCm;
    }

    public CustomerOrder packageLengthCm(Integer packageLengthCm) {
        this.setPackageLengthCm(packageLengthCm);
        return this;
    }

    public void setPackageLengthCm(Integer packageLengthCm) {
        this.packageLengthCm = packageLengthCm;
    }

    public Integer getPackageBreadthCm() {
        return packageBreadthCm;
    }

    public CustomerOrder packageBreadthCm(Integer packageBreadthCm) {
        this.setPackageBreadthCm(packageBreadthCm);
        return this;
    }

    public void setPackageBreadthCm(Integer packageBreadthCm) {
        this.packageBreadthCm = packageBreadthCm;
    }

    public Integer getPackageHeightCm() {
        return packageHeightCm;
    }

    public CustomerOrder packageHeightCm(Integer packageHeightCm) {
        this.setPackageHeightCm(packageHeightCm);
        return this;
    }

    public void setPackageHeightCm(Integer packageHeightCm) {
        this.packageHeightCm = packageHeightCm;
    }

    public Integer getPackageChargeableWeightGrams() {
        return packageChargeableWeightGrams;
    }

    public CustomerOrder packageChargeableWeightGrams(Integer packageChargeableWeightGrams) {
        this.setPackageChargeableWeightGrams(packageChargeableWeightGrams);
        return this;
    }

    public void setPackageChargeableWeightGrams(Integer packageChargeableWeightGrams) {
        this.packageChargeableWeightGrams = packageChargeableWeightGrams;
    }

    public String getPackageCourierId() {
        return packageCourierId;
    }

    public CustomerOrder packageCourierId(String packageCourierId) {
        this.setPackageCourierId(packageCourierId);
        return this;
    }

    public void setPackageCourierId(String packageCourierId) {
        this.packageCourierId = packageCourierId;
    }

    public String getPackageCourierName() {
        return packageCourierName;
    }

    public CustomerOrder packageCourierName(String packageCourierName) {
        this.setPackageCourierName(packageCourierName);
        return this;
    }

    public void setPackageCourierName(String packageCourierName) {
        this.packageCourierName = packageCourierName;
    }

    public PackageType getPackageType() {
        return packageType;
    }

    public CustomerOrder packageType(PackageType packageType) {
        this.setPackageType(packageType);
        return this;
    }

    public void setPackageType(PackageType packageType) {
        this.packageType = packageType;
    }

    public Long getPackageBoxId() {
        return packageBoxId;
    }

    public CustomerOrder packageBoxId(Long packageBoxId) {
        this.setPackageBoxId(packageBoxId);
        return this;
    }

    public void setPackageBoxId(Long packageBoxId) {
        this.packageBoxId = packageBoxId;
    }

    public Instant getPackedAt() {
        return packedAt;
    }

    public CustomerOrder packedAt(Instant packedAt) {
        this.setPackedAt(packedAt);
        return this;
    }

    public void setPackedAt(Instant packedAt) {
        this.packedAt = packedAt;
    }

    public Instant getLedgerlyTrackingLastPulledAt() {
        return this.ledgerlyTrackingLastPulledAt;
    }

    public CustomerOrder ledgerlyTrackingLastPulledAt(Instant ledgerlyTrackingLastPulledAt) {
        this.setLedgerlyTrackingLastPulledAt(ledgerlyTrackingLastPulledAt);
        return this;
    }

    public void setLedgerlyTrackingLastPulledAt(Instant ledgerlyTrackingLastPulledAt) {
        this.ledgerlyTrackingLastPulledAt = ledgerlyTrackingLastPulledAt;
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
            ", paymentMethod='" + getPaymentMethod() + "'" +
            ", codFee=" + getCodFee() +
            "}";
    }
}
