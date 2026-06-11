package com.miniandmore.integration.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ApplicationPropertiesTest {

    @Test
    void defaultsAndNestedSetters() {
        ApplicationProperties props = new ApplicationProperties();
        assertThat(props.getInternalApiKey()).isEmpty();
        assertThat(props.getLiquibase().getAsyncStart()).isTrue();
        assertThat(props.getStorage().getS3().getRegion()).isEqualTo("us-east-1");
        assertThat(props.getZoho().isSyncProductImages()).isTrue();
        assertThat(props.getZoho().getDefaultLocationName()).isEqualTo("Head Office");
        assertThat(props.getInventory().getReservationRelease().getFixedDelayMs()).isEqualTo(300_000L);

        props.setInternalApiKey("key");
        props.getZoho().setSyncProductStock(false);
        props.getZoho().getOauth().setClientId("cid");
        props.getZoho().getOauth().setRedirectUris(List.of("https://app/admin/zoho"));
        props.getStorage().getS3().setBucket("b");

        assertThat(props.getInternalApiKey()).isEqualTo("key");
        assertThat(props.getZoho().isSyncProductStock()).isFalse();
        assertThat(props.getZoho().getOauth().getClientId()).isEqualTo("cid");
        assertThat(props.getStorage().getS3().getBucket()).isEqualTo("b");

        props.getLiquibase().setAsyncStart(false);
        assertThat(props.getLiquibase().getAsyncStart()).isFalse();
    }
}
