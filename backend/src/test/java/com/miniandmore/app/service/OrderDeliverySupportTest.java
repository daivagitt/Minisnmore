package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.miniandmore.app.domain.CustomerOrder;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OrderDeliverySupportTest {

    @Test
    void markDelivered_usesExplicitProviderTimestamp() {
        CustomerOrder order = new CustomerOrder();

        OrderDeliverySupport.markDelivered(order, Map.of("shipment_delivered_date", "2026-09-29 16:30"));

        assertThat(order.getShipmentDeliveredAt()).isEqualTo(Instant.parse("2026-09-29T11:00:00Z"));
    }

    @Test
    void markDelivered_doesNotReplaceFirstDeliveryTimestamp() {
        Instant firstDelivery = Instant.parse("2026-09-28T09:00:00Z");
        CustomerOrder order = new CustomerOrder().shipmentDeliveredAt(firstDelivery);

        OrderDeliverySupport.markDelivered(order, Instant.parse("2026-09-29T09:00:00Z"));

        assertThat(order.getShipmentDeliveredAt()).isEqualTo(firstDelivery);
    }

    @Test
    void dateOnlyProviderValueUsesEndOfIndiaDay() {
        assertThat(OrderDeliverySupport.parseInstant("29/09/2026"))
            .isEqualTo(Instant.parse("2026-09-29T18:29:59.999999999Z"));
    }
}
