package com.miniandmore.gateway.security;

import com.miniandmore.gateway.config.ApplicationProperties;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.server.WebFilterExchange;
import org.springframework.security.web.server.authentication.ServerAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class OAuth2AuthenticationSuccessHandler implements ServerAuthenticationSuccessHandler {

    private static final Logger LOG = LoggerFactory.getLogger(OAuth2AuthenticationSuccessHandler.class);

    private final OAuth2UserTokenService oauth2UserTokenService;
    private final ApplicationProperties applicationProperties;
    private final WebFluxCookieOAuth2AuthorizationRequestRepository authorizationRequestRepository;

    public OAuth2AuthenticationSuccessHandler(
        OAuth2UserTokenService oauth2UserTokenService,
        ApplicationProperties applicationProperties,
        WebFluxCookieOAuth2AuthorizationRequestRepository authorizationRequestRepository
    ) {
        this.oauth2UserTokenService = oauth2UserTokenService;
        this.applicationProperties = applicationProperties;
        this.authorizationRequestRepository = authorizationRequestRepository;
    }

    @Override
    public Mono<Void> onAuthenticationSuccess(WebFilterExchange webFilterExchange, Authentication authentication) {
        ServerWebExchange exchange = webFilterExchange.getExchange();
        return authorizationRequestRepository
            .removeAuthorizationRequestCookies(exchange)
            .then(
                Mono.defer(() -> {
                    if (!(authentication.getPrincipal() instanceof OAuth2User oauthUser)) {
                        return redirect(exchange, OAuthCallbackRedirect.callbackUrl(applicationProperties, "error", "OAuth user principal was not available"));
                    }
                    return oauth2UserTokenService
                        .tokenFor(oauthUser)
                        .flatMap(token -> redirect(exchange, OAuthCallbackRedirect.callbackUrl(applicationProperties, "token", token)))
                        .onErrorResume(ex -> {
                            LOG.warn("OAuth sign-in failed: {}", ex.toString(), ex);
                            return redirect(
                                exchange,
                                OAuthCallbackRedirect.callbackUrl(applicationProperties, "error", OAuthErrorMessages.friendly(ex))
                            );
                        });
                })
            );
    }

    private Mono<Void> redirect(ServerWebExchange exchange, String location) {
        exchange.getResponse().setStatusCode(HttpStatus.FOUND);
        exchange.getResponse().getHeaders().setLocation(URI.create(location));
        return exchange.getResponse().setComplete();
    }
}
