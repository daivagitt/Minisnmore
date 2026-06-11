package com.miniandmore.app.service.mapper;

import com.miniandmore.app.domain.HomeSection;
import com.miniandmore.app.service.dto.HomeSectionDTO;
import org.mapstruct.*;

/**
 * Mapper for the entity {@link HomeSection} and its DTO {@link HomeSectionDTO}.
 */
@Mapper(componentModel = "spring")
public interface HomeSectionMapper extends EntityMapper<HomeSectionDTO, HomeSection> {}
