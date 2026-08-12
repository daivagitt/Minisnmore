package com.miniandmore.app.web.rest.admin;

import com.miniandmore.app.domain.ZohoSetting;
import com.miniandmore.app.repository.ZohoSettingRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/zoho-settings")
public class ZohoSettingsResource {

    private final ZohoSettingRepository settingRepository;

    public ZohoSettingsResource(ZohoSettingRepository settingRepository) {
        this.settingRepository = settingRepository;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getSettings() {
        return settingRepository
            .findActiveOrLatest()
            .map(s -> {
                Map<String, Object> dto = new LinkedHashMap<>();
                dto.put("orgId", s.getOrgId());
                dto.put("clientId", s.getClientId());
                dto.put("clientSecret", s.getClientSecret());
                dto.put("refreshToken", s.getRefreshToken());
                dto.put("dataCenter", s.getDataCenter());
                dto.put("connected", s.getConnected());
                return ResponseEntity.ok(dto);
            })
            .orElse(ResponseEntity.ok(null));
    }

    @PutMapping
    public Map<String, Object> saveSettings(@RequestBody Map<String, Object> body) {
        ZohoSetting setting = settingRepository.findActiveOrLatest().orElseGet(ZohoSetting::new);
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
        }
        settingRepository.save(setting);
        return Map.of("ok", true);
    }
}
