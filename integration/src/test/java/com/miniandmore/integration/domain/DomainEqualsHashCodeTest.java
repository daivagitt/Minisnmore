package com.miniandmore.integration.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.miniandmore.integration.domain.enumeration.OrderStatus;
import com.miniandmore.integration.domain.enumeration.PaymentStatus;
import com.miniandmore.integration.domain.enumeration.ZohoSyncStatus;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class DomainEqualsHashCodeTest {

    @Test
    void product_equalsHashCodeAndToString() {
        Product a = new Product();
        Product b = new Product();
        assertThat(a).isNotEqualTo(b);

        a.setId(1L);
        b.setId(1L);
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
        assertThat(a).isNotEqualTo(new Product());

        a.setSlug("dress");
        a.setName("Dress");
        a.setSku("SKU");
        a.setFabricType("Cotton");
        a.setDescription("d");
        a.setStory("s");
        a.setPrice(BigDecimal.TEN);
        a.setCompareAtPrice(BigDecimal.ONE);
        a.setCurrency("INR");
        a.setAgeMinMonths(0);
        a.setAgeMaxMonths(24);
        a.setSizesJson("[]");
        a.setColorsJson("[]");
        a.setImagesJson("[]");
        a.setBadge("new");
        a.setStock(3);
        a.setSortOrder(1);
        a.setFeatured(true);
        a.setTrending(false);
        a.setNewArrival(true);
        a.setBestSeller(false);
        a.setActive(true);
        a.setZohoItemId("z");
        a.setZohoSyncStatus(ZohoSyncStatus.SYNCED);
        a.setZohoLastSyncedAt(Instant.now());
        a.setZohoSyncError("err");
        assertThat(a.toString()).contains("Dress");
    }

    @Test
    void customerOrder_equalsHashCodeAndToString() {
        CustomerOrder a = new CustomerOrder();
        CustomerOrder b = new CustomerOrder();
        assertThat(a).isNotEqualTo(b);

        a.setId(2L);
        b.setId(2L);
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());

        a.setUserLogin("u");
        a.setOrderNumber("O-1");
        a.setStatus(OrderStatus.PENDING);
        a.setPaymentStatus(PaymentStatus.PAID);
        a.setRazorpayOrderId("ro");
        a.setRazorpayPaymentId("rp");
        a.setRazorpaySignature("sig");
        a.setSubtotal(BigDecimal.ONE);
        a.setDiscount(BigDecimal.ZERO);
        a.setShipping(BigDecimal.ZERO);
        a.setTax(BigDecimal.ZERO);
        a.setDonation(BigDecimal.ZERO);
        a.setTotal(BigDecimal.ONE);
        a.setCouponCode("C");
        a.setShippingAddressJson("{}");
        a.setNotes("n");
        a.setZohoSalesOrderId("so");
        a.setZohoInvoiceId("inv");
        a.setZohoSyncStatus(ZohoSyncStatus.PENDING);
        a.setZohoSyncError("e");
        a.setZohoLastSyncedAt(Instant.now());
        a.setTrackingNumber("t");
        a.setTrackingCarrier("c");
        a.setShipmentDate("2026-01-01");
        a.setZohoOrderStatus("open");
        a.setZohoTrackingLastPulledAt(Instant.now());
        assertThat(a.toString()).contains("O-1");
    }

    @Test
    void category_equalsHashCodeAndToString() {
        Category a = new Category();
        Category b = new Category();
        a.setId(3L);
        b.setId(3L);
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
        a.setSlug("tops");
        a.setName("Tops");
        a.setDescription("d");
        a.setImageUrl("https://x.com/i.jpg");
        a.setSortOrder(1);
        a.setActive(true);
        assertThat(a.toString()).contains("Tops");
    }

    @Test
    void orderItem_equalsHashCodeAndToString() {
        OrderItem a = new OrderItem();
        OrderItem b = new OrderItem();
        a.setId(4L);
        b.setId(4L);
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
        a.setProductId("10");
        a.setProductSlug("hat");
        a.setProductName("Hat");
        a.setItemSize("M");
        a.setColor("Blue");
        a.setQty(2);
        a.setPrice(BigDecimal.ONE);
        CustomerOrder order = new CustomerOrder().id(99L);
        a.setOrder(order);
        assertThat(a.getOrder()).isEqualTo(order);
        assertThat(a.toString()).contains("Hat");
    }

    @Test
    void zohoSetting_equalsBranches() {
        ZohoSetting withId = new ZohoSetting().id(5L);
        ZohoSetting same = new ZohoSetting().id(5L);
        ZohoSetting other = new ZohoSetting().id(6L);
        assertThat(withId).isEqualTo(same).isNotEqualTo(other);
        assertThat(withId.hashCode()).isEqualTo(same.hashCode());
    }

    @Test
    void abstractAuditingEntity_setters() {
        TestAudited entity = new TestAudited();
        Instant now = Instant.now();
        entity.setCreatedBy("admin");
        entity.setCreatedDate(now);
        entity.setLastModifiedBy("admin");
        entity.setLastModifiedDate(now);
        assertThat(entity.getCreatedBy()).isEqualTo("admin");
        assertThat(entity.getLastModifiedDate()).isEqualTo(now);
    }

    static class TestAudited extends AbstractAuditingEntity<Long> {

        @Override
        public Long getId() {
            return 1L;
        }
    }
}
