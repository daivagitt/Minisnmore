package com.miniandmore.integration.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ZohoBooksExceptionTest {

    @Test
    void messageOnly() {
        ZohoBooksException ex = new ZohoBooksException("failed");
        assertThat(ex).hasMessage("failed").hasNoCause();
    }

    @Test
    void messageWithCause() {
        RuntimeException cause = new RuntimeException("root");
        ZohoBooksException ex = new ZohoBooksException("failed", cause);
        assertThat(ex).hasMessage("failed").hasCause(cause);
    }
}
