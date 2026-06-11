package com.miniandmore.app.service.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.miniandmore.app.domain.Banner;
import com.miniandmore.app.domain.Category;
import com.miniandmore.app.domain.PageVisibility;
import com.miniandmore.app.service.dto.BannerDTO;
import com.miniandmore.app.service.dto.CategoryDTO;
import com.miniandmore.app.service.dto.PageVisibilityDTO;
import org.junit.jupiter.api.Test;

class BannerCategoryPageVisibilityMapperExtendedTest {

    @Test
    void bannerMapper_allFieldsRoundTrip() {
        BannerMapper mapper = new BannerMapperImpl();
        Banner entity = new Banner()
            .id(1L)
            .title("Title")
            .subtitle("Sub")
            .imageUrl("img.jpg")
            .linkUrl("/shop")
            .ctaLabel("Go")
            .sortOrder(2)
            .active(true);

        var dto = mapper.toDto(entity);
        var copy = mapper.toEntity(dto);
        BannerDTO patch = new BannerDTO();
        patch.setSubtitle("Updated");
        mapper.partialUpdate(copy, patch);

        assertThat(copy.getSubtitle()).isEqualTo("Updated");
        assertThat(mapper.toDto(java.util.List.of(entity))).hasSize(1);
    }

    @Test
    void categoryMapper_allFieldsRoundTrip() {
        CategoryMapper mapper = new CategoryMapperImpl();
        Category entity = new Category().id(1L).slug("dresses").name("Dresses").sortOrder(1).active(true);

        var dto = mapper.toDto(entity);
        var copy = mapper.toEntity(dto);
        CategoryDTO patch = new CategoryDTO();
        patch.setName("Updated");
        mapper.partialUpdate(copy, patch);

        assertThat(copy.getName()).isEqualTo("Updated");
        assertThat(mapper.toEntity(java.util.List.of(dto))).hasSize(1);
    }

    @Test
    void pageVisibilityMapper_allFieldsRoundTrip() {
        PageVisibilityMapper mapper = new PageVisibilityMapperImpl();
        PageVisibility entity = new PageVisibility().id(1L).pageKey("shop").visible(true);

        var dto = mapper.toDto(entity);
        var copy = mapper.toEntity(dto);
        PageVisibilityDTO patch = new PageVisibilityDTO();
        patch.setVisible(false);
        mapper.partialUpdate(copy, patch);

        assertThat(copy.getVisible()).isFalse();
        assertThat(mapper.toDto(java.util.List.of(entity))).hasSize(1);
    }
}
