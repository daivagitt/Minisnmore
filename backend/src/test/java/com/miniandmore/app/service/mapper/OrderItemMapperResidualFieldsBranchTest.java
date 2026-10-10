package com.miniandmore.app.service.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.miniandmore.app.domain.OrderItem;
import com.miniandmore.app.service.dto.OrderItemDTO;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class OrderItemMapperResidualFieldsBranchTest {

    @Test
    void partialUpdate_mapsAllDirectLineFields() {
        OrderItem entity = new OrderItem();
        OrderItemDTO patch = new OrderItemDTO();
        patch.setId(7L);
        patch.setProductId("17");
        patch.setProductSlug("dress");
        patch.setProductName("Dress");
        patch.setProductImage("image.jpg");
        patch.setItemSize("M");
        patch.setColor("Blue");
        patch.setVariantSku("D-M-B");
        patch.setQty(2);
        patch.setPrice(new BigDecimal("199"));
        patch.setMrp(new BigDecimal("299"));
        patch.setHsn("6204");
        patch.setTaxPct(new BigDecimal("5"));
        patch.setDiscountAllocation(new BigDecimal("20"));

        new OrderItemMapperImpl().partialUpdate(entity, patch);

        assertThat(entity.getId()).isEqualTo(7L);
        assertThat(entity.getProductId()).isEqualTo("17");
        assertThat(entity.getProductSlug()).isEqualTo("dress");
        assertThat(entity.getProductName()).isEqualTo("Dress");
        assertThat(entity.getProductImage()).isEqualTo("image.jpg");
        assertThat(entity.getItemSize()).isEqualTo("M");
        assertThat(entity.getColor()).isEqualTo("Blue");
        assertThat(entity.getVariantSku()).isEqualTo("D-M-B");
        assertThat(entity.getQty()).isEqualTo(2);
        assertThat(entity.getPrice()).isEqualByComparingTo("199");
        assertThat(entity.getMrp()).isEqualByComparingTo("299");
        assertThat(entity.getHsn()).isEqualTo("6204");
        assertThat(entity.getTaxPct()).isEqualByComparingTo("5");
        assertThat(entity.getDiscountAllocation()).isEqualByComparingTo("20");
    }
}
