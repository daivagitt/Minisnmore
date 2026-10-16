package com.miniandmore.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

class OAuth2AuthenticationFailureHandlerFriendlyMessageMatrixTest {

    @ParameterizedTest
    @CsvSource(
        {
            "invalid_client,misconfigured",
            "unauthorized_client,misconfigured",
            "redirect_uri_mismatch,redirect URI",
            "access_denied,cancelled",
            "invalid_grant,expired",
            "invalid_token,expired",
            "server_error,temporarily unavailable",
            "temporarily_unavailable,temporarily unavailable",
            "invalid_scope,permissions",
            "insufficient_scope,permissions",
            "account_selection_required,complete sign-in",
            "interaction_required,complete sign-in",
            "login_required,complete sign-in",
        }
    )
    void friendlyMessage_mapsKnownOAuthCodes(String code, String fragment) {
        OAuth2AuthenticationException ex = new OAuth2AuthenticationException(new OAuth2Error(code, "tech", null));
        assertThat(OAuth2AuthenticationFailureHandler.friendlyMessage(ex)).containsIgnoringCase(fragment.split(" ")[0]);
    }

    @Test
    void friendlyMessage_usesShortNonTechnicalDescriptionThenMessageThenFallback() {
        OAuth2AuthenticationException withDescription = new OAuth2AuthenticationException(
            new OAuth2Error("unknown_code", "Please allow calendar access", null)
        );
        assertThat(OAuth2AuthenticationFailureHandler.friendlyMessage(withDescription)).isEqualTo("Please allow calendar access");

        OAuth2AuthenticationException technical = new OAuth2AuthenticationException(
            new OAuth2Error("unknown_code", "OAuth2Error at org.springframework", null)
        );
        assertThat(OAuth2AuthenticationFailureHandler.friendlyMessage(technical)).contains("Social sign-in failed");

        OAuth2AuthenticationException blankDesc = new OAuth2AuthenticationException(new OAuth2Error("unknown_code", "  ", null));
        assertThat(OAuth2AuthenticationFailureHandler.friendlyMessage(blankDesc)).contains("Social sign-in failed");

        assertThat(OAuth2AuthenticationFailureHandler.friendlyMessage(new AuthenticationServiceException("Use another email")))
            .isEqualTo("Use another email");
        assertThat(OAuth2AuthenticationFailureHandler.friendlyMessage(new AuthenticationServiceException("exception stack")))
            .contains("Social sign-in failed");
        assertThat(OAuth2AuthenticationFailureHandler.friendlyMessage(new AuthenticationServiceException("x".repeat(200))))
            .contains("Social sign-in failed");
        assertThat(OAuth2AuthenticationFailureHandler.friendlyMessage(new AuthenticationServiceException((String) null)))
            .contains("Social sign-in failed");
        assertThat(OAuth2AuthenticationFailureHandler.friendlyMessage(new OAuth2AuthenticationException(new OAuth2Error("unmapped"))))
            .contains("Social sign-in failed");
    }
}
