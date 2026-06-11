package com.miniandmore.integration.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class CustomerOrderItemsTest {

    @Test
    void itemses_addAndRemoveItems() {
        CustomerOrder order = new CustomerOrder().id(1L);
        order.setItemses(new java.util.HashSet<>());
        OrderItem item = new OrderItem().id(2L).productName("Hat").qty(1).price(BigDecimal.ONE);

        order.addItems(item);
        assertThat(order.getItemses()).contains(item);
        assertThat(item.getOrder()).isEqualTo(order);

        order.removeItems(item);
        assertThat(order.getItemses()).doesNotContain(item);
        assertThat(item.getOrder()).isNull();
    }

    @Test
    void itemses_replacesCollection() {
        CustomerOrder order = new CustomerOrder().id(3L);
        OrderItem a = new OrderItem().id(4L);
        OrderItem b = new OrderItem().id(5L);
        order.setItemses(new java.util.HashSet<>(java.util.Set.of(a)));
        order.itemses(new java.util.HashSet<>(java.util.Set.of(b)));

        assertThat(order.getItemses()).containsExactly(b);
        assertThat(b.getOrder()).isEqualTo(order);
        assertThat(a.getOrder()).isNull();
    }
}
