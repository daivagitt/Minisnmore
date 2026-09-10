package com.miniandmore.app.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.miniandmore.app.domain.enumeration.ReturnDisposition;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/** Immutable order-price snapshot and warehouse outcome for one returned line. */
@Entity
@Table(
    name = "order_return_item",
    uniqueConstraints = @UniqueConstraint(name = "ux_order_return_item__return_order_item", columnNames = { "order_return_id", "order_item_id" })
)
public class OrderReturnItem implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    @Column(name = "id")
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "order_return_id", nullable = false)
    @JsonIgnore
    private OrderReturn orderReturn;

    @ManyToOne(optional = false)
    @JoinColumn(name = "order_item_id", nullable = false)
    @JsonIgnoreProperties(value = { "order" }, allowSetters = true)
    private OrderItem orderItem;

    @NotNull
    @Column(name = "requested_qty", nullable = false)
    private Integer requestedQty;

    @Column(name = "received_qty")
    private Integer receivedQty;

    @Column(name = "accepted_qty")
    private Integer acceptedQty;

    @NotNull
    @Column(name = "unit_amount", precision = 21, scale = 2, nullable = false)
    private BigDecimal unitAmount;

    @NotNull
    @Column(name = "gross_amount", precision = 21, scale = 2, nullable = false)
    private BigDecimal grossAmount;

    @NotNull
    @Column(name = "discount_allocation", precision = 21, scale = 2, nullable = false)
    private BigDecimal discountAllocation = BigDecimal.ZERO;

    @NotNull
    @Column(name = "refund_amount", precision = 21, scale = 2, nullable = false)
    private BigDecimal refundAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "disposition", length = 32)
    private ReturnDisposition disposition;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public OrderReturn getOrderReturn() {
        return orderReturn;
    }

    public void setOrderReturn(OrderReturn orderReturn) {
        this.orderReturn = orderReturn;
    }

    public OrderItem getOrderItem() {
        return orderItem;
    }

    public void setOrderItem(OrderItem orderItem) {
        this.orderItem = orderItem;
    }

    public Integer getRequestedQty() {
        return requestedQty;
    }

    public void setRequestedQty(Integer requestedQty) {
        this.requestedQty = requestedQty;
    }

    public Integer getReceivedQty() {
        return receivedQty;
    }

    public void setReceivedQty(Integer receivedQty) {
        this.receivedQty = receivedQty;
    }

    public Integer getAcceptedQty() {
        return acceptedQty;
    }

    public void setAcceptedQty(Integer acceptedQty) {
        this.acceptedQty = acceptedQty;
    }

    public BigDecimal getUnitAmount() {
        return unitAmount;
    }

    public void setUnitAmount(BigDecimal unitAmount) {
        this.unitAmount = unitAmount;
    }

    public BigDecimal getGrossAmount() {
        return grossAmount;
    }

    public void setGrossAmount(BigDecimal grossAmount) {
        this.grossAmount = grossAmount;
    }

    public BigDecimal getDiscountAllocation() {
        return discountAllocation;
    }

    public void setDiscountAllocation(BigDecimal discountAllocation) {
        this.discountAllocation = discountAllocation;
    }

    public BigDecimal getRefundAmount() {
        return refundAmount;
    }

    public void setRefundAmount(BigDecimal refundAmount) {
        this.refundAmount = refundAmount;
    }

    public ReturnDisposition getDisposition() {
        return disposition;
    }

    public void setDisposition(ReturnDisposition disposition) {
        this.disposition = disposition;
    }
}
