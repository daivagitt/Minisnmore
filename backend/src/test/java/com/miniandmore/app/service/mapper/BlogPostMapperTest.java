package com.miniandmore.app.service.mapper;

import static com.miniandmore.app.domain.BlogPostAsserts.*;
import static com.miniandmore.app.domain.BlogPostTestSamples.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BlogPostMapperTest {

    private BlogPostMapper blogPostMapper;

    @BeforeEach
    void setUp() {
        blogPostMapper = new BlogPostMapperImpl();
    }

    @Test
    void shouldConvertToDtoAndBack() {
        var expected = getBlogPostSample1();
        var actual = blogPostMapper.toEntity(blogPostMapper.toDto(expected));
        assertBlogPostAllPropertiesEquals(expected, actual);
    }
}
