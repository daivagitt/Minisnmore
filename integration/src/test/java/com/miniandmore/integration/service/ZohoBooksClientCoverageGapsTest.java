package com.miniandmore.integration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.miniandmore.integration.domain.ZohoSetting;
import java.io.IOException;
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
class ZohoBooksClientCoverageGapsTest {

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
        when(accessTokenCache.hasValidEntry(any())).thenReturn(false);
    }

    @Test
    void sanitizeMultipartFilename_stripsControlCharacters() {
        assertThat(ZohoBooksClient.sanitizeMultipartFilename("bad\"\nname.jpg")).isEqualTo("badname.jpg");
        assertThat(ZohoBooksClient.sanitizeMultipartFilename(null)).isEqualTo("product.jpg");
        assertThat(ZohoBooksClient.sanitizeMultipartFilename("   ")).isEqualTo("product.jpg");
    }

    @Test
    void isOAuthAccessDenied_nullMessageUsesCause() {
        assertThat(ZohoBooksClient.isOAuthAccessDenied(new RuntimeException((String) null))).isFalse();
    }

    @Test
    void isMissingZohoItem_nullMessageUsesCause() {
        assertThat(ZohoBooksClient.isMissingZohoItem(new RuntimeException((String) null))).isFalse();
    }

    @Test
    void exchangeAuthorizationCode_missingRefreshAndAccessToken() throws Exception {
        HttpResponse<String> response = stringResponse(200, "{\"error\":\"invalid\"}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        assertThatThrownBy(() -> client.exchangeAuthorizationCode("in", "cid", "sec", "http://cb", "code"))
            .isInstanceOf(ZohoBooksException.class);
    }

    @Test
    void zohoMessage_nullJson() throws Exception {
        assertThat((String) invoke("zohoMessage", new ObjectMapper().missingNode())).isEqualTo("no response body");
    }

    @Test
    void parse_blankBody() throws Exception {
        JsonNode node = (JsonNode) invokeString("parse", "  ");
        assertThat(node.isObject()).isTrue();
    }

    @Test
    void exchangeAuthorizationCode_returnsRefreshToken() throws Exception {
        HttpResponse<String> response = stringResponse(200, "{\"refresh_token\":\"rt-1\",\"access_token\":\"at\"}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        assertThat(client.exchangeAuthorizationCode("in", "cid", "sec", "http://cb", "code")).isEqualTo("rt-1");
    }

    @Test
    void exchangeAuthorizationCode_accessTokenWithoutRefreshToken() throws Exception {
        HttpResponse<String> response = stringResponse(200, "{\"access_token\":\"at-only\"}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        assertThatThrownBy(() -> client.exchangeAuthorizationCode("in", "cid", "sec", "http://cb", "code"))
            .isInstanceOf(ZohoBooksException.class)
            .hasMessageContaining("refresh token");
    }

    @Test
    void exchangeAuthorizationCode_ioFailure() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenThrow(new IOException("net"));
        assertThatThrownBy(() -> client.exchangeAuthorizationCode("in", "cid", "sec", "http://cb", "code"))
            .isInstanceOf(ZohoBooksException.class)
            .hasMessageContaining("authorization failed");
    }

    @Test
    void exchangeAuthorizationCode_interrupted() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenThrow(new InterruptedException("stop"));
        assertThatThrownBy(() -> client.exchangeAuthorizationCode("in", "cid", "sec", "http://cb", "code"))
            .isInstanceOf(ZohoBooksException.class)
            .hasMessageContaining("interrupted");
    }

    @Test
    void get_requestIOException() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenThrow(new IOException("down"));
        assertThatThrownBy(() -> client.get(setting, "/items", java.util.Map.of()))
            .isInstanceOf(ZohoBooksException.class)
            .hasMessageContaining("request failed");
    }

    @Test
    void get_requestInterrupted() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenThrow(new InterruptedException("stop"));
        assertThatThrownBy(() -> client.get(setting, "/items", java.util.Map.of()))
            .isInstanceOf(ZohoBooksException.class)
            .hasMessageContaining("interrupted");
    }

    @Test
    void uploadItemImage_ioAndInterrupted() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenThrow(new IOException("up"));
        assertThatThrownBy(() -> client.uploadItemImage(setting, "i1", new byte[] { 1 }, "a.jpg", "image/jpeg"))
            .isInstanceOf(ZohoBooksException.class);

        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenThrow(new InterruptedException("up"));
        assertThatThrownBy(() -> client.uploadItemImage(setting, "i1", new byte[] { 1 }, "a.jpg", "image/jpeg"))
            .isInstanceOf(ZohoBooksException.class);
    }

    @Test
    void downloadItemImage_ioAndInterrupted() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenThrow(new IOException("down"));
        assertThatThrownBy(() -> client.downloadItemImage(setting, "i1", 1000))
            .isInstanceOf(ZohoBooksException.class);

        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenThrow(new InterruptedException("down"));
        assertThatThrownBy(() -> client.downloadItemImage(setting, "i1", 1000))
            .isInstanceOf(ZohoBooksException.class);
    }

    @Test
    void downloadDocument_ioAndInterrupted() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenThrow(new IOException("doc"));
        assertThatThrownBy(() -> client.downloadDocument(setting, "doc-1", 1000))
            .isInstanceOf(ZohoBooksException.class);

        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenThrow(new InterruptedException("doc"));
        assertThatThrownBy(() -> client.downloadDocument(setting, "doc-1", 1000))
            .isInstanceOf(ZohoBooksException.class);
    }

    @Test
    void isOAuthAccessDenied_followsCauseWhenMessageNull() {
        RuntimeException wrapper = new RuntimeException(new ZohoBooksException("token refresh failed: access denied"));
        assertThat(ZohoBooksClient.isOAuthAccessDenied(wrapper)).isTrue();
    }

    @Test
    void isMissingZohoItem_followsCauseWhenMessageNull() {
        RuntimeException wrapper = new RuntimeException(new ZohoBooksException("HTTP 400 does not exist"));
        assertThat(ZohoBooksClient.isMissingZohoItem(wrapper)).isTrue();
    }

    @Test
    void zohoMessage_formatsCodeOnlyAndErrorField() throws Exception {
        ObjectNode codeOnly = new ObjectMapper().createObjectNode();
        codeOnly.put("code", 99);
        assertThat((String) invoke("zohoMessage", codeOnly)).contains("99");

        ObjectNode errorOnly = new ObjectMapper().createObjectNode();
        errorOnly.put("error", "invalid");
        assertThat((String) invoke("zohoMessage", errorOnly)).isEqualTo("invalid");

        ObjectNode raw = new ObjectMapper().createObjectNode().put("foo", "bar");
        assertThat((String) invoke("zohoMessage", raw)).contains("bar");
    }

    @Test
    void safeParse_returnsEmptyOnBadJson() throws Exception {
        JsonNode node = (JsonNode) invokeString("safeParse", "{");
        assertThat(node.isObject()).isTrue();
    }

    @Test
    void get_pathWithoutLeadingSlash() throws Exception {
        HttpResponse<String> ok = stringResponse(200, "{}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(ok);
        assertThat(client.get(setting, "items", java.util.Map.of())).isNotNull();
    }

    @Test
    void tokenRefresh_nonAccessDeniedMessage() throws Exception {
        HttpResponse<String> bad = stringResponse(400, "{\"message\":\"server busy\"}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(bad);
        when(accessTokenCache.getOrRefresh(any(), any())).thenAnswer(inv -> {
            java.util.function.Supplier<?> supplier = inv.getArgument(1);
            return supplier.get();
        });

        assertThatThrownBy(() -> client.get(setting, "/items", java.util.Map.of()))
            .isInstanceOf(ZohoBooksException.class)
            .hasMessageContaining("server busy");
    }

    private Object invoke(String name, Object arg) throws Exception {
        Method method = ZohoBooksClient.class.getDeclaredMethod(name, com.fasterxml.jackson.databind.JsonNode.class);
        method.setAccessible(true);
        return method.invoke(client, arg);
    }

    private Object invokeString(String name, String arg) throws Exception {
        Method method = ZohoBooksClient.class.getDeclaredMethod(name, String.class);
        method.setAccessible(true);
        return method.invoke(client, arg);
    }

    private static HttpResponse<String> stringResponse(int status, String body) {
        HttpResponse<String> response = org.mockito.Mockito.mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        return response;
    }

    private static ZohoCredentialResolver.Resolved resolved() {
        return new ZohoCredentialResolver.Resolved("client-id-long", "secret", "refresh", "org-1", "in");
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
