package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.OrderItem;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OrderMerchandiseAmountsTest {

    @Test
    void merchandiseRefundForQuantities_matchesPackageShortShipScenario() {
        CustomerOrder order = new CustomerOrder()
            .subtotal(new BigDecimal("300.00"))
            .discount(new BigDecimal("30.00"));
        OrderItem lineA = new OrderItem().id(11L).qty(2).price(new BigDecimal("100.00"));
        lineA.setDiscountAllocation(new BigDecimal("20.00"));
        OrderItem lineB = new OrderItem().id(12L).qty(1).price(new BigDecimal("100.00"));
        lineB.setDiscountAllocation(new BigDecimal("10.00"));

        BigDecimal refund = OrderMerchandiseAmounts.merchandiseRefundForQuantities(
            order,
            List.of(lineA, lineB),
            Map.of(11L, 1)
        );

        assertThat(refund).isEqualByComparingTo("90.00");
    }
}
