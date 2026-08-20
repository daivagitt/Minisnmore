package com.miniandmore.gateway.config;

import static org.springframework.security.config.Customizer.withDefaults;

import com.miniandmore.gateway.security.AuthoritiesConstants;
import com.miniandmore.gateway.security.OAuth2AuthenticationFailureHandler;
import com.miniandmore.gateway.security.OAuth2AuthenticationSuccessHandler;
import com.miniandmore.gateway.security.WebFluxCookieOAuth2AuthorizationRequestRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UserDetailsRepositoryReactiveAuthenticationManager;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
public class SecurityConfiguration {

    private final OAuth2AuthenticationSuccessHandler oauth2AuthenticationSuccessHandler;
    private final OAuth2AuthenticationFailureHandler oauth2AuthenticationFailureHandler;
    private final WebFluxCookieOAuth2AuthorizationRequestRepository authorizationRequestRepository;

    public SecurityConfiguration(
        OAuth2AuthenticationSuccessHandler oauth2AuthenticationSuccessHandler,
        OAuth2AuthenticationFailureHandler oauth2AuthenticationFailureHandler,
        WebFluxCookieOAuth2AuthorizationRequestRepository authorizationRequestRepository
    ) {
        this.oauth2AuthenticationSuccessHandler = oauth2AuthenticationSuccessHandler;
        this.oauth2AuthenticationFailureHandler = oauth2AuthenticationFailureHandler;
        this.authorizationRequestRepository = authorizationRequestRepository;
    }

    @Bean
    public ReactiveAuthenticationManager reactiveAuthenticationManager(
        ReactiveUserDetailsService userDetailsService,
        PasswordEncoder passwordEncoder
    ) {
        UserDetailsRepositoryReactiveAuthenticationManager authenticationManager = new UserDetailsRepositoryReactiveAuthenticationManager(
            userDetailsService
        );
        authenticationManager.setPasswordEncoder(passwordEncoder);
        return authenticationManager;
    }

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        http
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .cors(withDefaults())
            .authorizeExchange(authz ->
                authz
                    // Proxied store/commerce/catalog APIs — downstream services enforce JWT/admin rules.
                    .pathMatchers("/api/v1/**").permitAll()
                    .pathMatchers("/internal/v1/**").permitAll()
                    // Gateway auth & registration
                    .pathMatchers("/api/authenticate").permitAll()
                    .pathMatchers("/api/register").permitAll()
                    .pathMatchers("/api/activate").permitAll()
                    .pathMatchers("/api/account/reset-password/**").permitAll()
                    .pathMatchers("/api/account/resend-activation").permitAll()
                    .pathMatchers("/api/users").permitAll()
                    // Social login (OAuth2 authorization + callback)
                    .pathMatchers("/oauth2/**").permitAll()
                    .pathMatchers("/login/oauth2/**").permitAll()
                    // Actuator / health
                    .pathMatchers("/v3/api-docs", "/v3/api-docs/**", "/swagger-ui/**").permitAll()
                    .pathMatchers("/management/health", "/management/health/**", "/management/info").permitAll()
                    .pathMatchers("/management/prometheus").permitAll()
                    .pathMatchers("/management/**").hasAuthority(AuthoritiesConstants.ADMIN)
                    // Gateway user administration
                    .pathMatchers("/api/admin/**").hasAuthority(AuthoritiesConstants.ADMIN)
                    .pathMatchers("/api/authorities/**").hasAuthority(AuthoritiesConstants.ADMIN)
                    .pathMatchers("/api/**").authenticated()
                    .anyExchange().permitAll()
            )
            .oauth2Login(oauth2 ->
                oauth2
                    .authorizationRequestRepository(authorizationRequestRepository)
                    .authenticationSuccessHandler(oauth2AuthenticationSuccessHandler)
                    .authenticationFailureHandler(oauth2AuthenticationFailureHandler)
            )
            .oauth2Client(withDefaults())
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(withDefaults()));
        return http.build();
    }
}
