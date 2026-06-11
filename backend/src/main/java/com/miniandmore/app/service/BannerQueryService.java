package com.miniandmore.app.service;

import com.miniandmore.app.domain.*; // for static metamodels
import com.miniandmore.app.domain.Banner;
import com.miniandmore.app.repository.BannerRepository;
import com.miniandmore.app.service.criteria.BannerCriteria;
import com.miniandmore.app.service.dto.BannerDTO;
import com.miniandmore.app.service.mapper.BannerMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.jhipster.service.QueryService;

/**
 * Service for executing complex queries for {@link Banner} entities in the database.
 * The main input is a {@link BannerCriteria} which gets converted to {@link Specification},
 * in a way that all the filters must apply.
 * It returns a {@link Page} of {@link BannerDTO} which fulfills the criteria.
 */
@Service
@Transactional(readOnly = true)
public class BannerQueryService extends QueryService<Banner> {

    private static final Logger LOG = LoggerFactory.getLogger(BannerQueryService.class);

    private final BannerRepository bannerRepository;

    private final BannerMapper bannerMapper;

    public BannerQueryService(BannerRepository bannerRepository, BannerMapper bannerMapper) {
        this.bannerRepository = bannerRepository;
        this.bannerMapper = bannerMapper;
    }

    /**
     * Return a {@link Page} of {@link BannerDTO} which matches the criteria from the database.
     * @param criteria The object which holds all the filters, which the entities should match.
     * @param page The page, which should be returned.
     * @return the matching entities.
     */
    @Transactional(readOnly = true)
    public Page<BannerDTO> findByCriteria(BannerCriteria criteria, Pageable page) {
        LOG.debug("find by criteria : {}, page: {}", criteria, page);
        final Specification<Banner> specification = createSpecification(criteria);
        return bannerRepository.findAll(specification, page).map(bannerMapper::toDto);
    }

    /**
     * Return the number of matching entities in the database.
     * @param criteria The object which holds all the filters, which the entities should match.
     * @return the number of matching entities.
     */
    @Transactional(readOnly = true)
    public long countByCriteria(BannerCriteria criteria) {
        LOG.debug("count by criteria : {}", criteria);
        final Specification<Banner> specification = createSpecification(criteria);
        return bannerRepository.count(specification);
    }

    /**
     * Function to convert {@link BannerCriteria} to a {@link Specification}
     * @param criteria The object which holds all the filters, which the entities should match.
     * @return the matching {@link Specification} of the entity.
     */
    protected Specification<Banner> createSpecification(BannerCriteria criteria) {
        Specification<Banner> specification = Specification.unrestricted();
        if (criteria != null) {
            // This has to be called first, because the distinct method returns null
            specification = Specification.allOf(
                Boolean.TRUE.equals(criteria.getDistinct()) ? distinct(criteria.getDistinct()) : Specification.unrestricted(),
                buildRangeSpecification(criteria.getId(), Banner_.id),
                buildStringSpecification(criteria.getTitle(), Banner_.title),
                buildStringSpecification(criteria.getSubtitle(), Banner_.subtitle),
                buildStringSpecification(criteria.getImageUrl(), Banner_.imageUrl),
                buildStringSpecification(criteria.getLinkUrl(), Banner_.linkUrl),
                buildStringSpecification(criteria.getCtaLabel(), Banner_.ctaLabel),
                buildRangeSpecification(criteria.getSortOrder(), Banner_.sortOrder),
                buildSpecification(criteria.getActive(), Banner_.active)
            );
        }
        return specification;
    }
}
