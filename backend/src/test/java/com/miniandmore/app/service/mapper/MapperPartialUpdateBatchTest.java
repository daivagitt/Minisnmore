package com.miniandmore.app.service.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.miniandmore.app.domain.*;
import com.miniandmore.app.domain.enumeration.DiscountType;
import com.miniandmore.app.service.dto.*;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class MapperPartialUpdateBatchTest {

    @Test
    void categoryMapper_partialUpdate() {
        CategoryMapper mapper = new CategoryMapperImpl();
        Category entity = new Category().slug("s");
        CategoryDTO patch = new CategoryDTO();
        patch.setId(1L);
        patch.setSlug("new");
        patch.setName("Name");
        patch.setDescription("d");
        patch.setImageUrl("img");
        patch.setSortOrder(2);
        patch.setActive(false);
        mapper.partialUpdate(entity, patch);
        assertThat(entity.getSlug()).isEqualTo("new");
    }

    @Test
    void couponMapper_partialUpdate() {
        CouponMapper mapper = new CouponMapperImpl();
        Coupon entity = new Coupon().code("A");
        CouponDTO patch = new CouponDTO();
        patch.setId(1L);
        patch.setCode("B");
        patch.setDiscountType(DiscountType.FLAT);
        patch.setDiscountValue(BigDecimal.TEN);
        patch.setMinOrder(BigDecimal.ONE);
        patch.setMaxDiscount(BigDecimal.valueOf(50));
        patch.setExpiresAt(Instant.now());
        patch.setActive(false);
        mapper.partialUpdate(entity, patch);
        assertThat(entity.getActive()).isFalse();
    }

    @Test
    void blogPostMapper_partialUpdate() {
        BlogPostMapper mapper = new BlogPostMapperImpl();
        BlogPost entity = new BlogPost().slug("s");
        BlogPostDTO patch = new BlogPostDTO();
        patch.setId(1L);
        patch.setSlug("new");
        patch.setTitle("t");
        patch.setExcerpt("e");
        patch.setContent("c");
        patch.setCoverUrl("u");
        patch.setPublished(true);
        patch.setPublishedAt(Instant.now());
        mapper.partialUpdate(entity, patch);
        assertThat(entity.getPublished()).isTrue();
    }

    @Test
    void homeSectionMapper_partialUpdate() {
        HomeSectionMapper mapper = new HomeSectionMapperImpl();
        HomeSection entity = new HomeSection().sectionKey("k");
        HomeSectionDTO patch = new HomeSectionDTO();
        patch.setId(1L);
        patch.setSectionKey("hero");
        patch.setTitle("t");
        patch.setSubtitle("sub");
        patch.setEnabled(false);
        patch.setPayloadJson("{}");
        mapper.partialUpdate(entity, patch);
        assertThat(entity.getEnabled()).isFalse();
    }

    @Test
    void bannerMapper_partialUpdate() {
        BannerMapper mapper = new BannerMapperImpl();
        Banner entity = new Banner().title("t");
        BannerDTO patch = new BannerDTO();
        patch.setId(3L);
        patch.setTitle("New");
        patch.setSubtitle("s");
        patch.setImageUrl("i");
        patch.setLinkUrl("/l");
        patch.setCtaLabel("Go");
        patch.setSortOrder(2);
        patch.setActive(false);
        mapper.partialUpdate(entity, patch);
        assertThat(entity.getCtaLabel()).isEqualTo("Go");
    }

    @Test
    void pageVisibilityMapper_partialUpdate() {
        PageVisibilityMapper mapper = new PageVisibilityMapperImpl();
        PageVisibility entity = new PageVisibility().pageKey("shop");
        PageVisibilityDTO patch = new PageVisibilityDTO();
        patch.setId(1L);
        patch.setPageKey("cart");
        patch.setVisible(false);
        mapper.partialUpdate(entity, patch);
        assertThat(entity.getPageKey()).isEqualTo("cart");
    }
}
