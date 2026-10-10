package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.client.LedgerlyIntegrationClient;
import com.miniandmore.app.client.XpressBeesClient;
import com.miniandmore.app.client.ZohoBooksIntegrationClient;
import com.miniandmore.app.config.ApplicationProperties;
import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.OrderItem;
import com.miniandmore.app.domain.OrderReturn;
import com.miniandmore.app.domain.OrderReturnItem;
import com.miniandmore.app.domain.Product;
import com.miniandmore.app.domain.enumeration.ExchangeSettlementStatus;
import com.miniandmore.app.domain.enumeration.OrderRequestType;
import com.miniandmore.app.domain.enumeration.OrderReturnStatus;
import com.miniandmore.app.domain.enumeration.OrderStatus;
import com.miniandmore.app.domain.enumeration.PackageType;
import com.miniandmore.app.domain.enumeration.PaymentStatus;
import com.miniandmore.app.domain.enumeration.ReturnDisposition;
import com.miniandmore.app.domain.enumeration.ReturnLogisticsMode;
import com.miniandmore.app.repository.CustomerOrderRepository;
import com.miniandmore.app.repository.OrderItemRepository;
import com.miniandmore.app.repository.OrderReturnItemRepository;
import com.miniandmore.app.repository.OrderReturnRepository;
import com.miniandmore.app.repository.ProductRepository;
import com.miniandmore.app.repository.SiteSettingRepository;
import com.miniandmore.app.repository.StoreAddressRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

/** JaCoCo branch gaps in {@link OrderReturnService} helpers and guards. */
@ExtendWith(MockitoExtension.class)
class OrderReturnServiceBranchCoverageTest {

    private static final String USER = "customer1";
    private static final String ORDER_NUMBER = "ORD-9001";

    @Mock
    private OrderReturnRepository orderReturnRepository;

    @Mock
    private CustomerOrderRepository customerOrderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private OrderReturnItemRepository orderReturnItemRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private RazorpayRefundService razorpayRefundService;

    @Mock
    private LedgerlyIntegrationClient ledgerlyIntegrationClient;

    @Mock
    private ZohoBooksIntegrationClient zohoBooksIntegrationClient;

    @Spy
    private ProductVariantSupport variantSupport = StorePricingTestSupport.variantSupport(new ObjectMapper());

    @Mock
    private OrderMailService orderMailService;

    @Mock
    private OrderSmsService orderSmsService;

    @Mock
    private ApplicationProperties applicationProperties;

    @Mock
    private XpressBeesClient xpressBeesClient;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private CaseSlaSupport caseSlaSupport;

    @Mock
    private SiteSettingRepository siteSettingRepository;

    @Mock
    private OrderPackageService orderPackageService;

    @Mock
    private ExchangeRazorpaySettlementService exchangeRazorpaySettlementService;

    @Mock
    private StoreAddressRepository storeAddressRepository;

    @InjectMocks
    private OrderReturnService orderReturnService;

    @BeforeEach
    void setUp() {
        lenient().when(applicationProperties.getZoho()).thenReturn(new ApplicationProperties.Zoho());
        lenient().when(applicationProperties.getLedgerly()).thenReturn(new ApplicationProperties.Ledgerly());
        lenient().when(applicationProperties.getXpressbees()).thenReturn(new ApplicationProperties.XpressBees());
        ReturnXpressBeesShipmentFactory shipmentFactory = new ReturnXpressBeesShipmentFactory(
            applicationProperties,
            new ObjectMapper(),
            orderReturnItemRepository,
            storeAddressRepository
        );
        ReflectionTestUtils.setField(orderReturnService, "shipmentFactory", shipmentFactory);
    }

    @ParameterizedTest
    @EnumSource(OrderReturnStatus.class)
    void stageForSummary_mapsEveryStatusToStageBucket(OrderReturnStatus status) {
        String stage = ReflectionTestUtils.invokeMethod(OrderReturnService.class, "stageForSummary", status);
        assertThat(stage).isIn("request", "pickup", "inspection", "exchange", "closed");
    }

