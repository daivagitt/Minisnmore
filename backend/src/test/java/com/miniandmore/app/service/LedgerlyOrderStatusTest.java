package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.miniandmore.app.domain.CustomerOrder;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LedgerlyOrderStatusTest {

    @Test
    void resolveFromPullResponse_prefersFulfillmentStatus() {
        Map<String, Object> response = Map.of(
            "status",
            "Paid",
            "fulfillmentStatus",
            "Delivered",
            "trackingNumber",
            "AWB-123"
        );

        assertThat(LedgerlyOrderStatus.resolveFromPullResponse(response)).isEqualTo("delivered");
    }

    @Test
    void shouldReplace_doesNotDowngradeDeliveredToPaid() {
        assertThat(LedgerlyOrderStatus.shouldReplace("delivered", "Paid")).isFalse();
        assertThat(LedgerlyOrderStatus.shouldReplace("Paid", "delivered")).isTrue();
        assertThat(LedgerlyOrderStatus.shouldReplace("shipped", "Paid")).isFalse();
    }

    @Test
    void isDelivered_recognizesLedgerlyVariants() {
        assertThat(LedgerlyOrderStatus.isDelivered("closed")).isTrue();
        assertThat(LedgerlyOrderStatus.isDelivered("fulfilled")).isTrue();
        assertThat(LedgerlyOrderStatus.isDelivered("Paid")).isFalse();
    }

    @Test
    void applyTrackingFields_copiesTrackingShipmentAndCarrier() {
        CustomerOrder order = new CustomerOrder().orderNumber("ORD-1");
        LedgerlyOrderStatus.applyTrackingFields(
            order,
            Map.of(
                "tracking_number",
                "AWB-9",
                "trackingCarrier",
                "BlueDart",
                "shipment_date",
                "2026-08-31"
            )
        );

        assertThat(order.getTrackingNumber()).isEqualTo("AWB-9");
        assertThat(order.getTrackingCarrier()).isEqualTo("BlueDart");
        assertThat(order.getShipmentDate()).isEqualTo("2026-08-31");
    }

    @Test
    void isCancelled_and_isShipped_recognizeNormalizedStatuses() {
        assertThat(LedgerlyOrderStatus.isCancelled("void")).isTrue();
        assertThat(LedgerlyOrderStatus.isCancelled("Canceled")).isTrue();
        assertThat(LedgerlyOrderStatus.isShipped("in-transit")).isTrue();
        assertThat(LedgerlyOrderStatus.isShipped("on_the_way")).isTrue();
    }

    @Test
    void resolveFromPullResponse_fallsBackToDeliveryAndShipmentStatus() {
        assertThat(LedgerlyOrderStatus.resolveFromPullResponse(Map.of("deliveryStatus", "Shipped"))).isEqualTo("shipped");
        assertThat(LedgerlyOrderStatus.resolveFromPullResponse(Map.of("shipmentStatus", "Delivered"))).isEqualTo("delivered");
    }
}
