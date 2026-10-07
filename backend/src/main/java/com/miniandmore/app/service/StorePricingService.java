package com.miniandmore.app.service;

import com.miniandmore.app.domain.SiteSetting;
import com.miniandmore.app.repository.SiteSettingRepository;
import com.miniandmore.app.service.dto.ProductDTO;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Global store pricing: selling price derived from MRP using {@code site_setting.store_discount_percent}.
 */
@Service
public class StorePricingService {

    private static final BigDecimal DEFAULT_DISCOUNT_PERCENT = new BigDecimal("20");

    private final SiteSettingRepository siteSettingRepository;

    public StorePricingService(SiteSettingRepository siteSettingRepository) {
        this.siteSettingRepository = siteSettingRepository;
    }

    public BigDecimal resolveDiscountPercent() {
        return siteSettingRepository
            .findFirstByOrderByIdAsc()
            .map(SiteSetting::getStoreDiscountPercent)
            .filter(p -> p != null)
            .orElse(DEFAULT_DISCOUNT_PERCENT);
    }

    public BigDecimal sellingMultiplier(BigDecimal discountPercent) {
        BigDecimal discount = discountPercent != null ? discountPercent : DEFAULT_DISCOUNT_PERCENT;
        return BigDecimal.ONE.subtract(discount.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));
    }

    public BigDecimal sellingMultiplier() {
        return sellingMultiplier(resolveDiscountPercent());
    }

    public BigDecimal sellingFromMrp(BigDecimal mrp) {
        if (mrp == null || mrp.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return mrp.multiply(sellingMultiplier()).setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal mrpFromSelling(BigDecimal selling) {
        if (selling == null || selling.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal mult = sellingMultiplier();
        if (mult.compareTo(BigDecimal.ZERO) <= 0) {
            return selling;
        }
        return selling.divide(mult, 2, RoundingMode.HALF_UP);
    }

    public BigDecimal simpleProductSellingPrice(BigDecimal compareAtPrice, BigDecimal storedPrice) {
        if (compareAtPrice != null && compareAtPrice.compareTo(BigDecimal.ZERO) > 0) {
            return sellingFromMrp(compareAtPrice);
        }
        return storedPrice != null ? storedPrice : BigDecimal.ZERO;
    }

    public void applyRuntimePricing(ProductDTO dto) {
        if (dto == null) {
            return;
        }
        if (dto.getCompareAtPrice() != null && dto.getCompareAtPrice().compareTo(BigDecimal.ZERO) > 0) {
            dto.setPrice(sellingFromMrp(dto.getCompareAtPrice()));
        }
    }
}