    @Test
    void isPackageCompleted_requiresPackedAtAndMinimumDimensions() {
        OrderReturn incomplete = exchange(OrderReturnStatus.REPACKING);
        assertThat(OrderReturnService.isPackageCompleted(incomplete)).isFalse();

        incomplete.setPackedAt(Instant.now());
        assertThat(OrderReturnService.isPackageCompleted(incomplete)).isFalse();

        incomplete.setPackageLengthCm(10);
        incomplete.setPackageBreadthCm(8);
        incomplete.setPackageHeightCm(0);
        assertThat(OrderReturnService.isPackageCompleted(incomplete)).isFalse();

        incomplete.setPackageHeightCm(6);
        assertThat(OrderReturnService.isPackageCompleted(incomplete)).isTrue();
    }

    @Test
    void isOutboundPackageEligible_requiresExchangeInPackingStates() {
        assertThat(
            (Boolean) ReflectionTestUtils.invokeMethod(OrderReturnService.class, "isOutboundPackageEligible", new Object[] { null })
        ).isFalse();

        OrderReturn plain = returnWithStatus(OrderReturnStatus.REPACKING);
        assertThat(
            (Boolean) ReflectionTestUtils.invokeMethod(OrderReturnService.class, "isOutboundPackageEligible", plain)
        ).isFalse();

        OrderReturn exchange = exchange(OrderReturnStatus.APPROVED);
        assertThat(
            (Boolean) ReflectionTestUtils.invokeMethod(OrderReturnService.class, "isOutboundPackageEligible", exchange)
        ).isFalse();

        OrderReturn packing = exchange(OrderReturnStatus.REPACKING);
        assertThat(
            (Boolean) ReflectionTestUtils.invokeMethod(OrderReturnService.class, "isOutboundPackageEligible", packing)
        ).isTrue();
    }

    @Test
    void isCollectedCod_trueOnlyForPaidCodOrders() {
        CustomerOrder codPaid = eligibleOrder().paymentMethod("cod").paymentStatus(PaymentStatus.PAID);
        CustomerOrder codPending = eligibleOrder().paymentMethod("cod").paymentStatus(PaymentStatus.PENDING);
        assertThat(OrderReturnService.isCollectedCod(codPaid)).isTrue();
        assertThat(OrderReturnService.isCollectedCod(codPending)).isFalse();
        assertThat(OrderReturnService.isCollectedCod(null)).isFalse();
    }

    @Test
    void documentNumber_usesZeroWhenOrderReturnOrIdMissing() {
        assertThat(orderReturnService.documentNumber("DOC-", null)).isEqualTo("DOC-00000");
        assertThat(orderReturnService.documentNumber("DOC-", new OrderReturn())).isEqualTo("DOC-00000");
    }

    @Test
    void defaultPackageWeightGrams_multipliesPerLineWeightByOrderItemQty() {
        ApplicationProperties.XpressBees xpressBees = new ApplicationProperties.XpressBees();
        xpressBees.setDefaultWeightGrams(250);
        when(applicationProperties.getXpressbees()).thenReturn(xpressBees);

        assertThat(orderReturnService.defaultPackageWeightGrams(null)).isEqualTo(250);

        OrderReturn orderReturn = exchange(OrderReturnStatus.REPACKING);
        orderReturn.getOrderItem().qty(null);
        assertThat(orderReturnService.defaultPackageWeightGrams(orderReturn)).isEqualTo(250);

        orderReturn.getOrderItem().qty(3);
        assertThat(orderReturnService.defaultPackageWeightGrams(orderReturn)).isEqualTo(750);
    }

    @Test
    void parseImageUrls_skipsBlankTextualArrayEntries() {
        @SuppressWarnings("unchecked")
        List<String> urls = ReflectionTestUtils.invokeMethod(
            OrderReturnService.class,
            "parseImageUrls",
            "[\"  \", \"https://cdn.example.com/ok.jpg\", null]"
        );
        assertThat(urls).containsExactly("https://cdn.example.com/ok.jpg");
    }

    @Test
    void priorCreditNoteIds_includesOrderCreditNoteAndSkipsDuplicateExchangeNotes() {
        CustomerOrder order = eligibleOrder();
        order.setZohoCreditNoteId("cn-shared");
        OrderReturn earlier = exchange(OrderReturnStatus.ITEM_ALLOCATED);
        earlier.setZohoCreditNoteId("cn-shared");
        OrderReturn current = exchange(OrderReturnStatus.ACCEPTED);
        when(orderReturnRepository.findAllByOrder_IdOrderByRequestedAtAsc(10L)).thenReturn(List.of(earlier));

        @SuppressWarnings("unchecked")
        List<String> ids = ReflectionTestUtils.invokeMethod(orderReturnService, "priorCreditNoteIds", order, current);

        assertThat(ids).containsExactly("cn-shared");
    }

