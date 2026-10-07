package com.miniandmore.gateway.web.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockUser;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.springSecurity;

import com.miniandmore.gateway.domain.User;
import com.miniandmore.gateway.repository.UserRepository;
import com.miniandmore.gateway.security.AuthoritiesConstants;
import com.miniandmore.gateway.service.MailService;
import com.miniandmore.gateway.service.UserService;
import com.miniandmore.gateway.service.dto.AdminUserDTO;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
@WithMockUser(authorities = AuthoritiesConstants.ADMIN)
class UserResourceTest {

    @Mock
    private UserService userService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private MailService mailService;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        UserResource resource = new UserResource(userService, userRepository, mailService);
        ReflectionTestUtils.setField(resource, "applicationName", "gateway");
        webTestClient = WebTestClientFactory
            .bindBuilder(resource)
            .apply(springSecurity())
            .build()
            .mutateWith(mockUser("admin").authorities(AuthoritiesConstants.ADMIN));
    }

    @Test
    void createUser_returnsCreated() {
        AdminUserDTO dto = new AdminUserDTO();
        dto.setLogin("newuser");
        dto.setEmail("new@localhost");
        dto.setFirstName("A");
        dto.setLastName("B");
        dto.setAuthorities(Set.of(AuthoritiesConstants.SALES));
        User user = new User();
        user.setLogin("newuser");
        when(userRepository.findOneByLogin("newuser")).thenReturn(Mono.empty());
        when(userRepository.findOneByEmailIgnoreCase("new@localhost")).thenReturn(Mono.empty());
        when(userService.createStaffUser(any(), any())).thenReturn(Mono.just(user));

        webTestClient
            .post()
            .uri("/api/admin/users")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(dto)
            .exchange()
            .expectStatus()
            .isCreated();
    }

    @Test
    void createUser_withId_returnsBadRequest() {
        AdminUserDTO dto = new AdminUserDTO();
        dto.setId(1L);
        dto.setLogin("newuser");
        dto.setEmail("new@localhost");

        webTestClient
            .post()
            .uri("/api/admin/users")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(dto)
            .exchange()
            .expectStatus()
            .isBadRequest();
    }

    @Test
    void getAllUsers_invalidSort_returnsBadRequest() {
        webTestClient.get().uri("/api/admin/users?sort=invalid,asc").exchange().expectStatus().isBadRequest();
    }

    @Test
    void getAllUsers_returnsOk() {
        when(userService.countManagedUsers()).thenReturn(Mono.just(1L));
        when(userService.getAllManagedUsers(any(Pageable.class))).thenReturn(Flux.just(new AdminUserDTO()));

        webTestClient.get().uri("/api/admin/users?page=0&size=20").exchange().expectStatus().isOk();
    }

    @Test
    void getAllUsers_forSupport_listsCustomersOnly() {
        when(userService.countCustomerUsers()).thenReturn(Mono.just(1L));
        when(userService.getAllCustomerUsers(any(Pageable.class))).thenReturn(Flux.just(new AdminUserDTO()));

        webTestClient
            .mutateWith(mockUser("care").authorities(AuthoritiesConstants.CUSTOMER_SUPPORT))
            .get()
            .uri("/api/admin/users?page=0&size=20")
            .exchange()
            .expectStatus()
            .isOk();
    }

    @Test
    void getUser_returnsUser() {
        User user = new User();
        user.setLogin("user");
        when(userService.getUserWithAuthoritiesByLogin("user")).thenReturn(Mono.just(user));

        webTestClient.get().uri("/api/admin/users/user").exchange().expectStatus().isOk();
    }

    @Test
    void getUser_notFound() {
        when(userService.getUserWithAuthoritiesByLogin("missing")).thenReturn(Mono.empty());

        webTestClient.get().uri("/api/admin/users/missing").exchange().expectStatus().isNotFound();
    }

    @Test
    void deleteUser_returnsNoContent() {
        when(userService.deleteStaffUser(any(), any())).thenReturn(Mono.empty());

        webTestClient.delete().uri("/api/admin/users/user").exchange().expectStatus().isNoContent();
    }

    @Test
    void createUser_duplicateLogin_returnsBadRequest() {
        AdminUserDTO dto = new AdminUserDTO();
        dto.setLogin("existing");
        dto.setEmail("new@localhost");
        dto.setAuthorities(Set.of(AuthoritiesConstants.SALES));
        when(userRepository.findOneByLogin("existing")).thenReturn(Mono.just(new User()));

        webTestClient
            .post()
            .uri("/api/admin/users")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(dto)
            .exchange()
            .expectStatus()
            .isBadRequest();
    }

    @Test
    void createUser_duplicateEmail_returnsBadRequest() {
        AdminUserDTO dto = new AdminUserDTO();
        dto.setLogin("newuser");
        dto.setEmail("taken@localhost");
        dto.setAuthorities(Set.of(AuthoritiesConstants.SALES));
        when(userRepository.findOneByLogin("newuser")).thenReturn(Mono.empty());
        when(userRepository.findOneByEmailIgnoreCase("taken@localhost")).thenReturn(Mono.just(new User()));

        webTestClient
            .post()
            .uri("/api/admin/users")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(dto)
            .exchange()
            .expectStatus()
            .isBadRequest();
    }

    @Test
    void updateUser_duplicateEmail_returnsBadRequest() {
        AdminUserDTO dto = new AdminUserDTO();
        dto.setId(2L);
        dto.setLogin("user");
        dto.setEmail("taken@localhost");
        User existing = new User();
        existing.setId(1L);
        when(userRepository.findOneByEmailIgnoreCase("taken@localhost")).thenReturn(Mono.just(existing));

        webTestClient
            .put()
            .uri("/api/admin/users")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(dto)
            .exchange()
            .expectStatus()
            .isBadRequest();
    }

    @Test
    void updateUser_notFound_returns404() {
        AdminUserDTO dto = new AdminUserDTO();
        dto.setId(1L);
        dto.setLogin("user");
        dto.setEmail("user@localhost");
        dto.setAuthorities(Set.of(AuthoritiesConstants.SALES));
        when(userRepository.findOneByEmailIgnoreCase("user@localhost")).thenReturn(Mono.empty());
        when(userRepository.findOneByLogin("user")).thenReturn(Mono.empty());
        when(userService.updateStaffUser(any(), any())).thenReturn(Mono.empty());

        webTestClient
            .put()
            .uri("/api/admin/users")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(dto)
            .exchange()
            .expectStatus()
            .isNotFound();
    }

    @Test
    void updateUser_returnsOk() {
        AdminUserDTO dto = new AdminUserDTO();
        dto.setId(1L);
        dto.setLogin("user");
        dto.setEmail("user@localhost");
        dto.setFirstName("A");
        dto.setLastName("B");
        dto.setAuthorities(Set.of(AuthoritiesConstants.SALES));
        when(userRepository.findOneByEmailIgnoreCase("user@localhost")).thenReturn(Mono.empty());
        when(userRepository.findOneByLogin("user")).thenReturn(Mono.empty());
        when(userService.updateStaffUser(any(), any())).thenReturn(Mono.just(dto));

        webTestClient
            .put()
            .uri("/api/admin/users")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(dto)
            .exchange()
            .expectStatus()
            .isOk();
    }
}
