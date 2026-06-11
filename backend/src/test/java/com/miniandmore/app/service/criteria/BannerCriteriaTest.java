package com.miniandmore.app.service.criteria;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.assertj.core.api.Condition;
import org.junit.jupiter.api.Test;

class BannerCriteriaTest {

    @Test
    void newBannerCriteriaHasAllFiltersNullTest() {
        var bannerCriteria = new BannerCriteria();
        assertThat(bannerCriteria).is(criteriaFiltersAre(Objects::isNull));
    }

    @Test
    void bannerCriteriaFluentMethodsCreatesFiltersTest() {
        var bannerCriteria = new BannerCriteria();

        setAllFilters(bannerCriteria);

        assertThat(bannerCriteria).is(criteriaFiltersAre(Objects::nonNull));
    }

    @Test
    void bannerCriteriaCopyCreatesNullFilterTest() {
        var bannerCriteria = new BannerCriteria();
        var copy = bannerCriteria.copy();

        assertThat(bannerCriteria).satisfies(
            criteria ->
                assertThat(criteria).is(
                    copyFiltersAre(copy, (a, b) -> (a == null || a instanceof Boolean) ? a == b : (a != b && a.equals(b)))
                ),
            criteria -> assertThat(criteria).isEqualTo(copy),
            criteria -> assertThat(criteria).hasSameHashCodeAs(copy)
        );

        assertThat(copy).satisfies(
            criteria -> assertThat(criteria).is(criteriaFiltersAre(Objects::isNull)),
            criteria -> assertThat(criteria).isEqualTo(bannerCriteria)
        );
    }

    @Test
    void bannerCriteriaCopyDuplicatesEveryExistingFilterTest() {
        var bannerCriteria = new BannerCriteria();
        setAllFilters(bannerCriteria);

        var copy = bannerCriteria.copy();

        assertThat(bannerCriteria).satisfies(
            criteria ->
                assertThat(criteria).is(
                    copyFiltersAre(copy, (a, b) -> (a == null || a instanceof Boolean) ? a == b : (a != b && a.equals(b)))
                ),
            criteria -> assertThat(criteria).isEqualTo(copy),
            criteria -> assertThat(criteria).hasSameHashCodeAs(copy)
        );

        assertThat(copy).satisfies(
            criteria -> assertThat(criteria).is(criteriaFiltersAre(Objects::nonNull)),
            criteria -> assertThat(criteria).isEqualTo(bannerCriteria)
        );
    }

    @Test
    void toStringVerifier() {
        var bannerCriteria = new BannerCriteria();

        assertThat(bannerCriteria).hasToString("BannerCriteria{}");
    }

    private static void setAllFilters(BannerCriteria bannerCriteria) {
        bannerCriteria.id();
        bannerCriteria.title();
        bannerCriteria.subtitle();
        bannerCriteria.imageUrl();
        bannerCriteria.linkUrl();
        bannerCriteria.ctaLabel();
        bannerCriteria.sortOrder();
        bannerCriteria.active();
        bannerCriteria.distinct();
    }

    private static Condition<BannerCriteria> criteriaFiltersAre(Function<Object, Boolean> condition) {
        return new Condition<>(
            criteria ->
                condition.apply(criteria.getId()) &&
                condition.apply(criteria.getTitle()) &&
                condition.apply(criteria.getSubtitle()) &&
                condition.apply(criteria.getImageUrl()) &&
                condition.apply(criteria.getLinkUrl()) &&
                condition.apply(criteria.getCtaLabel()) &&
                condition.apply(criteria.getSortOrder()) &&
                condition.apply(criteria.getActive()) &&
                condition.apply(criteria.getDistinct()),
            "every filter matches"
        );
    }

    private static Condition<BannerCriteria> copyFiltersAre(BannerCriteria copy, BiFunction<Object, Object, Boolean> condition) {
        return new Condition<>(
            criteria ->
                condition.apply(criteria.getId(), copy.getId()) &&
                condition.apply(criteria.getTitle(), copy.getTitle()) &&
                condition.apply(criteria.getSubtitle(), copy.getSubtitle()) &&
                condition.apply(criteria.getImageUrl(), copy.getImageUrl()) &&
                condition.apply(criteria.getLinkUrl(), copy.getLinkUrl()) &&
                condition.apply(criteria.getCtaLabel(), copy.getCtaLabel()) &&
                condition.apply(criteria.getSortOrder(), copy.getSortOrder()) &&
                condition.apply(criteria.getActive(), copy.getActive()) &&
                condition.apply(criteria.getDistinct(), copy.getDistinct()),
            "every filter matches"
        );
    }
}
