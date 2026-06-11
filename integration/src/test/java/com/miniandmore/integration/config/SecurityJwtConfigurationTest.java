package com.miniandmore.integration.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.util.ReflectionTestUtils;

class SecurityJwtConfigurationTest {

    @Test
    void jwtDecoder_buildsWithConfiguredSecret() {
        SecurityJwtConfiguration config = new SecurityJwtConfiguration();
        byte[] key = new byte[64];
        ReflectionTestUtils.setField(config, "jwtKey", Base64.getEncoder().encodeToString(key));

        JwtDecoder decoder = config.jwtDecoder();

        assertThat(decoder).isNotNull();
    }
}
