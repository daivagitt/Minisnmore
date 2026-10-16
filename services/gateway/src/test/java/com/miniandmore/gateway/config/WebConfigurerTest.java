package com.miniandmore.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.gateway.web.rest.errors.ExceptionTranslator;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import tech.jhipster.config.JHipsterProperties;

class WebConfigurerTest {

    @Test
    void beans_areCreated() {
        JHipsterProperties properties = new JHipsterProperties();
        properties.getCors().setAllowedOrigins(java.util.List.of("http://localhost:5173"));
        WebConfigurer configurer = new WebConfigurer(properties);

        assertThat(configurer.corsConfigurationSource()).isNotNull();
        assertThat(configurer.reactivePageableHandlerMethodArgumentResolver()).isNotNull();
        assertThat(configurer.reactiveSortHandlerMethodArgumentResolver()).isNotNull();
        assertThat(configurer.problemExceptionHandler(new ObjectMapper(), new ExceptionTranslator(new MockEnvironment()))).isNotNull();
    }
}
