package com.miniandmore.app.domain;

import static com.miniandmore.app.domain.HomeSectionTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.miniandmore.app.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class HomeSectionTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(HomeSection.class);
        HomeSection homeSection1 = getHomeSectionSample1();
        HomeSection homeSection2 = new HomeSection();
        assertThat(homeSection1).isNotEqualTo(homeSection2);

        homeSection2.setId(homeSection1.getId());
        assertThat(homeSection1).isEqualTo(homeSection2);

        homeSection2 = getHomeSectionSample2();
        assertThat(homeSection1).isNotEqualTo(homeSection2);
    }
}
