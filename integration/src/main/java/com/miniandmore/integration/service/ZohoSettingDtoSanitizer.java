package com.miniandmore.integration.service;

import com.miniandmore.integration.service.dto.ZohoSettingDTO;
import org.apache.commons.lang3.StringUtils;

public final class ZohoSettingDtoSanitizer {

    private static final String REDACTED = "[REDACTED]";

    private ZohoSettingDtoSanitizer() {}

    public static ZohoSettingDTO forResponse(ZohoSettingDTO dto) {
        if (dto == null) {
            return null;
        }
        if (StringUtils.isNotBlank(dto.getClientSecret())) {
            dto.setClientSecret(REDACTED);
        }
        if (StringUtils.isNotBlank(dto.getRefreshToken())) {
            dto.setRefreshToken(REDACTED);
        }
        return dto;
    }
}
