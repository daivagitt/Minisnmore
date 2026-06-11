package com.miniandmore.gateway.security;

import static com.miniandmore.gateway.security.SecurityUtils.AUTHORITIES_CLAIM;
import static com.miniandmore.gateway.security.SecurityUtils.JWT_ALGORITHM;
import static com.miniandmore.gateway.security.SecurityUtils.USER_ID_CLAIM;

import com.miniandmore.gateway.domain.Authority;
import com.miniandmore.gateway.domain.User;
import com.miniandmore.gateway.security.DomainUserDetailsService.UserWithId;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
public class JwtTokenService {

    private final JwtEncoder jwtEncoder;

    @Value("${jhipster.security.authentication.jwt.token-validity-in-seconds:0}")
    private long tokenValidityInSeconds;

    @Value("${jhipster.security.authentication.jwt.token-validity-in-seconds-for-remember-me:0}")
    private long tokenValidityInSecondsForRememberMe;

    public JwtTokenService(JwtEncoder jwtEncoder) {
        this.jwtEncoder = jwtEncoder;
    }

    public String createToken(Authentication authentication, boolean rememberMe) {
        String authorities = authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.joining(" "));
        Long userId = authentication.getPrincipal() instanceof UserWithId user ? user.getId() : null;
        return encodeToken(authentication.getName(), authorities, userId, rememberMe);
    }

    /** OAuth sign-in issues JWTs without Spring Security credential objects (avoids password char[] casts). */
    public String createTokenForOAuthUser(User user, boolean rememberMe) {
        String authorities = user
            .getAuthorities()
            .stream()
            .map(Authority::getName)
            .filter(name -> name != null && !name.isBlank())
            .collect(Collectors.joining(" "));
        return encodeToken(user.getLogin(), authorities, user.getId(), rememberMe);
    }

    private String encodeToken(String subject, String authorities, Long userId, boolean rememberMe) {
        var now = Instant.now();
        Instant validity = rememberMe
            ? now.plus(this.tokenValidityInSecondsForRememberMe, ChronoUnit.SECONDS)
            : now.plus(this.tokenValidityInSeconds, ChronoUnit.SECONDS);

        JwtClaimsSet.Builder builder = JwtClaimsSet.builder()
            .issuedAt(now)
            .expiresAt(validity)
            .subject(subject)
            .claim(AUTHORITIES_CLAIM, authorities);
        if (userId != null) {
            builder.claim(USER_ID_CLAIM, userId);
        }

        JwsHeader jwsHeader = JwsHeader.with(JWT_ALGORITHM).build();
        return this.jwtEncoder.encode(JwtEncoderParameters.from(jwsHeader, builder.build())).getTokenValue();
    }
}
