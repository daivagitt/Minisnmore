package com.miniandmore.app.service;

import com.miniandmore.app.domain.CustomerProfile;
import com.miniandmore.app.repository.CustomerProfileRepository;
import com.miniandmore.app.service.dto.CustomerProfileDTO;
import com.miniandmore.app.service.mapper.CustomerProfileMapper;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service Implementation for managing {@link com.miniandmore.app.domain.CustomerProfile}.
 */
@Service
@Transactional
public class CustomerProfileService {

    private static final Logger LOG = LoggerFactory.getLogger(CustomerProfileService.class);

    private final CustomerProfileRepository customerProfileRepository;

    private final CustomerProfileMapper customerProfileMapper;

    public CustomerProfileService(CustomerProfileRepository customerProfileRepository, CustomerProfileMapper customerProfileMapper) {
        this.customerProfileRepository = customerProfileRepository;
        this.customerProfileMapper = customerProfileMapper;
    }

    /**
     * Save a customerProfile.
     *
     * @param customerProfileDTO the entity to save.
     * @return the persisted entity.
     */
    public CustomerProfileDTO save(CustomerProfileDTO customerProfileDTO) {
        LOG.debug("Request to save CustomerProfile : {}", customerProfileDTO);
        CustomerProfile customerProfile = customerProfileMapper.toEntity(customerProfileDTO);
        customerProfile = customerProfileRepository.save(customerProfile);
        return customerProfileMapper.toDto(customerProfile);
    }

    /**
     * Update a customerProfile.
     *
     * @param customerProfileDTO the entity to save.
     * @return the persisted entity.
     */
    public CustomerProfileDTO update(CustomerProfileDTO customerProfileDTO) {
        LOG.debug("Request to update CustomerProfile : {}", customerProfileDTO);
        CustomerProfile customerProfile = customerProfileMapper.toEntity(customerProfileDTO);
        customerProfile = customerProfileRepository.save(customerProfile);
        return customerProfileMapper.toDto(customerProfile);
    }

    /**
     * Partially update a customerProfile.
     *
     * @param customerProfileDTO the entity to update partially.
     * @return the persisted entity.
     */
    public Optional<CustomerProfileDTO> partialUpdate(CustomerProfileDTO customerProfileDTO) {
        LOG.debug("Request to partially update CustomerProfile : {}", customerProfileDTO);

        return customerProfileRepository
            .findById(customerProfileDTO.getId())
            .map(existingCustomerProfile -> {
                customerProfileMapper.partialUpdate(existingCustomerProfile, customerProfileDTO);

                return existingCustomerProfile;
            })
            .map(customerProfileRepository::save)
            .map(customerProfileMapper::toDto);
    }

    /**
     * Get one customerProfile by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    @Transactional(readOnly = true)
    public Optional<CustomerProfileDTO> findOne(Long id) {
        LOG.debug("Request to get CustomerProfile : {}", id);
        return customerProfileRepository.findById(id).map(customerProfileMapper::toDto);
    }

    /**
     * Delete the customerProfile by id.
     *
     * @param id the id of the entity.
     */
    public void delete(Long id) {
        LOG.debug("Request to delete CustomerProfile : {}", id);
        customerProfileRepository.deleteById(id);
    }
}
