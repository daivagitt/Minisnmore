package com.miniandmore.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.miniandmore.gateway.config.ApplicationProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.web.server.WebFilterExchange;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class OAuth2AuthenticationFailureHandlerTest {

    @Mock
    private WebFluxCookieOAuth2AuthorizationRequestRepository authorizationRequestRepository;

    private OAuth2AuthenticationFailureHandler handler;

    @BeforeEach
    void setUp() {
        ApplicationProperties properties = new ApplicationProperties();
        properties.getOAuth2().setFrontendCallbackUrl("http://localhost:5173/callback");
        handler = new OAuth2AuthenticationFailureHandler(properties, authorizationRequestRepository);
    }

    @Test
    void friendlyMessage_mapsKnownOAuthErrors() {
        OAuth2Error error = new OAuth2Error("invalid_client", "bad client", null);
        OAuth2AuthenticationException ex = new OAuth2AuthenticationException(error);

        assertThat(OAuth2AuthenticationFailureHandler.friendlyMessage(ex)).contains("misconfigured");
    }

    @Test
    void friendlyMessage_redirectUriMismatch() {
        OAuth2Error error = new OAuth2Error("redirect_uri_mismatch", null, null);
        assertThat(OAuth2AuthenticationFailureHandler.friendlyMessage(new OAuth2AuthenticationException(error))).contains("redirect URI");
    }

    @Test
    void friendlyMessage_mapsProviderCodesToHumanText() {
        OAuth2Error accessDenied = new OAuth2Error("access_denied", null, null);
        assertThat(OAuth2AuthenticationFailureHandler.friendlyMessage(new OAuth2AuthenticationException(accessDenied)))
            .contains("cancelled");

        OAuth2Error serverError = new OAuth2Error("server_error", "provider down", null);
        assertThat(OAuth2AuthenticationFailureHandler.friendlyMessage(new OAuth2AuthenticationException(serverError)))
            .contains("temporarily unavailable");

        OAuth2Error unknown = new OAuth2Error("custom_code", "Please allow email access", null);
        assertThat(OAuth2AuthenticationFailureHandler.friendlyMessage(new OAuth2AuthenticationException(unknown)))
            .isEqualTo("Please allow email access");
    }

    @Test
    void friendlyMessage_defaultsWhenBlank() {
        assertThat(OAuth2AuthenticationFailureHandler.friendlyMessage(new org.springframework.security.authentication.BadCredentialsException("")))
            .contains("Social sign-in failed");
    }

    @Test
    void friendlyMessage_hidesRawOAuthCodes() {
        OAuth2Error withCode = new OAuth2Error("temporarily_unavailable", null, null);
        assertThat(OAuth2AuthenticationFailureHandler.friendlyMessage(new OAuth2AuthenticationException(withCode)))
            .doesNotContain("temporarily_unavailable")
            .contains("temporarily unavailable");
    }

    @Test
    void onAuthenticationFailure_redirects() {
        when(authorizationRequestRepository.removeAuthorizationRequestCookies(org.mockito.Mockito.any())).thenReturn(reactor.core.publisher.Mono.empty());
        WebFilterExchange webFilterExchange = mock(WebFilterExchange.class);
        MockServerWebExchange exchange = MockServerWebExchange.from(org.springframework.mock.http.server.reactive.MockServerHttpRequest.get("/").build());
        when(webFilterExchange.getExchange()).thenReturn(exchange);

        StepVerifier.create(
            handler.onAuthenticationFailure(webFilterExchange, new OAuth2AuthenticationException(new OAuth2Error("access_denied")))
        ).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(302);
    }
}
