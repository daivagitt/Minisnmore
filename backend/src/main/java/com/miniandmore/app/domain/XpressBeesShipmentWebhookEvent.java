package com.miniandmore.app.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

@Entity
@Table(name = "xpressbees_shipment_webhook_event")
public class XpressBeesShipmentWebhookEvent implements Serializable {

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

    @Size(max = 40)
    @Column(name = "order_number", length = 40)
    private String orderNumber;

    @Size(max = 120)
    @Column(name = "awb", length = 120)
    private String awb;

    @Size(max = 80)
    @Column(name = "provider_status", length = 80)
    private String providerStatus;

    @Size(max = 80)
    @Column(name = "local_status", length = 80)
    private String localStatus;

    @NotNull
    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    public void setEventId(String eventId) { this.eventId = eventId; }
    public void setOrderNumber(String orderNumber) { this.orderNumber = orderNumber; }
    public void setAwb(String awb) { this.awb = awb; }
    public void setProviderStatus(String providerStatus) { this.providerStatus = providerStatus; }
    public void setLocalStatus(String localStatus) { this.localStatus = localStatus; }
    public void setReceivedAt(Instant receivedAt) { this.receivedAt = receivedAt; }
}
