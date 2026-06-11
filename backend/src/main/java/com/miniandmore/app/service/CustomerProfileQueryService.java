package com.miniandmore.app.service;

import com.miniandmore.app.domain.*; // for static metamodels
import com.miniandmore.app.domain.CustomerProfile;
import com.miniandmore.app.repository.CustomerProfileRepository;
import com.miniandmore.app.service.criteria.CustomerProfileCriteria;
import com.miniandmore.app.service.dto.CustomerProfileDTO;
import com.miniandmore.app.service.mapper.CustomerProfileMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.jhipster.service.QueryService;

/**
 * Service for executing complex queries for {@link CustomerProfile} entities in the database.
 * The main input is a {@link CustomerProfileCriteria} which gets converted to {@link Specification},
 * in a way that all the filters must apply.
 * It returns a {@link Page} of {@link CustomerProfileDTO} which fulfills the criteria.
 */
@Service
@Transactional(readOnly = true)
public class CustomerProfileQueryService extends QueryService<CustomerProfile> {

    private static final Logger LOG = LoggerFactory.getLogger(CustomerProfileQueryService.class);

    private final CustomerProfileRepository customerProfileRepository;

    private final CustomerProfileMapper customerProfileMapper;

    public CustomerProfileQueryService(CustomerProfileRepository customerProfileRepository, CustomerProfileMapper customerProfileMapper) {
        this.customerProfileRepository = customerProfileRepository;
        this.customerProfileMapper = customerProfileMapper;
    }

    /**
     * Return a {@link Page} of {@link CustomerProfileDTO} which matches the criteria from the database.
     * @param criteria The object which holds all the filters, which the entities should match.
     * @param page The page, which should be returned.
     * @return the matching entities.
     */
    @Transactional(readOnly = true)
    public Page<CustomerProfileDTO> findByCriteria(CustomerProfileCriteria criteria, Pageable page) {
        LOG.debug("find customer profiles by criteria (PII filters redacted), page: {}", page);
        final Specification<CustomerProfile> specification = createSpecification(criteria);
        return customerProfileRepository.findAll(specification, page).map(customerProfileMapper::toDto);
    }

    /**
     * Return the number of matching entities in the database.
     * @param criteria The object which holds all the filters, which the entities should match.
     * @return the number of matching entities.
     */
    @Transactional(readOnly = true)
    public long countByCriteria(CustomerProfileCriteria criteria) {
        LOG.debug("count customer profiles by criteria (PII filters redacted)");
        final Specification<CustomerProfile> specification = createSpecification(criteria);
        return customerProfileRepository.count(specification);
    }

    /**
     * Function to convert {@link CustomerProfileCriteria} to a {@link Specification}
     * @param criteria The object which holds all the filters, which the entities should match.
     * @return the matching {@link Specification} of the entity.
     */
    protected Specification<CustomerProfile> createSpecification(CustomerProfileCriteria criteria) {
        Specification<CustomerProfile> specification = Specification.unrestricted();
        if (criteria != null) {
            // This has to be called first, because the distinct method returns null
            specification = Specification.allOf(
                Boolean.TRUE.equals(criteria.getDistinct()) ? distinct(criteria.getDistinct()) : Specification.unrestricted(),
                buildRangeSpecification(criteria.getId(), CustomerProfile_.id),
                buildStringSpecification(criteria.getUserLogin(), CustomerProfile_.userLogin),
                buildStringSpecification(criteria.getFullName(), CustomerProfile_.fullName),
                buildStringSpecification(criteria.getPhone(), CustomerProfile_.phone)
            );
        }
        return specification;
    }
}
