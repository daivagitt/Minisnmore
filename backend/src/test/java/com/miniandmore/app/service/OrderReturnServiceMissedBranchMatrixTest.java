package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

/** Focused matrices for the remaining JaCoCo branches in {@link OrderReturnService}. */
@ExtendWith(MockitoExtension.class)
class OrderReturnServiceMissedBranchMatrixTest {

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
    private OrderReturnService service;

    @BeforeEach
    void setUp() {
        lenient().when(applicationProperties.getZoho()).thenReturn(new ApplicationProperties.Zoho());
        lenient().when(applicationProperties.getLedgerly()).thenReturn(new ApplicationProperties.Ledgerly());
        lenient().when(applicationProperties.getXpressbees()).thenReturn(new ApplicationProperties.XpressBees());
        ReflectionTestUtils.setField(
            service,
            "shipmentFactory",
            new ReturnXpressBeesShipmentFactory(
                applicationProperties,
                new ObjectMapper(),
                orderReturnItemRepository,
                storeAddressRepository
            )
        );
    }

    @Test
    void createReturnLineSnapshots_coversNullSelectionListNullEntryAndExistingPrimaryItem() {
        CustomerOrder order = order();
        OrderItem first = new OrderItem().id(11L).qty(null).price(BigDecimal.TEN);
        OrderItem alreadyPrimary = new OrderItem().id(99L);
        OrderReturn orderReturn = exchange();
        orderReturn.setOrderItem(alreadyPrimary);
        when(orderItemRepository.findAllByOrderId(order.getId())).thenReturn(List.of(first));
        when(orderReturnItemRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);

        ReflectionTestUtils.invokeMethod(service, "createReturnLineSnapshots", orderReturn, order, null);

        assertThat(orderReturn.getOrderItem()).isSameAs(alreadyPrimary);
        assertThat(orderReturn.getRefundAmount()).isEqualByComparingTo("10.00");

        assertThatThrownBy(() ->
            ReflectionTestUtils.invokeMethod(
                service,
                "createReturnLineSnapshots",
                new OrderReturn(),
                order,
                java.util.Collections.singletonList(null)
            )
        )
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("positive quantity");
    }

    @Test
    void issueExchangeCreditNote_coversMissingOrderIdAndExistingIntegrationIds() {
        OrderReturn missingId = exchange();
        missingId.getOrder().setId(null);
        ReflectionTestUtils.invokeMethod(service, "issueExchangeCreditNote", missingId, false);

        ApplicationProperties.Ledgerly ledgerly = new ApplicationProperties.Ledgerly();
        ledgerly.setSyncReturns(true);
        ApplicationProperties.Zoho zoho = new ApplicationProperties.Zoho();
        zoho.setSyncReturns(true);
        when(applicationProperties.getLedgerly()).thenReturn(ledgerly);
        when(applicationProperties.getZoho()).thenReturn(zoho);
        OrderReturn existing = exchange();
        existing.setLedgerlyCreditNoteId("ledgerly-existing");
        existing.setZohoCreditNoteId("zoho-existing");

        ReflectionTestUtils.invokeMethod(service, "issueExchangeCreditNote", existing, true);

        verify(ledgerlyIntegrationClient, never()).createCreditNote(any(), any(), any());
        verify(zohoBooksIntegrationClient, never()).createCreditNote(any(), any(), anyMap(), anyBoolean());
    }

    @Test
    void issueReplacementInvoice_coversExistingIdMissingItemIdAndNullOptional() {
        ApplicationProperties.Zoho zoho = new ApplicationProperties.Zoho();
        zoho.setSyncReturns(true);
        when(applicationProperties.getZoho()).thenReturn(zoho);

        OrderReturn existing = exchange();
        existing.setZohoReplacementInvoiceId("invoice-existing");
        ReflectionTestUtils.invokeMethod(service, "issueReplacementInvoice", existing);

        OrderReturn missingItemId = exchange();
        missingItemId.getOrderItem().setId(null);
        ReflectionTestUtils.invokeMethod(service, "issueReplacementInvoice", missingItemId);

        OrderReturn nullResponse = exchange();
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
            .thenReturn(null);
        ReflectionTestUtils.invokeMethod(service, "issueReplacementInvoice", nullResponse);

        assertThat(nullResponse.getZohoReplacementInvoiceId()).isNull();
    }

