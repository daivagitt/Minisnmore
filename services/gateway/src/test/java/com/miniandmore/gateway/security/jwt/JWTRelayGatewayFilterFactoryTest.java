package com.miniandmore.gateway.security.jwt;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.miniandmore.gateway.config.SecurityJwtConfiguration;
import com.miniandmore.gateway.management.SecurityMetersService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JWTRelayGatewayFilterFactoryTest {

    @Mock
    private GatewayFilterChain chain;

    private JWTRelayGatewayFilterFactory factory;

    @BeforeEach
    void setUp() {
        SecurityJwtConfiguration configuration = new SecurityJwtConfiguration();
        ReflectionTestUtils.setField(
            configuration,
            "jwtKey",
            "fd54a45s65fds737b9aafcb3412e07ed99b267f33413274720ddbb7f6c5e64e9f14075f2d7ed041592f0b7657baf8"
        );
        factory = new JWTRelayGatewayFilterFactory(configuration.jwtDecoder(org.mockito.Mockito.mock(SecurityMetersService.class)));
        when(chain.filter(any())).thenReturn(Mono.empty());
    }

    @Test
    void apply_withoutAuthorization_continuesChain() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/test").build());

        StepVerifier.create(factory.apply(new Object()).filter(exchange, chain)).verifyComplete();
    }

    @Test
    void apply_withInvalidBearerToken_errors() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/test")
            .header(HttpHeaders.AUTHORIZATION, "Bearer not-valid")
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(factory.apply(new Object()).filter(exchange, chain)).expectError().verify();
        org.mockito.Mockito.verifyNoInteractions(chain);
    }
}
