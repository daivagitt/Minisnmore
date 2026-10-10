package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

/** Additional JaCoCo branch coverage for {@link OrderReturnService} private helpers. */
@ExtendWith(MockitoExtension.class)
class OrderReturnServiceDeepBranchCoverageTest {

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
    @MethodSource("incompletePackageCases")
    void isPackageCompleted_falseForIncompleteDimensions(OrderReturn orderReturn) {
        assertThat(OrderReturnService.isPackageCompleted(orderReturn)).isFalse();
    }

    private static Stream<Arguments> incompletePackageCases() {
        OrderReturn base = new OrderReturn();
        base.setPackedAt(Instant.now());
        base.setPackageLengthCm(10);
        base.setPackageBreadthCm(10);
        base.setPackageHeightCm(10);

        OrderReturn nullReturn = null;
        OrderReturn noPackedAt = copyPackage(base);
        noPackedAt.setPackedAt(null);
        OrderReturn noLength = copyPackage(base);
        noLength.setPackageLengthCm(null);
        OrderReturn zeroHeight = copyPackage(base);
        zeroHeight.setPackageHeightCm(0);
        OrderReturn noBreadth = copyPackage(base);
        noBreadth.setPackageBreadthCm(null);
        OrderReturn zeroLength = copyPackage(base);
        zeroLength.setPackageLengthCm(0);

        return Stream.of(
            Arguments.of(nullReturn),
            Arguments.of(noPackedAt),
            Arguments.of(noLength),
            Arguments.of(noBreadth),
            Arguments.of(zeroHeight),
            Arguments.of(zeroLength)
        );
    }

    private static OrderReturn copyPackage(OrderReturn source) {
        OrderReturn copy = new OrderReturn();
        copy.setPackedAt(source.getPackedAt());
        copy.setPackageLengthCm(source.getPackageLengthCm());
        copy.setPackageBreadthCm(source.getPackageBreadthCm());
        copy.setPackageHeightCm(source.getPackageHeightCm());
        return copy;
    }

    @Test
    void hasReverseShipment_trueWhenLogisticsModeOrAwbPresent() {
        OrderReturn withMode = new OrderReturn();
        withMode.setLogisticsMode(ReturnLogisticsMode.XPRESSBEES);
        OrderReturn withAwb = new OrderReturn();
        withAwb.setReverseAwb("AWB-1");
        assertThat((Boolean) ReflectionTestUtils.invokeMethod(OrderReturnService.class, "hasReverseShipment", withMode)).isTrue();
        assertThat((Boolean) ReflectionTestUtils.invokeMethod(OrderReturnService.class, "hasReverseShipment", withAwb)).isTrue();
        assertThat((Boolean) ReflectionTestUtils.invokeMethod(OrderReturnService.class, "hasReverseShipment", new OrderReturn())).isFalse();
    }

    @Test
    void applyOutboundCourierSelection_skipsBlankIdAndSetsNameWhenProvided() {
        OrderReturn orderReturn = new OrderReturn();
        ReflectionTestUtils.invokeMethod(OrderReturnService.class, "applyOutboundCourierSelection", orderReturn, "  ", "Courier");
        assertThat(orderReturn.getPackageCourierId()).isNull();

        ReflectionTestUtils.invokeMethod(OrderReturnService.class, "applyOutboundCourierSelection", orderReturn, "42", " Fast Courier ");
        assertThat(orderReturn.getPackageCourierId()).isEqualTo("42");
        assertThat(orderReturn.getPackageCourierName()).isEqualTo("Fast Courier");
    }

