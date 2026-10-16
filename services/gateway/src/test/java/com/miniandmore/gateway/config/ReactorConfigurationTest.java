package com.miniandmore.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ReactorConfigurationTest {

    @Test
    void reactorConfiguration_canBeInstantiated() {
        assertThat(new ReactorConfiguration()).isNotNull();
    }
}
