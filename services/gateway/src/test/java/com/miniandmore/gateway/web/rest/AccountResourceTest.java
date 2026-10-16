package com.miniandmore.gateway.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockUser;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.springSecurity;

import com.miniandmore.gateway.config.ApplicationProperties;
import com.miniandmore.gateway.domain.User;
import com.miniandmore.gateway.repository.UserRepository;
import com.miniandmore.gateway.security.AuthoritiesConstants;
import com.miniandmore.gateway.service.MailService;
import com.miniandmore.gateway.service.UserService;
import com.miniandmore.gateway.service.dto.AdminUserDTO;
import com.miniandmore.gateway.service.dto.PasswordChangeDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
@WithMockUser(username = "user1", authorities = AuthoritiesConstants.USER)
class AccountResourceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserService userService;

    @Mock
    private MailService mailService;

    private ApplicationProperties applicationProperties;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        applicationProperties = new ApplicationProperties();
        AccountResource resource = new AccountResource(userRepository, userService, mailService, applicationProperties);
        webTestClient = WebTestClientFactory.bindBuilder(resource).apply(springSecurity()).build();
    }

    @Test
    void getAccount_returnsCurrentUser() {
        User user = new User();
        user.setLogin("user1");
        user.setEmail("user1@localhost");
        user.setFirstName("A");
        user.setLastName("B");
        when(userService.getUserWithAuthorities()).thenReturn(Mono.just(user));

        webTestClient
            .get()
            .uri("/api/account")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.login")
            .isEqualTo("user1");
    }

    @Test
    void saveAccount_updatesProfile() {
        User user = new User();
        user.setLogin("user1");
        when(userRepository.findOneByEmailIgnoreCase("new@localhost")).thenReturn(Mono.empty());
        when(userRepository.findOneByLogin("user1")).thenReturn(Mono.just(user));
        when(userService.updateUser(any(), any(), any(), any(), any())).thenReturn(Mono.empty());

        webTestClient
            .mutateWith(mockUser("user1"))
            .post()
            .uri("/api/account")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"login\":\"user1\",\"email\":\"new@localhost\",\"firstName\":\"N\",\"lastName\":\"M\"}")
            .exchange()
            .expectStatus()
            .isOk();
    }

    @Test
    void changePassword_invalidLength_returnsBadRequest() {
        webTestClient
            .post()
            .uri("/api/account/change-password")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"currentPassword\":\"old\",\"newPassword\":\"ab\"}")
            .exchange()
            .expectStatus()
            .isBadRequest();
    }

    @Test
    void changePassword_validLength_delegatesToService() {
        when(userService.changePassword("oldpass", "newpass123")).thenReturn(Mono.empty());

        webTestClient
            .post()
            .uri("/api/account/change-password")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"currentPassword\":\"oldpass\",\"newPassword\":\"newpass123\"}")
            .exchange()
            .expectStatus()
            .isOk();
    }

    @Test
    void requestPasswordReset_alwaysReturnsOk() {
        when(userService.requestPasswordReset("missing@localhost")).thenReturn(Mono.empty());

        webTestClient
            .post()
            .uri("/api/account/reset-password/init")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("\"missing@localhost\"")
            .exchange()
            .expectStatus()
            .isOk();
    }

    @Test
    void resendActivation_withJsonWrappedEmail() {
        when(userService.requestActivationResend("inactive@localhost")).thenReturn(Mono.empty());

        webTestClient
            .post()
            .uri("/api/account/resend-activation")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("\"inactive@localhost\"")
            .exchange()
            .expectStatus()
            .isOk();
    }

    @Test
    void registerAccount_emptyPassword_returnsBadRequest() {
        webTestClient
            .post()
            .uri("/api/register")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"login\":\"newuser\",\"email\":\"new@localhost\",\"password\":\"\"}")
            .exchange()
            .expectStatus()
            .isBadRequest();
    }

    @Test
    void saveAccount_sameEmailForCurrentUser_updatesProfile() {
        User user = new User();
        user.setLogin("user1");
        User sameEmail = new User();
        sameEmail.setLogin("user1");
        when(userRepository.findOneByEmailIgnoreCase("user1@localhost")).thenReturn(Mono.just(sameEmail));
        when(userRepository.findOneByLogin("user1")).thenReturn(Mono.just(user));
        when(userService.updateUser(any(), any(), any(), any(), any())).thenReturn(Mono.empty());

        webTestClient
            .mutateWith(mockUser("user1"))
            .post()
            .uri("/api/account")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"login\":\"user1\",\"email\":\"user1@localhost\",\"firstName\":\"N\",\"lastName\":\"M\"}")
            .exchange()
            .expectStatus()
            .isOk();
    }

    @Test
    void registerAccount_invalidPassword_returnsBadRequest() {
        webTestClient
            .post()
            .uri("/api/register")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"login\":\"newuser\",\"email\":\"new@localhost\",\"password\":\"ab\"}")
            .exchange()
            .expectStatus()
            .isBadRequest();
    }

    @Test
    void registerAccount_sendsActivationEmail() {
        User user = new User();
        user.setLogin("newuser");
        user.setEmail("new@localhost");
        when(userService.registerUser(any(), any())).thenReturn(Mono.just(user));

        webTestClient
            .post()
            .uri("/api/register")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"login\":\"newuser\",\"email\":\"new@localhost\",\"password\":\"password\"}")
            .exchange()
            .expectStatus()
            .isCreated();
    }

    @Test
    void registerAccount_capturesSubscribePreference() {
        User user = new User();
        user.setLogin("newuser");
        user.setEmail("new@localhost");
        when(userService.registerUser(any(), any())).thenReturn(Mono.just(user));

        webTestClient
            .post()
            .uri("/api/register")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"login\":\"newuser\",\"email\":\"new@localhost\",\"password\":\"password\",\"subscribed\":true}")
            .exchange()
            .expectStatus()
            .isCreated();

        ArgumentCaptor<AdminUserDTO> captor = ArgumentCaptor.forClass(AdminUserDTO.class);
        verify(userService).registerUser(captor.capture(), eq("password"));
        assertThat(captor.getValue().getSubscribed()).isTrue();
    }

    @Test
    void registerAccount_autoActivate_skipsEmail() {
        applicationProperties.setRegisterAutoActivate(true);
        User user = new User();
        user.setLogin("newuser");
        user.setActivationKey("activation-key");
        when(userService.registerUser(any(), any())).thenReturn(Mono.just(user));
        when(userService.activateRegistration("activation-key")).thenReturn(Mono.just(user));

        webTestClient
            .post()
            .uri("/api/register")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"login\":\"newuser\",\"email\":\"new@localhost\",\"password\":\"password\"}")
            .exchange()
            .expectStatus()
            .isCreated();
    }

    @Test
    void activateAccount_validKey_activatesUser() {
        User user = new User();
        user.setLogin("newuser");
        when(userService.activateRegistration("valid-key")).thenReturn(Mono.just(user));

        webTestClient.get().uri("/api/activate?key=valid-key").exchange().expectStatus().isOk();
    }

    @Test
    void activateAccount_invalidKey_returnsBadRequest() {
        when(userService.activateRegistration("bad-key")).thenReturn(Mono.empty());

        webTestClient.get().uri("/api/activate?key=bad-key").exchange().expectStatus().isBadRequest();
    }

    @Test
    void getAccount_missingUser_returnsServerError() {
        when(userService.getUserWithAuthorities()).thenReturn(Mono.empty());

        webTestClient.get().uri("/api/account").exchange().expectStatus().is5xxServerError();
    }

    @Test
    void saveAccount_emailAlreadyUsed_returnsBadRequest() {
        User existing = new User();
        existing.setLogin("other");
        when(userRepository.findOneByEmailIgnoreCase("taken@localhost")).thenReturn(Mono.just(existing));

        webTestClient
            .mutateWith(mockUser("user1"))
            .post()
            .uri("/api/account")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"login\":\"user1\",\"email\":\"taken@localhost\",\"firstName\":\"N\",\"lastName\":\"M\"}")
            .exchange()
            .expectStatus()
            .isBadRequest();
    }

    @Test
    void requestPasswordReset_existingUser_sendsMail() {
        User user = new User();
        user.setEmail("user@localhost");
        when(userService.requestPasswordReset("user@localhost")).thenReturn(Mono.just(user));

        webTestClient
            .post()
            .uri("/api/account/reset-password/init")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("\"user@localhost\"")
            .exchange()
            .expectStatus()
            .isOk();
    }

    @Test
    void resendActivation_existingInactiveUser_sendsMail() {
        User user = new User();
        user.setEmail("inactive@localhost");
        when(userService.requestActivationResend("inactive@localhost")).thenReturn(Mono.just(user));

        webTestClient
            .post()
            .uri("/api/account/resend-activation")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("\"inactive@localhost\"")
            .exchange()
            .expectStatus()
            .isOk();
    }

    @Test
    void finishPasswordReset_invalidPassword_returnsBadRequest() {
        webTestClient
            .post()
            .uri("/api/account/reset-password/finish")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"key\":\"reset-key\",\"newPassword\":\"ab\"}")
            .exchange()
            .expectStatus()
            .isBadRequest();
    }

    @Test
    void finishPasswordReset_invalidKey_returnsBadRequest() {
        when(userService.completePasswordReset("newpass123", "bad-key")).thenReturn(Mono.empty());

        webTestClient
            .post()
            .uri("/api/account/reset-password/finish")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"key\":\"bad-key\",\"newPassword\":\"newpass123\"}")
            .exchange()
            .expectStatus()
            .isBadRequest();
    }

    @Test
    void finishPasswordReset_validKey_resetsPassword() {
        User user = new User();
        user.setLogin("user1");
        when(userService.completePasswordReset("newpass123", "valid-key")).thenReturn(Mono.just(user));

        webTestClient
            .post()
            .uri("/api/account/reset-password/finish")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"key\":\"valid-key\",\"newPassword\":\"newpass123\"}")
            .exchange()
            .expectStatus()
            .isOk();
    }

    @Test
    void finishInvite_validKey_activatesStaffUser() {
        User user = new User();
        user.setLogin("staff");
        when(userService.completeInvite("newpass123", "invite-key")).thenReturn(Mono.just(user));

        webTestClient
            .post()
            .uri("/api/account/invite/finish")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"key\":\"invite-key\",\"newPassword\":\"newpass123\"}")
            .exchange()
            .expectStatus()
            .isOk();
    }

    @Test
    void finishInvite_invalidKey_returnsBadRequest() {
        when(userService.completeInvite("newpass123", "bad-key")).thenReturn(Mono.empty());

        webTestClient
            .post()
            .uri("/api/account/invite/finish")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"key\":\"bad-key\",\"newPassword\":\"newpass123\"}")
            .exchange()
            .expectStatus()
            .isBadRequest();
    }
}
