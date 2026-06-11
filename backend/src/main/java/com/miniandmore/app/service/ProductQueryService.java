package com.miniandmore.app.service;

import com.miniandmore.app.domain.*; // for static metamodels
import com.miniandmore.app.domain.Product;
import com.miniandmore.app.repository.ProductRepository;
import com.miniandmore.app.service.criteria.ProductCriteria;
import com.miniandmore.app.service.dto.ProductDTO;
import com.miniandmore.app.service.mapper.ProductMapper;
import jakarta.persistence.criteria.JoinType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.jhipster.service.QueryService;

/**
 * Service for executing complex queries for {@link Product} entities in the database.
 * The main input is a {@link ProductCriteria} which gets converted to {@link Specification},
 * in a way that all the filters must apply.
 * It returns a {@link Page} of {@link ProductDTO} which fulfills the criteria.
 */
@Service
@Transactional(readOnly = true)
public class ProductQueryService extends QueryService<Product> {

    private static final Logger LOG = LoggerFactory.getLogger(ProductQueryService.class);

    private final ProductRepository productRepository;

    private final ProductMapper productMapper;

    public ProductQueryService(ProductRepository productRepository, ProductMapper productMapper) {
        this.productRepository = productRepository;
        this.productMapper = productMapper;
    }

    /**
     * Return a {@link Page} of {@link ProductDTO} which matches the criteria from the database.
     * @param criteria The object which holds all the filters, which the entities should match.
     * @param page The page, which should be returned.
     * @return the matching entities.
     */
    @Transactional(readOnly = true)
    public Page<ProductDTO> findByCriteria(ProductCriteria criteria, Pageable page) {
        LOG.debug("find by criteria : {}, page: {}", criteria, page);
        final Specification<Product> specification = createSpecification(criteria);
        return productRepository.findAll(specification, page).map(productMapper::toDto);
    }

    /**
     * Return the number of matching entities in the database.
     * @param criteria The object which holds all the filters, which the entities should match.
     * @return the number of matching entities.
     */
    @Transactional(readOnly = true)
    public long countByCriteria(ProductCriteria criteria) {
        LOG.debug("count by criteria : {}", criteria);
        final Specification<Product> specification = createSpecification(criteria);
        return productRepository.count(specification);
    }

    /**
     * Function to convert {@link ProductCriteria} to a {@link Specification}
     * @param criteria The object which holds all the filters, which the entities should match.
     * @return the matching {@link Specification} of the entity.
     */
    protected Specification<Product> createSpecification(ProductCriteria criteria) {
        Specification<Product> specification = Specification.unrestricted();
        if (criteria != null) {
            // This has to be called first, because the distinct method returns null
            specification = Specification.allOf(
                Boolean.TRUE.equals(criteria.getDistinct()) ? distinct(criteria.getDistinct()) : Specification.unrestricted(),
                buildRangeSpecification(criteria.getId(), Product_.id),
                buildStringSpecification(criteria.getSlug(), Product_.slug),
                buildStringSpecification(criteria.getName(), Product_.name),
                buildStringSpecification(criteria.getSku(), Product_.sku),
                buildStringSpecification(criteria.getFabricType(), Product_.fabricType),
                buildRangeSpecification(criteria.getPrice(), Product_.price),
                buildRangeSpecification(criteria.getCompareAtPrice(), Product_.compareAtPrice),
                buildStringSpecification(criteria.getCurrency(), Product_.currency),
                buildRangeSpecification(criteria.getAgeMinMonths(), Product_.ageMinMonths),
                buildRangeSpecification(criteria.getAgeMaxMonths(), Product_.ageMaxMonths),
                buildStringSpecification(criteria.getBadge(), Product_.badge),
                buildRangeSpecification(criteria.getStock(), Product_.stock),
                buildRangeSpecification(criteria.getSortOrder(), Product_.sortOrder),
                buildSpecification(criteria.getFeatured(), Product_.featured),
                buildSpecification(criteria.getTrending(), Product_.trending),
                buildSpecification(criteria.getNewArrival(), Product_.newArrival),
                buildSpecification(criteria.getBestSeller(), Product_.bestSeller),
                buildSpecification(criteria.getActive(), Product_.active),
                buildSpecification(criteria.getCategoryId(), root -> root.join(Product_.category, JoinType.LEFT).get(Category_.id))
            );
        }
        return specification;
    }
}
