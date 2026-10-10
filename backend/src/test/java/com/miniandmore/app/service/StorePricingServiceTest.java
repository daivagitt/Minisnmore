package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.miniandmore.app.domain.SiteSetting;
import com.miniandmore.app.repository.SiteSettingRepository;
import com.miniandmore.app.service.dto.ProductDTO;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StorePricingServiceTest {

    @Mock
    private SiteSettingRepository siteSettingRepository;

    private StorePricingService pricing;

    @BeforeEach
    void setUp() {
        pricing = new StorePricingService(siteSettingRepository);
    }

    @Test
    void resolveDiscountPercent_defaultsWhenMissing() {
        when(siteSettingRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());
        assertThat(pricing.resolveDiscountPercent()).isEqualByComparingTo("20");
    }

    @Test
    void resolveDiscountPercent_readsSiteSetting() {
        when(siteSettingRepository.findFirstByOrderByIdAsc())
            .thenReturn(Optional.of(new SiteSetting().storeDiscountPercent(new BigDecimal("15"))));
        assertThat(pricing.resolveDiscountPercent()).isEqualByComparingTo("15");
    }

    @Test
    void sellingFromMrp_appliesConfiguredDiscount() {
        when(siteSettingRepository.findFirstByOrderByIdAsc())
            .thenReturn(Optional.of(new SiteSetting().storeDiscountPercent(new BigDecimal("25"))));
        assertThat(pricing.sellingFromMrp(new BigDecimal("200"))).isEqualByComparingTo("150.00");
    }

    @Test
    void simpleProductSellingPrice_prefersMrpOverStoredPrice() {
        when(siteSettingRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());
        assertThat(pricing.simpleProductSellingPrice(new BigDecimal("500"), new BigDecimal("300"))).isEqualByComparingTo("400.00");
    }

    @Test
    void applyRuntimePricing_setsPriceFromCompareAt() {
        when(siteSettingRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());
        ProductDTO dto = new ProductDTO();
        dto.setCompareAtPrice(new BigDecimal("250"));
        dto.setPrice(new BigDecimal("999"));
        pricing.applyRuntimePricing(dto);
        assertThat(dto.getPrice()).isEqualByComparingTo("200.00");
    }

    @Test
    void applyRuntimePricing_handlesNullOrZero() {
        pricing.applyRuntimePricing(null);

        ProductDTO dto1 = new ProductDTO();
        dto1.setCompareAtPrice(null);
        dto1.setPrice(new BigDecimal("100"));
        pricing.applyRuntimePricing(dto1);
        assertThat(dto1.getPrice()).isEqualByComparingTo("100");

        ProductDTO dto2 = new ProductDTO();
        dto2.setCompareAtPrice(BigDecimal.ZERO);
        dto2.setPrice(new BigDecimal("100"));
        pricing.applyRuntimePricing(dto2);
        assertThat(dto2.getPrice()).isEqualByComparingTo("100");
    }

    @Test
    void sellingFromMrp_nullOrZero_returnsZero() {
        assertThat(pricing.sellingFromMrp(null)).isEqualTo(BigDecimal.ZERO);
        assertThat(pricing.sellingFromMrp(BigDecimal.ZERO)).isEqualTo(BigDecimal.ZERO);
        assertThat(pricing.sellingFromMrp(new BigDecimal("-10"))).isEqualTo(BigDecimal.ZERO);
    }

    @Test
    void mrpFromSelling_computesCorrectly() {
        when(siteSettingRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());
        // Default discount is 20%, mult = 0.8000. Selling 160 -> MRP 200.00
        assertThat(pricing.mrpFromSelling(new BigDecimal("160"))).isEqualByComparingTo("200.00");
    }

    @Test
    void mrpFromSelling_nullOrZero_returnsZero() {
        assertThat(pricing.mrpFromSelling(null)).isEqualTo(BigDecimal.ZERO);
        assertThat(pricing.mrpFromSelling(BigDecimal.ZERO)).isEqualTo(BigDecimal.ZERO);
        assertThat(pricing.mrpFromSelling(new BigDecimal("-50"))).isEqualTo(BigDecimal.ZERO);
    }

    @Test
    void mrpFromSelling_zeroOrNegativeMultiplier_returnsSelling() {
        // If discount is 100% or more, mult <= 0
        when(siteSettingRepository.findFirstByOrderByIdAsc())
            .thenReturn(Optional.of(new SiteSetting().storeDiscountPercent(new BigDecimal("100"))));
        assertThat(pricing.mrpFromSelling(new BigDecimal("150"))).isEqualByComparingTo("150");
    }

    @Test
    void simpleProductSellingPrice_fallbackToStoredPrice() {
        assertThat(pricing.simpleProductSellingPrice(null, new BigDecimal("120"))).isEqualByComparingTo("120");
        assertThat(pricing.simpleProductSellingPrice(BigDecimal.ZERO, new BigDecimal("120"))).isEqualByComparingTo("120");
        assertThat(pricing.simpleProductSellingPrice(null, null)).isEqualTo(BigDecimal.ZERO);
    }
}
