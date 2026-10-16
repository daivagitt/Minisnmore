package com.miniandmore.gateway.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.miniandmore.gateway.config.ApplicationProperties;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.web.server.WebFilterExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class OAuth2AuthenticationSuccessHandlerTest {

    @Mock
    private OAuth2UserTokenService oauth2UserTokenService;

    @Mock
    private WebFluxCookieOAuth2AuthorizationRequestRepository authorizationRequestRepository;

    private OAuth2AuthenticationSuccessHandler handler;

    @BeforeEach
    void setUp() {
        ApplicationProperties properties = new ApplicationProperties();
        properties.getOAuth2().setFrontendCallbackUrl("http://localhost:5173/callback");
        handler = new OAuth2AuthenticationSuccessHandler(oauth2UserTokenService, properties, authorizationRequestRepository);
    }

    @Test
    void onAuthenticationSuccess_redirectsWithToken() {
        when(authorizationRequestRepository.removeAuthorizationRequestCookies(any())).thenReturn(Mono.empty());
        when(oauth2UserTokenService.tokenFor(any())).thenReturn(Mono.just("jwt"));
        WebFilterExchange webFilterExchange = mock(WebFilterExchange.class);
        MockServerWebExchange exchange = MockServerWebExchange.from(org.springframework.mock.http.server.reactive.MockServerHttpRequest.get("/").build());
        when(webFilterExchange.getExchange()).thenReturn(exchange);
        DefaultOAuth2User principal = new DefaultOAuth2User(
            AuthorityUtils.createAuthorityList("ROLE_USER"),
            Map.of("email", "user@localhost"),
            "email"
        );
        var authentication = new UsernamePasswordAuthenticationToken(principal, "N/A", principal.getAuthorities());

        StepVerifier.create(handler.onAuthenticationSuccess(webFilterExchange, authentication)).verifyComplete();
    }

    @Test
    void onAuthenticationSuccess_nonOAuthPrincipal_redirectsWithError() {
        when(authorizationRequestRepository.removeAuthorizationRequestCookies(any())).thenReturn(Mono.empty());
        WebFilterExchange webFilterExchange = mock(WebFilterExchange.class);
        MockServerWebExchange exchange = MockServerWebExchange.from(org.springframework.mock.http.server.reactive.MockServerHttpRequest.get("/").build());
        when(webFilterExchange.getExchange()).thenReturn(exchange);
        var authentication = new UsernamePasswordAuthenticationToken("user", "pass");

        StepVerifier.create(handler.onAuthenticationSuccess(webFilterExchange, authentication)).verifyComplete();
    }
}
