package com.miniandmore.app.service.mapper;

import com.miniandmore.app.domain.StoreAddress;
import com.miniandmore.app.service.dto.StoreAddressDTO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface StoreAddressMapper extends EntityMapper<StoreAddressDTO, StoreAddress> {}
