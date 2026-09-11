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

/** Recorded after an authenticated XpressBees return webhook is accepted. */
@Entity
@Table(name = "xpressbees_return_webhook_event")
public class XpressBeesReturnWebhookEvent implements Serializable {

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

    @Size(max = 48)
    @Column(name = "return_number", length = 48)
    private String returnNumber;

    @Size(max = 80)
    @Column(name = "reverse_awb", length = 80)
    private String reverseAwb;

    @Size(max = 80)
    @Column(name = "provider_status", length = 80)
    private String providerStatus;

    @Size(max = 80)
    @Column(name = "local_status", length = 80)
    private String localStatus;

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

    public String getReturnNumber() {
        return returnNumber;
    }

    public void setReturnNumber(String returnNumber) {
        this.returnNumber = returnNumber;
    }

    public String getReverseAwb() {
        return reverseAwb;
    }

    public void setReverseAwb(String reverseAwb) {
        this.reverseAwb = reverseAwb;
    }

    public String getProviderStatus() {
        return providerStatus;
    }

    public void setProviderStatus(String providerStatus) {
        this.providerStatus = providerStatus;
    }

    public String getLocalStatus() {
        return localStatus;
    }

    public void setLocalStatus(String localStatus) {
        this.localStatus = localStatus;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(Instant receivedAt) {
        this.receivedAt = receivedAt;
    }
}
