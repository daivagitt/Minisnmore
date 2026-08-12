package com.miniandmore.app.service;

import com.miniandmore.app.config.ApplicationProperties;
import com.miniandmore.app.domain.ZohoSetting;
import com.miniandmore.app.repository.ZohoSettingRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Zoho Books OAuth: builds the authorization URL, exchanges an auth code for a refresh token, and
 * resolves/caches short-lived access tokens from the stored refresh token.
 */
@Service
public class ZohoOAuthService {

    private final ZohoSettingRepository settingRepository;
    private final ApplicationProperties.Zoho.OAuth serverOAuth;
    private final RestClient restClient;
    private final Map<String, CachedToken> accessTokenCache = new ConcurrentHashMap<>();

    public ZohoOAuthService(
        ZohoSettingRepository settingRepository,
        ApplicationProperties applicationProperties,
        RestClient.Builder restClientBuilder
    ) {
        this.settingRepository = settingRepository;
        this.serverOAuth = applicationProperties.getZoho().getOauth();
        this.restClient = restClientBuilder.build();
    }

    /** Server-side OAuth app is configured when an admin never has to paste client id/secret. */
    public boolean isServerConfigured() {
        return StringUtils.isNotBlank(serverOAuth.getClientId()) && StringUtils.isNotBlank(serverOAuth.getClientSecret());
    }

    public String effectiveClientId() {
        return isServerConfigured() ? serverOAuth.getClientId() : settingRepository.findActiveOrLatest().map(ZohoSetting::getClientId).orElse(null);
    }

    public String effectiveClientSecret() {
        return isServerConfigured()
            ? serverOAuth.getClientSecret()
            : settingRepository.findActiveOrLatest().map(ZohoSetting::getClientSecret).orElse(null);
    }

    public String effectiveOrgId() {
        if (StringUtils.isNotBlank(serverOAuth.getOrgId())) {
            return serverOAuth.getOrgId();
        }
        return settingRepository.findActiveOrLatest().map(ZohoSetting::getOrgId).orElse(null);
    }

    public String effectiveDataCenter() {
        String stored = settingRepository.findActiveOrLatest().map(ZohoSetting::getDataCenter).filter(StringUtils::isNotBlank).orElse(null);
        return stored != null ? stored : StringUtils.defaultIfBlank(serverOAuth.getDefaultDataCenter(), "in");
    }

    public String accountsHost(String dataCenter) {
        return "https://accounts.zoho." + normalizeDataCenter(dataCenter);
    }

    public String apiHost(String dataCenter) {
        return "https://www.zohoapis." + normalizeDataCenter(dataCenter);
    }

    private String normalizeDataCenter(String dataCenter) {
        String dc = StringUtils.defaultIfBlank(dataCenter, "in").trim().toLowerCase();
        return switch (dc) {
            case "com", "eu", "com.au" -> dc;
            default -> "in";
        };
    }

    public Map<String, Object> oauthSetup() {
        Map<String, Object> setup = new LinkedHashMap<>();
        setup.put("serverConfigured", isServerConfigured());
        setup.put("homepageUrl", serverOAuth.getHomepageUrl());
        setup.put("redirectUris", serverOAuth.getRedirectUris());
        setup.put("javascriptDomains", serverOAuth.getJavascriptDomains());
        setup.put("scopes", serverOAuth.getScopes());
        return setup;
    }

    public String buildConnectUrl(String redirectUri) {
        String clientId = effectiveClientId();
        if (StringUtils.isBlank(clientId)) {
            throw new IllegalStateException("Zoho client id is not configured");
        }
        String dataCenter = effectiveDataCenter();
        return UriComponentsBuilder.fromUriString(accountsHost(dataCenter) + "/oauth/v2/auth")
            .queryParam("response_type", "code")
            .queryParam("client_id", clientId)
            .queryParam("scope", serverOAuth.getScopes())
            .queryParam("redirect_uri", redirectUri)
            .queryParam("access_type", "offline")
            .queryParam("prompt", "consent")
            .build()
            .toUriString();
    }

