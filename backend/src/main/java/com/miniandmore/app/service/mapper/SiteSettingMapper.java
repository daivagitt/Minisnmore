package com.miniandmore.app.service.mapper;

import com.miniandmore.app.domain.SiteSetting;
import com.miniandmore.app.service.dto.SiteSettingDTO;
import org.mapstruct.*;

/**
 * Mapper for the entity {@link SiteSetting} and its DTO {@link SiteSettingDTO}.
 */
@Mapper(componentModel = "spring")
public interface SiteSettingMapper extends EntityMapper<SiteSettingDTO, SiteSetting> {}
