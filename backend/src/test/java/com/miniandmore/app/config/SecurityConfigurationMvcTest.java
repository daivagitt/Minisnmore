package com.miniandmore.app.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = SecurityConfigurationMvcTest.EmptyController.class)
@Import({ SecurityConfiguration.class, SecurityConfigurationMvcTest.JwtConfig.class })
@AutoConfigureMockMvc
class SecurityConfigurationMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired(required = false)
    private SecurityFilterChain securityFilterChain;

    @MockitoBean
    private com.miniandmore.app.repository.CategoryRepository categoryRepository;

    @Test
    void securityFilterChain_isRegistered() {
        assertThat(securityFilterChain).isNotNull();
    }

    @org.springframework.web.bind.annotation.RestController
    static class EmptyController {}

    @TestConfiguration
    static class JwtConfig {

        @Bean
        JwtDecoder jwtDecoder() {
            return token -> Jwt.withTokenValue(token).header("alg", "none").claim("sub", "user").build();
        }
    }
}
