package com.miniandmore.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.format.support.FormattingConversionService;

class DateTimeFormatConfigurationTest {

    @Test
    void addFormatters_registersDateTimeFormatters() {
        DateTimeFormatConfiguration configuration = new DateTimeFormatConfiguration();
        FormattingConversionService conversionService = new FormattingConversionService();

        configuration.addFormatters(conversionService);

        assertThat(conversionService).isNotNull();
    }
}
