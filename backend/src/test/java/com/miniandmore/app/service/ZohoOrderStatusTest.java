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
    void applySalesOrderTracking_skipsShipmentFieldsWhenLocalForwardShipmentExists() {
        CustomerOrder order = paidOrder()
            .trackingNumber("XB-LOCAL")
            .trackingCarrier("XpressBees")
            .shipmentLogisticsMode(com.miniandmore.app.domain.enumeration.ReturnLogisticsMode.XPRESSBEES)
            .shipmentCarrierStatus("booked");

        ZohoOrderStatus.applySalesOrderTracking(
            order,
            Map.of(
                "status",
                "delivered",
                "shipment_date",
                "2026-09-21",
                "delivery_method",
                "DTDC",
                "tracking_number",
                "ZOHO-AWB"
            )
        );

        assertThat(order.getZohoOrderStatus()).isEqualTo("delivered");
        assertThat(order.getTrackingNumber()).isEqualTo("XB-LOCAL");
        assertThat(order.getTrackingCarrier()).isEqualTo("XpressBees");
        assertThat(order.getShipmentDate()).isNull();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void applyPackages_skipsLocalForwardShipment() {
        CustomerOrder order = paidOrder()
            .trackingNumber("XB-LOCAL")
            .shipmentLogisticsMode(com.miniandmore.app.domain.enumeration.ReturnLogisticsMode.XPRESSBEES)
            .shipmentCarrierStatus("booked");

        ZohoOrderStatus.applyPackages(
            order,
            List.of(Map.of("package_id", "pkg-42", "status", "delivered", "tracking_number", "ZOHO-AWB"))
        );

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.getTrackingNumber()).isEqualTo("XB-LOCAL");
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
    void applyPackages_marksConfirmedOrderHandWrapped() {
        CustomerOrder order = paidOrder();

        ZohoOrderStatus.applyPackages(order, List.of(Map.of("package_id", "pkg-42")));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.HAND_WRAPPED);
    }

    @Test
    void applySalesOrderTracking_packedQuantityMarksOrderHandWrapped() {
        CustomerOrder order = paidOrder();

        ZohoOrderStatus.applySalesOrderTracking(
            order,
            Map.of(
                "status",
                "invoiced",
                "shipped_status",
                "pending",
                "line_items",
                List.of(Map.of("quantity_packed", 1, "quantity_shipped", 0))
            )
        );

        assertThat(order.getStatus()).isEqualTo(OrderStatus.HAND_WRAPPED);
        assertThat(order.getShipmentDate()).isNull();
    }

    @Test
    void applySalesOrderTracking_shippedQuantityMarksOrderOnTheWay() {
        CustomerOrder order = paidOrder();

        ZohoOrderStatus.applySalesOrderTracking(
            order,
            Map.of(
                "status",
                "invoiced",
                "date",
                "2026-08-30",
                "shipped_status",
                "pending",
                "line_items",
                List.of(Map.of("quantity_packed", 1, "quantity_shipped", 1))
            )
        );

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.getShipmentDate()).isEqualTo("2026-08-30");
    }

    @Test
    void applyPackages_copiesShipmentTrackingFields() {
        CustomerOrder order = paidOrder().status(OrderStatus.HAND_WRAPPED);

        ZohoOrderStatus.applyPackages(
            order,
            List.of(
                Map.of(
                    "package_id",
                    "pkg-42",
                    "shipment_id",
                    "ship-42",
                    "status",
                    "shipped",
                    "shipment_date",
                    "2026-08-30",
                    "tracking_number",
                    "AWB-42",
                    "carrier",
                    "BlueDart"
                )
            )
        );

        assertThat(order.getShipmentDate()).isEqualTo("2026-08-30");
        assertThat(order.getTrackingNumber()).isEqualTo("AWB-42");
        assertThat(order.getTrackingCarrier()).isEqualTo("BlueDart");
        assertThat(order.getZohoOrderStatus()).isEqualTo("shipped");
        assertThat(OrderTrackingStatusResolver.resolve(order)).isEqualTo("shipped");
    }

    @Test
    void applyPackages_inTransitShipmentResolvesOnTheWay() {
        CustomerOrder order = paidOrder().status(OrderStatus.HAND_WRAPPED);

        ZohoOrderStatus.applyPackages(order, List.of(Map.of("shipment_id", "ship-42", "status", "in_transit")));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.HAND_WRAPPED);
        assertThat(order.getZohoOrderStatus()).isEqualTo("in_transit");
        assertThat(OrderTrackingStatusResolver.resolve(order)).isEqualTo("shipped");
    }

    @Test
    void applyPackages_fulfilledShipmentMarksDelivered() {
        CustomerOrder order = paidOrder().status(OrderStatus.HAND_WRAPPED);

        ZohoOrderStatus.applyPackages(
            order,
            List.of(Map.of("package_id", "pkg-42", "shipment_id", "ship-42", "status", "fulfilled"))
        );

        assertThat(order.getStatus()).isEqualTo(OrderStatus.FULFILLED);
        assertThat(order.getZohoOrderStatus()).isEqualTo("fulfilled");
        assertThat(OrderTrackingStatusResolver.resolve(order)).isEqualTo("delivered");
    }

    @Test
    void applyPackages_shipmentOrderDeliveredStatusMarksFulfilledWithoutCustomField() {
        CustomerOrder order = paidOrder().status(OrderStatus.HAND_WRAPPED);

        ZohoOrderStatus.applyPackages(
            order,
            List.of(Map.of("shipment_order_id", "ship-42", "status", "Delivered", "shipment_date", "2026-09-02"))
        );

        assertThat(order.getStatus()).isEqualTo(OrderStatus.FULFILLED);
        assertThat(OrderTrackingStatusResolver.resolve(order)).isEqualTo("delivered");
    }

    @Test
    void applyPackages_shipmentDeliveredDateMarksFulfilled() {
        CustomerOrder order = paidOrder().status(OrderStatus.HAND_WRAPPED);

        ZohoOrderStatus.applyPackages(
            order,
            List.of(Map.of("shipment_id", "ship-42", "status", "shipped", "shipment_delivered_date", "2026-09-02 18:00"))
        );

        assertThat(order.getStatus()).isEqualTo(OrderStatus.FULFILLED);
        assertThat(OrderTrackingStatusResolver.resolve(order)).isEqualTo("delivered");
    }

    @Test
    void applyPackages_shipmentOrderFulfilledStatusMarksFulfilledWithoutCustomField() {
        CustomerOrder order = paidOrder().status(OrderStatus.HAND_WRAPPED);

        ZohoOrderStatus.applyPackages(order, List.of(Map.of("shipment_order_id", "ship-42", "status", "Fulfilled")));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.FULFILLED);
        assertThat(OrderTrackingStatusResolver.resolve(order)).isEqualTo("delivered");
    }

    @Test
    void applyPackages_fulfilledPackageWithoutShipmentIdMarksDelivered() {
        CustomerOrder order = paidOrder().status(OrderStatus.HAND_WRAPPED);

        ZohoOrderStatus.applyPackages(order, List.of(Map.of("package_id", "pkg-42", "status", "fulfilled")));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.FULFILLED);
        assertThat(OrderTrackingStatusResolver.resolve(order)).isEqualTo("delivered");
    }

    @Test
    void applyPackages_deliveryStatusDeliveredMarksFulfilledEvenWhenStatusIsFulfilled() {
        CustomerOrder order = paidOrder().status(OrderStatus.HAND_WRAPPED);

        ZohoOrderStatus.applyPackages(
            order,
            List.of(Map.of("package_id", "pkg-42", "status", "fulfilled", "delivery_status", "Delivered"))
        );

        assertThat(order.getStatus()).isEqualTo(OrderStatus.FULFILLED);
    }

    @Test
    void applyPackages_miniMsDeliveryStatusDeliveredMarksFulfilledWhileShipmentStatusStaysShipped() {
        CustomerOrder order = paidOrder().status(OrderStatus.HAND_WRAPPED);

        ZohoOrderStatus.applyPackages(
            order,
            List.of(Map.of("shipment_order_id", "ship-42", "status", "Shipped", "cf_minims_delivery_status", "delivered"))
        );

        assertThat(order.getStatus()).isEqualTo(OrderStatus.FULFILLED);
        assertThat(OrderTrackingStatusResolver.resolve(order)).isEqualTo("delivered");
    }

    @Test
    void applyPackages_shipmentOrderIdWithoutShippedStatusDoesNotInventShipmentDate() {
        CustomerOrder order = paidOrder();

        ZohoOrderStatus.applyPackages(order, List.of(Map.of("shipment_order_id", "ship-42", "package_id", "pkg-42")));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.HAND_WRAPPED);
        assertThat(order.getShipmentDate()).isNull();
        assertThat(OrderTrackingStatusResolver.resolve(order)).isEqualTo("hand_wrapped");
    }

    @Test
    void applySalesOrderTracking_deliveryStatusDeliveredMarksFulfilledEvenWhenStatusIsFulfilled() {
        CustomerOrder order = paidOrder().status(OrderStatus.HAND_WRAPPED);

        ZohoOrderStatus.applySalesOrderTracking(order, Map.of("status", "fulfilled", "deliveryStatus", "Delivered"));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.FULFILLED);
        assertThat(order.getZohoOrderStatus()).isEqualTo("fulfilled");
        assertThat(OrderTrackingStatusResolver.resolve(order)).isEqualTo("delivered");
    }

    @Test
    void applyPackages_doesNotChangeDeliveredOrCancelledOrders() {
        CustomerOrder delivered = paidOrder().status(OrderStatus.FULFILLED);
        CustomerOrder cancelled = paidOrder().status(OrderStatus.CANCELLED);

        ZohoOrderStatus.applyPackages(delivered, List.of(Map.of("package_id", "pkg-42")));
        ZohoOrderStatus.applyPackages(cancelled, List.of(Map.of("package_id", "pkg-42")));

        assertThat(delivered.getStatus()).isEqualTo(OrderStatus.FULFILLED);
        assertThat(cancelled.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void applyPackages_doesNotAdvanceOrderAlreadyInShippedTracking() {
        CustomerOrder order = paidOrder().trackingNumber("AWB-42");

        ZohoOrderStatus.applyPackages(order, List.of(Map.of("package_id", "pkg-42")));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void applySalesOrderTracking_fulfilledWithoutDeliverySignalStaysConfirmed() {
        CustomerOrder order = paidOrder();

        ZohoOrderStatus.applySalesOrderTracking(order, Map.of("status", "fulfilled"));

        assertThat(order.getZohoOrderStatus()).isEqualTo("fulfilled");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void applySalesOrderTracking_shipmentDeliveredIsNotDemotedByInvoicedSalesOrder() {
        CustomerOrder order = paidOrder().status(OrderStatus.FULFILLED);

        ZohoOrderStatus.applySalesOrderTracking(order, Map.of("status", "invoiced", "shipped_status", "fulfilled"));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.FULFILLED);
        assertThat(OrderTrackingStatusResolver.resolve(order)).isEqualTo("delivered");
    }

    @Test
    void applySalesOrderTracking_shipmentDeliveredKeepsFulfilledWhenSalesOrderIsOnlyFulfilled() {
        CustomerOrder order = paidOrder().status(OrderStatus.FULFILLED);

        ZohoOrderStatus.applySalesOrderTracking(
            order,
            Map.of("status", "fulfilled", "line_items", List.of(Map.of("quantity_packed", 1)))
        );

        assertThat(order.getStatus()).isEqualTo(OrderStatus.FULFILLED);
        assertThat(OrderTrackingStatusResolver.resolve(order)).isEqualTo("delivered");
    }

    @Test
    void applySalesOrderTracking_paidPendingOrderConfirmsButDoesNotFulfill() {
        CustomerOrder order = paidOrder().status(OrderStatus.PENDING);

        ZohoOrderStatus.applySalesOrderTracking(
            order,
            Map.of("status", "invoiced", "shipped_status", "fulfilled")
        );

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void applySalesOrderTracking_completelyFulfilledWithShipmentStaysConfirmed() {
        CustomerOrder order = paidOrder();

        ZohoOrderStatus.applySalesOrderTracking(
            order,
            Map.of("status", "invoiced", "shipped_status", "fulfilled", "shipment_date", "2026-08-30")
        );

        assertThat(order.getZohoOrderStatus()).isEqualTo("invoiced");
        assertThat(order.getShipmentDate()).isEqualTo("2026-08-30");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void applySalesOrderTracking_invoicedFulfilledWithoutDispatchStaysConfirmed() {
        CustomerOrder order = paidOrder();

        ZohoOrderStatus.applySalesOrderTracking(
            order,
            Map.of("status", "invoiced", "shipped_status", "fulfilled", "date", "2026-08-29")
        );

        assertThat(order.getZohoOrderStatus()).isEqualTo("invoiced");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.getShipmentDate()).isNull();
    }

    @Test
    void applySalesOrderTracking_invoicedFulfilledWithTrackingNumberStaysConfirmed() {
        CustomerOrder order = paidOrder();

        ZohoOrderStatus.applySalesOrderTracking(
            order,
            Map.of("status", "invoiced", "shipped_status", "fulfilled", "tracking_number", "AWB-42")
        );

        assertThat(order.getZohoOrderStatus()).isEqualTo("invoiced");
        assertThat(order.getTrackingNumber()).isEqualTo("AWB-42");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void applySalesOrderTracking_invoicedFulfilledIgnoresStaleLocalTracking() {
        CustomerOrder order = paidOrder().trackingNumber("STALE-AWB");

        ZohoOrderStatus.applySalesOrderTracking(
            order,
            Map.of("status", "invoiced", "shipped_status", "fulfilled", "date", "2026-08-29")
        );

        assertThat(order.getZohoOrderStatus()).isEqualTo("invoiced");
        assertThat(order.getTrackingNumber()).isEqualTo("STALE-AWB");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void applySalesOrderTracking_pendingShipmentClearsStaleShipmentDate() {
        CustomerOrder order = paidOrder().shipmentDate("2026-09-02");

        ZohoOrderStatus.applySalesOrderTracking(
            order,
            Map.of(
                "status",
                "invoiced",
                "shipped_status",
                "pending",
                "line_items",
                List.of(Map.of("quantity_packed", 0, "quantity_shipped", 0))
            )
        );

        assertThat(order.getShipmentDate()).isNull();
        assertThat(OrderTrackingStatusResolver.resolve(order)).isEqualTo("confirmed");
    }

    @Test
    void applySalesOrderTracking_pendingShipmentDemotesStaleHandWrapped() {
        CustomerOrder order = paidOrder().status(OrderStatus.HAND_WRAPPED).shipmentDate("2026-09-02");

        ZohoOrderStatus.applySalesOrderTracking(
            order,
            Map.of(
                "status",
                "invoiced",
                "shipped_status",
                "pending",
                "line_items",
                List.of(Map.of("quantity_packed", 0, "quantity_shipped", 0))
            )
        );

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.getShipmentDate()).isNull();
        assertThat(OrderTrackingStatusResolver.resolve(order)).isEqualTo("confirmed");
    }

    @Test
    void applySalesOrderTracking_notShippedStatusClearsStaleShipmentDate() {
        CustomerOrder order = paidOrder().status(OrderStatus.HAND_WRAPPED).shipmentDate("2026-09-02");

        ZohoOrderStatus.applySalesOrderTracking(
            order,
            Map.of(
                "status",
                "invoiced",
                "shippedStatus",
                "not_shipped",
                "line_items",
                List.of(Map.of("quantity_packed", 0, "quantity_shipped", 0))
            )
        );

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.getShipmentDate()).isNull();
    }

    @Test
    void applySalesOrderTracking_invoicedFulfilledWithCustomFieldAwbStaysConfirmed() {
        CustomerOrder order = paidOrder();

        ZohoOrderStatus.applySalesOrderTracking(
            order,
            Map.of(
                "status",
                "invoiced",
                "shipped_status",
                "fulfilled",
                "custom_fields",
                List.of(Map.of("label", "AWB Number", "value", "CF-AWB-7"))
            )
        );

        assertThat(order.getTrackingNumber()).isEqualTo("CF-AWB-7");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void applySalesOrderTracking_explicitDeliveredStatusMarksFulfilled() {
        CustomerOrder order = paidOrder();

        ZohoOrderStatus.applySalesOrderTracking(order, Map.of("status", "delivered"));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.FULFILLED);
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
    void isDelivered_recognizesOnlyExplicitDeliveryStatus() {
        assertThat(ZohoOrderStatus.isDelivered("delivered")).isTrue();
        assertThat(ZohoOrderStatus.isDelivered("fulfilled")).isFalse();
        assertThat(ZohoOrderStatus.isDelivered("closed")).isFalse();
        assertThat(ZohoOrderStatus.isDelivered("invoiced")).isFalse();
    }

    @Test
    void isShipped_recognizesZohoVariants() {
        assertThat(ZohoOrderStatus.isShipped("shipped")).isTrue();
        assertThat(ZohoOrderStatus.isShipped("in_transit")).isTrue();
        assertThat(ZohoOrderStatus.isShipped("fulfilled")).isFalse();
        assertThat(ZohoOrderStatus.isShipped("invoiced")).isFalse();
    }

    @Test
    void applyDeliveryChallans_nullOrEmptyList_isNoOp() {
        CustomerOrder order = paidOrder();
        ZohoOrderStatus.applyDeliveryChallans(order, null);
        ZohoOrderStatus.applyDeliveryChallans(order, List.of());
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void applySalesOrderTracking_shippedSignalWithoutShipmentDate_usesOrderDateFallback() {
        CustomerOrder order = paidOrder();
        ZohoOrderStatus.applySalesOrderTracking(
            order,
            Map.of("status", "shipped", "shipped_status", "shipped", "date", "2026-05-01")
        );
        assertThat(order.getShipmentDate()).isEqualTo("2026-05-01");
    }

    @Test
    void applyDeliveryChallans_deliveredDoesNotOverrideCancelledOrder() {
        CustomerOrder order = paidOrder().status(OrderStatus.CANCELLED);
        ZohoOrderStatus.applyDeliveryChallans(order, List.of(Map.of("status", "delivered", "tracking_number", "DC-1")));
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getTrackingNumber()).isEqualTo("DC-1");
    }

    @Test
    void applySalesOrderTracking_localForwardShipment_blankStatus_skipsFieldUpdates() {
        CustomerOrder order = paidOrder()
            .trackingNumber("LOCAL")
            .shipmentLogisticsMode(com.miniandmore.app.domain.enumeration.ReturnLogisticsMode.XPRESSBEES);
        ZohoOrderStatus.applySalesOrderTracking(order, Map.of("status", "  ", "tracking_number", "ZOHO-AWB"));
        assertThat(order.getZohoOrderStatus()).isNull();
        assertThat(order.getTrackingNumber()).isEqualTo("LOCAL");
    }

    @Test
    void applySalesOrderTracking_quantityPackedPromotesConfirmedToHandWrapped() {
        CustomerOrder order = paidOrder();
        ZohoOrderStatus.applySalesOrderTracking(
            order,
            Map.of(
                "status",
                "invoiced",
                "shipped_status",
                "pending",
                "line_items",
                List.of(Map.of("quantity_packed", 2, "quantity_shipped", 0))
            )
        );
        assertThat(order.getStatus()).isEqualTo(OrderStatus.HAND_WRAPPED);
    }
}
