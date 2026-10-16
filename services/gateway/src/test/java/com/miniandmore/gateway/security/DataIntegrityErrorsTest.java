package com.miniandmore.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

class DataIntegrityErrorsTest {

    @Test
    void isDuplicateUserId_detectsNestedCause() {
        var nested = new RuntimeException("duplicate key value violates unique constraint \"jhi_user_pkey\"");
        var wrapped = new DuplicateKeyException("executeMany", nested);
        assertThat(DataIntegrityErrors.isDuplicateUserId(wrapped)).isTrue();
    }

    @Test
    void isDuplicateUserEmail_detectsConstraintName() {
        var error = new RuntimeException("duplicate key value violates unique constraint \"ux_user_email\"");
        assertThat(DataIntegrityErrors.isDuplicateUserEmail(error)).isTrue();
    }

    @Test
    void isDuplicateUserLogin_detectsConstraintName() {
        var error = new RuntimeException("duplicate key value violates unique constraint \"ux_user_login\"");
        assertThat(DataIntegrityErrors.isDuplicateUserLogin(error)).isTrue();
    }

    @Test
    void isDuplicateUserIdentity_matchesAnyUserConstraint() {
        var error = new RuntimeException("duplicate key value violates unique constraint \"jhi_user_pkey\"");
        assertThat(DataIntegrityErrors.isDuplicateUserIdentity(error)).isTrue();
    }

    @Test
    void isDuplicateKey_ignoresBlankMessagesInCauseChain() {
        var blank = new RuntimeException("   ");
        var duplicate = new RuntimeException("duplicate key value violates unique constraint", blank);
        assertThat(DataIntegrityErrors.isDuplicateKey(duplicate)).isTrue();
    }

    @Test
    void isDuplicateKey_requiresMatchingConstraintWhenProvided() {
        var error = new RuntimeException("duplicate key value violates unique constraint \"other\"");
        assertThat(DataIntegrityErrors.isDuplicateKey(error, "ux_user_email")).isFalse();
    }
}
