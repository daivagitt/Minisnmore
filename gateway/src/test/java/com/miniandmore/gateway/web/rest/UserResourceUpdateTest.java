package com.miniandmore.gateway.web.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.springSecurity;

import com.miniandmore.gateway.domain.User;
import com.miniandmore.gateway.repository.UserRepository;
import com.miniandmore.gateway.security.AuthoritiesConstants;
import com.miniandmore.gateway.service.MailService;
import com.miniandmore.gateway.service.UserService;
import com.miniandmore.gateway.service.dto.AdminUserDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
@WithMockUser(authorities = AuthoritiesConstants.ADMIN)
class UserResourceUpdateTest {

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
        webTestClient = WebTestClientFactory.bindBuilder(resource).apply(springSecurity()).build();
    }

    @Test
    void updateUser_returnsUpdatedUser() {
        AdminUserDTO dto = new AdminUserDTO();
        dto.setId(1L);
        dto.setLogin("user");
        dto.setEmail("user@localhost");
        dto.setFirstName("A");
        dto.setLastName("B");
        User existing = new User();
        existing.setId(1L);
        when(userRepository.findOneByEmailIgnoreCase("user@localhost")).thenReturn(Mono.just(existing));
        when(userRepository.findOneByLogin("user")).thenReturn(Mono.just(existing));
        when(userService.updateUser(any())).thenReturn(Mono.just(dto));

        webTestClient
            .put()
            .uri("/api/admin/users/user")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(dto)
            .exchange()
            .expectStatus()
            .isOk();
    }

    @Test
    void updateUser_duplicateEmail_returnsBadRequest() {
        AdminUserDTO dto = new AdminUserDTO();
        dto.setId(1L);
        dto.setLogin("user");
        dto.setEmail("other@localhost");
        User other = new User();
        other.setId(2L);
        when(userRepository.findOneByEmailIgnoreCase("other@localhost")).thenReturn(Mono.just(other));

        webTestClient
            .put()
            .uri("/api/admin/users/user")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(dto)
            .exchange()
            .expectStatus()
            .isBadRequest();
    }
}
