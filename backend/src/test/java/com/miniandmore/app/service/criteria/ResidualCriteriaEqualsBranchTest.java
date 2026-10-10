package com.miniandmore.app.service.criteria;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Exercises every equals short-circuit remaining on compact criteria classes. */
class ResidualCriteriaEqualsBranchTest {

    @ParameterizedTest
    @MethodSource("blogDifferences")
    void blogPostCriteria_eachFieldCanDiffer(BiConsumer<BlogPostCriteria, Integer> mutation) {
        assertDifferent(BlogPostCriteria::new, mutation);
    }

    @ParameterizedTest
    @MethodSource("profileDifferences")
    void customerProfileCriteria_eachFieldCanDiffer(BiConsumer<CustomerProfileCriteria, Integer> mutation) {
        assertDifferent(CustomerProfileCriteria::new, mutation);
    }

    @ParameterizedTest
    @MethodSource("policyDifferences")
    void policyCriteria_eachFieldCanDiffer(BiConsumer<PolicyCriteria, Integer> mutation) {
        assertDifferent(PolicyCriteria::new, mutation);
    }

    @ParameterizedTest
    @MethodSource("visibilityDifferences")
    void pageVisibilityCriteria_eachFieldCanDiffer(BiConsumer<PageVisibilityCriteria, Integer> mutation) {
        assertDifferent(PageVisibilityCriteria::new, mutation);
    }

    @ParameterizedTest
    @MethodSource("productDifferences")
    void productCriteria_eachFieldCanDiffer(BiConsumer<ProductCriteria, Integer> mutation) {
        assertDifferent(ProductCriteria::new, mutation);
    }

    @ParameterizedTest
    @MethodSource("siteSettingDifferences")
    void siteSettingCriteria_eachFieldCanDiffer(BiConsumer<SiteSettingCriteria, Integer> mutation) {
        assertDifferent(SiteSettingCriteria::new, mutation);
    }

    private static <T> void assertDifferent(Supplier<T> factory, BiConsumer<T, Integer> mutation) {
        T left = factory.get();
        T right = factory.get();
        mutation.accept(left, 1);
        mutation.accept(right, 2);
        assertThat(left).isNotEqualTo(right);
        assertThat(left).isEqualTo(left);
        assertThat(left).isNotEqualTo(null);
        assertThat(left).isNotEqualTo("other type");
    }

    private static Stream<Arguments> blogDifferences() {
        return Stream.of(
            Arguments.of((BiConsumer<BlogPostCriteria, Integer>) (c, v) -> c.id().setEquals((long) v)),
            Arguments.of((BiConsumer<BlogPostCriteria, Integer>) (c, v) -> c.slug().setEquals("slug-" + v)),
            Arguments.of((BiConsumer<BlogPostCriteria, Integer>) (c, v) -> c.title().setEquals("title-" + v)),
            Arguments.of((BiConsumer<BlogPostCriteria, Integer>) (c, v) -> c.coverUrl().setEquals("cover-" + v)),
            Arguments.of((BiConsumer<BlogPostCriteria, Integer>) (c, v) -> c.published().setEquals(v == 1)),
            Arguments.of(
                (BiConsumer<BlogPostCriteria, Integer>) (c, v) ->
                    c.publishedAt().setEquals(Instant.parse("2026-01-0" + v + "T00:00:00Z"))
            ),
            Arguments.of((BiConsumer<BlogPostCriteria, Integer>) (c, v) -> c.setDistinct(v == 1))
        );
    }

    private static Stream<Arguments> profileDifferences() {
        return Stream.of(
            Arguments.of((BiConsumer<CustomerProfileCriteria, Integer>) (c, v) -> c.id().setEquals((long) v)),
            Arguments.of((BiConsumer<CustomerProfileCriteria, Integer>) (c, v) -> c.userLogin().setEquals("user-" + v)),
            Arguments.of((BiConsumer<CustomerProfileCriteria, Integer>) (c, v) -> c.fullName().setEquals("name-" + v)),
            Arguments.of((BiConsumer<CustomerProfileCriteria, Integer>) (c, v) -> c.phone().setEquals("phone-" + v)),
            Arguments.of((BiConsumer<CustomerProfileCriteria, Integer>) (c, v) -> c.setDistinct(v == 1))
        );
    }

    private static Stream<Arguments> policyDifferences() {
        return Stream.of(
            Arguments.of((BiConsumer<PolicyCriteria, Integer>) (c, v) -> c.id().setEquals((long) v)),
            Arguments.of((BiConsumer<PolicyCriteria, Integer>) (c, v) -> c.slug().setEquals("slug-" + v)),
            Arguments.of((BiConsumer<PolicyCriteria, Integer>) (c, v) -> c.title().setEquals("title-" + v)),
            Arguments.of((BiConsumer<PolicyCriteria, Integer>) (c, v) -> c.setDistinct(v == 1))
        );
    }

    private static Stream<Arguments> visibilityDifferences() {
        return Stream.of(
            Arguments.of((BiConsumer<PageVisibilityCriteria, Integer>) (c, v) -> c.id().setEquals((long) v)),
            Arguments.of((BiConsumer<PageVisibilityCriteria, Integer>) (c, v) -> c.pageKey().setEquals("page-" + v)),
            Arguments.of((BiConsumer<PageVisibilityCriteria, Integer>) (c, v) -> c.visible().setEquals(v == 1)),
            Arguments.of((BiConsumer<PageVisibilityCriteria, Integer>) (c, v) -> c.setDistinct(v == 1))
        );
    }

