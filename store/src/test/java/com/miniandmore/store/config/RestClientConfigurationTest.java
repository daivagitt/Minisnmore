package com.miniandmore.store.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class RestClientConfigurationTest {

    @Test
    void restClientBuilderBean() {
        RestClientConfiguration configuration = new RestClientConfiguration();
        RestClient.Builder builder = configuration.restClientBuilder();
        assertThat(builder).isNotNull();
    }
}