    @Test
    void issueReplacementInvoice_skipsWhenZohoReturnSyncDisabled() {
        ApplicationProperties.Zoho zoho = new ApplicationProperties.Zoho();
        zoho.setSyncReturns(false);
        when(applicationProperties.getZoho()).thenReturn(zoho);
        OrderReturn orderReturn = exchange(OrderReturnStatus.ACCEPTED);

        ReflectionTestUtils.invokeMethod(orderReturnService, "issueReplacementInvoice", orderReturn);

        verify(zohoBooksIntegrationClient, never()).createExchangeReplacementInvoice(any(), any(), any(), any(), any(), any(), any(), anyInt(), any());
    }

    @Test
    void issueReplacementInvoice_toleratesEmptyInvoiceIdFromZoho() {
        ApplicationProperties.Zoho zoho = new ApplicationProperties.Zoho();
        zoho.setSyncReturns(true);
        when(applicationProperties.getZoho()).thenReturn(zoho);
        OrderReturn orderReturn = exchange(OrderReturnStatus.ACCEPTED);
        when(zohoBooksIntegrationClient.createExchangeReplacementInvoice(any(), any(), any(), any(), any(), any(), any(), anyInt(), any()))
            .thenReturn(Optional.empty());

        ReflectionTestUtils.invokeMethod(orderReturnService, "issueReplacementInvoice", orderReturn);

        assertThat(orderReturn.getZohoReplacementInvoiceId()).isNull();
    }

