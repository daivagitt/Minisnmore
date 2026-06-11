package com.miniandmore.app.service.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.miniandmore.app.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class PolicyDTOTest {

    @Test
    void dtoEqualsVerifier() throws Exception {
        TestUtil.equalsVerifier(PolicyDTO.class);
        PolicyDTO policyDTO1 = new PolicyDTO();
        policyDTO1.setId(1L);
        PolicyDTO policyDTO2 = new PolicyDTO();
        assertThat(policyDTO1).isNotEqualTo(policyDTO2);
        policyDTO2.setId(policyDTO1.getId());
        assertThat(policyDTO1).isEqualTo(policyDTO2);
        policyDTO2.setId(2L);
        assertThat(policyDTO1).isNotEqualTo(policyDTO2);
        policyDTO1.setId(null);
        assertThat(policyDTO1).isNotEqualTo(policyDTO2);
    }
}
