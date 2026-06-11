package com.miniandmore.app.service;

import com.miniandmore.app.domain.Policy;
import com.miniandmore.app.repository.PolicyRepository;
import com.miniandmore.app.service.dto.PolicyDTO;
import com.miniandmore.app.service.mapper.PolicyMapper;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service Implementation for managing {@link com.miniandmore.app.domain.Policy}.
 */
@Service
@Transactional
public class PolicyService {

    private static final Logger LOG = LoggerFactory.getLogger(PolicyService.class);

    private final PolicyRepository policyRepository;

    private final PolicyMapper policyMapper;

    public PolicyService(PolicyRepository policyRepository, PolicyMapper policyMapper) {
        this.policyRepository = policyRepository;
        this.policyMapper = policyMapper;
    }

    /**
     * Save a policy.
     *
     * @param policyDTO the entity to save.
     * @return the persisted entity.
     */
    public PolicyDTO save(PolicyDTO policyDTO) {
        LOG.debug("Request to save Policy : {}", policyDTO);
        Policy policy = policyMapper.toEntity(policyDTO);
        policy = policyRepository.save(policy);
        return policyMapper.toDto(policy);
    }

    /**
     * Update a policy.
     *
     * @param policyDTO the entity to save.
     * @return the persisted entity.
     */
    public PolicyDTO update(PolicyDTO policyDTO) {
        LOG.debug("Request to update Policy : {}", policyDTO);
        Policy policy = policyMapper.toEntity(policyDTO);
        policy = policyRepository.save(policy);
        return policyMapper.toDto(policy);
    }

    /**
     * Partially update a policy.
     *
     * @param policyDTO the entity to update partially.
     * @return the persisted entity.
     */
    public Optional<PolicyDTO> partialUpdate(PolicyDTO policyDTO) {
        LOG.debug("Request to partially update Policy : {}", policyDTO);

        return policyRepository
            .findById(policyDTO.getId())
            .map(existingPolicy -> {
                policyMapper.partialUpdate(existingPolicy, policyDTO);

                return existingPolicy;
            })
            .map(policyRepository::save)
            .map(policyMapper::toDto);
    }

    /**
     * Get one policy by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    @Transactional(readOnly = true)
    public Optional<PolicyDTO> findOne(Long id) {
        LOG.debug("Request to get Policy : {}", id);
        return policyRepository.findById(id).map(policyMapper::toDto);
    }

    /**
     * Delete the policy by id.
     *
     * @param id the id of the entity.
     */
    public void delete(Long id) {
        LOG.debug("Request to delete Policy : {}", id);
        policyRepository.deleteById(id);
    }
}
