package com.miniandmore.gateway.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.miniandmore.gateway.domain.User;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class UserAuthoritiesLoaderTest {

    @Mock
    private UserRepository userRepository;

    private UserAuthoritiesLoader loader;

    @BeforeEach
    void setUp() {
        loader = new UserAuthoritiesLoader(userRepository);
    }

    @Test
    void withAuthorities_nullUser_returnsEmpty() {
        StepVerifier.create(loader.withAuthorities(null)).verifyComplete();
    }

    @Test
    void withAuthorities_nullId_returnsEmpty() {
        User user = new User();
        user.setLogin("user1");

        StepVerifier.create(loader.withAuthorities(user)).verifyComplete();
    }

    @Test
    void withAuthorities_loadsAuthorityNames() {
        User user = new User();
        user.setId(42L);
        user.setLogin("user1");
        when(userRepository.findAuthorityNamesByUserIds(any()))
            .thenReturn(
                Flux.just(new UserAuthorityAssignment(42L, "ROLE_USER"), new UserAuthorityAssignment(42L, "ROLE_ADMIN"))
            );

        StepVerifier
            .create(loader.withAuthorities(user))
            .assertNext(loaded -> {
                assertThat(loaded.getAuthorities()).extracting("name").containsExactlyInAnyOrder("ROLE_USER", "ROLE_ADMIN");
            })
            .verifyComplete();
    }

    @Test
    void attachAuthorities_emptyList_returnsEmpty() {
        StepVerifier.create(loader.attachAuthorities(List.of())).verifyComplete();
    }

    @Test
    void attachAuthorities_loadsRolesInOneQuery() {
        User shopper = new User();
        shopper.setId(1L);
        shopper.setLogin("shopper");
        User staff = new User();
        staff.setId(2L);
        staff.setLogin("staff");
        when(userRepository.findAuthorityNamesByUserIds(any()))
            .thenReturn(
                Flux.just(
                    new UserAuthorityAssignment(1L, "ROLE_USER"),
                    new UserAuthorityAssignment(2L, "ROLE_ADMIN"),
                    new UserAuthorityAssignment(2L, "ROLE_USER")
                )
            );

        StepVerifier
            .create(loader.attachAuthorities(List.of(shopper, staff)))
            .assertNext(loaded -> assertThat(loaded.getAuthorities()).extracting("name").containsExactly("ROLE_USER"))
            .assertNext(loaded ->
                assertThat(loaded.getAuthorities()).extracting("name").containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_USER")
            )
            .verifyComplete();
    }

    @Test
    void attachAuthorities_usersWithoutRolesGetEmptySet() {
        User shopper = new User();
        shopper.setId(9L);
        shopper.setLogin("guest");
        when(userRepository.findAuthorityNamesByUserIds(any())).thenReturn(Flux.empty());

        StepVerifier
            .create(loader.attachAuthorities(List.of(shopper)))
            .assertNext(loaded -> assertThat(loaded.getAuthorities()).isEmpty())
            .verifyComplete();
    }
}
