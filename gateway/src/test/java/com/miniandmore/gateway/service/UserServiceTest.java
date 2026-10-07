package com.miniandmore.gateway.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.miniandmore.gateway.domain.Authority;
import com.miniandmore.gateway.domain.User;
import com.miniandmore.gateway.repository.AuthorityRepository;
import com.miniandmore.gateway.repository.UserAuthoritiesLoader;
import com.miniandmore.gateway.repository.UserRepository;
import com.miniandmore.gateway.security.AuthoritiesConstants;
import com.miniandmore.gateway.service.dto.AdminUserDTO;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import org.mockito.ArgumentMatchers;
import java.util.HashSet;
import java.util.Set;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthorityRepository authorityRepository;

    @Mock
    private UserAuthoritiesLoader userAuthoritiesLoader;

    private UserService userService;

    private User user;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, authorityRepository, userAuthoritiesLoader, passwordEncoder);
        user = sampleUser();
        lenient().when(userAuthoritiesLoader.withAuthorities(any(User.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        lenient().when(userAuthoritiesLoader.attachAuthorities(anyList())).thenAnswer(inv -> Flux.fromIterable(inv.getArgument(0)));
        lenient().when(passwordEncoder.encode(anyString())).thenReturn("encoded");
        lenient()
            .when(userRepository.save(any(User.class)))
            .thenAnswer(inv -> {
                User saved = inv.getArgument(0);
                if (saved.getId() == null) {
                    saved.setId(1L);
                }
                return Mono.just(saved);
            });
        when(userRepository.deleteUserAuthorities(anyLong())).thenReturn(Mono.empty());
        lenient().when(userRepository.saveUserAuthority(ArgumentMatchers.nullable(Long.class), anyString())).thenReturn(Mono.empty());
        lenient().when(userRepository.delete(any(User.class))).thenReturn(Mono.empty());
        lenient().when(userRepository.findOneByEmailIgnoreCase(anyString())).thenReturn(Mono.empty());
        Authority defaultAuthority = new Authority();
        defaultAuthority.setName(AuthoritiesConstants.USER);
        lenient().when(authorityRepository.findById(anyString())).thenReturn(Mono.just(defaultAuthority));
        lenient().when(userRepository.nextUserId()).thenReturn(Mono.just(100L));
    }

    @Test
    void activateRegistration_activatesUser() {
        user.setActivationKey("key");
        when(userRepository.findOneByActivationKey("key")).thenReturn(Mono.just(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(userRepository.saveUserAuthority(anyLong(), anyString())).thenReturn(Mono.empty());

        StepVerifier.create(userService.activateRegistration("key").contextWrite(securityContext()))
            .assertNext(activated -> assertThat(activated.isActivated()).isTrue())
            .verifyComplete();
    }

    @Test
    void activateRegistration_isIdempotentWhenAlreadyActivated() {
        user.setActivated(true);
        user.setActivationKey("key");
        when(userRepository.findOneByActivationKey("key")).thenReturn(Mono.just(user));

        StepVerifier.create(userService.activateRegistration("key").contextWrite(securityContext()))
            .assertNext(activated -> assertThat(activated.isActivated()).isTrue())
            .verifyComplete();

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void completePasswordReset_updatesPasswordWhenKeyValid() {
        user.setResetKey("reset");
        user.setResetDate(Instant.now());
        when(userRepository.findOneByResetKey("reset")).thenReturn(Mono.just(user));
        when(passwordEncoder.encode("new")).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(userRepository.saveUserAuthority(anyLong(), anyString())).thenReturn(Mono.empty());

        StepVerifier.create(userService.completePasswordReset("new", "reset").contextWrite(securityContext()))
            .assertNext(updated -> {
                assertThat(updated.getResetKey()).isNull();
                assertThat(updated.getPassword()).isEqualTo("encoded");
            })
            .verifyComplete();
    }

    @Test
    void requestPasswordReset_setsResetKeyForActivatedUser() {
        user.setActivated(true);
        when(userRepository.findOneByEmailIgnoreCase("user@localhost")).thenReturn(Mono.just(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(userRepository.saveUserAuthority(anyLong(), anyString())).thenReturn(Mono.empty());

        StepVerifier.create(userService.requestPasswordReset("user@localhost").contextWrite(securityContext()))
            .assertNext(updated -> assertThat(updated.getResetKey()).isNotBlank())
            .verifyComplete();
    }

    @Test
    void requestPasswordReset_fallsBackToLoginWhenEmailColumnDoesNotMatch() {
        user.setActivated(true);
        when(userRepository.findOneByEmailIgnoreCase("user@localhost")).thenReturn(Mono.empty());
        when(userRepository.findOneByLogin("user@localhost")).thenReturn(Mono.just(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(userRepository.saveUserAuthority(anyLong(), anyString())).thenReturn(Mono.empty());

        StepVerifier.create(userService.requestPasswordReset("user@localhost").contextWrite(securityContext()))
            .assertNext(updated -> assertThat(updated.getResetKey()).isNotBlank())
            .verifyComplete();
    }

    @Test
    void registerUser_createsNewUser() {
        AdminUserDTO dto = adminDto("newuser", "new@localhost");
        dto.setSubscribed(true);
        Authority authority = new Authority();
        authority.setName(AuthoritiesConstants.USER);
        when(userRepository.findOneByLogin("newuser")).thenReturn(Mono.empty());
        when(userRepository.findOneByEmailIgnoreCase("new@localhost")).thenReturn(Mono.empty());
        when(passwordEncoder.encode("password")).thenReturn("encoded");
        when(authorityRepository.findById(AuthoritiesConstants.USER)).thenReturn(Mono.just(authority));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User saved = inv.getArgument(0);
            saved.setId(2L);
            return Mono.just(saved);
        });
        when(userRepository.saveUserAuthority(anyLong(), anyString())).thenReturn(Mono.empty());

        StepVerifier.create(userService.registerUser(dto, "password").contextWrite(securityContext()))
            .assertNext(registered -> {
                assertThat(registered.getLogin()).isEqualTo("newuser");
                assertThat(registered.isSubscribed()).isTrue();
            })
            .verifyComplete();
    }

    @Test
    void registerUser_existingInactiveUserIsDeleted() {
        AdminUserDTO dto = adminDto("newuser", "new@localhost");
        User inactive = sampleUser();
        inactive.setActivated(false);
        Authority authority = new Authority();
        authority.setName(AuthoritiesConstants.USER);
        org.mockito.Mockito.doReturn(Mono.just(inactive)).when(userRepository).findOneByLogin("newuser");
        org.mockito.Mockito.doReturn(Mono.empty()).when(userRepository).delete(inactive);
        org.mockito.Mockito.doReturn(Mono.empty()).when(userRepository).deleteUserAuthorities(inactive.getId());
        org.mockito.Mockito.doReturn(Mono.empty()).when(userRepository).findOneByEmailIgnoreCase("new@localhost");
        org.mockito.Mockito.doReturn(Mono.just(authority)).when(authorityRepository).findById(AuthoritiesConstants.USER);
        User registered = sampleUser();
        registered.setLogin("newuser");
        registered.setEmail("new@localhost");
        org.mockito.Mockito.doReturn(Mono.just(registered)).when(userRepository).save(org.mockito.Mockito.any(User.class));

        StepVerifier.create(userService.registerUser(dto, "password").contextWrite(securityContext()))
            .expectNextCount(1)
            .verifyComplete();
        verify(userRepository).delete(inactive);
    }

    @Test
    void registerUser_existingActivatedUserFails() {
        AdminUserDTO dto = adminDto("user", "user@localhost");
        user.setActivated(true);
        when(userRepository.findOneByLogin("user")).thenReturn(Mono.just(user));
        when(userRepository.findOneByEmailIgnoreCase("user@localhost")).thenReturn(Mono.empty());

        StepVerifier.create(userService.registerUser(dto, "password")).expectError(UsernameAlreadyUsedException.class).verify();
    }

    @Test
    void createUser_invitesInactiveStaffUser() {
        AdminUserDTO dto = adminDto("created", "created@localhost");
        dto.setAuthorities(Set.of(AuthoritiesConstants.SALES));
        User saved = sampleUser();
        saved.setLogin("created");
        saved.setEmail("created@localhost");
        saved.setActivated(false);
        org.mockito.Mockito
            .doAnswer(invocation -> {
                User persisted = invocation.getArgument(0);
                persisted.setId(1L);
                return Mono.just(persisted);
            })
            .when(userRepository)
            .save(org.mockito.Mockito.any(User.class));

        StepVerifier.create(userService.createUser(dto).contextWrite(securityContext()))
            .assertNext(created -> {
                assertThat(created.isActivated()).isFalse();
                assertThat(created.getActivationKey()).isNotBlank();
            })
            .verifyComplete();
    }

    @Test
    void completeInvite_activatesStaffAndClearsKey() {
        user.setActivated(false);
        user.setActivationKey("invite-key");
        Authority sales = new Authority();
        sales.setName(AuthoritiesConstants.SALES);
        user.setAuthorities(Set.of(sales));
        when(userRepository.findOneByActivationKey("invite-key")).thenReturn(Mono.just(user));

        StepVerifier.create(userService.completeInvite("newpass123", "invite-key"))
            .assertNext(activated -> {
                assertThat(activated.isActivated()).isTrue();
                assertThat(activated.getActivationKey()).isNull();
                assertThat(activated.getPassword()).isEqualTo("encoded");
            })
            .verifyComplete();
    }

    @Test
    void completeInvite_rejectsShopperActivationKey() {
        user.setActivated(false);
        user.setActivationKey("shopper-key");
        when(userRepository.findOneByActivationKey("shopper-key")).thenReturn(Mono.just(user));

        StepVerifier.create(userService.completeInvite("newpass123", "shopper-key")).verifyComplete();
        verify(userRepository, never()).save(user);
    }

    @Test
    void updateStaffUser_rejectsActivatingPendingInvite() {
        user.setActivated(false);
        user.setActivationKey("invite-key");
        Authority sales = new Authority();
        sales.setName(AuthoritiesConstants.SALES);
        user.setAuthorities(Set.of(sales));
        AdminUserDTO dto = adminDto("user", "user@localhost");
        dto.setId(1L);
        dto.setActivated(true);
        dto.setAuthorities(Set.of(AuthoritiesConstants.SALES));
        when(userRepository.findById(1L)).thenReturn(Mono.just(user));

        StepVerifier.create(userService.updateStaffUser(dto, Set.of(AuthoritiesConstants.SUPER_ADMIN)))
            .expectErrorSatisfies(error -> {
                assertThat(error).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
                assertThat(error.getMessage()).contains("invitation email");
            })
            .verify();
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void updateUser_updatesFields() {
        AdminUserDTO dto = adminDto("user", "user@localhost");
        dto.setId(1L);
        dto.setAuthorities(Set.of(AuthoritiesConstants.SALES));
        Authority authority = new Authority();
        authority.setName(AuthoritiesConstants.USER);
        when(userRepository.findById(1L)).thenReturn(Mono.just(user));
        when(userRepository.deleteUserAuthorities(1L)).thenReturn(Mono.empty());
        when(authorityRepository.findById(AuthoritiesConstants.USER)).thenReturn(Mono.just(authority));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(userRepository.saveUserAuthority(anyLong(), anyString())).thenReturn(Mono.empty());

        StepVerifier.create(userService.updateUser(dto).contextWrite(securityContext()))
            .assertNext(updated -> assertThat(updated.getLogin()).isEqualTo("user"))
            .verifyComplete();
    }

    @Test
    void deleteUser_removesUser() {
        when(userRepository.findOneByLogin("user")).thenReturn(Mono.just(user));
        when(userRepository.delete(user)).thenReturn(Mono.empty());

        StepVerifier.create(userService.deleteUser("user").contextWrite(securityContext())).verifyComplete();
    }

    @Test
    void updateUserProfile_updatesCurrentUser() {
        when(userRepository.findOneByLogin("user")).thenReturn(Mono.just(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(userRepository.saveUserAuthority(anyLong(), anyString())).thenReturn(Mono.empty());

        StepVerifier.create(
            userService.updateUser("John", "Doe", "john@localhost", "en", "img").contextWrite(securityContext())
        ).verifyComplete();
    }

    @Test
    void changePassword_updatesWhenCurrentMatches() {
        user.setPassword("encoded");
        when(userRepository.findOneByLogin("user")).thenReturn(Mono.just(user));
        when(passwordEncoder.matches("old", "encoded")).thenReturn(true);
        when(passwordEncoder.encode("new")).thenReturn("new-encoded");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(userRepository.saveUserAuthority(anyLong(), anyString())).thenReturn(Mono.empty());

        StepVerifier.create(userService.changePassword("old", "new").contextWrite(securityContext())).verifyComplete();
    }

    @Test
    void changePassword_failsWhenCurrentDoesNotMatch() {
        user.setPassword("encoded");
        when(userRepository.findOneByLogin("user")).thenReturn(Mono.just(user));
        when(passwordEncoder.matches("wrong", "encoded")).thenReturn(false);

        StepVerifier.create(userService.changePassword("wrong", "new").contextWrite(securityContext()))
            .expectError(InvalidPasswordException.class)
            .verify();
    }

    @Test
    void getAllManagedUsers_mapsToDto() {
        when(userRepository.findAllUsersForAdmin(20, 0)).thenReturn(Flux.just(user));

        StepVerifier.create(userService.getAllManagedUsers(PageRequest.of(0, 20)))
            .assertNext(dto -> assertThat(dto.getLogin()).isEqualTo("user"))
            .verifyComplete();
        verify(userAuthoritiesLoader).attachAuthorities(anyList());
        verify(userAuthoritiesLoader, never()).withAuthorities(any(User.class));
    }

    @Test
    void getAllCustomerUsers_mapsToDto() {
        when(userRepository.findAllCustomerUsers(20, 0)).thenReturn(Flux.just(user));

        StepVerifier.create(userService.getAllCustomerUsers(PageRequest.of(0, 20)))
            .assertNext(dto -> assertThat(dto.getLogin()).isEqualTo("user"))
            .verifyComplete();
        verify(userAuthoritiesLoader).attachAuthorities(anyList());
    }

    @Test
    void countCustomerUsers_delegatesToRepository() {
        when(userRepository.countCustomerUsers()).thenReturn(Mono.just(4L));

        StepVerifier.create(userService.countCustomerUsers()).expectNext(4L).verifyComplete();
    }

    @Test
    void getAllPublicUsers_mapsToDto() {
        when(userRepository.findAllActivatedUsers(20, 0)).thenReturn(Flux.just(user));

        StepVerifier.create(userService.getAllPublicUsers(PageRequest.of(0, 20))).expectNextCount(1).verifyComplete();
    }

    @Test
    void countPublicUsers_delegatesToActivatedCount() {
        when(userRepository.countActivatedUsers()).thenReturn(Mono.just(7L));

        StepVerifier.create(userService.countPublicUsers()).expectNext(7L).verifyComplete();
    }

    @Test
    void countManagedUsers_delegatesToRepository() {
        when(userRepository.count()).thenReturn(Mono.just(3L));

        StepVerifier.create(userService.countManagedUsers()).expectNext(3L).verifyComplete();
    }

    @Test
    void getUserWithAuthoritiesByLogin_delegatesToRepository() {
        when(userRepository.findOneByLogin("user")).thenReturn(Mono.just(user));

        StepVerifier.create(userService.getUserWithAuthoritiesByLogin("user")).expectNext(user).verifyComplete();
    }

    @Test
    void getUserWithAuthorities_usesSecurityContext() {
        when(userRepository.findOneByLogin("user")).thenReturn(Mono.just(user));

        StepVerifier.create(userService.getUserWithAuthorities().contextWrite(securityContext())).expectNext(user).verifyComplete();
    }

    @Test
    void removeNotActivatedUsers_deletesStaleUsers() {
        User stale = sampleUser();
        stale.setActivated(false);
        when(userRepository.findAllByActivatedIsFalseAndActivationKeyIsNotNullAndCreatedDateBefore(any(Instant.class))).thenReturn(
            Flux.just(stale)
        );
        when(userRepository.delete(stale)).thenReturn(Mono.empty());

        userService.removeNotActivatedUsers();

        verify(userRepository).delete(stale);
    }

    @Test
    void removeNotActivatedUsers_deletesStaleStaffInvites() {
        User staleStaff = sampleUser();
        staleStaff.setActivated(false);
        Authority sales = new Authority();
        sales.setName(AuthoritiesConstants.SALES);
        staleStaff.setAuthorities(Set.of(sales));
        when(userAuthoritiesLoader.withAuthorities(staleStaff)).thenReturn(Mono.just(staleStaff));
        when(userRepository.findAllByActivatedIsFalseAndActivationKeyIsNotNullAndCreatedDateBefore(any(Instant.class))).thenAnswer(invocation -> {
            Instant cutoff = invocation.getArgument(0);
            if (cutoff.isBefore(Instant.now().minus(13, ChronoUnit.DAYS))) {
                return Flux.just(staleStaff);
            }
            return Flux.empty();
        });
        when(userRepository.delete(staleStaff)).thenReturn(Mono.empty());

        userService.removeNotActivatedUsers();

        verify(userRepository).delete(staleStaff);
    }

    @Test
    void removeNotActivatedUsers_blocksUntilComplete() {
        when(
            userRepository.findAllByActivatedIsFalseAndActivationKeyIsNotNullAndCreatedDateBefore(any(Instant.class))
        ).thenReturn(Flux.empty());

        userService.removeNotActivatedUsers();

        verify(userRepository, org.mockito.Mockito.times(2))
            .findAllByActivatedIsFalseAndActivationKeyIsNotNullAndCreatedDateBefore(any(Instant.class));
    }

    @Test
    void requestActivationResend_refreshesActivationKeyForInactiveUser() {
        user.setActivated(false);
        when(userRepository.findOneByEmailIgnoreCase("pending@localhost")).thenReturn(Mono.just(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(userRepository.saveUserAuthority(anyLong(), anyString())).thenReturn(Mono.empty());

        StepVerifier.create(userService.requestActivationResend("pending@localhost").contextWrite(securityContext()))
            .assertNext(updated -> assertThat(updated.getActivationKey()).isNotBlank())
            .verifyComplete();
    }

    @Test
    void requestActivationResend_emptyWhenUserMissingOrAlreadyActive() {
        when(userRepository.findOneByEmailIgnoreCase("missing@localhost")).thenReturn(Mono.empty());

        StepVerifier.create(userService.requestActivationResend("missing@localhost")).verifyComplete();

        user.setActivated(true);
        when(userRepository.findOneByEmailIgnoreCase("user@localhost")).thenReturn(Mono.just(user));

        StepVerifier.create(userService.requestActivationResend("user@localhost")).verifyComplete();
    }

    @Test
    void saveUser_persistsAuthoritiesForNewUser() {
        User newUser = sampleUser();
        newUser.setId(null);
        Authority authority = new Authority();
        authority.setName(AuthoritiesConstants.ADMIN);
        newUser.setAuthorities(Set.of(authority));
        when(userRepository.nextUserId()).thenReturn(Mono.just(42L));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(userRepository.saveUserAuthority(42L, AuthoritiesConstants.ADMIN)).thenReturn(Mono.empty());

        StepVerifier.create(userService.saveUser(newUser).contextWrite(securityContext()))
            .assertNext(saved -> assertThat(saved.getId()).isEqualTo(42L))
            .verifyComplete();

        verify(userRepository).saveUserAuthority(42L, AuthoritiesConstants.ADMIN);
    }

    @Test
    void registerUser_emailAlreadyUsedWhenActivated() {
        AdminUserDTO dto = adminDto("other", "user@localhost");
        when(userRepository.findOneByLogin("other")).thenReturn(Mono.empty());
        user.setActivated(true);
        when(userRepository.findOneByEmailIgnoreCase("user@localhost")).thenReturn(Mono.just(user));

        StepVerifier.create(userService.registerUser(dto, "password")).expectError(EmailAlreadyUsedException.class).verify();
    }

    private static AdminUserDTO adminDto(String login, String email) {
        AdminUserDTO dto = new AdminUserDTO();
        dto.setLogin(login);
        dto.setEmail(email);
        dto.setFirstName("First");
        dto.setLastName("Last");
        dto.setLangKey("en");
        return dto;
    }

    private static User sampleUser() {
        User u = new User();
        u.setId(1L);
        u.setLogin("user");
        u.setEmail("user@localhost");
        u.setPassword(RandomStringUtils.insecure().nextAlphanumeric(60));
        u.setActivated(true);
        u.setLangKey("en");
        Set<Authority> authorities = new HashSet<>();
        Authority authority = new Authority();
        authority.setName(AuthoritiesConstants.USER);
        authorities.add(authority);
        u.setAuthorities(authorities);
        return u;
    }

    private static reactor.util.context.Context securityContext() {
        return ReactiveSecurityContextHolder.withAuthentication(new UsernamePasswordAuthenticationToken("user", "pass"));
    }
}
