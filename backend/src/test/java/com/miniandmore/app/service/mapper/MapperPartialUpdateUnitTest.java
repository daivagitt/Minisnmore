package com.miniandmore.app.service.mapper;

import static com.miniandmore.app.domain.BannerTestSamples.getBannerSample1;
import static com.miniandmore.app.domain.BlogPostTestSamples.getBlogPostSample1;
import static com.miniandmore.app.domain.CategoryTestSamples.getCategorySample1;
import static com.miniandmore.app.domain.CouponTestSamples.getCouponSample1;
import static com.miniandmore.app.domain.CustomerOrderTestSamples.getCustomerOrderSample1;
import static com.miniandmore.app.domain.CustomerProfileTestSamples.getCustomerProfileSample1;
import static com.miniandmore.app.domain.HomeSectionTestSamples.getHomeSectionSample1;
import static com.miniandmore.app.domain.OrderItemTestSamples.getOrderItemSample1;
import static com.miniandmore.app.domain.PageVisibilityTestSamples.getPageVisibilitySample1;
import static com.miniandmore.app.domain.PolicyTestSamples.getPolicySample1;
import static com.miniandmore.app.domain.ProductTestSamples.getProductSample1;
import static com.miniandmore.app.domain.SiteSettingTestSamples.getSiteSettingSample1;
import static org.assertj.core.api.Assertions.assertThat;

