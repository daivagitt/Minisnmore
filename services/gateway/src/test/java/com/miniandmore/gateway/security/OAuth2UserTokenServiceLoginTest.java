package com.miniandmore.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OAuth2UserTokenServiceLoginTest {

    @Test
    void loginForEmail_keepsShortEmails() {
        assertThat(OAuth2UserTokenService.loginForEmail("User@Example.com")).isEqualTo("user@example.com");
    }

    @Test
    void loginForEmail_truncatesLongEmails() {
        String email = "a".repeat(45) + "@example.com";
        assertThat(OAuth2UserTokenService.loginForEmail(email)).hasSize(50);
    }
}
