package com.miniandmore.gateway.repository.rowmapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.miniandmore.gateway.domain.User;
import io.r2dbc.spi.Row;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UserRowMapperTest {

    private UserRowMapper userRowMapper;
    private ColumnConverter columnConverter;

    @BeforeEach
    void setUp() {
        columnConverter = mock(ColumnConverter.class);
        userRowMapper = new UserRowMapper(columnConverter);
    }

    @Test
    void apply_mapsAllFields() {
        Row row = mock(Row.class);
        when(row.get("u_id", Long.class)).thenReturn(1L);
        when(columnConverter.fromRow(row, "u_login", String.class)).thenReturn("user");
        when(columnConverter.fromRow(row, "u_password", String.class)).thenReturn("hash");
        when(columnConverter.fromRow(row, "u_first_name", String.class)).thenReturn("John");
        when(columnConverter.fromRow(row, "u_last_name", String.class)).thenReturn("Doe");
        when(columnConverter.fromRow(row, "u_email", String.class)).thenReturn("user@localhost");
        when(columnConverter.fromRow(row, "u_activated", Boolean.class)).thenReturn(true);
        when(columnConverter.fromRow(row, "u_subscribed", Boolean.class)).thenReturn(true);
        when(columnConverter.fromRow(row, "u_lang_key", String.class)).thenReturn("en");
        when(columnConverter.fromRow(row, "u_image_url", String.class)).thenReturn("img");
        when(columnConverter.fromRow(row, "u_activation_key", String.class)).thenReturn("key");
        when(columnConverter.fromRow(row, "u_reset_key", String.class)).thenReturn("reset");
        when(columnConverter.fromRow(row, "u_reset_date", Instant.class)).thenReturn(Instant.parse("2020-01-01T00:00:00Z"));

        User user = userRowMapper.apply(row, "u");

        assertThat(user.getId()).isEqualTo(1L);
        assertThat(user.getLogin()).isEqualTo("user");
        assertThat(user.getPassword()).isEqualTo("hash");
        assertThat(user.getFirstName()).isEqualTo("John");
        assertThat(user.getLastName()).isEqualTo("Doe");
        assertThat(user.getEmail()).isEqualTo("user@localhost");
        assertThat(user.isActivated()).isTrue();
        assertThat(user.isSubscribed()).isTrue();
        assertThat(user.getLangKey()).isEqualTo("en");
        assertThat(user.getImageUrl()).isEqualTo("img");
        assertThat(user.getActivationKey()).isEqualTo("key");
        assertThat(user.getResetKey()).isEqualTo("reset");
        assertThat(user.getResetDate()).isEqualTo(Instant.parse("2020-01-01T00:00:00Z"));
    }
}
