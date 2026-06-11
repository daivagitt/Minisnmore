package com.miniandmore.gateway.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.gateway.handler.AsyncPredicate;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class GatewayResourceTest {

    @Mock
    private RouteLocator routeLocator;

    @Mock
    private DiscoveryClient discoveryClient;

    @Test
    void activeRoutes_returnsRoutesExcludingGatewayApp() {
        GatewayResource resource = new GatewayResource(routeLocator, discoveryClient);
        ReflectionTestUtils.setField(resource, "appName", "gateway");

        Route route = org.mockito.Mockito.mock(Route.class);
        AsyncPredicate<ServerWebExchange> predicate = new AsyncPredicate<>() {
            @Override
            public Mono<Boolean> apply(ServerWebExchange exchange) {
                return Mono.just(true);
            }

            @Override
            public String toString() {
                return "Paths: [/api/foo/**], match trailing slash: true";
            }
        };
        when(route.getPredicate()).thenReturn(predicate);
        when(route.getId()).thenReturn("ReactiveCompositeDiscoveryClient_backend");
        when(routeLocator.getRoutes()).thenReturn(Flux.just(route));
        ServiceInstance instance = org.mockito.Mockito.mock(ServiceInstance.class);
        when(discoveryClient.getInstances("backend")).thenReturn(List.of(instance));

        var response = resource.activeRoutes().block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotEmpty();
    }
}
