package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.POST;

import com.miniandmore.app.config.ApplicationProperties;
import com.miniandmore.app.domain.ZohoSetting;
import com.miniandmore.app.repository.ZohoSettingRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

@ExtendWith(MockitoExtension.class)
class ZohoOAuthServiceTest {

    @Mock
    private ZohoSettingRepository settingRepository;

    @Mock
    private ZohoSettingsService zohoSettingsService;

    private ApplicationProperties applicationProperties;
    private MockRestServiceServer server;
    private ZohoOAuthService service;

    @BeforeEach
    void setUp() {
        applicationProperties = new ApplicationProperties();
        ApplicationProperties.Zoho.OAuth oauth = applicationProperties.getZoho().getOauth();
        oauth.setClientId("server-client-id");
        oauth.setClientSecret("server-client-secret");
        oauth.setOrgId("env-org-id");
        oauth.setDefaultDataCenter("in");
        oauth.setHomepageUrl("http://localhost:5173");
        oauth.setRedirectUris(List.of("http://localhost:5173", "http://localhost:5173/admin/zoho"));
        oauth.setScopes("ZohoBooks.fullaccess.all");

        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        service = new ZohoOAuthService(settingRepository, applicationProperties, builder, zohoSettingsService);
    }

    @Test
    void isServerConfigured_whenCredentialsPresent_returnsTrue() {
        assertThat(service.isServerConfigured()).isTrue();
    }

    @Test
    void isServerConfigured_whenSecretMissing_returnsFalse() {
        applicationProperties.getZoho().getOauth().setClientSecret("");

        assertThat(service.isServerConfigured()).isFalse();
    }

    @Test
    void effectiveClientId_prefersDatabaseOverServer() {
        ZohoSetting setting = new ZohoSetting().clientId("db-client-id");
        when(settingRepository.findActiveOrLatest()).thenReturn(Optional.of(setting));

        assertThat(service.effectiveClientId()).isEqualTo("db-client-id");
        assertThat(service.effectiveClientSecret()).isEqualTo("server-client-secret");
    }

    @Test
    void effectiveClientId_returnsNullWhenNeitherConfigured() {
        applicationProperties.getZoho().getOauth().setClientId("");
        applicationProperties.getZoho().getOauth().setClientSecret("");
        when(settingRepository.findActiveOrLatest()).thenReturn(Optional.empty());

        assertThat(service.effectiveClientId()).isNull();
        assertThat(service.effectiveClientSecret()).isNull();
    }

    @Test
    void effectiveOrgId_prefersDatabaseThenEnv() {
        when(settingRepository.findActiveOrLatest()).thenReturn(Optional.empty());

        assertThat(service.effectiveOrgId()).isEqualTo("env-org-id");

        ZohoSetting setting = new ZohoSetting().orgId("db-org-id");
        when(settingRepository.findActiveOrLatest()).thenReturn(Optional.of(setting));

        assertThat(service.effectiveOrgId()).isEqualTo("db-org-id");
    }

    @Test
    void effectiveDataCenter_usesStoredValueOtherwiseDefault() {
        when(settingRepository.findActiveOrLatest()).thenReturn(Optional.empty());
        assertThat(service.effectiveDataCenter()).isEqualTo("in");

        ZohoSetting setting = new ZohoSetting().dataCenter("eu");
        when(settingRepository.findActiveOrLatest()).thenReturn(Optional.of(setting));
        assertThat(service.effectiveDataCenter()).isEqualTo("eu");
    }

    @Test
    void accountsHost_normalizesKnownAndUnknownDataCenters() {
        assertThat(service.accountsHost("eu")).isEqualTo("https://accounts.zoho.eu");
        assertThat(service.accountsHost("com")).isEqualTo("https://accounts.zoho.com");
        assertThat(service.accountsHost("com.au")).isEqualTo("https://accounts.zoho.com.au");
        assertThat(service.accountsHost("unknown")).isEqualTo("https://accounts.zoho.in");
        assertThat(service.apiHost(null)).isEqualTo("https://www.zohoapis.in");
    }

    @Test
    void oauthSetup_exposesServerConfigurationMetadata() {
        Map<String, Object> setup = service.oauthSetup();

        assertThat(setup.get("serverConfigured")).isEqualTo(true);
        assertThat(setup.get("homepageUrl")).isEqualTo("http://localhost:5173");
        assertThat(setup.get("scopes")).isEqualTo("ZohoBooks.fullaccess.all");
        assertThat((List<String>) setup.get("redirectUris")).contains("http://localhost:5173/admin/zoho");
    }

    @Test
    void buildConnectUrl_includesClientScopeAndRedirectUri() {
        String url = service.buildConnectUrl("http://localhost:5173/admin/zoho");

        assertThat(url).startsWith("https://accounts.zoho.in/oauth/v2/auth?");
        assertThat(url).contains("client_id=server-client-id");
        assertThat(url).contains("scope=ZohoBooks.fullaccess.all");
        assertThat(url).contains("redirect_uri=http://localhost:5173/admin/zoho");
        assertThat(url).contains("access_type=offline");
        assertThat(url).contains("prompt=consent");
    }

