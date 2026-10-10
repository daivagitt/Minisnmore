package com.miniandmore.app.service.criteria;

import static org.assertj.core.api.Assertions.assertThat;

import com.miniandmore.app.domain.enumeration.PaymentStatus;
import java.math.BigDecimal;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Covers JaCoCo equals() short-circuit branches across JHipster criteria classes. */
class CriteriaEqualsBranchBatchTest {

    @ParameterizedTest
    @MethodSource("customerOrderUnequalPairs")
    void customerOrderCriteria_notEqualWhenSingleFilterDiffers(CustomerOrderCriteria left, CustomerOrderCriteria right) {
        assertThat(left).isNotEqualTo(right);
    }

    @ParameterizedTest
    @MethodSource("productUnequalPairs")
    void productCriteria_notEqualWhenSingleFilterDiffers(ProductCriteria left, ProductCriteria right) {
        assertThat(left).isNotEqualTo(right);
        assertThat(left.hashCode()).isNotEqualTo(right.hashCode());
    }

    @ParameterizedTest
    @MethodSource("siteSettingUnequalPairs")
    void siteSettingCriteria_notEqualWhenSingleFilterDiffers(SiteSettingCriteria left, SiteSettingCriteria right) {
        assertThat(left).isNotEqualTo(right);
    }

    private static Stream<Arguments> customerOrderUnequalPairs() {
        return Stream.of(
            customerPair(() -> withOrderId(1L), () -> withOrderId(2L)),
            customerPair(() -> withPaymentStatus(PaymentStatus.PAID), () -> withPaymentStatus(PaymentStatus.PENDING)),
            customerPair(() -> withRazorpayOrderId("ro-1"), () -> withRazorpayOrderId("ro-2")),
            customerPair(() -> withRazorpayPaymentId("pay-1"), () -> withRazorpayPaymentId("pay-2")),
            customerPair(() -> withRazorpaySignature("sig-1"), () -> withRazorpaySignature("sig-2")),
            customerPair(() -> withSubtotal("10"), () -> withSubtotal("11")),
            customerPair(() -> withDiscount("1"), () -> withDiscount("2")),
            customerPair(() -> withShipping("3"), () -> withShipping("4")),
            customerPair(() -> withTax("5"), () -> withTax("6")),
            customerPair(() -> withDonation("7"), () -> withDonation("8")),
            customerPair(() -> withTotal("9"), () -> withTotal("10")),
            customerPair(() -> withCouponCode("SAVE"), () -> withCouponCode("OTHER")),
            customerPair(() -> withNotes("n1"), () -> withNotes("n2")),
            customerPair(() -> withItemsId(1L), () -> withItemsId(2L))
        );
    }

    private static Stream<Arguments> productUnequalPairs() {
        return Stream.of(
            productPair(() -> withProductPrice("10"), () -> withProductPrice("11")),
            productPair(() -> withCompareAt("20"), () -> withCompareAt("21")),
            productPair(() -> withCurrency("INR"), () -> withCurrency("USD")),
            productPair(() -> withAgeMin(0), () -> withAgeMin(1)),
            productPair(() -> withAgeMax(12), () -> withAgeMax(24)),
            productPair(() -> withBadge("NEW"), () -> withBadge("SALE")),
            productPair(() -> withStock(1), () -> withStock(2)),
            productPair(() -> withSortOrder(1), () -> withSortOrder(2)),
            productPair(() -> withTrending(true), () -> withTrending(false)),
            productPair(() -> withNewArrival(true), () -> withNewArrival(false)),
            productPair(() -> withBestSeller(true), () -> withBestSeller(false)),
            productPair(() -> withActive(true), () -> withActive(false)),
            productPair(() -> withCategoryId(1L), () -> withCategoryId(2L))
        );
    }

