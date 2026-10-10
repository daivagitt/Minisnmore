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

    @Test
    void equals_sameInstanceNullAndWrongType() {
        var productCriteria = new ProductCriteria();
        assertThat(productCriteria.equals(productCriteria)).isTrue();
        assertThat(productCriteria.equals(null)).isFalse();
        assertThat(productCriteria.equals("ProductCriteria")).isFalse();
    }

    @ParameterizedTest
    @MethodSource("unequalCriteriaPairs")
    void equals_falseWhenAnyFilterDiffers(ProductCriteria left, ProductCriteria right) {
        assertThat(left).isNotEqualTo(right);
        assertThat(left.hashCode()).isNotEqualTo(right.hashCode());
    }

    private static Stream<org.junit.jupiter.params.provider.Arguments> unequalCriteriaPairs() {
        return Stream.of(
            pair(withId(1L), withId(2L)),
            pair(withSlug("a"), withSlug("b")),
            pair(withName("n1"), withName("n2")),
            pair(withSku("s1"), withSku("s2")),
            pair(withFabric("f1"), withFabric("f2")),
            pair(withFeatured(true), withFeatured(false)),
            pair(withDistinct(true), withDistinct(false))
        );
    }

    private static org.junit.jupiter.params.provider.Arguments pair(ProductCriteria left, ProductCriteria right) {
        return org.junit.jupiter.params.provider.Arguments.of(left, right);
    }

    private static ProductCriteria withId(Long id) {
        var c = new ProductCriteria();
        c.id().setEquals(id);
        return c;
    }

    private static ProductCriteria withSlug(String slug) {
        var c = new ProductCriteria();
        c.slug().setEquals(slug);
        return c;
    }

    private static ProductCriteria withName(String name) {
        var c = new ProductCriteria();
        c.name().setEquals(name);
        return c;
    }

    private static ProductCriteria withSku(String sku) {
        var c = new ProductCriteria();
        c.sku().setEquals(sku);
        return c;
    }

    private static ProductCriteria withFabric(String fabric) {
        var c = new ProductCriteria();
        c.fabricType().setEquals(fabric);
        return c;
    }

    private static ProductCriteria withFeatured(Boolean featured) {
        var c = new ProductCriteria();
        c.featured().setEquals(featured);
        return c;
    }

    private static ProductCriteria withDistinct(Boolean distinct) {
        var c = new ProductCriteria();
        c.setDistinct(distinct);
        return c;
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
