package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.enumeration.OrderStatus;
import com.miniandmore.app.domain.enumeration.PaymentStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** Covers JaCoCo-missed branches in {@link ZohoOrderStatus} field variants and shipment signals. */
class ZohoOrderStatusBranchCoverageTest {

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

    @ParameterizedTest
    @MethodSource("deliveredFieldVariants")
    void indicatesDelivery_recognizesExplicitDeliveredTokens(String field, String value) {
        assertThat(ZohoOrderStatus.indicatesDelivery(Map.of(field, value))).isTrue();
    }

    private static Stream<Arguments> deliveredFieldVariants() {
        return Stream.of(
            Arguments.of("detailedStatus", "delivered"),
            Arguments.of("status_message", "delivered"),
            Arguments.of("statusMessage", "delivered"),
            Arguments.of("current_status", "delivered"),
            Arguments.of("currentStatus", "delivered"),
            Arguments.of("delivery_status", "delivered"),
            Arguments.of("deliveryStatus", "delivered"),
            Arguments.of("cfMinimsDeliveryStatus", "delivered"),
            Arguments.of("shipmentStatus", "delivered"),
            Arguments.of("status_formatted", "delivered"),
            Arguments.of("shipment_status_formatted", "delivered")
        );
    }

    @ParameterizedTest
    @MethodSource("fulfilledNativeCompletionFields")
    void indicatesShipmentDelivery_recognizesFulfilledAcrossNativeFields(String field, String value) {
        assertThat(ZohoOrderStatus.indicatesShipmentDelivery(Map.of(field, value))).isTrue();
    }

    private static Stream<Arguments> fulfilledNativeCompletionFields() {
        return Stream.of(
            Arguments.of("detailedStatus", "fulfilled"),
            Arguments.of("shipment_status", "fulfilled"),
            Arguments.of("shipmentStatus", "fulfilled"),
            Arguments.of("status_message", "fulfilled"),
            Arguments.of("statusMessage", "fulfilled")
        );
    }

    @Test
    void indicatesDelivery_trueWhenShipmentDeliveredDateFormattedPresent() {
        assertThat(ZohoOrderStatus.indicatesDelivery(Map.of("shipment_delivered_date_formatted", "2026-01-02"))).isTrue();
    }

    @ParameterizedTest
    @MethodSource("packageShipmentSignalFields")
    void hasPackageShipmentSignal_trueForShippedAndInTransitTokens(String field, String value) {
        Boolean signal = ReflectionTestUtils.invokeMethod(ZohoOrderStatus.class, "hasPackageShipmentSignal", Map.of(field, value));
        assertThat(signal).isTrue();
    }

    @ParameterizedTest
    @MethodSource("shippedSignalSources")
    void hasShippedSignal_detectsShipmentMarkers(Map<String, Object> salesOrder, boolean expected) {
        Boolean shipped = ReflectionTestUtils.invokeMethod(ZohoOrderStatus.class, "hasShippedSignal", salesOrder);
        assertThat(shipped).isEqualTo(expected);
    }

    private static Stream<Arguments> shippedSignalSources() {
        return Stream.of(
            Arguments.of(Map.of("shipment_date", "2026-01-01"), true),
            Arguments.of(Map.of("shipped_status", "partially_shipped"), true),
            Arguments.of(Map.of("shipped_status", "pending"), false),
            Arguments.of(
                Map.of("sub_statuses", List.of(Map.of("status_code", "in_transit"))),
                true
            ),
            Arguments.of(Map.of("sub_statuses", List.of("not-a-map")), false)
        );
    }

    private static Stream<Arguments> packageShipmentSignalFields() {
        return Stream.of(
            Arguments.of("status", "shipped"),
            Arguments.of("detailed_status", "shipped"),
            Arguments.of("shipment_status", "in_transit"),
            Arguments.of("shipmentStatus", "in_transit")
        );
    }

