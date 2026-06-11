package com.miniandmore.app.domain;

import static com.miniandmore.app.domain.CustomerOrderTestSamples.*;
import static com.miniandmore.app.domain.OrderItemTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.miniandmore.app.web.rest.TestUtil;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CustomerOrderTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(CustomerOrder.class);
        CustomerOrder customerOrder1 = getCustomerOrderSample1();
        CustomerOrder customerOrder2 = new CustomerOrder();
        assertThat(customerOrder1).isNotEqualTo(customerOrder2);

        customerOrder2.setId(customerOrder1.getId());
        assertThat(customerOrder1).isEqualTo(customerOrder2);

        customerOrder2 = getCustomerOrderSample2();
        assertThat(customerOrder1).isNotEqualTo(customerOrder2);
    }

    @Test
    void itemsTest() {
        CustomerOrder customerOrder = getCustomerOrderRandomSampleGenerator();
        OrderItem orderItemBack = getOrderItemRandomSampleGenerator();

        customerOrder.addItems(orderItemBack);
        assertThat(customerOrder.getItemses()).containsOnly(orderItemBack);
        assertThat(orderItemBack.getOrder()).isEqualTo(customerOrder);

        customerOrder.removeItems(orderItemBack);
        assertThat(customerOrder.getItemses()).doesNotContain(orderItemBack);
        assertThat(orderItemBack.getOrder()).isNull();

        customerOrder.itemses(new HashSet<>(Set.of(orderItemBack)));
        assertThat(customerOrder.getItemses()).containsOnly(orderItemBack);
        assertThat(orderItemBack.getOrder()).isEqualTo(customerOrder);

        customerOrder.setItemses(new HashSet<>());
        assertThat(customerOrder.getItemses()).doesNotContain(orderItemBack);
        assertThat(orderItemBack.getOrder()).isNull();
    }
}
