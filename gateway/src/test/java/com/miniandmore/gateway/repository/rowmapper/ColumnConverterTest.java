package com.miniandmore.gateway.repository.rowmapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.miniandmore.gateway.config.DatabaseConfiguration;
import io.r2dbc.spi.Row;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.convert.ConversionService;
import org.springframework.data.r2dbc.convert.MappingR2dbcConverter;
import org.springframework.data.r2dbc.convert.R2dbcCustomConversions;
import org.springframework.data.r2dbc.dialect.PostgresDialect;
import org.springframework.data.r2dbc.mapping.R2dbcMappingContext;

class ColumnConverterTest {

    private ColumnConverter columnConverter;

    @BeforeEach
    void setUp() {
        R2dbcMappingContext context = new R2dbcMappingContext();
        context.setSimpleTypeHolder(PostgresDialect.INSTANCE.getSimpleTypeHolder());
        context.afterPropertiesSet();
        R2dbcCustomConversions conversions = new DatabaseConfiguration().r2dbcCustomConversions(PostgresDialect.INSTANCE);
        MappingR2dbcConverter r2dbcConverter = new MappingR2dbcConverter(context, conversions);
        columnConverter = new ColumnConverter(conversions, r2dbcConverter);
    }

    @Test
    void convert_returnsAssignableValue() {
        assertThat(columnConverter.convert("value", String.class)).isEqualTo("value");
    }

    @Test
    void convert_usesConversionService() {
        ConversionService conversionService = mock(ConversionService.class);
        R2dbcCustomConversions conversions = mock(R2dbcCustomConversions.class);
        MappingR2dbcConverter r2dbcConverter = mock(MappingR2dbcConverter.class);
        when(r2dbcConverter.getConversionService()).thenReturn(conversionService);
        when(conversions.hasCustomReadTarget(Long.class, String.class)).thenReturn(true);
        when(conversionService.convert(1L, String.class)).thenReturn("1");
        ColumnConverter converter = new ColumnConverter(conversions, r2dbcConverter);

        assertThat(converter.convert(1L, String.class)).isEqualTo("1");
    }

    @Test
    void convert_enumValue() {
        enum Sample {
            A,
            B
        }

        assertThat(columnConverter.convert("A", Sample.class)).isEqualTo(Sample.A);
    }

    @Test
    void fromRow_fallsBackWhenDirectReadFails() {
        Row row = mock(Row.class);
        when(row.get("col", String.class)).thenThrow(new RuntimeException("fail"));
        when(row.get("col")).thenReturn("text");

        assertThat(columnConverter.fromRow(row, "col", String.class)).isEqualTo("text");
    }
}
