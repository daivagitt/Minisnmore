package com.miniandmore.app.web.rest.errors;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

class DataIntegrityViolationExceptionHandlerTest {

    private final DataIntegrityViolationExceptionHandler handler = new DataIntegrityViolationExceptionHandler();

    @Test
    void handle_duplicateSlug_returnsConflictWithFriendlyDetail() {
        var ex = new DataIntegrityViolationException(
            "duplicate key value violates unique constraint ux_product__slug Detail: Key (slug)=(kedar) already exists."
        );

        var response = handler.handleDataIntegrityViolation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        ProblemDetail body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getDetail()).contains("slug");
        assertThat(body.getTitle()).isEqualTo("Duplicate value");
    }
}
