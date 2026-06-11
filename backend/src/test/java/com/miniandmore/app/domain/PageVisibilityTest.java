package com.miniandmore.app.domain;

import static com.miniandmore.app.domain.PageVisibilityTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.miniandmore.app.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class PageVisibilityTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(PageVisibility.class);
        PageVisibility pageVisibility1 = getPageVisibilitySample1();
        PageVisibility pageVisibility2 = new PageVisibility();
        assertThat(pageVisibility1).isNotEqualTo(pageVisibility2);

        pageVisibility2.setId(pageVisibility1.getId());
        assertThat(pageVisibility1).isEqualTo(pageVisibility2);

        pageVisibility2 = getPageVisibilitySample2();
        assertThat(pageVisibility1).isNotEqualTo(pageVisibility2);
    }
}
