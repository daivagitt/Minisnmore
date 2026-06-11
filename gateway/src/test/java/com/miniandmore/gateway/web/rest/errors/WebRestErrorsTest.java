package com.miniandmore.gateway.web.rest.errors;

import static org.assertj.core.api.Assertions.assertThat;

import com.miniandmore.gateway.service.EmailAlreadyUsedException;
import com.miniandmore.gateway.service.InvalidPasswordException;
import com.miniandmore.gateway.service.UsernameAlreadyUsedException;
import org.junit.jupiter.api.Test;

class WebRestErrorsTest {

    @Test
    void badRequestAlertException_exposesProperties() {
        BadRequestAlertException ex = new BadRequestAlertException("msg", "entity", "errorKey");

        assertThat(ex.getEntityName()).isEqualTo("entity");
        assertThat(ex.getErrorKey()).isEqualTo("errorKey");
        assertThat(ex.getProblemDetailWithCause().getTitle()).isEqualTo("msg");
    }

    @Test
    void fieldErrorVm_holdsValues() {
        FieldErrorVM vm = new FieldErrorVM("dto", "field", "message");

        assertThat(vm.getObjectName()).isEqualTo("dto");
        assertThat(vm.getField()).isEqualTo("field");
        assertThat(vm.getMessage()).isEqualTo("message");
    }

    @Test
    void errorConstants_areDefined() {
        assertThat(ErrorConstants.ERR_VALIDATION).isNotBlank();
        assertThat(ErrorConstants.DEFAULT_TYPE).isNotNull();
    }

    @Test
    void serviceExceptions_mapToWebExceptions() {
        assertThat(new LoginAlreadyUsedException()).isNotNull();
        assertThat(new com.miniandmore.gateway.web.rest.errors.EmailAlreadyUsedException()).isNotNull();
        assertThat(new com.miniandmore.gateway.web.rest.errors.InvalidPasswordException()).isNotNull();
        assertThat(new UsernameAlreadyUsedException()).isNotNull();
        assertThat(new EmailAlreadyUsedException()).isNotNull();
        assertThat(new InvalidPasswordException()).isNotNull();
    }
}
