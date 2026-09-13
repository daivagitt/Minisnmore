package com.miniandmore.app.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.miniandmore.app.domain.enumeration.OrderStatus;
import com.miniandmore.app.domain.enumeration.PaymentStatus;
import com.miniandmore.app.domain.enumeration.LedgerlySyncStatus;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class DomainEntityAdditionalTest {

    @Test
    void product_allFlagsAndJsonFields() {
        Product product = new Product()
            .description("desc")
            .story("story")
            .price(BigDecimal.TEN)
            .compareAtPrice(BigDecimal.valueOf(20))
            .sizesJson("[]")
            .colorsJson("[]")
            .imagesJson("[]")
            .featured(true)
            .trending(true)
            .newArrival(true)
            .bestSeller(true)
            .active(true)
            .ledgerlyItemId("z1")
            .ledgerlySyncStatus(LedgerlySyncStatus.PENDING)
            .ledgerlyLastSyncedAt(Instant.now())
            .ledgerlySyncError("err");

        assertThat(product.getDescription()).isEqualTo("desc");
        assertThat(product.getFeatured()).isTrue();
        assertThat(product.toString()).contains("name=");
    }

    @Test
    void customerOrder_trackingAndLedgerlyFields() {
        CustomerOrder order = new CustomerOrder()
            .status(OrderStatus.FULFILLED)
            .paymentStatus(PaymentStatus.PAID)
            .subtotal(BigDecimal.TEN)
            .discount(BigDecimal.ONE)
            .shipping(BigDecimal.ONE)
            .tax(BigDecimal.ONE)
            .donation(BigDecimal.ZERO)
            .total(BigDecimal.valueOf(13))
            .ledgerlyOrderStatus("shipped")
            .trackingCarrier("Delhivery")
            .trackingNumber("TN1")
            .shipmentDate("2026-01-01")
            .ledgerlyTrackingLastPulledAt(Instant.now())
            .inventoryReserved(true)
            .inventoryReservedUntil(Instant.now())
            .inventoryReleasedAt(Instant.now());

        assertThat(order.getTrackingCarrier()).isEqualTo("Delhivery");
        assertThat(order.getLedgerlyOrderStatus()).isEqualTo("shipped");
        assertThat(order.toString()).contains("orderNumber");
    }

    @Test
    void siteSetting_commerceAndSubscribeFields() {
        SiteSetting setting = new SiteSetting()
            .gstRate(new BigDecimal("18"))
            .pricesIncludeGst(false)
            .shippingStandardInr(99)
            .shippingExpressInr(150)
            .freeShippingMinInr(2500);
        setting.setSubscribeHeroTitle("Hero");
        setting.setSubscribePlansJson("[]");
        setting.setAboutPageJson("{}");

        assertThat(setting.getGstRate()).isEqualByComparingTo("18");
        assertThat(setting.getSubscribeHeroTitle()).isEqualTo("Hero");
        assertThat(setting.getAboutPageJson()).isEqualTo("{}");
        assertThat(setting.toString()).contains("brandName");
    }
}
