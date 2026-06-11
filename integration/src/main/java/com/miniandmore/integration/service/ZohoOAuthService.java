package com.miniandmore.integration.service;

import com.miniandmore.integration.config.ApplicationProperties;
import com.miniandmore.integration.domain.ZohoSetting;
import com.miniandmore.integration.repository.ZohoSettingRepository;
import com.miniandmore.integration.service.dto.ZohoSettingDTO;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * One-click Zoho OAuth: credentials live in server config; admin only signs in to Zoho.
 */
@Service
public class ZohoOAuthService {

    private final ApplicationProperties applicationProperties;
    private final ZohoBooksClient zohoBooksClient;
    private final ZohoSettingRepository zohoSettingRepository;
    private final ZohoSettingService zohoSettingService;

    public ZohoOAuthService(
        ApplicationProperties applicationProperties,
        ZohoBooksClient zohoBooksClient,
        ZohoSettingRepository zohoSettingRepository,
        ZohoSettingService zohoSettingService
    ) {
        this.applicationProperties = applicationProperties;
        this.zohoBooksClient = zohoBooksClient;
        this.zohoSettingRepository = zohoSettingRepository;
        this.zohoSettingService = zohoSettingService;
    }

    public boolean isServerConfigured() {
        ApplicationProperties.Zoho.OAuth oauth = applicationProperties.getZoho().getOauth();
        return StringUtils.isNoneBlank(oauth.getClientId(), oauth.getClientSecret(), oauth.getOrgId());
    }

    public boolean isConnected() {
        return zohoSettingRepository
            .findActiveOrLatest()
            .filter(setting -> Boolean.TRUE.equals(setting.getConnected()))
            .filter(this::hasRefreshToken)
            .isPresent();
    }

    public String resolveDataCenter() {
        String fromDb = zohoSettingRepository.findActiveOrLatest().map(ZohoSetting::getDataCenter).orElse(null);
        if (StringUtils.isNotBlank(fromDb)) {
            return ZohoDataCenterHosts.normalize(fromDb);
        }
        return ZohoDataCenterHosts.normalize(applicationProperties.getZoho().getOauth().getDefaultDataCenter());
    }

    public String organizationId() {
        if (isServerConfigured()) {
            return applicationProperties.getZoho().getOauth().getOrgId();
        }
        return zohoSettingRepository.findActiveOrLatest().map(ZohoSetting::getOrgId).orElse(null);
    }

    public String buildConnectUrl() {
        requireServerConfigured();
        ApplicationProperties.Zoho.OAuth oauth = applicationProperties.getZoho().getOauth();
        String dataCenter = resolveDataCenter();
        String redirectUri = oauthRedirectUri();
        return (
            ZohoDataCenterHosts.authorizationUrl(dataCenter) +
            "?scope=" +
            encode(oauth.getScopes()) +
            "&client_id=" +
            encode(oauth.getClientId()) +
            "&response_type=code" +
            "&access_type=offline" +
            "&prompt=consent" +
            "&redirect_uri=" +
            encode(redirectUri)
        );
    }

    @Transactional
    public ZohoSettingDTO completeOAuth(String code) {
        requireServerConfigured();
        if (StringUtils.isBlank(code)) {
            throw new ZohoBooksException("Zoho authorization code is missing");
        }
        ApplicationProperties.Zoho.OAuth oauth = applicationProperties.getZoho().getOauth();
        String dataCenter = resolveDataCenter();
        String redirectUri = oauthRedirectUri();
        String refreshToken = zohoBooksClient.exchangeAuthorizationCode(
            dataCenter,
            oauth.getClientId(),
            oauth.getClientSecret(),
            redirectUri,
            code.trim()
        );

        ZohoSettingDTO dto = new ZohoSettingDTO();
        dto.setOrgId(oauth.getOrgId());
        dto.setClientId(oauth.getClientId());
        dto.setClientSecret(oauth.getClientSecret());
        dto.setRefreshToken(refreshToken);
        dto.setDataCenter(dataCenter);
        dto.setConnected(true);

        zohoSettingRepository.findActiveOrLatest().ifPresent(existing -> dto.setId(existing.getId()));
        ZohoSettingDTO saved = dto.getId() == null ? zohoSettingService.save(dto) : zohoSettingService.update(dto);
        return ZohoSettingDtoSanitizer.forResponse(saved);
    }

    public String oauthRedirectUri() {
        List<String> uris = applicationProperties.getZoho().getOauth().getRedirectUris();
        return uris
            .stream()
            .filter(uri -> uri != null && uri.endsWith("/admin/zoho"))
            .findFirst()
            .orElseThrow(() -> new ZohoBooksException("Missing Zoho OAuth redirect URI for /admin/zoho in application config"));
    }

    private void requireServerConfigured() {
        if (!isServerConfigured()) {
            throw new ZohoBooksException(
                "Zoho OAuth is not configured on the server. Set ZOHO_CLIENT_ID, ZOHO_CLIENT_SECRET, and ZOHO_ORG_ID."
            );
        }
    }

    private boolean hasRefreshToken(ZohoSetting setting) {
        return StringUtils.isNotBlank(setting.getRefreshToken());
    }

    private static String encode(String value) {
        return URLEncoder.encode(StringUtils.defaultString(value), StandardCharsets.UTF_8);
    }
}