    @Test
    void outboundOrderAmount_coversAllFallbackOperands() {
        OrderReturn replacementZero = exchange();
        replacementZero.setReplacementItemAmount(BigDecimal.ZERO);
        replacementZero.getOrderItem().setPrice(null);
        replacementZero.getOrder().setTotal(new BigDecimal("77.00"));
        assertThat(amount(replacementZero)).isEqualByComparingTo("77.00");

        OrderReturn noItem = exchange();
        noItem.setOrderItem(null);
        noItem.setOrder(null);
        assertThat(amount(noItem)).isZero();

        OrderReturn noTotal = exchange();
        noTotal.setOrderItem(null);
        noTotal.getOrder().setTotal(null);
        assertThat(amount(noTotal)).isZero();

        OrderReturn nullQty = exchange();
        nullQty.getOrderItem().setQty(null);
        nullQty.getOrderItem().setPrice(new BigDecimal("12.00"));
        assertThat(amount(nullQty)).isEqualByComparingTo("12.00");

        OrderReturn zeroQty = exchange();
        zeroQty.getOrderItem().setQty(0);
        zeroQty.getOrderItem().setPrice(new BigDecimal("12.00"));
        assertThat(amount(zeroQty)).isEqualByComparingTo("12.00");
    }

    @Test
    void resolveOutboundWeight_coversNonPositiveArgumentsAndPackageWeight() {
        OrderReturn orderReturn = exchange();
        orderReturn.setPackageWeightGrams(640);
        assertThat(weight(orderReturn, 0)).isEqualTo(640);
        assertThat(weight(orderReturn, -1)).isEqualTo(640);

        orderReturn.setPackageWeightGrams(0);
        ApplicationProperties.XpressBees xpressBees = new ApplicationProperties.XpressBees();
        xpressBees.setDefaultWeightGrams(225);
        when(applicationProperties.getXpressbees()).thenReturn(xpressBees);
        assertThat(weight(orderReturn, null)).isEqualTo(225);
    }

    @Test
    void canBookOutbound_shortCircuitsEachLateGuard() {
        assertThat(service.canBookOutbound(plainReturn())).isFalse();
        assertThat(service.canCompletePackage(plainReturn())).isFalse();

        OrderReturn orderReturn = packedExchange();
        orderReturn.setStatus(OrderReturnStatus.REPACKING);
        assertThat(service.canBookOutbound(orderReturn)).isFalse();

        orderReturn.setStatus(OrderReturnStatus.READY_TO_SHIP);
        orderReturn.setSettlementStatus(ExchangeSettlementStatus.PENDING);
        assertThat(service.canBookOutbound(orderReturn)).isFalse();

        orderReturn.setSettlementStatus(ExchangeSettlementStatus.NOT_REQUIRED);
        orderReturn.setOutboundAwb("already-booked");
        assertThat(service.canBookOutbound(orderReturn)).isFalse();
    }

    @Test
    void packageCompletion_coversZeroBreadthAndZeroHeight() {
        OrderReturn orderReturn = packedExchange();
        orderReturn.setPackageLengthCm(0);
        assertThat(OrderReturnService.isPackageCompleted(orderReturn)).isFalse();

        orderReturn.setPackageLengthCm(10);
        orderReturn.setPackageBreadthCm(0);
        assertThat(OrderReturnService.isPackageCompleted(orderReturn)).isFalse();

        orderReturn.setPackageBreadthCm(10);
        orderReturn.setPackageHeightCm(0);
        assertThat(OrderReturnService.isPackageCompleted(orderReturn)).isFalse();
    }

