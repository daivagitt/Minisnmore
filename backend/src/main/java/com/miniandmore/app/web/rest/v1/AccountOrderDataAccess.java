package com.miniandmore.app.web.rest.v1;

import com.miniandmore.app.repository.*;
import com.miniandmore.app.client.LedgerlyIntegrationClient;
import org.springframework.stereotype.Component;

@Component
public record AccountOrderDataAccess(
    CouponRepository couponRepository,
    CustomerProfileRepository customerProfileRepository,
    CustomerOrderRepository customerOrderRepository,
    OrderItemRepository orderItemRepository,
    ProductRepository productRepository,
    SiteSettingRepository siteSettingRepository,
    LedgerlyIntegrationClient ledgerlyIntegrationClient
) {}
