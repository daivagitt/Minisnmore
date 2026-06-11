package com.miniandmore.app.service.mapper;

import static com.miniandmore.app.domain.BannerAsserts.*;
import static com.miniandmore.app.domain.BannerTestSamples.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BannerMapperTest {

    private BannerMapper bannerMapper;

    @BeforeEach
    void setUp() {
        bannerMapper = new BannerMapperImpl();
    }

    @Test
    void shouldConvertToDtoAndBack() {
        var expected = getBannerSample1();
        var actual = bannerMapper.toEntity(bannerMapper.toDto(expected));
        assertBannerAllPropertiesEquals(expected, actual);
    }
}
