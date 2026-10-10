package com.miniandmore.app.web.rest.admin;

import com.miniandmore.app.service.ZohoSettingsService;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/zoho-settings")
public class ZohoSettingsResource {

    private final ZohoSettingsService zohoSettingsService;

    public ZohoSettingsResource(ZohoSettingsService zohoSettingsService) {
        this.zohoSettingsService = zohoSettingsService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getSettings() {
        return zohoSettingsService
            .getAdminView()
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.ok(null));
    }

    @PutMapping
    public Map<String, Object> saveSettings(@RequestBody Map<String, Object> body) {
        return zohoSettingsService.saveAdminSettings(body);
    }
}
