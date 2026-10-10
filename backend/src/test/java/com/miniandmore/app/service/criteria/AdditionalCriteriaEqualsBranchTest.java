package com.miniandmore.app.service.criteria;

import static org.assertj.core.api.Assertions.assertThat;

import com.miniandmore.app.domain.enumeration.DiscountType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Additional JHipster criteria equals() and lazy-filter branch coverage. */
class AdditionalCriteriaEqualsBranchTest {

    @ParameterizedTest
    @MethodSource("orderItemUnequalPairs")
    void orderItemCriteria_notEqualWhenSingleFilterDiffers(OrderItemCriteria left, OrderItemCriteria right) {
        assertThat(left).isNotEqualTo(right);
    }

    @ParameterizedTest
    @MethodSource("couponUnequalPairs")
    void couponCriteria_notEqualWhenSingleFilterDiffers(CouponCriteria left, CouponCriteria right) {
        assertThat(left).isNotEqualTo(right);
    }

    @ParameterizedTest
    @MethodSource("bannerUnequalPairs")
    void bannerCriteria_notEqualWhenSingleFilterDiffers(BannerCriteria left, BannerCriteria right) {
        assertThat(left).isNotEqualTo(right);
    }

    @ParameterizedTest
    @MethodSource("homeSectionUnequalPairs")
    void homeSectionCriteria_notEqualWhenSingleFilterDiffers(HomeSectionCriteria left, HomeSectionCriteria right) {
        assertThat(left).isNotEqualTo(right);
    }

    @ParameterizedTest
    @MethodSource("categoryUnequalPairs")
    void categoryCriteria_notEqualWhenSingleFilterDiffers(CategoryCriteria left, CategoryCriteria right) {
        assertThat(left).isNotEqualTo(right);
    }

    @Test
    void orderItemCriteria_lazyFilterInitializers_createFiltersOnDemand() {
        OrderItemCriteria criteria = new OrderItemCriteria();
        criteria.id();
        criteria.productId();
        criteria.productSlug();
        criteria.productName();
        criteria.productImage();
        criteria.itemSize();
        criteria.color();
        criteria.qty();
        criteria.price();
        criteria.orderId();
        criteria.distinct();
        assertThat(criteria.getId()).isNotNull();
        assertThat(criteria.getProductId()).isNotNull();
        assertThat(criteria.getDistinct()).isTrue();
    }

    @Test
    void couponCriteria_lazyFilterInitializers_createFiltersOnDemand() {
        CouponCriteria criteria = new CouponCriteria();
        criteria.id();
        criteria.code();
        criteria.discountType();
        criteria.discountValue();
        criteria.minOrder();
        criteria.maxDiscount();
        criteria.expiresAt();
        criteria.active();
        criteria.distinct();
        assertThat(criteria.getCode()).isNotNull();
    }

    private static Stream<Arguments> orderItemUnequalPairs() {
        return Stream.of(
            orderItemPair(() -> withOrderItemId(1L), () -> withOrderItemId(2L)),
            orderItemPair(() -> withProductId("1"), () -> withProductId("2")),
            orderItemPair(() -> withProductSlug("a"), () -> withProductSlug("b")),
            orderItemPair(() -> withProductName("n1"), () -> withProductName("n2")),
            orderItemPair(() -> withProductImage("i1"), () -> withProductImage("i2")),
            orderItemPair(() -> withItemSize("S"), () -> withItemSize("M")),
            orderItemPair(() -> withItemColor("Red"), () -> withItemColor("Blue")),
            orderItemPair(() -> withQty(1), () -> withQty(2)),
            orderItemPair(() -> withItemPrice("10"), () -> withItemPrice("11")),
            orderItemPair(() -> withOrderId(1L), () -> withOrderId(2L)),
            orderItemPair(() -> withOrderItemDistinct(true), () -> withOrderItemDistinct(false))
        );
    }

    private static Stream<Arguments> couponUnequalPairs() {
        return Stream.of(
            couponPair(() -> withCouponId(1L), () -> withCouponId(2L)),
            couponPair(() -> withCouponCode("A"), () -> withCouponCode("B")),
            couponPair(() -> withDiscountType(DiscountType.PERCENT), () -> withDiscountType(DiscountType.FLAT)),
            couponPair(() -> withDiscountValue("10"), () -> withDiscountValue("20")),
            couponPair(() -> withMinOrder("100"), () -> withMinOrder("200")),
            couponPair(() -> withMaxDiscount("50"), () -> withMaxDiscount("60")),
            couponPair(
                () -> withExpiresAt(Instant.parse("2026-01-01T00:00:00Z")),
                () -> withExpiresAt(Instant.parse("2027-01-01T00:00:00Z"))
            ),
            couponPair(() -> withCouponActive(true), () -> withCouponActive(false)),
            couponPair(() -> withCouponDistinct(true), () -> withCouponDistinct(false))
        );
    }

