package com.miniandmore.app.service;

import com.miniandmore.app.domain.SiteSetting;
import com.miniandmore.app.repository.SiteSettingRepository;
import com.miniandmore.app.service.dto.SiteSettingDTO;
import com.miniandmore.app.service.mapper.SiteSettingMapper;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service Implementation for managing {@link com.miniandmore.app.domain.SiteSetting}.
 */
@Service
@Transactional
public class SiteSettingService {

    private static final Logger LOG = LoggerFactory.getLogger(SiteSettingService.class);

    private final SiteSettingRepository siteSettingRepository;

    private final SiteSettingMapper siteSettingMapper;

    public SiteSettingService(SiteSettingRepository siteSettingRepository, SiteSettingMapper siteSettingMapper) {
        this.siteSettingRepository = siteSettingRepository;
        this.siteSettingMapper = siteSettingMapper;
    }

    /**
     * Save a siteSetting.
     *
     * @param siteSettingDTO the entity to save.
     * @return the persisted entity.
     */
    public SiteSettingDTO save(SiteSettingDTO siteSettingDTO) {
        LOG.debug("Request to save SiteSetting : {}", siteSettingDTO);
        SiteSetting siteSetting = siteSettingMapper.toEntity(siteSettingDTO);
        siteSetting = siteSettingRepository.save(siteSetting);
        return siteSettingMapper.toDto(siteSetting);
    }

    /**
     * Update a siteSetting.
     *
     * @param siteSettingDTO the entity to save.
     * @return the persisted entity.
     */
    public SiteSettingDTO update(SiteSettingDTO siteSettingDTO) {
        LOG.debug("Request to update SiteSetting : {}", siteSettingDTO);
        SiteSetting siteSetting = siteSettingMapper.toEntity(siteSettingDTO);
        siteSetting = siteSettingRepository.save(siteSetting);
        return siteSettingMapper.toDto(siteSetting);
    }

    /**
     * Partially update a siteSetting.
     *
     * @param siteSettingDTO the entity to update partially.
     * @return the persisted entity.
     */
    public Optional<SiteSettingDTO> partialUpdate(SiteSettingDTO siteSettingDTO) {
        LOG.debug("Request to partially update SiteSetting : {}", siteSettingDTO);

        return siteSettingRepository
            .findById(siteSettingDTO.getId())
            .map(existingSiteSetting -> {
                siteSettingMapper.partialUpdate(existingSiteSetting, siteSettingDTO);

                return existingSiteSetting;
            })
            .map(siteSettingRepository::save)
            .map(siteSettingMapper::toDto);
    }

    /**
     * Get one siteSetting by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    @Transactional(readOnly = true)
    public Optional<SiteSettingDTO> findOne(Long id) {
        LOG.debug("Request to get SiteSetting : {}", id);
        return siteSettingRepository.findById(id).map(siteSettingMapper::toDto);
    }

    /**
     * Delete the siteSetting by id.
     *
     * @param id the id of the entity.
     */
    public void delete(Long id) {
        LOG.debug("Request to delete SiteSetting : {}", id);
        siteSettingRepository.deleteById(id);
    }
}
