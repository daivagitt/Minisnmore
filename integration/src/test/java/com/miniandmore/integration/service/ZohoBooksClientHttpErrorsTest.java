package com.miniandmore.integration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.miniandmore.integration.domain.ZohoSetting;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ZohoBooksClientHttpErrorsTest {

    @Mock
    private ZohoCredentialResolver credentialResolver;

    @Mock
    private ZohoAccessTokenCache accessTokenCache;

    @Mock
    private HttpClient httpClient;

    private ZohoBooksClient client;
    private ZohoSetting setting;
    private ZohoCredentialResolver.Resolved credentials;

    @BeforeEach
    void setUp() {
        client = new ZohoBooksClient(new ObjectMapper(), credentialResolver, accessTokenCache);
        ReflectionTestUtils.setField(client, "httpClient", httpClient);
        setting = connected();
        credentials = new ZohoCredentialResolver.Resolved("client-id-longer", "secret", "refresh", "org-99", "com");
        when(credentialResolver.resolve(setting)).thenReturn(credentials);
        when(accessTokenCache.getOrRefresh(any(), any())).thenReturn("token-1", "token-2");
        when(accessTokenCache.hasValidEntry(any())).thenReturn(false, true);
    }

    @Test
    void get_httpErrorIncludesZohoMessage() throws Exception {
        HttpResponse<String> error = stringResponse(400, "{\"message\":\"Bad\",\"code\":100}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(error);

        assertThatThrownBy(() -> client.get(setting, "/items", java.util.Map.of("page", "1")))
            .isInstanceOf(ZohoBooksException.class)
            .hasMessageContaining("HTTP 400")
            .hasMessageContaining("Bad");
    }

    @Test
    void put_withJsonBody() throws Exception {
        HttpResponse<String> ok = stringResponse(200, "{\"item\":{\"item_id\":\"9\"}}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(ok);
        ObjectNode body = new ObjectMapper().createObjectNode().put("name", "Hat");

        assertThat(client.put(setting, "/items/9", java.util.Map.of(), body).path("item").path("item_id").asText()).isEqualTo("9");
    }

    @Test
    void get_retriesAfter401() throws Exception {
        HttpResponse<String> unauthorized = stringResponse(401, "{}");
        HttpResponse<String> ok = stringResponse(200, "{\"contacts\":[]}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(unauthorized, ok);
        when(accessTokenCache.getOrRefresh(any(), any(), eq(true))).thenReturn("token-2");

        assertThat(client.get(setting, "/contacts", java.util.Map.of()).path("contacts").isArray()).isTrue();
    }

    @Test
    void exchangeAuthorizationCode_httpFailure() throws Exception {
        HttpResponse<String> error = stringResponse(400, "{\"message\":\"invalid_code\",\"code\":120}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(error);

        assertThatThrownBy(() -> client.exchangeAuthorizationCode("com", "c", "s", "http://cb", "bad"))
            .isInstanceOf(ZohoBooksException.class)
            .hasMessageContaining("authorization failed");
    }

    @Test
    void uploadItemImage_httpFailure() throws Exception {
        HttpResponse<String> error = stringResponse(500, "{\"message\":\"fail\"}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(error);

        assertThatThrownBy(() -> client.uploadItemImage(setting, "item-1", new byte[] { 1 }, "a.jpg", "image/jpeg"))
            .isInstanceOf(ZohoBooksException.class)
            .hasMessageContaining("upload failed");
    }

    @Test
    void downloadItemImage_emptyBodyReturnsEmpty() throws Exception {
        HttpResponse<InputStream> ok = org.mockito.Mockito.mock(HttpResponse.class);
        when(ok.statusCode()).thenReturn(200);
        when(ok.body()).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(ok.headers()).thenReturn(
            java.net.http.HttpHeaders.of(java.util.Map.of("Content-Type", java.util.List.of("image/png")), (a, b) -> true)
        );
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(ok);

        assertThat(client.downloadItemImage(setting, "item-1", 1024)).isEmpty();
    }

    @Test
    void get_interruptedWrapsException() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenThrow(new InterruptedException("stop"));

        assertThatThrownBy(() -> client.get(setting, "/items", java.util.Map.of()))
            .isInstanceOf(ZohoBooksException.class)
            .hasMessageContaining("interrupted");
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        Thread.interrupted();
    }

    private static HttpResponse<String> stringResponse(int status, String body) {
        HttpResponse<String> response = org.mockito.Mockito.mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        return response;
    }

    private static ZohoSetting connected() {
        ZohoSetting s = new ZohoSetting();
        s.setConnected(true);
        s.setRefreshToken("r");
        s.setClientId("c");
        s.setClientSecret("s");
        s.setOrgId("o");
        s.setDataCenter("com");
        return s;
    }
}
