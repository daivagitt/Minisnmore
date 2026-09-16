package com.miniandmore.app.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class StorefrontCacheControlFilterTest {

    private final StorefrontCacheControlFilter filter = new StorefrontCacheControlFilter();

    @Test
    void mediaPreviewIsNotForcedNoStore() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/v1/media/preview");
        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    @Test
    void storefrontProductJsonIsStillFiltered() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/v1/products");
        assertThat(filter.shouldNotFilter(request)).isFalse();
    }
}
