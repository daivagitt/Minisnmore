package com.miniandmore.integration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.miniandmore.integration.config.ApplicationProperties;
import com.miniandmore.integration.domain.ZohoSetting;
import com.miniandmore.integration.repository.ZohoSettingRepository;
import com.miniandmore.integration.service.dto.ZohoSettingDTO;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ZohoOAuthServiceTest {

    @Mock
    private ZohoBooksClient zohoBooksClient;

    @Mock
    private ZohoSettingRepository zohoSettingRepository;

    @Mock
    private ZohoSettingService zohoSettingService;

    private ApplicationProperties applicationProperties;
    private ZohoOAuthService service;

    @BeforeEach
    void setUp() {
        applicationProperties = new ApplicationProperties();
        service = new ZohoOAuthService(applicationProperties, zohoBooksClient, zohoSettingRepository, zohoSettingService);
    }

    @Test
    void buildConnectUrl_requiresServerConfig() {
        assertThatThrownBy(() -> service.buildConnectUrl()).isInstanceOf(ZohoBooksException.class);
    }

    @Test
    void buildConnectUrl_includesClientAndRedirect() {
        configureServerOAuth();
        when(zohoSettingRepository.findActiveOrLatest()).thenReturn(Optional.empty());

        String url = service.buildConnectUrl();

        assertThat(url).contains("accounts.zoho.in/oauth/v2/auth");
        assertThat(url).contains("client_id=srv-client");
        assertThat(url).contains("redirect_uri=");
        assertThat(url).contains("scope=");
    }

    @Test
    void completeOAuth_savesSetting() {
        configureServerOAuth();
        when(zohoBooksClient.exchangeAuthorizationCode(any(), any(), any(), any(), any())).thenReturn("refresh-token");
        ZohoSettingDTO saved = new ZohoSettingDTO();
        saved.setId(1L);
        when(zohoSettingService.save(any())).thenReturn(saved);

        ZohoSettingDTO result = service.completeOAuth("auth-code");

        assertThat(result.getId()).isEqualTo(1L);
        verify(zohoSettingService).save(any(ZohoSettingDTO.class));
    }

    @Test
    void completeOAuth_blankCodeThrows() {
        configureServerOAuth();
        assertThatThrownBy(() -> service.completeOAuth("  ")).isInstanceOf(ZohoBooksException.class);
    }

    @Test
    void isConnected_whenActiveWithRefreshToken() {
        ZohoSetting setting = new ZohoSetting();
        setting.setConnected(true);
        setting.setRefreshToken("rt");
        when(zohoSettingRepository.findActiveOrLatest()).thenReturn(Optional.of(setting));
        assertThat(service.isConnected()).isTrue();
    }

    @Test
    void oauthRedirectUri_missingAdminPathThrows() {
        configureServerOAuth();
        applicationProperties.getZoho().getOauth().setRedirectUris(List.of("http://localhost:5173"));
        assertThatThrownBy(() -> service.oauthRedirectUri()).isInstanceOf(ZohoBooksException.class);
    }

    private void configureServerOAuth() {
        ApplicationProperties.Zoho.OAuth oauth = applicationProperties.getZoho().getOauth();
        oauth.setClientId("srv-client");
        oauth.setClientSecret("srv-secret");
        oauth.setOrgId("srv-org");
        oauth.setRedirectUris(List.of("http://localhost:5173/admin/zoho"));
    }
}
