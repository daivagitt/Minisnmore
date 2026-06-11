package com.miniandmore.integration.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.integration.domain.ZohoSetting;
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
class ZohoBooksClientOAuthTest {

    @Mock
    private ZohoCredentialResolver credentialResolver;

    @Mock
    private ZohoAccessTokenCache accessTokenCache;

    @Mock
    private HttpClient httpClient;

    @Mock
    private HttpResponse<String> response;

    private ZohoBooksClient client;

    @BeforeEach
    void setUp() {
        client = new ZohoBooksClient(new ObjectMapper(), credentialResolver, accessTokenCache);
        ReflectionTestUtils.setField(client, "httpClient", httpClient);
    }

    @Test
    void exchangeAuthorizationCode_httpError() throws Exception {
        when(response.statusCode()).thenReturn(400);
        when(response.body()).thenReturn("{\"message\":\"denied\"}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        assertThatThrownBy(() -> client.exchangeAuthorizationCode("in", "c", "s", "http://x", "code"))
            .isInstanceOf(ZohoBooksException.class);
    }

    @Test
    void get_notConnected() {
        ZohoSetting setting = new ZohoSetting();
        setting.setConnected(false);
        assertThatThrownBy(() -> client.get(setting, "/x", java.util.Map.of())).isInstanceOf(ZohoBooksException.class);
    }
}
