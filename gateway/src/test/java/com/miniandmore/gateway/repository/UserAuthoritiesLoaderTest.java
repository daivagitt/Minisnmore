package com.miniandmore.gateway.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.miniandmore.gateway.domain.User;
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
        when(userRepository.findAuthorityNamesByUserId(42L)).thenReturn(Flux.just("ROLE_USER", "ROLE_ADMIN"));

        StepVerifier
            .create(loader.withAuthorities(user))
            .assertNext(loaded -> {
                assertThat(loaded.getAuthorities()).extracting("name").containsExactlyInAnyOrder("ROLE_USER", "ROLE_ADMIN");
            })
            .verifyComplete();
    }
}
