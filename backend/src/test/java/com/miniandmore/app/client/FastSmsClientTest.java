package com.miniandmore.app.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.config.ApplicationProperties;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FastSmsClientTest {

    private FastSmsClient client;

    @BeforeEach
    void setUp() {
        ApplicationProperties properties = new ApplicationProperties();
        ApplicationProperties.FastSms config = properties.getFastsms();
        config.setEnabled(true);
        config.setAccessToken("token");
        config.setAccessTokenKey("key");
        config.setSmsHeader("MINISM");
        config.setRoute("transactional");
        client = new FastSmsClient(properties, new ObjectMapper());
    }

    @Test
    void isConfigured_requiresEnabledCredentialsAndHeader() {
        assertThat(client.isConfigured()).isTrue();

        ApplicationProperties empty = new ApplicationProperties();
        FastSmsClient disabled = new FastSmsClient(empty, new ObjectMapper());
        assertThat(disabled.isConfigured()).isFalse();
    }

    @Test
    void normalizeMobile_acceptsIndiaFormats() {
        assertThat(FastSmsClient.normalizeMobile("9876543210")).isEqualTo("9876543210");
        assertThat(FastSmsClient.normalizeMobile("+91 98765 43210")).isEqualTo("9876543210");
        assertThat(FastSmsClient.normalizeMobile("919876543210")).isEqualTo("9876543210");
        assertThat(FastSmsClient.normalizeMobile("09876543210")).isEqualTo("9876543210");
        assertThat(FastSmsClient.normalizeMobile("12345")).isNull();
        assertThat(FastSmsClient.normalizeMobile("5999999999")).isNull();
    }

    @Test
    void parseResponse_success() {
        FastSmsClient.SendResult result = client.parseResponse(
            200,
            "{\"status\":\"success\",\"message\":\"SMS queued\",\"data\":[]}"
        );
        assertThat(result.success()).isTrue();
        assertThat(result.message()).isEqualTo("SMS queued");
    }

    @Test
    void parseResponse_invalidNumbers() {
        FastSmsClient.SendResult result = client.parseResponse(
            400,
            "{\"status\":\"failed\",\"message\":\"Invalid phone number(s).\",\"data\":{\"invalidNumbers\":[\"9999999999\"]}}"
        );
        assertThat(result.success()).isFalse();
        assertThat(result.invalidNumbers()).containsExactly("9999999999");
    }

    @Test
    void sendSms_whenNotConfigured_returnsEmpty() {
        ApplicationProperties empty = new ApplicationProperties();
        FastSmsClient disabled = new FastSmsClient(empty, new ObjectMapper());
        Optional<FastSmsClient.SendResult> result = disabled.sendSms("9876543210", "hello");
        assertThat(result).isEmpty();
    }
}
