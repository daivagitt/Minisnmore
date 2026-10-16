package com.miniandmore.gateway.service;

import com.miniandmore.gateway.config.Constants;
import com.miniandmore.gateway.domain.Authority;
import com.miniandmore.gateway.domain.User;
import com.miniandmore.gateway.repository.AuthorityRepository;
import com.miniandmore.gateway.repository.UserAuthoritiesLoader;
import com.miniandmore.gateway.repository.UserRepository;
import com.miniandmore.gateway.security.AuthoritiesConstants;
import com.miniandmore.gateway.security.StaffRoles;
import com.miniandmore.gateway.service.dto.AdminUserDTO;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Set;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
@Transactional
public class UserService {

    private static final Logger LOG = LoggerFactory.getLogger(UserService.class);
    private static final String LOG_DELETED_USER = "Deleted User: {}";

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
        Mono<User> withId = user.getId() == null
            ? userRepository.nextUserId().map(id -> {
                user.setId(id);
                return user;
            })
            : Mono.just(user);
        return withId.flatMap(toSave ->
            userRepository
                .save(toSave)
                .flatMap(saved -> {
                    Set<Authority> authorities = toSave.getAuthorities();
                    if (authorities == null || authorities.isEmpty()) {
                        return Mono.just(saved);
                    }
                    saved.setAuthorities(authorities);
                    return userRepository
                        .deleteUserAuthorities(saved.getId())
                        .thenMany(
                            Flux.fromIterable(authorities).flatMap(authority ->
                                userRepository.saveUserAuthority(saved.getId(), authority.getName())
                            )
                        )
                        .then(Mono.just(saved));
                })
        );
    }

    public Mono<User> activateRegistration(String key) {
        LOG.debug("Activating user for activation key {}", key);
        return userRepository
            .findOneByActivationKey(key)
            .flatMap(user -> {
                user.setIsPersisted();
                if (user.isActivated()) {
                    return Mono.just(user);
                }
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
            // Handle OAuth linked accounts which fallback to login when email does not match directly
            .switchIfEmpty(Mono.defer(() -> userRepository.findOneByLogin(mail.toLowerCase().trim())))
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
                    return userRepository
                        .deleteUserAuthorities(existingUser.getId())
                        .then(userRepository.delete(existingUser));
                } else {
                    return Mono.error(new UsernameAlreadyUsedException());
                }
            })
            .then(userRepository.findOneByEmailIgnoreCase(userDTO.getEmail()))
            .flatMap(existingUser -> {
                if (!existingUser.isActivated()) {
                    return userRepository
                        .deleteUserAuthorities(existingUser.getId())
                        .then(userRepository.delete(existingUser));
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
                    newUser.setSubscribed(Boolean.TRUE.equals(userDTO.getSubscribed()));
                    newUser.setActivationKey(generateActivationKey());
                    newUser.setCreatedBy(Constants.SYSTEM);
                    newUser.setLastModifiedBy(Constants.SYSTEM);
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
        String primaryRole = StaffRoles.primaryRole(userDTO.getAuthorities());
        Set<String> canonical = StaffRoles.canonicalAuthorities(primaryRole);
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
        user.setActivationKey(generateActivationKey());
        user.setActivated(false);
        user.setCreatedBy(Constants.SYSTEM);
        user.setLastModifiedBy(Constants.SYSTEM);
        user.setAuthorities(StaffRoles.toAuthorities(canonical));
        return saveUser(user).doOnNext(u -> LOG.debug("Invited staff user: {}", u));
    }

    public Mono<User> createStaffUser(AdminUserDTO userDTO, Set<String> actorAuthorities) {
        StaffRoles.requireAssignable(actorAuthorities, userDTO.getAuthorities());
        return createUser(userDTO);
    }

    public Mono<User> completeInvite(String newPassword, String key) {
        LOG.debug("Completing staff invite for activation key {}", key);
        return userRepository
            .findOneByActivationKey(key)
            .flatMap(userAuthoritiesLoader::withAuthorities)
            .filter(user -> !user.isActivated())
            .filter(user -> StaffRoles.isStaff(StaffRoles.names(user)))
            .publishOn(Schedulers.boundedElastic())
            .flatMap(user -> {
                user.setPassword(passwordEncoder.encode(newPassword));
                user.setActivated(true);
                user.setActivationKey(null);
                return saveUser(user);
            });
    }

    public Mono<AdminUserDTO> updateUser(AdminUserDTO userDTO) {
        return userRepository
            .findById(userDTO.getId())
            .flatMap(user -> applyUserUpdate(user, userDTO))
            .map(AdminUserDTO::new);
    }

    public Mono<AdminUserDTO> updateStaffUser(AdminUserDTO userDTO, Set<String> actorAuthorities) {
        return userRepository
            .findById(userDTO.getId())
            .flatMap(userAuthoritiesLoader::withAuthorities)
            .flatMap(existing ->
                protectLastSuperAdmin(existing, userDTO).then(
                    Mono.defer(() -> {
                        StaffRoles.requireCanManage(actorAuthorities, StaffRoles.names(existing));
                        StaffRoles.requireAssignable(actorAuthorities, userDTO.getAuthorities());
                        return applyUserUpdate(existing, userDTO);
                    })
                )
            )
            .map(AdminUserDTO::new);
    }

    private Mono<User> applyUserUpdate(User user, AdminUserDTO userDTO) {
        boolean activatingPendingInvite =
            userDTO.isActivated() && !user.isActivated() && StringUtils.isNotBlank(user.getActivationKey());
        if (activatingPendingInvite) {
            return Mono.error(
                new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "This user must activate from the invitation email and set a password"
                )
            );
        }
        String primaryRole = StaffRoles.primaryRole(userDTO.getAuthorities());
        Set<String> canonical = primaryRole == null ? Set.of() : StaffRoles.canonicalAuthorities(primaryRole);
        user.setLogin(userDTO.getLogin().toLowerCase());
        user.setFirstName(userDTO.getFirstName());
        user.setLastName(userDTO.getLastName());
        if (userDTO.getEmail() != null) {
            user.setEmail(userDTO.getEmail().toLowerCase());
        }
        user.setImageUrl(userDTO.getImageUrl());
        user.setActivated(userDTO.isActivated());
        user.setLangKey(userDTO.getLangKey());
        if (userDTO.getSubscribed() != null) {
            user.setSubscribed(userDTO.getSubscribed());
        }
        user.setAuthorities(StaffRoles.toAuthorities(canonical));
        return saveUser(user);
    }

    private Mono<Void> protectLastSuperAdmin(User existing, AdminUserDTO next) {
        if (!StaffRoles.names(existing).contains(AuthoritiesConstants.SUPER_ADMIN)) {
            return Mono.empty();
        }
        boolean removingSuperAdmin = !AuthoritiesConstants.SUPER_ADMIN.equals(StaffRoles.primaryRole(next.getAuthorities()));
        boolean deactivating = !next.isActivated();
        if (!removingSuperAdmin && !deactivating) {
            return Mono.empty();
        }
        return userRepository
            .countUsersWithAuthority(AuthoritiesConstants.SUPER_ADMIN)
            .flatMap(count -> {
                if (count != null && count <= 1) {
                    return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot remove the last Super Admin"));
                }
                return Mono.empty();
            });
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
            .doOnNext(u -> LOG.debug(LOG_DELETED_USER, login));
    }

    public Mono<Void> deleteStaffUser(String login, Set<String> actorAuthorities) {
        return userRepository
            .findOneByLogin(login)
            .flatMap(userAuthoritiesLoader::withAuthorities)
            .flatMap(existing -> {
                StaffRoles.requireCanManage(actorAuthorities, StaffRoles.names(existing));
                AdminUserDTO next = new AdminUserDTO(existing);
                next.setActivated(false);
                next.setAuthorities(Set.of());
                return protectLastSuperAdmin(existing, next).then(userRepository.delete(existing));
            })
            .doOnNext(u -> LOG.debug(LOG_DELETED_USER, login));
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
        long limit = pageable.getPageSize();
        long offset = pageable.getOffset();
        return userRepository
            .findAllUsersForAdmin(limit, offset)
            .collectList()
            .flatMapMany(userAuthoritiesLoader::attachAuthorities)
            .map(AdminUserDTO::new);
    }

    @Transactional(readOnly = true)
    public Flux<AdminUserDTO> getAllCustomerUsers(Pageable pageable) {
        long limit = pageable.getPageSize();
        long offset = pageable.getOffset();
        return userRepository
            .findAllCustomerUsers(limit, offset)
            .collectList()
            .flatMapMany(userAuthoritiesLoader::attachAuthorities)
            .map(AdminUserDTO::new);
    }

    @Transactional(readOnly = true)
    public Flux<com.miniandmore.gateway.service.dto.UserDTO> getAllPublicUsers(Pageable pageable) {
        return userRepository
            .findAllActivatedUsers(pageable.getPageSize(), pageable.getOffset())
            .map(com.miniandmore.gateway.service.dto.UserDTO::new);
    }

    @Transactional(readOnly = true)
    public Mono<Long> countPublicUsers() {
        return userRepository.countActivatedUsers();
    }

    @Transactional(readOnly = true)
    public Mono<Long> countManagedUsers() {
        return userRepository.count();
    }

    @Transactional(readOnly = true)
    public Mono<Long> countCustomerUsers() {
        return userRepository.countCustomerUsers();
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

    private static final long SHOPPER_INVITE_TTL_DAYS = 3;
    private static final long STAFF_INVITE_TTL_DAYS = 14;

    private Flux<User> removeNotActivatedUsersReactively() {
        Flux<User> staleShoppers = userRepository
            .findAllByActivatedIsFalseAndActivationKeyIsNotNullAndCreatedDateBefore(
                Instant.now().minus(SHOPPER_INVITE_TTL_DAYS, ChronoUnit.DAYS)
            )
            .flatMap(userAuthoritiesLoader::withAuthorities)
            .filter(user -> !StaffRoles.isStaff(StaffRoles.names(user)));

        Flux<User> staleStaffInvites = userRepository
            .findAllByActivatedIsFalseAndActivationKeyIsNotNullAndCreatedDateBefore(
                Instant.now().minus(STAFF_INVITE_TTL_DAYS, ChronoUnit.DAYS)
            )
            .flatMap(userAuthoritiesLoader::withAuthorities)
            .filter(user -> StaffRoles.isStaff(StaffRoles.names(user)));

        return Flux.merge(staleShoppers, staleStaffInvites)
            .flatMap(user -> userRepository.delete(user).thenReturn(user))
            .doOnNext(user -> LOG.debug(LOG_DELETED_USER, user));
    }

    public static String generatePassword() {
        return RandomStringUtils.randomAlphanumeric(20);
    }

    private static String generateActivationKey() {
        // Must stay a non-JSON-number string: TanStack parseSearch JSON-parses pure digits.
        return RandomStringUtils.randomAlphanumeric(20);
    }

    private static String generateResetKey() {
        return RandomStringUtils.randomAlphanumeric(20);
    }
}
