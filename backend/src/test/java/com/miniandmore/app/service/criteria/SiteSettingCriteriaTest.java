package com.miniandmore.app.service.criteria;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;
import org.assertj.core.api.Condition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class SiteSettingCriteriaTest {

    @Test
    void newSiteSettingCriteriaHasAllFiltersNullTest() {
        var siteSettingCriteria = new SiteSettingCriteria();
        assertThat(siteSettingCriteria).is(criteriaFiltersAre(Objects::isNull));
    }

    @Test
    void siteSettingCriteriaFluentMethodsCreatesFiltersTest() {
        var siteSettingCriteria = new SiteSettingCriteria();

        setAllFilters(siteSettingCriteria);

        assertThat(siteSettingCriteria).is(criteriaFiltersAre(Objects::nonNull));
    }

    @Test
    void siteSettingCriteriaCopyCreatesNullFilterTest() {
        var siteSettingCriteria = new SiteSettingCriteria();
        var copy = siteSettingCriteria.copy();

        assertThat(siteSettingCriteria).satisfies(
            criteria ->
                assertThat(criteria).is(
                    copyFiltersAre(copy, (a, b) -> (a == null || a instanceof Boolean) ? a == b : (a != b && a.equals(b)))
                ),
            criteria -> assertThat(criteria).isEqualTo(copy),
            criteria -> assertThat(criteria).hasSameHashCodeAs(copy)
        );

        assertThat(copy).satisfies(
            criteria -> assertThat(criteria).is(criteriaFiltersAre(Objects::isNull)),
            criteria -> assertThat(criteria).isEqualTo(siteSettingCriteria)
        );
    }

    @Test
    void siteSettingCriteriaCopyDuplicatesEveryExistingFilterTest() {
        var siteSettingCriteria = new SiteSettingCriteria();
        setAllFilters(siteSettingCriteria);

        var copy = siteSettingCriteria.copy();

        assertThat(siteSettingCriteria).satisfies(
            criteria ->
                assertThat(criteria).is(
                    copyFiltersAre(copy, (a, b) -> (a == null || a instanceof Boolean) ? a == b : (a != b && a.equals(b)))
                ),
            criteria -> assertThat(criteria).isEqualTo(copy),
            criteria -> assertThat(criteria).hasSameHashCodeAs(copy)
        );

        assertThat(copy).satisfies(
            criteria -> assertThat(criteria).is(criteriaFiltersAre(Objects::nonNull)),
            criteria -> assertThat(criteria).isEqualTo(siteSettingCriteria)
        );
    }

    @Test
    void toStringVerifier() {
        var siteSettingCriteria = new SiteSettingCriteria();

        assertThat(siteSettingCriteria).hasToString("SiteSettingCriteria{}");
    }

    @Test
    void equals_sameInstanceNullAndWrongType() {
        var siteSettingCriteria = new SiteSettingCriteria();
        assertThat(siteSettingCriteria.equals(siteSettingCriteria)).isTrue();
        assertThat(siteSettingCriteria.equals(null)).isFalse();
        assertThat(siteSettingCriteria.equals("SiteSettingCriteria")).isFalse();
    }

    @ParameterizedTest
    @MethodSource("unequalCriteriaPairs")
    void equals_falseWhenAnyFilterDiffers(SiteSettingCriteria left, SiteSettingCriteria right) {
        assertThat(left).isNotEqualTo(right);
    }

    private static Stream<org.junit.jupiter.params.provider.Arguments> unequalCriteriaPairs() {
        return Stream.of(
            org.junit.jupiter.params.provider.Arguments.of(withBrand("A"), withBrand("B")),
            org.junit.jupiter.params.provider.Arguments.of(withContactEmail("a@b.com"), withContactEmail("c@d.com")),
            org.junit.jupiter.params.provider.Arguments.of(withDistinct(true), withDistinct(false))
        );
    }

    private static SiteSettingCriteria withBrand(String brand) {
        var c = new SiteSettingCriteria();
        c.brandName().setEquals(brand);
        return c;
    }

    private static SiteSettingCriteria withContactEmail(String email) {
        var c = new SiteSettingCriteria();
        c.contactEmail().setEquals(email);
        return c;
    }

    private static SiteSettingCriteria withDistinct(Boolean distinct) {
        var c = new SiteSettingCriteria();
        c.setDistinct(distinct);
        return c;
    }

    private static void setAllFilters(SiteSettingCriteria siteSettingCriteria) {
        siteSettingCriteria.id();
        siteSettingCriteria.brandName();
        siteSettingCriteria.careLabel();
        siteSettingCriteria.contactEmail();
        siteSettingCriteria.contactPhone();
        siteSettingCriteria.ecatalogueLabel();
        siteSettingCriteria.ecatalogueUrl();
        siteSettingCriteria.facebookUrl();
        siteSettingCriteria.footerCopyright();
        siteSettingCriteria.footerTagline();
        siteSettingCriteria.instagramUrl();
        siteSettingCriteria.legalEntityName();
        siteSettingCriteria.logoHorizontalUrl();
        siteSettingCriteria.logoUrl();
        siteSettingCriteria.twitterUrl();
        siteSettingCriteria.whatsappNumber();
        siteSettingCriteria.youtubeUrl();
        siteSettingCriteria.distinct();
    }

    private static Condition<SiteSettingCriteria> criteriaFiltersAre(Function<Object, Boolean> condition) {
        return new Condition<>(
            criteria ->
                condition.apply(criteria.getId()) &&
                condition.apply(criteria.getBrandName()) &&
                condition.apply(criteria.getCareLabel()) &&
                condition.apply(criteria.getContactEmail()) &&
                condition.apply(criteria.getContactPhone()) &&
                condition.apply(criteria.getEcatalogueLabel()) &&
                condition.apply(criteria.getEcatalogueUrl()) &&
                condition.apply(criteria.getFacebookUrl()) &&
                condition.apply(criteria.getFooterCopyright()) &&
                condition.apply(criteria.getFooterTagline()) &&
                condition.apply(criteria.getInstagramUrl()) &&
                condition.apply(criteria.getLegalEntityName()) &&
                condition.apply(criteria.getLogoHorizontalUrl()) &&
                condition.apply(criteria.getLogoUrl()) &&
                condition.apply(criteria.getTwitterUrl()) &&
                condition.apply(criteria.getWhatsappNumber()) &&
                condition.apply(criteria.getYoutubeUrl()) &&
                condition.apply(criteria.getDistinct()),
            "every filter matches"
        );
    }

    private static Condition<SiteSettingCriteria> copyFiltersAre(SiteSettingCriteria copy, BiFunction<Object, Object, Boolean> condition) {
        return new Condition<>(
            criteria ->
                condition.apply(criteria.getId(), copy.getId()) &&
                condition.apply(criteria.getBrandName(), copy.getBrandName()) &&
                condition.apply(criteria.getCareLabel(), copy.getCareLabel()) &&
                condition.apply(criteria.getContactEmail(), copy.getContactEmail()) &&
                condition.apply(criteria.getContactPhone(), copy.getContactPhone()) &&
                condition.apply(criteria.getEcatalogueLabel(), copy.getEcatalogueLabel()) &&
                condition.apply(criteria.getEcatalogueUrl(), copy.getEcatalogueUrl()) &&
                condition.apply(criteria.getFacebookUrl(), copy.getFacebookUrl()) &&
                condition.apply(criteria.getFooterCopyright(), copy.getFooterCopyright()) &&
                condition.apply(criteria.getFooterTagline(), copy.getFooterTagline()) &&
                condition.apply(criteria.getInstagramUrl(), copy.getInstagramUrl()) &&
                condition.apply(criteria.getLegalEntityName(), copy.getLegalEntityName()) &&
                condition.apply(criteria.getLogoHorizontalUrl(), copy.getLogoHorizontalUrl()) &&
                condition.apply(criteria.getLogoUrl(), copy.getLogoUrl()) &&
                condition.apply(criteria.getTwitterUrl(), copy.getTwitterUrl()) &&
                condition.apply(criteria.getWhatsappNumber(), copy.getWhatsappNumber()) &&
                condition.apply(criteria.getYoutubeUrl(), copy.getYoutubeUrl()) &&
                condition.apply(criteria.getDistinct(), copy.getDistinct()),
            "every filter matches"
        );
    }
}
