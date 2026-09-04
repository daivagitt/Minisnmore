package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class InvalidBannerLinkExceptionTest {

    @Test
    void messageExplainsOnSitePathRequirement() {
        InvalidBannerLinkException ex = new InvalidBannerLinkException();

        assertThat(ex.getMessage()).contains("on-site path");
        assertThat(ex.getMessage()).contains("/shop");
    }
}
