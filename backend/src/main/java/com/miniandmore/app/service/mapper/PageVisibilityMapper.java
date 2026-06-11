package com.miniandmore.app.service.mapper;

import com.miniandmore.app.domain.PageVisibility;
import com.miniandmore.app.service.dto.PageVisibilityDTO;
import org.mapstruct.*;

/**
 * Mapper for the entity {@link PageVisibility} and its DTO {@link PageVisibilityDTO}.
 */
@Mapper(componentModel = "spring")
public interface PageVisibilityMapper extends EntityMapper<PageVisibilityDTO, PageVisibility> {}
