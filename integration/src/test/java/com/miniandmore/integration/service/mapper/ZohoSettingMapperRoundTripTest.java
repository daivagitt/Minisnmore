package com.miniandmore.integration.service.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.miniandmore.integration.domain.ZohoSetting;
import com.miniandmore.integration.service.dto.ZohoSettingDTO;
import org.junit.jupiter.api.Test;

class ZohoSettingMapperRoundTripTest {

    private final ZohoSettingMapper mapper = new ZohoSettingMapperImpl();

    @Test
    void toDto_fromEntityWithNullOptionalFields() {
        ZohoSetting entity = new ZohoSetting();
        entity.setId(3L);
        entity.setConnected(true);

        ZohoSettingDTO dto = mapper.toDto(entity);
        assertThat(dto.getId()).isEqualTo(3L);
        assertThat(dto.getConnected()).isTrue();
    }

    @Test
    void toEntity_fromDtoWithNullFields() {
        ZohoSettingDTO dto = new ZohoSettingDTO();
        dto.setId(4L);
        dto.setConnected(false);

        ZohoSetting entity = mapper.toEntity(dto);
        assertThat(entity.getId()).isEqualTo(4L);
        assertThat(entity.getConnected()).isFalse();
    }

    @Test
    void partialUpdate_appliesNonNullFields() {
        ZohoSetting entity = new ZohoSetting();
        entity.setOrgId("old");
        ZohoSettingDTO dto = new ZohoSettingDTO();
        dto.setOrgId("new");
        dto.setDataCenter("eu");

        mapper.partialUpdate(entity, dto);

        assertThat(entity.getOrgId()).isEqualTo("new");
        assertThat(entity.getDataCenter()).isEqualTo("eu");
    }
}
