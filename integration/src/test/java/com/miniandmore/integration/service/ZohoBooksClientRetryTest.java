package com.miniandmore.integration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
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
class ZohoBooksClientRetryTest {

    @Mock
    private ZohoCredentialResolver credentialResolver;

    @Mock
    private ZohoAccessTokenCache accessTokenCache;

    @Mock
    private HttpClient httpClient;

    private ZohoBooksClient client;
    private ZohoSetting setting;

    @BeforeEach
    void setUp() {
        client = new ZohoBooksClient(new ObjectMapper(), credentialResolver, accessTokenCache);
        ReflectionTestUtils.setField(client, "httpClient", httpClient);
        setting = connected();
        ZohoCredentialResolver.Resolved credentials = new ZohoCredentialResolver.Resolved("c", "s", "r", "o", "in");
        when(credentialResolver.resolve(setting)).thenReturn(credentials);
        when(accessTokenCache.getOrRefresh(any(), any())).thenReturn("token-1", "token-2");
    }

    @Test
    void get_retriesOn401() throws Exception {
        HttpResponse<String> unauthorized = stringResponse(401, "{}");
        HttpResponse<String> ok = stringResponse(200, "{\"items\":[]}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(unauthorized, ok);

        assertThat(client.get(setting, "/items", java.util.Map.of()).path("items").isArray()).isTrue();
    }

    @Test
    void downloadItemImage_404ReturnsEmpty() throws Exception {
        HttpResponse<InputStream> notFound = org.mockito.Mockito.mock(HttpResponse.class);
        when(notFound.statusCode()).thenReturn(404);
        when(notFound.body()).thenReturn(InputStream.nullInputStream());
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(notFound);

        assertThat(client.downloadItemImage(setting, "item-1", 1024)).isEmpty();
    }

    @Test
    void downloadItemImage_jsonBodyReturnsEmpty() throws Exception {
        HttpResponse<InputStream> jsonResponse = org.mockito.Mockito.mock(HttpResponse.class);
        when(jsonResponse.statusCode()).thenReturn(200);
        when(jsonResponse.body()).thenReturn(new ByteArrayInputStream("{\"message\":\"no image\"}".getBytes()));
        when(jsonResponse.headers()).thenReturn(
            java.net.http.HttpHeaders.of(java.util.Map.of("Content-Type", java.util.List.of("application/json")), (a, b) -> true)
        );
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(jsonResponse);

        assertThat(client.downloadItemImage(setting, "item-1", 1024)).isEmpty();
    }

    @Test
    void downloadDocument_http400ReturnsEmpty() throws Exception {
        HttpResponse<InputStream> error = org.mockito.Mockito.mock(HttpResponse.class);
        when(error.statusCode()).thenReturn(400);
        when(error.body()).thenReturn(InputStream.nullInputStream());
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(error);

        assertThat(client.downloadDocument(setting, "doc-1", 1024)).isEmpty();
    }

    @Test
    void downloadDocument_httpErrorThrows() throws Exception {
        HttpResponse<InputStream> error = org.mockito.Mockito.mock(HttpResponse.class);
        when(error.statusCode()).thenReturn(500);
        when(error.body()).thenReturn(InputStream.nullInputStream());
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(error);

        assertThatThrownBy(() -> client.downloadDocument(setting, "doc-1", 1024)).isInstanceOf(ZohoBooksException.class);
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
        s.setDataCenter("in");
        return s;
    }
}
