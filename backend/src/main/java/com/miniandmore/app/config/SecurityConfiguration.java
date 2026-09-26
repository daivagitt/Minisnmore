package com.miniandmore.app.config;

import static com.miniandmore.app.security.AuthoritiesConstants.ADMIN;
import static com.miniandmore.app.security.AuthoritiesConstants.SUPER_ADMIN;
import static org.springframework.security.config.Customizer.withDefaults;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(authz ->
                authz
                    .requestMatchers("/internal/v1/**")
                    .permitAll()
                    .requestMatchers(
                        "/api/v1/zoho/stock-webhook",
                        "/api/v1/zoho/shipment-webhook",
                        "/api/v1/zoho/item-webhook",
                        "/api/v1/zoho/order-status-webhook",
                        "/api/v1/zoho/invoice-webhook",
                        "/api/v1/webhooks/xpressbees/returns",
                        "/api/v1/webhooks/xpressbees/shipments"
                    )
                    .permitAll()
                    .requestMatchers("/api/v1/admin/**")
                    .hasAnyAuthority(ADMIN, SUPER_ADMIN)
                    .requestMatchers("/v3/api-docs", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                    .permitAll()
                    .requestMatchers("/management/health", "/management/health/**", "/management/info")
                    .permitAll()
                    .requestMatchers("/management/**")
                    .hasAnyAuthority(ADMIN, SUPER_ADMIN)
                    .anyRequest()
                    .authenticated()
            )
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(exceptions ->
                exceptions
                    .authenticationEntryPoint(new BearerTokenAuthenticationEntryPoint())
                    .accessDeniedHandler(new BearerTokenAccessDeniedHandler())
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(withDefaults()));
        return http.build();
    }
}
