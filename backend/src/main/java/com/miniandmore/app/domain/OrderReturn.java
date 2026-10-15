package com.miniandmore.app.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.miniandmore.app.domain.enumeration.ExchangeSettlementStatus;
import com.miniandmore.app.domain.enumeration.OrderRequestType;
import com.miniandmore.app.domain.enumeration.OrderReturnStatus;
import com.miniandmore.app.domain.enumeration.PackageType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "order_return")
public class OrderReturn implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    @Column(name = "id")
    private Long id;

    @NotNull
    @Size(min = 8, max = 48)
    @Column(name = "return_number", length = 48, nullable = false, unique = true)
    private String returnNumber;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 32, nullable = false)
    private OrderReturnStatus status;

    @NotNull
    @Column(name = "reason", columnDefinition = "text", nullable = false)
    private String reason;

    @Column(name = "image_urls_json", columnDefinition = "text")
    private String imageUrlsJson;

    @Size(max = 2000)
    @Column(name = "admin_notes", length = 2000)
    private String adminNotes;

    @Size(max = 1000)
    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;

    @Column(name = "refund_amount", precision = 21, scale = 2)
    private BigDecimal refundAmount;

    @Size(max = 120)
    @Column(name = "razorpay_refund_id", length = 120)
    private String razorpayRefundId;

    @Size(max = 120)
    @Column(name = "ledgerly_credit_note_id", length = 120)
    private String ledgerlyCreditNoteId;

    @Size(max = 120)
    @Column(name = "zoho_credit_note_id", length = 120)
    private String zohoCreditNoteId;

    @Size(max = 120)
    @Column(name = "zoho_replacement_invoice_id", length = 120)
    private String zohoReplacementInvoiceId;

    @NotNull
    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @ManyToOne(optional = false)
    @NotNull
    @JoinColumn(name = "customer_order_id", nullable = false)
    @JsonIgnoreProperties(value = { "itemses" }, allowSetters = true)
    private CustomerOrder order;

    @NotNull
    @Size(min = 1, max = 50)
    @Column(name = "user_login", length = 50, nullable = false)
    private String userLogin;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "request_type", length = 16, nullable = false)
    private OrderRequestType requestType = OrderRequestType.RETURN;

    @ManyToOne
    @JoinColumn(name = "order_item_id")
    @JsonIgnoreProperties(value = { "order" }, allowSetters = true)
    private OrderItem orderItem;

    @Size(max = 120)
    @Column(name = "replacement_variant_sku", length = 120)
    private String replacementVariantSku;

    @Size(max = 40)
    @Column(name = "replacement_size", length = 40)
    private String replacementSize;

    @Size(max = 40)
    @Column(name = "replacement_color", length = 40)
    private String replacementColor;

    @Enumerated(EnumType.STRING)
    @Column(name = "disposition", length = 32)
    private com.miniandmore.app.domain.enumeration.ReturnDisposition disposition;

    @Enumerated(EnumType.STRING)
    @Column(name = "logistics_mode", length = 16)
    private com.miniandmore.app.domain.enumeration.ReturnLogisticsMode logisticsMode;

    @Size(max = 80)
    @Column(name = "carrier_name", length = 80)
    private String carrierName;

    @Size(max = 80)
    @Column(name = "reverse_awb", length = 80)
    private String reverseAwb;

    @Size(max = 500)
    @Column(name = "tracking_url", length = 500)
    private String trackingUrl;

    @Size(max = 200)
    @Column(name = "pickup_window", length = 200)
    private String pickupWindow;

    @Size(max = 120)
    @Column(name = "provider_request_id", length = 120)
    private String providerRequestId;

    @Size(max = 500)
    @Column(name = "pickup_failure_reason", length = 500)
    private String pickupFailureReason;

    /** Latest ExpressBees reverse-pickup milestone. See {@code XpressBeesReturnCarrierStatus}. */
    @Size(max = 40)
    @Column(name = "reverse_carrier_status", length = 40)
    private String reverseCarrierStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "outbound_logistics_mode", length = 16)
    private com.miniandmore.app.domain.enumeration.ReturnLogisticsMode outboundLogisticsMode;

    @Size(max = 80)
    @Column(name = "outbound_carrier_name", length = 80)
    private String outboundCarrierName;

    @Size(max = 80)
    @Column(name = "outbound_awb", length = 80)
    private String outboundAwb;

    @Size(max = 500)
    @Column(name = "outbound_tracking_url", length = 500)
    private String outboundTrackingUrl;

    @Size(max = 500)
    @Column(name = "outbound_label_url", length = 500)
    private String outboundLabelUrl;

    @Size(max = 120)
    @Column(name = "outbound_provider_request_id", length = 120)
    private String outboundProviderRequestId;

    /** Latest ExpressBees replacement-shipment milestone. See {@code XpressBeesReturnCarrierStatus}. */
    @Size(max = 40)
    @Column(name = "outbound_carrier_status", length = 40)
    private String outboundCarrierStatus;

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

    @Column(name = "outbound_packed_qty")
    private Integer outboundPackedQty;

    @Column(name = "package_short_refund_amount", precision = 21, scale = 2)
    private BigDecimal packageShortRefundAmount;

    @Size(max = 80)
    @Column(name = "package_short_razorpay_refund_id", length = 80)
    private String packageShortRazorpayRefundId;

    @Column(name = "original_item_amount", precision = 21, scale = 2)
    private BigDecimal originalItemAmount;

    @Column(name = "replacement_item_amount", precision = 21, scale = 2)
    private BigDecimal replacementItemAmount;

    @Column(name = "price_difference", precision = 21, scale = 2)
    private BigDecimal priceDifference;

    @Enumerated(EnumType.STRING)
    @Column(name = "settlement_status", length = 20)
    private ExchangeSettlementStatus settlementStatus;

    @Size(max = 40)
    @Column(name = "settlement_method", length = 40)
    private String settlementMethod;

    @Size(max = 160)
    @Column(name = "settlement_reference", length = 160)
    private String settlementReference;

    @Column(name = "settlement_amount", precision = 21, scale = 2)
    private BigDecimal settlementAmount;

    @Column(name = "settled_at")
    private Instant settledAt;

    @Size(max = 50)
    @Column(name = "settled_by", length = 50)
    private String settledBy;

    @Column(name = "replacement_reserved_at")
    private Instant replacementReservedAt;

    @Column(name = "replacement_reservation_released_at")
    private Instant replacementReservationReleasedAt;

    /** Set when the exchanged original item is restocked locally (allocate or convert-to-refund). */
    @Column(name = "original_restocked_at")
    private Instant originalRestockedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "manual_refund_method", length = 32)
    private com.miniandmore.app.domain.enumeration.ManualRefundMethod manualRefundMethod;

    @Size(max = 160)
    @Column(name = "manual_refund_reference", length = 160)
    private String manualRefundReference;

    @Column(name = "manual_refund_recorded_at")
    private Instant manualRefundRecordedAt;

    @Size(max = 50)
    @Column(name = "manual_refund_recorded_by", length = 50)
    private String manualRefundRecordedBy;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getReturnNumber() {
        return returnNumber;
    }

    public void setReturnNumber(String returnNumber) {
        this.returnNumber = returnNumber;
    }

    public OrderReturnStatus getStatus() {
        return status;
    }

    public void setStatus(OrderReturnStatus status) {
        this.status = status;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getImageUrlsJson() {
        return imageUrlsJson;
    }

    public void setImageUrlsJson(String imageUrlsJson) {
        this.imageUrlsJson = imageUrlsJson;
    }

    public String getAdminNotes() {
        return adminNotes;
    }

    public void setAdminNotes(String adminNotes) {
        this.adminNotes = adminNotes;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public BigDecimal getRefundAmount() {
        return refundAmount;
    }

    public void setRefundAmount(BigDecimal refundAmount) {
        this.refundAmount = refundAmount;
    }

    public String getRazorpayRefundId() {
        return razorpayRefundId;
    }

    public void setRazorpayRefundId(String razorpayRefundId) {
        this.razorpayRefundId = razorpayRefundId;
    }

    public String getLedgerlyCreditNoteId() {
        return ledgerlyCreditNoteId;
    }

    public void setLedgerlyCreditNoteId(String ledgerlyCreditNoteId) {
        this.ledgerlyCreditNoteId = ledgerlyCreditNoteId;
    }

    public String getZohoCreditNoteId() {
        return zohoCreditNoteId;
    }

    public void setZohoCreditNoteId(String zohoCreditNoteId) {
        this.zohoCreditNoteId = zohoCreditNoteId;
    }

    public String getZohoReplacementInvoiceId() {
        return zohoReplacementInvoiceId;
    }

    public void setZohoReplacementInvoiceId(String zohoReplacementInvoiceId) {
        this.zohoReplacementInvoiceId = zohoReplacementInvoiceId;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(Instant requestedAt) {
        this.requestedAt = requestedAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(Instant resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    public CustomerOrder getOrder() {
        return order;
    }

    public void setOrder(CustomerOrder order) {
        this.order = order;
    }

    public String getUserLogin() {
        return userLogin;
    }

    public void setUserLogin(String userLogin) {
        this.userLogin = userLogin;
    }

    public OrderRequestType getRequestType() {
        return requestType;
    }

    public void setRequestType(OrderRequestType requestType) {
        this.requestType = requestType;
    }

    public OrderItem getOrderItem() {
        return orderItem;
    }

    public void setOrderItem(OrderItem orderItem) {
        this.orderItem = orderItem;
    }

    public String getReplacementVariantSku() {
        return replacementVariantSku;
    }

    public void setReplacementVariantSku(String replacementVariantSku) {
        this.replacementVariantSku = replacementVariantSku;
    }

    public String getReplacementSize() {
        return replacementSize;
    }

    public void setReplacementSize(String replacementSize) {
        this.replacementSize = replacementSize;
    }

    public String getReplacementColor() {
        return replacementColor;
    }

    public void setReplacementColor(String replacementColor) {
        this.replacementColor = replacementColor;
    }

    public com.miniandmore.app.domain.enumeration.ReturnDisposition getDisposition() {
        return disposition;
    }

    public void setDisposition(com.miniandmore.app.domain.enumeration.ReturnDisposition disposition) {
        this.disposition = disposition;
    }

    public com.miniandmore.app.domain.enumeration.ReturnLogisticsMode getLogisticsMode() {
        return logisticsMode;
    }

    public void setLogisticsMode(com.miniandmore.app.domain.enumeration.ReturnLogisticsMode logisticsMode) {
        this.logisticsMode = logisticsMode;
    }

    public String getCarrierName() {
        return carrierName;
    }

    public void setCarrierName(String carrierName) {
        this.carrierName = carrierName;
    }

    public String getReverseAwb() {
        return reverseAwb;
    }

    public void setReverseAwb(String reverseAwb) {
        this.reverseAwb = reverseAwb;
    }

    public String getTrackingUrl() {
        return trackingUrl;
    }

    public void setTrackingUrl(String trackingUrl) {
        this.trackingUrl = trackingUrl;
    }

    public String getPickupWindow() {
        return pickupWindow;
    }

    public void setPickupWindow(String pickupWindow) {
        this.pickupWindow = pickupWindow;
    }

    public String getProviderRequestId() {
        return providerRequestId;
    }

    public void setProviderRequestId(String providerRequestId) {
        this.providerRequestId = providerRequestId;
    }

    public String getPickupFailureReason() {
        return pickupFailureReason;
    }

    public void setPickupFailureReason(String pickupFailureReason) {
        this.pickupFailureReason = pickupFailureReason;
    }

    public String getReverseCarrierStatus() {
        return reverseCarrierStatus;
    }

    public void setReverseCarrierStatus(String reverseCarrierStatus) {
        this.reverseCarrierStatus = reverseCarrierStatus;
    }

    public com.miniandmore.app.domain.enumeration.ReturnLogisticsMode getOutboundLogisticsMode() {
        return outboundLogisticsMode;
    }

    public void setOutboundLogisticsMode(com.miniandmore.app.domain.enumeration.ReturnLogisticsMode outboundLogisticsMode) {
        this.outboundLogisticsMode = outboundLogisticsMode;
    }

    public String getOutboundCarrierName() {
        return outboundCarrierName;
    }

    public void setOutboundCarrierName(String outboundCarrierName) {
        this.outboundCarrierName = outboundCarrierName;
    }

    public String getOutboundAwb() {
        return outboundAwb;
    }

    public void setOutboundAwb(String outboundAwb) {
        this.outboundAwb = outboundAwb;
    }

    public String getOutboundTrackingUrl() {
        return outboundTrackingUrl;
    }

    public void setOutboundTrackingUrl(String outboundTrackingUrl) {
        this.outboundTrackingUrl = outboundTrackingUrl;
    }

    public String getOutboundLabelUrl() {
        return outboundLabelUrl;
    }

    public void setOutboundLabelUrl(String outboundLabelUrl) {
        this.outboundLabelUrl = outboundLabelUrl;
    }

    public String getOutboundProviderRequestId() {
        return outboundProviderRequestId;
    }

    public void setOutboundProviderRequestId(String outboundProviderRequestId) {
        this.outboundProviderRequestId = outboundProviderRequestId;
    }

    public String getOutboundCarrierStatus() {
        return outboundCarrierStatus;
    }

    public void setOutboundCarrierStatus(String outboundCarrierStatus) {
        this.outboundCarrierStatus = outboundCarrierStatus;
    }

    public Integer getPackageWeightGrams() {
        return packageWeightGrams;
    }

    public void setPackageWeightGrams(Integer packageWeightGrams) {
        this.packageWeightGrams = packageWeightGrams;
    }

    public Integer getPackageLengthCm() {
        return packageLengthCm;
    }

    public void setPackageLengthCm(Integer packageLengthCm) {
        this.packageLengthCm = packageLengthCm;
    }

    public Integer getPackageBreadthCm() {
        return packageBreadthCm;
    }

    public void setPackageBreadthCm(Integer packageBreadthCm) {
        this.packageBreadthCm = packageBreadthCm;
    }

    public Integer getPackageHeightCm() {
        return packageHeightCm;
    }

    public void setPackageHeightCm(Integer packageHeightCm) {
        this.packageHeightCm = packageHeightCm;
    }

    public Integer getPackageChargeableWeightGrams() {
        return packageChargeableWeightGrams;
    }

    public void setPackageChargeableWeightGrams(Integer packageChargeableWeightGrams) {
        this.packageChargeableWeightGrams = packageChargeableWeightGrams;
    }

    public String getPackageCourierId() {
        return packageCourierId;
    }

    public void setPackageCourierId(String packageCourierId) {
        this.packageCourierId = packageCourierId;
    }

    public String getPackageCourierName() {
        return packageCourierName;
    }

    public void setPackageCourierName(String packageCourierName) {
        this.packageCourierName = packageCourierName;
    }

    public PackageType getPackageType() {
        return packageType;
    }

    public void setPackageType(PackageType packageType) {
        this.packageType = packageType;
    }

    public Long getPackageBoxId() {
        return packageBoxId;
    }

    public void setPackageBoxId(Long packageBoxId) {
        this.packageBoxId = packageBoxId;
    }

    public Instant getPackedAt() {
        return packedAt;
    }

    public void setPackedAt(Instant packedAt) {
        this.packedAt = packedAt;
    }

    public Integer getOutboundPackedQty() {
        return outboundPackedQty;
    }

    public void setOutboundPackedQty(Integer outboundPackedQty) {
        this.outboundPackedQty = outboundPackedQty;
    }

    public BigDecimal getPackageShortRefundAmount() {
        return packageShortRefundAmount;
    }

    public void setPackageShortRefundAmount(BigDecimal packageShortRefundAmount) {
        this.packageShortRefundAmount = packageShortRefundAmount;
    }

    public String getPackageShortRazorpayRefundId() {
        return packageShortRazorpayRefundId;
    }

    public void setPackageShortRazorpayRefundId(String packageShortRazorpayRefundId) {
        this.packageShortRazorpayRefundId = packageShortRazorpayRefundId;
    }

    public BigDecimal getOriginalItemAmount() {
        return originalItemAmount;
    }

    public void setOriginalItemAmount(BigDecimal originalItemAmount) {
        this.originalItemAmount = originalItemAmount;
    }

    public BigDecimal getReplacementItemAmount() {
        return replacementItemAmount;
    }

    public void setReplacementItemAmount(BigDecimal replacementItemAmount) {
        this.replacementItemAmount = replacementItemAmount;
    }

    public BigDecimal getPriceDifference() {
        return priceDifference;
    }

    public void setPriceDifference(BigDecimal priceDifference) {
        this.priceDifference = priceDifference;
    }

    public ExchangeSettlementStatus getSettlementStatus() {
        return settlementStatus;
    }

    public void setSettlementStatus(ExchangeSettlementStatus settlementStatus) {
        this.settlementStatus = settlementStatus;
    }

    public String getSettlementMethod() {
        return settlementMethod;
    }

    public void setSettlementMethod(String settlementMethod) {
        this.settlementMethod = settlementMethod;
    }

    public String getSettlementReference() {
        return settlementReference;
    }

    public void setSettlementReference(String settlementReference) {
        this.settlementReference = settlementReference;
    }

    public BigDecimal getSettlementAmount() {
        return settlementAmount;
    }

    public void setSettlementAmount(BigDecimal settlementAmount) {
        this.settlementAmount = settlementAmount;
    }

    public Instant getSettledAt() {
        return settledAt;
    }

    public void setSettledAt(Instant settledAt) {
        this.settledAt = settledAt;
    }

    public String getSettledBy() {
        return settledBy;
    }

    public void setSettledBy(String settledBy) {
        this.settledBy = settledBy;
    }

    public Instant getReplacementReservedAt() {
        return replacementReservedAt;
    }

    public void setReplacementReservedAt(Instant replacementReservedAt) {
        this.replacementReservedAt = replacementReservedAt;
    }

    public Instant getReplacementReservationReleasedAt() {
        return replacementReservationReleasedAt;
    }

    public void setReplacementReservationReleasedAt(Instant replacementReservationReleasedAt) {
        this.replacementReservationReleasedAt = replacementReservationReleasedAt;
    }

    public Instant getOriginalRestockedAt() {
        return originalRestockedAt;
    }

    public void setOriginalRestockedAt(Instant originalRestockedAt) {
        this.originalRestockedAt = originalRestockedAt;
    }

    public com.miniandmore.app.domain.enumeration.ManualRefundMethod getManualRefundMethod() {
        return manualRefundMethod;
    }

    public void setManualRefundMethod(com.miniandmore.app.domain.enumeration.ManualRefundMethod manualRefundMethod) {
        this.manualRefundMethod = manualRefundMethod;
    }

    public String getManualRefundReference() {
        return manualRefundReference;
    }

    public void setManualRefundReference(String manualRefundReference) {
        this.manualRefundReference = manualRefundReference;
    }

    public Instant getManualRefundRecordedAt() {
        return manualRefundRecordedAt;
    }

    public void setManualRefundRecordedAt(Instant manualRefundRecordedAt) {
        this.manualRefundRecordedAt = manualRefundRecordedAt;
    }

    public String getManualRefundRecordedBy() {
        return manualRefundRecordedBy;
    }

    public void setManualRefundRecordedBy(String manualRefundRecordedBy) {
        this.manualRefundRecordedBy = manualRefundRecordedBy;
    }
}
