package com.miniandmore.app.service.criteria;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.assertj.core.api.Condition;
import org.junit.jupiter.api.Test;

class CustomerProfileCriteriaTest {

    @Test
    void newCustomerProfileCriteriaHasAllFiltersNullTest() {
        var customerProfileCriteria = new CustomerProfileCriteria();
        assertThat(customerProfileCriteria).is(criteriaFiltersAre(Objects::isNull));
    }

    @Test
    void customerProfileCriteriaFluentMethodsCreatesFiltersTest() {
        var customerProfileCriteria = new CustomerProfileCriteria();

        setAllFilters(customerProfileCriteria);

        assertThat(customerProfileCriteria).is(criteriaFiltersAre(Objects::nonNull));
    }

    @Test
    void customerProfileCriteriaCopyCreatesNullFilterTest() {
        var customerProfileCriteria = new CustomerProfileCriteria();
        var copy = customerProfileCriteria.copy();

        assertThat(customerProfileCriteria).satisfies(
            criteria ->
                assertThat(criteria).is(
                    copyFiltersAre(copy, (a, b) -> (a == null || a instanceof Boolean) ? a == b : (a != b && a.equals(b)))
                ),
            criteria -> assertThat(criteria).isEqualTo(copy),
            criteria -> assertThat(criteria).hasSameHashCodeAs(copy)
        );

        assertThat(copy).satisfies(
            criteria -> assertThat(criteria).is(criteriaFiltersAre(Objects::isNull)),
            criteria -> assertThat(criteria).isEqualTo(customerProfileCriteria)
        );
    }

    @Test
    void customerProfileCriteriaCopyDuplicatesEveryExistingFilterTest() {
        var customerProfileCriteria = new CustomerProfileCriteria();
        setAllFilters(customerProfileCriteria);

        var copy = customerProfileCriteria.copy();

        assertThat(customerProfileCriteria).satisfies(
            criteria ->
                assertThat(criteria).is(
                    copyFiltersAre(copy, (a, b) -> (a == null || a instanceof Boolean) ? a == b : (a != b && a.equals(b)))
                ),
            criteria -> assertThat(criteria).isEqualTo(copy),
            criteria -> assertThat(criteria).hasSameHashCodeAs(copy)
        );

        assertThat(copy).satisfies(
            criteria -> assertThat(criteria).is(criteriaFiltersAre(Objects::nonNull)),
            criteria -> assertThat(criteria).isEqualTo(customerProfileCriteria)
        );
    }

    @Test
    void toStringVerifier() {
        var customerProfileCriteria = new CustomerProfileCriteria();

        assertThat(customerProfileCriteria).hasToString("CustomerProfileCriteria{}");
    }

    private static void setAllFilters(CustomerProfileCriteria customerProfileCriteria) {
        customerProfileCriteria.id();
        customerProfileCriteria.userLogin();
        customerProfileCriteria.fullName();
        customerProfileCriteria.phone();
        customerProfileCriteria.distinct();
    }

    private static Condition<CustomerProfileCriteria> criteriaFiltersAre(Function<Object, Boolean> condition) {
        return new Condition<>(
            criteria ->
                condition.apply(criteria.getId()) &&
                condition.apply(criteria.getUserLogin()) &&
                condition.apply(criteria.getFullName()) &&
                condition.apply(criteria.getPhone()) &&
                condition.apply(criteria.getDistinct()),
            "every filter matches"
        );
    }

    private static Condition<CustomerProfileCriteria> copyFiltersAre(
        CustomerProfileCriteria copy,
        BiFunction<Object, Object, Boolean> condition
    ) {
        return new Condition<>(
            criteria ->
                condition.apply(criteria.getId(), copy.getId()) &&
                condition.apply(criteria.getUserLogin(), copy.getUserLogin()) &&
                condition.apply(criteria.getFullName(), copy.getFullName()) &&
                condition.apply(criteria.getPhone(), copy.getPhone()) &&
                condition.apply(criteria.getDistinct(), copy.getDistinct()),
            "every filter matches"
        );
    }
}
