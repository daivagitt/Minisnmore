package com.miniandmore.app.web.rest.errors;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class DataIntegrityViolationMapperAdditionalTest {

    @Test
    void resolve_genericReferentialViolation_usesFallbackMessage() {
        var issue = DataIntegrityViolationMapper.resolve(
            new DataIntegrityViolationException("violates foreign key constraint", new SQLException("23503"))
        );

        assertThat(issue).isPresent();
        assertThat(issue.orElseThrow().title()).isEqualTo("Linked records exist");
    }

    @Test
    void resolve_homeSectionTooLong_usesSpecificDetail() {
        var issue = DataIntegrityViolationMapper.resolve(
            new RuntimeException("ERROR: value too long for type character varying(180) on home_section")
        );

        assertThat(issue).isPresent();
        assertThat(issue.orElseThrow().detail()).contains("Title (max 180 characters)");
    }

    @Test
    void resolve_unknownConstraint_usesGenericDuplicate() {
        var issue = DataIntegrityViolationMapper.resolve(new RuntimeException("duplicate key value violates unique constraint \"unknown_idx\""));

        assertThat(issue).isPresent();
        assertThat(issue.orElseThrow().title()).isEqualTo("Duplicate value");
    }

    @Test
    void applies_returnsTrueForDataIntegrityViolationException() {
        assertThat(DataIntegrityViolationMapper.applies(new DataIntegrityViolationException("dup"))).isTrue();
    }

    @Test
    void resolve_constraintNameViaRegex_usesKnownMessage() {
        var issue = DataIntegrityViolationMapper.resolve(
            new RuntimeException("ERROR: duplicate key value violates unique constraint \"ux_product__slug\"")
        );

        assertThat(issue).isPresent();
        assertThat(issue.orElseThrow().detail()).contains("URL slug");
    }

    @Test
    void resolve_nullThrowable_returnsEmpty() {
        assertThat(DataIntegrityViolationMapper.resolve(null)).isEmpty();
    }
}
