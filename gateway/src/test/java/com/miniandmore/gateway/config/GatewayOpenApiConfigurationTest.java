package com.miniandmore.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = GatewayOpenApiConfiguration.class)
class GatewayOpenApiConfigurationTest {

    @Autowired
    private GroupedOpenApi gatewayAuthApi;

    @Test
    void gatewayAuthApi_exposesGatewayGroupWithAuthPaths() {
        assertThat(gatewayAuthApi.getGroup()).isEqualTo("gateway");
        assertThat(gatewayAuthApi.getDisplayName()).isEqualTo("Gateway (auth & users)");
        assertThat(gatewayAuthApi.getPathsToMatch())
            .contains(
                "/api/authenticate",
                "/api/register",
                "/api/activate",
                "/api/account/**",
                "/api/admin/users/**",
                "/api/users",
                "/api/authorities/**",
                "/api/gateway/**"
            );
    }

    @Test
    void gatewayAuthApi_beanMethodBuildsGroupedOpenApiDirectly() {
        GroupedOpenApi built = new GatewayOpenApiConfiguration().gatewayAuthApi();
        assertThat(built.getGroup()).isEqualTo("gateway");
        assertThat(built.getPathsToMatch()).contains("/api/register", "/api/authorities/**");
    }
}
