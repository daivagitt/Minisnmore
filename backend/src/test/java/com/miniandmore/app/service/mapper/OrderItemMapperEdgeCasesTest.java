package com.miniandmore.app.service.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.OrderItem;
import com.miniandmore.app.domain.enumeration.OrderStatus;
import com.miniandmore.app.domain.enumeration.PaymentStatus;
import com.miniandmore.app.service.dto.CustomerOrderDTO;
import com.miniandmore.app.service.dto.OrderItemDTO;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class OrderItemMapperEdgeCasesTest {

    @Test
    void nullAndListBranches() {
        OrderItemMapper mapper = new OrderItemMapperImpl();
        assertThat(mapper.toDto((OrderItem) null)).isNull();
        assertThat(mapper.toEntity((OrderItemDTO) null)).isNull();
        assertThat(mapper.toDto((List<OrderItem>) null)).isNull();
        assertThat(mapper.toEntity((List<OrderItemDTO>) null)).isNull();
        assertThat(mapper.toDtoCustomerOrderId(null)).isNull();
    }

    @Test
    void toEntity_withFullOrderDto_mapsNestedOrderFields() {
        OrderItemMapper mapper = new OrderItemMapperImpl();
        CustomerOrderDTO orderDto = new CustomerOrderDTO();
        orderDto.setId(9L);
        orderDto.setOrderNumber("MNM-1");
        orderDto.setStatus(OrderStatus.CONFIRMED);
        orderDto.setPaymentStatus(PaymentStatus.PAID);
        orderDto.setRazorpayOrderId("ro");
        orderDto.setRazorpayPaymentId("rp");
        orderDto.setRazorpaySignature("rs");
        orderDto.setSubtotal(BigDecimal.TEN);
        orderDto.setDiscount(BigDecimal.ONE);
        orderDto.setShipping(BigDecimal.ONE);
        orderDto.setTax(BigDecimal.ONE);
        orderDto.setDonation(BigDecimal.ZERO);
        orderDto.setTotal(BigDecimal.valueOf(13));
        orderDto.setCouponCode("SAVE");
        orderDto.setShippingAddressJson("{}");
        orderDto.setNotes("note");
        orderDto.setUserLogin("user1");

        OrderItemDTO dto = new OrderItemDTO();
        dto.setProductId("1");
        dto.setProductName("Dress");
        dto.setQty(1);
        dto.setPrice(BigDecimal.TEN);
        dto.setOrder(orderDto);

        OrderItem entity = mapper.toEntity(dto);

        assertThat(entity.getOrder()).isNotNull();
        assertThat(entity.getOrder().getOrderNumber()).isEqualTo("MNM-1");
        assertThat(entity.getOrder().getUserLogin()).isEqualTo("user1");
    }

    @Test
    void partialUpdate_orderWhenEntityHasNoOrder_createsOrder() {
        OrderItemMapper mapper = new OrderItemMapperImpl();
        OrderItem entity = new OrderItem().productId("1").productName("Dress").qty(1).price(BigDecimal.ONE);
        CustomerOrderDTO orderDto = new CustomerOrderDTO();
        orderDto.setId(5L);
        orderDto.setOrderNumber("MNM-5");
        OrderItemDTO patch = new OrderItemDTO();
        patch.setOrder(orderDto);

        mapper.partialUpdate(entity, patch);

        assertThat(entity.getOrder()).isNotNull();
        assertThat(entity.getOrder().getId()).isEqualTo(5L);
    }

    @Test
    void partialUpdate_orderWhenEntityHasOrder_updatesFields() {
        OrderItemMapper mapper = new OrderItemMapperImpl();
        CustomerOrder existing = new CustomerOrder().id(1L).orderNumber("OLD");
        OrderItem entity = new OrderItem().productId("1").productName("Dress").qty(1).price(BigDecimal.ONE).order(existing);
        CustomerOrderDTO orderDto = new CustomerOrderDTO();
        orderDto.setOrderNumber("NEW");
        orderDto.setNotes("updated");
        OrderItemDTO patch = new OrderItemDTO();
        patch.setOrder(orderDto);
        patch.setProductSlug("slug");
        patch.setProductImage("img");
        patch.setItemSize("M");
        patch.setColor("Blue");

        mapper.partialUpdate(entity, patch);

        assertThat(entity.getOrder().getOrderNumber()).isEqualTo("NEW");
        assertThat(entity.getProductSlug()).isEqualTo("slug");
        assertThat(entity.getColor()).isEqualTo("Blue");
    }

    @Test
    void partialUpdate_orderWithAllCustomerOrderFields_updatesTarget() {
        OrderItemMapper mapper = new OrderItemMapperImpl();
        CustomerOrder existing = new CustomerOrder().id(1L).orderNumber("OLD");
        OrderItem entity = new OrderItem().productId("1").productName("Dress").qty(1).price(BigDecimal.ONE).order(existing);
        CustomerOrderDTO orderDto = new CustomerOrderDTO();
        orderDto.setOrderNumber("NEW");
        orderDto.setStatus(OrderStatus.CONFIRMED);
        orderDto.setPaymentStatus(PaymentStatus.PAID);
        orderDto.setRazorpayOrderId("ro");
        orderDto.setRazorpayPaymentId("rp");
        orderDto.setRazorpaySignature("rs");
        orderDto.setSubtotal(BigDecimal.ONE);
        orderDto.setDiscount(BigDecimal.ZERO);
        orderDto.setShipping(BigDecimal.ZERO);
        orderDto.setTax(BigDecimal.ZERO);
        orderDto.setDonation(BigDecimal.ZERO);
        orderDto.setTotal(BigDecimal.ONE);
        orderDto.setCouponCode("C");
        orderDto.setShippingAddressJson("{}");
        orderDto.setNotes("n");
        orderDto.setUserLogin("u");
        OrderItemDTO patch = new OrderItemDTO();
        patch.setOrder(orderDto);

        mapper.partialUpdate(entity, patch);

        assertThat(entity.getOrder().getRazorpayPaymentId()).isEqualTo("rp");
        assertThat(entity.getOrder().getUserLogin()).isEqualTo("u");
    }

    @Test
    void partialUpdate_nullDto_isNoOp() {
        OrderItemMapper mapper = new OrderItemMapperImpl();
        OrderItem entity = new OrderItem().productName("Dress");
        mapper.partialUpdate(entity, null);
        assertThat(entity.getProductName()).isEqualTo("Dress");
    }
}
