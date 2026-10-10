package com.miniandmore.gateway.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.miniandmore.gateway.web.rest.vm.RouteVM;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.gateway.handler.AsyncPredicate;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

class GatewayResourceToRouteVmMatrixTest {

    private final GatewayResource resource = new GatewayResource(mock(RouteLocator.class), mock(DiscoveryClient.class));

    @Test
    void toRouteVm_coversNullPredicateIdAndBracketedPath() {
        Route empty = mock(Route.class);
        when(empty.getPredicate()).thenReturn(null);
        when(empty.getId()).thenReturn(null);
        RouteVM blank = ReflectionTestUtils.invokeMethod(resource, "toRouteVm", empty);
        assertThat(blank.getPath()).isNull();
        assertThat(blank.getServiceId()).isNull();

        Route noBrackets = mock(Route.class);
        when(noBrackets.getPredicate()).thenReturn(named("Path"));
        when(noBrackets.getId()).thenReturn("ReactiveCompositeDiscoveryClient");
        RouteVM noParse = ReflectionTestUtils.invokeMethod(resource, "toRouteVm", noBrackets);
        assertThat(noParse.getPath()).isNull();
        assertThat(noParse.getServiceId()).isNull();

        Route parsed = mock(Route.class);
        when(parsed.getPredicate()).thenReturn(named("Paths: [/services/store/**]"));
        when(parsed.getId()).thenReturn("ReactiveCompositeDiscoveryClient_STORE");
        RouteVM vm = ReflectionTestUtils.invokeMethod(resource, "toRouteVm", parsed);
        assertThat(vm.getPath()).isEqualTo("/services/store/**");
        assertThat(vm.getServiceId()).isEqualTo("store");
    }

    private static AsyncPredicate<ServerWebExchange> named(String label) {
        return new AsyncPredicate<>() {
            @Override
            public Mono<Boolean> apply(ServerWebExchange exchange) {
                return Mono.just(true);
            }

            @Override
            public String toString() {
                return label;
            }
        };
    }
}
