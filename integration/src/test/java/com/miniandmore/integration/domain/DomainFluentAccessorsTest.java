package com.miniandmore.integration.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.miniandmore.integration.domain.enumeration.OrderStatus;
import com.miniandmore.integration.domain.enumeration.PaymentStatus;
import com.miniandmore.integration.domain.enumeration.ZohoSyncStatus;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class DomainFluentAccessorsTest {

    @Test
    void product_fluentChain() {
        Category category = new Category().id(1L).name("Tops");
        Product product = new Product()
            .id(10L)
            .slug("dress")
            .name("Dress")
            .sku("SKU-10")
            .fabricType("Cotton")
            .description("desc")
            .story("story")
            .price(BigDecimal.TEN)
            .compareAtPrice(BigDecimal.ONE)
            .currency("INR")
            .ageMinMonths(0)
            .ageMaxMonths(24)
            .sizesJson("[]")
            .colorsJson("[]")
            .imagesJson("[]")
            .badge("new")
            .stock(2)
            .sortOrder(1)
            .featured(true)
            .trending(false)
            .newArrival(true)
            .bestSeller(false)
            .active(true)
            .category(category)
            .zohoItemId("z-10")
            .zohoSyncStatus(ZohoSyncStatus.SYNCED)
            .zohoLastSyncedAt(Instant.now())
            .zohoSyncError("none");

        assertThat(product.getCategory()).isEqualTo(category);
        assertThat(product.getZohoItemId()).isEqualTo("z-10");
    }

    @Test
    void customerOrder_fluentChain() {
        CustomerOrder order = new CustomerOrder()
            .id(20L)
            .userLogin("buyer")
            .orderNumber("O-20")
            .status(OrderStatus.CONFIRMED)
            .paymentStatus(PaymentStatus.PAID)
            .razorpayOrderId("ro")
            .razorpayPaymentId("rp")
            .razorpaySignature("sig")
            .subtotal(BigDecimal.ONE)
            .discount(BigDecimal.ZERO)
            .shipping(BigDecimal.ZERO)
            .tax(BigDecimal.ZERO)
            .donation(BigDecimal.ZERO)
            .total(BigDecimal.ONE)
            .couponCode("SAVE")
            .shippingAddressJson("{}")
            .notes("note")
            .zohoSalesOrderId("so")
            .zohoInvoiceId("inv")
            .zohoSyncStatus(ZohoSyncStatus.SYNCED)
            .zohoSyncError(null)
            .zohoLastSyncedAt(Instant.now())
            .zohoOrderStatus("open")
            .trackingNumber("TN")
            .trackingCarrier("C")
            .shipmentDate("2026-01-01");

        assertThat(order.getOrderNumber()).isEqualTo("O-20");
        assertThat(order.getTrackingNumber()).isEqualTo("TN");
    }

    @Test
    void category_fluentChain() {
        Category category = new Category().id(3L).slug("hats").name("Hats").description("d").imageUrl("u").sortOrder(2).active(true);
        assertThat(category.getSlug()).isEqualTo("hats");
    }

    @Test
    void orderItem_fluentChain() {
        CustomerOrder order = new CustomerOrder().id(30L);
        OrderItem item = new OrderItem()
            .id(31L)
            .productId("10")
            .productSlug("hat")
            .productName("Hat")
            .itemSize("M")
            .color("Blue")
            .qty(2)
            .price(BigDecimal.ONE)
            .order(order);
        assertThat(item.getOrder()).isEqualTo(order);
        assertThat(item.getProductName()).isEqualTo("Hat");
    }
}
