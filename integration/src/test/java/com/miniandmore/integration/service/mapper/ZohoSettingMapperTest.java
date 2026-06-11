package com.miniandmore.integration.service.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.miniandmore.integration.domain.ZohoSetting;
import com.miniandmore.integration.service.dto.ZohoSettingDTO;
import org.junit.jupiter.api.Test;

class ZohoSettingMapperTest {

    private final ZohoSettingMapper mapper = new ZohoSettingMapperImpl();

    @Test
    void toEntityAndToDto_roundTrip() {
        ZohoSettingDTO dto = new ZohoSettingDTO();
        dto.setId(1L);
        dto.setOrgId("org");
        dto.setClientId("client");
        dto.setConnected(true);
        dto.setDataCenter("in");

        ZohoSetting entity = mapper.toEntity(dto);
        ZohoSettingDTO back = mapper.toDto(entity);

        assertThat(entity.getOrgId()).isEqualTo("org");
        assertThat(back.getOrgId()).isEqualTo("org");
        assertThat(back.getConnected()).isTrue();
    }

    @Test
    void partialUpdate_ignoresNulls() {
        ZohoSetting entity = new ZohoSetting();
        entity.setOrgId("old");
        ZohoSettingDTO patch = new ZohoSettingDTO();
        patch.setClientId("new-client");

        mapper.partialUpdate(entity, patch);

        assertThat(entity.getOrgId()).isEqualTo("old");
        assertThat(entity.getClientId()).isEqualTo("new-client");
    }
}
