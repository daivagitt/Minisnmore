package com.miniandmore.gateway.security;

import com.miniandmore.gateway.config.ApplicationProperties;
import com.miniandmore.gateway.config.RateLimitingProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.netty.util.NetUtil;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.PathContainer;
import org.springframework.http.server.reactive.AbstractServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.DefaultCorsProcessor;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.netty.http.server.HttpServerRequest;

/** Runs before Spring Security, covering both local controllers and proxied routes. */
@Component
@Order(-110)
public class ApiRateLimitWebFilter implements WebFilter {

    private final RateLimitingProperties properties;
    private final RedisRequestLimiter limiter;
    private final MeterRegistry meters;
    private final CorsConfigurationSource cors;

    public ApiRateLimitWebFilter(
        ApplicationProperties properties, RedisRequestLimiter limiter, MeterRegistry meters, CorsConfigurationSource cors
    ) {
        this.properties = properties.getRateLimiting();
        this.limiter = limiter;
        this.meters = meters;
        this.cors = cors;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        // Match WebFlux's decoded segments, including matrix parameter removal.
        String path = exchange.getRequest().getPath().pathWithinApplication().elements().stream()
            .map(element -> element instanceof PathContainer.PathSegment segment ? segment.valueToMatch() : element.value())
            .collect(Collectors.joining()).replaceAll("/+$", "");
        if (!properties.isEnabled() || HttpMethod.OPTIONS.equals(exchange.getRequest().getMethod()) || !isApi(path)) {
            return chain.filter(exchange);
        }
        String policyName = policyName(path, exchange.getRequest().getMethod());
        String client = clientAddress(exchange.getRequest());
        // The general cap also covers specialized endpoints, except provider webhooks.
        Mono<Long> decision;
        if ("webhook".equals(policyName)) {
            decision = check(policyName, client);
        } else {
            decision = limiter.check(properties.getKeyPrefix() + "general:" + client, properties.getGeneral());
            if (!"general".equals(policyName)) {
                decision = decision.flatMap(wait -> wait > 0 ? Mono.just(wait) : check(policyName, client));
            }
        }
        // Handle Redis errors here, never catch/retry failures from downstream handlers.
        return decision.onErrorResume(error -> {
            meters.counter("gateway.rate.limit.errors").increment();
            return Mono.just(properties.isFailOpen() ? 0L : -1L);
        }).flatMap(wait -> {
            if (wait == 0) {
                return chain.filter(exchange);
            }
            boolean unavailable = wait < 0;
            meters.counter("gateway.rate.limit.rejections", "policy", policyName, "reason", unavailable ? "unavailable" : "limit").increment();
            if (!new DefaultCorsProcessor().process(cors.getCorsConfiguration(exchange), exchange)) {
                return exchange.getResponse().setComplete();
            }
            exchange.getResponse().setStatusCode(unavailable ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.TOO_MANY_REQUESTS);
            exchange.getResponse().getHeaders().set("Retry-After", Long.toString(unavailable ? 1 : wait));
            exchange.getResponse().getHeaders().setCacheControl("no-store");
            exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
            String body = unavailable
                ? "{\"status\":503,\"title\":\"Service temporarily unavailable\",\"detail\":\"Please try again shortly.\"}"
                : "{\"status\":429,\"title\":\"Too many requests\",\"detail\":\"Too many requests. Please wait and try again.\"}";
            return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8))));
        });
    }

    private Mono<Long> check(String name, String client) {
        RateLimitingProperties.Policy policy = switch (name) {
            case "auth" -> properties.getAuth();
            case "orders" -> properties.getOrders();
            case "payment" -> properties.getPayment();
            case "webhook" -> properties.getWebhook();
            case "admin" -> properties.getAdmin();
            default -> properties.getGeneral();
        };
        return limiter.check(properties.getKeyPrefix() + name + ":" + client, policy);
    }

    static boolean isApi(String path) {
        // Cover service-prefixed discovery routes as well as direct APIs.
        return path.startsWith("/api/") || path.startsWith("/internal/") || path.startsWith("/services/") ||
            path.startsWith("/oauth2/authorization/") || path.startsWith("/login/oauth2/");
    }

    static String policyName(String path, HttpMethod method) {
        path = path.replaceFirst("^/services/[^/]+/", "/");
        if (path.startsWith("/api/v1/webhooks/") || path.startsWith("/internal/v1/webhooks/") ||
            path.equals("/internal/v1/ledgerly/webhooks") ||
            path.matches("/(?:api|internal)/v1/zoho/(?:stock|shipment|item|order-status|invoice)-webhook")) return "webhook";
        if (path.startsWith("/oauth2/") || path.startsWith("/login/oauth2/")) return "auth";
        if (HttpMethod.POST.equals(method) && (path.equals("/api/authenticate") || path.equals("/api/register") ||
            path.equals("/api/users") || path.startsWith("/api/account/reset-password/") ||
            path.startsWith("/api/account/invite/") || path.equals("/api/account/resend-activation"))) return "auth";
        if (HttpMethod.POST.equals(method) && path.equals("/api/v1/orders")) return "orders";
        if (path.equals("/api/v1/orders/sync-payment") || path.equals("/api/v1/orders/verify-payment") ||
            path.equals("/api/v1/orders/retry-payment")) return "payment";
        if (path.startsWith("/api/v1/admin/")) return "admin";
        return "general";
    }

    String clientAddress(ServerHttpRequest request) {
        InetSocketAddress peer = transportAddress(request);
        String address = peer == null || peer.getAddress() == null ? "unknown" : peer.getAddress().getHostAddress();
        // Only an explicitly trusted proxy may supply X-Real-IP. Never use the client-controlled XFF chain.
        if (properties.getTrustedProxyAddresses().contains(address)) {
            String forwarded = request.getHeaders().getFirst("X-Real-IP");
            byte[] bytes = forwarded == null ? null : NetUtil.createByteArrayFromIpAddressString(forwarded);
            if (bytes != null) {
                try {
                    return InetAddress.getByAddress(bytes).getHostAddress();
                } catch (UnknownHostException ignored) {
                    // NetUtil only returns valid IPv4/IPv6 lengths.
                }
            }
        }
        return address;
    }

    private static InetSocketAddress transportAddress(ServerHttpRequest request) {
        ServerHttpRequest raw = request;
        while (raw instanceof ServerHttpRequestDecorator decorator) raw = decorator.getDelegate();
        if (raw instanceof AbstractServerHttpRequest nativeRequest) {
            Object underlying;
            try {
                underlying = nativeRequest.getNativeRequest();
            } catch (IllegalStateException unsupported) {
                // Mock requests have no native transport.
                return raw.getRemoteAddress();
            }
            if (underlying instanceof HttpServerRequest netty) {
                // getRemoteAddress() may already have been rewritten by native forwarded-header handling.
                AtomicReference<InetSocketAddress> peer = new AtomicReference<>();
                netty.withConnection(connection -> {
                    if (connection.channel().remoteAddress() instanceof InetSocketAddress socket) peer.set(socket);
                });
                return peer.get();
            }
        }
        return raw.getRemoteAddress();
    }
}
