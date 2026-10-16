package com.miniandmore.gateway.config;

import java.util.Arrays;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import tech.jhipster.config.JHipsterProperties;

@Configuration
public class GatewayProductionValidator {

    private static final String SAMPLE_JWT_SECRET =
        "NWNhYWRmODZlZDAzNzg4NGY2NTA0MzAyZTdjNjI2ZjYzYWI2YmQ3Yjk4ZDA5NGVkODNhYWY2ZmExOTJmZjA3YmJhNDdiYTYxMTdjZmM2ZWUyMmMwMzQyMTY1NDBjNTc5ODg4MjNkZjExODFmZjUwNDdlNjVhOTlhMWE5MjRjYTU=";

    public GatewayProductionValidator(Environment environment, JHipsterProperties jHipsterProperties, ApplicationProperties applicationProperties) {
        if (!environment.acceptsProfiles(Profiles.of("prod"))) {
            return;
        }
        if (Arrays.asList(environment.getActiveProfiles()).contains("secret-samples")) {
            throw new IllegalStateException("secret-samples profile must not be active in production");
        }
        String jwtSecret = jHipsterProperties.getSecurity().getAuthentication().getJwt().getBase64Secret();
        if (StringUtils.isNotBlank(jwtSecret) && SAMPLE_JWT_SECRET.equals(jwtSecret.trim())) {
            throw new IllegalStateException("JHipster sample JWT secret must not be used in production");
        }
        if (applicationProperties.getRateLimiting().isEnabled() && applicationProperties.getRateLimiting().isFailOpen()) {
            throw new IllegalStateException("GATEWAY_RATE_LIMIT_FAIL_OPEN must not be enabled in production");
        }
    }
}
