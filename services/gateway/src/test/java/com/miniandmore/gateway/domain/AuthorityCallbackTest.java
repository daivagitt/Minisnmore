package com.miniandmore.gateway.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.data.relational.core.sql.SqlIdentifier;
import reactor.test.StepVerifier;

class AuthorityCallbackTest {

    private final AuthorityCallback callback = new AuthorityCallback();

    @Test
    void onAfterConvert_marksEntityPersisted() {
        Authority authority = new Authority();
        authority.setName("ROLE_USER");

        StepVerifier.create(callback.onAfterConvert(authority, SqlIdentifier.unquoted("jhi_authority")))
            .assertNext(result -> assertThat(result.isNew()).isFalse())
            .verifyComplete();
    }

    @Test
    void onAfterSave_marksEntityPersisted() {
        Authority authority = new Authority();
        authority.setName("ROLE_USER");

        StepVerifier.create(callback.onAfterSave(authority, null, SqlIdentifier.unquoted("jhi_authority")))
            .assertNext(result -> assertThat(result.isNew()).isFalse())
            .verifyComplete();
    }
}
