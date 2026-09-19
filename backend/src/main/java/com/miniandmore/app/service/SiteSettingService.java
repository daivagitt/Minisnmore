package com.miniandmore.app.service;

import com.miniandmore.app.domain.SiteSetting;
import com.miniandmore.app.repository.SiteSettingRepository;
import com.miniandmore.app.service.dto.SiteSettingDTO;
import com.miniandmore.app.service.mapper.SiteSettingMapper;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Service Implementation for managing {@link com.miniandmore.app.domain.SiteSetting}.
 */
@Service
@Transactional
public class SiteSettingService {

    private static final Logger LOG = LoggerFactory.getLogger(SiteSettingService.class);
    private static final int DEFAULT_COD_FEE_INR = 99;

    private final SiteSettingRepository siteSettingRepository;

    private final SiteSettingMapper siteSettingMapper;

    private final EntityManager entityManager;

    private final AdminConfigChangeMailService adminConfigChangeMailService;

    public SiteSettingService(
        SiteSettingRepository siteSettingRepository,
        SiteSettingMapper siteSettingMapper,
        EntityManager entityManager,
        AdminConfigChangeMailService adminConfigChangeMailService
    ) {
        this.siteSettingRepository = siteSettingRepository;
        this.siteSettingMapper = siteSettingMapper;
        this.entityManager = entityManager;
        this.adminConfigChangeMailService = adminConfigChangeMailService;
    }

    /**
     * Save a siteSetting.
     *
     * @param siteSettingDTO the entity to save.
     * @return the persisted entity.
     */
    public SiteSettingDTO save(SiteSettingDTO siteSettingDTO) {
        LOG.debug("Request to save SiteSetting : {}", siteSettingDTO);
        SiteSettingDTO previous = snapshotExisting(siteSettingDTO.getId());
        defaultStoreDiscountPercent(siteSettingDTO);
        defaultCodFee(siteSettingDTO);
        defaultDeliveryEtaBufferDays(siteSettingDTO);
        SiteSetting siteSetting = siteSettingMapper.toEntity(siteSettingDTO);
        siteSetting = siteSettingRepository.save(siteSetting);
        SiteSettingDTO saved = siteSettingMapper.toDto(siteSetting);
        notifyConfigChanged(previous, saved);
        return saved;
    }

    /**
     * Update a siteSetting.
     *
     * @param siteSettingDTO the entity to save.
     * @return the persisted entity.
     */
    public SiteSettingDTO update(SiteSettingDTO siteSettingDTO) {
        LOG.debug("Request to update SiteSetting : {}", siteSettingDTO);
        SiteSettingDTO previous = snapshotExisting(siteSettingDTO.getId());
        defaultStoreDiscountPercent(siteSettingDTO);
        defaultCodFee(siteSettingDTO);
        defaultDeliveryEtaBufferDays(siteSettingDTO);
        SiteSetting siteSetting = siteSettingMapper.toEntity(siteSettingDTO);
        siteSetting = siteSettingRepository.save(siteSetting);
        SiteSettingDTO saved = siteSettingMapper.toDto(siteSetting);
        notifyConfigChanged(previous, saved);
        return saved;
    }

    /**
     * Partially update a siteSetting.
     *
     * @param siteSettingDTO the entity to update partially.
     * @return the persisted entity.
     */
    public Optional<SiteSettingDTO> partialUpdate(SiteSettingDTO siteSettingDTO) {
        LOG.debug("Request to partially update SiteSetting : {}", siteSettingDTO);

        SiteSettingDTO previous = snapshotExisting(siteSettingDTO.getId());
        return siteSettingRepository
            .findById(siteSettingDTO.getId())
            .map(existingSiteSetting -> {
                siteSettingMapper.partialUpdate(existingSiteSetting, siteSettingDTO);

                return existingSiteSetting;
            })
            .map(siteSettingRepository::save)
            .map(siteSettingMapper::toDto)
            .map(saved -> {
                notifyConfigChanged(previous, saved);
                return saved;
            });
    }

    /**
     * Get one siteSetting by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    @Transactional(readOnly = true)
    public Optional<SiteSettingDTO> findOne(Long id) {
        LOG.debug("Request to get SiteSetting : {}", id);
        return siteSettingRepository.findById(id).map(siteSettingMapper::toDto);
    }

    /**
     * Delete the siteSetting by id.
     *
     * @param id the id of the entity.
     */
    public void delete(Long id) {
        LOG.debug("Request to delete SiteSetting : {}", id);
        siteSettingRepository.deleteById(id);
    }

    private SiteSettingDTO snapshotExisting(Long id) {
        Optional<SiteSetting> existing = id != null
            ? siteSettingRepository.findById(id)
            : siteSettingRepository.findFirstByOrderByIdAsc();
        return existing
            .map(entity -> {
                SiteSettingDTO dto = siteSettingMapper.toDto(entity);
                entityManager.detach(entity);
                return dto;
            })
            .orElse(null);
    }

    private void notifyConfigChanged(SiteSettingDTO previous, SiteSettingDTO current) {
        Runnable send = () -> {
            try {
                adminConfigChangeMailService.notifySiteSettingsChanged(previous, current);
            } catch (RuntimeException ex) {
                LOG.warn("Admin configuration email failed", ex);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        send.run();
                    }
                }
            );
            return;
        }
        send.run();
    }

    private void defaultStoreDiscountPercent(SiteSettingDTO dto) {
        if (dto.getStoreDiscountPercent() != null) {
            return;
        }
        siteSettingRepository
            .findFirstByOrderByIdAsc()
            .map(SiteSetting::getStoreDiscountPercent)
            .filter(p -> p != null)
            .ifPresent(dto::setStoreDiscountPercent);
        if (dto.getStoreDiscountPercent() == null) {
            dto.setStoreDiscountPercent(new BigDecimal("20"));
        }
    }

    private void defaultCodFee(SiteSettingDTO dto) {
        if (dto.getCodFeeInr() != null) {
            return;
        }
        siteSettingRepository
            .findFirstByOrderByIdAsc()
            .map(SiteSetting::getCodFeeInr)
            .filter(fee -> fee != null)
            .ifPresent(dto::setCodFeeInr);
        if (dto.getCodFeeInr() == null) {
            dto.setCodFeeInr(DEFAULT_COD_FEE_INR);
        }
    }

    private void defaultDeliveryEtaBufferDays(SiteSettingDTO dto) {
        if (dto.getDeliveryEtaBufferDays() != null) {
            return;
        }
        siteSettingRepository
            .findFirstByOrderByIdAsc()
            .map(SiteSetting::getDeliveryEtaBufferDays)
            .filter(days -> days != null)
            .ifPresent(dto::setDeliveryEtaBufferDays);
        if (dto.getDeliveryEtaBufferDays() == null) {
            dto.setDeliveryEtaBufferDays(SiteSetting.DEFAULT_DELIVERY_ETA_BUFFER_DAYS);
        }
    }
}
