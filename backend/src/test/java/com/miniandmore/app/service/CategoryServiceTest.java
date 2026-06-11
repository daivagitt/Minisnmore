package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.miniandmore.app.domain.Category;
import com.miniandmore.app.repository.CategoryRepository;
import com.miniandmore.app.service.dto.CategoryDTO;
import com.miniandmore.app.service.mapper.CategoryMapper;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    private static final Long ID = 1L;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private CategoryMapper categoryMapper;

    @InjectMocks
    private CategoryService categoryService;

    @Test
    void save() {
        CategoryDTO input = new CategoryDTO();
        Category entity = new Category().id(ID);
        Category saved = new Category().id(ID);
        CategoryDTO expected = new CategoryDTO();
        expected.setId(ID);

        when(categoryMapper.toEntity(input)).thenReturn(entity);
        when(categoryRepository.save(entity)).thenReturn(saved);
        when(categoryMapper.toDto(saved)).thenReturn(expected);

        CategoryDTO result = categoryService.save(input);

        assertThat(result).isSameAs(expected);
        verify(categoryMapper).toEntity(input);
        verify(categoryRepository).save(entity);
        verify(categoryMapper).toDto(saved);
    }

    @Test
    void update() {
        CategoryDTO input = new CategoryDTO();
        input.setId(ID);
        Category entity = new Category().id(ID);
        Category saved = new Category().id(ID);
        CategoryDTO expected = new CategoryDTO();
        expected.setId(ID);

        when(categoryMapper.toEntity(input)).thenReturn(entity);
        when(categoryRepository.save(entity)).thenReturn(saved);
        when(categoryMapper.toDto(saved)).thenReturn(expected);

        CategoryDTO result = categoryService.update(input);

        assertThat(result).isSameAs(expected);
        verify(categoryMapper).toEntity(input);
        verify(categoryRepository).save(entity);
    }

    @Test
    void partialUpdate_found() {
        CategoryDTO input = new CategoryDTO();
        input.setId(ID);
        Category existing = new Category().id(ID);
        CategoryDTO expected = new CategoryDTO();
        expected.setId(ID);

        when(categoryRepository.findById(ID)).thenReturn(Optional.of(existing));
        when(categoryRepository.save(existing)).thenReturn(existing);
        when(categoryMapper.toDto(existing)).thenReturn(expected);

        Optional<CategoryDTO> result = categoryService.partialUpdate(input);

        assertThat(result).contains(expected);
        verify(categoryMapper).partialUpdate(existing, input);
        verify(categoryRepository).save(existing);
    }

    @Test
    void partialUpdate_notFound() {
        CategoryDTO input = new CategoryDTO();
        input.setId(ID);
        when(categoryRepository.findById(ID)).thenReturn(Optional.empty());

        Optional<CategoryDTO> result = categoryService.partialUpdate(input);

        assertThat(result).isEmpty();
    }

    @Test
    void findOne_found() {
        Category entity = new Category().id(ID);
        CategoryDTO dto = new CategoryDTO();
        dto.setId(ID);
        when(categoryRepository.findById(ID)).thenReturn(Optional.of(entity));
        when(categoryMapper.toDto(entity)).thenReturn(dto);

        Optional<CategoryDTO> result = categoryService.findOne(ID);

        assertThat(result).contains(dto);
    }

    @Test
    void findOne_notFound() {
        when(categoryRepository.findById(ID)).thenReturn(Optional.empty());

        Optional<CategoryDTO> result = categoryService.findOne(ID);

        assertThat(result).isEmpty();
    }

    @Test
    void delete() {
        categoryService.delete(ID);
        verify(categoryRepository).deleteById(ID);
    }

}
