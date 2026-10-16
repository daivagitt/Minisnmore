package com.miniandmore.gateway.web.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.springSecurity;

import com.miniandmore.gateway.domain.Authority;
import com.miniandmore.gateway.repository.AuthorityRepository;
import com.miniandmore.gateway.security.AuthoritiesConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
@WithMockUser(authorities = AuthoritiesConstants.ADMIN)
class AuthorityResourceTest {

    @Mock
    private AuthorityRepository authorityRepository;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        AuthorityResource resource = new AuthorityResource(authorityRepository);
        ReflectionTestUtils.setField(resource, "applicationName", "gateway");
        webTestClient = WebTestClientFactory.bindBuilder(resource).apply(springSecurity()).build();
    }

    @Test
    @WithMockUser(authorities = AuthoritiesConstants.SUPER_ADMIN)
    void createAuthority_returnsCreated() {
        Authority authority = new Authority();
        authority.setName("ROLE_TEST");
        when(authorityRepository.existsById("ROLE_TEST")).thenReturn(Mono.just(false));
        when(authorityRepository.save(any(Authority.class))).thenReturn(Mono.just(authority));

        webTestClient
            .post()
            .uri("/api/authorities")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(authority)
            .exchange()
            .expectStatus()
            .isCreated();
    }

    @Test
    @WithMockUser(authorities = AuthoritiesConstants.SUPER_ADMIN)
    void createAuthority_existing_returnsBadRequest() {
        Authority authority = new Authority();
        authority.setName("ROLE_TEST");
        when(authorityRepository.existsById("ROLE_TEST")).thenReturn(Mono.just(true));

        webTestClient
            .post()
            .uri("/api/authorities")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(authority)
            .exchange()
            .expectStatus()
            .isBadRequest();
    }

    @Test
    void getAllAuthorities_returnsList() {
        Authority authority = new Authority();
        authority.setName(AuthoritiesConstants.USER);
        when(authorityRepository.findAll()).thenReturn(Flux.just(authority));

        webTestClient.get().uri("/api/authorities").accept(MediaType.APPLICATION_JSON).exchange().expectStatus().isOk();
    }

    @Test
    void getAllAuthoritiesAsStream_returnsFlux() {
        Authority authority = new Authority();
        authority.setName(AuthoritiesConstants.USER);
        when(authorityRepository.findAll()).thenReturn(Flux.just(authority));

        webTestClient.get().uri("/api/authorities").accept(MediaType.APPLICATION_NDJSON).exchange().expectStatus().isOk();
    }

    @Test
    void getAuthority_returnsEntity() {
        Authority authority = new Authority();
        authority.setName(AuthoritiesConstants.USER);
        when(authorityRepository.findById(AuthoritiesConstants.USER)).thenReturn(Mono.just(authority));

        webTestClient.get().uri("/api/authorities/" + AuthoritiesConstants.USER).exchange().expectStatus().isOk();
    }

    @Test
    @WithMockUser(authorities = AuthoritiesConstants.SUPER_ADMIN)
    void deleteAuthority_returnsNoContent() {
        when(authorityRepository.deleteById(AuthoritiesConstants.USER)).thenReturn(Mono.empty());

        webTestClient.delete().uri("/api/authorities/" + AuthoritiesConstants.USER).exchange().expectStatus().isNoContent();
    }
}