    private static Stream<Arguments> siteSettingUnequalPairs() {
        return Stream.of(
            sitePair(() -> withSiteId(1L), () -> withSiteId(2L)),
            sitePair(() -> withCareLabel("A"), () -> withCareLabel("B")),
            sitePair(() -> withContactPhone("1"), () -> withContactPhone("2")),
            sitePair(() -> withEcatalogueLabel("L1"), () -> withEcatalogueLabel("L2")),
            sitePair(() -> withEcatalogueUrl("u1"), () -> withEcatalogueUrl("u2")),
            sitePair(() -> withFacebookUrl("f1"), () -> withFacebookUrl("f2")),
            sitePair(() -> withFooterCopyright("c1"), () -> withFooterCopyright("c2")),
            sitePair(() -> withFooterTagline("t1"), () -> withFooterTagline("t2")),
            sitePair(() -> withInstagramUrl("i1"), () -> withInstagramUrl("i2")),
            sitePair(() -> withLegalEntityName("le1"), () -> withLegalEntityName("le2")),
            sitePair(() -> withLogoHorizontalUrl("h1"), () -> withLogoHorizontalUrl("h2")),
            sitePair(() -> withLogoUrl("l1"), () -> withLogoUrl("l2")),
            sitePair(() -> withTwitterUrl("tw1"), () -> withTwitterUrl("tw2")),
            sitePair(() -> withWhatsappNumber("w1"), () -> withWhatsappNumber("w2")),
            sitePair(() -> withYoutubeUrl("y1"), () -> withYoutubeUrl("y2"))
        );
    }

    private static Arguments customerPair(Supplier<CustomerOrderCriteria> left, Supplier<CustomerOrderCriteria> right) {
        return Arguments.of(left.get(), right.get());
    }

    private static Arguments productPair(Supplier<ProductCriteria> left, Supplier<ProductCriteria> right) {
        return Arguments.of(left.get(), right.get());
    }

    private static Arguments sitePair(Supplier<SiteSettingCriteria> left, Supplier<SiteSettingCriteria> right) {
        return Arguments.of(left.get(), right.get());
    }

    private static CustomerOrderCriteria withOrderId(Long id) {
        var c = new CustomerOrderCriteria();
        c.id().setEquals(id);
        return c;
    }

    private static CustomerOrderCriteria withPaymentStatus(PaymentStatus status) {
        var c = new CustomerOrderCriteria();
        c.paymentStatus().setEquals(status);
        return c;
    }

    private static CustomerOrderCriteria withRazorpayOrderId(String id) {
        var c = new CustomerOrderCriteria();
        c.razorpayOrderId().setEquals(id);
        return c;
    }

    private static CustomerOrderCriteria withRazorpayPaymentId(String id) {
        var c = new CustomerOrderCriteria();
        c.razorpayPaymentId().setEquals(id);
        return c;
    }

    private static CustomerOrderCriteria withRazorpaySignature(String sig) {
        var c = new CustomerOrderCriteria();
        c.razorpaySignature().setEquals(sig);
        return c;
    }

    private static CustomerOrderCriteria withSubtotal(String amount) {
        var c = new CustomerOrderCriteria();
        c.subtotal().setEquals(new BigDecimal(amount));
        return c;
    }

    private static CustomerOrderCriteria withDiscount(String amount) {
        var c = new CustomerOrderCriteria();
        c.discount().setEquals(new BigDecimal(amount));
        return c;
    }

    private static CustomerOrderCriteria withShipping(String amount) {
        var c = new CustomerOrderCriteria();
        c.shipping().setEquals(new BigDecimal(amount));
        return c;
    }

    private static CustomerOrderCriteria withTax(String amount) {
        var c = new CustomerOrderCriteria();
        c.tax().setEquals(new BigDecimal(amount));
        return c;
    }

    private static CustomerOrderCriteria withDonation(String amount) {
        var c = new CustomerOrderCriteria();
        c.donation().setEquals(new BigDecimal(amount));
        return c;
    }

    private static CustomerOrderCriteria withTotal(String amount) {
        var c = new CustomerOrderCriteria();
        c.total().setEquals(new BigDecimal(amount));
        return c;
    }

    private static CustomerOrderCriteria withCouponCode(String code) {
        var c = new CustomerOrderCriteria();
        c.couponCode().setEquals(code);
        return c;
    }

    private static CustomerOrderCriteria withNotes(String notes) {
        var c = new CustomerOrderCriteria();
        c.notes().setEquals(notes);
        return c;
    }

    private static CustomerOrderCriteria withItemsId(Long id) {
        var c = new CustomerOrderCriteria();
        c.itemsId().setEquals(id);
        return c;
    }

    private static ProductCriteria withProductPrice(String amount) {
        var c = new ProductCriteria();
        c.price().setEquals(new BigDecimal(amount));
        return c;
    }

    private static ProductCriteria withCompareAt(String amount) {
        var c = new ProductCriteria();
        c.compareAtPrice().setEquals(new BigDecimal(amount));
        return c;
    }

