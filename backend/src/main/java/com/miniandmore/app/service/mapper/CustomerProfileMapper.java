package com.miniandmore.app.service.mapper;

import com.miniandmore.app.domain.CustomerProfile;
import com.miniandmore.app.service.dto.CustomerProfileDTO;
import org.mapstruct.*;

/**
 * Mapper for the entity {@link CustomerProfile} and its DTO {@link CustomerProfileDTO}.
 */
@Mapper(componentModel = "spring")
public interface CustomerProfileMapper extends EntityMapper<CustomerProfileDTO, CustomerProfile> {}
