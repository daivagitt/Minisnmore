package com.miniandmore.app.web.rest.errors;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

class DataIntegrityViolationMapperTest {

    @Test
    void resolve_nullThrowable_returnsEmpty() {
        assertThat(DataIntegrityViolationMapper.resolve(null)).isEmpty();
    }

    @Test
    void resolve_foreignKeyConstraint_returnsReferentialIssue() {
        var ex = new DataIntegrityViolationException("violates foreign key constraint fk_product__category_id");

        var issue = DataIntegrityViolationMapper.resolve(ex);

        assertThat(issue).isPresent();
        assertThat(issue.orElseThrow().title()).isEqualTo("Linked records exist");
        assertThat(issue.orElseThrow().detail()).contains("category");
        assertThat(issue.orElseThrow().status()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(issue.orElseThrow().messageKey()).isEqualTo(ErrorConstants.ERR_REFERENTIAL_INTEGRITY);
    }

    @Test
    void resolve_uniqueConstraint_returnsDuplicateIssue() {
        var ex = new DataIntegrityViolationException("duplicate key value violates unique constraint ux_product__slug");

        var issue = DataIntegrityViolationMapper.resolve(ex);

        assertThat(issue).isPresent();
        assertThat(issue.orElseThrow().title()).isEqualTo("Duplicate value");
        assertThat(issue.orElseThrow().detail()).contains("slug");
        assertThat(issue.orElseThrow().messageKey()).isEqualTo(ErrorConstants.ERR_DUPLICATE_KEY);
    }

    @Test
    void resolve_notNullViolation_returnsValidationIssue() {
        var ex = new DataIntegrityViolationException("null value in column violates not-null constraint 23502");

        var issue = DataIntegrityViolationMapper.resolve(ex);

        assertThat(issue).isPresent();
        assertThat(issue.orElseThrow().title()).isEqualTo("Invalid data");
        assertThat(issue.orElseThrow().messageKey()).isEqualTo(ErrorConstants.ERR_VALIDATION);
    }

    @Test
    void resolve_valueTooLong_homeSection_returnsSpecificMessage() {
        var ex = new DataIntegrityViolationException("value too long for type character varying(180) on home_section");

        var issue = DataIntegrityViolationMapper.resolve(ex);

        assertThat(issue).isPresent();
        assertThat(issue.orElseThrow().detail()).contains("Title");
    }

    @Test
    void resolve_valueTooLong_generic_returnsFallback() {
        var ex = new DataIntegrityViolationException("value too long for type varchar(50)");

        var issue = DataIntegrityViolationMapper.resolve(ex);

        assertThat(issue).isPresent();
        assertThat(issue.orElseThrow().detail()).contains("too long");
    }

    @Test
    void applies_dataIntegrityException_returnsTrue() {
        assertThat(DataIntegrityViolationMapper.applies(new DataIntegrityViolationException("duplicate key"))).isTrue();
    }

    @Test
    void resolve_knownForeignKeyConstraint_returnsSpecificMessage() {
        var ex = new DataIntegrityViolationException("violates foreign key constraint fk_order_item__order_id");

        var issue = DataIntegrityViolationMapper.resolve(ex);

        assertThat(issue).isPresent();
        assertThat(issue.orElseThrow().detail()).contains("line items");
    }

    @Test
    void resolve_knownUniqueConstraint_returnsSpecificMessage() {
        var ex = new DataIntegrityViolationException("duplicate key value violates unique constraint ux_coupon__code");

        var issue = DataIntegrityViolationMapper.resolve(ex);

        assertThat(issue).isPresent();
        assertThat(issue.orElseThrow().detail()).contains("coupon code");
    }

    @Test
    void resolve_referentialIntegrityCode_returnsReferentialIssue() {
        var ex = new DataIntegrityViolationException("ERROR: insert or update on table violates foreign key constraint 23503");

        var issue = DataIntegrityViolationMapper.resolve(ex);

        assertThat(issue).isPresent();
        assertThat(issue.orElseThrow().title()).isEqualTo("Linked records exist");
    }

    @Test
    void applies_knownIntegrityIssue_returnsTrue() {
        assertThat(
            DataIntegrityViolationMapper.applies(new DataIntegrityViolationException("duplicate key ux_user_email"))
        ).isTrue();
    }
}
