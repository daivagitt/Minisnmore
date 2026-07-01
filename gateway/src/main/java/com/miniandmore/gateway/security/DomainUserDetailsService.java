package com.miniandmore.gateway.security;

import com.miniandmore.gateway.domain.Authority;
import com.miniandmore.gateway.domain.User;
import com.miniandmore.gateway.repository.UserAuthoritiesLoader;
import com.miniandmore.gateway.repository.UserRepository;
import java.util.Collection;
import java.util.Locale;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

/**
 * Authenticate a user from the database.
 */
@Component("userDetailsService")
public class DomainUserDetailsService implements ReactiveUserDetailsService {

    private static final Logger LOG = LoggerFactory.getLogger(DomainUserDetailsService.class);

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$", Pattern.CASE_INSENSITIVE);

    private final UserRepository userRepository;
    private final UserAuthoritiesLoader userAuthoritiesLoader;

    public DomainUserDetailsService(UserRepository userRepository, UserAuthoritiesLoader userAuthoritiesLoader) {
        this.userRepository = userRepository;
        this.userAuthoritiesLoader = userAuthoritiesLoader;
    }

    @Override
    @Transactional(readOnly = true)
    public Mono<UserDetails> findByUsername(final String login) {
        LOG.debug("Authenticating {}", login);

        if (looksLikeEmail(login)) {
            return userRepository
                .findOneWithAuthoritiesByEmailIgnoreCase(login)
                .switchIfEmpty(Mono.error(new UsernameNotFoundException("User with email " + login + " was not found in the database")))
                .flatMap(user -> withPasswordHash(user, login));
        }

        String lowercaseLogin = login.toLowerCase(Locale.ENGLISH);
        return userRepository
            .findOneWithAuthoritiesByLogin(lowercaseLogin)
            .switchIfEmpty(Mono.error(new UsernameNotFoundException("User " + lowercaseLogin + " was not found in the database")))
            .flatMap(user -> withPasswordHash(user, lowercaseLogin));
    }

    private static boolean looksLikeEmail(String login) {
        return login != null && EMAIL_PATTERN.matcher(login).matches();
    }

    private Mono<UserDetails> withPasswordHash(User user, String lowercaseLogin) {
        return userAuthoritiesLoader
            .withAuthorities(user)
            .flatMap(
                loaded ->
                    userRepository
                        .findPasswordHashByUserId(loaded.getId())
                        .map(password -> {
                            loaded.setPassword(password);
                            return createSpringSecurityUser(lowercaseLogin, loaded);
                        })
            );
    }

    private org.springframework.security.core.userdetails.User createSpringSecurityUser(String lowercaseLogin, User user) {
        if (!user.isActivated()) {
            throw new UserNotActivatedException("User " + lowercaseLogin + " was not activated");
        }
        return UserWithId.fromUser(user);
    }

    public static class UserWithId extends org.springframework.security.core.userdetails.User {

        private final Long id;

        public UserWithId(String login, String password, Collection<? extends GrantedAuthority> authorities, Long id) {
            super(login, password, authorities);
            this.id = id;
        }

        public Long getId() {
            return id;
        }

        @Override
        public boolean equals(Object obj) {
            return super.equals(obj);
        }

        @Override
        public int hashCode() {
            return super.hashCode();
        }

        public static UserWithId fromUser(User user) {
            return new UserWithId(
                user.getLogin(),
                user.getPassword(),
                user.getAuthorities().stream().map(Authority::getName).map(SimpleGrantedAuthority::new).toList(),
                user.getId()
            );
        }
    }
}
