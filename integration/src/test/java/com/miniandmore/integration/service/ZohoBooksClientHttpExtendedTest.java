package com.miniandmore.integration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.integration.domain.ZohoSetting;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
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
class ZohoBooksClientHttpExtendedTest {

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
        setting = connectedSetting();
        credentials = new ZohoCredentialResolver.Resolved("client", "secret", "refresh", "org", "in");
        when(credentialResolver.resolve(setting)).thenReturn(credentials);
        when(accessTokenCache.getOrRefresh(any(), any())).thenReturn("access-token");
    }

    @Test
    void post_put_andWarmToken() throws Exception {
        HttpResponse<String> ok = stringResponse(200, "{\"item\":{\"item_id\":\"1\"}}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(ok);

        assertThat(client.post(setting, "/items", java.util.Map.of(), new ObjectMapper().createObjectNode()).path("item").path("item_id").asText())
            .isEqualTo("1");
        assertThat(client.put(setting, "/items/1", java.util.Map.of(), new ObjectMapper().createObjectNode()).path("item").path("item_id").asText())
            .isEqualTo("1");
        client.warmAccessToken(setting);
    }

    @Test
    void downloadItemImage_returnsBytes() throws Exception {
        byte[] png = new byte[] { (byte) 0x89, 0x50, 0x4e, 0x47 };
        HttpResponse<InputStream> binary = org.mockito.Mockito.mock(HttpResponse.class);
        when(binary.statusCode()).thenReturn(200);
        when(binary.body()).thenReturn(new ByteArrayInputStream(png));
        when(binary.headers()).thenReturn(java.net.http.HttpHeaders.of(java.util.Map.of("Content-Type", java.util.List.of("image/png")), (a, b) -> true));
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(binary);

        assertThat(client.downloadItemImage(setting, "item-1", 1024)).isPresent();
    }

    @Test
    void uploadItemImage_success() throws Exception {
        HttpResponse<String> ok = stringResponse(200, "{}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(ok);

        client.uploadItemImage(setting, "item-1", new byte[] { 1, 2, 3 }, "a.png", "image/png");
    }

    private static HttpResponse<String> stringResponse(int status, String body) {
        HttpResponse<String> response = org.mockito.Mockito.mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        return response;
    }

    private static ZohoSetting connectedSetting() {
        ZohoSetting s = new ZohoSetting();
        s.setConnected(true);
        s.setRefreshToken("refresh");
        s.setClientId("id");
        s.setClientSecret("secret");
        s.setOrgId("org");
        s.setDataCenter("in");
        return s;
    }
}
