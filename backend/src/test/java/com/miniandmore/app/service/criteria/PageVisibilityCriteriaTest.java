package com.miniandmore.app.service.criteria;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.assertj.core.api.Condition;
import org.junit.jupiter.api.Test;

class PageVisibilityCriteriaTest {

    @Test
    void newPageVisibilityCriteriaHasAllFiltersNullTest() {
        var pageVisibilityCriteria = new PageVisibilityCriteria();
        assertThat(pageVisibilityCriteria).is(criteriaFiltersAre(Objects::isNull));
    }

    @Test
    void pageVisibilityCriteriaFluentMethodsCreatesFiltersTest() {
        var pageVisibilityCriteria = new PageVisibilityCriteria();

        setAllFilters(pageVisibilityCriteria);

        assertThat(pageVisibilityCriteria).is(criteriaFiltersAre(Objects::nonNull));
    }

    @Test
    void pageVisibilityCriteriaCopyCreatesNullFilterTest() {
        var pageVisibilityCriteria = new PageVisibilityCriteria();
        var copy = pageVisibilityCriteria.copy();

        assertThat(pageVisibilityCriteria).satisfies(
            criteria ->
                assertThat(criteria).is(
                    copyFiltersAre(copy, (a, b) -> (a == null || a instanceof Boolean) ? a == b : (a != b && a.equals(b)))
                ),
            criteria -> assertThat(criteria).isEqualTo(copy),
            criteria -> assertThat(criteria).hasSameHashCodeAs(copy)
        );

        assertThat(copy).satisfies(
            criteria -> assertThat(criteria).is(criteriaFiltersAre(Objects::isNull)),
            criteria -> assertThat(criteria).isEqualTo(pageVisibilityCriteria)
        );
    }

    @Test
    void pageVisibilityCriteriaCopyDuplicatesEveryExistingFilterTest() {
        var pageVisibilityCriteria = new PageVisibilityCriteria();
        setAllFilters(pageVisibilityCriteria);

        var copy = pageVisibilityCriteria.copy();

        assertThat(pageVisibilityCriteria).satisfies(
            criteria ->
                assertThat(criteria).is(
                    copyFiltersAre(copy, (a, b) -> (a == null || a instanceof Boolean) ? a == b : (a != b && a.equals(b)))
                ),
            criteria -> assertThat(criteria).isEqualTo(copy),
            criteria -> assertThat(criteria).hasSameHashCodeAs(copy)
        );

        assertThat(copy).satisfies(
            criteria -> assertThat(criteria).is(criteriaFiltersAre(Objects::nonNull)),
            criteria -> assertThat(criteria).isEqualTo(pageVisibilityCriteria)
        );
    }

    @Test
    void toStringVerifier() {
        var pageVisibilityCriteria = new PageVisibilityCriteria();

        assertThat(pageVisibilityCriteria).hasToString("PageVisibilityCriteria{}");
    }

    private static void setAllFilters(PageVisibilityCriteria pageVisibilityCriteria) {
        pageVisibilityCriteria.id();
        pageVisibilityCriteria.pageKey();
        pageVisibilityCriteria.visible();
        pageVisibilityCriteria.distinct();
    }

    private static Condition<PageVisibilityCriteria> criteriaFiltersAre(Function<Object, Boolean> condition) {
        return new Condition<>(
            criteria ->
                condition.apply(criteria.getId()) &&
                condition.apply(criteria.getPageKey()) &&
                condition.apply(criteria.getVisible()) &&
                condition.apply(criteria.getDistinct()),
            "every filter matches"
        );
    }

    private static Condition<PageVisibilityCriteria> copyFiltersAre(
        PageVisibilityCriteria copy,
        BiFunction<Object, Object, Boolean> condition
    ) {
        return new Condition<>(
            criteria ->
                condition.apply(criteria.getId(), copy.getId()) &&
                condition.apply(criteria.getPageKey(), copy.getPageKey()) &&
                condition.apply(criteria.getVisible(), copy.getVisible()) &&
                condition.apply(criteria.getDistinct(), copy.getDistinct()),
            "every filter matches"
        );
    }
}
