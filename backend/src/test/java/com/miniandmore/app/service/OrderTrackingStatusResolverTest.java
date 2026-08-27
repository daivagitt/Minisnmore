package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.enumeration.OrderStatus;
import com.miniandmore.app.domain.enumeration.PaymentStatus;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class OrderTrackingStatusResolverTest {

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
    void resolve_cancelledOrder() {
        CustomerOrder order = paidOrder().status(OrderStatus.CANCELLED);

        assertThat(OrderTrackingStatusResolver.resolve(order)).isEqualTo("cancelled");
    }

    @Test
    void resolve_unpaidOrder() {
        CustomerOrder order = paidOrder().status(OrderStatus.PENDING).paymentStatus(PaymentStatus.PENDING);

        assertThat(OrderTrackingStatusResolver.resolve(order)).isEqualTo("pending");
    }

    @Test
    void resolve_paidPendingOrderDoesNotImplicitlyBecomeHandWrapped() {
        CustomerOrder order = paidOrder().status(OrderStatus.PENDING);

        assertThat(OrderTrackingStatusResolver.resolve(order)).isEqualTo("confirmed");
    }

    @Test
    void resolve_shippedCodOrderWhilePaymentIsPending() {
        CustomerOrder order = paidOrder()
            .paymentStatus(PaymentStatus.PENDING)
            .paymentMethod("cod")
            .ledgerlyOrderStatus("shipped");

        assertThat(OrderTrackingStatusResolver.resolve(order)).isEqualTo("shipped");
    }

    @Test
    void resolve_fulfilledOrder() {
        CustomerOrder order = paidOrder().status(OrderStatus.FULFILLED);

        assertThat(OrderTrackingStatusResolver.resolve(order)).isEqualTo("delivered");
    }

    @Test
    void resolve_shippedWhenTrackingPresent() {
        CustomerOrder order = paidOrder().trackingNumber("TRACK-1");

        assertThat(OrderTrackingStatusResolver.resolve(order)).isEqualTo("shipped");
    }

    @Test
    void resolve_confirmedForConfirmedPaidOrder() {
        CustomerOrder order = paidOrder().status(OrderStatus.CONFIRMED);

        assertThat(OrderTrackingStatusResolver.resolve(order)).isEqualTo("confirmed");
    }

    @Test
    void resolve_handWrappedForExplicitBackendState() {
        CustomerOrder order = paidOrder().status(OrderStatus.HAND_WRAPPED);

        assertThat(OrderTrackingStatusResolver.resolve(order)).isEqualTo("hand_wrapped");
    }

    @Test
    void resolve_deliveredWhenLedgerlyReportsDelivered() {
        CustomerOrder order = paidOrder().ledgerlyOrderStatus("delivered");

        assertThat(OrderTrackingStatusResolver.resolve(order)).isEqualTo("delivered");
    }

    @Test
    void resolve_shippedWhenLedgerlyReportsInTransit() {
        CustomerOrder order = paidOrder().ledgerlyOrderStatus("in_transit");

        assertThat(OrderTrackingStatusResolver.resolve(order)).isEqualTo("shipped");
    }

    @Test
    void resolve_shippedWhenZohoReportsShipped() {
        CustomerOrder order = paidOrder().zohoOrderStatus("shipped");

        assertThat(OrderTrackingStatusResolver.resolve(order)).isEqualTo("shipped");
    }

    @Test
    void resolve_confirmedWhenZohoOrderClosedButNotShipped() {
        CustomerOrder order = paidOrder().zohoOrderStatus("closed");

        assertThat(OrderTrackingStatusResolver.resolve(order)).isEqualTo("confirmed");
    }
}
