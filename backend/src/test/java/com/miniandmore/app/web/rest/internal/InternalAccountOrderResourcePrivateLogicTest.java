package com.miniandmore.app.web.rest.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.client.LedgerlyIntegrationClient;
import com.miniandmore.app.domain.*;
import com.miniandmore.app.domain.enumeration.DiscountType;
import com.miniandmore.app.domain.enumeration.OrderStatus;
import com.miniandmore.app.domain.enumeration.PaymentStatus;
import com.miniandmore.app.repository.*;
import com.miniandmore.app.service.LedgerlySyncService;
import com.miniandmore.app.service.OrderEventService;
import com.miniandmore.app.service.StorePricingTestSupport;
import com.miniandmore.app.service.ZohoSyncService;
import com.miniandmore.app.web.rest.v1.AccountOrderDataAccess;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class InternalAccountOrderResourcePrivateLogicTest {

    @Mock
    private CouponRepository couponRepository;

    @Mock
    private CustomerProfileRepository customerProfileRepository;

    @Mock
    private CustomerOrderRepository customerOrderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private SiteSettingRepository siteSettingRepository;

    @Mock
    private LedgerlyIntegrationClient ledgerlyIntegrationClient;

    @Mock
    private LedgerlySyncService ledgerlySyncService;

    @Mock
    private ZohoSyncService zohoSyncService;

    @Mock
    private OrderEventService orderEventService;

    @Mock
    private com.miniandmore.app.service.RazorpayRefundService razorpayRefundService;

    @Mock
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

    private InternalAccountOrderResource resource;

    @BeforeEach
    void setUp() {
        AccountOrderDataAccess dataAccess = new AccountOrderDataAccess(
            couponRepository,
            customerProfileRepository,
            customerOrderRepository,
            orderItemRepository,
            productRepository,
            siteSettingRepository,
            ledgerlyIntegrationClient
        );
        resource = new InternalAccountOrderResource(
            dataAccess,
            new ObjectMapper(),
            new com.miniandmore.app.config.ApplicationProperties(),
            ledgerlySyncService,
            zohoSyncService,
            StorePricingTestSupport.variantSupport(new ObjectMapper()),
            StorePricingTestSupport.defaultPricing(),
            orderEventService,
            razorpayRefundService,
            transactionManager
        );
        ReflectionTestUtils.setField(resource, "razorpayKeyId", "rzp_test");
        ReflectionTestUtils.setField(resource, "razorpayKeySecret", "secret");
        ReflectionTestUtils.setField(resource, "razorpayPaymentDemo", false);
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void utilityHelpers_formatValues() {
        assertThat((String) ReflectionTestUtils.invokeMethod(resource, "blankToNull", "  ")).isNull();
        assertThat((String) ReflectionTestUtils.invokeMethod(resource, "blankToNull", " x ")).isEqualTo("x");
        assertThat((String) ReflectionTestUtils.invokeMethod(resource, "lower", PaymentStatus.PAID)).isEqualTo("paid");
        assertThat((Long) ReflectionTestUtils.invokeMethod(resource, "parseProductId", "42")).isEqualTo(42L);
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(resource, "parseProductId", "bad"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Invalid product id");
        assertThat((long) ReflectionTestUtils.invokeMethod(resource, "toPaise", BigDecimal.valueOf(10.5))).isEqualTo(1050L);
        assertThat((String) ReflectionTestUtils.invokeMethod(resource, "stockAvailabilityMessage", 1)).contains("1 item");
        assertThat((String) ReflectionTestUtils.invokeMethod(resource, "stockAvailabilityMessage", 3)).contains("3 items");
        assertThat((Boolean) ReflectionTestUtils.invokeMethod(resource, "isExpressShipping", "exp")).isTrue();
        assertThat((Boolean) ReflectionTestUtils.invokeMethod(resource, "isExpressShipping", "standard")).isFalse();
    }

    @Test
    void generateOrderNumber_and_displayName() {
        String orderNumber = (String) ReflectionTestUtils.invokeMethod(resource, "generateOrderNumber");
        assertThat(orderNumber).startsWith("MNM-");

        CustomerProfile profile = new CustomerProfile().fullName("Jane Doe");
        assertThat((String) ReflectionTestUtils.invokeMethod(resource, "displayName", "login1", Optional.of(profile))).isEqualTo(
            "Jane Doe"
        );
        assertThat((String) ReflectionTestUtils.invokeMethod(resource, "displayName", "login1", Optional.empty())).isEqualTo("login1");
    }

    @Test
    void commerceSettings_and_shippingTax() {
        SiteSetting settings = new SiteSetting()
            .shippingStandardInr(49)
            .shippingExpressInr(99)
            .gstRate(BigDecimal.valueOf(5))
            .pricesIncludeGst(false);
        when(siteSettingRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(settings));

        Object commerce = ReflectionTestUtils.invokeMethod(resource, "commerceSettings");
        assertThat(commerce).isNotNull();

        BigDecimal shipping = (BigDecimal) ReflectionTestUtils.invokeMethod(
            resource,
            "calculateShipping",
            BigDecimal.valueOf(500),
            "standard",
            commerce
        );
        assertThat(shipping).isEqualByComparingTo("49");

        BigDecimal tax = (BigDecimal) ReflectionTestUtils.invokeMethod(
            resource,
            "calculateTax",
            BigDecimal.valueOf(500),
            commerce
        );
        assertThat(tax).isEqualByComparingTo("25.00");
    }

    @Test
    void calculateCouponDiscount_invalidAndWelcome10() {
        when(couponRepository.findOneByCodeIgnoreCase("MISSING")).thenReturn(Optional.empty());
        Optional<Map<String, Object>> missing = (Optional<Map<String, Object>>) ReflectionTestUtils.invokeMethod(
            resource,
            "calculateCouponDiscount",
            "MISSING",
            BigDecimal.valueOf(500),
            Optional.of("customer1")
        );
        assertThat(missing).isEmpty();

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("customer1", "pw"));
        when(customerOrderRepository.existsByUserLoginAndPaymentStatus("customer1", PaymentStatus.PAID)).thenReturn(true);

        Optional<Map<String, Object>> blocked = (Optional<Map<String, Object>>) ReflectionTestUtils.invokeMethod(
            resource,
            "calculateCouponDiscount",
            "WELCOME10",
            BigDecimal.valueOf(500),
            Optional.of("customer1")
        );
        assertThat(blocked.orElseThrow()).containsKey("error");
    }

    @Test
    void couponSummary_mapsFields() {
        Coupon coupon = new Coupon()
            .code("FLAT50")
            .discountType(DiscountType.FLAT)
            .discountValue(BigDecimal.valueOf(50))
            .minOrder(BigDecimal.valueOf(200))
            .maxDiscount(BigDecimal.valueOf(50));
        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) ReflectionTestUtils.invokeMethod(resource, "couponSummary", coupon);
        assertThat(summary.get("code")).isEqualTo("FLAT50");
        assertThat(summary.get("discount_type")).isEqualTo("flat");
    }

    @Test
    void jsonHelpers_readAndWrite() {
        Object parsed = ReflectionTestUtils.invokeMethod(resource, "readJson", "{\"city\":\"City\"}");
        assertThat(parsed).isNotNull();
        assertThat((Object) ReflectionTestUtils.invokeMethod(resource, "readJson", (Object) null)).isEqualTo(Map.of());
        assertThat((String) ReflectionTestUtils.invokeMethod(resource, "toJson", Map.of("ok", true))).contains("ok");
    }

    @Test
    void releaseInventoryReservation_restoresStock() {
        Product product = new Product().id(5L).stock(2);
        OrderItem line = new OrderItem().productId("5").qty(2);
        CustomerOrder order = new CustomerOrder()
            .id(9L)
            .inventoryReserved(true)
            .itemses(Set.of(line));
        when(orderItemRepository.findAllByOrderId(9L)).thenReturn(List.of(line));
        when(productRepository.findOneForInventoryUpdate(5L)).thenReturn(Optional.of(product));
        when(customerOrderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        ReflectionTestUtils.invokeMethod(resource, "releaseInventoryReservation", order);

        assertThat(product.getStock()).isEqualTo(4);
        assertThat(order.getInventoryReserved()).isFalse();
    }

    @Test
    void computeRazorpaySignature_returnsLowercaseHex() {
        String signature = (String) ReflectionTestUtils.invokeMethod(resource, "computeRazorpaySignature", "order_1", "pay_1");
        assertThat(signature).matches("[0-9a-f]{64}");
    }

    @Test
    void findSuccessfulRazorpayPayment_apiFailure_returnsEmpty() {
        Optional<?> result = (Optional<?>) ReflectionTestUtils.invokeMethod(
            resource,
            "findSuccessfulRazorpayPayment",
            "order_unreachable_test_id"
        );
        assertThat(result).isEmpty();
    }

    @Test
    void refreshTrackingIfStale_pullsWhenLedgerlyLinkedAndStale() {
        CustomerOrder order = new CustomerOrder()
            .id(20L)
            .orderNumber("MNM-20")
            .status(OrderStatus.CONFIRMED)
            .ledgerlySalesOrderId("SO-20")
            .ledgerlyTrackingLastPulledAt(Instant.now().minusSeconds(7200));
        when(customerOrderRepository.findById(20L)).thenReturn(Optional.of(order));

        CustomerOrder refreshed = (CustomerOrder) ReflectionTestUtils.invokeMethod(resource, "refreshTrackingIfStale", order);

        verify(ledgerlySyncService).pullOrderTracking(20L);
        assertThat(refreshed).isNotNull();
    }

    @Test
    void refreshTrackingIfStale_skipsWhenRecentlyPulled() {
        CustomerOrder order = new CustomerOrder()
            .id(21L)
            .orderNumber("MNM-21")
            .status(OrderStatus.CONFIRMED)
            .ledgerlySalesOrderId("SO-21")
            .ledgerlyTrackingLastPulledAt(Instant.now());
        CustomerOrder same = (CustomerOrder) ReflectionTestUtils.invokeMethod(resource, "refreshTrackingIfStale", order);
        assertThat(same).isSameAs(order);
        verify(ledgerlySyncService, never()).pullOrderTracking(any());
    }

    @Test
    void ensureInventoryStillReserved_expiredReservation_releasesAndThrows() {
        Product product = new Product().id(6L).stock(1);
        OrderItem line = new OrderItem().productId("6").qty(1);
        CustomerOrder order = new CustomerOrder()
            .id(30L)
            .inventoryReserved(true)
            .inventoryReservedUntil(Instant.now().minusSeconds(60));
        when(orderItemRepository.findAllByOrderId(30L)).thenReturn(List.of(line));
        when(productRepository.findOneForInventoryUpdate(6L)).thenReturn(Optional.of(product));
        when(customerOrderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(resource, "ensureInventoryStillReserved", order))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("expired");
    }

    @Test
    void releaseInventoryReservation_restoresVariantStock() {
        String variantsJson =
            "[{\"sku\":\"TEE-S-RED\",\"size\":\"S\",\"color\":\"Red\",\"rate\":500,\"stock\":1,\"active\":true}]";
        Product product = new Product()
            .id(7L)
            .stock(1)
            .itemStructure("VARIANTS")
            .variantsJson(variantsJson);
        OrderItem line = new OrderItem().productId("7").qty(1).variantSku("TEE-S-RED").itemSize("S").color("Red");
        CustomerOrder order = new CustomerOrder().id(31L).inventoryReserved(true);
        when(orderItemRepository.findAllByOrderId(31L)).thenReturn(List.of(line));
        when(productRepository.findOneForInventoryUpdate(7L)).thenReturn(Optional.of(product));
        when(customerOrderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        ReflectionTestUtils.invokeMethod(resource, "releaseInventoryReservation", order);

        assertThat(order.getInventoryReserved()).isFalse();
        verify(productRepository).save(product);
    }

    @Test
    void calculateShipping_freeShippingAndZeroSubtotal() {
        SiteSetting settings = new SiteSetting()
            .shippingStandardInr(99)
            .shippingExpressInr(199)
            .freeShippingMinInr(500)
            .pricesIncludeGst(true);
        when(siteSettingRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(settings));
        Object commerce = ReflectionTestUtils.invokeMethod(resource, "commerceSettings");

        BigDecimal zeroShipping = (BigDecimal) ReflectionTestUtils.invokeMethod(
            resource,
            "calculateShipping",
            BigDecimal.ZERO,
            "standard",
            commerce
        );
        assertThat(zeroShipping).isEqualByComparingTo("0");

        BigDecimal freeShipping = (BigDecimal) ReflectionTestUtils.invokeMethod(
            resource,
            "calculateShipping",
            BigDecimal.valueOf(600),
            "standard",
            commerce
        );
        assertThat(freeShipping).isEqualByComparingTo("0");
    }

    @Test
    void attemptLedgerlyOrderSync_skipsWhenAlreadySynced() {
        CustomerOrder order = new CustomerOrder()
            .id(40L)
            .paymentStatus(PaymentStatus.PAID)
            .ledgerlySyncStatus(com.miniandmore.app.domain.enumeration.LedgerlySyncStatus.SYNCED);

        ReflectionTestUtils.invokeMethod(resource, "attemptLedgerlyOrderSync", order);

        verify(ledgerlySyncService, never()).syncOrder(any());
    }

    @Test
    void publicTrackingSummary_limitsFields() {
        CustomerOrder order = new CustomerOrder()
            .orderNumber("MNM-PUB")
            .status(OrderStatus.CONFIRMED)
            .trackingNumber("AWB-1")
            .shipmentDate("2026-08-01");
        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) ReflectionTestUtils.invokeMethod(resource, "publicTrackingSummary", order);
        @SuppressWarnings("unchecked")
        Map<String, Object> orderMap = (Map<String, Object>) summary.get("order");
        assertThat(orderMap).containsKey("tracking_number").doesNotContainKey("total");
    }

    @Test
    void calculateCouponDiscount_expiredInactiveAndMinOrder() {
        Coupon expired = new Coupon()
            .code("OLD")
            .active(true)
            .discountType(DiscountType.PERCENT)
            .discountValue(BigDecimal.TEN)
            .minOrder(BigDecimal.ZERO)
            .expiresAt(Instant.now().minusSeconds(3600));
        when(couponRepository.findOneByCodeIgnoreCase("OLD")).thenReturn(Optional.of(expired));
        Optional<Map<String, Object>> expiredResult = (Optional<Map<String, Object>>) ReflectionTestUtils.invokeMethod(
            resource,
            "calculateCouponDiscount",
            "OLD",
            BigDecimal.valueOf(500),
            Optional.of("customer1")
        );
        assertThat(expiredResult).isEmpty();

        Coupon inactive = new Coupon()
            .code("OFF")
            .active(false)
            .discountType(DiscountType.FLAT)
            .discountValue(BigDecimal.TEN)
            .minOrder(BigDecimal.ZERO);
        when(couponRepository.findOneByCodeIgnoreCase("OFF")).thenReturn(Optional.of(inactive));
        Optional<Map<String, Object>> inactiveResult = (Optional<Map<String, Object>>) ReflectionTestUtils.invokeMethod(
            resource,
            "calculateCouponDiscount",
            "OFF",
            BigDecimal.valueOf(500),
            Optional.of("customer1")
        );
        assertThat(inactiveResult).isEmpty();

        Coupon minOrder = new Coupon()
            .code("BIG")
            .active(true)
            .discountType(DiscountType.FLAT)
            .discountValue(BigDecimal.valueOf(50))
            .minOrder(BigDecimal.valueOf(1000));
        when(couponRepository.findOneByCodeIgnoreCase("BIG")).thenReturn(Optional.of(minOrder));
        Optional<Map<String, Object>> minResult = (Optional<Map<String, Object>>) ReflectionTestUtils.invokeMethod(
            resource,
            "calculateCouponDiscount",
            "BIG",
            BigDecimal.valueOf(500),
            Optional.of("customer1")
        );
        assertThat(minResult).isEmpty();
    }

    @Test
    void calculateCouponDiscount_flatWithMaxDiscountCap() {
        Coupon coupon = new Coupon()
            .code("FLAT200")
            .active(true)
            .discountType(DiscountType.FLAT)
            .discountValue(BigDecimal.valueOf(200))
            .minOrder(BigDecimal.ZERO)
            .maxDiscount(BigDecimal.valueOf(100));
        when(couponRepository.findOneByCodeIgnoreCase("FLAT200")).thenReturn(Optional.of(coupon));

        Optional<Map<String, Object>> result = (Optional<Map<String, Object>>) ReflectionTestUtils.invokeMethod(
            resource,
            "calculateCouponDiscount",
            "FLAT200",
            BigDecimal.valueOf(500),
            Optional.of("customer1")
        );

        assertThat((BigDecimal) result.orElseThrow().get("discount")).isEqualByComparingTo("100.00");
    }

    @Test
    void reserveInventory_productNotFound_throwsConflict() {
        when(productRepository.findOneForInventoryUpdate(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
            ReflectionTestUtils.invokeMethod(
                resource,
                "reserveInventory",
                List.of(new InternalAccountOrderResource.OrderItemRequest("99", null, "Missing", null, null, null, null, 1, BigDecimal.TEN))
            )
        )
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("unavailable");
    }

    @Test
    void reserveInventory_variantUnavailable_throwsConflict() {
        Product product = new Product()
            .id(3L)
            .name("Tee")
            .active(true)
            .itemStructure("VARIANTS")
            .variantsJson("[{\"sku\":\"TEE-S-RED\",\"size\":\"S\",\"color\":\"Red\",\"rate\":500,\"stock\":1,\"active\":true}]");
        when(productRepository.findOneForInventoryUpdate(3L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() ->
            ReflectionTestUtils.invokeMethod(
                resource,
                "reserveInventory",
                List.of(
                    new InternalAccountOrderResource.OrderItemRequest("3", null, "Tee", null, "XL", "Blue", null, 1, BigDecimal.TEN)
                )
            )
        )
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("unavailable");
    }

    @Test
    void readJson_invalidPayload_returnsEmptyMap() {
        Object parsed = ReflectionTestUtils.invokeMethod(resource, "readJson", "{bad");
        assertThat(parsed).isEqualTo(Map.of());
    }

    @Test
    void resolveOrderPlacedAt_derivesFromReservationExpiry() {
        Instant reservedUntil = Instant.parse("2026-08-10T12:30:00Z");
        CustomerOrder order = new CustomerOrder().inventoryReservedUntil(reservedUntil);
        Instant placed = (Instant) ReflectionTestUtils.invokeMethod(resource, "resolveOrderPlacedAt", order);
        assertThat(placed).isEqualTo(reservedUntil.minus(java.time.Duration.ofMinutes(30)));

        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) ReflectionTestUtils.invokeMethod(
            resource,
            "orderSummary",
            new CustomerOrder()
                .id(99L)
                .orderNumber("MNM-99")
                .status(OrderStatus.CONFIRMED)
                .paymentStatus(PaymentStatus.PAID)
                .paymentMethod("online")
                .total(BigDecimal.TEN)
                .inventoryReservedUntil(reservedUntil)
        );
        assertThat(summary.get("created_at")).isNotEqualTo("");
    }

    @Test
    void validRazorpaySignature_demoMode_acceptsAnySignature() {
        ReflectionTestUtils.setField(resource, "razorpayPaymentDemo", true);
        InternalAccountOrderResource.VerifyPaymentRequest request = new InternalAccountOrderResource.VerifyPaymentRequest(
            1L,
            "order_demo",
            "pay_demo",
            "anything"
        );
        assertThat((Boolean) ReflectionTestUtils.invokeMethod(resource, "validRazorpaySignature", request)).isTrue();
    }

    @Test
    void releaseInventoryReservation_skipsInvalidProductId() {
        OrderItem bad = new OrderItem().productId("bad").qty(1);
        CustomerOrder order = new CustomerOrder().id(32L).inventoryReserved(true);
        when(orderItemRepository.findAllByOrderId(32L)).thenReturn(List.of(bad));
        when(customerOrderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        ReflectionTestUtils.invokeMethod(resource, "releaseInventoryReservation", order);

        assertThat(order.getInventoryReserved()).isFalse();
    }

    @Test
    void calculateTax_pricesIncludeGst_extractsEmbeddedTax() {
        SiteSetting settings = new SiteSetting().gstRate(BigDecimal.valueOf(18)).pricesIncludeGst(true);
        when(siteSettingRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(settings));
        Object commerce = ReflectionTestUtils.invokeMethod(resource, "commerceSettings");
        BigDecimal tax = (BigDecimal) ReflectionTestUtils.invokeMethod(
            resource,
            "calculateTax",
            BigDecimal.valueOf(118),
            commerce
        );
        assertThat(tax).isEqualByComparingTo("18.00");
    }

    @Test
    void calculateIncludedTax_extractsCodGstAtAggregateRate() {
        BigDecimal tax = (BigDecimal) ReflectionTestUtils.invokeMethod(
            resource,
            "calculateIncludedTax",
            BigDecimal.valueOf(99),
            BigDecimal.valueOf(5)
        );

        assertThat(tax).isEqualByComparingTo("4.71");
    }

    @Test
    void refreshTrackingIfStale_pullFailure_stillReturnsOrder() {
        CustomerOrder order = new CustomerOrder()
            .id(22L)
            .orderNumber("MNM-22")
            .status(OrderStatus.CONFIRMED)
            .ledgerlySalesOrderId("SO-22");
        org.mockito.Mockito.doThrow(new RuntimeException("pull down")).when(ledgerlySyncService).pullOrderTracking(22L);

        CustomerOrder result = (CustomerOrder) ReflectionTestUtils.invokeMethod(resource, "refreshTrackingIfStale", order);

        assertThat(result).isNotNull();
    }

    @Test
    void releaseInventoryReservation_alreadyReleased_isNoOp() {
        CustomerOrder order = new CustomerOrder().id(33L).inventoryReserved(false).inventoryReleasedAt(java.time.Instant.now());
        ReflectionTestUtils.invokeMethod(resource, "releaseInventoryReservation", order);
        org.mockito.Mockito.verify(orderItemRepository, org.mockito.Mockito.never()).findAllByOrderId(any());
    }

    @Test
    void reserveInventory_variantMissingSku_throwsConflict() {
        Product product = new Product()
            .id(4L)
            .name("Tee")
            .active(true)
            .itemStructure("VARIANTS")
            .variantsJson("[{\"sku\":\"\",\"size\":\"S\",\"color\":\"Red\",\"rate\":500,\"stock\":3,\"active\":true}]");
        when(productRepository.findOneForInventoryUpdate(4L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() ->
            ReflectionTestUtils.invokeMethod(
                resource,
                "reserveInventory",
                List.of(
                    new InternalAccountOrderResource.OrderItemRequest("4", null, "Tee", null, "S", "Red", null, 1, BigDecimal.TEN)
                )
            )
        )
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Variant SKU missing");
    }

    @Test
    void attemptLedgerlyOrderSync_unpaidOrder_skipsSync() {
        CustomerOrder order = new CustomerOrder().id(41L).paymentStatus(PaymentStatus.PENDING);
        ReflectionTestUtils.invokeMethod(resource, "attemptLedgerlyOrderSync", order);
        org.mockito.Mockito.verify(ledgerlySyncService, org.mockito.Mockito.never()).syncOrder(any());
    }

    @Test
    void orderSummaries_includeLineItems() {
        CustomerOrder order = new CustomerOrder()
            .id(12L)
            .orderNumber("MNM-12")
            .status(OrderStatus.CONFIRMED)
            .paymentStatus(PaymentStatus.PAID)
            .paymentMethod("online")
            .total(BigDecimal.valueOf(100))
            .placedAt(Instant.parse("2026-08-10T10:00:00Z"));
        OrderItem item = new OrderItem().id(1L).productId("1").productName("Dress").qty(1).price(BigDecimal.TEN);
        when(orderItemRepository.findAllByOrderId(12L)).thenReturn(List.of(item));

        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) ReflectionTestUtils.invokeMethod(resource, "orderSummary", order);
        assertThat(summary.get("order_number")).isEqualTo("MNM-12");
        assertThat(summary.get("order_items")).isInstanceOf(List.class);

        @SuppressWarnings("unchecked")
        Map<String, Object> payment = (Map<String, Object>) ReflectionTestUtils.invokeMethod(resource, "paymentSummary", order);
        assertThat(payment.get("payment_status")).isEqualTo("paid");
    }
}
