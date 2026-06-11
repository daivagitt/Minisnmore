package com.miniandmore.integration.service.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.miniandmore.integration.domain.ZohoSetting;
import com.miniandmore.integration.service.dto.ZohoSettingDTO;
import org.junit.jupiter.api.Test;

class ZohoSettingMapperFullTest {

    private final ZohoSettingMapper mapper = new ZohoSettingMapperImpl();

    @Test
    void mapsEveryDtoField() {
        ZohoSettingDTO dto = new ZohoSettingDTO();
        dto.setId(9L);
        dto.setOrgId("org");
        dto.setClientId("cid");
        dto.setClientSecret("sec");
        dto.setRefreshToken("rt");
        dto.setDataCenter("sa");
        dto.setConnected(true);

        ZohoSetting entity = mapper.toEntity(dto);
        ZohoSettingDTO back = mapper.toDto(entity);

        assertThat(entity.getOrgId()).isEqualTo("org");
        assertThat(entity.getClientSecret()).isEqualTo("sec");
        assertThat(back.getRefreshToken()).isEqualTo("rt");
        assertThat(back.getDataCenter()).isEqualTo("sa");
    }

    @Test
    void partialUpdate_nullDtoIsNoOp() {
        ZohoSetting entity = new ZohoSetting();
        entity.setOrgId("keep");
        mapper.partialUpdate(entity, null);
        assertThat(entity.getOrgId()).isEqualTo("keep");
    }
}
