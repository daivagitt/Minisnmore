package com.miniandmore.gateway.config;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayOpenApiConfiguration {

  @Bean
  public GroupedOpenApi gatewayAuthApi() {
    return GroupedOpenApi.builder()
      .group("gateway")
      .displayName("Gateway (auth & users)")
      .pathsToMatch(
        "/api/authenticate",
        "/api/register",
        "/api/activate",
        "/api/account/**",
        "/api/admin/users/**",
        "/api/users",
        "/api/authorities/**",
        "/api/gateway/**"
      )
      .build();
  }
}