    private static ProductCriteria withCurrency(String currency) {
        var c = new ProductCriteria();
        c.currency().setEquals(currency);
        return c;
    }

    private static ProductCriteria withAgeMin(int months) {
        var c = new ProductCriteria();
        c.ageMinMonths().setEquals(months);
        return c;
    }

    private static ProductCriteria withAgeMax(int months) {
        var c = new ProductCriteria();
        c.ageMaxMonths().setEquals(months);
        return c;
    }

    private static ProductCriteria withBadge(String badge) {
        var c = new ProductCriteria();
        c.badge().setEquals(badge);
        return c;
    }

    private static ProductCriteria withStock(int stock) {
        var c = new ProductCriteria();
        c.stock().setEquals(stock);
        return c;
    }

    private static ProductCriteria withSortOrder(int order) {
        var c = new ProductCriteria();
        c.sortOrder().setEquals(order);
        return c;
    }

    private static ProductCriteria withTrending(boolean trending) {
        var c = new ProductCriteria();
        c.trending().setEquals(trending);
        return c;
    }

    private static ProductCriteria withNewArrival(boolean value) {
        var c = new ProductCriteria();
        c.newArrival().setEquals(value);
        return c;
    }

    private static ProductCriteria withBestSeller(boolean value) {
        var c = new ProductCriteria();
        c.bestSeller().setEquals(value);
        return c;
    }

    private static ProductCriteria withActive(boolean active) {
        var c = new ProductCriteria();
        c.active().setEquals(active);
        return c;
    }

    private static ProductCriteria withCategoryId(Long id) {
        var c = new ProductCriteria();
        c.categoryId().setEquals(id);
        return c;
    }

    private static SiteSettingCriteria withSiteId(Long id) {
        var c = new SiteSettingCriteria();
        c.id().setEquals(id);
        return c;
    }

    private static SiteSettingCriteria withCareLabel(String value) {
        var c = new SiteSettingCriteria();
        c.careLabel().setEquals(value);
        return c;
    }

    private static SiteSettingCriteria withContactPhone(String value) {
        var c = new SiteSettingCriteria();
        c.contactPhone().setEquals(value);
        return c;
    }

    private static SiteSettingCriteria withEcatalogueLabel(String value) {
        var c = new SiteSettingCriteria();
        c.ecatalogueLabel().setEquals(value);
        return c;
    }

    private static SiteSettingCriteria withEcatalogueUrl(String value) {
        var c = new SiteSettingCriteria();
        c.ecatalogueUrl().setEquals(value);
        return c;
    }

    private static SiteSettingCriteria withFacebookUrl(String value) {
        var c = new SiteSettingCriteria();
        c.facebookUrl().setEquals(value);
        return c;
    }

    private static SiteSettingCriteria withFooterCopyright(String value) {
        var c = new SiteSettingCriteria();
        c.footerCopyright().setEquals(value);
        return c;
    }

    private static SiteSettingCriteria withFooterTagline(String value) {
        var c = new SiteSettingCriteria();
        c.footerTagline().setEquals(value);
        return c;
    }

    private static SiteSettingCriteria withInstagramUrl(String value) {
        var c = new SiteSettingCriteria();
        c.instagramUrl().setEquals(value);
        return c;
    }

    private static SiteSettingCriteria withLegalEntityName(String value) {
        var c = new SiteSettingCriteria();
        c.legalEntityName().setEquals(value);
        return c;
    }

    private static SiteSettingCriteria withLogoHorizontalUrl(String value) {
        var c = new SiteSettingCriteria();
        c.logoHorizontalUrl().setEquals(value);
        return c;
    }

    private static SiteSettingCriteria withLogoUrl(String value) {
        var c = new SiteSettingCriteria();
        c.logoUrl().setEquals(value);
        return c;
    }

    private static SiteSettingCriteria withTwitterUrl(String value) {
        var c = new SiteSettingCriteria();
        c.twitterUrl().setEquals(value);
        return c;
    }

    private static SiteSettingCriteria withWhatsappNumber(String value) {
        var c = new SiteSettingCriteria();
        c.whatsappNumber().setEquals(value);
        return c;
    }

    private static SiteSettingCriteria withYoutubeUrl(String value) {
        var c = new SiteSettingCriteria();
        c.youtubeUrl().setEquals(value);
        return c;
    }
}
