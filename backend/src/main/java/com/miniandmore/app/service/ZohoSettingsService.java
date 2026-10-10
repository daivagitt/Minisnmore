package com.miniandmore.app.service;

import com.miniandmore.app.domain.ZohoSetting;
import com.miniandmore.app.repository.ZohoSettingRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin Zoho credentials / connection settings, with config-change email on save.
 */
@Service
@Transactional
public class ZohoSettingsService {

    private final ZohoSettingRepository settingRepository;
    private final AdminConfigChangeMailService adminConfigChangeMailService;

    public ZohoSettingsService(
        ZohoSettingRepository settingRepository,
        AdminConfigChangeMailService adminConfigChangeMailService
    ) {
        this.settingRepository = settingRepository;
        this.adminConfigChangeMailService = adminConfigChangeMailService;
    }

    @Transactional(readOnly = true)
    public Optional<Map<String, Object>> getAdminView() {
        return settingRepository.findActiveOrLatest().map(this::toAdminView);
    }

    public Map<String, Object> saveAdminSettings(Map<String, Object> body) {
        ZohoSetting setting = settingRepository.findActiveOrLatest().orElseGet(ZohoSetting::new);
        ZohoSetting previous = copyForEmail(setting);

        if (setting.getConnected() == null) {
            setting.setConnected(false);
        }
        if (body.get("orgId") != null) {
            setting.setOrgId(body.get("orgId").toString());
        }
        if (body.get("clientId") != null) {
            setting.setClientId(body.get("clientId").toString());
        }
        Object clientSecret = body.get("clientSecret");
        if (clientSecret != null && !clientSecret.toString().isBlank()) {
            setting.setClientSecret(clientSecret.toString());
        }
        Object refreshToken = body.get("refreshToken");
        if (refreshToken != null && !refreshToken.toString().isBlank()) {
            setting.setRefreshToken(refreshToken.toString());
        }
        if (body.get("dataCenter") != null) {
            setting.setDataCenter(body.get("dataCenter").toString());
        }
        if (body.get("connected") != null) {
            setting.setConnected(Boolean.parseBoolean(body.get("connected").toString()));
        } else if (setting.getRefreshToken() != null && !setting.getRefreshToken().isBlank()) {
            setting.setConnected(true);
        }
        settingRepository.save(setting);
        adminConfigChangeMailService.notifyZohoSettingsChangedAfterCommit(previous, copyForEmail(setting));
        return Map.of("ok", true);
    }

    /** Snapshot + notify after an in-place Zoho setting mutation elsewhere (OAuth, admin toggles). */
    public void notifyChanged(ZohoSetting previous, ZohoSetting current) {
        adminConfigChangeMailService.notifyZohoSettingsChangedAfterCommit(previous, copyForEmail(current));
    }

    public static ZohoSetting copyForEmail(ZohoSetting source) {
        if (source == null) {
            return null;
        }
        ZohoSetting copy = new ZohoSetting();
        copy.setOrgId(source.getOrgId());
        copy.setClientId(source.getClientId());
        copy.setClientSecret(source.getClientSecret());
        copy.setRefreshToken(source.getRefreshToken());
        copy.setDataCenter(source.getDataCenter());
        copy.setConnected(source.getConnected());
        copy.setPullProductsAutoCreateCategory(source.getPullProductsAutoCreateCategory());
        return copy;
    }

    private Map<String, Object> toAdminView(ZohoSetting s) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("orgId", s.getOrgId());
        dto.put("clientId", s.getClientId());
        dto.put("hasClientSecret", s.getClientSecret() != null && !s.getClientSecret().isBlank());
        dto.put("hasRefreshToken", s.getRefreshToken() != null && !s.getRefreshToken().isBlank());
        dto.put("dataCenter", s.getDataCenter());
        dto.put("connected", s.getConnected());
        return dto;
    }
}
