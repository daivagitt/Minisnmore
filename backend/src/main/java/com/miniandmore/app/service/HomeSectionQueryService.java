package com.miniandmore.app.service;

import com.miniandmore.app.domain.*; // for static metamodels
import com.miniandmore.app.domain.HomeSection;
import com.miniandmore.app.repository.HomeSectionRepository;
import com.miniandmore.app.service.criteria.HomeSectionCriteria;
import com.miniandmore.app.service.dto.HomeSectionDTO;
import com.miniandmore.app.service.mapper.HomeSectionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.jhipster.service.QueryService;

/**
 * Service for executing complex queries for {@link HomeSection} entities in the database.
 * The main input is a {@link HomeSectionCriteria} which gets converted to {@link Specification},
 * in a way that all the filters must apply.
 * It returns a {@link Page} of {@link HomeSectionDTO} which fulfills the criteria.
 */
@Service
@Transactional(readOnly = true)
public class HomeSectionQueryService extends QueryService<HomeSection> {

    private static final Logger LOG = LoggerFactory.getLogger(HomeSectionQueryService.class);

    private final HomeSectionRepository homeSectionRepository;

    private final HomeSectionMapper homeSectionMapper;

    public HomeSectionQueryService(HomeSectionRepository homeSectionRepository, HomeSectionMapper homeSectionMapper) {
        this.homeSectionRepository = homeSectionRepository;
        this.homeSectionMapper = homeSectionMapper;
    }

    /**
     * Return a {@link Page} of {@link HomeSectionDTO} which matches the criteria from the database.
     * @param criteria The object which holds all the filters, which the entities should match.
     * @param page The page, which should be returned.
     * @return the matching entities.
     */
    @Transactional(readOnly = true)
    public Page<HomeSectionDTO> findByCriteria(HomeSectionCriteria criteria, Pageable page) {
        LOG.debug("find by criteria : {}, page: {}", criteria, page);
        final Specification<HomeSection> specification = createSpecification(criteria);
        return homeSectionRepository.findAll(specification, page).map(homeSectionMapper::toDto);
    }

    /**
     * Return the number of matching entities in the database.
     * @param criteria The object which holds all the filters, which the entities should match.
     * @return the number of matching entities.
     */
    @Transactional(readOnly = true)
    public long countByCriteria(HomeSectionCriteria criteria) {
        LOG.debug("count by criteria : {}", criteria);
        final Specification<HomeSection> specification = createSpecification(criteria);
        return homeSectionRepository.count(specification);
    }

    /**
     * Function to convert {@link HomeSectionCriteria} to a {@link Specification}
     * @param criteria The object which holds all the filters, which the entities should match.
     * @return the matching {@link Specification} of the entity.
     */
    protected Specification<HomeSection> createSpecification(HomeSectionCriteria criteria) {
        Specification<HomeSection> specification = Specification.unrestricted();
        if (criteria != null) {
            // This has to be called first, because the distinct method returns null
            specification = Specification.allOf(
                Boolean.TRUE.equals(criteria.getDistinct()) ? distinct(criteria.getDistinct()) : Specification.unrestricted(),
                buildRangeSpecification(criteria.getId(), HomeSection_.id),
                buildStringSpecification(criteria.getSectionKey(), HomeSection_.sectionKey),
                buildStringSpecification(criteria.getTitle(), HomeSection_.title),
                buildStringSpecification(criteria.getSubtitle(), HomeSection_.subtitle),
                buildSpecification(criteria.getEnabled(), HomeSection_.enabled),
                buildRangeSpecification(criteria.getSortOrder(), HomeSection_.sortOrder)
            );
        }
        return specification;
    }
}
