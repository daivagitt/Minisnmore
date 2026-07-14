package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;

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
}
