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
class ZohoBooksClientHttpOAuthExtendedTest {

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
        when(credentialResolver.resolve(setting)).thenReturn(resolved());
        when(accessTokenCache.getOrRefresh(any(), any())).thenReturn("token-a", "token-b");
        when(accessTokenCache.hasValidEntry(any())).thenReturn(true);
    }

    @Test
    void exchangeAuthorizationCode_returnsRefreshToken() throws Exception {
        HttpResponse<String> ok = stringResponse(200, "{\"refresh_token\":\"rt-1\",\"access_token\":\"at\"}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(ok);

        assertThat(client.exchangeAuthorizationCode("in", "cid", "sec", "http://localhost/cb", "code-1")).isEqualTo("rt-1");
    }

    @Test
    void exchangeAuthorizationCode_missingRefreshTokenThrows() throws Exception {
        HttpResponse<String> ok = stringResponse(200, "{\"access_token\":\"only-access\"}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(ok);

        assertThatThrownBy(() -> client.exchangeAuthorizationCode("in", "cid", "sec", "http://localhost/cb", "code-2"))
            .isInstanceOf(ZohoBooksException.class)
            .hasMessageContaining("refresh token");
    }

    @Test
    void uploadItemImage_emptyBytesThrows() {
        assertThatThrownBy(() -> client.uploadItemImage(setting, "item-1", new byte[0], "a.jpg", "image/jpeg"))
            .isInstanceOf(ZohoBooksException.class)
            .hasMessageContaining("empty");
    }

    @Test
    void downloadItemImage_blankIdReturnsEmpty() {
        assertThat(client.downloadItemImage(setting, " ", 1024)).isEmpty();
    }

    @Test
    void downloadDocument_binary401Retries() throws Exception {
        HttpResponse<InputStream> unauthorized = binaryResponse(401);
        HttpResponse<InputStream> ok = binaryResponse(200, new byte[] { 1, 2 });
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(unauthorized, ok);

        assertThat(client.downloadDocument(setting, "doc-1", 5000)).isPresent();
    }

    @Test
    void downloadItemImage_exceedsMaxBytesThrows() throws Exception {
        byte[] huge = new byte[100];
        HttpResponse<InputStream> ok = org.mockito.Mockito.mock(HttpResponse.class);
        when(ok.statusCode()).thenReturn(200);
        when(ok.body()).thenReturn(new ByteArrayInputStream(huge));
        when(ok.headers()).thenReturn(
            java.net.http.HttpHeaders.of(java.util.Map.of("Content-Type", java.util.List.of("image/png")), (a, b) -> true)
        );
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(ok);

        assertThatThrownBy(() -> client.downloadItemImage(setting, "item-1", 10)).isInstanceOf(ZohoBooksException.class);
    }

    @Test
    void sanitizeMultipartFilename_stripsUnsafeCharacters() {
        assertThat(ZohoBooksClient.sanitizeMultipartFilename("bad\"\nname.png")).isEqualTo("badname.png");
        assertThat(ZohoBooksClient.sanitizeMultipartFilename(null)).isEqualTo("product.jpg");
        assertThat(ZohoBooksClient.sanitizeMultipartFilename("   ")).isEqualTo("product.jpg");
    }

    @Test
    void isOAuthAccessDenied_detectsNestedCause() {
        assertThat(
            ZohoBooksClient.isOAuthAccessDenied(
                new ZohoBooksException("Zoho token refresh failed: access denied invalid_grant")
            )
        )
            .isTrue();
        assertThat(ZohoBooksClient.isOAuthAccessDenied(new ZohoBooksException("other"))).isFalse();
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
        return new ZohoCredentialResolver.Resolved("c", "s", "r", "o", "in");
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
