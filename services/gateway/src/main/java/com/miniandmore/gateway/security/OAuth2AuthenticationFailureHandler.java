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
            var mapped = mapOAuthCode(code);
            if (mapped != null) {
                return mapped;
            }
            var description = oauth2Ex.getError().getDescription();
            if (description != null && !description.isBlank() && description.length() <= 180 && !looksTechnical(description)) {
                return description;
            }
        }
        var message = exception.getMessage();
        if (message != null && !message.isBlank() && message.length() <= 180 && !looksTechnical(message)) {
            return message;
        }
        return "Social sign-in failed. Please try again, or create an account with email.";
    }

    private static String mapOAuthCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return switch (code) {
            case "invalid_client", "unauthorized_client" ->
                "Social sign-in is misconfigured on the server. Please try again later or use email sign-up.";
            case "redirect_uri_mismatch" ->
                "Social sign-in redirect URI is not registered for this site. Please contact support.";
            case "access_denied" ->
                "Sign-in was cancelled. You can try again or create an account with email.";
            case "invalid_grant", "invalid_token" ->
                "Social sign-in expired. Please try Continue with Google again.";
            case "server_error", "temporarily_unavailable" ->
                "Google or the social provider is temporarily unavailable. Please try again in a moment.";
            case "invalid_scope", "insufficient_scope" ->
                "We could not get the permissions needed to sign you in. Please try again and allow email access.";
            case "account_selection_required", "interaction_required", "login_required" ->
                "Please complete sign-in with your social account, then try again.";
            default -> null;
        };
    }

    private static boolean looksTechnical(String message) {
        String lower = message.toLowerCase();
        return lower.contains("exception") ||
            lower.contains("oauth2error") ||
            lower.contains("at org.") ||
            lower.contains("at com.") ||
            message.contains("\n");
    }

    private Mono<Void> redirect(ServerWebExchange exchange, String location) {
        exchange.getResponse().setStatusCode(HttpStatus.FOUND);
        exchange.getResponse().getHeaders().setLocation(URI.create(location));
        return exchange.getResponse().setComplete();
    }
}
