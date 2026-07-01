package com.miniandmore.gateway.service;

import com.miniandmore.gateway.config.Constants;
import com.miniandmore.gateway.domain.Authority;
import com.miniandmore.gateway.domain.User;
import com.miniandmore.gateway.repository.AuthorityRepository;
import com.miniandmore.gateway.repository.UserAuthoritiesLoader;
import com.miniandmore.gateway.repository.UserRepository;
import com.miniandmore.gateway.security.AuthoritiesConstants;
import com.miniandmore.gateway.service.dto.AdminUserDTO;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.RandomStringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
@Transactional
public class UserService {

    private static final Logger LOG = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final AuthorityRepository authorityRepository;
    private final UserAuthoritiesLoader userAuthoritiesLoader;
    private final PasswordEncoder passwordEncoder;

    public UserService(
        UserRepository userRepository,
        AuthorityRepository authorityRepository,
        UserAuthoritiesLoader userAuthoritiesLoader,
        PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.authorityRepository = authorityRepository;
        this.userAuthoritiesLoader = userAuthoritiesLoader;
        this.passwordEncoder = passwordEncoder;
    }

    public Mono<User> saveUser(User user) {
        return userRepository
            .save(user)
            .flatMap(saved -> {
                Set<Authority> authorities = user.getAuthorities();
                if (authorities == null || authorities.isEmpty()) {
                    return Mono.just(saved);
                }
                saved.setAuthorities(authorities);
                return Mono.just(saved);
            });
    }

    public Mono<User> activateRegistration(String key) {
        LOG.debug("Activating user for activation key {}", key);
        return userRepository
            .findOneByActivationKey(key)
            .flatMap(user -> {
                user.setActivated(true);
                user.setActivationKey(null);
                return saveUser(user);
            });
    }

    public Mono<User> completePasswordReset(String newPassword, String key) {
        LOG.debug("Reset user password for reset key {}", key);
        return userRepository
            .findOneByResetKey(key)
            .filter(user -> user.getResetDate().isAfter(Instant.now().minus(1, ChronoUnit.DAYS)))
            .publishOn(Schedulers.boundedElastic())
            .flatMap(user -> {
                user.setPassword(passwordEncoder.encode(newPassword));
                user.setResetKey(null);
                user.setResetDate(null);
                return saveUser(user);
            });
    }

    public Mono<User> requestPasswordReset(String mail) {
        return userRepository
            .findOneByEmailIgnoreCase(mail)
            .filter(User::isActivated)
            .publishOn(Schedulers.boundedElastic())
            .flatMap(user -> {
                user.setResetKey(generateResetKey());
                user.setResetDate(Instant.now());
                return saveUser(user);
            });
    }

    public Mono<User> requestActivationResend(String mail) {
        return userRepository
            .findOneByEmailIgnoreCase(mail)
            .filter(user -> !user.isActivated())
            .flatMap(user -> {
                user.setActivationKey(generateActivationKey());
                return saveUser(user);
            });
    }

    public Mono<User> registerUser(AdminUserDTO userDTO, String password) {
        return userRepository
            .findOneByLogin(userDTO.getLogin().toLowerCase())
            .flatMap(existingUser -> {
                if (!existingUser.isActivated()) {
                    return userRepository.delete(existingUser);
                } else {
                    return Mono.error(new UsernameAlreadyUsedException());
                }
            })
            .then(userRepository.findOneByEmailIgnoreCase(userDTO.getEmail()))
            .flatMap(existingUser -> {
                if (!existingUser.isActivated()) {
                    return userRepository.delete(existingUser);
                } else {
                    return Mono.error(new EmailAlreadyUsedException());
                }
            })
            .then(
                Mono.defer(() -> {
                    User newUser = new User();
                    newUser.setLogin(userDTO.getLogin().toLowerCase());
                    newUser.setPassword(passwordEncoder.encode(password));
                    newUser.setFirstName(userDTO.getFirstName());
                    newUser.setLastName(userDTO.getLastName());
                    if (userDTO.getEmail() != null) {
                        newUser.setEmail(userDTO.getEmail().toLowerCase());
                    }
                    newUser.setImageUrl(userDTO.getImageUrl());
                    newUser.setLangKey(userDTO.getLangKey() != null ? userDTO.getLangKey() : Constants.DEFAULT_LANGUAGE);
                    newUser.setActivated(false);
                    newUser.setActivationKey(generateActivationKey());
                    Set<Authority> authorities = new HashSet<>();
                    return authorityRepository
                        .findById(AuthoritiesConstants.USER)
                        .doOnNext(authorities::add)
                        .then(Mono.defer(() -> {
                            newUser.setAuthorities(authorities);
                            return saveUser(newUser);
                        }));
                })
            );
    }

