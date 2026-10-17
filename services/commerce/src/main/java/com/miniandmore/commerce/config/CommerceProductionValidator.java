package com.miniandmore.commerce.config;

import com.miniandmore.shared.vault.VaultJdbcSecretsLoader;
import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

@Configuration
@SuppressWarnings("java:S1118")
public class CommerceProductionValidator {

    private static final String SAMPLE_JWT_SECRET =
        "NWNhYWRmODZlZDAzNzg4NGY2NTA0MzAyZTdjNjI2ZjYzYWI2YmQ3Yjk4ZDA5NGVkODNhYWY2ZmExOTJmZjA3YmJhNDdiYTYxMTdjZmM2ZWUyMmMwMzQyMTY1NDBjNTc5ODg4MjNkZjExODFmZjUwNDdlNjVhOTlhMWE5MjRjYTU=";

    public CommerceProductionValidator(Environment environment, @Value("${jhipster.security.authentication.jwt.base64-secret:}") String jwtSecret) {
        if (!environment.acceptsProfiles(Profiles.of("prod"))) {
            return;
        }
        if (Arrays.asList(environment.getActiveProfiles()).contains("secret-samples")) {
            throw new IllegalStateException("secret-samples profile must not be active in production");
        }
        if (StringUtils.hasText(jwtSecret) && SAMPLE_JWT_SECRET.equals(jwtSecret.trim())) {
            throw new IllegalStateException("JHipster sample JWT secret must not be used in production");
        }
        VaultJdbcSecretsLoader.assertProductionReady(environment);
    }
}
