package com.miniandmore.integration.service.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.miniandmore.integration.domain.ZohoSetting;
import com.miniandmore.integration.service.dto.ZohoSettingDTO;
import java.util.List;
import org.junit.jupiter.api.Test;

class ZohoSettingMapperListTest {

    private final ZohoSettingMapper mapper = new ZohoSettingMapperImpl();

    @Test
    void nullSourcesReturnNull() {
        assertThat(mapper.toEntity((ZohoSettingDTO) null)).isNull();
        assertThat(mapper.toDto((ZohoSetting) null)).isNull();
        assertThat(mapper.toEntity((List<ZohoSettingDTO>) null)).isNull();
        assertThat(mapper.toDto((List<ZohoSetting>) null)).isNull();
    }

    @Test
    void mapsLists() {
        ZohoSettingDTO dto = new ZohoSettingDTO();
        dto.setId(1L);
        dto.setOrgId("org");

        List<ZohoSetting> entities = mapper.toEntity(List.of(dto));
        List<ZohoSettingDTO> dtos = mapper.toDto(entities);

        assertThat(entities).hasSize(1);
        assertThat(dtos.getFirst().getOrgId()).isEqualTo("org");
    }

    @Test
    void partialUpdate_eachFieldIndividually() {
        ZohoSetting entity = new ZohoSetting();
        ZohoSettingDTO patch = new ZohoSettingDTO();
        patch.setId(2L);
        mapper.partialUpdate(entity, patch);
        patch = new ZohoSettingDTO();
        patch.setOrgId("o");
        mapper.partialUpdate(entity, patch);
        patch = new ZohoSettingDTO();
        patch.setClientId("c");
        mapper.partialUpdate(entity, patch);
        patch = new ZohoSettingDTO();
        patch.setClientSecret("s");
        mapper.partialUpdate(entity, patch);
        patch = new ZohoSettingDTO();
        patch.setRefreshToken("r");
        mapper.partialUpdate(entity, patch);
        patch = new ZohoSettingDTO();
        patch.setDataCenter("in");
        mapper.partialUpdate(entity, patch);
        patch = new ZohoSettingDTO();
        patch.setConnected(true);
        mapper.partialUpdate(entity, patch);

        assertThat(entity.getId()).isEqualTo(2L);
        assertThat(entity.getOrgId()).isEqualTo("o");
        assertThat(entity.getConnected()).isTrue();
    }
}
