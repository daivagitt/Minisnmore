package com.miniandmore.gateway.security;

import org.springframework.security.oauth2.client.web.server.ServerAuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Stores OAuth authorization state in a short-lived cookie so social login works without server sessions.
 */
@Component
public class WebFluxCookieOAuth2AuthorizationRequestRepository implements ServerAuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    public static final String OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME = "oauth2_auth_request";
    private static final int COOKIE_EXPIRE_SECONDS = 180;

    @Override
    public Mono<OAuth2AuthorizationRequest> loadAuthorizationRequest(ServerWebExchange exchange) {
        return Mono.justOrEmpty(OAuthCookieUtils.getCookieValue(exchange.getRequest(), OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME))
            .flatMap(value ->
                Mono.fromCallable(() -> OAuthCookieUtils.deserialize(value, OAuth2AuthorizationRequest.class)).onErrorResume(
                    IllegalArgumentException.class,
                    ex -> Mono.empty()
                )
            );
    }

    @Override
    public Mono<Void> saveAuthorizationRequest(OAuth2AuthorizationRequest authorizationRequest, ServerWebExchange exchange) {
        if (authorizationRequest == null) {
            return removeAuthorizationRequestCookies(exchange);
        }
        OAuthCookieUtils.addCookie(
            exchange,
            OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME,
            OAuthCookieUtils.serialize(authorizationRequest),
            COOKIE_EXPIRE_SECONDS
        );
        return Mono.empty();
    }

    @Override
    public Mono<OAuth2AuthorizationRequest> removeAuthorizationRequest(ServerWebExchange exchange) {
        return loadAuthorizationRequest(exchange).flatMap(request -> removeAuthorizationRequestCookies(exchange).thenReturn(request));
    }

    public Mono<Void> removeAuthorizationRequestCookies(ServerWebExchange exchange) {
        OAuthCookieUtils.deleteCookie(exchange, OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME);
        return Mono.empty();
    }
}