    @Test
    void hasPackageShipmentSignal_falseForNullEmptyOrMatchingShipmentIds() {
        Boolean nullPkg = ReflectionTestUtils.invokeMethod(ZohoOrderStatus.class, "hasPackageShipmentSignal", (Object) null);
        Boolean empty = ReflectionTestUtils.invokeMethod(ZohoOrderStatus.class, "hasPackageShipmentSignal", Map.of());
        Boolean sameIds = ReflectionTestUtils.invokeMethod(
            ZohoOrderStatus.class,
            "hasPackageShipmentSignal",
            Map.of("shipment_id", "same", "shipment_order_id", "same", "status", "open")
        );
        assertThat(nullPkg).isFalse();
        assertThat(empty).isFalse();
        assertThat(sameIds).isFalse();
    }

    @Test
    void hasPackageShipmentSignal_trueForDistinctShipmentIdsAndInTransitVariants() {
        Boolean byShipmentId = ReflectionTestUtils.invokeMethod(
            ZohoOrderStatus.class,
            "hasPackageShipmentSignal",
            Map.of("shipment_id", "ship-1", "shipment_order_id", "order-1")
        );
        Boolean byDetailedStatusCamel = ReflectionTestUtils.invokeMethod(
            ZohoOrderStatus.class,
            "hasPackageShipmentSignal",
            Map.of("detailedStatus", "in_transit")
        );
        Boolean byShipmentStatus = ReflectionTestUtils.invokeMethod(
            ZohoOrderStatus.class,
            "hasPackageShipmentSignal",
            Map.of("shipmentStatus", "shipped")
        );
        assertThat(byShipmentId).isTrue();
        assertThat(byDetailedStatusCamel).isTrue();
        assertThat(byShipmentStatus).isTrue();
    }

    @Test
    void applySalesOrderTracking_subStatusTransitSetsShipmentDateFromOrderDate() {
        CustomerOrder order = paidOrder();
        ZohoOrderStatus.applySalesOrderTracking(
            order,
            Map.of(
                "status",
                "invoiced",
                "date",
                "2026-04-01",
                "sub_statuses",
                List.of(Map.of("status_code", "in_transit"))
            )
        );
        assertThat(order.getShipmentDate()).isEqualTo("2026-04-01");
    }

    @Test
    void applySalesOrderTracking_customFieldCarrierSetsTrackingCarrier() {
        CustomerOrder order = paidOrder();
        ZohoOrderStatus.applySalesOrderTracking(
            order,
            Map.of(
                "status",
                "invoiced",
                "custom_fields",
                List.of(Map.of("label", "Courier partner", "value", "Delhivery"))
            )
        );
        assertThat(order.getTrackingCarrier()).isEqualTo("Delhivery");
    }

    @Test
    void applySalesOrderTracking_shippedStatusDeliveredMarksFulfilled() {
        CustomerOrder order = paidOrder();
        ZohoOrderStatus.applySalesOrderTracking(order, Map.of("status", "invoiced", "shippedStatus", "delivered"));
        assertThat(order.getStatus()).isEqualTo(OrderStatus.FULFILLED);
    }

    @Test
    void applySalesOrderTracking_voidStatusCancelsOrder() {
        CustomerOrder order = paidOrder();
        ZohoOrderStatus.applySalesOrderTracking(order, Map.of("status", "void"));
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void applySalesOrderTracking_partiallyShippedStatusCountsAsShippedSignal() {
        CustomerOrder order = paidOrder();
        ZohoOrderStatus.applySalesOrderTracking(
            order,
            Map.of("status", "invoiced", "shipped_status", "partially_shipped", "date", "2026-03-15")
        );
        assertThat(order.getShipmentDate()).isEqualTo("2026-03-15");
    }

    @Test
    void applyDeliveryChallans_usesReferenceNumberWhenTrackingMissing() {
        CustomerOrder order = paidOrder();
        ZohoOrderStatus.applyDeliveryChallans(order, List.of(Map.of("status", "open", "reference_number", "REF-77")));
        assertThat(order.getTrackingNumber()).isEqualTo("REF-77");
    }

    @Test
    void applySalesOrderTracking_parsesStringLineQuantityForPackedPromotion() {
        CustomerOrder order = paidOrder();
        ZohoOrderStatus.applySalesOrderTracking(
            order,
            Map.of(
                "status",
                "invoiced",
                "shipped_status",
                "pending",
                "line_items",
                List.of(Map.of("quantity_packed", "2", "quantity_shipped", "0"))
            )
        );
        assertThat(order.getStatus()).isEqualTo(OrderStatus.HAND_WRAPPED);
    }
}