    @Test
    void applyReturnLineInspection_emptyLines_isNoOp() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.INSPECTED);
        orderReturn.setId(1L);
        when(orderReturnItemRepository.findAllByOrderReturnIdOrderById(1L)).thenReturn(List.of());

        ReflectionTestUtils.invokeMethod(
            orderReturnService,
            "applyReturnLineInspection",
            orderReturn,
            ReturnDisposition.RESTOCKABLE,
            List.of()
        );

        verify(orderReturnItemRepository, never()).saveAll(any());
    }

    @Test
    void applyReturnLineInspection_rejectsOutOfRangeAcceptedQty() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.INSPECTED);
        orderReturn.setId(2L);
        OrderReturnItem line = lineItem(10L, 2, BigDecimal.TEN);
        when(orderReturnItemRepository.findAllByOrderReturnIdOrderById(2L)).thenReturn(List.of(line));

        assertThatThrownBy(() ->
            ReflectionTestUtils.invokeMethod(
                orderReturnService,
                "applyReturnLineInspection",
                orderReturn,
                ReturnDisposition.RESTOCKABLE,
                List.of(new OrderReturnService.InspectionLineDecision(10L, 3, ReturnDisposition.RESTOCKABLE))
            )
        )
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("within the requested quantity");
    }

    @Test
    void applyReturnLineInspection_rejectsWhenNoUnitsAccepted() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.INSPECTED);
        orderReturn.setId(3L);
        OrderReturnItem line = lineItem(11L, 2, BigDecimal.TEN);
        when(orderReturnItemRepository.findAllByOrderReturnIdOrderById(3L)).thenReturn(List.of(line));

        assertThatThrownBy(() ->
            ReflectionTestUtils.invokeMethod(
                orderReturnService,
                "applyReturnLineInspection",
                orderReturn,
                ReturnDisposition.RESTOCKABLE,
                List.of(new OrderReturnService.InspectionLineDecision(11L, 0, ReturnDisposition.RESTOCKABLE))
            )
        )
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("At least one returned unit must be accepted");
    }

    @Test
    void applyReturnLineInspection_usesPerLineDispositionAndSkipsNullDecisionIds() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.INSPECTED);
        orderReturn.setId(4L);
        OrderReturnItem line = lineItem(12L, 2, new BigDecimal("100.00"));
        when(orderReturnItemRepository.findAllByOrderReturnIdOrderById(4L)).thenReturn(List.of(line));
        when(orderReturnItemRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        ReflectionTestUtils.invokeMethod(
            orderReturnService,
            "applyReturnLineInspection",
            orderReturn,
            ReturnDisposition.QUARANTINE,
            List.of(
                new OrderReturnService.InspectionLineDecision(null, 1, ReturnDisposition.RESTOCKABLE),
                new OrderReturnService.InspectionLineDecision(12L, 1, ReturnDisposition.RESTOCKABLE)
            )
        );

        assertThat(line.getAcceptedQty()).isEqualTo(1);
        assertThat(line.getDisposition()).isEqualTo(ReturnDisposition.RESTOCKABLE);
        assertThat(orderReturn.getRefundAmount()).isEqualByComparingTo("50.00");
    }

    @Test
    void adminSummary_skipsMalformedRowsAndNonOverdueCases() {
        java.util.List<Object[]> requestRows = new java.util.ArrayList<>();
        requestRows.add(null);
        requestRows.add(new Object[] { OrderRequestType.RETURN, 1L });
        when(orderReturnRepository.countGroupedByRequestType()).thenReturn(requestRows);
        when(orderReturnRepository.countGroupedByStatus()).thenReturn(
            java.util.Arrays.asList(new Object[] { "not-a-status", 1L }, new Object[] { OrderReturnStatus.COMPLETED, 3L })
        );
        when(orderReturnRepository.countByStatus(OrderReturnStatus.APPROVED)).thenReturn(0L);
        Instant recent = Instant.now();
        when(orderReturnRepository.findStatusAndRequestedAtByStatusNotIn(any())).thenReturn(
            List.<Object[]>of(new Object[] { OrderReturnStatus.REQUESTED, recent })
        );
        when(caseSlaSupport.isReturnOverdue(anyString(), eq(recent))).thenReturn(false);

        Map<String, Object> summary = orderReturnService.adminSummary();

        assertThat(summary.get("returns")).isEqualTo(1L);
        assertThat(summary.get("overdue")).isEqualTo(0L);
        assertThat(summary.get("closed")).isEqualTo(3L);
    }

    @Test
    void createReturnLineSnapshots_emptyOrderItems_setsMerchandiseRefund() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.REQUESTED);
        CustomerOrder order = orderReturn.getOrder();
        when(orderItemRepository.findAllByOrderId(order.getId())).thenReturn(List.of());

        ReflectionTestUtils.invokeMethod(orderReturnService, "createReturnLineSnapshots", orderReturn, order, List.of());

        assertThat(orderReturn.getRefundAmount()).isEqualByComparingTo(OrderReturnService.merchandiseRefund(order));
        verify(orderReturnItemRepository, never()).save(any());
    }

    @Test
    void createReturnLineSnapshots_duplicateLineSelection_throwsBadRequest() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.REQUESTED);
        CustomerOrder order = orderReturn.getOrder();
        OrderItem item = new OrderItem().id(30L).qty(2).price(BigDecimal.TEN);
        when(orderItemRepository.findAllByOrderId(order.getId())).thenReturn(List.of(item));

        assertThatThrownBy(() ->
            ReflectionTestUtils.invokeMethod(
                orderReturnService,
                "createReturnLineSnapshots",
                orderReturn,
                order,
                List.of(new OrderReturnService.ReturnLineSelection(30L, 1), new OrderReturnService.ReturnLineSelection(30L, 1))
            )
        )
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("more than once");
    }

    @Test
    void finalizeRefund_demoOrder_stubsLedgerlyWhenOptionalEmpty() {
        ApplicationProperties.Ledgerly ledgerly = new ApplicationProperties.Ledgerly();
        ledgerly.setSyncReturns(true);
        when(applicationProperties.getLedgerly()).thenReturn(ledgerly);
        when(applicationProperties.getZoho()).thenReturn(new ApplicationProperties.Zoho());

        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.ACCEPTED);
        orderReturn.getOrder().setRazorpayPaymentId("pay_demo_stub");
        when(razorpayRefundService.isDemoMode()).thenReturn(true);
        when(ledgerlyIntegrationClient.cancelOrder(any(), any(), any())).thenReturn(Optional.empty());
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);
        when(customerOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ReflectionTestUtils.invokeMethod(
            orderReturnService,
            "finalizeRefund",
            orderReturn,
            BigDecimal.TEN,
            null,
            false
        );

        assertThat(orderReturn.getLedgerlyCreditNoteId()).isEqualTo("cn_demo_" + ORDER_NUMBER);
    }

    @Test
    void finalizeRefund_exchangeSkipsLedgerlySync() {
        OrderReturn orderReturn = exchange(OrderReturnStatus.ACCEPTED);
        lenient().when(customerOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);

        ReflectionTestUtils.invokeMethod(
            orderReturnService,
            "finalizeRefund",
            orderReturn,
            BigDecimal.TEN,
            null,
            false
        );

        verify(ledgerlyIntegrationClient, never()).cancelOrder(any(), any(), any());
    }

    @Test
    void finalizeRefund_settlementRefundedAmount_countsTowardFullRefund() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.ACCEPTED);
        orderReturn.getOrder().setTotal(new BigDecimal("500.00"));
        orderReturn.setManualRefundRecordedAt(Instant.now());
        orderReturn.setManualRefundReference("UTR-1234");
        orderReturn.setSettlementStatus(ExchangeSettlementStatus.REFUNDED);
        orderReturn.setSettlementAmount(new BigDecimal("200.00"));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);
        when(customerOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ReflectionTestUtils.invokeMethod(
            orderReturnService,
            "finalizeRefund",
            orderReturn,
            new BigDecimal("300.00"),
            null,
            false
        );

        assertThat(orderReturn.getOrder().getPaymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    void finalizeRefund_restockOnRefund_restoresReturnLines() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.ACCEPTED);
        orderReturn.setId(40L);
        orderReturn.setDisposition(ReturnDisposition.RESTOCKABLE);
        OrderReturnItem line = new OrderReturnItem();
        line.setAcceptedQty(1);
        line.setDisposition(ReturnDisposition.RESTOCKABLE);
        line.setOrderItem(new OrderItem().productId("5").qty(1));
        Product product = new Product().id(5L).stock(2);
        when(orderReturnItemRepository.findAllByOrderReturnIdOrderById(40L)).thenReturn(List.of(line));
        when(productRepository.findOneForInventoryUpdate(5L)).thenReturn(Optional.of(product));
        when(productRepository.save(product)).thenReturn(product);
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);
        when(customerOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ReflectionTestUtils.invokeMethod(
            orderReturnService,
            "finalizeRefund",
            orderReturn,
            BigDecimal.TEN,
            null,
            true
        );

        assertThat(product.getStock()).isEqualTo(3);
    }

    @Test
    void executeRefund_collectedCod_throwsBadRequest() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.ACCEPTED);
        orderReturn.getOrder().setPaymentMethod("cod");
        orderReturn.getOrder().setPaymentStatus(PaymentStatus.PAID);

        assertThatThrownBy(() ->
            ReflectionTestUtils.invokeMethod(
                orderReturnService,
                "executeRefund",
                orderReturn,
                BigDecimal.TEN,
                "notes"
            )
        )
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Collected COD refund");
    }

    @Test
    void executeRefund_uncollectedCodExchange_finalizesWithoutRazorpay() {
        OrderReturn orderReturn = exchange(OrderReturnStatus.ACCEPTED);
        orderReturn.getOrder().setPaymentMethod("cod");
        orderReturn.getOrder().setPaymentStatus(PaymentStatus.PENDING);
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);

        OrderReturn saved = ReflectionTestUtils.invokeMethod(
            orderReturnService,
            "executeRefund",
            orderReturn,
            BigDecimal.TEN,
            "cod refund"
        );

        assertThat(saved.getStatus()).isEqualTo(OrderReturnStatus.REFUNDED);
        verify(razorpayRefundService, never()).refundPayment(any(), any(), any());
    }

    @Test
    void executeRefund_razorpayFailure_marksFailedWhenNoPriorRefundId() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.ACCEPTED);
        when(razorpayRefundService.findExistingRefundForOrder(any(), any(), any())).thenReturn(Optional.empty());
        when(razorpayRefundService.refundPayment(any(), any(), any())).thenThrow(new RuntimeException("gateway down"));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);

        assertThatThrownBy(() ->
            ReflectionTestUtils.invokeMethod(orderReturnService, "executeRefund", orderReturn, BigDecimal.TEN, "fail")
        )
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("gateway down");

        assertThat(orderReturn.getStatus()).isEqualTo(OrderReturnStatus.FAILED);
        verify(orderMailService).sendReturnUpdate(any(), eq(orderReturn));
    }

    @Test
    void issueReplacementInvoice_successAndFailurePaths() {
        ApplicationProperties.Zoho zoho = new ApplicationProperties.Zoho();
        zoho.setSyncReturns(true);
        when(applicationProperties.getZoho()).thenReturn(zoho);
        OrderReturn orderReturn = exchange(OrderReturnStatus.ACCEPTED);
        when(
            zohoBooksIntegrationClient.createExchangeReplacementInvoice(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                anyInt(),
                anyList()
            )
        )
            .thenReturn(Optional.of("inv-99"));

        ReflectionTestUtils.invokeMethod(orderReturnService, "issueReplacementInvoice", orderReturn);
        assertThat(orderReturn.getZohoReplacementInvoiceId()).isEqualTo("inv-99");

        OrderReturn failing = exchange(OrderReturnStatus.ACCEPTED);
        when(
            zohoBooksIntegrationClient.createExchangeReplacementInvoice(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                anyInt(),
                anyList()
            )
        )
            .thenThrow(new RuntimeException("invoice failed"));

        ReflectionTestUtils.invokeMethod(orderReturnService, "issueReplacementInvoice", failing);
        assertThat(failing.getAdminNotes()).contains("Zoho replacement invoice failed");
    }

    @Test
    void issueExchangeCreditNote_zohoSuccess_persistsCreditNoteId() {
        ApplicationProperties.Zoho zoho = new ApplicationProperties.Zoho();
        zoho.setSyncReturns(true);
        when(applicationProperties.getZoho()).thenReturn(zoho);
        OrderReturn orderReturn = exchange(OrderReturnStatus.ACCEPTED);
        orderReturn.setOriginalItemAmount(new BigDecimal("250.00"));
        when(zohoBooksIntegrationClient.createCreditNote(any(), any(), anyMap(), anyBoolean())).thenReturn(Optional.of("cn-zoho-1"));

        ReflectionTestUtils.invokeMethod(orderReturnService, "issueExchangeCreditNote", orderReturn, false);

        assertThat(orderReturn.getZohoCreditNoteId()).isEqualTo("cn-zoho-1");
    }

    @Test
    void returnedQuantitiesForZoho_mergesAcceptedQtyForSameOrderItem() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.ACCEPTED);
        orderReturn.setId(50L);
        OrderItem shared = new OrderItem().id(900L);
        OrderReturnItem first = new OrderReturnItem();
        first.setAcceptedQty(1);
        first.setOrderItem(shared);
        OrderReturnItem second = new OrderReturnItem();
        second.setAcceptedQty(2);
        second.setOrderItem(shared);
        when(orderReturnItemRepository.findAllByOrderReturnIdOrderById(50L)).thenReturn(List.of(first, second));

        @SuppressWarnings("unchecked")
        Map<Long, Integer> qty = ReflectionTestUtils.invokeMethod(orderReturnService, "returnedQuantitiesForZoho", orderReturn);

        assertThat(qty).containsEntry(900L, 3);
    }

    @Test
    void normalizeReturnImageUrls_rejectsNonHttpAndDedupes() {
        ApplicationProperties.Storage storage = new ApplicationProperties.Storage();
        storage.getS3().setPublicBaseUrl("https://cdn.example.com/bucket");
        when(applicationProperties.getStorage()).thenReturn(storage);

        assertThatThrownBy(() ->
            ReflectionTestUtils.invokeMethod(
                orderReturnService,
                "normalizeReturnImageUrls",
                List.of("ftp://cdn.example/returns/x.jpg")
            )
        )
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("http or https");

        @SuppressWarnings("unchecked")
        List<String> normalized = ReflectionTestUtils.invokeMethod(
            orderReturnService,
            "normalizeReturnImageUrls",
            List.of(
                "https://cdn.example.com/bucket/returns/a.jpg",
                "https://cdn.example.com/bucket/returns/a.jpg",
                "https://cdn.example.com/bucket/returns/b.jpg"
            )
        );
        assertThat(normalized).containsExactly(
            "https://cdn.example.com/bucket/returns/a.jpg",
            "https://cdn.example.com/bucket/returns/b.jpg"
        );
    }

    @Test
    void normalizeReturnImageUrls_rejectsNonReturnObjectKeyWhenPublicBaseConfigured() {
        ApplicationProperties.Storage storage = new ApplicationProperties.Storage();
        storage.getS3().setPublicBaseUrl("https://cdn.example.com/bucket");
        when(applicationProperties.getStorage()).thenReturn(storage);

        assertThatThrownBy(() ->
            ReflectionTestUtils.invokeMethod(
                orderReturnService,
                "normalizeReturnImageUrls",
                List.of("https://cdn.example.com/bucket/catalog/not-return.jpg")
            )
        )
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("uploaded for this request");
    }

    @Test
    void parseImageUrls_skipsNonTextualArrayEntries() {
        @SuppressWarnings("unchecked")
        List<String> urls = ReflectionTestUtils.invokeMethod(OrderReturnService.class, "parseImageUrls", "[1, \"https://ok.example/r.jpg\"]");
        assertThat(urls).containsExactly("https://ok.example/r.jpg");
    }

    @Test
    void restoreItemStock_variantMatch_incrementsVariantNotParentFallback() {
        Product product = variantProduct(7L, 2);
        OrderItem item = new OrderItem().productId("7").variantSku("TEE-M-BLUE").itemSize("M").color("Blue").qty(1);
        when(productRepository.findOneForInventoryUpdate(7L)).thenReturn(Optional.of(product));
        when(productRepository.save(product)).thenReturn(product);

        Long id = ReflectionTestUtils.invokeMethod(orderReturnService, "restoreItemStock", item, 1);

        assertThat(id).isEqualTo(7L);
        assertThat(product.getStock()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void restoreItemStock_variantMiss_fallsBackToParentStock() {
        Product product = variantProduct(8L, 4);
        OrderItem item = new OrderItem().productId("8").variantSku("UNKNOWN").qty(2);
        when(productRepository.findOneForInventoryUpdate(8L)).thenReturn(Optional.of(product));
        when(productRepository.save(product)).thenReturn(product);

        ReflectionTestUtils.invokeMethod(orderReturnService, "restoreItemStock", item, 2);

        assertThat(product.getStock()).isEqualTo(6);
    }

    @Test
    void reverseItemStock_variantProduct_decrementsWhenStockAvailable() {
        Product product = variantProduct(9L, 5);
        OrderItem item = new OrderItem().productId("9").variantSku("TEE-M-BLUE").itemSize("M").color("Blue").qty(2);
        when(productRepository.findOneForInventoryUpdate(9L)).thenReturn(Optional.of(product));
        when(productRepository.save(product)).thenReturn(product);

        Boolean ok = ReflectionTestUtils.invokeMethod(orderReturnService, "reverseItemStock", item);

        assertThat(ok).isTrue();
    }

    @Test
    void restoreExchangeOriginalIfRestockable_exchangeRestockable_restocksOnce() {
        OrderReturn orderReturn = exchange(OrderReturnStatus.ACCEPTED);
        orderReturn.setDisposition(ReturnDisposition.RESTOCKABLE);
        Product product = new Product().id(2L).stock(1);
        when(productRepository.findOneForInventoryUpdate(2L)).thenReturn(Optional.of(product));
        when(productRepository.save(product)).thenReturn(product);

        ReflectionTestUtils.invokeMethod(orderReturnService, "restoreExchangeOriginalIfRestockable", orderReturn);

        assertThat(orderReturn.getOriginalRestockedAt()).isNotNull();
        assertThat(product.getStock()).isEqualTo(2);
    }

    @Test
    void syncZohoInventoryAfterExchangeStockChange_failurePersistsAdminNoteOnReturn() {
        ApplicationProperties.Zoho zoho = new ApplicationProperties.Zoho();
        zoho.setSyncProductStock(true);
        when(applicationProperties.getZoho()).thenReturn(zoho);
        OrderReturn orderReturn = exchange(OrderReturnStatus.ACCEPTED);
        orderReturn.setReturnNumber("RET-SYNC");
        OrderReturn persisted = exchange(OrderReturnStatus.ACCEPTED);
        persisted.setReturnNumber("RET-SYNC");
        when(zohoBooksIntegrationClient.syncProductStockOnly(2L)).thenThrow(new RuntimeException("sync boom"));
        when(orderReturnRepository.findOneByReturnNumber("RET-SYNC")).thenReturn(Optional.of(persisted));
        when(orderReturnRepository.save(persisted)).thenReturn(persisted);

        ReflectionTestUtils.invokeMethod(orderReturnService, "syncZohoInventoryAfterExchangeStockChange", orderReturn, 2L);

        assertThat(orderReturn.getAdminNotes()).contains("Zoho stock sync failed");
        assertThat(persisted.getAdminNotes()).contains("Zoho stock sync failed");
        verify(orderReturnRepository).save(persisted);
    }

    @Test
    void syncExchangeProductStockToZoho_nullOrderReturnOrItem_isNoOp() {
        ReflectionTestUtils.invokeMethod(orderReturnService, "syncExchangeProductStockToZoho", (OrderReturn) null);
        OrderReturn noItem = exchange(OrderReturnStatus.ACCEPTED);
        noItem.setOrderItem(null);
        ReflectionTestUtils.invokeMethod(orderReturnService, "syncExchangeProductStockToZoho", noItem);
        verify(zohoBooksIntegrationClient, never()).syncProductStockOnly(any());
    }

    @Test
    void releaseExchangeReservation_withoutImmediateZohoSync_savesProductOnly() {
        OrderReturn orderReturn = exchange(OrderReturnStatus.APPROVED);
        orderReturn.setReplacementReservedAt(Instant.now());
        Product product = variantProduct(2L, 1);
        when(productRepository.findOneForInventoryUpdate(2L)).thenReturn(Optional.of(product));
        when(productRepository.save(product)).thenReturn(product);

        ReflectionTestUtils.invokeMethod(orderReturnService, "releaseExchangeReservation", orderReturn, false);

        assertThat(orderReturn.getReplacementReservationReleasedAt()).isNotNull();
        verify(productRepository).save(product);
        verify(zohoBooksIntegrationClient, never()).syncProductStockOnly(any());
    }

    @Test
    void closePickupFailed_copiesPickupFailureWhenRejectionBlank() {
        OrderReturn orderReturn = exchange(OrderReturnStatus.PICKUP_FAILED);
        orderReturn.setPickupFailureReason("Customer unavailable");
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(orderReturnRepository.save(any(OrderReturn.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderReturn closed = orderReturnService.closePickupFailed("RET-1", "Closing");

        assertThat(closed.getRejectionReason()).isEqualTo("Customer unavailable");
    }

    @Test
    void markShipped_xpressBeesExchange_doesNotNotifyOutForDelivery() {
        OrderReturn orderReturn = packedExchange(OrderReturnStatus.READY_TO_SHIP);
        orderReturn.setOutboundAwb("AWB-OUT");
        orderReturn.setOutboundLogisticsMode(ReturnLogisticsMode.XPRESSBEES);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);

        orderReturnService.markShipped("RET-1", null);

        verify(orderMailService, never()).sendExchangeOutForDelivery(any(), any());
    }

    @Test
    void queueReturnEmail_nullOrder_isNoOp() {
        ReflectionTestUtils.invokeMethod(
            orderReturnService,
            "queueReturnEmail",
            null,
            returnWithStatus(OrderReturnStatus.REQUESTED)
        );
        verify(orderMailService, never()).sendReturnUpdate(any(), any());
    }

    @Test
    void resolveRefundAmount_sumsPersistedLineRefunds() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.ACCEPTED);
        orderReturn.setId(60L);
        orderReturn.setRefundAmount(null);
        OrderReturnItem line = new OrderReturnItem();
        line.setRefundAmount(new BigDecimal("45.50"));
        when(orderReturnItemRepository.findAllByOrderReturnIdOrderById(60L)).thenReturn(List.of(line));

        BigDecimal amount = ReflectionTestUtils.invokeMethod(orderReturnService, "resolveRefundAmount", orderReturn);

        assertThat(amount).isEqualByComparingTo("45.50");
    }

    @Test
    void isReplacementSellable_trueForMatchingVariant() {
        CustomerOrder order = eligibleOrder();
        OrderItem item = new OrderItem().id(3L).productId("2").order(order);
        Product product = variantProduct(2L, 3);
        when(orderItemRepository.findById(3L)).thenReturn(Optional.of(item));
        when(productRepository.findById(2L)).thenReturn(Optional.of(product));

        assertThat(orderReturnService.isReplacementSellable(USER, 3L, "M", "Blue")).isTrue();
    }

    @Test
    void recordManualCodRefund_nullMethodStillRejected() {
        assertThatThrownBy(() ->
            orderReturnService.recordManualCodRefund("RET-1", null, "REF-1234", null, "admin")
        )
            .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void processRefund_exchangeRequestType_throwsUseConvertToRefund() {
        OrderReturn orderReturn = exchange(OrderReturnStatus.ACCEPTED);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));

        assertThatThrownBy(() -> orderReturnService.processRefund("RET-1", null))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("convert-to-refund");
    }

    @Test
    void finalizeRefund_persistsZohoAndLedgerlyCreditNoteIds() {
        ApplicationProperties.Ledgerly ledgerly = new ApplicationProperties.Ledgerly();
        ledgerly.setSyncReturns(true);
        ApplicationProperties.Zoho zoho = new ApplicationProperties.Zoho();
        zoho.setSyncReturns(true);
        when(applicationProperties.getLedgerly()).thenReturn(ledgerly);
        when(applicationProperties.getZoho()).thenReturn(zoho);

        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.ACCEPTED);
        orderReturn.setRazorpayRefundId("rfnd_live");
        when(ledgerlyIntegrationClient.cancelOrder(any(), any(), any())).thenReturn(Optional.of("cn-ledgerly-live"));
        when(zohoBooksIntegrationClient.createCreditNote(eq(10L), eq("RET-1"), anyMap())).thenReturn(Optional.of("cn-zoho-live"));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);
        when(customerOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ReflectionTestUtils.invokeMethod(
            orderReturnService,
            "finalizeRefund",
            orderReturn,
            BigDecimal.TEN,
            null,
            false
        );

        assertThat(orderReturn.getLedgerlyCreditNoteId()).isEqualTo("cn-ledgerly-live");
        assertThat(orderReturn.getZohoCreditNoteId()).isEqualTo("cn-zoho-live");
    }

    @Test
    void finalizeRefund_nonDemoEmptyOptional_doesNotStubCreditNotes() {
        ApplicationProperties.Ledgerly ledgerly = new ApplicationProperties.Ledgerly();
        ledgerly.setSyncReturns(true);
        ApplicationProperties.Zoho zoho = new ApplicationProperties.Zoho();
        zoho.setSyncReturns(true);
        when(applicationProperties.getLedgerly()).thenReturn(ledgerly);
        when(applicationProperties.getZoho()).thenReturn(zoho);
        when(ledgerlyIntegrationClient.cancelOrder(any(), any(), any())).thenReturn(Optional.empty());
        when(zohoBooksIntegrationClient.createCreditNote(any(), any(), anyMap())).thenReturn(Optional.empty());

        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.ACCEPTED);
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

        assertThat(orderReturn.getLedgerlyCreditNoteId()).isNull();
        assertThat(orderReturn.getZohoCreditNoteId()).isNull();
    }

    @Test
    void finalizeRefund_matchingRazorpayAndSettlement_usesPartialRefundStatus() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.ACCEPTED);
        orderReturn.getOrder().setTotal(new BigDecimal("500.00"));
        orderReturn.setRazorpayRefundId("rfnd_same");
        orderReturn.setSettlementReference("rfnd_same");
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);
        when(customerOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ReflectionTestUtils.invokeMethod(
            orderReturnService,
            "finalizeRefund",
            orderReturn,
            new BigDecimal("100.00"),
            null,
            false
        );

        assertThat(orderReturn.getOrder().getPaymentStatus()).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);
    }

    @Test
    void applyReturnLineInspection_negativeAcceptedQty_throwsBadRequest() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.INSPECTED);
        orderReturn.setId(6L);
        OrderReturnItem line = lineItem(20L, 2, BigDecimal.TEN);
        when(orderReturnItemRepository.findAllByOrderReturnIdOrderById(6L)).thenReturn(List.of(line));

        assertThatThrownBy(() ->
            ReflectionTestUtils.invokeMethod(
                orderReturnService,
                "applyReturnLineInspection",
                orderReturn,
                ReturnDisposition.RESTOCKABLE,
                List.of(new OrderReturnService.InspectionLineDecision(20L, -1, ReturnDisposition.RESTOCKABLE))
            )
        )
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("within the requested quantity");
    }

    @Test
    void applyReturnLineInspection_nullDecisions_usesRequestedQtyAndDefaultDisposition() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.INSPECTED);
        orderReturn.setId(7L);
        OrderReturnItem line = lineItem(21L, 2, new BigDecimal("80.00"));
        when(orderReturnItemRepository.findAllByOrderReturnIdOrderById(7L)).thenReturn(List.of(line));
        when(orderReturnItemRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        ReflectionTestUtils.invokeMethod(
            orderReturnService,
            "applyReturnLineInspection",
            orderReturn,
            ReturnDisposition.RESTOCKABLE,
            null
        );

        assertThat(line.getAcceptedQty()).isEqualTo(2);
        assertThat(line.getDisposition()).isEqualTo(ReturnDisposition.RESTOCKABLE);
        assertThat(orderReturn.getRefundAmount()).isEqualByComparingTo("80.00");
    }

    @Test
    void createReturnLineSnapshots_selectedLine_persistsReturnItem() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.REQUESTED);
        CustomerOrder order = orderReturn.getOrder();
        OrderItem item = new OrderItem().id(31L).qty(2).price(new BigDecimal("100.00")).discountAllocation(BigDecimal.ZERO);
        when(orderItemRepository.findAllByOrderId(order.getId())).thenReturn(List.of(item));
        when(orderReturnItemRepository.save(any(OrderReturnItem.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);

        ReflectionTestUtils.invokeMethod(
            orderReturnService,
            "createReturnLineSnapshots",
            orderReturn,
            order,
            List.of(new OrderReturnService.ReturnLineSelection(31L, 1))
        );

        verify(orderReturnItemRepository).save(any(OrderReturnItem.class));
        assertThat(orderReturn.getRefundAmount()).isEqualByComparingTo("100.00");
        assertThat(orderReturn.getOrderItem()).isSameAs(item);
    }

    @Test
    void createReturnLineSnapshots_exceedsOrderedQty_throwsBadRequest() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.REQUESTED);
        CustomerOrder order = orderReturn.getOrder();
        OrderItem item = new OrderItem().id(32L).qty(1).price(BigDecimal.TEN);
        when(orderItemRepository.findAllByOrderId(order.getId())).thenReturn(List.of(item));

        assertThatThrownBy(() ->
            ReflectionTestUtils.invokeMethod(
                orderReturnService,
                "createReturnLineSnapshots",
                orderReturn,
                order,
                List.of(new OrderReturnService.ReturnLineSelection(32L, 2))
            )
        )
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("exceeds the ordered quantity");
    }

    @Test
    void normalizeReturnImageUrls_nullOrEmpty_returnsEmptyList() {
        @SuppressWarnings("unchecked")
        List<String> empty = ReflectionTestUtils.invokeMethod(orderReturnService, "normalizeReturnImageUrls", List.of());
        @SuppressWarnings("unchecked")
        List<String> nullList = ReflectionTestUtils.invokeMethod(orderReturnService, "normalizeReturnImageUrls", (List<String>) null);
        assertThat(empty).isEmpty();
        assertThat(nullList).isEmpty();
    }

    @Test
    void returnedQuantitiesForZoho_ignoresZeroAcceptedQty() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.ACCEPTED);
        orderReturn.setId(52L);
        OrderReturnItem line = new OrderReturnItem();
        line.setAcceptedQty(0);
        line.setOrderItem(new OrderItem().id(901L).qty(1));
        when(orderReturnItemRepository.findAllByOrderReturnIdOrderById(52L)).thenReturn(List.of(line));

        @SuppressWarnings("unchecked")
        Map<Long, Integer> qty = ReflectionTestUtils.invokeMethod(orderReturnService, "returnedQuantitiesForZoho", orderReturn);

        assertThat(qty).isEmpty();
    }

    @Test
    void issueReplacementInvoice_nullOrderOrOrderId_skipsZohoCall() {
        ApplicationProperties.Zoho zoho = new ApplicationProperties.Zoho();
        zoho.setSyncReturns(true);
        lenient().when(applicationProperties.getZoho()).thenReturn(zoho);

        OrderReturn missingOrder = exchange(OrderReturnStatus.ACCEPTED);
        missingOrder.setOrder(null);
        ReflectionTestUtils.invokeMethod(orderReturnService, "issueReplacementInvoice", missingOrder);

        OrderReturn missingOrderId = exchange(OrderReturnStatus.ACCEPTED);
        missingOrderId.getOrder().setId(null);
        ReflectionTestUtils.invokeMethod(orderReturnService, "issueReplacementInvoice", missingOrderId);

        verify(zohoBooksIntegrationClient, never()).createExchangeReplacementInvoice(
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            anyInt(),
            anyList()
        );
    }

    @Test
    void executeRefund_existingRefundId_skipsRazorpayAndRestock() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.ACCEPTED);
        orderReturn.setRazorpayRefundId("rfnd_existing");
        orderReturn.setDisposition(ReturnDisposition.RESTOCKABLE);
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);
        when(customerOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ReflectionTestUtils.invokeMethod(orderReturnService, "executeRefund", orderReturn, BigDecimal.TEN, "retry");

        verify(razorpayRefundService, never()).refundPayment(any(), any(), any());
        verify(productRepository, never()).findOneForInventoryUpdate(any());
    }

    private OrderReturnItem lineItem(Long id, int requestedQty, BigDecimal refund) {
        OrderReturnItem item = new OrderReturnItem();
        item.setId(id);
        item.setRequestedQty(requestedQty);
        item.setRefundAmount(refund);
        return item;
    }

    private Product variantProduct(long id, int stock) {
        return new Product()
            .id(id)
            .itemStructure("VARIANTS")
            .stock(stock)
            .variantsJson(
                "[{\"sku\":\"TEE-M-BLUE\",\"size\":\"M\",\"color\":\"Blue\",\"rate\":500,\"stock\":" +
                stock +
                ",\"active\":true}]"
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
}
