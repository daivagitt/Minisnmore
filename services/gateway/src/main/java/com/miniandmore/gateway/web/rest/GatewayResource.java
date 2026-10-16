package com.miniandmore.gateway.web.rest;

import com.miniandmore.gateway.security.AuthoritiesConstants;
import com.miniandmore.gateway.web.rest.vm.RouteVM;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * REST controller for managing Gateway configuration.
 */
@RestController
@RequestMapping("/api/gateway")
public class GatewayResource {

    private final RouteLocator routeLocator;

    private final DiscoveryClient discoveryClient;

    @Value("${spring.application.name}")
    private String appName;

    public GatewayResource(RouteLocator routeLocator, DiscoveryClient discoveryClient) {
        this.routeLocator = routeLocator;
        this.discoveryClient = discoveryClient;
    }

    /**
     * {@code GET  /routes} : get the active routes.
     *
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the list of routes.
     */
    @GetMapping("/routes")
    @Secured(AuthoritiesConstants.ADMIN)
    public Mono<ResponseEntity<java.util.List<RouteVM>>> activeRoutes() {
        return routeLocator
            .getRoutes()
            .map(this::toRouteVm)
            .filter(routeVM -> routeVM.getServiceId() != null && !routeVM.getServiceId().equalsIgnoreCase(appName))
            .flatMap(routeVM ->
                Mono.fromCallable(() -> discoveryClient.getInstances(routeVM.getServiceId()))
                    .subscribeOn(Schedulers.boundedElastic())
                    .doOnNext(routeVM::setServiceInstances)
                    .thenReturn(routeVM)
            )
            .collectList()
            .map(ResponseEntity::ok);
    }

    private RouteVM toRouteVm(Route route) {
        RouteVM routeVM = new RouteVM();
        String predicate = route.getPredicate() != null ? route.getPredicate().toString() : "";
        int open = predicate.indexOf('[');
        int close = predicate.indexOf(']');
        if (open >= 0 && close > open) {
            routeVM.setPath(predicate.substring(open + 1, close));
        }
        String routeId = route.getId() != null ? route.getId() : "";
        int underscore = routeId.indexOf('_');
        if (underscore >= 0 && underscore < routeId.length() - 1) {
            routeVM.setServiceId(routeId.substring(underscore + 1).toLowerCase());
        }
        return routeVM;
    }
}
