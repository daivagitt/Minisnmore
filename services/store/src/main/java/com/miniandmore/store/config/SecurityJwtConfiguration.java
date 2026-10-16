package com.miniandmore.store.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

@Configuration
public class SecurityJwtConfiguration {

    private static final MacAlgorithm JWT_ALGORITHM = MacAlgorithm.HS512;

    @Value("${jhipster.security.authentication.jwt.base64-secret}")
    private String jwtKey;

    @Bean
    public JwtDecoder jwtDecoder() {
        byte[] keyBytes = Base64.from(jwtKey).decode();
        SecretKey secretKey = new SecretKeySpec(keyBytes, 0, keyBytes.length, "HmacSHA512");
        return NimbusJwtDecoder.withSecretKey(secretKey).macAlgorithm(JWT_ALGORITHM).build();
    }
}
