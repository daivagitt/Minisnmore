package com.miniandmore.gateway.security;

import com.miniandmore.gateway.config.ApplicationProperties;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.server.WebFilterExchange;
import org.springframework.security.web.server.authentication.ServerAuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class OAuth2AuthenticationFailureHandler implements ServerAuthenticationFailureHandler {

    private final ApplicationProperties applicationProperties;
    private final WebFluxCookieOAuth2AuthorizationRequestRepository authorizationRequestRepository;

    public OAuth2AuthenticationFailureHandler(
        ApplicationProperties applicationProperties,
        WebFluxCookieOAuth2AuthorizationRequestRepository authorizationRequestRepository
    ) {
        this.applicationProperties = applicationProperties;
        this.authorizationRequestRepository = authorizationRequestRepository;
    }

    @Override
    public Mono<Void> onAuthenticationFailure(WebFilterExchange webFilterExchange, AuthenticationException exception) {
        ServerWebExchange exchange = webFilterExchange.getExchange();
        return authorizationRequestRepository
            .removeAuthorizationRequestCookies(exchange)
            .then(redirect(exchange, OAuthCallbackRedirect.callbackUrl(applicationProperties, "error", friendlyMessage(exception))));
    }

    static String friendlyMessage(AuthenticationException exception) {
        if (exception instanceof OAuth2AuthenticationException oauth2Ex && oauth2Ex.getError() != null) {
            var code = oauth2Ex.getError().getErrorCode();
            var description = oauth2Ex.getError().getDescription();
            if ("invalid_client".equals(code) || "unauthorized_client".equals(code)) {
                return "Social sign-in is misconfigured on the server (missing or invalid client secret).";
            }
            if ("redirect_uri_mismatch".equals(code)) {
                return "Social sign-in redirect URI is not registered for this site.";
            }
            if (description != null && !description.isBlank()) {
                return description;
            }
            if (code != null && !code.isBlank()) {
                return code;
            }
        }
        var message = exception.getMessage();
        return message == null || message.isBlank() ? "Social sign-in failed. Please try again." : message;
    }

    private Mono<Void> redirect(ServerWebExchange exchange, String location) {
        exchange.getResponse().setStatusCode(HttpStatus.FOUND);
        exchange.getResponse().getHeaders().setLocation(URI.create(location));
        return exchange.getResponse().setComplete();
    }
}
