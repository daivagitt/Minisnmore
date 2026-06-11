package com.miniandmore.app.service.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.miniandmore.app.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class BlogPostDTOTest {

    @Test
    void dtoEqualsVerifier() throws Exception {
        TestUtil.equalsVerifier(BlogPostDTO.class);
        BlogPostDTO blogPostDTO1 = new BlogPostDTO();
        blogPostDTO1.setId(1L);
        BlogPostDTO blogPostDTO2 = new BlogPostDTO();
        assertThat(blogPostDTO1).isNotEqualTo(blogPostDTO2);
        blogPostDTO2.setId(blogPostDTO1.getId());
        assertThat(blogPostDTO1).isEqualTo(blogPostDTO2);
        blogPostDTO2.setId(2L);
        assertThat(blogPostDTO1).isNotEqualTo(blogPostDTO2);
        blogPostDTO1.setId(null);
        assertThat(blogPostDTO1).isNotEqualTo(blogPostDTO2);
    }
}
