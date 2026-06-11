package com.miniandmore.integration.service.mapper;

import com.miniandmore.integration.domain.ZohoSetting;
import com.miniandmore.integration.service.dto.ZohoSettingDTO;
import org.mapstruct.*;

/**
 * Mapper for the entity {@link ZohoSetting} and its DTO {@link ZohoSettingDTO}.
 */
@Mapper(componentModel = "spring")
public interface ZohoSettingMapper extends EntityMapper<ZohoSettingDTO, ZohoSetting> {}
