package com.miniandmore.gateway.repository;

import com.miniandmore.gateway.domain.Authority;
import com.miniandmore.gateway.domain.User;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
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
        return attachAuthorities(List.of(user)).singleOrEmpty();
    }

    /**
     * Load roles for a page of users in one query. Per-user lookups overflow the R2DBC
     * request queue when admin lists hundreds of accounts.
     */
    public Flux<User> attachAuthorities(List<User> users) {
        if (users == null || users.isEmpty()) {
            return Flux.empty();
        }
        Long[] ids = users
            .stream()
            .map(User::getId)
            .filter(Objects::nonNull)
            .distinct()
            .toArray(Long[]::new);
        if (ids.length == 0) {
            return Flux.fromIterable(users);
        }
        return userRepository
            .findAuthorityNamesByUserIds(ids)
            .collectMultimap(UserAuthorityAssignment::userId, UserAuthorityAssignment::authorityName)
            .map(byUser -> applyAssignments(users, byUser))
            .flatMapMany(Flux::fromIterable);
    }

    List<User> applyAssignments(List<User> users, Map<Long, Collection<String>> byUser) {
        for (User user : users) {
            if (user.getId() == null) {
                continue;
            }
            Collection<String> names = byUser.get(user.getId());
            user.setAuthorities(
                names == null ? Set.of() : names.stream().filter(Objects::nonNull).map(this::toAuthority).collect(Collectors.toSet())
            );
        }
        return users;
    }

    private Authority toAuthority(String name) {
        Authority authority = new Authority();
        authority.setName(name);
        return authority.setIsPersisted();
    }
}
