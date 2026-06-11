package com.miniandmore.integration.service.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.miniandmore.integration.domain.ZohoSetting;
import com.miniandmore.integration.service.dto.ZohoSettingDTO;
import org.junit.jupiter.api.Test;

class ZohoSettingMapperExtendedTest {

    private final ZohoSettingMapper mapper = new ZohoSettingMapperImpl();

    @Test
    void mapsAllScalarFields() {
        ZohoSetting entity = new ZohoSetting();
        entity.setId(2L);
        entity.setOrgId("org-2");
        entity.setClientId("client");
        entity.setClientSecret("secret");
        entity.setRefreshToken("refresh");
        entity.setConnected(true);
        entity.setDataCenter("eu");
        entity.setPullProductsAutoCreateCategory(true);

        ZohoSettingDTO dto = mapper.toDto(entity);
        ZohoSetting roundTrip = mapper.toEntity(dto);

        assertThat(dto.getOrgId()).isEqualTo("org-2");
        assertThat(dto.getDataCenter()).isEqualTo("eu");
        assertThat(roundTrip.getOrgId()).isEqualTo("org-2");
        assertThat(entity.getPullProductsAutoCreateCategory()).isTrue();
    }

    @Test
    void partialUpdate_overwritesPresentFields() {
        ZohoSetting entity = new ZohoSetting();
        entity.setOrgId("old-org");
        entity.setConnected(false);
        ZohoSettingDTO patch = new ZohoSettingDTO();
        patch.setOrgId("new-org");
        patch.setConnected(true);
        patch.setDataCenter("com");
        patch.setClientId("cid");
        patch.setClientSecret("sec");
        patch.setRefreshToken("rt");

        mapper.partialUpdate(entity, patch);

        assertThat(entity.getOrgId()).isEqualTo("new-org");
        assertThat(entity.getConnected()).isTrue();
        assertThat(entity.getDataCenter()).isEqualTo("com");
        assertThat(entity.getClientId()).isEqualTo("cid");
    }
}
