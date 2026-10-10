package com.miniandmore.app.service.mapper;

import com.miniandmore.app.domain.ContactMessage;
import com.miniandmore.app.service.dto.ContactMessageDTO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ContactMessageMapper extends EntityMapper<ContactMessageDTO, ContactMessage> {}
