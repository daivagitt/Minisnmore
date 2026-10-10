package com.miniandmore.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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

    @Test
    void friendly_mapsLoginDuplicateAndEmailLength() {
        assertThat(OAuthErrorMessages.friendly(new RuntimeException("violates ux_user_login")))
            .contains("already exists");
        assertThat(OAuthErrorMessages.friendly(new RuntimeException("value too long for character varying(50)")))
            .contains("too long");
        assertThat(OAuthErrorMessages.friendly(new RuntimeException("null value in column created_by")))
            .contains("could not create your account");
    }

    @Test
    void friendly_mapsNonUniqueAndTechnicalStackMessages() {
        assertThat(OAuthErrorMessages.friendly(new RuntimeException("IncorrectResultSizeDataAccessException: non unique")))
            .contains("more than one matching account");
        assertThat(OAuthErrorMessages.friendly(new RuntimeException("java.lang.IllegalStateException: broken")))
            .contains("server error");
        assertThat(OAuthErrorMessages.friendly(new RuntimeException("OAuth provider did not return an email address")))
            .contains("did not share an email");
    }

    @Test
    void friendly_mapsAdditionalDuplicateAndTechnicalPatterns() {
        assertThat(OAuthErrorMessages.friendly(new RuntimeException("violates jhi_user_pkey constraint")))
            .contains("already exists");
        assertThat(OAuthErrorMessages.friendly(new RuntimeException("r2dbc connection failed"))).contains("server error");
        assertThat(OAuthErrorMessages.friendly(new RuntimeException("cannot be cast to java.lang.String"))).contains("server error");
        assertThat(OAuthErrorMessages.friendly(new RuntimeException("stacktrace follows"))).contains("server error");
        assertThat(OAuthErrorMessages.friendly(new RuntimeException("psql timeout while reading"))).contains("server error");
    }

    @Test
    void friendly_walksCauseChainAndSkipsBlankMessages() {
        Throwable root = new RuntimeException("   ");
        Throwable wrapped = new RuntimeException("Query did not return a unique result: 2", root);
        assertThat(OAuthErrorMessages.friendly(wrapped)).contains("more than one matching account");
    }

    @Test
    void friendly_mapsSpringFrameworkTechnicalMessages() {
        assertThat(OAuthErrorMessages.friendly(new RuntimeException("failure at org.springframework.dao.DataAccessException")))
            .contains("server error");
    }

    @Test
    void friendly_multilineMessageFallsBackToGenericSignInFailure() {
        assertThat(OAuthErrorMessages.friendly(new RuntimeException("line1\nline2")))
            .contains("Social sign-in failed");
    }

    @Test
    void friendly_hidesLongTechnicalMessages() {
        String longTechnical =
            "org.springframework.dao.DataAccessException: something failed at com.example.Foo.bar(Foo.java:12) with a very long technical explanation that should not be shown to end users because it contains exception details";
        assertThat(OAuthErrorMessages.friendly(new RuntimeException(longTechnical))).contains("server error");
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
            "Failure during executemany batch",
            "broken jdbc connection",
            "violates not-null constraint on email",
            "did not return an email from provider"
        }
    )
    void friendly_branchMatrix_mapsKnownFailurePatterns(String message) {
        String friendly = OAuthErrorMessages.friendly(new RuntimeException(message));
        assertThat(friendly).isNotBlank();
        assertThat(friendly).doesNotContain("executemany");
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
            "broken jdbc connection while saving",
            "Wrapped java.lang.RuntimeException: state",
            "psql timeout during read",
            "failure during data access while saving user"
        }
    )
    void friendly_branchMatrix_hidesTechnicalSurfacePatterns(String message) {
        assertThat(OAuthErrorMessages.friendly(new RuntimeException(message))).contains("server error");
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
            "Something failed\twith tab",
            "SqlState 23505 duplicate",
            "Failure at com.example.Service.method",
            "Failure at org.example.Service.method"
        }
    )
    void friendly_branchMatrix_technicalStackTokensFallBackGeneric(String message) {
        assertThat(OAuthErrorMessages.friendly(new RuntimeException(message))).contains("Social sign-in failed");
    }
}
