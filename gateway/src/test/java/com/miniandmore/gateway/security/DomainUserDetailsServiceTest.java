package com.miniandmore.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.miniandmore.gateway.domain.Authority;
import com.miniandmore.gateway.domain.User;
import com.miniandmore.gateway.repository.UserAuthoritiesLoader;
import com.miniandmore.gateway.repository.UserRepository;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class DomainUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserAuthoritiesLoader userAuthoritiesLoader;

    private DomainUserDetailsService service;

    @BeforeEach
    void setUp() {
        service = new DomainUserDetailsService(userRepository, userAuthoritiesLoader);
    }

    @Test
    void findByUsername_login_resolvesPasswordHash() {
        User user = activeUser("user1", "user1@localhost");
        when(userRepository.findOneByLogin("user1")).thenReturn(Mono.just(user));
        when(userAuthoritiesLoader.withAuthorities(user)).thenReturn(Mono.just(user));
        when(userRepository.findPasswordHashByUserId(user.getId())).thenReturn(Mono.just("hash"));

        StepVerifier
            .create(service.findByUsername("User1"))
            .assertNext(details -> {
                assertThat(details.getUsername()).isEqualTo("user1");
                assertThat(details.getPassword()).isEqualTo("hash");
                assertThat(((DomainUserDetailsService.UserWithId) details).getId()).isEqualTo(user.getId());
            })
            .verifyComplete();
    }

    @Test
    void findByUsername_email_lookup() {
        User user = activeUser("user1", "user1@localhost");
        when(userRepository.findOneByEmailIgnoreCase("user1@example.com")).thenReturn(Mono.just(user));
        when(userAuthoritiesLoader.withAuthorities(user)).thenReturn(Mono.just(user));
        when(userRepository.findPasswordHashByUserId(user.getId())).thenReturn(Mono.just("hash"));

        StepVerifier.create(service.findByUsername("user1@example.com")).expectNextCount(1).verifyComplete();
    }

    @Test
    void findByUsername_notActivated_throws() {
        User user = activeUser("user1", "user1@localhost");
        user.setActivated(false);
        when(userRepository.findOneByLogin("user1")).thenReturn(Mono.just(user));
        when(userAuthoritiesLoader.withAuthorities(user)).thenReturn(Mono.just(user));
        when(userRepository.findPasswordHashByUserId(user.getId())).thenReturn(Mono.just("hash"));

        StepVerifier.create(service.findByUsername("user1")).expectError(UserNotActivatedException.class).verify();
    }

    @Test
    void findByUsername_missingUser_errors() {
        when(userRepository.findOneByLogin("missing")).thenReturn(Mono.empty());

        StepVerifier.create(service.findByUsername("missing")).expectError().verify();
    }

    @Test
    void userWithId_equalsUsesSuper() {
    DomainUserDetailsService.UserWithId left = new DomainUserDetailsService.UserWithId(
        "user1",
        "hash",
        List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER")),
        1L
    );
    DomainUserDetailsService.UserWithId right = new DomainUserDetailsService.UserWithId(
        "user1",
        "hash",
        List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER")),
        1L
    );
    assertThat(left).isEqualTo(right);
    assertThat(left.hashCode()).isEqualTo(right.hashCode());
  }

    private static User activeUser(String login, String email) {
        User user = new User();
        user.setId(1L);
        user.setLogin(login);
        user.setEmail(email);
        user.setActivated(true);
        Authority authority = new Authority();
        authority.setName("ROLE_USER");
        user.setAuthorities(Set.of(authority));
        return user;
    }
}
