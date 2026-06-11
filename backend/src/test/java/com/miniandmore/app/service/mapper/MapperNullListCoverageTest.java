package com.miniandmore.app.service.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.miniandmore.app.service.dto.PolicyDTO;
import org.junit.jupiter.api.Test;

class MapperNullListCoverageTest {

    @Test
    void policyMapper_nullListsAndListMapping() {
        PolicyMapper mapper = new PolicyMapperImpl();
        assertThat(mapper.toDto((java.util.List<com.miniandmore.app.domain.Policy>) null)).isNull();
        assertThat(mapper.toEntity((java.util.List<PolicyDTO>) null)).isNull();
        PolicyDTO dto = new PolicyDTO();
        dto.setSlug("privacy");
        dto.setTitle("Privacy");
        dto.setBody("Body");
        assertThat(mapper.toEntity(java.util.List.of(dto))).hasSize(1);
    }

    @Test
    void customerProfileMapper_nullLists() {
        CustomerProfileMapper mapper = new CustomerProfileMapperImpl();
        assertThat(mapper.toDto((java.util.List<com.miniandmore.app.domain.CustomerProfile>) null)).isNull();
        assertThat(mapper.toEntity((java.util.List<com.miniandmore.app.service.dto.CustomerProfileDTO>) null)).isNull();
        var entity = new com.miniandmore.app.domain.CustomerProfile().userLogin("u").fullName("Jane");
        assertThat(mapper.toDto(java.util.List.of(entity))).hasSize(1);
    }

    @Test
    void couponMapper_nullLists() {
        CouponMapper mapper = new CouponMapperImpl();
        assertThat(mapper.toDto((java.util.List<com.miniandmore.app.domain.Coupon>) null)).isNull();
        assertThat(mapper.toEntity((java.util.List<com.miniandmore.app.service.dto.CouponDTO>) null)).isNull();
    }

    @Test
    void blogPostMapper_nullLists() {
        BlogPostMapper mapper = new BlogPostMapperImpl();
        assertThat(mapper.toDto((java.util.List<com.miniandmore.app.domain.BlogPost>) null)).isNull();
        assertThat(mapper.toEntity((java.util.List<com.miniandmore.app.service.dto.BlogPostDTO>) null)).isNull();
    }
}
