package com.miniandmore.integration.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CategoryZohoFluentTest {

    @Test
    void zohoCategoryIdFluentAccessor() {
        Category category = new Category().zohoCategoryId("cat-zoho");
        assertThat(category.getZohoCategoryId()).isEqualTo("cat-zoho");
        category.setZohoCategoryId("cat-2");
        assertThat(category.getZohoCategoryId()).isEqualTo("cat-2");
    }
}
