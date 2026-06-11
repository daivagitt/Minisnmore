package com.miniandmore.integration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.miniandmore.integration.domain.ZohoSetting;
import com.miniandmore.integration.repository.ZohoSettingRepository;
import com.miniandmore.integration.service.dto.ZohoSettingDTO;
import com.miniandmore.integration.service.mapper.ZohoSettingMapper;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ZohoSettingServiceTest {

    @Mock
    private ZohoSettingRepository zohoSettingRepository;

    @Mock
    private ZohoSettingMapper zohoSettingMapper;

    @InjectMocks
    private ZohoSettingService zohoSettingService;

    @Test
    void save_mapsAndPersists() {
        ZohoSettingDTO dto = new ZohoSettingDTO();
        ZohoSetting entity = new ZohoSetting();
        when(zohoSettingMapper.toEntity(dto)).thenReturn(entity);
        when(zohoSettingRepository.save(entity)).thenReturn(entity);
        when(zohoSettingMapper.toDto(entity)).thenReturn(dto);

        assertThat(zohoSettingService.save(dto)).isSameAs(dto);
        verify(zohoSettingRepository).save(entity);
    }

    @Test
    void partialUpdate_whenFound() {
        ZohoSettingDTO dto = new ZohoSettingDTO();
        dto.setId(1L);
        ZohoSetting existing = new ZohoSetting();
        when(zohoSettingRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(zohoSettingRepository.save(existing)).thenReturn(existing);
        when(zohoSettingMapper.toDto(existing)).thenReturn(dto);

        assertThat(zohoSettingService.partialUpdate(dto)).contains(dto);
        verify(zohoSettingMapper).partialUpdate(existing, dto);
    }

    @Test
    void delete_delegatesToRepository() {
        zohoSettingService.delete(9L);
        verify(zohoSettingRepository).deleteById(9L);
    }
}
