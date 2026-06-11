package com.miniandmore.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import static org.mockito.Mockito.mock;

import java.util.BitSet;
import org.junit.jupiter.api.Test;
import org.springframework.data.r2dbc.convert.MappingR2dbcConverter;
import org.springframework.data.r2dbc.dialect.PostgresDialect;
import org.springframework.data.r2dbc.dialect.R2dbcDialect;

class DatabaseConfigurationTest {

    @Test
    void converters_roundTripValues() {
        Instant instant = Instant.parse("2020-01-01T12:00:00Z");
        LocalDateTime localDateTime = DatabaseConfiguration.InstantWriteConverter.INSTANCE.convert(instant);
        assertThat(DatabaseConfiguration.InstantReadConverter.INSTANCE.convert(localDateTime)).isEqualTo(instant);

        BitSet bitSet = new BitSet();
        bitSet.set(0);
        assertThat(DatabaseConfiguration.BitSetReadConverter.INSTANCE.convert(bitSet)).isTrue();

        ZonedDateTime zoned = ZonedDateTime.of(localDateTime, ZoneOffset.UTC);
        assertThat(DatabaseConfiguration.ZonedDateTimeReadConverter.INSTANCE.convert(localDateTime)).isEqualTo(zoned);
        assertThat(DatabaseConfiguration.ZonedDateTimeWriteConverter.INSTANCE.convert(zoned)).isEqualTo(localDateTime);

        Duration duration = Duration.ofMillis(1500);
        assertThat(DatabaseConfiguration.DurationWriteConverter.INSTANCE.convert(duration)).isEqualTo(1500L);
        assertThat(DatabaseConfiguration.DurationReadConverter.INSTANCE.convert(1500L)).isEqualTo(duration);
        assertThat(DatabaseConfiguration.DurationWriteConverter.INSTANCE.convert(null)).isNull();
        assertThat(DatabaseConfiguration.DurationReadConverter.INSTANCE.convert(null)).isNull();
    }

    @Test
    void r2dbcCustomConversions_registersAllConverters() {
        DatabaseConfiguration configuration = new DatabaseConfiguration();
        assertThat(configuration.r2dbcCustomConversions(PostgresDialect.INSTANCE)).isNotNull();
    }

    @Test
    void beans_createUpdateMapperAndSqlRenderer() {
        DatabaseConfiguration configuration = new DatabaseConfiguration();
        R2dbcDialect dialect = PostgresDialect.INSTANCE;
        MappingR2dbcConverter mappingConverter = mock(MappingR2dbcConverter.class);

        assertThat(configuration.updateMapper(dialect, mappingConverter)).isNotNull();
        assertThat(configuration.sqlRenderer(dialect)).isNotNull();
    }
}
