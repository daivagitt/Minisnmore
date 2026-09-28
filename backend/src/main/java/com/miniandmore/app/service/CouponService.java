package com.miniandmore.app.service;

import com.miniandmore.app.domain.Coupon;
import com.miniandmore.app.domain.enumeration.CouponEligibilityScope;
import com.miniandmore.app.domain.enumeration.DiscountType;
import com.miniandmore.app.repository.CouponRepository;
import com.miniandmore.app.service.dto.CouponDTO;
import com.miniandmore.app.service.mapper.CouponMapper;
import java.math.BigDecimal;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service Implementation for managing {@link com.miniandmore.app.domain.Coupon}.
 */
@Service
@Transactional
public class CouponService {

    private static final Logger LOG = LoggerFactory.getLogger(CouponService.class);

    private final CouponRepository couponRepository;

    private final CouponMapper couponMapper;

    private final AdminConfigChangeMailService adminConfigChangeMailService;

    public CouponService(
        CouponRepository couponRepository,
        CouponMapper couponMapper,
        AdminConfigChangeMailService adminConfigChangeMailService
    ) {
        this.couponRepository = couponRepository;
        this.couponMapper = couponMapper;
        this.adminConfigChangeMailService = adminConfigChangeMailService;
    }

    /**
     * Save a coupon.
     *
     * @param couponDTO the entity to save.
     * @return the persisted entity.
     */
    public CouponDTO save(CouponDTO couponDTO) {
        LOG.debug("Request to save Coupon : {}", couponDTO);
        CouponDTO previous = snapshotExisting(couponDTO.getId());
        normalizeCoupon(couponDTO);
        Coupon coupon = couponMapper.toEntity(couponDTO);
        coupon = couponRepository.save(coupon);
        CouponDTO saved = couponMapper.toDto(coupon);
        notifyConfigChanged(previous, saved);
        return saved;
    }

    /**
     * Update a coupon.
     *
     * @param couponDTO the entity to save.
     * @return the persisted entity.
     */
    public CouponDTO update(CouponDTO couponDTO) {
        LOG.debug("Request to update Coupon : {}", couponDTO);
        CouponDTO previous = snapshotExisting(couponDTO.getId());
        normalizeCoupon(couponDTO);
        Coupon coupon = couponMapper.toEntity(couponDTO);
        coupon = couponRepository.save(coupon);
        CouponDTO saved = couponMapper.toDto(coupon);
        notifyConfigChanged(previous, saved);
        return saved;
    }

    /**
     * Partially update a coupon.
     *
     * @param couponDTO the entity to update partially.
     * @return the persisted entity.
     */
    public Optional<CouponDTO> partialUpdate(CouponDTO couponDTO) {
        LOG.debug("Request to partially update Coupon : {}", couponDTO);

        CouponDTO previous = snapshotExisting(couponDTO.getId());
        return couponRepository
            .findById(couponDTO.getId())
            .map(existingCoupon -> {
                normalizeCoupon(couponDTO);
                couponMapper.partialUpdate(existingCoupon, couponDTO);

                return existingCoupon;
            })
            .map(couponRepository::save)
            .map(couponMapper::toDto)
            .map(saved -> {
                notifyConfigChanged(previous, saved);
                return saved;
            });
    }

    /**
     * Get one coupon by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    @Transactional(readOnly = true)
    public Optional<CouponDTO> findOne(Long id) {
        LOG.debug("Request to get Coupon : {}", id);
        return couponRepository.findById(id).map(couponMapper::toDto);
    }

    /**
     * Delete the coupon by id.
     *
     * @param id the id of the entity.
     */
    public void delete(Long id) {
        LOG.debug("Request to delete Coupon : {}", id);
        CouponDTO previous = snapshotExisting(id);
        couponRepository.deleteById(id);
        notifyConfigChanged(previous, null);
    }

    private CouponDTO snapshotExisting(Long id) {
        if (id == null) {
            return null;
        }
        return couponRepository.findById(id).map(couponMapper::toDto).orElse(null);
    }

    private void notifyConfigChanged(CouponDTO previous, CouponDTO current) {
        adminConfigChangeMailService.notifyCouponChangedAfterCommit(previous, current);
    }

    static void normalizeCoupon(CouponDTO couponDTO) {
        couponDTO.setBogoBuyQty(BogoDiscountCalculator.clampQty(couponDTO.getBogoBuyQty()));
        couponDTO.setBogoGetQty(BogoDiscountCalculator.clampQty(couponDTO.getBogoGetQty()));
        couponDTO.setEligibilityScope(BogoDiscountCalculator.resolveScope(couponDTO.getEligibilityScope()));
        if (couponDTO.getEligibilityScope() != CouponEligibilityScope.CATEGORY) {
            couponDTO.setEligibilityCategoryId(null);
        } else if (couponDTO.getEligibilityCategoryId() == null) {
            throw new IllegalArgumentException("eligibilityCategoryId is required when eligibilityScope is CATEGORY");
        }
        if (couponDTO.getDiscountType() == DiscountType.BOGO && couponDTO.getDiscountValue() != null) {
            BigDecimal percent = couponDTO.getDiscountValue();
            if (percent.compareTo(BigDecimal.ONE) < 0) {
                couponDTO.setDiscountValue(BigDecimal.ONE);
            } else if (percent.compareTo(BigDecimal.valueOf(100)) > 0) {
                couponDTO.setDiscountValue(BigDecimal.valueOf(100));
            }
        }
    }
}
