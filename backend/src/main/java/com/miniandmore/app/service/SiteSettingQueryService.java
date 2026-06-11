package com.miniandmore.app.service;

import com.miniandmore.app.domain.*; // for static metamodels
import com.miniandmore.app.domain.SiteSetting;
import com.miniandmore.app.repository.SiteSettingRepository;
import com.miniandmore.app.service.criteria.SiteSettingCriteria;
import com.miniandmore.app.service.dto.SiteSettingDTO;
import com.miniandmore.app.service.mapper.SiteSettingMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.jhipster.service.QueryService;

/**
 * Service for executing complex queries for {@link SiteSetting} entities in the database.
 * The main input is a {@link SiteSettingCriteria} which gets converted to {@link Specification},
 * in a way that all the filters must apply.
 * It returns a {@link Page} of {@link SiteSettingDTO} which fulfills the criteria.
 */
@Service
@Transactional(readOnly = true)
public class SiteSettingQueryService extends QueryService<SiteSetting> {

    private static final Logger LOG = LoggerFactory.getLogger(SiteSettingQueryService.class);

    private final SiteSettingRepository siteSettingRepository;

    private final SiteSettingMapper siteSettingMapper;

    public SiteSettingQueryService(SiteSettingRepository siteSettingRepository, SiteSettingMapper siteSettingMapper) {
        this.siteSettingRepository = siteSettingRepository;
        this.siteSettingMapper = siteSettingMapper;
    }

    /**
     * Return a {@link Page} of {@link SiteSettingDTO} which matches the criteria from the database.
     * @param criteria The object which holds all the filters, which the entities should match.
     * @param page The page, which should be returned.
     * @return the matching entities.
     */
    @Transactional(readOnly = true)
    public Page<SiteSettingDTO> findByCriteria(SiteSettingCriteria criteria, Pageable page) {
        LOG.debug("find by criteria : {}, page: {}", criteria, page);
        final Specification<SiteSetting> specification = createSpecification(criteria);
        return siteSettingRepository.findAll(specification, page).map(siteSettingMapper::toDto);
    }

    /**
     * Return the number of matching entities in the database.
     * @param criteria The object which holds all the filters, which the entities should match.
     * @return the number of matching entities.
     */
    @Transactional(readOnly = true)
    public long countByCriteria(SiteSettingCriteria criteria) {
        LOG.debug("count by criteria : {}", criteria);
        final Specification<SiteSetting> specification = createSpecification(criteria);
        return siteSettingRepository.count(specification);
    }

    /**
     * Function to convert {@link SiteSettingCriteria} to a {@link Specification}
     * @param criteria The object which holds all the filters, which the entities should match.
     * @return the matching {@link Specification} of the entity.
     */
    protected Specification<SiteSetting> createSpecification(SiteSettingCriteria criteria) {
        Specification<SiteSetting> specification = Specification.unrestricted();
        if (criteria != null) {
            // This has to be called first, because the distinct method returns null
            specification = Specification.allOf(
                Boolean.TRUE.equals(criteria.getDistinct()) ? distinct(criteria.getDistinct()) : Specification.unrestricted(),
                buildRangeSpecification(criteria.getId(), SiteSetting_.id),
                buildStringSpecification(criteria.getBrandName(), SiteSetting_.brandName),
                buildStringSpecification(criteria.getCareLabel(), SiteSetting_.careLabel),
                buildStringSpecification(criteria.getContactEmail(), SiteSetting_.contactEmail),
                buildStringSpecification(criteria.getContactPhone(), SiteSetting_.contactPhone),
                buildStringSpecification(criteria.getEcatalogueLabel(), SiteSetting_.ecatalogueLabel),
                buildStringSpecification(criteria.getEcatalogueUrl(), SiteSetting_.ecatalogueUrl),
                buildStringSpecification(criteria.getFacebookUrl(), SiteSetting_.facebookUrl),
                buildStringSpecification(criteria.getFooterCopyright(), SiteSetting_.footerCopyright),
                buildStringSpecification(criteria.getFooterTagline(), SiteSetting_.footerTagline),
                buildStringSpecification(criteria.getInstagramUrl(), SiteSetting_.instagramUrl),
                buildStringSpecification(criteria.getLegalEntityName(), SiteSetting_.legalEntityName),
                buildStringSpecification(criteria.getLogoHorizontalUrl(), SiteSetting_.logoHorizontalUrl),
                buildStringSpecification(criteria.getLogoUrl(), SiteSetting_.logoUrl),
                buildStringSpecification(criteria.getTwitterUrl(), SiteSetting_.twitterUrl),
                buildStringSpecification(criteria.getWhatsappNumber(), SiteSetting_.whatsappNumber),
                buildStringSpecification(criteria.getYoutubeUrl(), SiteSetting_.youtubeUrl)
            );
        }
        return specification;
    }
}
