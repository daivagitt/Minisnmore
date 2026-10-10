package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class ProductSizeNotesValidationResidualTest {

    @Test
    void allowsJsonNullValuesAndTooManyKeysRejected() {
        assertThatCode(() -> ProductSizeNotesValidation.validate("{\"0-3m\":null}")).doesNotThrowAnyException();
        StringBuilder json = new StringBuilder("{");
        for (int i = 0; i <= ProductSizeNotesValidation.MAX_NOTES; i++) {
            if (i > 0) {
                json.append(',');
            }
            json.append("\"s").append(i).append("\":\"ok\"");
        }
        json.append('}');
        assertThatThrownBy(() -> ProductSizeNotesValidation.validate(json.toString())).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void rejectsWhitespaceOnlyKeyAndNumericNote() {
        assertThatThrownBy(() -> ProductSizeNotesValidation.validate("{\"   \":\"note\"}")).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> ProductSizeNotesValidation.validate("{\"0-3m\":12}")).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void allowsNoteAtMaxLength() {
        String note = "x".repeat(ProductSizeNotesValidation.MAX_NOTE_LEN);
        assertThatCode(() -> ProductSizeNotesValidation.validate("{\"6-12m\":\"" + note + "\"}")).doesNotThrowAnyException();
    }
}
