package com.miniandmore.app.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.miniandmore.app.domain.enumeration.OrderReturnStatus;
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
}
