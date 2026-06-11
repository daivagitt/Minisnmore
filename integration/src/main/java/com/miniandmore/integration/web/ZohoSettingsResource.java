package com.miniandmore.integration.web;

import com.miniandmore.integration.config.ApplicationProperties;
import com.miniandmore.integration.repository.ZohoSettingRepository;
import com.miniandmore.integration.service.ZohoDataCenterHosts;
import com.miniandmore.integration.service.ZohoOAuthService;
import com.miniandmore.integration.service.ZohoSettingDtoSanitizer;
import com.miniandmore.integration.service.ZohoSettingService;
import com.miniandmore.integration.service.dto.ZohoSettingDTO;
import com.miniandmore.integration.service.mapper.ZohoSettingMapper;
import com.miniandmore.integration.web.ApiResponse;
import jakarta.validation.Valid;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/zoho-settings")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class ZohoSettingsResource {

    private final ZohoSettingRepository zohoSettingRepository;
    private final ZohoSettingService zohoSettingService;
    private final ZohoSettingMapper zohoSettingMapper;
    private final ZohoOAuthService zohoOAuthService;
    private final ApplicationProperties applicationProperties;

    public ZohoSettingsResource(
        ZohoSettingRepository zohoSettingRepository,
        ZohoSettingService zohoSettingService,
        ZohoSettingMapper zohoSettingMapper,
        ZohoOAuthService zohoOAuthService,
        ApplicationProperties applicationProperties
    ) {
        this.zohoSettingRepository = zohoSettingRepository;
        this.zohoSettingService = zohoSettingService;
        this.zohoSettingMapper = zohoSettingMapper;
        this.zohoOAuthService = zohoOAuthService;
        this.applicationProperties = applicationProperties;
    }

    @GetMapping
    public ApiResponse<ZohoSettingDTO> zohoSettings() {
        return ApiResponse.ok(
            zohoSettingRepository.findActiveOrLatest().map(zohoSettingMapper::toDto).map(ZohoSettingDtoSanitizer::forResponse).orElse(null)
        );
    }

    @PutMapping
    public ApiResponse<ZohoSettingDTO> updateZohoSettings(@Valid @RequestBody ZohoSettingDTO dto) {
        dto.setId(null);
        zohoSettingRepository.findActiveOrLatest().ifPresent(existing -> dto.setId(existing.getId()));
        applyServerZohoCredentials(dto);
        ZohoSettingDTO saved = dto.getId() == null ? zohoSettingService.save(dto) : zohoSettingService.update(dto);
        return ApiResponse.ok(ZohoSettingDtoSanitizer.forResponse(saved));
    }

    private void applyServerZohoCredentials(ZohoSettingDTO dto) {
        if (!zohoOAuthService.isServerConfigured()) {
            return;
        }
        ApplicationProperties.Zoho.OAuth oauth = applicationProperties.getZoho().getOauth();
        dto.setClientId(oauth.getClientId());
        dto.setClientSecret(oauth.getClientSecret());
        dto.setOrgId(oauth.getOrgId());
        if (StringUtils.isBlank(dto.getDataCenter())) {
            dto.setDataCenter(ZohoDataCenterHosts.normalize(oauth.getDefaultDataCenter()));
        } else {
            dto.setDataCenter(ZohoDataCenterHosts.normalize(dto.getDataCenter()));
        }
    }
}
