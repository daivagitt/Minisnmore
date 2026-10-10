package com.miniandmore.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DataIntegrityErrorsIdentityMatrixTest {

    @Test
    void isDuplicateUserIdentity_coversIdEmailLoginAndNonMatches() {
        assertThat(DataIntegrityErrors.isDuplicateUserIdentity(null)).isFalse();
        assertThat(DataIntegrityErrors.isDuplicateUserIdentity(new RuntimeException((String) null))).isFalse();
        assertThat(DataIntegrityErrors.isDuplicateUserIdentity(new RuntimeException("   "))).isFalse();
        assertThat(DataIntegrityErrors.isDuplicateUserIdentity(new RuntimeException("other error"))).isFalse();
        assertThat(DataIntegrityErrors.isDuplicateUserIdentity(new RuntimeException("duplicate key value ux_other"))).isFalse();
        assertThat(DataIntegrityErrors.isDuplicateUserIdentity(new RuntimeException("duplicate key value jhi_user_pkey"))).isTrue();
        assertThat(DataIntegrityErrors.isDuplicateUserIdentity(new RuntimeException("duplicate key value ux_user_email"))).isTrue();
        assertThat(DataIntegrityErrors.isDuplicateUserIdentity(new RuntimeException("duplicate key value ux_user_login"))).isTrue();
        assertThat(DataIntegrityErrors.isDuplicateKey(new RuntimeException("duplicate key value"), new String[0])).isTrue();
        RuntimeException nested = new RuntimeException("wrapper", new RuntimeException("duplicate key value ux_user_email"));
        assertThat(DataIntegrityErrors.isDuplicateUserEmail(nested)).isTrue();
    }
}
