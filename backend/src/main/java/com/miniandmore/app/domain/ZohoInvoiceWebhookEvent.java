package com.miniandmore.app.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

/** Recorded after a successful Zoho invoice status webhook update. */
@Entity
@Table(name = "zoho_invoice_webhook_event")
public class ZohoInvoiceWebhookEvent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    @Column(name = "id")
    private Long id;

    @NotNull
    @Size(max = 160)
    @Column(name = "event_id", length = 160, nullable = false, unique = true)
    private String eventId;

    @Size(max = 120)
    @Column(name = "zoho_invoice_id", length = 120)
    private String zohoInvoiceId;

    @Size(max = 120)
    @Column(name = "zoho_sales_order_id", length = 120)
    private String zohoSalesOrderId;

    @Size(max = 120)
    @Column(name = "invoice_number", length = 120)
    private String invoiceNumber;

    @Size(max = 80)
    @Column(name = "status", length = 80)
    private String status;

    @NotNull
    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getZohoInvoiceId() {
        return zohoInvoiceId;
    }

    public void setZohoInvoiceId(String zohoInvoiceId) {
        this.zohoInvoiceId = zohoInvoiceId;
    }

    public String getZohoSalesOrderId() {
        return zohoSalesOrderId;
    }

    public void setZohoSalesOrderId(String zohoSalesOrderId) {
        this.zohoSalesOrderId = zohoSalesOrderId;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public void setInvoiceNumber(String invoiceNumber) {
        this.invoiceNumber = invoiceNumber;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(Instant receivedAt) {
        this.receivedAt = receivedAt;
    }
}
