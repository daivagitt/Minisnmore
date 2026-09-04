package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.enumeration.OrderStatus;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ZohoInvoiceStatusTest {

    @Test
    void applyInvoice_setsStatusAndInvoiceId() {
        CustomerOrder order = new CustomerOrder();
        order.setStatus(OrderStatus.CONFIRMED);

        boolean changed = ZohoInvoiceStatus.applyInvoice(order, Map.of("invoice_id", "inv-1", "status", "sent"));

        assertThat(changed).isFalse();
        assertThat(order.getZohoInvoiceId()).isEqualTo("inv-1");
        assertThat(order.getZohoOrderStatus()).isEqualTo("sent");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.getZohoTrackingLastPulledAt()).isNotNull();
    }

    @Test
    void applyInvoice_voidCancelsOrder() {
        CustomerOrder order = new CustomerOrder();
        order.setStatus(OrderStatus.CONFIRMED);

        boolean changed = ZohoInvoiceStatus.applyInvoice(order, Map.of("invoice_id", "inv-2", "status", "void"));

        assertThat(changed).isTrue();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getCancellationReason()).isEqualTo("Invoice voided in Zoho");
    }

    @Test
    void applyInvoice_voidedStatusCancelsOrder() {
        CustomerOrder order = new CustomerOrder();
        order.setStatus(OrderStatus.CONFIRMED);

        boolean changed = ZohoInvoiceStatus.applyInvoice(order, Map.of("invoice_id", "inv-2b", "status", "voided"));

        assertThat(changed).isTrue();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void applyInvoice_voidDoesNotCancelFulfilledOrder() {
        CustomerOrder order = new CustomerOrder();
        order.setStatus(OrderStatus.FULFILLED);

        boolean changed = ZohoInvoiceStatus.applyInvoice(order, Map.of("invoice_id", "inv-3", "status", "void"));

        assertThat(changed).isFalse();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.FULFILLED);
    }

    @Test
    void applyInvoice_voidDoesNotCancelAlreadyCancelledOrder() {
        CustomerOrder order = new CustomerOrder();
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancellationReason("Customer requested");

        boolean changed = ZohoInvoiceStatus.applyInvoice(order, Map.of("invoice_id", "inv-4", "status", "void"));

        assertThat(changed).isFalse();
        assertThat(order.getCancellationReason()).isEqualTo("Customer requested");
    }

    @Test
    void shouldCachePdf_forSentAndPaidOnly() {
        assertThat(ZohoInvoiceStatus.shouldCachePdf("sent")).isTrue();
        assertThat(ZohoInvoiceStatus.shouldCachePdf("paid")).isTrue();
        assertThat(ZohoInvoiceStatus.shouldCachePdf("void")).isFalse();
    }

    @Test
    void applyInvoice_sentDoesNotOverwriteShippedZohoOrderStatus() {
        CustomerOrder order = new CustomerOrder();
        order.setStatus(OrderStatus.CONFIRMED);
        order.setZohoOrderStatus("shipped");

        ZohoInvoiceStatus.applyInvoice(order, Map.of("invoice_id", "inv-5", "status", "sent"));

        assertThat(order.getZohoOrderStatus()).isEqualTo("shipped");
    }

    @Test
    void applyInvoice_paidDoesNotOverwriteInTransitZohoOrderStatus() {
        CustomerOrder order = new CustomerOrder();
        order.setStatus(OrderStatus.CONFIRMED);
        order.setZohoOrderStatus("in_transit");

        ZohoInvoiceStatus.applyInvoice(order, Map.of("invoice_id", "inv-6", "status", "paid"));

        assertThat(order.getZohoOrderStatus()).isEqualTo("in_transit");
    }

    @Test
    void applyInvoice_voidStillOverwritesShippedZohoOrderStatus() {
        CustomerOrder order = new CustomerOrder();
        order.setStatus(OrderStatus.CONFIRMED);
        order.setZohoOrderStatus("shipped");

        ZohoInvoiceStatus.applyInvoice(order, Map.of("invoice_id", "inv-7", "status", "void"));

        assertThat(order.getZohoOrderStatus()).isEqualTo("void");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void applyInvoice_sentStillSetsZohoOrderStatusWhenNoFulfillmentStatusPresent() {
        CustomerOrder order = new CustomerOrder();
        order.setStatus(OrderStatus.CONFIRMED);

        ZohoInvoiceStatus.applyInvoice(order, Map.of("invoice_id", "inv-8", "status", "sent"));

        assertThat(order.getZohoOrderStatus()).isEqualTo("sent");
    }
}
