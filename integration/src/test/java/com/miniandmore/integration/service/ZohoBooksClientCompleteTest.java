package com.miniandmore.integration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.miniandmore.integration.domain.ZohoSetting;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
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
class ZohoBooksClientCompleteTest {

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
        when(credentialResolver.resolve(any())).thenReturn(resolved());
        when(accessTokenCache.getOrRefresh(any(), any())).thenReturn("token");
        when(accessTokenCache.hasValidEntry(any())).thenReturn(true);
    }

    @Test
    void get_withOrgAndQueryParams() throws Exception {
        HttpResponse<String> ok = stringResponse(200, "{\"page_context\":{}}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(ok);

        assertThat(client.get(setting, "items", java.util.Map.of("page", "2", "per_page", "50"))).isNotNull();
    }

    @Test
    void post_withNullBody() throws Exception {
        HttpResponse<String> ok = stringResponse(200, "{}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(ok);

        assertThat(client.post(setting, "/contacts", java.util.Map.of(), null)).isNotNull();
    }

    @Test
    void uploadItemImage_retries401() throws Exception {
        HttpResponse<String> unauthorized = stringResponse(401, "{}");
        HttpResponse<String> ok = stringResponse(200, "{}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(unauthorized, ok);
        when(accessTokenCache.getOrRefresh(any(), any(), any(Boolean.class))).thenReturn("token-2");

        client.uploadItemImage(setting, "item-9", new byte[] { 9 }, "photo.png", "image/png");
    }

    @Test
    void downloadItemImage_retries401() throws Exception {
        HttpResponse<InputStream> unauthorized = binaryResponse(401);
        HttpResponse<InputStream> ok = binaryResponse(200, new byte[] { 1 });
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(unauthorized, ok);
        when(accessTokenCache.getOrRefresh(any(), any(), any(Boolean.class))).thenReturn("token-2");

        assertThat(client.downloadItemImage(setting, "item-9", 5000)).isPresent();
    }

    @Test
    void zohoMessage_formatsErrorsArray() throws Exception {
        ObjectNode json = new ObjectMapper().createObjectNode();
        json.put("code", 57);
        json.putArray("errors").add("Line error");
        String message = (String) invokePrivate("zohoMessage", json);
        assertThat(message).contains("57").contains("Line error");
    }

    @Test
    void isMissingZohoItem_checksCause() {
        assertThat(ZohoBooksClient.isMissingZohoItem(new RuntimeException(new ZohoBooksException("HTTP 400 does not exist")))).isTrue();
    }

    private Object invokePrivate(String name, Object arg) throws Exception {
        Method method = ZohoBooksClient.class.getDeclaredMethod(name, com.fasterxml.jackson.databind.JsonNode.class);
        method.setAccessible(true);
        return method.invoke(client, arg);
    }

    private static HttpResponse<String> stringResponse(int status, String body) {
        HttpResponse<String> response = org.mockito.Mockito.mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        return response;
    }

    private static HttpResponse<InputStream> binaryResponse(int status) {
        return binaryResponse(status, new byte[0]);
    }

    private static HttpResponse<InputStream> binaryResponse(int status, byte[] bytes) {
        HttpResponse<InputStream> response = org.mockito.Mockito.mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(new ByteArrayInputStream(bytes));
        when(response.headers()).thenReturn(
            java.net.http.HttpHeaders.of(java.util.Map.of("Content-Type", java.util.List.of("image/png")), (a, b) -> true)
        );
        return response;
    }

    private static ZohoCredentialResolver.Resolved resolved() {
        return new ZohoCredentialResolver.Resolved("client", "secret", "refresh", "org-1", "eu");
    }

    private static ZohoSetting connected() {
        ZohoSetting s = new ZohoSetting();
        s.setConnected(true);
        s.setRefreshToken("r");
        s.setClientId("c");
        s.setClientSecret("s");
        s.setOrgId("o");
        s.setDataCenter("eu");
        return s;
    }
}
