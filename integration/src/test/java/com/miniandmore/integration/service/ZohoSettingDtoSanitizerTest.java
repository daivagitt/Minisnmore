package com.miniandmore.integration.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.miniandmore.integration.service.dto.ZohoSettingDTO;
import org.junit.jupiter.api.Test;

class ZohoSettingDtoSanitizerTest {

    @Test
    void forResponse_nullReturnsNull() {
        assertThat(ZohoSettingDtoSanitizer.forResponse(null)).isNull();
    }

    @Test
    void forResponse_redactsSecrets() {
        ZohoSettingDTO dto = new ZohoSettingDTO();
        dto.setClientSecret("secret");
        dto.setRefreshToken("refresh");
        dto.setClientId("visible");

        ZohoSettingDTO result = ZohoSettingDtoSanitizer.forResponse(dto);

        assertThat(result.getClientSecret()).isEqualTo("[REDACTED]");
        assertThat(result.getRefreshToken()).isEqualTo("[REDACTED]");
        assertThat(result.getClientId()).isEqualTo("visible");
    }

    @Test
    void forResponse_blankSecretsUnchanged() {
        ZohoSettingDTO dto = new ZohoSettingDTO();
        dto.setClientSecret("  ");

        ZohoSettingDTO result = ZohoSettingDtoSanitizer.forResponse(dto);

        assertThat(result.getClientSecret()).isEqualTo("  ");
    }
}