import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.OrderItem;
import com.miniandmore.app.domain.Product;
import com.miniandmore.app.service.dto.BannerDTO;
import com.miniandmore.app.service.dto.BlogPostDTO;
import com.miniandmore.app.service.dto.CategoryDTO;
import com.miniandmore.app.service.dto.CouponDTO;
import com.miniandmore.app.service.dto.CustomerOrderDTO;
import com.miniandmore.app.service.dto.CustomerProfileDTO;
import com.miniandmore.app.service.dto.HomeSectionDTO;
import com.miniandmore.app.service.dto.OrderItemDTO;
import com.miniandmore.app.service.dto.PageVisibilityDTO;
import com.miniandmore.app.service.dto.PolicyDTO;
import com.miniandmore.app.service.dto.ProductDTO;
import com.miniandmore.app.service.dto.SiteSettingDTO;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class MapperPartialUpdateUnitTest {

    @Test
    void bannerMapper_partialUpdateAndListMapping() {
        BannerMapper mapper = new BannerMapperImpl();
        var entity = getBannerSample1();
        var dto = mapper.toDto(entity);
        var copy = mapper.toEntity(dto);
        BannerDTO patch = new BannerDTO();
        patch.setTitle("Updated");
        mapper.partialUpdate(copy, patch);
        assertThat(copy.getTitle()).isEqualTo("Updated");
        assertThat(mapper.toDto(List.of(entity))).hasSize(1);
        assertThat(mapper.toEntity(List.of(dto))).hasSize(1);
    }

    @Test
    void blogPostMapper_partialUpdateAndListMapping() {
        BlogPostMapper mapper = new BlogPostMapperImpl();
        var entity = getBlogPostSample1();
        var dto = mapper.toDto(entity);
        var copy = mapper.toEntity(dto);
        BlogPostDTO patch = new BlogPostDTO();
        patch.setTitle("Updated");
        mapper.partialUpdate(copy, patch);
        assertThat(copy.getTitle()).isEqualTo("Updated");
        assertThat(mapper.toDto(List.of(entity))).hasSize(1);
    }

    @Test
    void categoryMapper_partialUpdateAndListMapping() {
        CategoryMapper mapper = new CategoryMapperImpl();
        var entity = getCategorySample1();
        var dto = mapper.toDto(entity);
        var copy = mapper.toEntity(dto);
        CategoryDTO patch = new CategoryDTO();
        patch.setName("Updated");
        mapper.partialUpdate(copy, patch);
        assertThat(copy.getName()).isEqualTo("Updated");
        assertThat(mapper.toEntity(List.of(dto))).hasSize(1);
    }

    @Test
    void couponMapper_partialUpdateAndListMapping() {
        CouponMapper mapper = new CouponMapperImpl();
        var entity = getCouponSample1();
        var dto = mapper.toDto(entity);
        var copy = mapper.toEntity(dto);
        CouponDTO patch = new CouponDTO();
        patch.setCode("NEW");
        mapper.partialUpdate(copy, patch);
        assertThat(copy.getCode()).isEqualTo("NEW");
        assertThat(mapper.toDto(List.of(entity))).hasSize(1);
    }

    @Test
    void customerOrderMapper_partialUpdateAndListMapping() {
        CustomerOrderMapper mapper = new CustomerOrderMapperImpl();
        var entity = getCustomerOrderSample1();
        var dto = mapper.toDto(entity);
        var copy = mapper.toEntity(dto);
        CustomerOrderDTO patch = new CustomerOrderDTO();
        patch.setNotes("Updated");
        mapper.partialUpdate(copy, patch);
        assertThat(copy.getNotes()).isEqualTo("Updated");
        assertThat(mapper.toDto(List.of(entity))).hasSize(1);
    }

    @Test
    void customerProfileMapper_partialUpdateAndListMapping() {
        CustomerProfileMapper mapper = new CustomerProfileMapperImpl();
        var entity = getCustomerProfileSample1();
        var dto = mapper.toDto(entity);
        var copy = mapper.toEntity(dto);
        CustomerProfileDTO patch = new CustomerProfileDTO();
        patch.setFullName("Updated");
        mapper.partialUpdate(copy, patch);
        assertThat(copy.getFullName()).isEqualTo("Updated");
    }

    @Test
    void homeSectionMapper_partialUpdateAndListMapping() {
        HomeSectionMapper mapper = new HomeSectionMapperImpl();
        var entity = getHomeSectionSample1();
        var dto = mapper.toDto(entity);
        var copy = mapper.toEntity(dto);
        HomeSectionDTO patch = new HomeSectionDTO();
        patch.setTitle("Updated");
        mapper.partialUpdate(copy, patch);
        assertThat(copy.getTitle()).isEqualTo("Updated");
    }

    @Test
    void orderItemMapper_mapsOrderReference() {
        OrderItemMapper mapper = new OrderItemMapperImpl();
        CustomerOrder order = getCustomerOrderSample1();
        OrderItem entity = getOrderItemSample1().order(order);
        OrderItemDTO dto = mapper.toDto(entity);
        assertThat(dto.getOrder()).isNotNull();
        assertThat(dto.getOrder().getId()).isEqualTo(order.getId());
        OrderItemDTO patch = new OrderItemDTO();
        patch.setProductName("Updated");
        OrderItem copy = mapper.toEntity(dto);
        mapper.partialUpdate(copy, patch);
        assertThat(copy.getProductName()).isEqualTo("Updated");
        assertThat(mapper.toDto(List.of(entity))).hasSize(1);
    }

    @Test
    void pageVisibilityMapper_partialUpdateAndListMapping() {
        PageVisibilityMapper mapper = new PageVisibilityMapperImpl();
        var entity = getPageVisibilitySample1();
        var dto = mapper.toDto(entity);
        var copy = mapper.toEntity(dto);
        PageVisibilityDTO patch = new PageVisibilityDTO();
        patch.setPageKey("updated");
        mapper.partialUpdate(copy, patch);
        assertThat(copy.getPageKey()).isEqualTo("updated");
    }

    @Test
    void policyMapper_partialUpdateAndListMapping() {
        PolicyMapper mapper = new PolicyMapperImpl();
        var entity = getPolicySample1();
        var dto = mapper.toDto(entity);
        var copy = mapper.toEntity(dto);
        PolicyDTO patch = new PolicyDTO();
        patch.setTitle("Updated");
        mapper.partialUpdate(copy, patch);
        assertThat(copy.getTitle()).isEqualTo("Updated");
    }

    @Test
    void productMapper_partialUpdateAndListMapping() {
        ProductMapper mapper = new ProductMapperImpl();
        Product entity = getProductSample1().price(BigDecimal.TEN);
        var dto = mapper.toDto(entity);
        var copy = mapper.toEntity(dto);
        ProductDTO patch = new ProductDTO();
        patch.setName("Updated");
        mapper.partialUpdate(copy, patch);
        assertThat(copy.getName()).isEqualTo("Updated");
        assertThat(mapper.toDto(List.of(entity))).hasSize(1);
    }

    @Test
    void siteSettingMapper_partialUpdateWithCommerceFields() {
        SiteSettingMapper mapper = new SiteSettingMapperImpl();
        var entity = getSiteSettingSample1();
        entity.setGstRate(new BigDecimal("18"));
        entity.setPricesIncludeGst(true);
        entity.setShippingStandardInr(99);
        entity.setShippingExpressInr(150);
        entity.setFreeShippingMinInr(2500);
        entity.setSubscribeHeroTitle("Hero");
        entity.setSubscribeHeroSubtitle("Subtitle");
        entity.setSubscribeTestimonial("Great");
        entity.setSubscribeBadgesJson("[]");
        entity.setSubscribePlansJson("[]");
        entity.setSubscribeFeaturesJson("[]");
        var dto = mapper.toDto(entity);
        assertThat(dto.getSubscribeHeroTitle()).isEqualTo("Hero");
        var copy = mapper.toEntity(dto);
        SiteSettingDTO patch = new SiteSettingDTO();
        patch.setBrandName("Updated Brand");
        mapper.partialUpdate(copy, patch);
        assertThat(copy.getBrandName()).isEqualTo("Updated Brand");
        assertThat(mapper.toDto(List.of(entity))).hasSize(1);
        assertThat(mapper.toEntity(List.of(dto))).hasSize(1);
    }
}
