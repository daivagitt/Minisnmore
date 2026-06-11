package com.miniandmore.integration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.integration.domain.ZohoSetting;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ZohoBooksClientTest {

    @Mock
    private ZohoCredentialResolver credentialResolver;

    @Mock
    private ZohoAccessTokenCache accessTokenCache;

    private ZohoBooksClient client;

    @BeforeEach
    void setUp() {
        client = new ZohoBooksClient(new ObjectMapper(), credentialResolver, accessTokenCache);
    }

    @Test
    void uploadItemImage_emptyBytesThrows() {
        ZohoSetting setting = connectedSetting();
        assertThatThrownBy(() -> client.uploadItemImage(setting, "item-1", new byte[0], "a.jpg", "image/jpeg"))
            .isInstanceOf(ZohoBooksException.class)
            .hasMessageContaining("empty");
    }

    @Test
    void downloadItemImage_blankIdReturnsEmpty() {
        assertThat(client.downloadItemImage(connectedSetting(), "  ", 1024)).isEmpty();
    }

    @Test
    void downloadDocument_blankIdReturnsEmpty() {
        assertThat(client.downloadDocument(connectedSetting(), null, 1024)).isEmpty();
    }

    @Test
    void isOAuthAccessDenied_detectsRefreshFailures() {
        assertThat(
            ZohoBooksClient.isOAuthAccessDenied(new ZohoBooksException("Zoho token refresh failed: Access Denied invalid_grant"))
        ).isTrue();
        assertThat(ZohoBooksClient.isOAuthAccessDenied(new ZohoBooksException("other"))).isFalse();
        assertThat(ZohoBooksClient.isOAuthAccessDenied(null)).isFalse();
    }

    @Test
    void isMissingZohoItem_detectsHttp400() {
        assertThat(ZohoBooksClient.isMissingZohoItem(new ZohoBooksException("HTTP 400 item does not exist"))).isTrue();
        assertThat(ZohoBooksClient.isMissingZohoItem(new ZohoBooksException("HTTP 500"))).isFalse();
    }

    @Test
    void sanitizeMultipartFilename_stripsUnsafeChars() {
        assertThat(ZohoBooksClient.sanitizeMultipartFilename(null)).isEqualTo("product.jpg");
        assertThat(ZohoBooksClient.sanitizeMultipartFilename("bad\"\nname.png")).isEqualTo("badname.png");
        assertThat(ZohoBooksClient.sanitizeMultipartFilename("   ")).isEqualTo("product.jpg");
    }

    @Test
    void get_notConnectedThrows() {
        ZohoSetting setting = new ZohoSetting();
        setting.setConnected(false);
        assertThatThrownBy(() -> client.get(setting, "/items", java.util.Map.of())).isInstanceOf(ZohoBooksException.class);
    }

    @Test
    void get_incompleteCredentialsThrows() {
        ZohoSetting setting = connectedSetting();
        ZohoCredentialResolver.Resolved incomplete = mock(ZohoCredentialResolver.Resolved.class);
        when(incomplete.isComplete()).thenReturn(false);
        when(credentialResolver.resolve(setting)).thenReturn(incomplete);
        assertThatThrownBy(() -> client.get(setting, "/items", java.util.Map.of()))
            .isInstanceOf(ZohoBooksException.class)
            .hasMessageContaining("incomplete");
    }

    private static ZohoSetting connectedSetting() {
        ZohoSetting setting = new ZohoSetting();
        setting.setConnected(true);
        setting.setRefreshToken("refresh");
        setting.setClientId("id");
        setting.setClientSecret("secret");
        setting.setOrgId("org");
        setting.setDataCenter("in");
        return setting;
    }
}
