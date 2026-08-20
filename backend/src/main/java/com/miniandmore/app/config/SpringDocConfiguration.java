package com.miniandmore.app.config;

import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.info.Info;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.ServerBaseUrlCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SpringDocConfiguration {

  private static final String INTERNAL_PREFIX = "/internal/v1";
  private static final String PUBLIC_PREFIX = "/api/v1";

  @Bean
  public ServerBaseUrlCustomizer serverBaseUrlRequestCustomizer() {
    return (serverBaseUrl, request) -> {
      List<String> forwardedPrefix = request.getHeaders().get("X-Forwarded-Prefix");
      if (forwardedPrefix != null && !forwardedPrefix.isEmpty()) {
        return forwardedPrefix.get(0);
      }
      return serverBaseUrl;
    };
  }

  /**
   * Document catalog/commerce/store public surface only — not legacy JHipster entity CRUD.
   */
  @Bean
  public GroupedOpenApi catalogPublicApi() {
    return GroupedOpenApi.builder()
      .group("catalog")
      .displayName("Catalog & commerce API")
      .pathsToMatch(INTERNAL_PREFIX + "/**", PUBLIC_PREFIX + "/**")
      .addOpenApiCustomizer(publicApiPathCustomizer())
      .build();
  }

  /**
   * Internal controllers use {@code /internal/v1}; the gateway exposes them as {@code /api/v1}.
   */
  @Bean
  public OpenApiCustomizer publicApiPathCustomizer() {
    return openApi -> {
      if (openApi.getPaths() == null) {
        return;
      }
      Paths rewritten = new Paths();
      openApi
        .getPaths()
        .forEach((path, pathItem) -> {
          String publicPath = toPublicApiPath(path);
          rewritten.addPathItem(publicPath, pathItem);
        });
      openApi.setPaths(rewritten);
      openApi.info(
        new Info()
          .title("Minisandmore API")
          .version("0.0.1")
          .description(
            "Catalog & commerce API (`/api/v1/**`). " +
            "Switch to **Gateway (auth & users)** in the dropdown above for `/api/authenticate`, `/api/account`, `/api/admin/users`, etc."
          )
      );
    };
  }

  static String toPublicApiPath(String path) {
    if (path.startsWith(INTERNAL_PREFIX)) {
      return PUBLIC_PREFIX + path.substring(INTERNAL_PREFIX.length());
    }
    return path;
  }
}
