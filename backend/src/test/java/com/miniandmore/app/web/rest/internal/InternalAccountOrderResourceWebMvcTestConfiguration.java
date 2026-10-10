package com.miniandmore.app.web.rest.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.client.LedgerlyIntegrationClient;
import com.miniandmore.app.repository.CouponRepository;
import com.miniandmore.app.repository.CustomerOrderRepository;
import com.miniandmore.app.repository.CustomerProfileRepository;
import com.miniandmore.app.repository.OrderItemRepository;
import com.miniandmore.app.repository.ProductRepository;
import com.miniandmore.app.repository.SiteSettingRepository;
import com.miniandmore.app.service.StorePricingTestSupport;
import com.miniandmore.app.web.rest.v1.AccountOrderDataAccess;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.PlatformTransactionManager;

/** Shared beans for {@link InternalAccountOrderResourceCoverageTest} WebMvc slice. */
@TestConfiguration
public class InternalAccountOrderResourceWebMvcTestConfiguration {

    @Bean
    ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Bean
    com.miniandmore.app.service.StorePricingService storePricingService() {
        return StorePricingTestSupport.defaultPricing();
    }

    @Bean
    com.miniandmore.app.service.ProductVariantSupport productVariantSupport(ObjectMapper objectMapper) {
        return StorePricingTestSupport.variantSupport(objectMapper);
    }

    @Bean
    PlatformTransactionManager transactionManager() {
        return org.mockito.Mockito.mock(PlatformTransactionManager.class);
    }

    @Bean
    AccountOrderDataAccess accountOrderDataAccess(
        CouponRepository couponRepository,
        CustomerProfileRepository customerProfileRepository,
        CustomerOrderRepository customerOrderRepository,
        OrderItemRepository orderItemRepository,
        ProductRepository productRepository,
        SiteSettingRepository siteSettingRepository,
        LedgerlyIntegrationClient ledgerlyIntegrationClient
    ) {
        return new AccountOrderDataAccess(
            couponRepository,
            customerProfileRepository,
            customerOrderRepository,
            orderItemRepository,
            productRepository,
            siteSettingRepository,
            ledgerlyIntegrationClient
        );
    }
}
