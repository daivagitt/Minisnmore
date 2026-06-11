package com.miniandmore.app.service.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.Policy;
import com.miniandmore.app.domain.CustomerProfile;
import com.miniandmore.app.domain.enumeration.OrderStatus;
import com.miniandmore.app.domain.enumeration.PaymentStatus;
import com.miniandmore.app.service.dto.CustomerOrderDTO;
import com.miniandmore.app.service.dto.PolicyDTO;
import com.miniandmore.app.service.dto.CustomerProfileDTO;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MapperPartialUpdateRemainingTest {

    @Test
    void customerOrderMapper_partialUpdateAllSupportedFields() {
        CustomerOrderMapper mapper = new CustomerOrderMapperImpl();
        CustomerOrder entity = new CustomerOrder().orderNumber("MNM-1").total(BigDecimal.TEN);
        CustomerOrderDTO patch = new CustomerOrderDTO();
        patch.setId(100L);
        patch.setUserLogin("u");
        patch.setOrderNumber("MNM-2");
        patch.setStatus(OrderStatus.CONFIRMED);
        patch.setPaymentStatus(PaymentStatus.PAID);
        patch.setRazorpayOrderId("ro");
        patch.setRazorpayPaymentId("rp");
        patch.setRazorpaySignature("rs");
        patch.setSubtotal(BigDecimal.ONE);
        patch.setDiscount(BigDecimal.ONE);
        patch.setShipping(BigDecimal.ONE);
        patch.setTax(BigDecimal.ONE);
        patch.setDonation(BigDecimal.ZERO);
        patch.setTotal(BigDecimal.valueOf(5));
        patch.setCouponCode("C");
        patch.setShippingAddressJson("{}");
        patch.setNotes("n");

        mapper.partialUpdate(entity, patch);

        assertThat(entity.getOrderNumber()).isEqualTo("MNM-2");
        assertThat(entity.getNotes()).isEqualTo("n");
    }

    @Test
    void policyMapper_partialUpdateFields() {
        PolicyMapper mapper = new PolicyMapperImpl();
        Policy entity = new Policy().slug("privacy").title("Privacy");
        PolicyDTO patch = new PolicyDTO();
        patch.setId(2L);
        patch.setSlug("terms");
        patch.setTitle("Updated");
        patch.setBody("Body");
        mapper.partialUpdate(entity, patch);
        assertThat(entity.getSlug()).isEqualTo("terms");
        assertThat(entity.getBody()).isEqualTo("Body");
    }

    @Test
    void customerProfileMapper_partialUpdateFields() {
        CustomerProfileMapper mapper = new CustomerProfileMapperImpl();
        CustomerProfile entity = new CustomerProfile().userLogin("u");
        CustomerProfileDTO patch = new CustomerProfileDTO();
        patch.setId(9L);
        patch.setUserLogin("u2");
        patch.setFullName("Jane");
        patch.setPhone("999");
        mapper.partialUpdate(entity, patch);
        assertThat(entity.getUserLogin()).isEqualTo("u2");
        assertThat(entity.getPhone()).isEqualTo("999");
    }
}
