package com.miniandmore.app.web.rest.errors;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class BadRequestAlertExceptionTest {

    @Test
    void constructor_setsEntityNameAndErrorKey() {
        BadRequestAlertException ex = new BadRequestAlertException("Invalid product", "product", "idexists");

        assertThat(ex.getEntityName()).isEqualTo("product");
        assertThat(ex.getErrorKey()).isEqualTo("idexists");
        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getProblemDetailWithCause().getProperties()).containsEntry("params", "product");
    }

    @Test
    void typedConstructor_usesCustomType() {
        BadRequestAlertException ex = new BadRequestAlertException(
            ErrorConstants.INVALID_PASSWORD_TYPE,
            "Invalid password",
            "user",
            "invalidpassword"
        );

        assertThat(ex.getProblemDetailWithCause().getType()).isEqualTo(ErrorConstants.INVALID_PASSWORD_TYPE);
    }
}
