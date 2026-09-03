package com.miniandmore.app.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.repository.SiteSettingRepository;
import java.util.Optional;
import org.mockito.Mockito;

/** Test helpers for {@link StorePricingService} and {@link ProductVariantSupport}. */
public final class StorePricingTestSupport {

    private StorePricingTestSupport() {}

    public static StorePricingService defaultPricing() {
        SiteSettingRepository repo = Mockito.mock(SiteSettingRepository.class);
        // This shared helper is also used by tests that do not exercise pricing.
        // Keep the default available when pricing is reached without making those
        // unrelated tests fail Mockito's strict unused-stubbing validation.
        Mockito.lenient().when(repo.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());
        return new StorePricingService(repo);
    }

    public static ProductVariantSupport variantSupport(ObjectMapper objectMapper) {
        return new ProductVariantSupport(objectMapper, defaultPricing());
    }
}