    @Test
    void releaseExchangeReservation_plainReturnExitsWithoutMutatingReservation() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.ACCEPTED);
        orderReturn.setReplacementReservedAt(Instant.now());

        ReflectionTestUtils.invokeMethod(orderReturnService, "releaseExchangeReservation", orderReturn);

        assertThat(orderReturn.getReplacementReservationReleasedAt()).isNull();
    }

    @Test
    void closePickupFailed_preservesExistingRejectionReason() {
        OrderReturn orderReturn = exchange(OrderReturnStatus.PICKUP_FAILED);
        orderReturn.setPickupFailureReason("Courier no-show");
        orderReturn.setRejectionReason("Already recorded");
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(orderReturnRepository.save(any(OrderReturn.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderReturn closed = orderReturnService.closePickupFailed("RET-1", "Closing");

        assertThat(closed.getRejectionReason()).isEqualTo("Already recorded");
    }

    @Test
    void markPickupFailed_allowedFromApprovedStatus() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.APPROVED);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(orderReturnRepository.save(any(OrderReturn.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderReturn failed = orderReturnService.markPickupFailed("RET-1", "Address issue", "note");

        assertThat(failed.getStatus()).isEqualTo(OrderReturnStatus.PICKUP_FAILED);
        verify(orderMailService).sendPickupFailedAdminNotice(any(), eq(failed));
    }

    @Test
    void acceptAfterInspection_exchangeSkipsReturnLineInspection() {
        OrderReturn orderReturn = exchange(OrderReturnStatus.INSPECTED);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(orderReturnRepository.save(any(OrderReturn.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderReturn accepted = orderReturnService.acceptAfterInspection(
            "RET-1",
            ReturnDisposition.RESTOCKABLE,
            List.of(),
            "QC ok"
        );

        assertThat(accepted.getStatus()).isEqualTo(OrderReturnStatus.ACCEPTED);
        verify(orderReturnItemRepository, never()).findAllByOrderReturnIdOrderById(any());
    }

    @Test
    void settlePriceDifference_rejectsNullSettlementStatus() {
        OrderReturn orderReturn = exchange(OrderReturnStatus.APPROVED);
        orderReturn.setPriceDifference(new BigDecimal("50.00"));
        orderReturn.setSettlementStatus(ExchangeSettlementStatus.PENDING);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));

        assertThatThrownBy(() ->
            orderReturnService.settlePriceDifference(
                "RET-1",
                null,
                new BigDecimal("50.00"),
                "manual",
                "ref-1",
                "admin",
                null,
                false
            )
        )
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("does not match");
    }

    @Test
    void queueAdminPickupFailedEmail_nullOrderReturn_isNoOp() {
        ReflectionTestUtils.invokeMethod(orderReturnService, "queueAdminPickupFailedEmail", eligibleOrder(), null);
        verify(orderMailService, never()).sendPickupFailedAdminNotice(any(), any());
    }

    @Test
    void syncZohoInventoryAfterExchangeStockChange_skipsWhenStockSyncDisabled() {
        ApplicationProperties.Zoho zoho = new ApplicationProperties.Zoho();
        zoho.setSyncProductStock(false);
        when(applicationProperties.getZoho()).thenReturn(zoho);
        OrderReturn orderReturn = exchange(OrderReturnStatus.ACCEPTED);

        ReflectionTestUtils.invokeMethod(orderReturnService, "syncZohoInventoryAfterExchangeStockChange", orderReturn, 2L);

        verify(zohoBooksIntegrationClient, never()).syncProductStockOnly(any());
    }

    @Test
    void restoreItemStock_invalidProductIdAndNonPositiveQtyReturnNull() {
        OrderItem badId = new OrderItem().productId("not-a-number").qty(1);
        assertThat((Long) ReflectionTestUtils.invokeMethod(orderReturnService, "restoreItemStock", badId)).isNull();

        OrderItem zeroQty = new OrderItem().productId("12").qty(1);
        assertThat((Long) ReflectionTestUtils.invokeMethod(orderReturnService, "restoreItemStock", zeroQty, 0)).isNull();
    }

    @Test
    void restoreItemStock_simpleProductIncrementsAggregateStock() {
        OrderItem item = new OrderItem().productId("2").qty(2);
        Product product = new Product().id(2L).stock(4).itemStructure("SIMPLE");
        when(productRepository.findOneForInventoryUpdate(2L)).thenReturn(Optional.of(product));
        when(productRepository.save(product)).thenReturn(product);

        Long productId = ReflectionTestUtils.invokeMethod(orderReturnService, "restoreItemStock", item, 2);

        assertThat(productId).isEqualTo(2L);
        assertThat(product.getStock()).isEqualTo(6);
    }

    @Test
    void syncExchangeProductStockToZoho_ignoresNonNumericProductIds() {
        OrderReturn orderReturn = exchange(OrderReturnStatus.ACCEPTED);
        orderReturn.getOrderItem().productId("catalog-sku-only");

        ReflectionTestUtils.invokeMethod(orderReturnService, "syncExchangeProductStockToZoho", orderReturn);

        verify(zohoBooksIntegrationClient, never()).syncProductStockOnly(any());
    }

    @Test
    void completeOutboundPackage_rejectsWhenForwardConfiguredButCourierIdBlank() {
        OrderReturn orderReturn = packedExchange(OrderReturnStatus.REPACKING);
        orderReturn.getOrder().setShippingAddressJson("{\"pincode\":\"560001\"}");
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(xpressBeesClient.isForwardShipmentConfigured()).thenReturn(true);
        when(orderPackageService.previewRatesForDestination(any(), anyInt(), anyInt(), anyInt(), anyInt(), eq(false), any()))
            .thenReturn(Map.of("couriers", List.of()));
        when(orderPackageService.selectCourier(any(), any(), any())).thenReturn(new OrderPackageService.CourierSelection("", "Courier", 500));

        assertThatThrownBy(() -> orderReturnService.completeOutboundPackage("RET-1", 500, 10, 8, 6, null, null, "box"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("No serviceable couriers");
    }

    @Test
    void completeOutboundPackage_fromReadyToShip_keepsStatus() {
        OrderReturn orderReturn = packedExchange(OrderReturnStatus.READY_TO_SHIP);
        orderReturn.getOrder().setShippingAddressJson("{\"pincode\":\"560001\"}");
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);
        when(xpressBeesClient.isForwardShipmentConfigured()).thenReturn(false);
        when(orderPackageService.previewRatesForDestination(any(), anyInt(), anyInt(), anyInt(), anyInt(), eq(false), any()))
            .thenReturn(Map.of("couriers", List.of(Map.of("id", "1", "name", "Courier"))));
        when(orderPackageService.selectCourier(any(), any(), any())).thenReturn(new OrderPackageService.CourierSelection("1", "Courier", 500));

        OrderReturn saved = orderReturnService.completeOutboundPackage("RET-1", 500, 10, 8, 6, null, null, "box");

        assertThat(saved.getStatus()).isEqualTo(OrderReturnStatus.READY_TO_SHIP);
        assertThat(saved.getPackageType()).isEqualTo(PackageType.BOX);
    }

    @Test
    void isReplacementSellable_falseWhenOrderMissingOnItem() {
        OrderItem orphan = new OrderItem().id(5L).productId("2");
        when(orderItemRepository.findById(5L)).thenReturn(Optional.of(orphan));
        assertThat(orderReturnService.isReplacementSellable(USER, 5L, "M", "Blue")).isFalse();
    }

    @Test
    void requestExchange_rejectsWhenProductCannotBeResolved() {
        CustomerOrder order = eligibleOrder();
        OrderItem item = new OrderItem().id(7L).productId("99");
        when(customerOrderRepository.findOneByOrderNumberAndUserLogin(ORDER_NUMBER, USER)).thenReturn(Optional.of(order));
        when(orderItemRepository.findByIdAndOrderId(7L, 10L)).thenReturn(Optional.of(item));
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
            orderReturnService.requestExchange(USER, ORDER_NUMBER, 7L, "Need larger size please", "L", "Blue")
        )
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("does not support exchanges");
    }

    @ParameterizedTest
    @MethodSource("invalidReturnLineSelections")
    void requestReturn_rejectsInvalidLineSelections(OrderReturnService.ReturnLineSelection line) {
        CustomerOrder order = eligibleOrder();
        OrderItem lineItem = new OrderItem().id(77L).qty(2).price(BigDecimal.TEN);
        when(customerOrderRepository.findOneByOrderNumberAndUserLogin(ORDER_NUMBER, USER)).thenReturn(Optional.of(order));
        when(orderReturnRepository.existsByOrderIdAndStatusIn(eq(order.getId()), any())).thenReturn(false);
        when(orderItemRepository.findAllByOrderId(order.getId())).thenReturn(List.of(lineItem));

        assertThatThrownBy(() ->
            orderReturnService.requestReturn(USER, ORDER_NUMBER, "Damaged item received", List.of(line), List.of())
        )
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("order item and positive quantity");
    }

    private static Stream<Arguments> invalidReturnLineSelections() {
        return Stream.of(
            Arguments.of(new OrderReturnService.ReturnLineSelection(null, 1)),
            Arguments.of(new OrderReturnService.ReturnLineSelection(77L, null)),
            Arguments.of(new OrderReturnService.ReturnLineSelection(77L, 0))
        );
    }

    private CustomerOrder eligibleOrder() {
        return new CustomerOrder()
            .id(10L)
            .userLogin(USER)
            .orderNumber(ORDER_NUMBER)
            .status(OrderStatus.FULFILLED)
            .paymentStatus(PaymentStatus.PAID)
            .subtotal(BigDecimal.TEN)
            .discount(BigDecimal.ZERO)
            .shipping(BigDecimal.ZERO)
            .tax(BigDecimal.ZERO)
            .donation(BigDecimal.ZERO)
            .total(BigDecimal.TEN)
            .shippingAddressJson("{}")
            .shipmentDate(LocalDate.now().toString())
            .shipmentDeliveredAt(Instant.now());
    }

    private OrderReturn returnWithStatus(OrderReturnStatus status) {
        OrderReturn orderReturn = new OrderReturn();
        orderReturn.setReturnNumber("RET-1");
        orderReturn.setStatus(status);
        orderReturn.setRequestType(OrderRequestType.RETURN);
        orderReturn.setOrder(eligibleOrder());
        return orderReturn;
    }

    private OrderReturn exchange(OrderReturnStatus status) {
        OrderReturn orderReturn = returnWithStatus(status);
        orderReturn.setRequestType(OrderRequestType.EXCHANGE);
        orderReturn.setOrderItem(new OrderItem().id(5L).productId("2").qty(1).price(BigDecimal.TEN));
        orderReturn.setReplacementVariantSku("TEE-M-BLUE");
        orderReturn.setReplacementSize("M");
        orderReturn.setReplacementColor("Blue");
        return orderReturn;
    }

    private OrderReturn packedExchange(OrderReturnStatus status) {
        OrderReturn orderReturn = exchange(status);
        orderReturn.setPackedAt(Instant.now());
        orderReturn.setPackageWeightGrams(500);
        orderReturn.setPackageLengthCm(12);
        orderReturn.setPackageBreadthCm(10);
        orderReturn.setPackageHeightCm(8);
        return orderReturn;
    }

    @Test
    void adminSummary_countsOverdueOpenCases() {
        when(orderReturnRepository.countGroupedByRequestType()).thenReturn(java.util.Collections.emptyList());
        when(orderReturnRepository.countGroupedByStatus())
            .thenReturn(java.util.Collections.singletonList(new Object[] { OrderReturnStatus.REQUESTED, 2L }));
        when(orderReturnRepository.countByStatus(OrderReturnStatus.APPROVED)).thenReturn(1L);
        Instant old = Instant.now().minus(30, ChronoUnit.DAYS);
        when(orderReturnRepository.findStatusAndRequestedAtByStatusNotIn(any()))
            .thenReturn(java.util.Collections.singletonList(new Object[] { OrderReturnStatus.REQUESTED, old }));
        when(caseSlaSupport.isReturnOverdue("requested", old)).thenReturn(true);

        Map<String, Object> summary = orderReturnService.adminSummary();

        assertThat(summary.get("overdue")).isEqualTo(1L);
        assertThat(summary.get("xpressbeesReverseReady")).isEqualTo(1L);
        assertThat(summary.get("open")).isEqualTo(2L);
    }

    @Test
    void finalizeRefund_partialAmount_marksPartiallyRefunded() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.ACCEPTED);
        orderReturn.getOrder().setTotal(BigDecimal.valueOf(1000));
        orderReturn.setRazorpayRefundId(null);
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);
        when(customerOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ReflectionTestUtils.invokeMethod(
            orderReturnService,
            "finalizeRefund",
            orderReturn,
            BigDecimal.valueOf(250),
            null,
            false
        );

        assertThat(orderReturn.getOrder().getPaymentStatus()).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);
        assertThat(orderReturn.getRefundAmount()).isEqualByComparingTo("250");
    }

    @Test
    void finalizeRefund_codUncollected_keepsPaymentStatus() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.ACCEPTED);
        CustomerOrder order = orderReturn.getOrder();
        order.setPaymentMethod("cod");
        order.setPaymentStatus(PaymentStatus.PENDING);
        order.setTotal(BigDecimal.valueOf(500));
        orderReturn.setRazorpayRefundId("rfnd_cod");
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);

        ReflectionTestUtils.invokeMethod(
            orderReturnService,
            "finalizeRefund",
            orderReturn,
            BigDecimal.valueOf(500),
            null,
            false
        );

        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void finalizeRefund_razorpayRefundPending_marksRefundInitiated() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.ACCEPTED);
        orderReturn.setRazorpayRefundId("rfnd_live");
        orderReturn.getOrder().setPaymentStatus(PaymentStatus.PAID);
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);
        when(customerOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ReflectionTestUtils.invokeMethod(
            orderReturnService,
            "finalizeRefund",
            orderReturn,
            BigDecimal.valueOf(100),
            null,
            false
        );

        assertThat(orderReturn.getOrder().getPaymentStatus()).isEqualTo(PaymentStatus.REFUND_INITIATED);
    }

    @Test
    void finalizeRefund_fullMerchandiseRefund_marksOrderRefunded() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.ACCEPTED);
        orderReturn.getOrder().setTotal(BigDecimal.valueOf(500));
        orderReturn.setRazorpayRefundId(null);
        orderReturn.setManualRefundReference("UTR-FULL-REF");
        orderReturn.setManualRefundRecordedAt(Instant.now());
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);
        when(customerOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ReflectionTestUtils.invokeMethod(
            orderReturnService,
            "finalizeRefund",
            orderReturn,
            BigDecimal.valueOf(500),
            "approved",
            false
        );

        assertThat(orderReturn.getOrder().getPaymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(orderReturn.getRefundAmount()).isEqualByComparingTo("500");
    }

    @Test
    void finalizeRefund_ledgerlySyncEnabled_recordsCreditNoteId() {
        ApplicationProperties.Ledgerly ledgerly = new ApplicationProperties.Ledgerly();
        ledgerly.setSyncReturns(true);
        when(applicationProperties.getLedgerly()).thenReturn(ledgerly);
        when(applicationProperties.getZoho()).thenReturn(new ApplicationProperties.Zoho());

        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.ACCEPTED);
        orderReturn.getOrder().setOrderNumber("MNM-77");
        orderReturn.setReturnNumber("RET-77");
        orderReturn.setRazorpayRefundId("rfnd_77");
        when(ledgerlyIntegrationClient.cancelOrder("MNM-77", "RET-77", "rfnd_77")).thenReturn(Optional.of("cn-ledgerly-77"));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);
        when(customerOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ReflectionTestUtils.invokeMethod(
            orderReturnService,
            "finalizeRefund",
            orderReturn,
            BigDecimal.valueOf(50),
            null,
            false
        );

        assertThat(orderReturn.getLedgerlyCreditNoteId()).isEqualTo("cn-ledgerly-77");
    }

    @Test
    void finalizeRefund_exchangeSettlementReferenceWithoutRazorpayId_marksRefundInitiated() {
        OrderReturn orderReturn = exchange(OrderReturnStatus.ACCEPTED);
        orderReturn.setSettlementReference("rfnd_exchange_1");
        orderReturn.setRazorpayRefundId(null);
        orderReturn.getOrder().setPaymentStatus(PaymentStatus.PAID);
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);
        when(customerOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ReflectionTestUtils.invokeMethod(
            orderReturnService,
            "finalizeRefund",
            orderReturn,
            BigDecimal.valueOf(100),
            null,
            false
        );

        assertThat(orderReturn.getOrder().getPaymentStatus()).isEqualTo(PaymentStatus.REFUND_INITIATED);
    }

    @Test
    void finalizeRefund_zohoSyncFailure_mergesAdminNotes() {
        ApplicationProperties.Zoho zoho = new ApplicationProperties.Zoho();
        zoho.setSyncReturns(true);
        when(applicationProperties.getZoho()).thenReturn(zoho);
        when(applicationProperties.getLedgerly()).thenReturn(new ApplicationProperties.Ledgerly());

        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.ACCEPTED);
        orderReturn.getOrder().setId(88L);
        orderReturn.setReturnNumber("RET-ZOHO-FAIL");
        when(zohoBooksIntegrationClient.createCreditNote(eq(88L), eq("RET-ZOHO-FAIL"), any(Map.class)))
            .thenThrow(new RuntimeException("Zoho unavailable"));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);
        when(customerOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ReflectionTestUtils.invokeMethod(
            orderReturnService,
            "finalizeRefund",
            orderReturn,
            BigDecimal.TEN,
            "initial",
            false
        );

        assertThat(orderReturn.getAdminNotes()).contains("Zoho credit note failed");
    }

    @Test
    void finalizeRefund_ledgerlyFailure_mergesAdminNotes() {
        ApplicationProperties.Ledgerly ledgerly = new ApplicationProperties.Ledgerly();
        ledgerly.setSyncReturns(true);
        when(applicationProperties.getLedgerly()).thenReturn(ledgerly);
        when(applicationProperties.getZoho()).thenReturn(new ApplicationProperties.Zoho());

        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.ACCEPTED);
        orderReturn.getOrder().setOrderNumber("MNM-FAIL");
        orderReturn.setReturnNumber("RET-FAIL");
        when(ledgerlyIntegrationClient.cancelOrder(any(), any(), any())).thenThrow(new RuntimeException("Ledgerly down"));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);
        when(customerOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ReflectionTestUtils.invokeMethod(
            orderReturnService,
            "finalizeRefund",
            orderReturn,
            BigDecimal.ONE,
            null,
            false
        );

        assertThat(orderReturn.getAdminNotes()).contains("Ledgerly order cancel failed");
    }

    @Test
    void finalizeRefund_skipsPaymentUpdateWhenOrderAlreadyRefunded() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.ACCEPTED);
        orderReturn.getOrder().setPaymentStatus(PaymentStatus.REFUNDED);
        orderReturn.getOrder().setTotal(BigDecimal.valueOf(200));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);

        ReflectionTestUtils.invokeMethod(
            orderReturnService,
            "finalizeRefund",
            orderReturn,
            BigDecimal.valueOf(50),
            null,
            false
        );

        org.mockito.Mockito.verify(customerOrderRepository, org.mockito.Mockito.never()).save(any());
    }
}
