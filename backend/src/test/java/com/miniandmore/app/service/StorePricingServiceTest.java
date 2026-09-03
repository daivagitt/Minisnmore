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
}
