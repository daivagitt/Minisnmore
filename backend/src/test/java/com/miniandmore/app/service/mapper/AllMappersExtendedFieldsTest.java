package com.miniandmore.app.service.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.miniandmore.app.domain.BlogPost;
import com.miniandmore.app.domain.Coupon;
import com.miniandmore.app.domain.CustomerProfile;
import com.miniandmore.app.domain.HomeSection;
import com.miniandmore.app.domain.OrderItem;
import com.miniandmore.app.domain.Policy;
import com.miniandmore.app.domain.enumeration.DiscountType;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AllMappersExtendedFieldsTest {

    @Test
    void blogPostMapper_mapsContentFields() {
        BlogPostMapper mapper = new BlogPostMapperImpl();
        BlogPost entity = new BlogPost()
            .id(1L)
            .slug("post")
            .title("Title")
            .excerpt("Excerpt")
            .content("Body")
            .coverUrl("cover.jpg")
            .published(true)
            .publishedAt(Instant.parse("2026-01-01T00:00:00Z"));

        var dto = mapper.toDto(entity);
        var roundTrip = mapper.toEntity(dto);

        assertThat(roundTrip.getExcerpt()).isEqualTo("Excerpt");
        assertThat(roundTrip.getContent()).isEqualTo("Body");
        assertThat(roundTrip.getPublished()).isTrue();
    }

    @Test
    void couponMapper_mapsDiscountFields() {
        CouponMapper mapper = new CouponMapperImpl();
        Coupon entity = new Coupon()
            .id(1L)
            .code("SAVE10")
            .discountType(DiscountType.PERCENT)
            .discountValue(BigDecimal.TEN)
            .minOrder(BigDecimal.valueOf(500))
            .maxDiscount(BigDecimal.valueOf(100))
            .expiresAt(Instant.parse("2026-12-31T00:00:00Z"))
            .active(true);

        var dto = mapper.toDto(entity);
        var roundTrip = mapper.toEntity(dto);

        assertThat(roundTrip.getDiscountType()).isEqualTo(DiscountType.PERCENT);
        assertThat(roundTrip.getMinOrder()).isEqualByComparingTo("500");
        assertThat(roundTrip.getActive()).isTrue();
    }

    @Test
    void homeSectionMapper_mapsPayloadJson() {
        HomeSectionMapper mapper = new HomeSectionMapperImpl();
        HomeSection entity = new HomeSection()
            .id(1L)
            .sectionKey("hero")
            .title("Welcome")
            .subtitle("Subtitle")
            .enabled(true)
            .sortOrder(1)
            .payloadJson("{\"cta\":\"shop\"}");

        var dto = mapper.toDto(entity);
        var roundTrip = mapper.toEntity(dto);

        assertThat(roundTrip.getPayloadJson()).contains("cta");
        assertThat(roundTrip.getEnabled()).isTrue();
    }

    @Test
    void orderItemMapper_mapsPriceField() {
        OrderItemMapper mapper = new OrderItemMapperImpl();
        OrderItem entity = new OrderItem()
            .id(1L)
            .productId("1")
            .productSlug("dress")
            .productName("Dress")
            .productImage("img.jpg")
            .itemSize("M")
            .color("Pink")
            .qty(2)
            .price(BigDecimal.valueOf(999));

        var dto = mapper.toDto(entity);
        var roundTrip = mapper.toEntity(dto);

        assertThat(roundTrip.getPrice()).isEqualByComparingTo("999");
        assertThat(roundTrip.getQty()).isEqualTo(2);
    }

    @Test
    void policyMapper_mapsBodyField() {
        PolicyMapper mapper = new PolicyMapperImpl();
        Policy entity = new Policy().id(1L).slug("privacy").title("Privacy").body("Policy body");

        var dto = mapper.toDto(entity);
        assertThat(mapper.toEntity(dto).getBody()).isEqualTo("Policy body");
    }

    @Test
    void customerProfileMapper_mapsPhoneField() {
        CustomerProfileMapper mapper = new CustomerProfileMapperImpl();
        CustomerProfile entity = new CustomerProfile().id(1L).userLogin("user").fullName("Jane").phone("9999999999");

        var dto = mapper.toDto(entity);
        assertThat(mapper.toEntity(dto).getPhone()).isEqualTo("9999999999");
    }
}