    private static Stream<Arguments> bannerUnequalPairs() {
        return Stream.of(
            bannerPair(() -> withBannerId(1L), () -> withBannerId(2L)),
            bannerPair(() -> withBannerTitle("t1"), () -> withBannerTitle("t2")),
            bannerPair(() -> withBannerSubtitle("s1"), () -> withBannerSubtitle("s2")),
            bannerPair(() -> withBannerImage("i1"), () -> withBannerImage("i2")),
            bannerPair(() -> withBannerLink("l1"), () -> withBannerLink("l2")),
            bannerPair(() -> withBannerCta("c1"), () -> withBannerCta("c2")),
            bannerPair(() -> withBannerSort(1), () -> withBannerSort(2)),
            bannerPair(() -> withBannerActive(true), () -> withBannerActive(false)),
            bannerPair(() -> withBannerDistinct(true), () -> withBannerDistinct(false))
        );
    }

    private static Stream<Arguments> homeSectionUnequalPairs() {
        return Stream.of(
            homePair(() -> withHomeId(1L), () -> withHomeId(2L)),
            homePair(() -> withSectionKey("a"), () -> withSectionKey("b")),
            homePair(() -> withHomeTitle("t1"), () -> withHomeTitle("t2")),
            homePair(() -> withHomeSubtitle("s1"), () -> withHomeSubtitle("s2")),
            homePair(() -> withHomeEnabled(true), () -> withHomeEnabled(false)),
            homePair(() -> withHomeSort(1), () -> withHomeSort(2)),
            homePair(() -> withHomeDistinct(true), () -> withHomeDistinct(false))
        );
    }

    private static Stream<Arguments> categoryUnequalPairs() {
        return Stream.of(
            categoryPair(() -> withCategoryId(1L), () -> withCategoryId(2L)),
            categoryPair(() -> withCategorySlug("a"), () -> withCategorySlug("b")),
            categoryPair(() -> withCategoryName("n1"), () -> withCategoryName("n2")),
            categoryPair(() -> withCategoryImage("i1"), () -> withCategoryImage("i2")),
            categoryPair(() -> withCategorySort(1), () -> withCategorySort(2)),
            categoryPair(() -> withCategoryActive(true), () -> withCategoryActive(false)),
            categoryPair(() -> withCategoryDistinct(true), () -> withCategoryDistinct(false))
        );
    }

    private static Arguments orderItemPair(Supplier<OrderItemCriteria> left, Supplier<OrderItemCriteria> right) {
        return Arguments.of(left.get(), right.get());
    }

    private static Arguments couponPair(Supplier<CouponCriteria> left, Supplier<CouponCriteria> right) {
        return Arguments.of(left.get(), right.get());
    }

    private static Arguments bannerPair(Supplier<BannerCriteria> left, Supplier<BannerCriteria> right) {
        return Arguments.of(left.get(), right.get());
    }

    private static Arguments homePair(Supplier<HomeSectionCriteria> left, Supplier<HomeSectionCriteria> right) {
        return Arguments.of(left.get(), right.get());
    }

    private static Arguments categoryPair(Supplier<CategoryCriteria> left, Supplier<CategoryCriteria> right) {
        return Arguments.of(left.get(), right.get());
    }

    private static OrderItemCriteria withOrderItemId(Long id) {
        var c = new OrderItemCriteria();
        c.id().setEquals(id);
        return c;
    }

    private static OrderItemCriteria withProductId(String id) {
        var c = new OrderItemCriteria();
        c.productId().setEquals(id);
        return c;
    }

    private static OrderItemCriteria withProductSlug(String slug) {
        var c = new OrderItemCriteria();
        c.productSlug().setEquals(slug);
        return c;
    }

    private static OrderItemCriteria withProductName(String name) {
        var c = new OrderItemCriteria();
        c.productName().setEquals(name);
        return c;
    }

    private static OrderItemCriteria withProductImage(String image) {
        var c = new OrderItemCriteria();
        c.productImage().setEquals(image);
        return c;
    }

    private static OrderItemCriteria withItemSize(String size) {
        var c = new OrderItemCriteria();
        c.itemSize().setEquals(size);
        return c;
    }

    private static OrderItemCriteria withItemColor(String color) {
        var c = new OrderItemCriteria();
        c.color().setEquals(color);
        return c;
    }

    private static OrderItemCriteria withQty(int qty) {
        var c = new OrderItemCriteria();
        c.qty().setEquals(qty);
        return c;
    }

    private static OrderItemCriteria withItemPrice(String price) {
        var c = new OrderItemCriteria();
        c.price().setEquals(new BigDecimal(price));
        return c;
    }

    private static OrderItemCriteria withOrderId(Long id) {
        var c = new OrderItemCriteria();
        c.orderId().setEquals(id);
        return c;
    }

    private static OrderItemCriteria withOrderItemDistinct(boolean distinct) {
        var c = new OrderItemCriteria();
        c.setDistinct(distinct);
        return c;
    }

    private static CouponCriteria withCouponId(Long id) {
        var c = new CouponCriteria();
        c.id().setEquals(id);
        return c;
    }

