package com.miniandmore.app.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Prevent browsers and intermediaries from caching live storefront catalog JSON.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class StorefrontCacheControlFilter extends OncePerRequestFilter {

    private static final String CACHE_HEADER = "Cache-Control";
    private static final String NO_STORE = "no-store, no-cache, must-revalidate";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        if (path == null || !path.startsWith("/internal/v1/")) {
            return true;
        }
        return (
            path.startsWith("/internal/v1/admin/") ||
            path.startsWith("/internal/v1/account/") ||
            path.startsWith("/internal/v1/zoho/") ||
            path.startsWith("/internal/v1/ledgerly/") ||
            path.startsWith("/internal/v1/order-returns/")
        );
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        filterChain.doFilter(request, response);
        response.setHeader(CACHE_HEADER, NO_STORE);
    }
}