    /** Exchange an authorization code for a refresh token and persist it. */
    public void completeOAuth(String code, String redirectUri) {
        String clientId = effectiveClientId();
        String clientSecret = effectiveClientSecret();
        if (StringUtils.isBlank(clientId) || StringUtils.isBlank(clientSecret)) {
            throw new IllegalStateException("Zoho client id/secret are not configured");
        }
        String dataCenter = effectiveDataCenter();
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("redirect_uri", redirectUri);
        form.add("code", code);

        @SuppressWarnings("unchecked")
        Map<String, Object> response = restClient
            .post()
            .uri(accountsHost(dataCenter) + "/oauth/v2/token")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(form)
            .retrieve()
            .body(Map.class);

        String refreshToken = response != null ? String.valueOf(response.get("refresh_token")) : null;
        if (StringUtils.isBlank(refreshToken) || "null".equals(refreshToken)) {
            throw new IllegalStateException("Zoho did not return a refresh token");
        }

        ZohoSetting setting = settingRepository.findActiveOrLatest().orElseGet(ZohoSetting::new);
        setting.setRefreshToken(refreshToken);
        setting.setDataCenter(dataCenter);
        if (StringUtils.isBlank(setting.getOrgId())) {
            setting.setOrgId(effectiveOrgId());
        }
        setting.setConnected(true);
        settingRepository.save(setting);
    }

    /** Resolve (and cache) a live access token from the stored refresh token. */
    public String accessToken() {
        ZohoSetting setting = settingRepository.findActiveOrLatest().orElse(null);
        String refreshToken = setting != null ? setting.getRefreshToken() : null;
        if (StringUtils.isBlank(refreshToken)) {
            throw new IllegalStateException("Zoho is not connected (no refresh token on file)");
        }
        CachedToken cached = accessTokenCache.get(refreshToken);
        if (cached != null && cached.expiresAt.isAfter(Instant.now())) {
            return cached.accessToken;
        }
        String clientId = effectiveClientId();
        String clientSecret = effectiveClientSecret();
        String dataCenter = StringUtils.defaultIfBlank(setting.getDataCenter(), effectiveDataCenter());

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "refresh_token");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("refresh_token", refreshToken);

        @SuppressWarnings("unchecked")
        Map<String, Object> response = restClient
            .post()
            .uri(accountsHost(dataCenter) + "/oauth/v2/token")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(form)
            .retrieve()
            .body(Map.class);

        String accessToken = response != null ? String.valueOf(response.get("access_token")) : null;
        if (StringUtils.isBlank(accessToken) || "null".equals(accessToken)) {
            throw new IllegalStateException("Zoho did not return an access token");
        }
        Object expiresInRaw = response.getOrDefault("expires_in", 3600);
        long expiresInSeconds = expiresInRaw instanceof Number number ? number.longValue() : 3600L;
        // Refresh a little early so a slow downstream call never races token expiry.
        Instant expiresAt = Instant.now().plusSeconds(Math.max(60, expiresInSeconds - 120));
        accessTokenCache.put(refreshToken, new CachedToken(accessToken, expiresAt));
        return accessToken;
    }

    public String orgId() {
        ZohoSetting setting = settingRepository.findActiveOrLatest().orElse(null);
        String stored = setting != null ? setting.getOrgId() : null;
        return StringUtils.isNotBlank(stored) ? stored : effectiveOrgId();
    }

    public String dataCenter() {
        ZohoSetting setting = settingRepository.findActiveOrLatest().orElse(null);
        String stored = setting != null ? setting.getDataCenter() : null;
        return StringUtils.isNotBlank(stored) ? stored : effectiveDataCenter();
    }

    private record CachedToken(String accessToken, Instant expiresAt) {}
}
