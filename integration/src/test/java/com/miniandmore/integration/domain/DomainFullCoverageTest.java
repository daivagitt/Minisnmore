package com.miniandmore.integration.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.miniandmore.integration.domain.enumeration.OrderStatus;
import com.miniandmore.integration.domain.enumeration.PaymentStatus;
import com.miniandmore.integration.domain.enumeration.ZohoSyncStatus;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class DomainFullCoverageTest {

    @Test
    void zohoSetting_equalsHashCodeToString() {
        ZohoSetting a = new ZohoSetting().id(1L).orgId("o").connected(true);
        ZohoSetting b = new ZohoSetting().id(1L);
        ZohoSetting c = new ZohoSetting().id(2L);
        assertThat(a).isEqualTo(b).isNotEqualTo(c);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
        assertThat(a.toString()).contains("ZohoSetting");
    }

    @Test
    void category_fullFluentApi() {
        Category category = new Category()
            .id(1L)
            .name("Tops")
            .slug("tops")
            .description("desc")
            .sortOrder(1)
            .active(true);
        assertThat(category.getName()).isEqualTo("Tops");
        assertThat(category.toString()).contains("Category");
        Category same = new Category().id(1L);
        assertThat(category).isEqualTo(same);
    }

    @Test
    void product_fullFluentApi() {
        Category category = new Category().id(9L);
        Product product = new Product()
            .id(2L)
            .slug("dress")
            .name("Dress")
            .sku("SKU")
            .fabricType("Cotton")
            .description("d")
            .story("s")
            .price(BigDecimal.TEN)
            .compareAtPrice(BigDecimal.ONE)
            .currency("INR")
            .ageMinMonths(0)
            .ageMaxMonths(24)
            .sizesJson("[\"S\"]")
            .colorsJson("[\"Red\"]")
            .imagesJson("[\"https://x.com/a.jpg\"]")
            .badge("New")
            .stock(5)
            .sortOrder(1)
            .featured(true)
            .trending(false)
            .newArrival(true)
            .bestSeller(false)
            .active(true)
            .zohoItemId("z-1")
            .zohoSyncStatus(ZohoSyncStatus.SYNCED)
            .zohoLastSyncedAt(Instant.now())
            .zohoSyncError(null)
            .category(category);
        assertThat(product.getCategory()).isEqualTo(category);
        assertThat(product).isEqualTo(new Product().id(2L));
        assertThat(product.toString()).contains("Dress");
    }

    @Test
    void customerOrder_withItems() {
        CustomerOrder order = new CustomerOrder().id(10L).orderNumber("O-10");
        OrderItem item = new OrderItem().id(11L).productName("Hat");
        order.setItemses(new java.util.HashSet<>(java.util.Set.of(item)));
        assertThat(order.getItemses()).hasSize(1);
        assertThat(order.getItemses().iterator().next().getOrder()).isEqualTo(order);
    }

    @Test
    void customerOrder_fullFluentApi() {
        CustomerOrder order = new CustomerOrder()
            .id(3L)
            .userLogin("user")
            .orderNumber("O-1")
            .status(OrderStatus.CONFIRMED)
            .paymentStatus(PaymentStatus.PAID)
            .razorpayOrderId("ro")
            .razorpayPaymentId("rp")
            .razorpaySignature("sig")
            .subtotal(BigDecimal.TEN)
            .discount(BigDecimal.ONE)
            .shipping(BigDecimal.ZERO)
            .tax(BigDecimal.ZERO)
            .donation(BigDecimal.ZERO)
            .total(BigDecimal.valueOf(9))
            .couponCode("SAVE")
            .shippingAddressJson("{}")
            .notes("n")
            .zohoSalesOrderId("so")
            .zohoInvoiceId("inv")
            .zohoSyncStatus(ZohoSyncStatus.PENDING)
            .zohoSyncError("err")
            .zohoLastSyncedAt(Instant.now())
            .trackingNumber("TN")
            .trackingCarrier("DHL")
            .shipmentDate("2026-01-01")
            .zohoOrderStatus("open")
            .zohoTrackingLastPulledAt(Instant.now());
        assertThat(order.getOrderNumber()).isEqualTo("O-1");
        assertThat(order).isEqualTo(new CustomerOrder().id(3L));
        assertThat(order.toString()).contains("O-1");
    }

    @Test
    void orderItem_fullFluentApi() {
        OrderItem item = new OrderItem()
            .id(4L)
            .productId("10")
            .productSlug("hat")
            .productName("Hat")
            .itemSize("M")
            .color("Blue")
            .qty(2)
            .price(BigDecimal.ONE);
        assertThat(item.getProductName()).isEqualTo("Hat");
        assertThat(item).isEqualTo(new OrderItem().id(4L));
        assertThat(item.toString()).contains("Hat");
    }

    @Test
    void abstractAuditingEntity_setters() {
        TestAudited audited = new TestAudited();
        audited.setCreatedBy("admin");
        audited.setCreatedDate(Instant.now());
        audited.setLastModifiedBy("admin");
        audited.setLastModifiedDate(Instant.now());
        assertThat(audited.getCreatedBy()).isEqualTo("admin");
    }

    static class TestAudited extends AbstractAuditingEntity<Long> {

        @Override
        public Long getId() {
            return 1L;
        }
    }
}
