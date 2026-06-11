package com.miniandmore.app.service.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.miniandmore.app.domain.*;
import com.miniandmore.app.domain.enumeration.DiscountType;
import com.miniandmore.app.domain.enumeration.OrderStatus;
import com.miniandmore.app.domain.enumeration.PaymentStatus;
import com.miniandmore.app.domain.enumeration.ZohoSyncStatus;
import com.miniandmore.app.service.dto.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Exercises generated {@code *MapperImpl} classes for full field mapping branches.
 */
class MapperImplFullRoundTripTest {

    @Test
    void productMapper_nullCategoryAndFullFields() {
        ProductMapper mapper = new ProductMapperImpl();
        Product entity = fullProduct(null);
        assertThat(mapper.toDto(entity).getCategory()).isNull();
        Product roundTrip = mapper.toEntity(mapper.toDto(entity));
        assertThat(roundTrip.getSku()).isEqualTo("SKU-X");
        Product copy = mapper.toEntity(mapper.toDto(entity));
        ProductDTO patch = new ProductDTO();
        patch.setBadge("Sale");
        mapper.partialUpdate(copy, patch);
        assertThat(copy.getBadge()).isEqualTo("Sale");
        assertThat(mapper.toDto(List.of(entity))).hasSize(1);
    }

    @Test
    void customerOrderMapper_fullRoundTrip() {
        CustomerOrderMapper mapper = new CustomerOrderMapperImpl();
        CustomerOrder entity = new CustomerOrder()
            .id(1L)
            .userLogin("u")
            .orderNumber("MNM-1")
            .status(OrderStatus.CONFIRMED)
            .paymentStatus(PaymentStatus.PAID)
            .subtotal(BigDecimal.TEN)
            .discount(BigDecimal.ONE)
            .shipping(BigDecimal.ONE)
            .tax(BigDecimal.ONE)
            .donation(BigDecimal.ZERO)
            .total(BigDecimal.valueOf(13))
            .couponCode("C")
            .shippingAddressJson("{}")
            .notes("n")
            .trackingNumber("T")
            .trackingCarrier("DHL")
            .shipmentDate("2026-01-01")
            .zohoOrderStatus("shipped")
            .zohoSalesOrderId("SO")
            .zohoInvoiceId("INV")
            .zohoSyncStatus(ZohoSyncStatus.SYNCED)
            .zohoSyncError("err")
            .zohoLastSyncedAt(Instant.parse("2026-01-01T00:00:00Z"))
            .zohoTrackingLastPulledAt(Instant.parse("2026-01-02T00:00:00Z"))
            .inventoryReserved(true)
            .inventoryReservedUntil(Instant.parse("2026-01-03T00:00:00Z"))
            .inventoryReleasedAt(Instant.parse("2026-01-04T00:00:00Z"))
            .razorpayOrderId("ro")
            .razorpayPaymentId("rp")
            .razorpaySignature("rs");

        CustomerOrderDTO dto = mapper.toDto(entity);
        CustomerOrder roundTrip = mapper.toEntity(dto);
        CustomerOrder copy = mapper.toEntity(dto);
        CustomerOrderDTO patch = new CustomerOrderDTO();
        patch.setNotes("updated");
        mapper.partialUpdate(copy, patch);

        assertThat(dto.getOrderNumber()).isEqualTo("MNM-1");
        assertThat(roundTrip.getOrderNumber()).isEqualTo("MNM-1");
        assertThat(copy.getNotes()).isEqualTo("updated");
        assertThat(mapper.toEntity(List.of(dto))).hasSize(1);
    }

    @Test
    void categoryBannerPageVisibilityMappers_fullRoundTrip() {
        CategoryMapper categoryMapper = new CategoryMapperImpl();
        Category category = new Category()
            .id(1L)
            .slug("cat")
            .name("Category")
            .description("Desc")
            .imageUrl("img.jpg")
            .sortOrder(1)
            .active(true);
        CategoryDTO categoryDto = categoryMapper.toDto(category);
        assertThat(categoryMapper.toEntity(categoryDto).getSlug()).isEqualTo("cat");

        BannerMapper bannerMapper = new BannerMapperImpl();
        Banner banner = new Banner()
            .id(2L)
            .title("Banner")
            .subtitle("Sub")
            .imageUrl("b.jpg")
            .linkUrl("/shop")
            .ctaLabel("Shop")
            .sortOrder(1)
            .active(true);
        BannerDTO bannerDto = bannerMapper.toDto(banner);
        BannerDTO bannerPatch = new BannerDTO();
        bannerPatch.setSubtitle("New");
        Banner bannerCopy = bannerMapper.toEntity(bannerDto);
        bannerMapper.partialUpdate(bannerCopy, bannerPatch);
        assertThat(bannerCopy.getSubtitle()).isEqualTo("New");

        PageVisibilityMapper pageMapper = new PageVisibilityMapperImpl();
        PageVisibility page = new PageVisibility().id(3L).pageKey("shop").visible(true);
        PageVisibilityDTO pageDto = pageMapper.toDto(page);
        assertThat(pageMapper.toEntity(pageDto).getPageKey()).isEqualTo("shop");
    }

