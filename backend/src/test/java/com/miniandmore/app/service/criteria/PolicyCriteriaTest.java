package com.miniandmore.app.service.criteria;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.assertj.core.api.Condition;
import org.junit.jupiter.api.Test;

class PolicyCriteriaTest {

    @Test
    void newPolicyCriteriaHasAllFiltersNullTest() {
        var policyCriteria = new PolicyCriteria();
        assertThat(policyCriteria).is(criteriaFiltersAre(Objects::isNull));
    }

    @Test
    void policyCriteriaFluentMethodsCreatesFiltersTest() {
        var policyCriteria = new PolicyCriteria();

        setAllFilters(policyCriteria);

        assertThat(policyCriteria).is(criteriaFiltersAre(Objects::nonNull));
    }

    @Test
    void policyCriteriaCopyCreatesNullFilterTest() {
        var policyCriteria = new PolicyCriteria();
        var copy = policyCriteria.copy();

        assertThat(policyCriteria).satisfies(
            criteria ->
                assertThat(criteria).is(
                    copyFiltersAre(copy, (a, b) -> (a == null || a instanceof Boolean) ? a == b : (a != b && a.equals(b)))
                ),
            criteria -> assertThat(criteria).isEqualTo(copy),
            criteria -> assertThat(criteria).hasSameHashCodeAs(copy)
        );

        assertThat(copy).satisfies(
            criteria -> assertThat(criteria).is(criteriaFiltersAre(Objects::isNull)),
            criteria -> assertThat(criteria).isEqualTo(policyCriteria)
        );
    }

    @Test
    void policyCriteriaCopyDuplicatesEveryExistingFilterTest() {
        var policyCriteria = new PolicyCriteria();
        setAllFilters(policyCriteria);

        var copy = policyCriteria.copy();

        assertThat(policyCriteria).satisfies(
            criteria ->
                assertThat(criteria).is(
                    copyFiltersAre(copy, (a, b) -> (a == null || a instanceof Boolean) ? a == b : (a != b && a.equals(b)))
                ),
            criteria -> assertThat(criteria).isEqualTo(copy),
            criteria -> assertThat(criteria).hasSameHashCodeAs(copy)
        );

        assertThat(copy).satisfies(
            criteria -> assertThat(criteria).is(criteriaFiltersAre(Objects::nonNull)),
            criteria -> assertThat(criteria).isEqualTo(policyCriteria)
        );
    }

    @Test
    void toStringVerifier() {
        var policyCriteria = new PolicyCriteria();

        assertThat(policyCriteria).hasToString("PolicyCriteria{}");
    }

    private static void setAllFilters(PolicyCriteria policyCriteria) {
        policyCriteria.id();
        policyCriteria.slug();
        policyCriteria.title();
        policyCriteria.distinct();
    }

    private static Condition<PolicyCriteria> criteriaFiltersAre(Function<Object, Boolean> condition) {
        return new Condition<>(
            criteria ->
                condition.apply(criteria.getId()) &&
                condition.apply(criteria.getSlug()) &&
                condition.apply(criteria.getTitle()) &&
                condition.apply(criteria.getDistinct()),
            "every filter matches"
        );
    }

    private static Condition<PolicyCriteria> copyFiltersAre(PolicyCriteria copy, BiFunction<Object, Object, Boolean> condition) {
        return new Condition<>(
            criteria ->
                condition.apply(criteria.getId(), copy.getId()) &&
                condition.apply(criteria.getSlug(), copy.getSlug()) &&
                condition.apply(criteria.getTitle(), copy.getTitle()) &&
                condition.apply(criteria.getDistinct(), copy.getDistinct()),
            "every filter matches"
        );
    }
}
