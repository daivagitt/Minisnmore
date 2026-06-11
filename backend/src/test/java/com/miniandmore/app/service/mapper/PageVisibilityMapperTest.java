package com.miniandmore.app.service.mapper;

import static com.miniandmore.app.domain.PageVisibilityAsserts.*;
import static com.miniandmore.app.domain.PageVisibilityTestSamples.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PageVisibilityMapperTest {

    private PageVisibilityMapper pageVisibilityMapper;

    @BeforeEach
    void setUp() {
        pageVisibilityMapper = new PageVisibilityMapperImpl();
    }

    @Test
    void shouldConvertToDtoAndBack() {
        var expected = getPageVisibilitySample1();
        var actual = pageVisibilityMapper.toEntity(pageVisibilityMapper.toDto(expected));
        assertPageVisibilityAllPropertiesEquals(expected, actual);
    }
}
