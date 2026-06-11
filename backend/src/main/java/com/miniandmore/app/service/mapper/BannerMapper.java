package com.miniandmore.app.service.mapper;

import com.miniandmore.app.domain.Banner;
import com.miniandmore.app.service.dto.BannerDTO;
import org.mapstruct.*;

/**
 * Mapper for the entity {@link Banner} and its DTO {@link BannerDTO}.
 */
@Mapper(componentModel = "spring")
public interface BannerMapper extends EntityMapper<BannerDTO, Banner> {}
