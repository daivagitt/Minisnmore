package com.miniandmore.commerce.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class RestClientConfigurationTest {

    @Test
    void restClientBuilderBean() {
        assertThat(new RestClientConfiguration().restClientBuilder()).isNotNull().isInstanceOf(RestClient.Builder.class);
    }
}
