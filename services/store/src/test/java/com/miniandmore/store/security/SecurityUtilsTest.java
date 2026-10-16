package com.miniandmore.store.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class SecurityUtilsTest {

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void currentBearerToken_returnsNullWhenNotJwt() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("user", "pass"));
        assertThat(SecurityUtils.currentBearerToken()).isNull();
    }

    @Test
    void currentBearerToken_returnsBearerPrefixForJwt() {
        Jwt jwt = Jwt.withTokenValue("token-abc").header("alg", "none").claim("sub", "user").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        assertThat(SecurityUtils.currentBearerToken()).isEqualTo("Bearer token-abc");
    }
}
