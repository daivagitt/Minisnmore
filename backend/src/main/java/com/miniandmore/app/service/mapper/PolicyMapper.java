package com.miniandmore.app.service.mapper;

import com.miniandmore.app.domain.Policy;
import com.miniandmore.app.service.dto.PolicyDTO;
import org.mapstruct.*;

/**
 * Mapper for the entity {@link Policy} and its DTO {@link PolicyDTO}.
 */
@Mapper(componentModel = "spring")
public interface PolicyMapper extends EntityMapper<PolicyDTO, Policy> {}
