package com.miniandmore.app.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.config.ApplicationProperties;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

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

    @Test
    void sendSms_invalidMobile_returnsEmpty() {
        assertThat(client.sendSms("12345", "hello")).isEmpty();
    }

    @Test
    void sendSms_blankMessage_returnsEmpty() {
        assertThat(client.sendSms("9876543210", "  ")).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void sendSms_success_postsJsonAndParsesResponse() throws Exception {
        HttpClient http = mock(HttpClient.class);
        ReflectionTestUtils.setField(client, "httpClient", http);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"status\":\"success\",\"message\":\"SMS queued\"}");
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        Optional<FastSmsClient.SendResult> result = client.sendSms("9876543210", "Order confirmed");

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().success()).isTrue();
        assertThat(result.orElseThrow().message()).isEqualTo("SMS queued");
    }

    @Test
    @SuppressWarnings("unchecked")
    void sendSms_httpFailure_returnsFailedResult() throws Exception {
        HttpClient http = mock(HttpClient.class);
        ReflectionTestUtils.setField(client, "httpClient", http);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(500);
        when(response.body()).thenReturn("{\"status\":\"failed\",\"message\":\"down\"}");
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        Optional<FastSmsClient.SendResult> result = client.sendSms("9876543210", "hello");

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().success()).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    void sendSms_transportError_returnsEmpty() throws Exception {
        HttpClient http = mock(HttpClient.class);
        ReflectionTestUtils.setField(client, "httpClient", http);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenThrow(new RuntimeException("network down"));

        assertThat(client.sendSms("9876543210", "hello")).isEmpty();
    }

    @Test
    void parseResponse_malformedJson_abbreviatesMessage() {
        FastSmsClient.SendResult result = client.parseResponse(200, "not-json");
        assertThat(result.success()).isFalse();
        assertThat(result.message()).isEqualTo("not-json");
    }

    @Test
    void sendSms_includesTemplateAndEntityWhenConfigured() throws Exception {
        ApplicationProperties properties = new ApplicationProperties();
        ApplicationProperties.FastSms config = properties.getFastsms();
        config.setEnabled(true);
        config.setAccessToken("token");
        config.setAccessTokenKey("key");
        config.setSmsHeader("MINISM");
        config.setRoute("transactional");
        config.setTemplateId("tpl-1");
        config.setEntityId("ent-1");
        FastSmsClient configured = new FastSmsClient(properties, new ObjectMapper());
        HttpClient http = mock(HttpClient.class);
        ReflectionTestUtils.setField(configured, "httpClient", http);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"status\":\"success\",\"message\":\"ok\"}");
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        assertThat(configured.sendSms("9876543210", "hello", "tpl-override", null)).isPresent();
    }
}
