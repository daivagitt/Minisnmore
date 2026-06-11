package com.miniandmore.integration.service.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ZohoSettingDTOTest {

    @Test
    void accessors() {
        ZohoSettingDTO dto = new ZohoSettingDTO();
        dto.setId(1L);
        dto.setOrgId("org");
        dto.setClientId("cid");
        dto.setClientSecret("sec");
        dto.setRefreshToken("rt");
        dto.setDataCenter("in");
        dto.setConnected(true);

        assertThat(dto.getId()).isEqualTo(1L);
        assertThat(dto.getOrgId()).isEqualTo("org");
        assertThat(dto.getConnected()).isTrue();

        ZohoSettingDTO other = new ZohoSettingDTO();
        other.setId(1L);
        assertThat(dto).isEqualTo(other);
        assertThat(dto.hashCode()).isEqualTo(other.hashCode());
        assertThat(dto.toString()).contains("ZohoSettingDTO");
    }
}
