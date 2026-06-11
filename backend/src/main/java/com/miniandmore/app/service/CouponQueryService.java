package com.miniandmore.app.service;

import com.miniandmore.app.domain.*; // for static metamodels
import com.miniandmore.app.domain.Coupon;
import com.miniandmore.app.repository.CouponRepository;
import com.miniandmore.app.service.criteria.CouponCriteria;
import com.miniandmore.app.service.dto.CouponDTO;
import com.miniandmore.app.service.mapper.CouponMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.jhipster.service.QueryService;

/**
 * Service for executing complex queries for {@link Coupon} entities in the database.
 * The main input is a {@link CouponCriteria} which gets converted to {@link Specification},
 * in a way that all the filters must apply.
 * It returns a {@link Page} of {@link CouponDTO} which fulfills the criteria.
 */
@Service
@Transactional(readOnly = true)
public class CouponQueryService extends QueryService<Coupon> {

    private static final Logger LOG = LoggerFactory.getLogger(CouponQueryService.class);

    private final CouponRepository couponRepository;

    private final CouponMapper couponMapper;

    public CouponQueryService(CouponRepository couponRepository, CouponMapper couponMapper) {
        this.couponRepository = couponRepository;
        this.couponMapper = couponMapper;
    }

    /**
     * Return a {@link Page} of {@link CouponDTO} which matches the criteria from the database.
     * @param criteria The object which holds all the filters, which the entities should match.
     * @param page The page, which should be returned.
     * @return the matching entities.
     */
    @Transactional(readOnly = true)
    public Page<CouponDTO> findByCriteria(CouponCriteria criteria, Pageable page) {
        LOG.debug("find by criteria : {}, page: {}", criteria, page);
        final Specification<Coupon> specification = createSpecification(criteria);
        return couponRepository.findAll(specification, page).map(couponMapper::toDto);
    }

    /**
     * Return the number of matching entities in the database.
     * @param criteria The object which holds all the filters, which the entities should match.
     * @return the number of matching entities.
     */
    @Transactional(readOnly = true)
    public long countByCriteria(CouponCriteria criteria) {
        LOG.debug("count by criteria : {}", criteria);
        final Specification<Coupon> specification = createSpecification(criteria);
        return couponRepository.count(specification);
    }

    /**
     * Function to convert {@link CouponCriteria} to a {@link Specification}
     * @param criteria The object which holds all the filters, which the entities should match.
     * @return the matching {@link Specification} of the entity.
     */
    protected Specification<Coupon> createSpecification(CouponCriteria criteria) {
        Specification<Coupon> specification = Specification.unrestricted();
        if (criteria != null) {
            // This has to be called first, because the distinct method returns null
            specification = Specification.allOf(
                Boolean.TRUE.equals(criteria.getDistinct()) ? distinct(criteria.getDistinct()) : Specification.unrestricted(),
                buildRangeSpecification(criteria.getId(), Coupon_.id),
                buildStringSpecification(criteria.getCode(), Coupon_.code),
                buildSpecification(criteria.getDiscountType(), Coupon_.discountType),
                buildRangeSpecification(criteria.getDiscountValue(), Coupon_.discountValue),
                buildRangeSpecification(criteria.getMinOrder(), Coupon_.minOrder),
                buildRangeSpecification(criteria.getMaxDiscount(), Coupon_.maxDiscount),
                buildRangeSpecification(criteria.getExpiresAt(), Coupon_.expiresAt),
                buildSpecification(criteria.getActive(), Coupon_.active)
            );
        }
        return specification;
    }
}
