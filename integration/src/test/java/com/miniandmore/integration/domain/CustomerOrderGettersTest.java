package com.miniandmore.integration.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.miniandmore.integration.domain.enumeration.OrderStatus;
import com.miniandmore.integration.domain.enumeration.PaymentStatus;
import com.miniandmore.integration.domain.enumeration.ZohoSyncStatus;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class CustomerOrderGettersTest {

    @Test
    void allGettersReturnSetValues() {
        CustomerOrder order = new CustomerOrder();
        Instant now = Instant.now();
        order.setId(99L);
        order.setUserLogin("login");
        order.setOrderNumber("ORD");
        order.setStatus(OrderStatus.CANCELLED);
        order.setPaymentStatus(PaymentStatus.FAILED);
        order.setRazorpayOrderId("ro");
        order.setRazorpayPaymentId("rp");
        order.setRazorpaySignature("sig");
        order.setSubtotal(BigDecimal.ONE);
        order.setDiscount(BigDecimal.ONE);
        order.setShipping(BigDecimal.ONE);
        order.setTax(BigDecimal.ONE);
        order.setDonation(BigDecimal.ONE);
        order.setTotal(BigDecimal.ONE);
        order.setCouponCode("C");
        order.setShippingAddressJson("{}");
        order.setNotes("n");
        order.setZohoSalesOrderId("so");
        order.setZohoInvoiceId("inv");
        order.setZohoSyncStatus(ZohoSyncStatus.FAILED);
        order.setZohoSyncError("err");
        order.setZohoLastSyncedAt(now);
        order.setZohoOrderStatus("void");
        order.setTrackingNumber("t");
        order.setTrackingCarrier("c");
        order.setShipmentDate("d");
        order.setZohoTrackingLastPulledAt(now);

        assertThat(order.getId()).isEqualTo(99L);
        assertThat(order.getUserLogin()).isEqualTo("login");
        assertThat(order.getRazorpaySignature()).isEqualTo("sig");
        assertThat(order.getCouponCode()).isEqualTo("C");
        assertThat(order.getNotes()).isEqualTo("n");
        assertThat(order.getZohoSyncError()).isEqualTo("err");
        assertThat(order.getZohoTrackingLastPulledAt()).isEqualTo(now);
        assertThat(order.toString()).contains("ORD");
    }
}