    @Test
    void priorCreditNotesAndCourierSelection_coverNullOptionalFields() {
        CustomerOrder transientOrder = order();
        transientOrder.setId(null);
        @SuppressWarnings("unchecked")
        List<String> ids = ReflectionTestUtils.invokeMethod(service, "priorCreditNoteIds", transientOrder, exchange());
        assertThat(ids).isEmpty();

        OrderReturn orderReturn = exchange();
        ReflectionTestUtils.invokeMethod(OrderReturnService.class, "applyOutboundCourierSelection", orderReturn, " courier-1 ", null);
        assertThat(orderReturn.getPackageCourierId()).isEqualTo("courier-1");
        assertThat(orderReturn.getPackageCourierName()).isNull();
    }

    @Test
    void normalizeReturnImageUrls_coversNoStorageBlankHttpLengthAndLimitBranches() {
        when(applicationProperties.getStorage()).thenReturn(null);
        @SuppressWarnings("unchecked")
        List<String> urls = ReflectionTestUtils.invokeMethod(
            service,
            "normalizeReturnImageUrls",
            java.util.Arrays.asList(null, "  ", "http://example.test/returns/a.jpg")
        );
        assertThat(urls).containsExactly("http://example.test/returns/a.jpg");

        assertThatThrownBy(() ->
            ReflectionTestUtils.invokeMethod(
                service,
                "normalizeReturnImageUrls",
                List.of("https://example.test/" + "x".repeat(2100))
            )
        )
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("too long");

        assertThatThrownBy(() ->
            ReflectionTestUtils.invokeMethod(
                service,
                "normalizeReturnImageUrls",
                List.of(
                    "https://example.test/returns/1.jpg",
                    "https://example.test/returns/2.jpg",
                    "https://example.test/returns/3.jpg",
                    "https://example.test/returns/4.jpg",
                    "https://example.test/returns/5.jpg",
                    "https://example.test/returns/6.jpg"
                )
            )
        )
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("up to");
    }

    @Test
    void returnedQuantitiesForZoho_coversMalformedLinesAndFallbackSuppression() {
        OrderReturn orderReturn = exchange();
        orderReturn.setId(44L);
        OrderReturnItem noOrderItem = new OrderReturnItem();
        OrderReturnItem noOrderItemId = new OrderReturnItem();
        noOrderItemId.setOrderItem(new OrderItem());
        OrderReturnItem zeroAccepted = new OrderReturnItem();
        zeroAccepted.setOrderItem(new OrderItem().id(20L));
        zeroAccepted.setAcceptedQty(null);
        when(orderReturnItemRepository.findAllByOrderReturnIdOrderById(44L))
            .thenReturn(List.of(noOrderItem, noOrderItemId, zeroAccepted));

        assertThat(returnedQuantities(orderReturn)).isEmpty();

        OrderReturn transientReturn = exchange();
        transientReturn.setId(null);
        transientReturn.getOrderItem().setQty(null);
        assertThat(returnedQuantities(transientReturn)).containsEntry(5L, 1);
    }

    @Test
    void restoreItemStock_coversNullBlankMissingProductAndNullAggregateStock() {
        assertThat(restoredId(null, 1)).isNull();
        assertThat(restoredId(new OrderItem().productId(" "), 1)).isNull();

        OrderItem missing = new OrderItem().productId("72");
        when(productRepository.findOneForInventoryUpdate(72L)).thenReturn(Optional.empty());
        assertThat(restoredId(missing, 1)).isNull();

        OrderItem simple = new OrderItem().productId("73");
        Product product = new Product().id(73L).itemStructure("SIMPLE").stock(null);
        when(productRepository.findOneForInventoryUpdate(73L)).thenReturn(Optional.of(product));
        when(productRepository.save(product)).thenReturn(product);
        assertThat(restoredId(simple, 2)).isEqualTo(73L);
        assertThat(product.getStock()).isEqualTo(2);
    }

