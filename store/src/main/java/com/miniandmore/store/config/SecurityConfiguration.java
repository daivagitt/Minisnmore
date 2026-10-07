package com.miniandmore.store.config;

import static org.springframework.security.config.Customizer.withDefaults;

import com.miniandmore.store.security.AuthoritiesConstants;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(securedEnabled = true)
public class SecurityConfiguration {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth ->
                auth
                    .requestMatchers("/management/health", "/management/health/**", "/management/info").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/admin/products", "/api/v1/admin/products/**")
                    .hasAnyAuthority(
                        AuthoritiesConstants.ADMIN,
                        AuthoritiesConstants.SUPER_ADMIN,
                        AuthoritiesConstants.SALES,
                        AuthoritiesConstants.OPERATIONS
                    )
                    .requestMatchers(HttpMethod.GET, "/api/v1/admin/contact-messages", "/api/v1/admin/contact-messages/**")
                    .hasAnyAuthority(
                        AuthoritiesConstants.ADMIN,
                        AuthoritiesConstants.SUPER_ADMIN,
                        AuthoritiesConstants.OPERATIONS,
                        AuthoritiesConstants.CUSTOMER_SUPPORT
                    )
                    .requestMatchers("/api/v1/admin/**").hasAnyAuthority(AuthoritiesConstants.ADMIN, AuthoritiesConstants.SUPER_ADMIN)
                    .requestMatchers("/api/v1/**").permitAll()
                    .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(withDefaults()));
        return http.build();
    }
}
