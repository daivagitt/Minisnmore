package com.miniandmore.app.service;

import com.miniandmore.app.domain.*; // for static metamodels
import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.repository.CustomerOrderRepository;
import com.miniandmore.app.service.criteria.CustomerOrderCriteria;
import com.miniandmore.app.service.dto.CustomerOrderDTO;
import com.miniandmore.app.service.mapper.CustomerOrderMapper;
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
 * Service for executing complex queries for {@link CustomerOrder} entities in the database.
 * The main input is a {@link CustomerOrderCriteria} which gets converted to {@link Specification},
 * in a way that all the filters must apply.
 * It returns a {@link Page} of {@link CustomerOrderDTO} which fulfills the criteria.
 */
@Service
@Transactional(readOnly = true)
public class CustomerOrderQueryService extends QueryService<CustomerOrder> {

    private static final Logger LOG = LoggerFactory.getLogger(CustomerOrderQueryService.class);

    private final CustomerOrderRepository customerOrderRepository;

    private final CustomerOrderMapper customerOrderMapper;

    public CustomerOrderQueryService(CustomerOrderRepository customerOrderRepository, CustomerOrderMapper customerOrderMapper) {
        this.customerOrderRepository = customerOrderRepository;
        this.customerOrderMapper = customerOrderMapper;
    }

    /**
     * Return a {@link Page} of {@link CustomerOrderDTO} which matches the criteria from the database.
     * @param criteria The object which holds all the filters, which the entities should match.
     * @param page The page, which should be returned.
     * @return the matching entities.
     */
    @Transactional(readOnly = true)
    public Page<CustomerOrderDTO> findByCriteria(CustomerOrderCriteria criteria, Pageable page) {
        LOG.debug("find customer orders by criteria (payment filters redacted), page: {}", page);
        final Specification<CustomerOrder> specification = createSpecification(criteria);
        return customerOrderRepository.findAll(specification, page).map(customerOrderMapper::toDto);
    }

    /**
     * Return the number of matching entities in the database.
     * @param criteria The object which holds all the filters, which the entities should match.
     * @return the number of matching entities.
     */
    @Transactional(readOnly = true)
    public long countByCriteria(CustomerOrderCriteria criteria) {
        LOG.debug("count customer orders by criteria (payment filters redacted)");
        final Specification<CustomerOrder> specification = createSpecification(criteria);
        return customerOrderRepository.count(specification);
    }

    /**
     * Function to convert {@link CustomerOrderCriteria} to a {@link Specification}
     * @param criteria The object which holds all the filters, which the entities should match.
     * @return the matching {@link Specification} of the entity.
     */
    protected Specification<CustomerOrder> createSpecification(CustomerOrderCriteria criteria) {
        Specification<CustomerOrder> specification = Specification.unrestricted();
        if (criteria != null) {
            // This has to be called first, because the distinct method returns null
            specification = Specification.allOf(
                Boolean.TRUE.equals(criteria.getDistinct()) ? distinct(criteria.getDistinct()) : Specification.unrestricted(),
                buildRangeSpecification(criteria.getId(), CustomerOrder_.id),
                buildStringSpecification(criteria.getUserLogin(), CustomerOrder_.userLogin),
                buildStringSpecification(criteria.getOrderNumber(), CustomerOrder_.orderNumber),
                buildSpecification(criteria.getStatus(), CustomerOrder_.status),
                buildSpecification(criteria.getPaymentStatus(), CustomerOrder_.paymentStatus),
                buildStringSpecification(criteria.getRazorpayOrderId(), CustomerOrder_.razorpayOrderId),
                buildStringSpecification(criteria.getRazorpayPaymentId(), CustomerOrder_.razorpayPaymentId),
                buildStringSpecification(criteria.getRazorpaySignature(), CustomerOrder_.razorpaySignature),
                buildRangeSpecification(criteria.getSubtotal(), CustomerOrder_.subtotal),
                buildRangeSpecification(criteria.getDiscount(), CustomerOrder_.discount),
                buildRangeSpecification(criteria.getShipping(), CustomerOrder_.shipping),
                buildRangeSpecification(criteria.getTax(), CustomerOrder_.tax),
                buildRangeSpecification(criteria.getDonation(), CustomerOrder_.donation),
                buildRangeSpecification(criteria.getTotal(), CustomerOrder_.total),
                buildStringSpecification(criteria.getCouponCode(), CustomerOrder_.couponCode),
                buildStringSpecification(criteria.getNotes(), CustomerOrder_.notes),
                buildSpecification(criteria.getItemsId(), root -> root.join(CustomerOrder_.itemses, JoinType.LEFT).get(OrderItem_.id))
            );
        }
        return specification;
    }
}
