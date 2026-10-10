package com.miniandmore.commerce.config;

import static org.springframework.security.config.Customizer.withDefaults;

import com.miniandmore.commerce.security.AuthoritiesConstants;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfiguration {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(authz ->
                authz
                    .dispatcherTypeMatchers(DispatcherType.ERROR)
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/coupons/validate").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/coupons/active").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/orders/track/**").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/admin/order-returns", "/api/v1/admin/order-returns/**")
                    .hasAnyAuthority(
                        AuthoritiesConstants.SUPER_ADMIN,
                        AuthoritiesConstants.ADMIN,
                        AuthoritiesConstants.OPERATIONS,
                        AuthoritiesConstants.CUSTOMER_SUPPORT
                    )
                    .requestMatchers(HttpMethod.POST, "/api/v1/admin/order-returns/*/approve", "/api/v1/admin/order-returns/*/reject")
                    .hasAnyAuthority(
                        AuthoritiesConstants.SUPER_ADMIN,
                        AuthoritiesConstants.ADMIN,
                        AuthoritiesConstants.OPERATIONS,
                        AuthoritiesConstants.CUSTOMER_SUPPORT
                    )
                    .requestMatchers("/api/v1/admin/order-returns/**")
                    .hasAnyAuthority(
                        AuthoritiesConstants.SUPER_ADMIN,
                        AuthoritiesConstants.ADMIN,
                        AuthoritiesConstants.OPERATIONS
                    )
                    .requestMatchers(HttpMethod.GET, "/api/v1/admin/cancellation-requests", "/api/v1/admin/cancellation-requests/**")
                    .hasAnyAuthority(
                        AuthoritiesConstants.SUPER_ADMIN,
                        AuthoritiesConstants.ADMIN,
                        AuthoritiesConstants.OPERATIONS,
                        AuthoritiesConstants.CUSTOMER_SUPPORT
                    )
                    .requestMatchers(
                        HttpMethod.POST,
                        "/api/v1/admin/cancellation-requests/*/approve",
                        "/api/v1/admin/cancellation-requests/*/reject"
                    )
                    .hasAnyAuthority(
                        AuthoritiesConstants.SUPER_ADMIN,
                        AuthoritiesConstants.ADMIN,
                        AuthoritiesConstants.OPERATIONS,
                        AuthoritiesConstants.CUSTOMER_SUPPORT
                    )
                    .requestMatchers("/api/v1/admin/cancellation-requests/**")
                    .hasAnyAuthority(
                        AuthoritiesConstants.SUPER_ADMIN,
                        AuthoritiesConstants.ADMIN,
                        AuthoritiesConstants.OPERATIONS
                    )
                    .requestMatchers(HttpMethod.GET, "/api/v1/admin/orders", "/api/v1/admin/orders/**")
                    .hasAnyAuthority(
                        AuthoritiesConstants.SUPER_ADMIN,
                        AuthoritiesConstants.ADMIN,
                        AuthoritiesConstants.OPERATIONS,
                        AuthoritiesConstants.CUSTOMER_SUPPORT
                    )
                    .requestMatchers(HttpMethod.POST, "/api/v1/admin/orders/*/cancel")
                    .hasAnyAuthority(
                        AuthoritiesConstants.SUPER_ADMIN,
                        AuthoritiesConstants.ADMIN,
                        AuthoritiesConstants.OPERATIONS
                    )
                    .requestMatchers("/api/v1/admin/orders", "/api/v1/admin/orders/**")
                    .hasAnyAuthority(
                        AuthoritiesConstants.SUPER_ADMIN,
                        AuthoritiesConstants.ADMIN,
                        AuthoritiesConstants.OPERATIONS
                    )
                    .requestMatchers("/api/v1/**").authenticated()
                    .requestMatchers("/management/health", "/management/health/**", "/management/info")
                    .permitAll()
                    .requestMatchers("/management/**")
                    .hasAuthority(AuthoritiesConstants.ADMIN)
                    .anyRequest()
                    .denyAll()
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
