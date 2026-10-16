package com.miniandmore.gateway.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.data.relational.core.sql.SqlIdentifier;
import reactor.test.StepVerifier;

class UserCallbackTest {

    private final UserCallback callback = new UserCallback();

    @Test
    void onAfterConvert_marksEntityPersisted() {
        User user = new User();
        user.setLogin("user1");

        StepVerifier.create(callback.onAfterConvert(user, SqlIdentifier.unquoted("jhi_user")))
            .assertNext(result -> assertThat(result.isNew()).isFalse())
            .verifyComplete();
    }

    @Test
    void onAfterSave_marksEntityPersisted() {
        User user = new User();
        user.setLogin("user1");

        StepVerifier.create(callback.onAfterSave(user, null, SqlIdentifier.unquoted("jhi_user")))
            .assertNext(result -> assertThat(result.isNew()).isFalse())
            .verifyComplete();
    }
}
