package com.miniandmore.gateway.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.miniandmore.gateway.domain.Authority;
import com.miniandmore.gateway.domain.User;
import com.miniandmore.gateway.repository.AuthorityRepository;
import com.miniandmore.gateway.repository.UserAuthoritiesLoader;
import com.miniandmore.gateway.repository.UserRepository;
import com.miniandmore.gateway.service.UserService;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OAuth2UserTokenServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserAuthoritiesLoader userAuthoritiesLoader;

    @Mock
    private UserService userService;

    @Mock
    private AuthorityRepository authorityRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenService jwtTokenService;

    private OAuth2UserTokenService service;

    @BeforeEach
    void setUp() {
        service = new OAuth2UserTokenService(
            userRepository,
            userAuthoritiesLoader,
            userService,
            authorityRepository,
            passwordEncoder,
            jwtTokenService
        );
        org.mockito.Mockito.lenient().when(passwordEncoder.encode(anyString())).thenReturn("encoded");
        org.mockito.Mockito.lenient().when(userService.saveUser(any(User.class))).thenAnswer(inv -> {
            User saved = inv.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(2L);
            }
            return Mono.just(saved);
        });
        org.mockito.Mockito.lenient().when(userRepository.saveUserAuthority(anyLong(), anyString())).thenReturn(Mono.empty());
        org.mockito.Mockito.lenient()
            .when(authorityRepository.findById(AuthoritiesConstants.USER))
            .thenReturn(Mono.just(new Authority().name(AuthoritiesConstants.USER)));
        org.mockito.Mockito.lenient().when(userRepository.findOneWithAuthoritiesByEmailIgnoreCaseForOAuth(anyString())).thenReturn(Mono.empty());
        org.mockito.Mockito.lenient().when(userRepository.findOneWithAuthoritiesByLoginForOAuth(anyString())).thenReturn(Mono.empty());
        org.mockito.Mockito.lenient().when(userRepository.deleteInactiveByLogin(anyString())).thenReturn(Mono.empty());
        org.mockito.Mockito.lenient().when(userRepository.deleteInactiveByEmail(anyString())).thenReturn(Mono.empty());
        org.mockito.Mockito.lenient()
            .when(userAuthoritiesLoader.withAuthorities(any(User.class)))
            .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        org.mockito.Mockito.lenient().when(jwtTokenService.createTokenForOAuthUser(any(User.class), any(Boolean.class))).thenReturn("token");
    }

    @Test
    void tokenFor_existingUser_returnsJwt() {
        User user = userWithRole();
        user.setIsPersisted();
        user.setPassword("$2a$10$abcdefghijklmnopqrstuv");
        DefaultOAuth2User oauthUser = oauthUser("email", Map.of("email", "user@localhost", "given_name", "John", "family_name", "Doe"));
        when(userRepository.findOneWithAuthoritiesByEmailIgnoreCaseForOAuth("user@localhost")).thenReturn(Mono.just(user));
        JwtEncoder encoder = new NimbusJwtEncoder(
            new ImmutableSecret<>(
                new SecretKeySpec(
                    Base64.from(
                        "fd54a45s65fds737b9aafcb3412e07ed99b267f33413274720ddbb7f6c5e64e9f14075f2d7ed041592f0b7657baf8"
                    ).decode(),
                    SecurityUtils.JWT_ALGORITHM.getName()
                )
            )
        );
        JwtTokenService realJwtTokenService = new JwtTokenService(encoder);
        ReflectionTestUtils.setField(realJwtTokenService, "tokenValidityInSeconds", 600L);
        ReflectionTestUtils.setField(realJwtTokenService, "tokenValidityInSecondsForRememberMe", 1200L);
        service = new OAuth2UserTokenService(
            userRepository,
            userAuthoritiesLoader,
            userService,
            authorityRepository,
            passwordEncoder,
            realJwtTokenService
        );

        StepVerifier.create(service.tokenFor(oauthUser)).expectNextMatches(token -> token != null && !token.isBlank()).verifyComplete();
        org.mockito.Mockito.verify(userService, org.mockito.Mockito.never()).saveUser(any(User.class));
    }

    @Test
    void tokenFor_newUser_createsAndReturnsJwt() {
        DefaultOAuth2User oauthUser = oauthUser(
            "email",
            Map.of("email", "new@localhost", "name", "New User", "picture", "http://img", "given_name", "New", "family_name", "User")
        );
        Authority authority = new Authority();
        authority.setName(AuthoritiesConstants.USER);
        when(userRepository.findOneWithAuthoritiesByEmailIgnoreCaseForOAuth("new@localhost")).thenReturn(Mono.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("encoded");
        when(authorityRepository.findById(AuthoritiesConstants.USER)).thenReturn(Mono.just(authority));
        when(userService.saveUser(any(User.class))).thenAnswer(inv -> {
            User saved = inv.getArgument(0);
            saved.setId(2L);
            return Mono.just(saved);
        });
        when(userRepository.saveUserAuthority(anyLong(), anyString())).thenReturn(Mono.empty());
        when(jwtTokenService.createTokenForOAuthUser(any(User.class), any(Boolean.class))).thenReturn("token");

        StepVerifier.create(service.tokenFor(oauthUser)).expectNext("token").verifyComplete();
    }

    @Test
    void tokenFor_facebookId_buildsSyntheticEmail() {
        DefaultOAuth2User oauthUser = oauthUser("id", Map.of("id", "12345", "name", "FB User"));
        Authority authority = new Authority();
        authority.setName(AuthoritiesConstants.USER);
        when(userRepository.findOneWithAuthoritiesByEmailIgnoreCaseForOAuth("facebook.12345@oauth.miniandmore.local")).thenReturn(Mono.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("encoded");
        when(authorityRepository.findById(AuthoritiesConstants.USER)).thenReturn(Mono.just(authority));
        when(userService.saveUser(any(User.class))).thenAnswer(inv -> {
            User saved = inv.getArgument(0);
            saved.setId(99L);
            return Mono.just(saved);
        });
        when(userRepository.saveUserAuthority(anyLong(), anyString())).thenReturn(Mono.empty());
        when(jwtTokenService.createTokenForOAuthUser(any(User.class), any(Boolean.class))).thenReturn("token");

        StepVerifier.create(service.tokenFor(oauthUser)).expectNext("token").verifyComplete();
    }

    @Test
    void tokenFor_missingEmail_errors() {
        DefaultOAuth2User oauthUser = oauthUser("sub", Map.of("sub", "oidc-only"));

        StepVerifier.create(service.tokenFor(oauthUser)).expectError(IllegalArgumentException.class).verify();
    }

    @Test
    void tokenFor_facebookPictureMap_resolvesUrl() {
        Map<String, Object> picture = Map.of("data", Map.of("url", "http://img"));
        DefaultOAuth2User oauthUser = oauthUser("email", Map.of("email", "pic@localhost", "picture", picture));
        when(userRepository.findOneWithAuthoritiesByEmailIgnoreCaseForOAuth("pic@localhost")).thenReturn(Mono.empty());
        when(authorityRepository.findById(AuthoritiesConstants.USER)).thenReturn(Mono.just(new Authority().name(AuthoritiesConstants.USER)));

        StepVerifier.create(service.tokenFor(oauthUser)).expectNext("token").verifyComplete();
    }

    @Test
    void tokenFor_appleNameParts_usesNestedName() {
        Map<String, Object> name = Map.of("firstName", "Apple", "lastName", "User");
        DefaultOAuth2User oauthUser = oauthUser("email", Map.of("email", "apple@localhost", "name", name));
        Authority authority = new Authority();
        authority.setName(AuthoritiesConstants.USER);
        when(userRepository.findOneWithAuthoritiesByEmailIgnoreCaseForOAuth("apple@localhost")).thenReturn(Mono.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("encoded");
        when(authorityRepository.findById(AuthoritiesConstants.USER)).thenReturn(Mono.just(authority));
        when(userService.saveUser(any(User.class))).thenAnswer(inv -> {
            User saved = inv.getArgument(0);
            saved.setId(99L);
            return Mono.just(saved);
        });
        when(userRepository.saveUserAuthority(anyLong(), anyString())).thenReturn(Mono.empty());
        when(jwtTokenService.createTokenForOAuthUser(any(User.class), any(Boolean.class))).thenReturn("token");

        StepVerifier.create(service.tokenFor(oauthUser)).expectNext("token").verifyComplete();
    }

    private static User userWithRole() {
        User user = new User();
        user.setId(1L);
        user.setLogin("user");
        user.setEmail("user@localhost");
        user.setActivated(true);
        user.setPassword("encoded");
        Authority authority = new Authority();
        authority.setName(AuthoritiesConstants.USER);
        user.setAuthorities(Set.of(authority));
        return user;
    }

    private static DefaultOAuth2User oauthUser(String nameAttributeKey, Map<String, Object> attributes) {
        return new DefaultOAuth2User(
            org.springframework.security.core.authority.AuthorityUtils.createAuthorityList("ROLE_USER"),
            new HashMap<>(attributes),
            nameAttributeKey
        );
    }
}
