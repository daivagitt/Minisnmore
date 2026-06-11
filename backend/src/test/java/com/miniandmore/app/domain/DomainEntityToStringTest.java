package com.miniandmore.app.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.miniandmore.app.domain.enumeration.DiscountType;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class DomainEntityToStringTest {

    @Test
    void category_allFields() {
        Category category = new Category().id(1L).slug("tops").name("Tops").sortOrder(1).active(true);
        assertThat(category.getSlug()).isEqualTo("tops");
        assertThat(category.toString()).contains("Tops");
    }

    @Test
    void banner_allFields() {
        Banner banner = new Banner()
            .id(1L)
            .title("Sale")
            .subtitle("Sub")
            .imageUrl("img.jpg")
            .linkUrl("/sale")
            .ctaLabel("Shop")
            .sortOrder(1)
            .active(true);
        assertThat(banner.getLinkUrl()).isEqualTo("/sale");
        assertThat(banner.toString()).contains("Sale");
    }

    @Test
    void coupon_allFields() {
        Coupon coupon = new Coupon()
            .id(1L)
            .code("SAVE")
            .discountType(DiscountType.FLAT)
            .discountValue(BigDecimal.TEN)
            .minOrder(BigDecimal.ZERO)
            .maxDiscount(BigDecimal.valueOf(50))
            .expiresAt(Instant.now())
            .active(true);
        assertThat(coupon.getDiscountType()).isEqualTo(DiscountType.FLAT);
        assertThat(coupon.toString()).contains("SAVE");
    }

    @Test
    void blogPost_allFields() {
        BlogPost post = new BlogPost()
            .id(1L)
            .slug("post")
            .title("Title")
            .excerpt("Excerpt")
            .content("Content")
            .coverUrl("cover.jpg")
            .published(true)
            .publishedAt(Instant.now());
        assertThat(post.getContent()).isEqualTo("Content");
        assertThat(post.toString()).contains("Title");
    }

    @Test
    void homeSection_allFields() {
        HomeSection section = new HomeSection()
            .id(1L)
            .sectionKey("hero")
            .title("Welcome")
            .subtitle("Sub")
            .enabled(true)
            .sortOrder(1)
            .payloadJson("{}");
        assertThat(section.getSectionKey()).isEqualTo("hero");
        assertThat(section.toString()).contains("hero");
    }

    @Test
    void pageVisibility_allFields() {
        PageVisibility page = new PageVisibility().id(1L).pageKey("shop").visible(true);
        assertThat(page.getPageKey()).isEqualTo("shop");
        assertThat(page.toString()).contains("shop");
    }

    @Test
    void policy_allFields() {
        Policy policy = new Policy().id(1L).slug("terms").title("Terms").body("Body");
        assertThat(policy.getBody()).isEqualTo("Body");
        assertThat(policy.toString()).contains("Terms");
    }
}
