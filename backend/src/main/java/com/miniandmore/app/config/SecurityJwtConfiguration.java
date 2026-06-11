package com.miniandmore.app.config;

import static com.miniandmore.app.security.SecurityUtils.JWT_ALGORITHM;

import com.miniandmore.app.management.SecurityMetersService;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

@Configuration
public class SecurityJwtConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(SecurityJwtConfiguration.class);

    @Value("${jhipster.security.authentication.jwt.base64-secret}")
    private String jwtKey;

    @Bean
    public JwtDecoder jwtDecoder(SecurityMetersService metersService) {
        NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder.withSecretKey(getSecretKey()).macAlgorithm(JWT_ALGORITHM).build();
        return token -> {
            try {
                return jwtDecoder.decode(token);
            } catch (Exception e) {
                trackJwtError(e, metersService);
                throw e;
            }
        };
    }

    private SecretKey getSecretKey() {
        byte[] keyBytes = Base64.from(jwtKey).decode();
        return new SecretKeySpec(keyBytes, 0, keyBytes.length, JWT_ALGORITHM.getName());
    }

    private static void trackJwtError(Exception e, SecurityMetersService metersService) {
        String message = e.getMessage();
        if (message == null) {
            LOG.error("Unknown JWT error", e);
            return;
        }
        if (message.contains("Invalid signature")) {
            metersService.trackTokenInvalidSignature();
        } else if (message.contains("Jwt expired at")) {
            metersService.trackTokenExpired();
        } else if (
            message.contains("Invalid JWT serialization") ||
            message.contains("Malformed token") ||
            message.contains("Invalid unsecured/JWS/JWE")
        ) {
            metersService.trackTokenMalformed();
        } else {
            LOG.error("Unknown JWT error {}", message);
        }
    }
}
