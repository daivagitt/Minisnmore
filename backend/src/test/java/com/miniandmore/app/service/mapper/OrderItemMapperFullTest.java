package com.miniandmore.app.service.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.OrderItem;
import com.miniandmore.app.domain.enumeration.OrderStatus;
import com.miniandmore.app.domain.enumeration.PaymentStatus;
import com.miniandmore.app.service.dto.OrderItemDTO;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class OrderItemMapperFullTest {

    @Test
    void orderItemMapper_roundTripsAllFieldsWithOrderReference() {
        OrderItemMapper mapper = new OrderItemMapperImpl();
        CustomerOrder order = new CustomerOrder()
            .id(42L)
            .orderNumber("MNM-001")
            .status(OrderStatus.CONFIRMED)
            .paymentStatus(PaymentStatus.PAID)
            .total(BigDecimal.TEN);
        OrderItem item = new OrderItem()
            .id(7L)
            .productId("99")
            .productSlug("dress")
            .productName("Summer Dress")
            .productImage("https://cdn/img.jpg")
            .itemSize("M")
            .color("Pink")
            .qty(2)
            .price(BigDecimal.valueOf(499))
            .order(order);

        OrderItemDTO dto = mapper.toDto(item);
        OrderItem entity = mapper.toEntity(dto);
        OrderItem copy = mapper.toEntity(dto);
        OrderItemDTO patch = new OrderItemDTO();
        patch.setQty(3);
        mapper.partialUpdate(copy, patch);

        assertThat(dto.getId()).isEqualTo(7L);
        assertThat(dto.getProductName()).isEqualTo("Summer Dress");
        assertThat(dto.getOrder()).isNotNull();
        assertThat(dto.getOrder().getId()).isEqualTo(42L);
        assertThat(entity.getProductSlug()).isEqualTo("dress");
        assertThat(entity.getColor()).isEqualTo("Pink");
        assertThat(copy.getQty()).isEqualTo(3);
        assertThat(mapper.toDto(List.of(item))).hasSize(1);
        assertThat(mapper.toEntity(List.of(dto))).hasSize(1);
    }
}
