package com.miniandmore.integration.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.integration.domain.ZohoSetting;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.function.Supplier;
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
class ZohoBooksClientRefreshTest {

    @Mock
    private ZohoCredentialResolver credentialResolver;

    @Mock
    private ZohoAccessTokenCache accessTokenCache;

    @Mock
    private HttpClient httpClient;

    private ZohoBooksClient client;

    @BeforeEach
    void setUp() {
        client = new ZohoBooksClient(new ObjectMapper(), credentialResolver, accessTokenCache);
        ReflectionTestUtils.setField(client, "httpClient", httpClient);
        ZohoCredentialResolver.Resolved credentials = new ZohoCredentialResolver.Resolved("client-abcdefgh", "s", "r", "o", "in");
        when(credentialResolver.resolve(any())).thenReturn(credentials);
        when(accessTokenCache.hasValidEntry(any())).thenReturn(false);
        when(accessTokenCache.getOrRefresh(any(), any())).thenAnswer(invocation -> {
            Supplier<ZohoAccessTokenCache.TokenBundle> supplier = invocation.getArgument(1);
            return supplier.get().accessToken();
        });
        when(accessTokenCache.getOrRefresh(any(), any(), any(Boolean.class))).thenAnswer(invocation -> {
            Supplier<ZohoAccessTokenCache.TokenBundle> supplier = invocation.getArgument(1);
            return supplier.get().accessToken();
        });
    }

    @Test
    void get_refreshTokenAccessDeniedMessage() throws Exception {
        HttpResponse<String> tokenFail = stringResponse(400, "{\"message\":\"Access Denied\",\"error\":\"invalid_grant\"}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(tokenFail);

        assertThatThrownBy(() -> client.get(connected(), "/items", java.util.Map.of()))
            .isInstanceOf(ZohoBooksException.class)
            .hasMessageContaining("token refresh failed")
            .hasMessageContaining("Connect to Zoho");
    }

    @Test
    void warmAccessToken_triggersRefresh() throws Exception {
        HttpResponse<String> tokenOk = stringResponse(200, "{\"access_token\":\"at\",\"expires_in\":3600}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(tokenOk);

        client.warmAccessToken(connected());
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
