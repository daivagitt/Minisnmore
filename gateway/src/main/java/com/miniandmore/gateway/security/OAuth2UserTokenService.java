package com.miniandmore.gateway.security;

import com.miniandmore.gateway.config.Constants;
import com.miniandmore.gateway.domain.Authority;
import com.miniandmore.gateway.domain.User;
import com.miniandmore.gateway.repository.AuthorityRepository;
import com.miniandmore.gateway.repository.UserRepository;
import com.miniandmore.gateway.service.UserService;
import java.util.*;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class OAuth2UserTokenService {

    private static final Logger LOG = LoggerFactory.getLogger(OAuth2UserTokenService.class);

    private final UserRepository userRepository;
    private final UserService userService;
    private final AuthorityRepository authorityRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;

    public OAuth2UserTokenService(
        UserRepository userRepository,
        UserService userService,
        AuthorityRepository authorityRepository,
        PasswordEncoder passwordEncoder,
        JwtTokenService jwtTokenService
    ) {
        this.userRepository = userRepository;
        this.userService = userService;
        this.authorityRepository = authorityRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
    }

    public Mono<String> tokenFor(OAuth2User oauthUser) {
        return resolveEmail(oauthUser)
            .map(String::toLowerCase)
            .switchIfEmpty(Mono.error(new IllegalArgumentException("OAuth provider did not return an email address")))
            .flatMap(email ->
                loadOrCreateUser(email, oauthUser)
                    .flatMap(user -> {
                        boolean needsActivation = !user.isActivated();
                        if (needsActivation) {
                            user.setActivated(true);
                        }
                        return ensureUserAuthority(user).flatMap(resolved -> {
                            if (resolved.getId() != null && resolved.isActivated() && hasUserRole(resolved)) {
                                LOG.info("OAuth sign-in reusing existing account for {}", email);
                                return Mono.just(resolved);
                            }
                            return persistUser(resolved);
                        });
                    })
                    .map(user -> {
                        String token = jwtTokenService.createTokenForOAuthUser(user, true);
                        LOG.info("OAuth sign-in succeeded for " + email);
                        return token;
                    })
            );
    }

    private Mono<User> loadOrCreateUser(String email, OAuth2User oauthUser) {
        return removeInactiveDuplicate(email)
            .then(userRepository.findOneWithAuthoritiesByEmailIgnoreCaseForOAuth(email))
            .switchIfEmpty(Mono.defer(() -> userRepository.findOneWithAuthoritiesByLoginForOAuth(loginForEmail(email))))
            .switchIfEmpty(Mono.defer(() -> createAndSaveUser(email, oauthUser)))
            .onErrorResume(
                DataIntegrityErrors::isDuplicateUserIdentity,
                ex -> {
                    LOG.warn("OAuth account race for {}, loading existing user instead", email);
                    return userRepository
                        .findOneWithAuthoritiesByEmailIgnoreCaseForOAuth(email)
                        .switchIfEmpty(userRepository.findOneWithAuthoritiesByLoginForOAuth(loginForEmail(email)));
                }
            );
    }

    private Mono<Void> removeInactiveDuplicate(String email) {
        String login = loginForEmail(email);
        return userRepository.deleteInactiveByLogin(login).then(userRepository.deleteInactiveByEmail(email));
    }

    private Mono<User> createAndSaveUser(String email, OAuth2User oauthUser) {
        return createUser(email, oauthUser).flatMap(userService::saveUser);
    }

    private Mono<User> createUser(String email, OAuth2User oauthUser) {
        User user = new User();
        user.setLogin(loginForEmail(email));
        user.setEmail(email);
        user.setFirstName(
            resolveGivenName(oauthUser).orElseGet(() -> firstName(resolveDisplayName(oauthUser).orElse("")))
        );
        user.setLastName(
            resolveFamilyName(oauthUser).orElseGet(() -> lastName(resolveDisplayName(oauthUser).orElse("")))
        );
        user.setImageUrl(resolvePictureUrl(oauthUser).orElse(null));
        user.setLangKey(Constants.DEFAULT_LANGUAGE);
        user.setActivated(true);
        user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
        user.setCreatedBy(Constants.SYSTEM);
        user.setLastModifiedBy(Constants.SYSTEM);
        return ensureUserAuthority(user);
    }

    private Mono<User> ensureUserAuthority(User user) {
        if (user.getAuthorities() == null) {
            user.setAuthorities(new HashSet<>());
        }
        if (hasUserRole(user)) {
            return Mono.just(user);
        }
        return authorityRepository.findById(AuthoritiesConstants.USER).doOnNext(user.getAuthorities()::add).thenReturn(user);
    }

    private static boolean hasUserRole(User user) {
        return user.getAuthorities().stream().map(Authority::getName).anyMatch(AuthoritiesConstants.USER::equals);
    }

    private Mono<User> persistUser(User user) {
        return userService.saveUser(user);
    }

    private Mono<String> resolveEmail(OAuth2User oauthUser) {
        return Mono.justOrEmpty(attribute(oauthUser, "email")).switchIfEmpty(Mono.defer(() -> facebookIdEmail(oauthUser)));
    }

    private Mono<String> facebookIdEmail(OAuth2User oauthUser) {
        if (attribute(oauthUser, "sub").isPresent()) {
            return Mono.empty();
        }
        return Mono.justOrEmpty(attribute(oauthUser, "id").map(id -> "facebook." + id + "@oauth.miniandmore.local"));
    }

    private Optional<String> resolveGivenName(OAuth2User oauthUser) {
        return attribute(oauthUser, "given_name")
            .or(() -> attribute(oauthUser, "first_name"))
            .or(() -> appleNamePart(oauthUser, "firstName"));
    }

    private Optional<String> resolveFamilyName(OAuth2User oauthUser) {
        return attribute(oauthUser, "family_name")
            .or(() -> attribute(oauthUser, "last_name"))
            .or(() -> appleNamePart(oauthUser, "lastName"));
    }

    private Optional<String> resolveDisplayName(OAuth2User oauthUser) {
        return attribute(oauthUser, "name");
    }

    private Optional<String> resolvePictureUrl(OAuth2User oauthUser) {
        return attribute(oauthUser, "picture").or(() -> facebookPictureUrl(oauthUser.getAttribute("picture")));
    }

    private Optional<String> appleNamePart(OAuth2User oauthUser, String key) {
        var name = oauthUser.getAttribute("name");
        if (!(name instanceof Map<?, ?> map)) {
            return Optional.empty();
        }
        var value = map.get(key);
        return value instanceof String text && !text.isBlank() ? Optional.of(text.trim()) : Optional.empty();
    }

    private Optional<String> facebookPictureUrl(Object picture) {
        if (picture instanceof String url && !url.isBlank()) {
            return Optional.of(url.trim());
        }
        if (!(picture instanceof Map<?, ?> map)) {
            return Optional.empty();
        }
        var data = map.get("data");
        if (!(data instanceof Map<?, ?> dataMap)) {
            return Optional.empty();
        }
        var url = dataMap.get("url");
        return url instanceof String text && !text.isBlank() ? Optional.of(text.trim()) : Optional.empty();
    }

    private Optional<String> attribute(OAuth2User oauthUser, String key) {
        var value = oauthUser.getAttribute(key);
        return value instanceof String text && !text.isBlank() ? Optional.of(text.trim()) : Optional.empty();
    }

    private String firstName(String name) {
        var parts = name.trim().split("\\s+");
        return parts.length > 0 && !parts[0].isBlank() ? parts[0] : "";
    }

    private String lastName(String name) {
        var parts = name.trim().split("\\s+");
        return parts.length > 1 ? String.join(" ", Arrays.copyOfRange(parts, 1, parts.length)) : "";
    }

    static String loginForEmail(String email) {
        String normalized = email.toLowerCase(Locale.ENGLISH).trim();
        if (normalized.length() <= 50) {
            return normalized;
        }
        return normalized.substring(0, 50);
    }
}
