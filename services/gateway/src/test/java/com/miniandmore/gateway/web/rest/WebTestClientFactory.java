package com.miniandmore.gateway.web.rest;

import com.miniandmore.gateway.web.rest.errors.ExceptionTranslator;
import org.springframework.data.web.ReactivePageableHandlerMethodArgumentResolver;
import org.springframework.data.web.ReactiveSortHandlerMethodArgumentResolver;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.result.method.annotation.ArgumentResolverConfigurer;

final class WebTestClientFactory {

    private WebTestClientFactory() {}

    static WebTestClient bindToRestController(Object controller) {
        return bindBuilder(controller).build();
    }

    static WebTestClient.ControllerSpec bindBuilder(Object controller) {
        return WebTestClient.bindToController(controller)
            .controllerAdvice(new ExceptionTranslator(new MockEnvironment()))
            .argumentResolvers(WebTestClientFactory::registerPageableResolvers);
    }

    private static void registerPageableResolvers(ArgumentResolverConfigurer configurer) {
        configurer.addCustomResolver(new ReactivePageableHandlerMethodArgumentResolver());
        configurer.addCustomResolver(new ReactiveSortHandlerMethodArgumentResolver());
    }
}
