package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.miniandmore.app.domain.HomeSection;
import com.miniandmore.app.repository.HomeSectionRepository;
import com.miniandmore.app.service.dto.HomeSectionDTO;
import com.miniandmore.app.service.mapper.HomeSectionMapper;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HomeSectionServiceTest {

    private static final Long ID = 1L;

    @Mock
    private HomeSectionRepository homeSectionRepository;

    @Mock
    private HomeSectionMapper homeSectionMapper;

    @InjectMocks
    private HomeSectionService homeSectionService;

    @Test
    void save() {
        HomeSectionDTO input = new HomeSectionDTO();
        HomeSection entity = new HomeSection().id(ID);
        HomeSection saved = new HomeSection().id(ID);
        HomeSectionDTO expected = new HomeSectionDTO();
        expected.setId(ID);

        when(homeSectionMapper.toEntity(input)).thenReturn(entity);
        when(homeSectionRepository.save(entity)).thenReturn(saved);
        when(homeSectionMapper.toDto(saved)).thenReturn(expected);

        HomeSectionDTO result = homeSectionService.save(input);

        assertThat(result).isSameAs(expected);
        verify(homeSectionMapper).toEntity(input);
        verify(homeSectionRepository).save(entity);
        verify(homeSectionMapper).toDto(saved);
    }

    @Test
    void update() {
        HomeSectionDTO input = new HomeSectionDTO();
        input.setId(ID);
        HomeSection entity = new HomeSection().id(ID);
        HomeSection saved = new HomeSection().id(ID);
        HomeSectionDTO expected = new HomeSectionDTO();
        expected.setId(ID);

        when(homeSectionMapper.toEntity(input)).thenReturn(entity);
        when(homeSectionRepository.save(entity)).thenReturn(saved);
        when(homeSectionMapper.toDto(saved)).thenReturn(expected);

        HomeSectionDTO result = homeSectionService.update(input);

        assertThat(result).isSameAs(expected);
        verify(homeSectionMapper).toEntity(input);
        verify(homeSectionRepository).save(entity);
    }

    @Test
    void partialUpdate_found() {
        HomeSectionDTO input = new HomeSectionDTO();
        input.setId(ID);
        HomeSection existing = new HomeSection().id(ID);
        HomeSectionDTO expected = new HomeSectionDTO();
        expected.setId(ID);

        when(homeSectionRepository.findById(ID)).thenReturn(Optional.of(existing));
        when(homeSectionRepository.save(existing)).thenReturn(existing);
        when(homeSectionMapper.toDto(existing)).thenReturn(expected);

        Optional<HomeSectionDTO> result = homeSectionService.partialUpdate(input);

        assertThat(result).contains(expected);
        verify(homeSectionMapper).partialUpdate(existing, input);
        verify(homeSectionRepository).save(existing);
    }

    @Test
    void partialUpdate_notFound() {
        HomeSectionDTO input = new HomeSectionDTO();
        input.setId(ID);
        when(homeSectionRepository.findById(ID)).thenReturn(Optional.empty());

        Optional<HomeSectionDTO> result = homeSectionService.partialUpdate(input);

        assertThat(result).isEmpty();
    }

    @Test
    void findOne_found() {
        HomeSection entity = new HomeSection().id(ID);
        HomeSectionDTO dto = new HomeSectionDTO();
        dto.setId(ID);
        when(homeSectionRepository.findById(ID)).thenReturn(Optional.of(entity));
        when(homeSectionMapper.toDto(entity)).thenReturn(dto);

        Optional<HomeSectionDTO> result = homeSectionService.findOne(ID);

        assertThat(result).contains(dto);
    }

    @Test
    void findOne_notFound() {
        when(homeSectionRepository.findById(ID)).thenReturn(Optional.empty());

        Optional<HomeSectionDTO> result = homeSectionService.findOne(ID);

        assertThat(result).isEmpty();
    }

    @Test
    void delete() {
        homeSectionService.delete(ID);
        verify(homeSectionRepository).deleteById(ID);
    }

}
