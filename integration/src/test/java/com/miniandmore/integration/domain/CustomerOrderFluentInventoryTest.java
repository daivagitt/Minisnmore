package com.miniandmore.integration.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class CustomerOrderFluentInventoryTest {

    @Test
    void fluentInventoryAndZohoAccessors() {
        Instant now = Instant.parse("2026-06-01T00:00:00Z");
        CustomerOrder order = new CustomerOrder()
            .inventoryReserved(true)
            .inventoryReservedUntil(now)
            .inventoryReleasedAt(now)
            .zohoSalesOrderId("so-1");

        assertThat(order.getInventoryReserved()).isTrue();
        assertThat(order.getInventoryReservedUntil()).isEqualTo(now);
        assertThat(order.getInventoryReleasedAt()).isEqualTo(now);
        assertThat(order.getZohoSalesOrderId()).isEqualTo("so-1");

        order.setInventoryReserved(false);
        order.setInventoryReservedUntil(null);
        order.setInventoryReleasedAt(null);
        order.setZohoSalesOrderId("so-2");
        assertThat(order.getInventoryReserved()).isFalse();
        assertThat(order.getZohoSalesOrderId()).isEqualTo("so-2");
    }
}
