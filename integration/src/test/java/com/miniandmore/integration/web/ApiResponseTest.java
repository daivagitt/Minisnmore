package com.miniandmore.integration.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ApiResponseTest {

    @Test
    void ok_wrapsDataWithMeta() {
        ApiResponse<String> response = ApiResponse.ok("payload");

        assertThat(response.success()).isTrue();
        assertThat(response.data()).isEqualTo("payload");
        assertThat(response.meta()).containsKeys("requestId", "timestamp");
    }

    @Test
    void constructor_copiesMeta() {
        Map<String, Object> meta = new java.util.HashMap<>();
        meta.put("key", "value");
        ApiResponse<Integer> response = new ApiResponse<>(false, 1, meta);

        assertThat(response.meta()).containsEntry("key", "value");
        meta.put("key", "mutated");
        assertThat(response.meta()).containsEntry("key", "value");
    }

    @Test
    void constructor_nullMetaBecomesEmpty() {
        ApiResponse<Object> response = new ApiResponse<>(true, null, null);
        assertThat(response.meta()).isEmpty();
    }
}
