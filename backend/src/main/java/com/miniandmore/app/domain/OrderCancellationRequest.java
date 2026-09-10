package com.miniandmore.app.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.miniandmore.app.domain.enumeration.CancellationRequestStatus;
import com.miniandmore.app.domain.enumeration.ManualRefundMethod;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "order_cancellation_request")
public class OrderCancellationRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    @Column(name = "id")
    private Long id;

    @NotNull
    @Size(min = 8, max = 48)
    @Column(name = "request_number", length = 48, nullable = false, unique = true)
    private String requestNumber;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 32, nullable = false)
    private CancellationRequestStatus status;

    @NotNull
    @Column(name = "reason", columnDefinition = "text", nullable = false)
    private String reason;

    @Size(max = 2000)
    @Column(name = "admin_notes", length = 2000)
    private String adminNotes;

    @Size(max = 1000)
    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;

    @Size(max = 120)
    @Column(name = "razorpay_refund_id", length = 120)
    private String razorpayRefundId;

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

    @Enumerated(EnumType.STRING)
    @Column(name = "manual_refund_method", length = 32)
    private ManualRefundMethod manualRefundMethod;

    @Size(max = 160)
    @Column(name = "manual_refund_reference", length = 160)
    private String manualRefundReference;

    @Column(name = "manual_refund_recorded_at")
    private Instant manualRefundRecordedAt;

    @Size(max = 50)
    @Column(name = "manual_refund_recorded_by", length = 50)
    private String manualRefundRecordedBy;

    @Column(name = "refund_amount", precision = 21, scale = 2)
    private BigDecimal refundAmount;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRequestNumber() {
        return requestNumber;
    }

    public void setRequestNumber(String requestNumber) {
        this.requestNumber = requestNumber;
    }

    public CancellationRequestStatus getStatus() {
        return status;
    }

    public void setStatus(CancellationRequestStatus status) {
        this.status = status;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
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

    public String getRazorpayRefundId() {
        return razorpayRefundId;
    }

    public void setRazorpayRefundId(String razorpayRefundId) {
        this.razorpayRefundId = razorpayRefundId;
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

    public ManualRefundMethod getManualRefundMethod() {
        return manualRefundMethod;
    }

    public void setManualRefundMethod(ManualRefundMethod manualRefundMethod) {
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

    public BigDecimal getRefundAmount() {
        return refundAmount;
    }

    public void setRefundAmount(BigDecimal refundAmount) {
        this.refundAmount = refundAmount;
    }
}