    private static Stream<Arguments> productDifferences() {
        return Stream.of(
            Arguments.of((BiConsumer<ProductCriteria, Integer>) (c, v) -> c.id().setEquals((long) v)),
            Arguments.of((BiConsumer<ProductCriteria, Integer>) (c, v) -> c.slug().setEquals("slug-" + v)),
            Arguments.of((BiConsumer<ProductCriteria, Integer>) (c, v) -> c.name().setEquals("name-" + v)),
            Arguments.of((BiConsumer<ProductCriteria, Integer>) (c, v) -> c.sku().setEquals("sku-" + v)),
            Arguments.of((BiConsumer<ProductCriteria, Integer>) (c, v) -> c.fabricType().setEquals("fabric-" + v)),
            Arguments.of((BiConsumer<ProductCriteria, Integer>) (c, v) -> c.price().setEquals(BigDecimal.valueOf(v))),
            Arguments.of((BiConsumer<ProductCriteria, Integer>) (c, v) -> c.compareAtPrice().setEquals(BigDecimal.valueOf(v))),
            Arguments.of((BiConsumer<ProductCriteria, Integer>) (c, v) -> c.currency().setEquals("INR" + v)),
            Arguments.of((BiConsumer<ProductCriteria, Integer>) (c, v) -> c.ageMinMonths().setEquals(v)),
            Arguments.of((BiConsumer<ProductCriteria, Integer>) (c, v) -> c.ageMaxMonths().setEquals(v + 10)),
            Arguments.of((BiConsumer<ProductCriteria, Integer>) (c, v) -> c.badge().setEquals("badge-" + v)),
            Arguments.of((BiConsumer<ProductCriteria, Integer>) (c, v) -> c.stock().setEquals(v)),
            Arguments.of((BiConsumer<ProductCriteria, Integer>) (c, v) -> c.sortOrder().setEquals(v)),
            Arguments.of((BiConsumer<ProductCriteria, Integer>) (c, v) -> c.featured().setEquals(v == 1)),
            Arguments.of((BiConsumer<ProductCriteria, Integer>) (c, v) -> c.trending().setEquals(v == 1)),
            Arguments.of((BiConsumer<ProductCriteria, Integer>) (c, v) -> c.newArrival().setEquals(v == 1)),
            Arguments.of((BiConsumer<ProductCriteria, Integer>) (c, v) -> c.bestSeller().setEquals(v == 1)),
            Arguments.of((BiConsumer<ProductCriteria, Integer>) (c, v) -> c.active().setEquals(v == 1)),
            Arguments.of((BiConsumer<ProductCriteria, Integer>) (c, v) -> c.categoryId().setEquals((long) v)),
            Arguments.of((BiConsumer<ProductCriteria, Integer>) (c, v) -> c.setDistinct(v == 1))
        );
    }

    private static Stream<Arguments> siteSettingDifferences() {
        return Stream.of(
            Arguments.of((BiConsumer<SiteSettingCriteria, Integer>) (c, v) -> c.id().setEquals((long) v)),
            Arguments.of((BiConsumer<SiteSettingCriteria, Integer>) (c, v) -> c.brandName().setEquals("brand-" + v)),
            Arguments.of((BiConsumer<SiteSettingCriteria, Integer>) (c, v) -> c.careLabel().setEquals("care-" + v)),
            Arguments.of((BiConsumer<SiteSettingCriteria, Integer>) (c, v) -> c.contactEmail().setEquals("mail-" + v)),
            Arguments.of((BiConsumer<SiteSettingCriteria, Integer>) (c, v) -> c.contactPhone().setEquals("phone-" + v)),
            Arguments.of((BiConsumer<SiteSettingCriteria, Integer>) (c, v) -> c.ecatalogueLabel().setEquals("label-" + v)),
            Arguments.of((BiConsumer<SiteSettingCriteria, Integer>) (c, v) -> c.ecatalogueUrl().setEquals("url-" + v)),
            Arguments.of((BiConsumer<SiteSettingCriteria, Integer>) (c, v) -> c.facebookUrl().setEquals("fb-" + v)),
            Arguments.of((BiConsumer<SiteSettingCriteria, Integer>) (c, v) -> c.footerCopyright().setEquals("c-" + v)),
            Arguments.of((BiConsumer<SiteSettingCriteria, Integer>) (c, v) -> c.footerTagline().setEquals("t-" + v)),
            Arguments.of((BiConsumer<SiteSettingCriteria, Integer>) (c, v) -> c.instagramUrl().setEquals("ig-" + v)),
            Arguments.of((BiConsumer<SiteSettingCriteria, Integer>) (c, v) -> c.legalEntityName().setEquals("legal-" + v)),
            Arguments.of((BiConsumer<SiteSettingCriteria, Integer>) (c, v) -> c.logoHorizontalUrl().setEquals("lh-" + v)),
            Arguments.of((BiConsumer<SiteSettingCriteria, Integer>) (c, v) -> c.logoUrl().setEquals("logo-" + v)),
            Arguments.of((BiConsumer<SiteSettingCriteria, Integer>) (c, v) -> c.twitterUrl().setEquals("tw-" + v)),
            Arguments.of((BiConsumer<SiteSettingCriteria, Integer>) (c, v) -> c.whatsappNumber().setEquals("wa-" + v)),
            Arguments.of((BiConsumer<SiteSettingCriteria, Integer>) (c, v) -> c.youtubeUrl().setEquals("yt-" + v)),
            Arguments.of((BiConsumer<SiteSettingCriteria, Integer>) (c, v) -> c.setDistinct(v == 1))
        );
    }
}
