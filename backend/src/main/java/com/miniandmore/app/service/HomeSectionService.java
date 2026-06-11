package com.miniandmore.app.service;

import com.miniandmore.app.domain.HomeSection;
import com.miniandmore.app.repository.HomeSectionRepository;
import com.miniandmore.app.service.dto.HomeSectionDTO;
import com.miniandmore.app.service.mapper.HomeSectionMapper;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service Implementation for managing {@link com.miniandmore.app.domain.HomeSection}.
 */
@Service
@Transactional
public class HomeSectionService {

    private static final Logger LOG = LoggerFactory.getLogger(HomeSectionService.class);

    private final HomeSectionRepository homeSectionRepository;

    private final HomeSectionMapper homeSectionMapper;

    public HomeSectionService(HomeSectionRepository homeSectionRepository, HomeSectionMapper homeSectionMapper) {
        this.homeSectionRepository = homeSectionRepository;
        this.homeSectionMapper = homeSectionMapper;
    }

    /**
     * Save a homeSection.
     *
     * @param homeSectionDTO the entity to save.
     * @return the persisted entity.
     */
    public HomeSectionDTO save(HomeSectionDTO homeSectionDTO) {
        LOG.debug("Request to save HomeSection : {}", homeSectionDTO);
        HomeSection homeSection = homeSectionMapper.toEntity(homeSectionDTO);
        homeSection = homeSectionRepository.save(homeSection);
        return homeSectionMapper.toDto(homeSection);
    }

    /**
     * Update a homeSection.
     *
     * @param homeSectionDTO the entity to save.
     * @return the persisted entity.
     */
    public HomeSectionDTO update(HomeSectionDTO homeSectionDTO) {
        LOG.debug("Request to update HomeSection : {}", homeSectionDTO);
        HomeSection homeSection = homeSectionMapper.toEntity(homeSectionDTO);
        homeSection = homeSectionRepository.save(homeSection);
        return homeSectionMapper.toDto(homeSection);
    }

    /**
     * Partially update a homeSection.
     *
     * @param homeSectionDTO the entity to update partially.
     * @return the persisted entity.
     */
    public Optional<HomeSectionDTO> partialUpdate(HomeSectionDTO homeSectionDTO) {
        LOG.debug("Request to partially update HomeSection : {}", homeSectionDTO);

        return homeSectionRepository
            .findById(homeSectionDTO.getId())
            .map(existingHomeSection -> {
                homeSectionMapper.partialUpdate(existingHomeSection, homeSectionDTO);

                return existingHomeSection;
            })
            .map(homeSectionRepository::save)
            .map(homeSectionMapper::toDto);
    }

    /**
     * Get one homeSection by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    @Transactional(readOnly = true)
    public Optional<HomeSectionDTO> findOne(Long id) {
        LOG.debug("Request to get HomeSection : {}", id);
        return homeSectionRepository.findById(id).map(homeSectionMapper::toDto);
    }

    /**
     * Delete the homeSection by id.
     *
     * @param id the id of the entity.
     */
    public void delete(Long id) {
        LOG.debug("Request to delete HomeSection : {}", id);
        homeSectionRepository.deleteById(id);
    }
}
