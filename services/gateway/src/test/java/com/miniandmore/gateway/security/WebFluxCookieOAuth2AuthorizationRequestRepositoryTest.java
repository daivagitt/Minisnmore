package com.miniandmore.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpCookie;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import reactor.test.StepVerifier;

class WebFluxCookieOAuth2AuthorizationRequestRepositoryTest {

    private final WebFluxCookieOAuth2AuthorizationRequestRepository repository = new WebFluxCookieOAuth2AuthorizationRequestRepository();

    @Test
    void saveAndLoadAuthorizationRequest_roundTrip() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/oauth2/authorization/google").build());
        OAuth2AuthorizationRequest request = OAuth2AuthorizationRequest.authorizationCode()
            .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
            .clientId("client")
            .redirectUri("http://localhost:8080/login/oauth2/code/google")
            .build();

        StepVerifier.create(repository.saveAuthorizationRequest(request, exchange)).verifyComplete();

        String cookieValue = OAuthCookieUtils.serialize(request);
        MockServerWebExchange exchangeWithCookie = MockServerWebExchange.from(
            MockServerHttpRequest.get("/")
                .cookie(new HttpCookie(WebFluxCookieOAuth2AuthorizationRequestRepository.OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME, cookieValue))
                .build()
        );

        StepVerifier
            .create(repository.loadAuthorizationRequest(exchangeWithCookie))
            .assertNext(loaded -> assertThat(loaded.getClientId()).isEqualTo("client"))
            .verifyComplete();
    }

    @Test
    void saveNull_removesCookie() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/").build());
        StepVerifier.create(repository.saveAuthorizationRequest(null, exchange)).verifyComplete();
    }

    @Test
    void loadAuthorizationRequest_emptyWhenCookieMissing() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/").build());
        StepVerifier.create(repository.loadAuthorizationRequest(exchange)).verifyComplete();
    }

    @Test
    void removeAuthorizationRequest_returnsSavedRequest() {
        HashMap<String, Object> attributes = new HashMap<>();
        attributes.put("key", "value");
        OAuth2AuthorizationRequest request = OAuth2AuthorizationRequest.authorizationCode()
            .clientId("client")
            .authorizationUri("https://example.com")
            .attributes(attributes)
            .build();
        String serialized = OAuthCookieUtils.serialize(request);
        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/")
                .cookie(new HttpCookie(WebFluxCookieOAuth2AuthorizationRequestRepository.OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME, serialized))
                .build()
        );

        StepVerifier
            .create(repository.removeAuthorizationRequest(exchange))
            .assertNext(removed -> assertThat(removed.getClientId()).isEqualTo("client"))
            .verifyComplete();
    }

    @Test
    void removeAuthorizationRequestCookies_completes() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/").build());
        StepVerifier.create(repository.removeAuthorizationRequestCookies(exchange)).verifyComplete();
    }
}