    @Test
    void buildConnectUrl_throwsWhenClientIdMissing() {
        applicationProperties.getZoho().getOauth().setClientId("");
        when(settingRepository.findActiveOrLatest()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.buildConnectUrl("http://localhost:5173/admin/zoho"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Zoho client id is not configured");
    }

    @Test
    void accessToken_throwsWhenRefreshTokenMissing() {
        when(settingRepository.findActiveOrLatest()).thenReturn(Optional.empty());

        assertThatThrownBy(service::accessToken)
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Zoho is not connected (no refresh token on file)");
    }

    @Test
    void accessToken_refreshesAndCachesToken() {
        ZohoSetting setting = connectedSetting();
        when(settingRepository.findActiveOrLatest()).thenReturn(Optional.of(setting));

        server
            .expect(requestTo("https://accounts.zoho.in/oauth/v2/token"))
            .andExpect(method(POST))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
            .andRespond(withSuccess("{\"access_token\":\"live-token\",\"expires_in\":3600}", MediaType.APPLICATION_JSON));

        assertThat(service.accessToken()).isEqualTo("live-token");
        assertThat(service.accessToken()).isEqualTo("live-token");

        server.verify();
    }

    @Test
    void completeOAuth_throwsWhenCredentialsMissing() {
        applicationProperties.getZoho().getOauth().setClientSecret("");
        when(settingRepository.findActiveOrLatest()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.completeOAuth("auth-code", "http://localhost:5173/admin/zoho"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Zoho client id/secret are not configured");
    }

    @Test
    void completeOAuth_throwsWhenRefreshTokenMissingInResponse() {
        when(settingRepository.findActiveOrLatest()).thenReturn(Optional.empty());

        server
            .expect(requestTo("https://accounts.zoho.in/oauth/v2/token"))
            .andExpect(method(POST))
            .andRespond(withSuccess("{\"access_token\":\"only-access\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> service.completeOAuth("auth-code", "http://localhost:5173/admin/zoho"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Zoho did not return a refresh token");
    }

    @Test
    void completeOAuth_persistsRefreshTokenAndMarksConnected() {
        ZohoSetting setting = new ZohoSetting().orgId("env-org-id");
        when(settingRepository.findActiveOrLatest()).thenReturn(Optional.of(setting));
        when(settingRepository.save(any(ZohoSetting.class))).thenAnswer(invocation -> invocation.getArgument(0));

        server
            .expect(requestTo("https://accounts.zoho.in/oauth/v2/token"))
            .andExpect(method(POST))
            .andRespond(
                withSuccess("{\"refresh_token\":\"refresh-1\",\"access_token\":\"access-1\",\"expires_in\":3600}", MediaType.APPLICATION_JSON)
            );
        server
            .expect(requestTo("https://www.zohoapis.in/books/v3/organizations"))
            .andRespond(
                withSuccess("{\"organizations\":[{\"organization_id\":\"env-org-id\",\"name\":\"Mini\"}]}", MediaType.APPLICATION_JSON)
            );

        service.completeOAuth("auth-code", "http://localhost:5173/admin/zoho");

        verify(settingRepository).save(
            argThat(saved ->
                "refresh-1".equals(saved.getRefreshToken()) &&
                Boolean.TRUE.equals(saved.getConnected()) &&
                "in".equals(saved.getDataCenter()) &&
                "env-org-id".equals(saved.getOrgId())
            )
        );
    }

    @Test
    void completeOAuth_replacesInaccessibleOrgWhenReconnectAuthorizesDifferentAccount() {
        ZohoSetting setting = new ZohoSetting().orgId("old-org").connected(false);
        when(settingRepository.findActiveOrLatest()).thenReturn(Optional.of(setting));
        when(settingRepository.save(any(ZohoSetting.class))).thenAnswer(invocation -> invocation.getArgument(0));

        server
            .expect(requestTo("https://accounts.zoho.in/oauth/v2/token"))
            .andExpect(method(POST))
            .andRespond(
                withSuccess("{\"refresh_token\":\"refresh-2\",\"access_token\":\"access-2\",\"expires_in\":3600}", MediaType.APPLICATION_JSON)
            );
        server
            .expect(requestTo("https://www.zohoapis.in/books/v3/organizations"))
            .andRespond(
                withSuccess("{\"organizations\":[{\"organization_id\":\"new-org\",\"name\":\"New Org\"}]}", MediaType.APPLICATION_JSON)
            );

        service.completeOAuth("auth-code", "http://localhost:5173/admin/zoho");

        verify(settingRepository).save(
            argThat(saved ->
                "refresh-2".equals(saved.getRefreshToken()) &&
                "new-org".equals(saved.getOrgId()) &&
                Boolean.TRUE.equals(saved.getConnected())
            )
        );
    }

    @Test
    void orgIdAndDataCenter_preferStoredSettingValues() {
        ZohoSetting setting = connectedSetting().orgId("stored-org").dataCenter("eu");
        when(settingRepository.findActiveOrLatest()).thenReturn(Optional.of(setting));

        assertThat(service.orgId()).isEqualTo("stored-org");
        assertThat(service.dataCenter()).isEqualTo("eu");
    }

    private static ZohoSetting connectedSetting() {
        return new ZohoSetting().refreshToken("refresh-token").connected(true).dataCenter("in");
    }
}
