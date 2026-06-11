package com.miniandmore.app.service.criteria;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.assertj.core.api.Condition;
import org.junit.jupiter.api.Test;

class ProductCriteriaTest {

    @Test
    void newProductCriteriaHasAllFiltersNullTest() {
        var productCriteria = new ProductCriteria();
        assertThat(productCriteria).is(criteriaFiltersAre(Objects::isNull));
    }

    @Test
    void productCriteriaFluentMethodsCreatesFiltersTest() {
        var productCriteria = new ProductCriteria();

        setAllFilters(productCriteria);

        assertThat(productCriteria).is(criteriaFiltersAre(Objects::nonNull));
    }

    @Test
    void productCriteriaCopyCreatesNullFilterTest() {
        var productCriteria = new ProductCriteria();
        var copy = productCriteria.copy();

        assertThat(productCriteria).satisfies(
            criteria ->
                assertThat(criteria).is(
                    copyFiltersAre(copy, (a, b) -> (a == null || a instanceof Boolean) ? a == b : (a != b && a.equals(b)))
                ),
            criteria -> assertThat(criteria).isEqualTo(copy),
            criteria -> assertThat(criteria).hasSameHashCodeAs(copy)
        );

        assertThat(copy).satisfies(
            criteria -> assertThat(criteria).is(criteriaFiltersAre(Objects::isNull)),
            criteria -> assertThat(criteria).isEqualTo(productCriteria)
        );
    }

    @Test
    void productCriteriaCopyDuplicatesEveryExistingFilterTest() {
        var productCriteria = new ProductCriteria();
        setAllFilters(productCriteria);

        var copy = productCriteria.copy();

        assertThat(productCriteria).satisfies(
            criteria ->
                assertThat(criteria).is(
                    copyFiltersAre(copy, (a, b) -> (a == null || a instanceof Boolean) ? a == b : (a != b && a.equals(b)))
                ),
            criteria -> assertThat(criteria).isEqualTo(copy),
            criteria -> assertThat(criteria).hasSameHashCodeAs(copy)
        );

        assertThat(copy).satisfies(
            criteria -> assertThat(criteria).is(criteriaFiltersAre(Objects::nonNull)),
            criteria -> assertThat(criteria).isEqualTo(productCriteria)
        );
    }

    @Test
    void toStringVerifier() {
        var productCriteria = new ProductCriteria();

        assertThat(productCriteria).hasToString("ProductCriteria{}");
    }

    private static void setAllFilters(ProductCriteria productCriteria) {
        productCriteria.id();
        productCriteria.slug();
        productCriteria.name();
        productCriteria.sku();
        productCriteria.fabricType();
        productCriteria.price();
        productCriteria.compareAtPrice();
        productCriteria.currency();
        productCriteria.ageMinMonths();
        productCriteria.ageMaxMonths();
        productCriteria.badge();
        productCriteria.stock();
        productCriteria.sortOrder();
        productCriteria.featured();
        productCriteria.trending();
        productCriteria.newArrival();
        productCriteria.bestSeller();
        productCriteria.active();
        productCriteria.categoryId();
        productCriteria.distinct();
    }

    private static Condition<ProductCriteria> criteriaFiltersAre(Function<Object, Boolean> condition) {
        return new Condition<>(
            criteria ->
                condition.apply(criteria.getId()) &&
                condition.apply(criteria.getSlug()) &&
                condition.apply(criteria.getName()) &&
                condition.apply(criteria.getSku()) &&
                condition.apply(criteria.getFabricType()) &&
                condition.apply(criteria.getPrice()) &&
                condition.apply(criteria.getCompareAtPrice()) &&
                condition.apply(criteria.getCurrency()) &&
                condition.apply(criteria.getAgeMinMonths()) &&
                condition.apply(criteria.getAgeMaxMonths()) &&
                condition.apply(criteria.getBadge()) &&
                condition.apply(criteria.getStock()) &&
                condition.apply(criteria.getSortOrder()) &&
                condition.apply(criteria.getFeatured()) &&
                condition.apply(criteria.getTrending()) &&
                condition.apply(criteria.getNewArrival()) &&
                condition.apply(criteria.getBestSeller()) &&
                condition.apply(criteria.getActive()) &&
                condition.apply(criteria.getCategoryId()) &&
                condition.apply(criteria.getDistinct()),
            "every filter matches"
        );
    }

    private static Condition<ProductCriteria> copyFiltersAre(ProductCriteria copy, BiFunction<Object, Object, Boolean> condition) {
        return new Condition<>(
            criteria ->
                condition.apply(criteria.getId(), copy.getId()) &&
                condition.apply(criteria.getSlug(), copy.getSlug()) &&
                condition.apply(criteria.getName(), copy.getName()) &&
                condition.apply(criteria.getSku(), copy.getSku()) &&
                condition.apply(criteria.getFabricType(), copy.getFabricType()) &&
                condition.apply(criteria.getPrice(), copy.getPrice()) &&
                condition.apply(criteria.getCompareAtPrice(), copy.getCompareAtPrice()) &&
                condition.apply(criteria.getCurrency(), copy.getCurrency()) &&
                condition.apply(criteria.getAgeMinMonths(), copy.getAgeMinMonths()) &&
                condition.apply(criteria.getAgeMaxMonths(), copy.getAgeMaxMonths()) &&
                condition.apply(criteria.getBadge(), copy.getBadge()) &&
                condition.apply(criteria.getStock(), copy.getStock()) &&
                condition.apply(criteria.getSortOrder(), copy.getSortOrder()) &&
                condition.apply(criteria.getFeatured(), copy.getFeatured()) &&
                condition.apply(criteria.getTrending(), copy.getTrending()) &&
                condition.apply(criteria.getNewArrival(), copy.getNewArrival()) &&
                condition.apply(criteria.getBestSeller(), copy.getBestSeller()) &&
                condition.apply(criteria.getActive(), copy.getActive()) &&
                condition.apply(criteria.getCategoryId(), copy.getCategoryId()) &&
                condition.apply(criteria.getDistinct(), copy.getDistinct()),
            "every filter matches"
        );
    }
}
