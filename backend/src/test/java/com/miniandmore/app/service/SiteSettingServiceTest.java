package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.miniandmore.app.domain.SiteSetting;
import com.miniandmore.app.repository.SiteSettingRepository;
import com.miniandmore.app.service.dto.SiteSettingDTO;
import com.miniandmore.app.service.mapper.SiteSettingMapper;
import jakarta.persistence.EntityManager;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SiteSettingServiceTest {

    private static final Long ID = 1L;

    @Mock
    private SiteSettingRepository siteSettingRepository;

    @Mock
    private SiteSettingMapper siteSettingMapper;

    @Mock
    private EntityManager entityManager;

    @Mock
    private AdminConfigChangeMailService adminConfigChangeMailService;

    @InjectMocks
    private SiteSettingService siteSettingService;

    @Test
    void save() {
        SiteSettingDTO input = new SiteSettingDTO();
        SiteSetting entity = new SiteSetting().id(ID);
        SiteSetting saved = new SiteSetting().id(ID);
        SiteSettingDTO expected = new SiteSettingDTO();
        expected.setId(ID);

        when(siteSettingMapper.toEntity(input)).thenReturn(entity);
        when(siteSettingRepository.save(entity)).thenReturn(saved);
        when(siteSettingMapper.toDto(saved)).thenReturn(expected);

        SiteSettingDTO result = siteSettingService.save(input);

        assertThat(result).isSameAs(expected);
        verify(siteSettingMapper).toEntity(input);
        verify(siteSettingRepository).save(entity);
        verify(siteSettingMapper).toDto(saved);
        verify(adminConfigChangeMailService).notifySiteSettingsChangedAfterCommit(null, expected);
    }

    @Test
    void update() {
        SiteSettingDTO input = new SiteSettingDTO();
        input.setId(ID);
        SiteSetting entity = new SiteSetting().id(ID);
        SiteSetting saved = new SiteSetting().id(ID);
        SiteSettingDTO expected = new SiteSettingDTO();
        expected.setId(ID);
        SiteSetting existing = new SiteSetting().id(ID).brandName("Old");
        SiteSettingDTO previous = new SiteSettingDTO();
        previous.setId(ID);
        previous.setBrandName("Old");

        when(siteSettingRepository.findById(ID)).thenReturn(Optional.of(existing));
        when(siteSettingMapper.toDto(any(SiteSetting.class))).thenReturn(previous, expected);
        when(siteSettingMapper.toEntity(input)).thenReturn(entity);
        when(siteSettingRepository.save(entity)).thenReturn(saved);

        SiteSettingDTO result = siteSettingService.update(input);

        assertThat(result).isSameAs(expected);
        verify(entityManager).detach(existing);
        verify(siteSettingMapper).toEntity(input);
        verify(siteSettingRepository).save(entity);
        verify(adminConfigChangeMailService).notifySiteSettingsChangedAfterCommit(previous, expected);
    }

    @Test
    void update_preservesExistingCodFeeWhenOlderClientOmitsIt() {
        SiteSettingDTO input = new SiteSettingDTO();
        input.setId(ID);
        SiteSetting existing = new SiteSetting().id(ID).codFeeInr(149);
        SiteSetting mapped = new SiteSetting().id(ID);
        when(siteSettingRepository.findById(ID)).thenReturn(Optional.of(existing));
        when(siteSettingMapper.toDto(existing)).thenReturn(new SiteSettingDTO());
        when(siteSettingRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(existing));
        when(siteSettingMapper.toEntity(input)).thenReturn(mapped);
        when(siteSettingRepository.save(mapped)).thenReturn(mapped);
        when(siteSettingMapper.toDto(mapped)).thenReturn(input);

        siteSettingService.update(input);

        assertThat(input.getCodFeeInr()).isEqualTo(149);
    }

    @Test
    void update_preservesExistingDeliveryEtaBufferDaysWhenOlderClientOmitsIt() {
        SiteSettingDTO input = new SiteSettingDTO();
        input.setId(ID);
        SiteSetting existing = new SiteSetting().id(ID).deliveryEtaBufferDays(2);
        SiteSetting mapped = new SiteSetting().id(ID);
        when(siteSettingRepository.findById(ID)).thenReturn(Optional.of(existing));
        when(siteSettingMapper.toDto(existing)).thenReturn(new SiteSettingDTO());
        when(siteSettingRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(existing));
        when(siteSettingMapper.toEntity(input)).thenReturn(mapped);
        when(siteSettingRepository.save(mapped)).thenReturn(mapped);
        when(siteSettingMapper.toDto(mapped)).thenReturn(input);

        siteSettingService.update(input);

        assertThat(input.getDeliveryEtaBufferDays()).isEqualTo(2);
    }

    @Test
    void partialUpdate_found() {
        SiteSettingDTO input = new SiteSettingDTO();
        input.setId(ID);
        SiteSetting existing = new SiteSetting().id(ID);
        SiteSettingDTO previous = new SiteSettingDTO();
        previous.setId(ID);
        SiteSettingDTO expected = new SiteSettingDTO();
        expected.setId(ID);

        when(siteSettingRepository.findById(ID)).thenReturn(Optional.of(existing));
        when(siteSettingMapper.toDto(existing)).thenReturn(previous, expected);
        when(siteSettingRepository.save(existing)).thenReturn(existing);

        Optional<SiteSettingDTO> result = siteSettingService.partialUpdate(input);

        assertThat(result).contains(expected);
        verify(entityManager).detach(existing);
        verify(siteSettingMapper).partialUpdate(existing, input);
        verify(siteSettingRepository).save(existing);
        verify(adminConfigChangeMailService).notifySiteSettingsChangedAfterCommit(previous, expected);
    }

    @Test
    void partialUpdate_notFound() {
        SiteSettingDTO input = new SiteSettingDTO();
        input.setId(ID);
        when(siteSettingRepository.findById(ID)).thenReturn(Optional.empty());

        Optional<SiteSettingDTO> result = siteSettingService.partialUpdate(input);

        assertThat(result).isEmpty();
    }

    @Test
    void findOne_found() {
        SiteSetting entity = new SiteSetting().id(ID);
        SiteSettingDTO dto = new SiteSettingDTO();
        dto.setId(ID);
        when(siteSettingRepository.findById(ID)).thenReturn(Optional.of(entity));
        when(siteSettingMapper.toDto(entity)).thenReturn(dto);

        Optional<SiteSettingDTO> result = siteSettingService.findOne(ID);

        assertThat(result).contains(dto);
    }

    @Test
    void findOne_notFound() {
        when(siteSettingRepository.findById(ID)).thenReturn(Optional.empty());

        Optional<SiteSettingDTO> result = siteSettingService.findOne(ID);

        assertThat(result).isEmpty();
    }

    @Test
    void delete() {
        siteSettingService.delete(ID);
        verify(siteSettingRepository).deleteById(ID);
    }
}
