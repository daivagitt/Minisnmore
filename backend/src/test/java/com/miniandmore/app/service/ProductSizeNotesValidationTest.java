package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class ProductSizeNotesValidationTest {

    @Test
    void allowsBlankNullAndValidNotes() {
        assertThatCode(() -> ProductSizeNotesValidation.validate(null)).doesNotThrowAnyException();
        assertThatCode(() -> ProductSizeNotesValidation.validate("")).doesNotThrowAnyException();
        assertThatCode(() -> ProductSizeNotesValidation.validate("{}")).doesNotThrowAnyException();
        assertThatCode(
            () -> ProductSizeNotesValidation.validate("{\"0-3m\":\"Without grippers\",\"6-12m\":\"This size has grippers\"}")
        ).doesNotThrowAnyException();
    }

    @Test
    void rejectsMalformedNonTextAndOverlongNotes() {
        assertThatThrownBy(() -> ProductSizeNotesValidation.validate("not json")).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> ProductSizeNotesValidation.validate("[\"gripper\"]")).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> ProductSizeNotesValidation.validate("{\"0-3m\":true}")).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> ProductSizeNotesValidation.validate("{\"\":\"note\"}")).isInstanceOf(ResponseStatusException.class);
        String tooLong = "x".repeat(ProductSizeNotesValidation.MAX_NOTE_LEN + 1);
        assertThatThrownBy(() -> ProductSizeNotesValidation.validate("{\"6-12m\":\"" + tooLong + "\"}"))
            .isInstanceOf(ResponseStatusException.class);
    }
}
