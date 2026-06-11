package com.miniandmore.app.service.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.miniandmore.app.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class HomeSectionDTOTest {

    @Test
    void dtoEqualsVerifier() throws Exception {
        TestUtil.equalsVerifier(HomeSectionDTO.class);
        HomeSectionDTO homeSectionDTO1 = new HomeSectionDTO();
        homeSectionDTO1.setId(1L);
        HomeSectionDTO homeSectionDTO2 = new HomeSectionDTO();
        assertThat(homeSectionDTO1).isNotEqualTo(homeSectionDTO2);
        homeSectionDTO2.setId(homeSectionDTO1.getId());
        assertThat(homeSectionDTO1).isEqualTo(homeSectionDTO2);
        homeSectionDTO2.setId(2L);
        assertThat(homeSectionDTO1).isNotEqualTo(homeSectionDTO2);
        homeSectionDTO1.setId(null);
        assertThat(homeSectionDTO1).isNotEqualTo(homeSectionDTO2);
    }
}
