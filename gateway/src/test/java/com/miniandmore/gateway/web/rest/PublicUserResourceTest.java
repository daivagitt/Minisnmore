package com.miniandmore.gateway.web.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.miniandmore.gateway.service.UserService;
import com.miniandmore.gateway.service.dto.UserDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class PublicUserResourceTest {

    @Mock
    private UserService userService;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClientFactory.bindToRestController(new PublicUserResource(userService));
    }

    @Test
    void getAllPublicUsers_invalidSort_returnsBadRequest() {
        webTestClient.get().uri("/api/users?sort=invalid,asc").exchange().expectStatus().isBadRequest();
    }

    @Test
    void getAllPublicUsers_returnsOk() {
        when(userService.countManagedUsers()).thenReturn(Mono.just(1L));
        when(userService.getAllPublicUsers(any(Pageable.class))).thenReturn(Flux.just(new UserDTO()));

        webTestClient.get().uri("/api/users?page=0&size=20").exchange().expectStatus().isOk();
    }
}
