package com.miniandmore.app.web.rest.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.client.LedgerlyIntegrationClient;
import com.miniandmore.app.config.ApplicationProperties;
import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.OrderItem;
import com.miniandmore.app.domain.Product;
import com.miniandmore.app.domain.enumeration.OrderStatus;
import com.miniandmore.app.domain.enumeration.ReturnLogisticsMode;
import com.miniandmore.app.repository.CouponRepository;
import com.miniandmore.app.repository.CustomerOrderRepository;
import com.miniandmore.app.repository.CustomerProfileRepository;
import com.miniandmore.app.repository.OrderItemRepository;
import com.miniandmore.app.repository.ProductRepository;
import com.miniandmore.app.repository.SiteSettingRepository;
import com.miniandmore.app.service.LedgerlySyncService;
import com.miniandmore.app.service.OrderCancellationService;
import com.miniandmore.app.service.OrderEventService;
import com.miniandmore.app.service.OrderShipmentService;
import com.miniandmore.app.service.RazorpayRefundService;
import com.miniandmore.app.service.StorePricingTestSupport;
import com.miniandmore.app.service.ZohoSyncService;
import com.miniandmore.app.web.rest.v1.AccountOrderDataAccess;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InternalAccountOrderResourceRankedBranchMatrixTest {

    @Mock private CouponRepository coupons;
    @Mock private CustomerProfileRepository profiles;
    @Mock private CustomerOrderRepository orders;
    @Mock private OrderItemRepository items;
    @Mock private ProductRepository products;
    @Mock private SiteSettingRepository settings;
    @Mock private LedgerlyIntegrationClient ledgerlyClient;
    @Mock private LedgerlySyncService ledgerly;
    @Mock private ZohoSyncService zoho;
    @Mock private OrderEventService events;
    @Mock private RazorpayRefundService refunds;
    @Mock private OrderCancellationService cancellations;
    @Mock private OrderShipmentService shipments;
    @Mock private PlatformTransactionManager transactions;

    private ApplicationProperties properties;
    private InternalAccountOrderResource resource;

    @BeforeEach
    void setUp() {
        properties = new ApplicationProperties();
        AccountOrderDataAccess data = new AccountOrderDataAccess(
            coupons,
            profiles,
            orders,
            items,
            products,
            settings,
            ledgerlyClient
        );
        resource = new InternalAccountOrderResource(
            data,
            new ObjectMapper(),
            properties,
            ledgerly,
            zoho,
            StorePricingTestSupport.variantSupport(new ObjectMapper()),
            StorePricingTestSupport.defaultPricing(),
            events,
            refunds,
            cancellations,
            shipments,
            transactions
        );
    }

    @Test
    void refreshTrackingIfStale_cancelledOrderShortCircuits() {
        CustomerOrder order = new CustomerOrder()
            .id(1L)
            .status(OrderStatus.CANCELLED)
            .ledgerlySalesOrderId("SO")
            .zohoSalesOrderId("ZOHO")
            .shipmentLogisticsMode(ReturnLogisticsMode.XPRESSBEES);

        assertThat(refresh(order)).isSameAs(order);
        verify(ledgerly, never()).pullOrderTracking(any());
        verify(zoho, never()).pullOrderTracking(any());
        verify(shipments, never()).refreshExpressBeesTracking(any());
    }

    @Test
    void refreshTrackingIfStale_skipsFreshExpressBeesAndBlankZohoLinks() {
        properties.getZoho().getOrderTracking().setEnabled(true);
        CustomerOrder order = new CustomerOrder()
            .id(2L)
            .status(OrderStatus.CONFIRMED)
            .shipmentLogisticsMode(ReturnLogisticsMode.XPRESSBEES);
        when(shipments.isExpressBeesTrackingFresh(order)).thenReturn(true);

        assertThat(refresh(order)).isSameAs(order);
        verify(zoho, never()).pullOrderTracking(any());
        verify(shipments, never()).refreshExpressBeesTracking(any());
    }

    @Test
    void refreshTrackingIfStale_sameExpressBeesInstanceDoesNotReloadRepository() {
        CustomerOrder order = new CustomerOrder()
            .id(3L)
            .status(OrderStatus.CONFIRMED)
            .shipmentLogisticsMode(ReturnLogisticsMode.XPRESSBEES)
            .trackingNumber("AWB");
        when(shipments.isExpressBeesTrackingFresh(order)).thenReturn(false);
        when(shipments.refreshExpressBeesTracking(order)).thenReturn(order);

        assertThat(refresh(order)).isSameAs(order);
        verify(orders, never()).findById(any());
    }

    @Test
    void refreshTrackingIfStale_recentZohoLinkDoesNotPull() {
        properties.getZoho().getOrderTracking().setEnabled(true);
        CustomerOrder order = new CustomerOrder()
            .id(4L)
            .status(OrderStatus.CONFIRMED)
            .zohoInvoiceId("INV")
            .zohoTrackingLastPulledAt(Instant.now());

        assertThat(refresh(order)).isSameAs(order);
        verify(zoho, never()).pullOrderTracking(any());
    }

    @Test
    void invoiceItemMrp_fallsBackAcrossNullProductBlankSkuAndMissingVariant() {
        OrderItem noProduct = new OrderItem().price(new BigDecimal("250")).mrp(BigDecimal.ZERO);
        assertThat(invoiceMrp(noProduct, null)).isEqualByComparingTo("250");

        Product simple = new Product().compareAtPrice(new BigDecimal("500"));
        OrderItem noSku = new OrderItem().price(new BigDecimal("300"));
        assertThat(invoiceMrp(noSku, simple)).isEqualByComparingTo("500");

        Product variants = new Product()
            .compareAtPrice(new BigDecimal("600"))
            .itemStructure("VARIANTS")
            .variantsJson("[{\"sku\":\"OTHER\",\"rate\":450,\"mrp\":700,\"stock\":1,\"active\":true}]");
        OrderItem missingSku = new OrderItem().variantSku("MISSING").price(new BigDecimal("400"));
        assertThat(invoiceMrp(missingSku, variants)).isEqualByComparingTo("600");
    }

    private CustomerOrder refresh(CustomerOrder order) {
        return ReflectionTestUtils.invokeMethod(resource, "refreshTrackingIfStale", order);
    }

    private BigDecimal invoiceMrp(OrderItem item, Product product) {
        return ReflectionTestUtils.invokeMethod(resource, "invoiceItemMrp", item, product);
    }
}
