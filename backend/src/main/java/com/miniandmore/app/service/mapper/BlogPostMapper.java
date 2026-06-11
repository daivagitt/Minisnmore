package com.miniandmore.app.service.mapper;

import com.miniandmore.app.domain.BlogPost;
import com.miniandmore.app.service.dto.BlogPostDTO;
import org.mapstruct.*;

/**
 * Mapper for the entity {@link BlogPost} and its DTO {@link BlogPostDTO}.
 */
@Mapper(componentModel = "spring")
public interface BlogPostMapper extends EntityMapper<BlogPostDTO, BlogPost> {}
