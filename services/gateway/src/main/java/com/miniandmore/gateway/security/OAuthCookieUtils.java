package com.miniandmore.gateway.security;

import java.io.Serializable;
import java.util.Base64;
import java.util.Optional;
import org.apache.commons.lang3.SerializationUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;

final class OAuthCookieUtils {

    private OAuthCookieUtils() {}

    static Optional<String> getCookieValue(ServerHttpRequest request, String name) {
        if (request.getCookies().getFirst(name) == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(request.getCookies().getFirst(name).getValue());
    }

    static void addCookie(ServerWebExchange exchange, String name, String value, int maxAgeSeconds) {
        ServerHttpResponse response = exchange.getResponse();
        ResponseCookie cookie = ResponseCookie.from(name, value)
            .path("/")
            .maxAge(maxAgeSeconds)
            .httpOnly(true)
            .secure(isSecure(exchange.getRequest()))
            .sameSite("Lax")
            .build();
        response.getHeaders().add(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    static void deleteCookie(ServerWebExchange exchange, String name) {
        ServerHttpResponse response = exchange.getResponse();
        ResponseCookie cookie = ResponseCookie.from(name, "")
            .path("/")
            .maxAge(0)
            .httpOnly(true)
            .secure(isSecure(exchange.getRequest()))
            .sameSite("Lax")
            .build();
        response.getHeaders().add(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    /** Nginx terminates TLS; honor X-Forwarded-Proto so OAuth state cookies work on HTTPS UAT/prod. */
    static boolean isSecure(ServerHttpRequest request) {
        if (request.getSslInfo() != null) {
            return true;
        }
        String forwarded = request.getHeaders().getFirst("X-Forwarded-Proto");
        return forwarded != null && forwarded.equalsIgnoreCase("https");
    }

    static String serialize(Object object) {
        return Base64.getUrlEncoder().encodeToString(SerializationUtils.serialize((Serializable) object));
    }

    static <T> T deserialize(String cookieValue, Class<T> type) {
        byte[] bytes = Base64.getUrlDecoder().decode(cookieValue);
        Object value = SerializationUtils.deserialize(bytes);
        return type.cast(value);
    }
}
