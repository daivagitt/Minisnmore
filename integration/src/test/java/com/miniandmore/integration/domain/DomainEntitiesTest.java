package com.miniandmore.integration.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.miniandmore.integration.domain.enumeration.DiscountType;
import com.miniandmore.integration.domain.enumeration.OrderReturnStatus;
import com.miniandmore.integration.domain.enumeration.OrderStatus;
import com.miniandmore.integration.domain.enumeration.PaymentStatus;
import com.miniandmore.integration.domain.enumeration.ZohoSyncStatus;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class DomainEntitiesTest {

    @Test
    void zohoSetting_fluentSetters() {
        ZohoSetting setting = new ZohoSetting().id(1L).orgId("org").connected(true).dataCenter("in");
        assertThat(setting.getId()).isEqualTo(1L);
        assertThat(setting.getOrgId()).isEqualTo("org");
        assertThat(setting.getConnected()).isTrue();
    }

    @Test
    void product_fluentSetters() {
        Product product = new Product().id(2L).name("Dress").slug("dress").sku("SKU").active(true);
        assertThat(product.getName()).isEqualTo("Dress");
        assertThat(product.getSlug()).isEqualTo("dress");
    }

    @Test
    void customerOrder_fluentSetters() {
        CustomerOrder order = new CustomerOrder()
            .id(3L)
            .orderNumber("O-1")
            .status(OrderStatus.PENDING)
            .paymentStatus(PaymentStatus.PAID)
            .total(BigDecimal.TEN);
        assertThat(order.getOrderNumber()).isEqualTo("O-1");
        assertThat(order.getTotal()).isEqualByComparingTo(BigDecimal.TEN);
    }

    @Test
    void category_andOrderItem() {
        Category category = new Category().id(4L).name("Tops").slug("tops").active(true);
        assertThat(category.getName()).isEqualTo("Tops");

        OrderItem item = new OrderItem().id(5L).productName("Hat").qty(2).price(BigDecimal.ONE);
        assertThat(item.getQty()).isEqualTo(2);
    }

    @Test
    void enumerations_haveValues() {
        assertThat(OrderStatus.values()).contains(OrderStatus.CONFIRMED);
        assertThat(PaymentStatus.values()).contains(PaymentStatus.PAID);
        assertThat(ZohoSyncStatus.values()).contains(ZohoSyncStatus.SYNCED);
        assertThat(DiscountType.values()).isNotEmpty();
        assertThat(OrderReturnStatus.values()).isNotEmpty();
    }
}
