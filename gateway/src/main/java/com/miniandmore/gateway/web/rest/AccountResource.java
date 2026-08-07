package com.miniandmore.gateway.web.rest;

import com.miniandmore.gateway.config.ApplicationProperties;
import com.miniandmore.gateway.repository.UserRepository;
import com.miniandmore.gateway.security.SecurityUtils;
import com.miniandmore.gateway.service.MailService;
import com.miniandmore.gateway.service.UserService;
import com.miniandmore.gateway.service.dto.AdminUserDTO;
import com.miniandmore.gateway.service.dto.PasswordChangeDTO;
import com.miniandmore.gateway.web.rest.errors.*;
import com.miniandmore.gateway.web.rest.vm.KeyAndPasswordVM;
import com.miniandmore.gateway.web.rest.vm.ManagedUserVM;
import jakarta.validation.Valid;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * REST controller for managing the current user's account.
 */
@RestController
@RequestMapping("/api")
public class AccountResource {

    private static class AccountResourceException extends RuntimeException {

        private AccountResourceException(String message) {
            super(message);
        }
    }

    private static final Logger LOG = LoggerFactory.getLogger(AccountResource.class);

    private final UserRepository userRepository;

    private final UserService userService;

    private final MailService mailService;

    private final ApplicationProperties applicationProperties;

    public AccountResource(
        UserRepository userRepository,
        UserService userService,
        MailService mailService,
        ApplicationProperties applicationProperties
    ) {
        this.userRepository = userRepository;
        this.userService = userService;
        this.mailService = mailService;
        this.applicationProperties = applicationProperties;
    }

    /**
     * {@code POST  /register} : register the user.
     *
     * @param managedUserVM the managed user View Model.
     * @throws InvalidPasswordException {@code 400 (Bad Request)} if the password is incorrect.
     * @throws EmailAlreadyUsedException {@code 400 (Bad Request)} if the email is already used.
     * @throws LoginAlreadyUsedException {@code 400 (Bad Request)} if the login is already used.
     */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Void> registerAccount(@Valid @RequestBody ManagedUserVM managedUserVM) {
        if (isPasswordLengthInvalid(managedUserVM.getPassword())) {
            throw new InvalidPasswordException();
        }
        return userService
            .registerUser(managedUserVM, managedUserVM.getPassword())
            .flatMap(user -> {
                if (applicationProperties.isRegisterAutoActivate()) {
                    return userService
                        .activateRegistration(user.getActivationKey())
                        .doOnNext(activated -> LOG.info("Auto-activated user {}", activated.getLogin()));
                }
                return Mono.fromRunnable(() -> mailService.sendActivationEmail(user)).subscribeOn(Schedulers.boundedElastic());
            })
            .then();
    }

    /**
     * {@code GET  /activate} : activate the registered user.
     *
     * @param key the activation key.
     * @throws RuntimeException {@code 500 (Internal Server Error)} if the user couldn't be activated.
     */
    @GetMapping("/activate")
    public Mono<Void> activateAccount(@RequestParam(value = "key") String key) {
        return userService
            .activateRegistration(key)
            .switchIfEmpty(
                Mono.error(
                    new BadRequestAlertException(
                        "This activation link is invalid or has already been used.",
                        "userManagement",
                        "invalidactivationkey"
                    )
                )
            )
            .then();
    }

    /**
     * {@code GET  /account} : get the current user.
     *
     * @return the current user.
     * @throws RuntimeException {@code 500 (Internal Server Error)} if the user couldn't be returned.
     */
    @GetMapping("/account")
    public Mono<AdminUserDTO> getAccount() {
        return userService
            .getUserWithAuthorities()
            .map(AdminUserDTO::new)
            .switchIfEmpty(Mono.error(new AccountResourceException("User could not be found")));
    }

    /**
     * {@code POST  /account} : update the current user information.
     *
     * @param userDTO the current user information.
     * @throws EmailAlreadyUsedException {@code 400 (Bad Request)} if the email is already used.
     * @throws RuntimeException {@code 500 (Internal Server Error)} if the user login wasn't found.
     */
    @PostMapping("/account")
    public Mono<Void> saveAccount(@Valid @RequestBody AdminUserDTO userDTO) {
        return SecurityUtils.getCurrentUserLogin()
            .switchIfEmpty(Mono.error(new AccountResourceException("Current user login not found")))
            .flatMap(userLogin ->
                userRepository
                    .findOneByEmailIgnoreCase(userDTO.getEmail())
                    .filter(existingUser -> !existingUser.getLogin().equalsIgnoreCase(userLogin))
                    .hasElement()
                    .flatMap(emailExists -> {
                        if (emailExists) {
                            throw new EmailAlreadyUsedException();
                        }
                        return userRepository.findOneByLogin(userLogin);
                    })
            )
            .switchIfEmpty(Mono.error(new AccountResourceException("User could not be found")))
            .flatMap(user ->
                userService.updateUser(
                    userDTO.getFirstName(),
                    userDTO.getLastName(),
                    userDTO.getEmail(),
                    userDTO.getLangKey(),
                    userDTO.getImageUrl()
                )
            );
    }

