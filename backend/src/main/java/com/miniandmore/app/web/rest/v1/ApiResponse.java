package com.miniandmore.app.web.rest.v1;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ApiResponse<T>(boolean success, T data, Map<String, Object> meta) {
    public ApiResponse {
        meta = meta == null ? Map.of() : Map.copyOf(meta);
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, buildMeta());
    }

    private static Map<String, Object> buildMeta() {
        return Map.of("requestId", UUID.randomUUID().toString(), "timestamp", Instant.now().toString());
    }
}
