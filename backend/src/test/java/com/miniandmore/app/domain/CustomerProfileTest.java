package com.miniandmore.app.domain;

import static com.miniandmore.app.domain.CustomerProfileTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.miniandmore.app.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class CustomerProfileTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(CustomerProfile.class);
        CustomerProfile customerProfile1 = getCustomerProfileSample1();
        CustomerProfile customerProfile2 = new CustomerProfile();
        assertThat(customerProfile1).isNotEqualTo(customerProfile2);

        customerProfile2.setId(customerProfile1.getId());
        assertThat(customerProfile1).isEqualTo(customerProfile2);

        customerProfile2 = getCustomerProfileSample2();
        assertThat(customerProfile1).isNotEqualTo(customerProfile2);
    }
}
