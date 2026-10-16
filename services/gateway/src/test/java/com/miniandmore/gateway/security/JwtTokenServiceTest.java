package com.miniandmore.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.miniandmore.gateway.security.DomainUserDetailsService.UserWithId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.util.Base64;
import javax.crypto.spec.SecretKeySpec;
import java.util.List;

class JwtTokenServiceTest {

    private JwtTokenService jwtTokenService;

    @BeforeEach
    void setUp() {
        byte[] keyBytes = Base64.from(
            "fd54a45s65fds737b9aafcb3412e07ed99b267f33413274720ddbb7f6c5e64e9f14075f2d7ed041592f0b7657baf8"
        ).decode();
        JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(new SecretKeySpec(keyBytes, SecurityUtils.JWT_ALGORITHM.getName())));
        jwtTokenService = new JwtTokenService(encoder);
        ReflectionTestUtils.setField(jwtTokenService, "tokenValidityInSeconds", 600L);
        ReflectionTestUtils.setField(jwtTokenService, "tokenValidityInSecondsForRememberMe", 1200L);
    }

    @Test
    void createToken_includesAuthorities() {
        var auth = new UsernamePasswordAuthenticationToken("user", "pass", List.of(new SimpleGrantedAuthority("ROLE_USER")));

        String token = jwtTokenService.createToken(auth, false);

        assertThat(token).isNotBlank();
    }

    @Test
    void createToken_withUserId_includesClaim() {
        UserWithId principal = new UserWithId("user", "pass", List.of(new SimpleGrantedAuthority("ROLE_USER")), 42L);
        var auth = new UsernamePasswordAuthenticationToken(principal, "pass", principal.getAuthorities());

        String token = jwtTokenService.createToken(auth, true);

        assertThat(token).isNotBlank();
    }

    @Test
    void createTokenForOAuthUser_issuesJwtWithoutAuthenticationToken() {
        com.miniandmore.gateway.domain.User user = new com.miniandmore.gateway.domain.User();
        user.setId(42L);
        user.setLogin("oauth-user");
        com.miniandmore.gateway.domain.Authority authority = new com.miniandmore.gateway.domain.Authority();
        authority.setName("ROLE_USER");
        user.setAuthorities(java.util.Set.of(authority));

        String token = jwtTokenService.createTokenForOAuthUser(user, true);

        assertThat(token).isNotBlank();
    }
}
