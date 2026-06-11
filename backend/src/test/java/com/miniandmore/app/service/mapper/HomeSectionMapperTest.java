package com.miniandmore.app.service.mapper;

import static com.miniandmore.app.domain.HomeSectionAsserts.*;
import static com.miniandmore.app.domain.HomeSectionTestSamples.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HomeSectionMapperTest {

    private HomeSectionMapper homeSectionMapper;

    @BeforeEach
    void setUp() {
        homeSectionMapper = new HomeSectionMapperImpl();
    }

    @Test
    void shouldConvertToDtoAndBack() {
        var expected = getHomeSectionSample1();
        var actual = homeSectionMapper.toEntity(homeSectionMapper.toDto(expected));
        assertHomeSectionAllPropertiesEquals(expected, actual);
    }
}
