package com.miniandmore.app.service;

import com.miniandmore.app.domain.*; // for static metamodels
import com.miniandmore.app.domain.Policy;
import com.miniandmore.app.repository.PolicyRepository;
import com.miniandmore.app.service.criteria.PolicyCriteria;
import com.miniandmore.app.service.dto.PolicyDTO;
import com.miniandmore.app.service.mapper.PolicyMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.jhipster.service.QueryService;

/**
 * Service for executing complex queries for {@link Policy} entities in the database.
 * The main input is a {@link PolicyCriteria} which gets converted to {@link Specification},
 * in a way that all the filters must apply.
 * It returns a {@link Page} of {@link PolicyDTO} which fulfills the criteria.
 */
@Service
@Transactional(readOnly = true)
public class PolicyQueryService extends QueryService<Policy> {

    private static final Logger LOG = LoggerFactory.getLogger(PolicyQueryService.class);

    private final PolicyRepository policyRepository;

    private final PolicyMapper policyMapper;

    public PolicyQueryService(PolicyRepository policyRepository, PolicyMapper policyMapper) {
        this.policyRepository = policyRepository;
        this.policyMapper = policyMapper;
    }

    /**
     * Return a {@link Page} of {@link PolicyDTO} which matches the criteria from the database.
     * @param criteria The object which holds all the filters, which the entities should match.
     * @param page The page, which should be returned.
     * @return the matching entities.
     */
    @Transactional(readOnly = true)
    public Page<PolicyDTO> findByCriteria(PolicyCriteria criteria, Pageable page) {
        LOG.debug("find by criteria : {}, page: {}", criteria, page);
        final Specification<Policy> specification = createSpecification(criteria);
        return policyRepository.findAll(specification, page).map(policyMapper::toDto);
    }

    /**
     * Return the number of matching entities in the database.
     * @param criteria The object which holds all the filters, which the entities should match.
     * @return the number of matching entities.
     */
    @Transactional(readOnly = true)
    public long countByCriteria(PolicyCriteria criteria) {
        LOG.debug("count by criteria : {}", criteria);
        final Specification<Policy> specification = createSpecification(criteria);
        return policyRepository.count(specification);
    }

    /**
     * Function to convert {@link PolicyCriteria} to a {@link Specification}
     * @param criteria The object which holds all the filters, which the entities should match.
     * @return the matching {@link Specification} of the entity.
     */
    protected Specification<Policy> createSpecification(PolicyCriteria criteria) {
        Specification<Policy> specification = Specification.unrestricted();
        if (criteria != null) {
            // This has to be called first, because the distinct method returns null
            specification = Specification.allOf(
                Boolean.TRUE.equals(criteria.getDistinct()) ? distinct(criteria.getDistinct()) : Specification.unrestricted(),
                buildRangeSpecification(criteria.getId(), Policy_.id),
                buildStringSpecification(criteria.getSlug(), Policy_.slug),
                buildStringSpecification(criteria.getTitle(), Policy_.title)
            );
        }
        return specification;
    }
}
