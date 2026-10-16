package com.miniandmore.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

class OAuthErrorMessagesMapMessageMatrixTest {

    @ParameterizedTest
    @ValueSource(strings = { "duplicate key value", "ux_user_email violated", "UX_USER_LOGIN", "jhi_user_pkey" })
    void mapMessage_duplicateAccount(String message) {
        assertThat((String) ReflectionTestUtils.invokeMethod(OAuthErrorMessages.class, "mapMessage", message)).contains("already exists");
    }

    @ParameterizedTest
    @ValueSource(strings = { "Unique result was not", "IncorrectResultSizeDataAccess", "non unique query" })
    void mapMessage_duplicateResult(String message) {
        assertThat((String) ReflectionTestUtils.invokeMethod(OAuthErrorMessages.class, "mapMessage", message)).contains("more than one");
    }

    @Test
    void mapMessage_emailMissingTooLongNullAndJdbc() {
        assertThat(
            (String) ReflectionTestUtils.invokeMethod(OAuthErrorMessages.class, "mapMessage", "OAuth provider did not return an email")
        ).contains("email address");
        assertThat((String) ReflectionTestUtils.invokeMethod(OAuthErrorMessages.class, "mapMessage", "value too long for character varying(50)"))
            .contains("too long");
        assertThat((String) ReflectionTestUtils.invokeMethod(OAuthErrorMessages.class, "mapMessage", "null value in column created_by"))
            .contains("could not create");
        assertThat((String) ReflectionTestUtils.invokeMethod(OAuthErrorMessages.class, "mapMessage", "R2DBC data access jdbc psql"))
            .contains("server error");
        assertThat((String) ReflectionTestUtils.invokeMethod(OAuthErrorMessages.class, "mapMessage", "Please try again tomorrow"))
            .isEqualTo("Please try again tomorrow");
        assertThat((String) ReflectionTestUtils.invokeMethod(OAuthErrorMessages.class, "mapMessage", "x".repeat(200))).isNull();
        assertThat((String) ReflectionTestUtils.invokeMethod(OAuthErrorMessages.class, "mapMessage", "sqlstate 23505 exception")).isNull();
    }
}
