package com.miniandmore.gateway.security;

import com.miniandmore.gateway.config.ApplicationProperties;
import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/** Blocks OAuth authorization requests for providers not enabled in application config. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class OAuthProviderGateWebFilter implements WebFilter {

    static final String DISABLED_PROVIDER_MESSAGE = "This sign-in option is not available right now.";

    private static final Pattern AUTHORIZATION_PATH = Pattern.compile("^/oauth2/authorization/([^/]+)$");

    private final ApplicationProperties applicationProperties;

    public OAuthProviderGateWebFilter(ApplicationProperties applicationProperties) {
        this.applicationProperties = applicationProperties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        Matcher matcher = AUTHORIZATION_PATH.matcher(exchange.getRequest().getPath().value());
        if (!matcher.matches()) {
            return chain.filter(exchange);
        }
        String provider = matcher.group(1);
        if (applicationProperties.getOAuth2().isProviderEnabled(provider)) {
            return chain.filter(exchange);
        }
        String location = OAuthCallbackRedirect.callbackUrl(applicationProperties, "error", DISABLED_PROVIDER_MESSAGE);
        exchange.getResponse().setStatusCode(HttpStatus.FOUND);
        exchange.getResponse().getHeaders().setLocation(URI.create(location));
        return exchange.getResponse().setComplete();
    }
}
