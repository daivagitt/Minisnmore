package com.miniandmore.app.web.rest.errors;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

class DataIntegrityViolationMapperBranchCoverageTest {

    @ParameterizedTest
    @ValueSource(
        strings = {
            "string data, right truncated for column title",
            "data too long for column name",
            "length must be between 0 and 50"
        }
    )
    void resolve_valueTooLongPatterns_useValidationTitle(String message) {
        var issue = DataIntegrityViolationMapper.resolve(new RuntimeException(message));
        assertThat(issue).isPresent();
        assertThat(issue.orElseThrow().title()).isEqualTo("Invalid data");
        assertThat(issue.orElseThrow().status()).isEqualTo(HttpStatus.CONFLICT);
    }

    @ParameterizedTest
    @MethodSource("referentialPhrases")
    void resolve_referentialPhrases_mapToLinkedRecordsTitle(String message) {
        var issue = DataIntegrityViolationMapper.resolve(new RuntimeException(message));
        assertThat(issue).isPresent();
        assertThat(issue.orElseThrow().title()).isEqualTo("Linked records exist");
    }

    @ParameterizedTest
    @MethodSource("duplicatePhrases")
    void resolve_duplicatePhrases_mapToDuplicateTitle(String message) {
        var issue = DataIntegrityViolationMapper.resolve(new RuntimeException(message));
        assertThat(issue).isPresent();
        assertThat(issue.orElseThrow().title()).isEqualTo("Duplicate value");
    }

    @ParameterizedTest
    @MethodSource("notNullPhrases")
    void resolve_notNullPhrases_mapToInvalidData(String message) {
        var issue = DataIntegrityViolationMapper.resolve(new RuntimeException(message));
        assertThat(issue).isPresent();
        assertThat(issue.orElseThrow().detail()).contains("required field");
    }

    @Test
    void resolve_partialConstraintMatch_usesKnownUniqueMessage() {
        var issue = DataIntegrityViolationMapper.resolve(
            new RuntimeException("duplicate key value violates unique constraint \"prefix_ux_product__sku_suffix\"")
        );
        assertThat(issue).isPresent();
        assertThat(issue.orElseThrow().detail()).contains("SKU");
    }

    @ParameterizedTest
    @MethodSource("knownUniqueConstraints")
    void resolve_knownUniqueConstraints_useSpecificMessages(String constraint, String fragment) {
        var issue = DataIntegrityViolationMapper.resolve(new RuntimeException("duplicate key " + constraint));
        assertThat(issue).isPresent();
        assertThat(issue.orElseThrow().detail()).containsIgnoringCase(fragment);
    }

    private static Stream<Arguments> knownUniqueConstraints() {
        return Stream.of(
            Arguments.of("ux_product__zoho_item_id", "Ledgerly"),
            Arguments.of("ux_customer_order__ledgerly_sales_order_id", "sales order"),
            Arguments.of("ux_page_visibility__page_key", "page visibility"),
            Arguments.of("ux_policy__slug", "policy")
        );
    }

    @Test
    void applies_trueWhenResolvableNonDataIntegrityThrowable() {
        assertThat(DataIntegrityViolationMapper.applies(new RuntimeException("duplicate entry for key"))).isTrue();
    }

    @Test
    void resolve_blankMessageChain_returnsEmpty() {
        assertThat(DataIntegrityViolationMapper.resolve(new RuntimeException("   "))).isEmpty();
    }

    private static Stream<String> referentialPhrases() {
        return Stream.of(
            "violates foreign key constraint",
            "referential integrity violation",
            "still referenced from table",
            "delete restrict on fk",
            "SQLSTATE 23503"
        );
    }

    private static Stream<String> duplicatePhrases() {
        return Stream.of(
            "unique index ux_foo detected",
            "duplicate key value",
            "already exists in database",
            "SQLSTATE 23505",
            "duplicate entry"
        );
    }

    private static Stream<String> notNullPhrases() {
        return Stream.of(
            "SQLSTATE 23502",
            "not-null constraint",
            "null value in column email",
            "cannot be null",
            "may not be null"
        );
    }
}