    public Mono<User> createUser(AdminUserDTO userDTO) {
        User user = new User();
        user.setLogin(userDTO.getLogin().toLowerCase());
        user.setFirstName(userDTO.getFirstName());
        user.setLastName(userDTO.getLastName());
        if (userDTO.getEmail() != null) {
            user.setEmail(userDTO.getEmail().toLowerCase());
        }
        user.setImageUrl(userDTO.getImageUrl());
        user.setLangKey(userDTO.getLangKey() != null ? userDTO.getLangKey() : Constants.DEFAULT_LANGUAGE);
        user.setPassword(passwordEncoder.encode(generatePassword()));
        user.setResetKey(generateResetKey());
        user.setResetDate(Instant.now());
        user.setActivated(true);
        Set<Authority> authorities = new HashSet<>();
        if (userDTO.getAuthorities() != null) {
            userDTO.getAuthorities().forEach(a -> {
                Authority auth = new Authority();
                auth.setName(a);
                authorities.add(auth);
            });
        }
        user.setAuthorities(authorities);
        return saveUser(user).doOnNext(u -> LOG.debug("Created Information for User: {}", u));
    }

    public Mono<AdminUserDTO> updateUser(AdminUserDTO userDTO) {
        return userRepository
            .findById(userDTO.getId())
            .flatMap(user -> {
                user.setLogin(userDTO.getLogin().toLowerCase());
                user.setFirstName(userDTO.getFirstName());
                user.setLastName(userDTO.getLastName());
                if (userDTO.getEmail() != null) {
                    user.setEmail(userDTO.getEmail().toLowerCase());
                }
                user.setImageUrl(userDTO.getImageUrl());
                user.setActivated(userDTO.isActivated());
                user.setLangKey(userDTO.getLangKey());
                Set<Authority> managedAuthorities = new HashSet<>();
                if (userDTO.getAuthorities() != null) {
                    userDTO.getAuthorities().forEach(a -> {
                        Authority auth = new Authority();
                        auth.setName(a);
                        managedAuthorities.add(auth);
                    });
                }
                user.setAuthorities(managedAuthorities);
                return saveUser(user);
            })
            .map(AdminUserDTO::new);
    }

    public Mono<Void> updateUser(String firstName, String lastName, String email, String langKey, String imageUrl) {
        return com.miniandmore.gateway.security.SecurityUtils.getCurrentUserLogin()
            .flatMap(userRepository::findOneByLogin)
            .flatMap(user -> {
                user.setFirstName(firstName);
                user.setLastName(lastName);
                if (email != null) {
                    user.setEmail(email.toLowerCase());
                }
                user.setLangKey(langKey);
                user.setImageUrl(imageUrl);
                return saveUser(user);
            })
            .then();
    }

    public Mono<Void> deleteUser(String login) {
        return userRepository
            .findOneByLogin(login)
            .flatMap(userRepository::delete)
            .doOnNext(u -> LOG.debug("Deleted User: {}", login));
    }

    public Mono<Void> changePassword(String currentClearTextPassword, String newPassword) {
        return com.miniandmore.gateway.security.SecurityUtils.getCurrentUserLogin()
            .flatMap(userRepository::findOneByLogin)
            .publishOn(Schedulers.boundedElastic())
            .map(user -> {
                if (!passwordEncoder.matches(currentClearTextPassword, user.getPassword())) {
                    throw new InvalidPasswordException();
                }
                user.setPassword(passwordEncoder.encode(newPassword));
                return user;
            })
            .flatMap(this::saveUser)
            .then();
    }

    @Transactional(readOnly = true)
    public Flux<AdminUserDTO> getAllManagedUsers(Pageable pageable) {
        return userRepository.findAllByIdNotNull(pageable).map(AdminUserDTO::new);
    }

    @Transactional(readOnly = true)
    public Flux<com.miniandmore.gateway.service.dto.UserDTO> getAllPublicUsers(Pageable pageable) {
        return userRepository.findAllByActivatedIsTrue(pageable).map(com.miniandmore.gateway.service.dto.UserDTO::new);
    }

    @Transactional(readOnly = true)
    public Mono<Long> countManagedUsers() {
        return userRepository.count();
    }

    @Transactional(readOnly = true)
    public Mono<User> getUserWithAuthoritiesByLogin(String login) {
        return userRepository.findOneByLogin(login).flatMap(userAuthoritiesLoader::withAuthorities);
    }

    @Transactional(readOnly = true)
    public Mono<User> getUserWithAuthorities() {
        return com.miniandmore.gateway.security.SecurityUtils.getCurrentUserLogin()
            .flatMap(userRepository::findOneByLogin)
            .flatMap(userAuthoritiesLoader::withAuthorities);
    }

    @Scheduled(cron = "0 0 1 * * ?")
    public void removeNotActivatedUsers() {
        removeNotActivatedUsersReactively().blockLast();
    }

    private Flux<User> removeNotActivatedUsersReactively() {
        return userRepository
            .findAllByActivatedIsFalseAndActivationKeyIsNotNullAndCreatedDateBefore(Instant.now().minus(3, ChronoUnit.DAYS))
            .flatMap(user -> userRepository.delete(user).thenReturn(user))
            .doOnNext(user -> LOG.debug("Deleted User: {}", user));
    }

    public static String generatePassword() {
        return RandomStringUtils.randomAlphanumeric(20);
    }

    private static String generateActivationKey() {
        return RandomStringUtils.randomNumeric(20);
    }

    private static String generateResetKey() {
        return RandomStringUtils.randomNumeric(20);
    }
}
