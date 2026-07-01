package com.miniandmore.gateway.repository;

import com.miniandmore.gateway.domain.Authority;
import com.miniandmore.gateway.domain.User;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Loads {@link User#getAuthorities()} from {@code jhi_user_authority} (R2DBC does not map the join automatically).
 */
@Component
public class UserAuthoritiesLoader {

    private final UserRepository userRepository;

    public UserAuthoritiesLoader(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Mono<User> withAuthorities(User user) {
        if (user == null || user.getId() == null) {
            return Mono.empty();
        }
        return userRepository
            .findAuthorityNamesByUserId(user.getId())
            .map(this::toAuthority)
            .collect(Collectors.toSet())
            .map(authorities -> {
                user.setAuthorities(authorities);
                return user;
            });
    }

    private Authority toAuthority(String name) {
        Authority authority = new Authority();
        authority.setName(name);
        return authority.setIsPersisted();
    }
}
