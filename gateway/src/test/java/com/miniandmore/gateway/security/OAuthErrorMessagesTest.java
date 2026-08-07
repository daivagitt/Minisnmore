package com.miniandmore.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OAuthErrorMessagesTest {

    @Test
    void friendly_mapsMissingEmail() {
        assertThat(OAuthErrorMessages.friendly(new IllegalArgumentException("OAuth provider did not return an email address")))
            .contains("did not share an email");
    }

    @Test
    void friendly_mapsDuplicateAndNonUnique() {
        assertThat(OAuthErrorMessages.friendly(new RuntimeException("duplicate key value violates unique constraint \"ux_user_email\"")))
            .contains("already exists");
        assertThat(OAuthErrorMessages.friendly(new RuntimeException("Query did not return a unique result: 2")))
            .contains("more than one matching account");
    }

    @Test
    void friendly_hidesTechnicalSqlAndDefaults() {
        assertThat(OAuthErrorMessages.friendly(new RuntimeException("Failure during data access; SQL [INSERT INTO jhi_user]")))
            .contains("server error");
        assertThat(OAuthErrorMessages.friendly(null)).contains("try again");
        assertThat(OAuthErrorMessages.friendly(new RuntimeException(""))).contains("try again");
    }

    @Test
    void friendly_keepsShortHumanMessages() {
        assertThat(OAuthErrorMessages.friendly(new RuntimeException("Please allow email access")))
            .isEqualTo("Please allow email access");
    }
}
