package com.miniandmore.gateway.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.miniandmore.gateway.security.JwtTokenService;
import com.miniandmore.gateway.web.rest.vm.LoginVM;
import java.security.Principal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class AuthenticateControllerTest {

    @Mock
    private JwtTokenService jwtTokenService;

    @Mock
    private ReactiveAuthenticationManager authenticationManager;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToController(new AuthenticateController(jwtTokenService, authenticationManager)).build();
    }

    @Test
    void authorize_returnsToken() {
        Authentication auth = new UsernamePasswordAuthenticationToken("user", "pass");
        when(authenticationManager.authenticate(any())).thenReturn(Mono.just(auth));
        when(jwtTokenService.createToken(auth, false)).thenReturn("jwt-token");

        LoginVM login = new LoginVM();
        login.setUsername("user");
        login.setPassword("pass");

        webTestClient
            .post()
            .uri("/api/authenticate")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(login)
            .exchange()
            .expectStatus()
            .isOk()
            .expectHeader()
            .valueEquals("Authorization", "Bearer jwt-token");
    }

    @Test
    void isAuthenticated_withoutPrincipal_returnsUnauthorized() {
        webTestClient.get().uri("/api/authenticate").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void isAuthenticated_withPrincipal_returnsNoContent() {
        AuthenticateController controller = new AuthenticateController(jwtTokenService, authenticationManager);
        Principal principal = () -> "user";
        assertThat(controller.isAuthenticated(principal).getStatusCode().value()).isEqualTo(204);
        assertThat(controller.isAuthenticated(null).getStatusCode().value()).isEqualTo(401);
    }
}
