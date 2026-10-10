package com.miniandmore.app.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;

class StorefrontCacheControlFilterPathMatrixTest {

    private final StorefrontCacheControlFilter filter = new StorefrontCacheControlFilter();

    @ParameterizedTest
    @ValueSource(strings = { "POST", "PUT", "DELETE", "PATCH", "HEAD" })
    void nonGetIsSkipped(String method) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, "/internal/v1/products");
        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { "/api/v1/products", "/internal/v2/products" })
    void nonInternalGetIsSkipped(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path == null ? null : path);
        if (path == null) {
            request.setRequestURI(null);
        }
        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    @ParameterizedTest
    @CsvSource(
        {
            "/internal/v1/admin/orders,true",
            "/internal/v1/account/me,true",
            "/internal/v1/zoho/webhook,true",
            "/internal/v1/ledgerly/webhook,true",
            "/internal/v1/order-returns/RET-1,true",
            "/internal/v1/media/preview,true",
            "/internal/v1/products,false",
            "/internal/v1/categories,false",
            "/internal/v1/home,false",
        }
    )
    void getPathsCoverAdminAndStorefront(String path, boolean skip) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        assertThat(filter.shouldNotFilter(request)).isEqualTo(skip);
    }

    @Test
    void getIsCaseInsensitive() {
        MockHttpServletRequest request = new MockHttpServletRequest("get", "/internal/v1/products");
        assertThat(filter.shouldNotFilter(request)).isFalse();
    }
}
