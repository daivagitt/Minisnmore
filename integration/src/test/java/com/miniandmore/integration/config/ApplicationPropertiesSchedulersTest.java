package com.miniandmore.integration.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ApplicationPropertiesSchedulersTest {

    @Test
    void zohoOrderSchedulerDefaultsAndSetters() {
        ApplicationProperties props = new ApplicationProperties();
        assertThat(props.getZoho().getOrderRetry().getFixedDelayMs()).isPositive();
        assertThat(props.getZoho().getOrderTracking().getFixedDelayMs()).isPositive();

        props.getZoho().getOrderRetry().setFixedDelayMs(120_000L);
        props.getZoho().getOrderTracking().setFixedDelayMs(90_000L);

        assertThat(props.getZoho().getOrderRetry().getFixedDelayMs()).isEqualTo(120_000L);
        assertThat(props.getZoho().getOrderTracking().getFixedDelayMs()).isEqualTo(90_000L);
    }
}
