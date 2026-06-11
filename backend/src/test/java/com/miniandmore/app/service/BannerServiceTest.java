package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.miniandmore.app.domain.Banner;
import com.miniandmore.app.repository.BannerRepository;
import com.miniandmore.app.service.dto.BannerDTO;
import com.miniandmore.app.service.mapper.BannerMapper;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BannerServiceTest {

    private static final Long ID = 1L;

    @Mock
    private BannerRepository bannerRepository;

    @Mock
    private BannerMapper bannerMapper;

    @InjectMocks
    private BannerService bannerService;

    @Test
    void save() {
        BannerDTO input = new BannerDTO();
        Banner entity = new Banner().id(ID);
        Banner saved = new Banner().id(ID);
        BannerDTO expected = new BannerDTO();
        expected.setId(ID);

        when(bannerMapper.toEntity(input)).thenReturn(entity);
        when(bannerRepository.save(entity)).thenReturn(saved);
        when(bannerMapper.toDto(saved)).thenReturn(expected);

        BannerDTO result = bannerService.save(input);

        assertThat(result).isSameAs(expected);
        verify(bannerMapper).toEntity(input);
        verify(bannerRepository).save(entity);
        verify(bannerMapper).toDto(saved);
    }

    @Test
    void update() {
        BannerDTO input = new BannerDTO();
        input.setId(ID);
        Banner entity = new Banner().id(ID);
        Banner saved = new Banner().id(ID);
        BannerDTO expected = new BannerDTO();
        expected.setId(ID);

        when(bannerMapper.toEntity(input)).thenReturn(entity);
        when(bannerRepository.save(entity)).thenReturn(saved);
        when(bannerMapper.toDto(saved)).thenReturn(expected);

        BannerDTO result = bannerService.update(input);

        assertThat(result).isSameAs(expected);
        verify(bannerMapper).toEntity(input);
        verify(bannerRepository).save(entity);
    }

    @Test
    void partialUpdate_found() {
        BannerDTO input = new BannerDTO();
        input.setId(ID);
        Banner existing = new Banner().id(ID);
        BannerDTO expected = new BannerDTO();
        expected.setId(ID);

        when(bannerRepository.findById(ID)).thenReturn(Optional.of(existing));
        when(bannerRepository.save(existing)).thenReturn(existing);
        when(bannerMapper.toDto(existing)).thenReturn(expected);

        Optional<BannerDTO> result = bannerService.partialUpdate(input);

        assertThat(result).contains(expected);
        verify(bannerMapper).partialUpdate(existing, input);
        verify(bannerRepository).save(existing);
    }

    @Test
    void partialUpdate_notFound() {
        BannerDTO input = new BannerDTO();
        input.setId(ID);
        when(bannerRepository.findById(ID)).thenReturn(Optional.empty());

        Optional<BannerDTO> result = bannerService.partialUpdate(input);

        assertThat(result).isEmpty();
    }

    @Test
    void findOne_found() {
        Banner entity = new Banner().id(ID);
        BannerDTO dto = new BannerDTO();
        dto.setId(ID);
        when(bannerRepository.findById(ID)).thenReturn(Optional.of(entity));
        when(bannerMapper.toDto(entity)).thenReturn(dto);

        Optional<BannerDTO> result = bannerService.findOne(ID);

        assertThat(result).contains(dto);
    }

    @Test
    void findOne_notFound() {
        when(bannerRepository.findById(ID)).thenReturn(Optional.empty());

        Optional<BannerDTO> result = bannerService.findOne(ID);

        assertThat(result).isEmpty();
    }

    @Test
    void delete() {
        bannerService.delete(ID);
        verify(bannerRepository).deleteById(ID);
    }

}