    private static CouponCriteria withCouponCode(String code) {
        var c = new CouponCriteria();
        c.code().setEquals(code);
        return c;
    }

    private static CouponCriteria withDiscountType(DiscountType type) {
        var c = new CouponCriteria();
        c.discountType().setEquals(type);
        return c;
    }

    private static CouponCriteria withDiscountValue(String value) {
        var c = new CouponCriteria();
        c.discountValue().setEquals(new BigDecimal(value));
        return c;
    }

    private static CouponCriteria withMinOrder(String value) {
        var c = new CouponCriteria();
        c.minOrder().setEquals(new BigDecimal(value));
        return c;
    }

    private static CouponCriteria withMaxDiscount(String value) {
        var c = new CouponCriteria();
        c.maxDiscount().setEquals(new BigDecimal(value));
        return c;
    }

    private static CouponCriteria withExpiresAt(Instant instant) {
        var c = new CouponCriteria();
        c.expiresAt().setEquals(instant);
        return c;
    }

    private static CouponCriteria withCouponActive(boolean active) {
        var c = new CouponCriteria();
        c.active().setEquals(active);
        return c;
    }

    private static CouponCriteria withCouponDistinct(boolean distinct) {
        var c = new CouponCriteria();
        c.setDistinct(distinct);
        return c;
    }

    private static BannerCriteria withBannerId(Long id) {
        var c = new BannerCriteria();
        c.id().setEquals(id);
        return c;
    }

    private static BannerCriteria withBannerTitle(String title) {
        var c = new BannerCriteria();
        c.title().setEquals(title);
        return c;
    }

    private static BannerCriteria withBannerSubtitle(String subtitle) {
        var c = new BannerCriteria();
        c.subtitle().setEquals(subtitle);
        return c;
    }

    private static BannerCriteria withBannerImage(String url) {
        var c = new BannerCriteria();
        c.imageUrl().setEquals(url);
        return c;
    }

    private static BannerCriteria withBannerLink(String url) {
        var c = new BannerCriteria();
        c.linkUrl().setEquals(url);
        return c;
    }

    private static BannerCriteria withBannerCta(String label) {
        var c = new BannerCriteria();
        c.ctaLabel().setEquals(label);
        return c;
    }

    private static BannerCriteria withBannerSort(int order) {
        var c = new BannerCriteria();
        c.sortOrder().setEquals(order);
        return c;
    }

    private static BannerCriteria withBannerActive(boolean active) {
        var c = new BannerCriteria();
        c.active().setEquals(active);
        return c;
    }

    private static BannerCriteria withBannerDistinct(boolean distinct) {
        var c = new BannerCriteria();
        c.setDistinct(distinct);
        return c;
    }

    private static HomeSectionCriteria withHomeId(Long id) {
        var c = new HomeSectionCriteria();
        c.id().setEquals(id);
        return c;
    }

    private static HomeSectionCriteria withSectionKey(String key) {
        var c = new HomeSectionCriteria();
        c.sectionKey().setEquals(key);
        return c;
    }

    private static HomeSectionCriteria withHomeTitle(String title) {
        var c = new HomeSectionCriteria();
        c.title().setEquals(title);
        return c;
    }

    private static HomeSectionCriteria withHomeSubtitle(String subtitle) {
        var c = new HomeSectionCriteria();
        c.subtitle().setEquals(subtitle);
        return c;
    }

    private static HomeSectionCriteria withHomeEnabled(boolean enabled) {
        var c = new HomeSectionCriteria();
        c.enabled().setEquals(enabled);
        return c;
    }

    private static HomeSectionCriteria withHomeSort(int order) {
        var c = new HomeSectionCriteria();
        c.sortOrder().setEquals(order);
        return c;
    }

    private static HomeSectionCriteria withHomeDistinct(boolean distinct) {
        var c = new HomeSectionCriteria();
        c.setDistinct(distinct);
        return c;
    }

    private static CategoryCriteria withCategoryId(Long id) {
        var c = new CategoryCriteria();
        c.id().setEquals(id);
        return c;
    }

    private static CategoryCriteria withCategorySlug(String slug) {
        var c = new CategoryCriteria();
        c.slug().setEquals(slug);
        return c;
    }

    private static CategoryCriteria withCategoryName(String name) {
        var c = new CategoryCriteria();
        c.name().setEquals(name);
        return c;
    }

    private static CategoryCriteria withCategoryImage(String url) {
        var c = new CategoryCriteria();
        c.imageUrl().setEquals(url);
        return c;
    }

    private static CategoryCriteria withCategorySort(int order) {
        var c = new CategoryCriteria();
        c.sortOrder().setEquals(order);
        return c;
    }

    private static CategoryCriteria withCategoryActive(boolean active) {
        var c = new CategoryCriteria();
        c.active().setEquals(active);
        return c;
    }

    private static CategoryCriteria withCategoryDistinct(boolean distinct) {
        var c = new CategoryCriteria();
        c.setDistinct(distinct);
        return c;
    }
}
