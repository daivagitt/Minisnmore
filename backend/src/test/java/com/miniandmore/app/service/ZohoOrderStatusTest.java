package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.enumeration.OrderStatus;
import com.miniandmore.app.domain.enumeration.PaymentStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ZohoOrderStatusTest {

    private CustomerOrder paidOrder() {
        return new CustomerOrder()
            .status(OrderStatus.CONFIRMED)
            .paymentStatus(PaymentStatus.PAID)
            .subtotal(BigDecimal.ONE)
            .discount(BigDecimal.ZERO)
            .shipping(BigDecimal.ZERO)
            .tax(BigDecimal.ZERO)
            .donation(BigDecimal.ZERO)
            .total(BigDecimal.ONE)
            .shippingAddressJson("{}");
    }

    @Test
    void applySalesOrderTracking_setsShipmentDateAndCarrier() {
        CustomerOrder order = paidOrder();
        Map<String, Object> salesOrder = Map.of(
            "status",
            "invoiced",
            "shipment_date",
            "2026-08-14",
            "delivery_method",
            "BlueDart",
            "tracking_number",
            "AWB-99"
        );

        ZohoOrderStatus.applySalesOrderTracking(order, salesOrder);

        assertThat(order.getZohoOrderStatus()).isEqualTo("invoiced");
        assertThat(order.getShipmentDate()).isEqualTo("2026-08-14");
        assertThat(order.getTrackingCarrier()).isEqualTo("BlueDart");
        assertThat(order.getTrackingNumber()).isEqualTo("AWB-99");
    }

    @Test
    void applySalesOrderTracking_usesShippedStatusWhenShipmentDateMissing() {
        CustomerOrder order = paidOrder();
        Map<String, Object> salesOrder = Map.of(
            "status",
            "invoiced",
            "shipped_status",
            "shipped",
            "date",
            "2026-08-14"
        );

        ZohoOrderStatus.applySalesOrderTracking(order, salesOrder);

        assertThat(order.getShipmentDate()).isEqualTo("2026-08-14");
    }

    @Test
    void applyDeliveryChallans_marksFulfilledWhenDelivered() {
        CustomerOrder order = paidOrder();
        List<Map<String, Object>> challans = List.of(
            Map.of("status", "delivered", "shipment_date", "2026-08-15", "tracking_number", "DC-1")
        );

        ZohoOrderStatus.applyDeliveryChallans(order, challans);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.FULFILLED);
        assertThat(order.getShipmentDate()).isEqualTo("2026-08-15");
        assertThat(order.getTrackingNumber()).isEqualTo("DC-1");
    }

    @Test
    void applySalesOrderTracking_invoicedClosedOrderStaysConfirmed() {
        CustomerOrder order = paidOrder();
        Map<String, Object> salesOrder = Map.of(
            "status",
            "invoiced",
            "order_status",
            "closed",
            "shipped_status",
            "pending"
        );

        ZohoOrderStatus.applySalesOrderTracking(order, salesOrder);

        assertThat(order.getZohoOrderStatus()).isEqualTo("invoiced");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.getShipmentDate()).isNull();
    }

    @Test
    void isDelivered_onlyMatchesDeliveredStatus() {
        assertThat(ZohoOrderStatus.isDelivered("delivered")).isTrue();
        assertThat(ZohoOrderStatus.isDelivered("closed")).isFalse();
        assertThat(ZohoOrderStatus.isDelivered("invoiced")).isFalse();
    }

    @Test
    void isShipped_recognizesZohoVariants() {
        assertThat(ZohoOrderStatus.isShipped("shipped")).isTrue();
        assertThat(ZohoOrderStatus.isShipped("in_transit")).isTrue();
        assertThat(ZohoOrderStatus.isShipped("invoiced")).isFalse();
    }
}
