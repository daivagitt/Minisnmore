package com.miniandmore.app.service.criteria;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.assertj.core.api.Condition;
import org.junit.jupiter.api.Test;

class HomeSectionCriteriaTest {

    @Test
    void newHomeSectionCriteriaHasAllFiltersNullTest() {
        var homeSectionCriteria = new HomeSectionCriteria();
        assertThat(homeSectionCriteria).is(criteriaFiltersAre(Objects::isNull));
    }

    @Test
    void homeSectionCriteriaFluentMethodsCreatesFiltersTest() {
        var homeSectionCriteria = new HomeSectionCriteria();

        setAllFilters(homeSectionCriteria);

        assertThat(homeSectionCriteria).is(criteriaFiltersAre(Objects::nonNull));
    }

    @Test
    void homeSectionCriteriaCopyCreatesNullFilterTest() {
        var homeSectionCriteria = new HomeSectionCriteria();
        var copy = homeSectionCriteria.copy();

        assertThat(homeSectionCriteria).satisfies(
            criteria ->
                assertThat(criteria).is(
                    copyFiltersAre(copy, (a, b) -> (a == null || a instanceof Boolean) ? a == b : (a != b && a.equals(b)))
                ),
            criteria -> assertThat(criteria).isEqualTo(copy),
            criteria -> assertThat(criteria).hasSameHashCodeAs(copy)
        );

        assertThat(copy).satisfies(
            criteria -> assertThat(criteria).is(criteriaFiltersAre(Objects::isNull)),
            criteria -> assertThat(criteria).isEqualTo(homeSectionCriteria)
        );
    }

    @Test
    void homeSectionCriteriaCopyDuplicatesEveryExistingFilterTest() {
        var homeSectionCriteria = new HomeSectionCriteria();
        setAllFilters(homeSectionCriteria);

        var copy = homeSectionCriteria.copy();

        assertThat(homeSectionCriteria).satisfies(
            criteria ->
                assertThat(criteria).is(
                    copyFiltersAre(copy, (a, b) -> (a == null || a instanceof Boolean) ? a == b : (a != b && a.equals(b)))
                ),
            criteria -> assertThat(criteria).isEqualTo(copy),
            criteria -> assertThat(criteria).hasSameHashCodeAs(copy)
        );

        assertThat(copy).satisfies(
            criteria -> assertThat(criteria).is(criteriaFiltersAre(Objects::nonNull)),
            criteria -> assertThat(criteria).isEqualTo(homeSectionCriteria)
        );
    }

    @Test
    void toStringVerifier() {
        var homeSectionCriteria = new HomeSectionCriteria();

        assertThat(homeSectionCriteria).hasToString("HomeSectionCriteria{}");
    }

    private static void setAllFilters(HomeSectionCriteria homeSectionCriteria) {
        homeSectionCriteria.id();
        homeSectionCriteria.sectionKey();
        homeSectionCriteria.title();
        homeSectionCriteria.subtitle();
        homeSectionCriteria.enabled();
        homeSectionCriteria.sortOrder();
        homeSectionCriteria.distinct();
    }

    private static Condition<HomeSectionCriteria> criteriaFiltersAre(Function<Object, Boolean> condition) {
        return new Condition<>(
            criteria ->
                condition.apply(criteria.getId()) &&
                condition.apply(criteria.getSectionKey()) &&
                condition.apply(criteria.getTitle()) &&
                condition.apply(criteria.getSubtitle()) &&
                condition.apply(criteria.getEnabled()) &&
                condition.apply(criteria.getSortOrder()) &&
                condition.apply(criteria.getDistinct()),
            "every filter matches"
        );
    }

    private static Condition<HomeSectionCriteria> copyFiltersAre(HomeSectionCriteria copy, BiFunction<Object, Object, Boolean> condition) {
        return new Condition<>(
            criteria ->
                condition.apply(criteria.getId(), copy.getId()) &&
                condition.apply(criteria.getSectionKey(), copy.getSectionKey()) &&
                condition.apply(criteria.getTitle(), copy.getTitle()) &&
                condition.apply(criteria.getSubtitle(), copy.getSubtitle()) &&
                condition.apply(criteria.getEnabled(), copy.getEnabled()) &&
                condition.apply(criteria.getSortOrder(), copy.getSortOrder()) &&
                condition.apply(criteria.getDistinct(), copy.getDistinct()),
            "every filter matches"
        );
    }
}