    @Test
    void restoreRestockableStockOnRefund_coversNonReturnAndLineDecisionGuards() {
        OrderReturn unrelated = new OrderReturn();
        unrelated.setRequestType(null);
        ReflectionTestUtils.invokeMethod(service, "restoreRestockableStockOnRefund", unrelated);

        OrderReturn orderReturn = plainReturn();
        orderReturn.setId(50L);
        orderReturn.setDisposition(com.miniandmore.app.domain.enumeration.ReturnDisposition.RESTOCKABLE);
        OrderReturnItem rejected = new OrderReturnItem();
        rejected.setAcceptedQty(0);
        rejected.setDisposition(com.miniandmore.app.domain.enumeration.ReturnDisposition.RESTOCKABLE);
        rejected.setOrderItem(new OrderItem().productId("80"));
        OrderReturnItem quarantined = new OrderReturnItem();
        quarantined.setAcceptedQty(1);
        quarantined.setDisposition(com.miniandmore.app.domain.enumeration.ReturnDisposition.QUARANTINE);
        quarantined.setOrderItem(new OrderItem().productId("81"));
        when(orderReturnItemRepository.findAllByOrderReturnIdOrderById(50L)).thenReturn(List.of(rejected, quarantined));

        ReflectionTestUtils.invokeMethod(service, "restoreRestockableStockOnRefund", orderReturn);

        verify(productRepository, never()).findOneForInventoryUpdate(any());
    }

    private BigDecimal amount(OrderReturn orderReturn) {
        return ReflectionTestUtils.invokeMethod(service, "outboundOrderAmount", orderReturn);
    }

    private int weight(OrderReturn orderReturn, Integer supplied) {
        return ReflectionTestUtils.invokeMethod(service, "resolveOutboundWeight", orderReturn, supplied);
    }

    @SuppressWarnings("unchecked")
    private Map<Long, Integer> returnedQuantities(OrderReturn orderReturn) {
        return ReflectionTestUtils.invokeMethod(service, "returnedQuantitiesForZoho", orderReturn);
    }

    private Long restoredId(OrderItem item, int quantity) {
        return ReflectionTestUtils.invokeMethod(service, "restoreItemStock", item, quantity);
    }

    private CustomerOrder order() {
        return new CustomerOrder()
            .id(10L)
            .userLogin("matrix-user")
            .orderNumber("ORD-MATRIX")
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

    private OrderReturn plainReturn() {
        OrderReturn orderReturn = new OrderReturn();
        orderReturn.setReturnNumber("RET-MATRIX");
        orderReturn.setRequestType(OrderRequestType.RETURN);
        orderReturn.setStatus(OrderReturnStatus.ACCEPTED);
        orderReturn.setOrder(order());
        return orderReturn;
    }

    private OrderReturn exchange() {
        OrderReturn orderReturn = plainReturn();
        orderReturn.setRequestType(OrderRequestType.EXCHANGE);
        orderReturn.setOrderItem(new OrderItem().id(5L).productId("2").qty(1).price(BigDecimal.TEN));
        orderReturn.setReplacementVariantSku("SKU-MATRIX");
        orderReturn.setReplacementSize("M");
        orderReturn.setReplacementColor("Blue");
        return orderReturn;
    }

    private OrderReturn packedExchange() {
        OrderReturn orderReturn = exchange();
        orderReturn.setStatus(OrderReturnStatus.READY_TO_SHIP);
        orderReturn.setPackedAt(Instant.now());
        orderReturn.setPackageLengthCm(10);
        orderReturn.setPackageBreadthCm(10);
        orderReturn.setPackageHeightCm(10);
        orderReturn.setSettlementStatus(ExchangeSettlementStatus.NOT_REQUIRED);
        return orderReturn;
    }
}