    @Test
    void couponBlogPolicyProfileMappers_fullRoundTrip() {
        CouponMapper couponMapper = new CouponMapperImpl();
        Coupon coupon = new Coupon()
            .id(1L)
            .code("X")
            .discountType(DiscountType.FLAT)
            .discountValue(BigDecimal.TEN)
            .minOrder(BigDecimal.ZERO)
            .maxDiscount(BigDecimal.valueOf(50))
            .expiresAt(Instant.parse("2026-12-31T00:00:00Z"))
            .active(true);
        CouponDTO couponDto = couponMapper.toDto(coupon);
        Coupon couponCopy = couponMapper.toEntity(couponDto);
        CouponDTO couponPatch = new CouponDTO();
        couponPatch.setCode("Y");
        couponMapper.partialUpdate(couponCopy, couponPatch);
        assertThat(couponCopy.getCode()).isEqualTo("Y");

        BlogPostMapper blogMapper = new BlogPostMapperImpl();
        BlogPost blog = new BlogPost()
            .id(2L)
            .slug("s")
            .title("T")
            .excerpt("E")
            .content("C")
            .coverUrl("u")
            .published(true)
            .publishedAt(Instant.now());
        assertThat(blogMapper.toEntity(blogMapper.toDto(blog)).getContent()).isEqualTo("C");

        PolicyMapper policyMapper = new PolicyMapperImpl();
        Policy policy = new Policy().id(3L).slug("privacy").title("Privacy").body("Body");
        PolicyDTO policyDto = policyMapper.toDto(policy);
        Policy policyCopy = policyMapper.toEntity(policyDto);
        PolicyDTO policyPatch = new PolicyDTO();
        policyPatch.setTitle("Updated");
        policyMapper.partialUpdate(policyCopy, policyPatch);
        assertThat(policyCopy.getTitle()).isEqualTo("Updated");

        CustomerProfileMapper profileMapper = new CustomerProfileMapperImpl();
        CustomerProfile profile = new CustomerProfile().id(4L).userLogin("u").fullName("Jane").phone("99");
        assertThat(profileMapper.toEntity(profileMapper.toDto(profile)).getFullName()).isEqualTo("Jane");
    }

    @Test
    void homeSectionMapper_fullRoundTrip() {
        HomeSectionMapper mapper = new HomeSectionMapperImpl();
        HomeSection entity = new HomeSection()
            .id(1L)
            .sectionKey("hero")
            .title("Hero")
            .subtitle("Sub")
            .enabled(true)
            .sortOrder(1)
            .payloadJson("{\"k\":\"v\"}");
        HomeSectionDTO dto = mapper.toDto(entity);
        HomeSection copy = mapper.toEntity(dto);
        HomeSectionDTO patch = new HomeSectionDTO();
        patch.setTitle("New Hero");
        mapper.partialUpdate(copy, patch);
        assertThat(copy.getTitle()).isEqualTo("New Hero");
        assertThat(mapper.toDto(List.of(entity))).hasSize(1);
    }

    private static Product fullProduct(Category category) {
        return new Product()
            .id(99L)
            .slug("p")
            .name("Product")
            .sku("SKU-X")
            .fabricType("Cotton")
            .description("D")
            .story("S")
            .price(BigDecimal.TEN)
            .compareAtPrice(BigDecimal.valueOf(20))
            .currency("INR")
            .ageMinMonths(0)
            .ageMaxMonths(24)
            .sizesJson("[]")
            .colorsJson("[]")
            .imagesJson("[]")
            .badge("B")
            .stock(5)
            .sortOrder(1)
            .featured(true)
            .trending(true)
            .newArrival(true)
            .bestSeller(true)
            .active(true)
            .zohoItemId("Z")
            .zohoSyncStatus(ZohoSyncStatus.PENDING)
            .zohoLastSyncedAt(Instant.now())
            .zohoSyncError("e")
            .category(category);
    }
}
