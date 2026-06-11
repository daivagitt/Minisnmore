package com.miniandmore.app.service.mapper;

import static com.miniandmore.app.domain.SiteSettingAsserts.*;
import static com.miniandmore.app.domain.SiteSettingTestSamples.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SiteSettingMapperTest {

    private SiteSettingMapper siteSettingMapper;

    @BeforeEach
    void setUp() {
        siteSettingMapper = new SiteSettingMapperImpl();
    }

    @Test
    void shouldConvertToDtoAndBack() {
        var expected = getSiteSettingSample1();
        var actual = siteSettingMapper.toEntity(siteSettingMapper.toDto(expected));
        assertSiteSettingAllPropertiesEquals(expected, actual);
    }
}
