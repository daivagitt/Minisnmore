package com.miniandmore.integration.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CustomerOrderEqualsTest {

    @Test
    void equalsById_andHashCodeStable() {
        CustomerOrder a = new CustomerOrder();
        a.setId(1L);
        CustomerOrder b = new CustomerOrder();
        b.setId(1L);
        CustomerOrder otherType = new CustomerOrder();

        assertThat(a).isEqualTo(b);
        assertThat(a).isNotEqualTo(otherType);
        assertThat(a).isNotEqualTo("x");
        assertThat(a.hashCode()).isEqualTo(CustomerOrder.class.hashCode());
    }
}
