package com.miniandmore.app.service.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.miniandmore.app.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class PageVisibilityDTOTest {

    @Test
    void dtoEqualsVerifier() throws Exception {
        TestUtil.equalsVerifier(PageVisibilityDTO.class);
        PageVisibilityDTO pageVisibilityDTO1 = new PageVisibilityDTO();
        pageVisibilityDTO1.setId(1L);
        PageVisibilityDTO pageVisibilityDTO2 = new PageVisibilityDTO();
        assertThat(pageVisibilityDTO1).isNotEqualTo(pageVisibilityDTO2);
        pageVisibilityDTO2.setId(pageVisibilityDTO1.getId());
        assertThat(pageVisibilityDTO1).isEqualTo(pageVisibilityDTO2);
        pageVisibilityDTO2.setId(2L);
        assertThat(pageVisibilityDTO1).isNotEqualTo(pageVisibilityDTO2);
        pageVisibilityDTO1.setId(null);
        assertThat(pageVisibilityDTO1).isNotEqualTo(pageVisibilityDTO2);
    }
}