    /**
     * {@code POST  /account/change-password} : changes the current user's password.
     *
     * @param passwordChangeDto current and new password.
     * @throws InvalidPasswordException {@code 400 (Bad Request)} if the new password is incorrect.
     */
    @PostMapping(path = "/account/change-password")
    public Mono<Void> changePassword(@RequestBody PasswordChangeDTO passwordChangeDto) {
        if (isPasswordLengthInvalid(passwordChangeDto.getNewPassword())) {
            throw new InvalidPasswordException();
        }
        return userService.changePassword(passwordChangeDto.getCurrentPassword(), passwordChangeDto.getNewPassword());
    }

    /**
     * {@code POST   /account/resend-activation} : Resend the activation email for an inactive account.
     *
     * @param mail the mail of the user.
     */
    @PostMapping(path = "/account/resend-activation")
    public Mono<Void> resendActivationEmail(@RequestBody String mail) {
        return userService
            .requestActivationResend(unwrapPlainStringBody(mail))
            .flatMap(user -> {
                if (Objects.nonNull(user)) {
                    return Mono.fromRunnable(() -> mailService.sendActivationEmail(user)).subscribeOn(Schedulers.boundedElastic());
                }
                LOG.warn("Activation resend requested for non existing or already active mail");
                return Mono.empty();
            })
            .then();
    }

    /**
     * {@code POST   /account/reset-password/init} : Send an email to reset the password of the user.
     *
     * @param mail the mail of the user.
     */
    @PostMapping(path = "/account/reset-password/init")
    public Mono<Void> requestPasswordReset(@RequestBody String mail) {
        return userService
            .requestPasswordReset(unwrapPlainStringBody(mail))
            .doOnSuccess(user -> {
                if (Objects.nonNull(user)) {
                    mailService.sendPasswordResetMail(user);
                } else {
                    // Pretend the request has been successful to prevent checking which emails really exist
                    // but log that an invalid attempt has been made
                    LOG.warn("Password reset requested for non existing mail");
                }
            })
            .then();
    }

    /**
     * {@code POST   /account/reset-password/finish} : Finish to reset the password of the user.
     *
     * @param keyAndPassword the generated key and the new password.
     * @throws InvalidPasswordException {@code 400 (Bad Request)} if the password is incorrect.
     * @throws RuntimeException {@code 500 (Internal Server Error)} if the password could not be reset.
     */
    @PostMapping(path = "/account/reset-password/finish")
    public Mono<Void> finishPasswordReset(@RequestBody KeyAndPasswordVM keyAndPassword) {
        if (isPasswordLengthInvalid(keyAndPassword.getNewPassword())) {
            throw new InvalidPasswordException();
        }
        return userService
            .completePasswordReset(keyAndPassword.getNewPassword(), keyAndPassword.getKey())
            .switchIfEmpty(Mono.error(new BadRequestAlertException("Invalid reset key", "userManagement", "invalidresetkey")))
            .then();
    }

    private static boolean isPasswordLengthInvalid(String password) {
        return (
            StringUtils.isEmpty(password) ||
            password.length() < ManagedUserVM.PASSWORD_MIN_LENGTH ||
            password.length() > ManagedUserVM.PASSWORD_MAX_LENGTH
        );
    }

    /**
     * WebFlux decodes a plain {@code @RequestBody String} verbatim from the raw bytes, without
     * JSON-parsing it, even when Content-Type is application/json. Clients that send a JSON string
     * literal (e.g. {@code "user@example.com"}, via {@code JSON.stringify(email)}) end up with the
     * literal quote characters inside this method's argument. Strip a single matching pair before use.
     */
    private static String unwrapPlainStringBody(String raw) {
        return StringUtils.trimToEmpty(StringUtils.unwrap(StringUtils.trim(raw), '"'));
    }
}
