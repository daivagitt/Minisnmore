package com.miniandmore.app.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ApplicationPropertiesTest {

    @Test
    void nestedProperties_haveDefaultsAndSetters() {
        ApplicationProperties properties = new ApplicationProperties();

        assertThat(properties.getLiquibase().getAsyncStart()).isTrue();
        properties.getLiquibase().setAsyncStart(false);
        assertThat(properties.getLiquibase().getAsyncStart()).isFalse();

        assertThat(properties.getStorage().getS3().getRegion()).isEqualTo("us-east-1");
        properties.getStorage().getS3().setBucket("bucket");
        assertThat(properties.getStorage().getS3().getBucket()).isEqualTo("bucket");

        assertThat(properties.getInventory()).isNotNull();
        properties.getInventory().getReservationRelease().setFixedDelayMs(120_000L);
        assertThat(properties.getInventory().getReservationRelease().getFixedDelayMs()).isEqualTo(120_000L);

        assertThat(properties.getMail().getConfigChangeTo()).isEqualTo("info@stoltglobal.com,saikedar2003@gmail.com");
        properties.getMail().setConfigChangeTo("ops@example.com");
        assertThat(properties.getMail().getConfigChangeTo()).isEqualTo("ops@example.com");
    }
}
