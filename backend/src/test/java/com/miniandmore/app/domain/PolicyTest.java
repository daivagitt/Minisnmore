package com.miniandmore.app.domain;

import static com.miniandmore.app.domain.PolicyTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.miniandmore.app.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class PolicyTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(Policy.class);
        Policy policy1 = getPolicySample1();
        Policy policy2 = new Policy();
        assertThat(policy1).isNotEqualTo(policy2);

        policy2.setId(policy1.getId());
        assertThat(policy1).isEqualTo(policy2);

        policy2 = getPolicySample2();
        assertThat(policy1).isNotEqualTo(policy2);
    }
}
