package com.miniandmore.app.service.criteria;

import static org.assertj.core.api.Assertions.assertThat;

import com.miniandmore.app.domain.enumeration.OrderStatus;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;
import org.assertj.core.api.Condition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class CustomerOrderCriteriaTest {

    @Test
    void newCustomerOrderCriteriaHasAllFiltersNullTest() {
        var customerOrderCriteria = new CustomerOrderCriteria();
        assertThat(customerOrderCriteria).is(criteriaFiltersAre(Objects::isNull));
    }

    @Test
    void customerOrderCriteriaFluentMethodsCreatesFiltersTest() {
        var customerOrderCriteria = new CustomerOrderCriteria();

        setAllFilters(customerOrderCriteria);

        assertThat(customerOrderCriteria).is(criteriaFiltersAre(Objects::nonNull));
    }

    @Test
    void customerOrderCriteriaCopyCreatesNullFilterTest() {
        var customerOrderCriteria = new CustomerOrderCriteria();
        var copy = customerOrderCriteria.copy();

        assertThat(customerOrderCriteria).satisfies(
            criteria ->
                assertThat(criteria).is(
                    copyFiltersAre(copy, (a, b) -> (a == null || a instanceof Boolean) ? a == b : (a != b && a.equals(b)))
                ),
            criteria -> assertThat(criteria).isEqualTo(copy),
            criteria -> assertThat(criteria).hasSameHashCodeAs(copy)
        );

        assertThat(copy).satisfies(
            criteria -> assertThat(criteria).is(criteriaFiltersAre(Objects::isNull)),
            criteria -> assertThat(criteria).isEqualTo(customerOrderCriteria)
        );
    }

    @Test
    void customerOrderCriteriaCopyDuplicatesEveryExistingFilterTest() {
        var customerOrderCriteria = new CustomerOrderCriteria();
        setAllFilters(customerOrderCriteria);

        var copy = customerOrderCriteria.copy();

        assertThat(customerOrderCriteria).satisfies(
            criteria ->
                assertThat(criteria).is(
                    copyFiltersAre(copy, (a, b) -> (a == null || a instanceof Boolean) ? a == b : (a != b && a.equals(b)))
                ),
            criteria -> assertThat(criteria).isEqualTo(copy),
            criteria -> assertThat(criteria).hasSameHashCodeAs(copy)
        );

        assertThat(copy).satisfies(
            criteria -> assertThat(criteria).is(criteriaFiltersAre(Objects::nonNull)),
            criteria -> assertThat(criteria).isEqualTo(customerOrderCriteria)
        );
    }

    @Test
    void toStringVerifier() {
        var customerOrderCriteria = new CustomerOrderCriteria();

        assertThat(customerOrderCriteria).hasToString("CustomerOrderCriteria{}");
    }

    @Test
    void equals_sameInstanceNullAndWrongType() {
        var customerOrderCriteria = new CustomerOrderCriteria();
        assertThat(customerOrderCriteria.equals(customerOrderCriteria)).isTrue();
        assertThat(customerOrderCriteria.equals(null)).isFalse();
        assertThat(customerOrderCriteria.equals("CustomerOrderCriteria")).isFalse();
    }

    @ParameterizedTest
    @MethodSource("unequalCriteriaPairs")
    void equals_falseWhenAnyFilterDiffers(CustomerOrderCriteria left, CustomerOrderCriteria right) {
        assertThat(left).isNotEqualTo(right);
    }

    private static Stream<org.junit.jupiter.params.provider.Arguments> unequalCriteriaPairs() {
        return Stream.of(
            org.junit.jupiter.params.provider.Arguments.of(withOrderNumber("A"), withOrderNumber("B")),
            org.junit.jupiter.params.provider.Arguments.of(withUserLogin("u1"), withUserLogin("u2")),
            org.junit.jupiter.params.provider.Arguments.of(withStatus(OrderStatus.CONFIRMED), withStatus(OrderStatus.CANCELLED)),
            org.junit.jupiter.params.provider.Arguments.of(withDistinct(true), withDistinct(false))
        );
    }

    private static CustomerOrderCriteria withOrderNumber(String orderNumber) {
        var c = new CustomerOrderCriteria();
        c.orderNumber().setEquals(orderNumber);
        return c;
    }

    private static CustomerOrderCriteria withUserLogin(String login) {
        var c = new CustomerOrderCriteria();
        c.userLogin().setEquals(login);
        return c;
    }

    private static CustomerOrderCriteria withStatus(OrderStatus status) {
        var c = new CustomerOrderCriteria();
        c.status().setEquals(status);
        return c;
    }

    private static CustomerOrderCriteria withDistinct(Boolean distinct) {
        var c = new CustomerOrderCriteria();
        c.setDistinct(distinct);
        return c;
    }

    private static void setAllFilters(CustomerOrderCriteria customerOrderCriteria) {
        customerOrderCriteria.id();
        customerOrderCriteria.userLogin();
        customerOrderCriteria.orderNumber();
        customerOrderCriteria.status();
        customerOrderCriteria.paymentStatus();
        customerOrderCriteria.razorpayOrderId();
        customerOrderCriteria.razorpayPaymentId();
        customerOrderCriteria.razorpaySignature();
        customerOrderCriteria.subtotal();
        customerOrderCriteria.discount();
        customerOrderCriteria.shipping();
        customerOrderCriteria.tax();
        customerOrderCriteria.donation();
        customerOrderCriteria.total();
        customerOrderCriteria.couponCode();
        customerOrderCriteria.notes();
        customerOrderCriteria.itemsId();
        customerOrderCriteria.distinct();
    }

    private static Condition<CustomerOrderCriteria> criteriaFiltersAre(Function<Object, Boolean> condition) {
        return new Condition<>(
            criteria ->
                condition.apply(criteria.getId()) &&
                condition.apply(criteria.getUserLogin()) &&
                condition.apply(criteria.getOrderNumber()) &&
                condition.apply(criteria.getStatus()) &&
                condition.apply(criteria.getPaymentStatus()) &&
                condition.apply(criteria.getRazorpayOrderId()) &&
                condition.apply(criteria.getRazorpayPaymentId()) &&
                condition.apply(criteria.getRazorpaySignature()) &&
                condition.apply(criteria.getSubtotal()) &&
                condition.apply(criteria.getDiscount()) &&
                condition.apply(criteria.getShipping()) &&
                condition.apply(criteria.getTax()) &&
                condition.apply(criteria.getDonation()) &&
                condition.apply(criteria.getTotal()) &&
                condition.apply(criteria.getCouponCode()) &&
                condition.apply(criteria.getNotes()) &&
                condition.apply(criteria.getItemsId()) &&
                condition.apply(criteria.getDistinct()),
            "every filter matches"
        );
    }

    private static Condition<CustomerOrderCriteria> copyFiltersAre(
        CustomerOrderCriteria copy,
        BiFunction<Object, Object, Boolean> condition
    ) {
        return new Condition<>(
            criteria ->
                condition.apply(criteria.getId(), copy.getId()) &&
                condition.apply(criteria.getUserLogin(), copy.getUserLogin()) &&
                condition.apply(criteria.getOrderNumber(), copy.getOrderNumber()) &&
                condition.apply(criteria.getStatus(), copy.getStatus()) &&
                condition.apply(criteria.getPaymentStatus(), copy.getPaymentStatus()) &&
                condition.apply(criteria.getRazorpayOrderId(), copy.getRazorpayOrderId()) &&
                condition.apply(criteria.getRazorpayPaymentId(), copy.getRazorpayPaymentId()) &&
                condition.apply(criteria.getRazorpaySignature(), copy.getRazorpaySignature()) &&
                condition.apply(criteria.getSubtotal(), copy.getSubtotal()) &&
                condition.apply(criteria.getDiscount(), copy.getDiscount()) &&
                condition.apply(criteria.getShipping(), copy.getShipping()) &&
                condition.apply(criteria.getTax(), copy.getTax()) &&
                condition.apply(criteria.getDonation(), copy.getDonation()) &&
                condition.apply(criteria.getTotal(), copy.getTotal()) &&
                condition.apply(criteria.getCouponCode(), copy.getCouponCode()) &&
                condition.apply(criteria.getNotes(), copy.getNotes()) &&
                condition.apply(criteria.getItemsId(), copy.getItemsId()) &&
                condition.apply(criteria.getDistinct(), copy.getDistinct()),
            "every filter matches"
        );
    }
}
