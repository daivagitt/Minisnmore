package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.miniandmore.app.domain.PageVisibility;
import com.miniandmore.app.repository.PageVisibilityRepository;
import com.miniandmore.app.service.dto.PageVisibilityDTO;
import com.miniandmore.app.service.mapper.PageVisibilityMapper;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PageVisibilityServiceTest {

    private static final Long ID = 1L;

    @Mock
    private PageVisibilityRepository pageVisibilityRepository;

    @Mock
    private PageVisibilityMapper pageVisibilityMapper;

    @InjectMocks
    private PageVisibilityService pageVisibilityService;

    @Test
    void save() {
        PageVisibilityDTO input = new PageVisibilityDTO();
        PageVisibility entity = new PageVisibility().id(ID);
        PageVisibility saved = new PageVisibility().id(ID);
        PageVisibilityDTO expected = new PageVisibilityDTO();
        expected.setId(ID);

        when(pageVisibilityMapper.toEntity(input)).thenReturn(entity);
        when(pageVisibilityRepository.save(entity)).thenReturn(saved);
        when(pageVisibilityMapper.toDto(saved)).thenReturn(expected);

        PageVisibilityDTO result = pageVisibilityService.save(input);

        assertThat(result).isSameAs(expected);
        verify(pageVisibilityMapper).toEntity(input);
        verify(pageVisibilityRepository).save(entity);
        verify(pageVisibilityMapper).toDto(saved);
    }

    @Test
    void update() {
        PageVisibilityDTO input = new PageVisibilityDTO();
        input.setId(ID);
        PageVisibility entity = new PageVisibility().id(ID);
        PageVisibility saved = new PageVisibility().id(ID);
        PageVisibilityDTO expected = new PageVisibilityDTO();
        expected.setId(ID);

        when(pageVisibilityMapper.toEntity(input)).thenReturn(entity);
        when(pageVisibilityRepository.save(entity)).thenReturn(saved);
        when(pageVisibilityMapper.toDto(saved)).thenReturn(expected);

        PageVisibilityDTO result = pageVisibilityService.update(input);

        assertThat(result).isSameAs(expected);
        verify(pageVisibilityMapper).toEntity(input);
        verify(pageVisibilityRepository).save(entity);
    }

    @Test
    void partialUpdate_found() {
        PageVisibilityDTO input = new PageVisibilityDTO();
        input.setId(ID);
        PageVisibility existing = new PageVisibility().id(ID);
        PageVisibilityDTO expected = new PageVisibilityDTO();
        expected.setId(ID);

        when(pageVisibilityRepository.findById(ID)).thenReturn(Optional.of(existing));
        when(pageVisibilityRepository.save(existing)).thenReturn(existing);
        when(pageVisibilityMapper.toDto(existing)).thenReturn(expected);

        Optional<PageVisibilityDTO> result = pageVisibilityService.partialUpdate(input);

        assertThat(result).contains(expected);
        verify(pageVisibilityMapper).partialUpdate(existing, input);
        verify(pageVisibilityRepository).save(existing);
    }

    @Test
    void partialUpdate_notFound() {
        PageVisibilityDTO input = new PageVisibilityDTO();
        input.setId(ID);
        when(pageVisibilityRepository.findById(ID)).thenReturn(Optional.empty());

        Optional<PageVisibilityDTO> result = pageVisibilityService.partialUpdate(input);

        assertThat(result).isEmpty();
    }

    @Test
    void findOne_found() {
        PageVisibility entity = new PageVisibility().id(ID);
        PageVisibilityDTO dto = new PageVisibilityDTO();
        dto.setId(ID);
        when(pageVisibilityRepository.findById(ID)).thenReturn(Optional.of(entity));
        when(pageVisibilityMapper.toDto(entity)).thenReturn(dto);

        Optional<PageVisibilityDTO> result = pageVisibilityService.findOne(ID);

        assertThat(result).contains(dto);
    }

    @Test
    void findOne_notFound() {
        when(pageVisibilityRepository.findById(ID)).thenReturn(Optional.empty());

        Optional<PageVisibilityDTO> result = pageVisibilityService.findOne(ID);

        assertThat(result).isEmpty();
    }

    @Test
    void delete() {
        pageVisibilityService.delete(ID);
        verify(pageVisibilityRepository).deleteById(ID);
    }

}
