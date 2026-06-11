package com.miniandmore.integration.service;

import com.miniandmore.integration.domain.ZohoSetting;
import com.miniandmore.integration.repository.ZohoSettingRepository;
import com.miniandmore.integration.service.dto.ZohoSettingDTO;
import com.miniandmore.integration.service.mapper.ZohoSettingMapper;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service Implementation for managing {@link com.miniandmore.integration.domain.ZohoSetting}.
 */
@Service
@Transactional
public class ZohoSettingService {

    private static final Logger LOG = LoggerFactory.getLogger(ZohoSettingService.class);

    private final ZohoSettingRepository zohoSettingRepository;

    private final ZohoSettingMapper zohoSettingMapper;

    public ZohoSettingService(ZohoSettingRepository zohoSettingRepository, ZohoSettingMapper zohoSettingMapper) {
        this.zohoSettingRepository = zohoSettingRepository;
        this.zohoSettingMapper = zohoSettingMapper;
    }

    /**
     * Save a zohoSetting.
     *
     * @param zohoSettingDTO the entity to save.
     * @return the persisted entity.
     */
    public ZohoSettingDTO save(ZohoSettingDTO zohoSettingDTO) {
        LOG.debug("Request to save ZohoSetting : {}", zohoSettingDTO);
        ZohoSetting zohoSetting = zohoSettingMapper.toEntity(zohoSettingDTO);
        zohoSetting = zohoSettingRepository.save(zohoSetting);
        return zohoSettingMapper.toDto(zohoSetting);
    }

    /**
     * Update a zohoSetting.
     *
     * @param zohoSettingDTO the entity to save.
     * @return the persisted entity.
     */
    public ZohoSettingDTO update(ZohoSettingDTO zohoSettingDTO) {
        LOG.debug("Request to update ZohoSetting : {}", zohoSettingDTO);
        ZohoSetting zohoSetting = zohoSettingMapper.toEntity(zohoSettingDTO);
        zohoSetting = zohoSettingRepository.save(zohoSetting);
        return zohoSettingMapper.toDto(zohoSetting);
    }

    /**
     * Partially update a zohoSetting.
     *
     * @param zohoSettingDTO the entity to update partially.
     * @return the persisted entity.
     */
    public Optional<ZohoSettingDTO> partialUpdate(ZohoSettingDTO zohoSettingDTO) {
        LOG.debug("Request to partially update ZohoSetting : {}", zohoSettingDTO);

        return zohoSettingRepository
            .findById(zohoSettingDTO.getId())
            .map(existingZohoSetting -> {
                zohoSettingMapper.partialUpdate(existingZohoSetting, zohoSettingDTO);

                return existingZohoSetting;
            })
            .map(zohoSettingRepository::save)
            .map(zohoSettingMapper::toDto);
    }

    /**
     * Get one zohoSetting by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    @Transactional(readOnly = true)
    public Optional<ZohoSettingDTO> findOne(Long id) {
        LOG.debug("Request to get ZohoSetting : {}", id);
        return zohoSettingRepository.findById(id).map(zohoSettingMapper::toDto);
    }

    /**
     * Delete the zohoSetting by id.
     *
     * @param id the id of the entity.
     */
    public void delete(Long id) {
        LOG.debug("Request to delete ZohoSetting : {}", id);
        zohoSettingRepository.deleteById(id);
    }
}
