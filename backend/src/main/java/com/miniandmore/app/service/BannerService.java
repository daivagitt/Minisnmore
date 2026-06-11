package com.miniandmore.app.service;

import com.miniandmore.app.domain.Banner;
import com.miniandmore.app.repository.BannerRepository;
import com.miniandmore.app.service.dto.BannerDTO;
import com.miniandmore.app.service.mapper.BannerMapper;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service Implementation for managing {@link com.miniandmore.app.domain.Banner}.
 */
@Service
@Transactional
public class BannerService {

    private static final Logger LOG = LoggerFactory.getLogger(BannerService.class);

    private final BannerRepository bannerRepository;

    private final BannerMapper bannerMapper;

    public BannerService(BannerRepository bannerRepository, BannerMapper bannerMapper) {
        this.bannerRepository = bannerRepository;
        this.bannerMapper = bannerMapper;
    }

    /**
     * Save a banner.
     *
     * @param bannerDTO the entity to save.
     * @return the persisted entity.
     */
    public BannerDTO save(BannerDTO bannerDTO) {
        LOG.debug("Request to save Banner : {}", bannerDTO);
        Banner banner = bannerMapper.toEntity(bannerDTO);
        banner = bannerRepository.save(banner);
        return bannerMapper.toDto(banner);
    }

    /**
     * Update a banner.
     *
     * @param bannerDTO the entity to save.
     * @return the persisted entity.
     */
    public BannerDTO update(BannerDTO bannerDTO) {
        LOG.debug("Request to update Banner : {}", bannerDTO);
        Banner banner = bannerMapper.toEntity(bannerDTO);
        banner = bannerRepository.save(banner);
        return bannerMapper.toDto(banner);
    }

    /**
     * Partially update a banner.
     *
     * @param bannerDTO the entity to update partially.
     * @return the persisted entity.
     */
    public Optional<BannerDTO> partialUpdate(BannerDTO bannerDTO) {
        LOG.debug("Request to partially update Banner : {}", bannerDTO);

        return bannerRepository
            .findById(bannerDTO.getId())
            .map(existingBanner -> {
                bannerMapper.partialUpdate(existingBanner, bannerDTO);

                return existingBanner;
            })
            .map(bannerRepository::save)
            .map(bannerMapper::toDto);
    }

    /**
     * Get one banner by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    @Transactional(readOnly = true)
    public Optional<BannerDTO> findOne(Long id) {
        LOG.debug("Request to get Banner : {}", id);
        return bannerRepository.findById(id).map(bannerMapper::toDto);
    }

    /**
     * Delete the banner by id.
     *
     * @param id the id of the entity.
     */
    public void delete(Long id) {
        LOG.debug("Request to delete Banner : {}", id);
        bannerRepository.deleteById(id);
    }
}
