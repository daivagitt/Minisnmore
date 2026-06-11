package com.miniandmore.app.service;

import com.miniandmore.app.domain.PageVisibility;
import com.miniandmore.app.repository.PageVisibilityRepository;
import com.miniandmore.app.service.dto.PageVisibilityDTO;
import com.miniandmore.app.service.mapper.PageVisibilityMapper;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service Implementation for managing {@link com.miniandmore.app.domain.PageVisibility}.
 */
@Service
@Transactional
public class PageVisibilityService {

    private static final Logger LOG = LoggerFactory.getLogger(PageVisibilityService.class);

    private final PageVisibilityRepository pageVisibilityRepository;

    private final PageVisibilityMapper pageVisibilityMapper;

    public PageVisibilityService(PageVisibilityRepository pageVisibilityRepository, PageVisibilityMapper pageVisibilityMapper) {
        this.pageVisibilityRepository = pageVisibilityRepository;
        this.pageVisibilityMapper = pageVisibilityMapper;
    }

    /**
     * Save a pageVisibility.
     *
     * @param pageVisibilityDTO the entity to save.
     * @return the persisted entity.
     */
    public PageVisibilityDTO save(PageVisibilityDTO pageVisibilityDTO) {
        LOG.debug("Request to save PageVisibility : {}", pageVisibilityDTO);
        PageVisibility pageVisibility = pageVisibilityMapper.toEntity(pageVisibilityDTO);
        pageVisibility = pageVisibilityRepository.save(pageVisibility);
        return pageVisibilityMapper.toDto(pageVisibility);
    }

    /**
     * Update a pageVisibility.
     *
     * @param pageVisibilityDTO the entity to save.
     * @return the persisted entity.
     */
    public PageVisibilityDTO update(PageVisibilityDTO pageVisibilityDTO) {
        LOG.debug("Request to update PageVisibility : {}", pageVisibilityDTO);
        PageVisibility pageVisibility = pageVisibilityMapper.toEntity(pageVisibilityDTO);
        pageVisibility = pageVisibilityRepository.save(pageVisibility);
        return pageVisibilityMapper.toDto(pageVisibility);
    }

    /**
     * Partially update a pageVisibility.
     *
     * @param pageVisibilityDTO the entity to update partially.
     * @return the persisted entity.
     */
    public Optional<PageVisibilityDTO> partialUpdate(PageVisibilityDTO pageVisibilityDTO) {
        LOG.debug("Request to partially update PageVisibility : {}", pageVisibilityDTO);

        return pageVisibilityRepository
            .findById(pageVisibilityDTO.getId())
            .map(existingPageVisibility -> {
                pageVisibilityMapper.partialUpdate(existingPageVisibility, pageVisibilityDTO);

                return existingPageVisibility;
            })
            .map(pageVisibilityRepository::save)
            .map(pageVisibilityMapper::toDto);
    }

    /**
     * Get one pageVisibility by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    @Transactional(readOnly = true)
    public Optional<PageVisibilityDTO> findOne(Long id) {
        LOG.debug("Request to get PageVisibility : {}", id);
        return pageVisibilityRepository.findById(id).map(pageVisibilityMapper::toDto);
    }

    /**
     * Delete the pageVisibility by id.
     *
     * @param id the id of the entity.
     */
    public void delete(Long id) {
        LOG.debug("Request to delete PageVisibility : {}", id);
        pageVisibilityRepository.deleteById(id);
    }
}
