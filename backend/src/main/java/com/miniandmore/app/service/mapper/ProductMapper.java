package com.miniandmore.app.service.mapper;

import com.miniandmore.app.domain.Category;
import com.miniandmore.app.domain.Product;
import com.miniandmore.app.service.dto.CategoryDTO;
import com.miniandmore.app.service.dto.ProductDTO;
import org.mapstruct.*;

/**
 * Mapper for the entity {@link Product} and its DTO {@link ProductDTO}.
 */
@Mapper(componentModel = "spring", uses = CategoryMapper.class)
public interface ProductMapper extends EntityMapper<ProductDTO, Product> {
    @Override
    @Named("partialUpdate")
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "category", ignore = true)
    void partialUpdate(@MappingTarget Product entity, ProductDTO dto);

    @AfterMapping
    default void mergeCategory(@MappingTarget Product entity, ProductDTO dto) {
        if (dto == null || dto.getCategory() == null) {
            return;
        }
        CategoryDTO categoryDto = dto.getCategory();
        Category category = entity.getCategory();
        if (category == null) {
            category = new Category();
            entity.setCategory(category);
        }
        if (categoryDto.getId() != null) {
            category.setId(categoryDto.getId());
        }
        if (categoryDto.getName() != null) {
            category.setName(categoryDto.getName());
        }
        if (categoryDto.getSlug() != null) {
            category.setSlug(categoryDto.getSlug());
        }
    }

    default String toDtoCategoryName(Category category) {
        return category == null ? null : category.getName();
    }
}
