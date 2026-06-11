package com.miniandmore.app.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class InternalLinkValidatorTest {

    @Test
    void isValid_blankOrNull_isAllowed() {
        assertThat(InternalLinkValidator.isValid(null)).isTrue();
        assertThat(InternalLinkValidator.isValid("  ")).isTrue();
    }

    @Test
    void isValid_internalPath_isAllowed() {
        assertThat(InternalLinkValidator.isValid("/products/dress")).isTrue();
        assertThat(InternalLinkValidator.isValid("/")).isTrue();
    }

    @Test
    void isValid_externalScheme_isRejected() {
        assertThat(InternalLinkValidator.isValid("https://example.com")).isFalse();
        assertThat(InternalLinkValidator.isValid("javascript:alert(1)")).isFalse();
        assertThat(InternalLinkValidator.isValid("//evil.com")).isFalse();
    }
}
