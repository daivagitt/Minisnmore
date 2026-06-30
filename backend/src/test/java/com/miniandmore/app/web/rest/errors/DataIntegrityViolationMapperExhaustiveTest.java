package com.miniandmore.app.web.rest.errors;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.dao.DataIntegrityViolationException;

class DataIntegrityViolationMapperExhaustiveTest {

    static Stream<Arguments> knownForeignKeys() {
        return Stream.of(
            Arguments.of("fk_product__category_id", "category"),
            Arguments.of("fk_order_item__order_id", "line items"),
            Arguments.of("fk_customer_order__user_id", "orders"),
            Arguments.of("fk_customer_profile__user_id", "profile"),
            Arguments.of("fk_user_id", "linked"),
            Arguments.of("fk_authority_name", "role")
        );
    }

    static Stream<Arguments> knownUniqueKeys() {
        return Stream.of(
            Arguments.of("ux_category__slug", "slug"),
            Arguments.of("ux_product__slug", "slug"),
            Arguments.of("ux_product__sku", "SKU"),
            Arguments.of("ux_blog_post__slug", "slug"),
            Arguments.of("ux_coupon__code", "coupon"),
            Arguments.of("ux_customer_order__order_number", "order number"),
            Arguments.of("ux_home_section__section_key", "home section"),
            Arguments.of("ux_user_login", "login"),
            Arguments.of("ux_user_email", "email")
        );
    }

    @ParameterizedTest
    @MethodSource("knownForeignKeys")
    void resolve_knownForeignKey(String constraint, String detailFragment) {
        var issue = DataIntegrityViolationMapper.resolve(new DataIntegrityViolationException("violates " + constraint));
        assertThat(issue).isPresent();
        assertThat(issue.orElseThrow().detail()).containsIgnoringCase(detailFragment);
    }

    @ParameterizedTest
    @MethodSource("knownUniqueKeys")
    void resolve_knownUniqueKey(String constraint, String detailFragment) {
        var issue = DataIntegrityViolationMapper.resolve(new DataIntegrityViolationException("duplicate key " + constraint));
        assertThat(issue).isPresent();
        assertThat(issue.orElseThrow().detail()).containsIgnoringCase(detailFragment);
    }
}
