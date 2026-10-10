package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.miniandmore.app.client.LedgerlyIntegrationClient;
import com.miniandmore.app.client.XpressBeesClient;
import com.miniandmore.app.client.ZohoBooksIntegrationClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.config.ApplicationProperties;
import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.OrderItem;
import com.miniandmore.app.domain.OrderReturn;
import com.miniandmore.app.domain.OrderReturnItem;
import com.miniandmore.app.domain.Product;
import com.miniandmore.app.domain.enumeration.ExchangeSettlementStatus;
import com.miniandmore.app.domain.enumeration.ManualRefundMethod;
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
import com.miniandmore.app.domain.SiteSetting;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class OrderReturnServiceTest {

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
    private ProductVariantSupport variantSupport = StorePricingTestSupport.variantSupport(new com.fasterxml.jackson.databind.ObjectMapper());

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

    @org.junit.jupiter.api.BeforeEach
    void setUp() throws Exception {
        lenient().when(applicationProperties.getZoho()).thenReturn(new ApplicationProperties.Zoho());
        lenient().when(applicationProperties.getLedgerly()).thenReturn(new ApplicationProperties.Ledgerly());
        lenient().when(applicationProperties.getXpressbees()).thenReturn(new ApplicationProperties.XpressBees());
        lenient()
            .when(orderReturnRepository.findOneByReturnNumberForUpdate(any()))
            .thenAnswer(inv -> orderReturnRepository.findOneByReturnNumber(inv.getArgument(0)));
        lenient()
            .when(zohoBooksIntegrationClient.attachCreditNoteToSourceInvoice(any(), any(), any(), anyMap()))
            .thenAnswer(inv -> Optional.ofNullable(inv.getArgument(2)));
        lenient().when(objectMapper.createObjectNode()).thenReturn(new ObjectMapper().createObjectNode());
        lenient()
            .when(objectMapper.readTree(any(String.class)))
            .thenAnswer(inv -> {
                try {
                    return new ObjectMapper().readTree((String) inv.getArgument(0));
                } catch (Exception ex) {
                    return new ObjectMapper().createObjectNode();
                }
            });
    }

    private void enableLedgerlyReturnsSync() {
        ApplicationProperties.Ledgerly ledgerly = new ApplicationProperties.Ledgerly();
        ledgerly.setSyncReturns(true);
        when(applicationProperties.getLedgerly()).thenReturn(ledgerly);
    }

    @Test
    void requestReturn_shortReason_throwsBadRequest() {
        assertThatThrownBy(() -> orderReturnService.requestReturn(USER, ORDER_NUMBER, "too short"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("at least 10 characters");
    }

    @Test
    void requestReturn_orderNotFound_throwsNotFound() {
        when(customerOrderRepository.findOneByOrderNumberAndUserLogin(ORDER_NUMBER, USER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderReturnService.requestReturn(USER, ORDER_NUMBER, "Product arrived damaged"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Order not found");
    }

    @Test
    void requestReturn_eligibleOrder_persistsReturn() {
        CustomerOrder order = eligibleOrder();
        when(customerOrderRepository.findOneByOrderNumberAndUserLogin(ORDER_NUMBER, USER)).thenReturn(Optional.of(order));
        when(orderReturnRepository.existsByOrderIdAndStatusIn(eq(order.getId()), any())).thenReturn(false);
        when(orderReturnRepository.save(any(OrderReturn.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderReturn result = orderReturnService.requestReturn(USER, ORDER_NUMBER, "Product arrived damaged in transit");

        assertThat(result.getStatus()).isEqualTo(OrderReturnStatus.REQUESTED);
        assertThat(result.getUserLogin()).isEqualTo(USER);
        assertThat(result.getOrder()).isSameAs(order);
        assertThat(result.getReturnNumber()).startsWith("RET-" + ORDER_NUMBER);
        verify(orderReturnRepository).save(any(OrderReturn.class));
        verify(orderMailService).sendReturnUpdate(eq(order), any(OrderReturn.class));
    }

    @Test
    void requestReturn_withImages_persistsImageUrls() {
        CustomerOrder order = eligibleOrder();
        when(customerOrderRepository.findOneByOrderNumberAndUserLogin(ORDER_NUMBER, USER)).thenReturn(Optional.of(order));
        when(orderReturnRepository.existsByOrderIdAndStatusIn(eq(order.getId()), any())).thenReturn(false);
        when(orderReturnRepository.save(any(OrderReturn.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderReturn result = orderReturnService.requestReturn(
            USER,
            ORDER_NUMBER,
            "Product arrived damaged in transit",
            List.of(),
            List.of("https://cdn.example/returns/damage.jpg")
        );

        assertThat(result.getImageUrlsJson()).contains("https://cdn.example/returns/damage.jpg");
        assertThat(orderReturnService.imageUrls(result)).containsExactly("https://cdn.example/returns/damage.jpg");
    }

    @Test
    void requestReturn_rejectsImageUrlOutsideStorageWhenConfigured() {
        ApplicationProperties.Storage storage = new ApplicationProperties.Storage();
        storage.getS3().setPublicBaseUrl("https://cdn.example.com/my-bucket");
        when(applicationProperties.getStorage()).thenReturn(storage);
        CustomerOrder order = eligibleOrder();
        when(customerOrderRepository.findOneByOrderNumberAndUserLogin(ORDER_NUMBER, USER)).thenReturn(Optional.of(order));
        when(orderReturnRepository.existsByOrderIdAndStatusIn(eq(order.getId()), any())).thenReturn(false);

        assertThatThrownBy(() ->
            orderReturnService.requestReturn(
                USER,
                ORDER_NUMBER,
                "Product arrived damaged in transit",
                List.of(),
                List.of("https://evil.example/returns/fake.jpg")
            )
        )
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("uploaded for this request");
    }

    @Test
    void requestReturn_rejectsNonHttpImageUrl() {
        CustomerOrder order = eligibleOrder();
        when(customerOrderRepository.findOneByOrderNumberAndUserLogin(ORDER_NUMBER, USER)).thenReturn(Optional.of(order));
        when(orderReturnRepository.existsByOrderIdAndStatusIn(eq(order.getId()), any())).thenReturn(false);

        assertThatThrownBy(() ->
            orderReturnService.requestReturn(
                USER,
                ORDER_NUMBER,
                "Product arrived damaged in transit",
                List.of(),
                List.of("javascript:alert(1)")
            )
        )
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("http or https");
    }

    @Test
    void requestReturn_tooManyImages_throwsBadRequest() {
        CustomerOrder order = eligibleOrder();
        when(customerOrderRepository.findOneByOrderNumberAndUserLogin(ORDER_NUMBER, USER)).thenReturn(Optional.of(order));
        when(orderReturnRepository.existsByOrderIdAndStatusIn(eq(order.getId()), any())).thenReturn(false);

        assertThatThrownBy(() ->
            orderReturnService.requestReturn(
                USER,
                ORDER_NUMBER,
                "Product arrived damaged in transit",
                List.of(),
                List.of(
                    "https://cdn.example/1.jpg",
                    "https://cdn.example/2.jpg",
                    "https://cdn.example/3.jpg",
                    "https://cdn.example/4.jpg",
                    "https://cdn.example/5.jpg",
                    "https://cdn.example/6.jpg"
                )
            )
        )
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("up to 5 images");
    }

    @Test
    void requestReturn_mailFailure_stillPersists() {
        CustomerOrder order = eligibleOrder();
        when(customerOrderRepository.findOneByOrderNumberAndUserLogin(ORDER_NUMBER, USER)).thenReturn(Optional.of(order));
        when(orderReturnRepository.existsByOrderIdAndStatusIn(eq(order.getId()), any())).thenReturn(false);
        when(orderReturnRepository.save(any(OrderReturn.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new RuntimeException("smtp down")).when(orderMailService).sendReturnUpdate(any(), any());

        OrderReturn result = orderReturnService.requestReturn(USER, ORDER_NUMBER, "Product arrived damaged in transit");

        assertThat(result.getStatus()).isEqualTo(OrderReturnStatus.REQUESTED);
    }

    @Test
    void cancelByCustomer_nullOrder_skipsEmail() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.REQUESTED);
        orderReturn.setUserLogin(USER);
        orderReturn.setOrder(null);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);

        orderReturnService.cancelByCustomer(USER, "RET-1");

        verify(orderMailService, never()).sendReturnUpdate(any(), any());
    }

    @Test
    void cancelByCustomer_onlyRequestedStatusAllowed() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.APPROVED);
        orderReturn.setUserLogin(USER);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));

        assertThatThrownBy(() -> orderReturnService.cancelByCustomer(USER, "RET-1"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Only pending return requests");
    }

    @Test
    void cancelByCustomer_requestedStatus_cancelsReturn() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.REQUESTED);
        orderReturn.setUserLogin(USER);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);

        OrderReturn result = orderReturnService.cancelByCustomer(USER, "RET-1");

        assertThat(result.getStatus()).isEqualTo(OrderReturnStatus.CANCELLED);
        assertThat(result.getResolvedAt()).isNotNull();
        verify(orderMailService).sendReturnUpdate(any(), eq(orderReturn));
    }

    @Test
    void approve_requestedReturn_movesToApproved() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.REQUESTED);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);

        OrderReturn result = orderReturnService.approve("RET-1", "Approved by admin");

        assertThat(result.getStatus()).isEqualTo(OrderReturnStatus.APPROVED);
        assertThat(result.getAdminNotes()).isEqualTo("Approved by admin");
        verify(orderMailService).sendReturnUpdate(any(), eq(orderReturn));
    }

    @Test
    void eligibilityForUser_orderNotFound() {
        when(customerOrderRepository.findOneByOrderNumberAndUserLogin(ORDER_NUMBER, USER)).thenReturn(Optional.empty());

        Map<String, Object> result = orderReturnService.eligibilityForUser(USER, ORDER_NUMBER);

        assertThat(result.get("eligible")).isEqualTo(false);
        assertThat(result.get("message")).isEqualTo("Order not found");
    }

    @Test
    void eligibilityForUser_paidDeliveredOrder_isEligible() {
        CustomerOrder order = eligibleOrder();
        when(customerOrderRepository.findOneByOrderNumberAndUserLogin(ORDER_NUMBER, USER)).thenReturn(Optional.of(order));

        Map<String, Object> result = orderReturnService.eligibilityForUser(USER, ORDER_NUMBER);

        assertThat(result.get("eligible")).isEqualTo(true);
        assertThat(result.get("tracking_status")).isEqualTo("delivered");
        assertThat(result.get("delivered_at")).isEqualTo(order.getShipmentDeliveredAt());
        assertThat(result.get("return_deadline")).isEqualTo(order.getShipmentDeliveredAt().plus(3, ChronoUnit.DAYS));
        assertThat(result.get("window_days")).isEqualTo(3);
        assertThat(result.get("refund_min_business_days")).isEqualTo(2);
        assertThat(result.get("refund_max_business_days")).isEqualTo(5);
    }

    @Test
    void eligibilityForUser_usesConfiguredReturnWindow() {
        CustomerOrder order = eligibleOrder();
        when(customerOrderRepository.findOneByOrderNumberAndUserLogin(ORDER_NUMBER, USER)).thenReturn(Optional.of(order));
        when(siteSettingRepository.findFirstByOrderByIdAsc()).thenReturn(
            Optional.of(new SiteSetting().returnWindowDays(10).refundMinBusinessDays(3).refundMaxBusinessDays(6))
        );

        Map<String, Object> result = orderReturnService.eligibilityForUser(USER, ORDER_NUMBER);

        assertThat(result.get("eligible")).isEqualTo(true);
        assertThat(result.get("window_days")).isEqualTo(10);
        assertThat(result.get("return_deadline")).isEqualTo(order.getShipmentDeliveredAt().plus(10, ChronoUnit.DAYS));
        assertThat(result.get("refund_min_business_days")).isEqualTo(3);
        assertThat(result.get("refund_max_business_days")).isEqualTo(6);
    }

    @Test
    void resolveDeliveryAnchor_usesPhysicalDeliveryTimestamp() {
        Instant deliveredAt = Instant.parse("2026-09-28T12:30:00Z");
        CustomerOrder order = eligibleOrder().shipmentDeliveredAt(deliveredAt);

        Instant anchor = orderReturnService.resolveDeliveryAnchor(order);

        assertThat(anchor).isEqualTo(deliveredAt);
    }

    @Test
    void listForAdmin_withoutFilter_returnsAllStatuses() {
        List<OrderReturn> returns = List.of(returnWithStatus(OrderReturnStatus.REQUESTED));
        when(orderReturnRepository.findAllByStatusInOrderByRequestedAtDesc(any())).thenReturn(returns);

        List<OrderReturn> result = orderReturnService.listForAdmin(null);

        assertThat(result).isEqualTo(returns);
        verify(orderReturnRepository).findAllByStatusInOrderByRequestedAtDesc(List.of(OrderReturnStatus.values()));
    }

    @Test
    void listForAdmin_withStatusFilter() {
        List<OrderReturn> returns = List.of(returnWithStatus(OrderReturnStatus.APPROVED));
        when(orderReturnRepository.findAllByStatusInOrderByRequestedAtDesc(List.of(OrderReturnStatus.APPROVED))).thenReturn(returns);

        List<OrderReturn> result = orderReturnService.listForAdmin(OrderReturnStatus.APPROVED);

        assertThat(result).isEqualTo(returns);
    }

    @Test
    void adminSummary_countsReturnsExchangesStagesAndOverdue() {
        when(orderReturnRepository.countGroupedByRequestType()).thenReturn(
            List.of(new Object[] { OrderRequestType.RETURN, 8L }, new Object[] { OrderRequestType.EXCHANGE, 4L })
        );
        when(orderReturnRepository.countGroupedByStatus()).thenReturn(
            List.of(
                new Object[] { OrderReturnStatus.REQUESTED, 3L },
                new Object[] { OrderReturnStatus.APPROVED, 4L },
                new Object[] { OrderReturnStatus.PICKUP_FAILED, 2L },
                new Object[] { OrderReturnStatus.RECEIVED, 1L },
                new Object[] { OrderReturnStatus.SHIPPED, 1L },
                new Object[] { OrderReturnStatus.REFUNDED, 5L }
            )
        );
        when(orderReturnRepository.countByStatus(OrderReturnStatus.APPROVED)).thenReturn(4L);
        Instant overdueRequested = Instant.now().minus(72, ChronoUnit.HOURS);
        Instant freshRequested = Instant.now().minus(1, ChronoUnit.HOURS);
        when(orderReturnRepository.findStatusAndRequestedAtByStatusNotIn(any())).thenReturn(
            List.of(
                new Object[] { OrderReturnStatus.REQUESTED, overdueRequested },
                new Object[] { OrderReturnStatus.PICKUP_FAILED, freshRequested }
            )
        );
        when(caseSlaSupport.isReturnOverdue("requested", overdueRequested)).thenReturn(true);
        when(caseSlaSupport.isReturnOverdue("pickup_failed", freshRequested)).thenReturn(false);

        Map<String, Object> summary = orderReturnService.adminSummary();

        assertThat(summary.get("total")).isEqualTo(16L);
        assertThat(summary.get("returns")).isEqualTo(8L);
        assertThat(summary.get("exchanges")).isEqualTo(4L);
        assertThat(summary.get("open")).isEqualTo(11L);
        assertThat(summary.get("closed")).isEqualTo(5L);
        assertThat(summary.get("overdue")).isEqualTo(1L);
        assertThat(summary.get("xpressbeesReverseReady")).isEqualTo(4L);
        @SuppressWarnings("unchecked")
        Map<String, Long> stages = (Map<String, Long>) summary.get("stages");
        assertThat(stages.get("request")).isEqualTo(7L);
        assertThat(stages.get("pickup")).isEqualTo(2L);
        assertThat(stages.get("inspection")).isEqualTo(1L);
        assertThat(stages.get("exchange")).isEqualTo(1L);
        assertThat(stages.get("closed")).isEqualTo(5L);
    }

    @Test
    void adminSummary_emptyQueue_returnsZeros() {
        when(orderReturnRepository.countGroupedByRequestType()).thenReturn(List.of());
        when(orderReturnRepository.countGroupedByStatus()).thenReturn(List.of());
        when(orderReturnRepository.findStatusAndRequestedAtByStatusNotIn(any())).thenReturn(List.of());
        when(orderReturnRepository.countByStatus(OrderReturnStatus.APPROVED)).thenReturn(0L);

        Map<String, Object> summary = orderReturnService.adminSummary();

        assertThat(summary.get("total")).isEqualTo(0L);
        assertThat(summary.get("returns")).isEqualTo(0L);
        assertThat(summary.get("exchanges")).isEqualTo(0L);
        assertThat(summary.get("open")).isEqualTo(0L);
        assertThat(summary.get("closed")).isEqualTo(0L);
        assertThat(summary.get("overdue")).isEqualTo(0L);
        assertThat(summary.get("xpressbeesReverseReady")).isEqualTo(0L);
    }

    @Test
    void adminSummary_skipsMalformedGroupedRows() {
        when(orderReturnRepository.countGroupedByRequestType()).thenReturn(
            List.of(new Object[] { null, 2L }, new Object[] { OrderRequestType.RETURN, 3L }, new Object[] { OrderRequestType.RETURN })
        );
        when(orderReturnRepository.countGroupedByStatus()).thenReturn(
            List.of(new Object[] { null, 1L }, new Object[] { OrderReturnStatus.REQUESTED, 3L })
        );
        when(orderReturnRepository.findStatusAndRequestedAtByStatusNotIn(any())).thenReturn(
            List.of(new Object[] { OrderReturnStatus.REQUESTED }, new Object[] { OrderReturnStatus.REQUESTED, "not-an-instant" })
        );

        Map<String, Object> summary = orderReturnService.adminSummary();

        assertThat(summary.get("returns")).isEqualTo(3L);
        assertThat(summary.get("exchanges")).isEqualTo(0L);
        assertThat(summary.get("total")).isEqualTo(3L);
        assertThat(summary.get("overdue")).isEqualTo(0L);
    }

    @Test
    void reject_requestedReturn_movesToRejected() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.REQUESTED);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);

        OrderReturn result = orderReturnService.reject("RET-1", "Item not eligible", "Admin note");

        assertThat(result.getStatus()).isEqualTo(OrderReturnStatus.REJECTED);
        assertThat(result.getRejectionReason()).isEqualTo("Item not eligible");
    }

    @Test
    void markReceived_approvedReturn_withoutShipment_throwsBadRequest() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.APPROVED);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));

        assertThatThrownBy(() -> orderReturnService.markReceived("RET-1", "Received at warehouse"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Book a reverse pickup shipment before marking received");
    }

    @Test
    void processRefund_demoMode_marksRefunded() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.ACCEPTED);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(razorpayRefundService.refundPayment(any(), any(), any())).thenReturn("rfnd_demo_ORD-9001");
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);
        when(customerOrderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderReturn result = orderReturnService.processRefund("RET-1", "Refunded");

        assertThat(result.getStatus()).isEqualTo(OrderReturnStatus.REFUNDED);
        assertThat(result.getRazorpayRefundId()).isEqualTo("rfnd_demo_ORD-9001");
        assertThat(orderReturn.getOrder().getPaymentStatus()).isEqualTo(PaymentStatus.REFUND_INITIATED);
    }

    @Test
    void processRefund_zohoSyncReturnsDisabledByDefault_neverCallsZoho() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.ACCEPTED);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(razorpayRefundService.refundPayment(any(), any(), any())).thenReturn("rfnd_demo_ORD-9001");
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);
        when(customerOrderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        orderReturnService.processRefund("RET-1", "Refunded");

        verify(zohoBooksIntegrationClient, never()).createCreditNote(any(), any(), anyMap());
    }

    @Test
    void processRefund_ledgerlySyncReturnsDisabledByDefault_neverCallsLedgerly() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.ACCEPTED);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(razorpayRefundService.refundPayment(any(), any(), any())).thenReturn("rfnd_demo_ORD-9001");
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);
        when(customerOrderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderReturn result = orderReturnService.processRefund("RET-1", "Refunded");

        verify(ledgerlyIntegrationClient, never()).cancelOrder(any(), any(), any());
        assertThat(result.getStatus()).isEqualTo(OrderReturnStatus.REFUNDED);
    }

    @Test
    void assertReturnEligible_unpaidOrder_throwsBadRequest() {
        CustomerOrder order = eligibleOrder().paymentStatus(PaymentStatus.PENDING);

        assertThatThrownBy(() -> orderReturnService.assertReturnEligible(order))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Only paid orders");
    }

    @Test
    void listForUser_returnsUserReturns() {
        List<OrderReturn> returns = List.of(returnWithStatus(OrderReturnStatus.REQUESTED));
        when(orderReturnRepository.findAllByUserLoginOrderByRequestedAtDesc(USER)).thenReturn(returns);

        assertThat(orderReturnService.listForUser(USER)).isEqualTo(returns);
    }

    @Test
    void requestReturn_openReturnExists_throwsConflict() {
        CustomerOrder order = eligibleOrder();
        when(customerOrderRepository.findOneByOrderNumberAndUserLogin(ORDER_NUMBER, USER)).thenReturn(java.util.Optional.of(order));
        when(orderReturnRepository.existsByOrderIdAndStatusIn(eq(order.getId()), any())).thenReturn(true);

        assertThatThrownBy(() -> orderReturnService.requestReturn(USER, ORDER_NUMBER, "Product arrived damaged in transit"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("already open");
    }

    @Test
    void reject_shortReason_throwsBadRequest() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.REQUESTED);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(java.util.Optional.of(orderReturn));

        assertThatThrownBy(() -> orderReturnService.reject("RET-1", "no", null))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Rejection reason");
    }

    @Test
    void processRefund_ledgerlySuccess_setsCreditNoteId() {
        enableLedgerlyReturnsSync();
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.ACCEPTED);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(razorpayRefundService.refundPayment(any(), any(), any())).thenReturn("rfnd_demo_ORD-9001");
        when(ledgerlyIntegrationClient.cancelOrder(eq(ORDER_NUMBER), eq("RET-1"), any())).thenReturn(Optional.of("cn-9001"));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);
        when(customerOrderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderReturn result = orderReturnService.processRefund("RET-1", null);

        assertThat(result.getStatus()).isEqualTo(OrderReturnStatus.REFUNDED);
        assertThat(result.getLedgerlyCreditNoteId()).isEqualTo("cn-9001");
        verify(ledgerlyIntegrationClient).cancelOrder(eq(ORDER_NUMBER), eq("RET-1"), any());
    }

    @Test
    void processRefund_existingCreditNoteId_skipsLedgerlyCall() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.ACCEPTED);
        orderReturn.setLedgerlyCreditNoteId("cn-existing");
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(razorpayRefundService.refundPayment(any(), any(), any())).thenReturn("rfnd_demo");
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);
        when(customerOrderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        orderReturnService.processRefund("RET-1", null);

        verify(ledgerlyIntegrationClient, never()).cancelOrder(any(), any(), any());
        verify(ledgerlyIntegrationClient, never()).createCreditNote(any(), any(), any());
    }

    @Test
    void processRefund_ledgerlyFailure_stillRefunds() {
        enableLedgerlyReturnsSync();
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.ACCEPTED);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(java.util.Optional.of(orderReturn));
        when(razorpayRefundService.refundPayment(any(), any(), any())).thenReturn("rfnd_demo_ORD-9001");
        when(ledgerlyIntegrationClient.cancelOrder(any(), any(), any())).thenThrow(new RuntimeException("ledgerly down"));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);
        when(customerOrderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderReturn result = orderReturnService.processRefund("RET-1", null);

        assertThat(result.getStatus()).isEqualTo(OrderReturnStatus.REFUNDED);
    }

    @Test
    void processRefund_ledgerlyEmptyResponse_demoOrder_stubsCreditNoteId() {
        enableLedgerlyReturnsSync();
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.ACCEPTED);
        CustomerOrder order = orderReturn.getOrder();
        order.setRazorpayPaymentId("pay_demo_scenario_123");
        order.setRazorpayOrderId("order_demo_ORD-9001");
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(razorpayRefundService.isDemoMode()).thenReturn(true);
        when(razorpayRefundService.refundPayment(any(), any(), any())).thenReturn("rfnd_demo_ORD-9001");
        when(ledgerlyIntegrationClient.cancelOrder(any(), any(), any())).thenReturn(Optional.empty());
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);
        when(customerOrderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderReturn result = orderReturnService.processRefund("RET-1", null);

        assertThat(result.getLedgerlyCreditNoteId()).isEqualTo("cn_demo_" + ORDER_NUMBER);
    }

    @Test
    void processRefund_existingRefundId_skipsRazorpay() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.FAILED);
        orderReturn.setRazorpayRefundId("rfnd_existing");
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);
        when(customerOrderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderReturn result = orderReturnService.processRefund("RET-1", "Retry");

        assertThat(result.getStatus()).isEqualTo(OrderReturnStatus.REFUNDED);
    }

    @Test
    void acceptWithRestockableDisposition_doesNotIncrementProductStock() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.INSPECTED);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);

        orderReturnService.acceptAfterInspection("RET-1", ReturnDisposition.RESTOCKABLE, null);

        assertThat(orderReturn.getStatus()).isEqualTo(OrderReturnStatus.ACCEPTED);
        assertThat(orderReturn.getDisposition()).isEqualTo(ReturnDisposition.RESTOCKABLE);
        verify(productRepository, never()).findOneForInventoryUpdate(any());
        verify(productRepository, never()).save(any());
    }

    @Test
    void processRefund_restockableDisposition_incrementsProductStock() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.ACCEPTED);
        orderReturn.setDisposition(ReturnDisposition.RESTOCKABLE);
        OrderItem item = new OrderItem().productId("1").qty(2);
        Product product = new Product().id(1L).stock(5);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(orderItemRepository.findAllByOrderId(10L)).thenReturn(List.of(item));
        when(productRepository.findOneForInventoryUpdate(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(product)).thenReturn(product);
        when(razorpayRefundService.refundPayment(any(), any(), any())).thenReturn("rfnd_demo");
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);
        when(customerOrderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        orderReturnService.processRefund("RET-1", null);

        assertThat(product.getStock()).isEqualTo(7);
    }

    @Test
    void processRefund_failedRetry_doesNotIncrementStockAgain() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.FAILED);
        orderReturn.setDisposition(ReturnDisposition.RESTOCKABLE);
        orderReturn.setRazorpayRefundId("rfnd_existing");
        OrderItem item = new OrderItem().productId("1").qty(2);
        orderReturn.setOrderItem(item);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(razorpayRefundService.findExistingRefundForOrder(any(), any(), any())).thenReturn(Optional.empty());
        when(razorpayRefundService.refundPayment(any(), any(), any())).thenReturn("rfnd_retry");
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);
        when(customerOrderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        orderReturnService.processRefund("RET-1", "Retry");

        verify(productRepository, never()).findOneForInventoryUpdate(any());
        verify(productRepository, never()).save(any());
    }

    @Test
    void processRefund_afterRazorpayFailure_restocksOnSuccessfulRetry() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.FAILED);
        orderReturn.setDisposition(ReturnDisposition.RESTOCKABLE);
        OrderItem item = new OrderItem().productId("1").qty(2);
        Product product = new Product().id(1L).stock(5);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(orderItemRepository.findAllByOrderId(10L)).thenReturn(List.of(item));
        when(productRepository.findOneForInventoryUpdate(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(product)).thenReturn(product);
        when(razorpayRefundService.refundPayment(any(), any(), any())).thenReturn("rfnd_demo");
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);
        when(customerOrderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        orderReturnService.processRefund("RET-1", "Retry");

        assertThat(product.getStock()).isEqualTo(7);
    }

    @Test
    void approve_wrongStatus_throwsBadRequest() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.APPROVED);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));

        assertThatThrownBy(() -> orderReturnService.approve("RET-1", null))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("not awaiting approval");
    }

    @Test
    void markReceived_wrongStatus_throwsBadRequest() {
        OrderReturn orderReturn = returnWithReverseShipment(OrderReturnStatus.REQUESTED);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));

        assertThatThrownBy(() -> orderReturnService.markReceived("RET-1", null))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("picked up or scheduled for pickup before marking received");
    }

    @Test
    void eligibilityForUser_notDeliveredYet_notEligible() {
        CustomerOrder order = eligibleOrder().shipmentDate(null).shipmentDeliveredAt(null).status(OrderStatus.CONFIRMED);
        when(customerOrderRepository.findOneByOrderNumberAndUserLogin(ORDER_NUMBER, USER)).thenReturn(Optional.of(order));

        Map<String, Object> result = orderReturnService.eligibilityForUser(USER, ORDER_NUMBER);

        assertThat(result.get("eligible")).isEqualTo(false);
        assertThat(result.get("tracking_status")).isEqualTo("confirmed");
        assertThat(result.get("delivered_at")).isNull();
        assertThat(result.get("return_deadline")).isNull();
        assertThat(result.get("message")).asString().contains("not marked as delivered");
    }

    @Test
    void processRefund_wrongStatus_throwsBadRequest() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.REQUESTED);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));

        assertThatThrownBy(() -> orderReturnService.processRefund("RET-1", null))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("accepted");
    }

    @Test
    void resolveDeliveryAnchor_doesNotUseLedgerlyPullTimeAsDeliveryTime() {
        CustomerOrder order = eligibleOrder()
            .shipmentDate(null)
            .shipmentDeliveredAt(null)
            .status(OrderStatus.FULFILLED)
            .ledgerlyTrackingLastPulledAt(Instant.parse("2026-01-05T00:00:00Z"));

        Instant anchor = orderReturnService.resolveDeliveryAnchor(order);

        assertThat(anchor).isNull();
    }

    @Test
    void eligibilityForUser_shipmentDateWithoutDeliveryTimestamp_isNotEligible() {
        CustomerOrder order = eligibleOrder()
            .status(OrderStatus.FULFILLED)
            .shipmentDate("2026-09-20")
            .shipmentDeliveredAt(null);
        when(customerOrderRepository.findOneByOrderNumberAndUserLogin(ORDER_NUMBER, USER)).thenReturn(Optional.of(order));

        Map<String, Object> result = orderReturnService.eligibilityForUser(USER, ORDER_NUMBER);

        assertThat(result.get("eligible")).isEqualTo(false);
        assertThat(result.get("delivered_at")).isNull();
        assertThat(result.get("return_deadline")).isNull();
        assertThat(result.get("message")).asString().contains("not marked as delivered");
    }

    @Test
    void processRefund_razorpayFailure_marksFailedAndRethrows() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.ACCEPTED);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(razorpayRefundService.refundPayment(any(), any(), any())).thenThrow(new RuntimeException("gateway down"));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);

        assertThatThrownBy(() -> orderReturnService.processRefund("RET-1", "note"))
            .isInstanceOf(RuntimeException.class);

        assertThat(orderReturn.getStatus()).isEqualTo(OrderReturnStatus.FAILED);
    }

    @Test
    void markReceived_withExistingNotes_mergesNotes() {
        OrderReturn orderReturn = returnWithReverseShipment(OrderReturnStatus.PICKED_UP);
        orderReturn.setAdminNotes("Existing");
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);

        OrderReturn result = orderReturnService.markReceived("RET-1", "Received");

        assertThat(result.getAdminNotes()).contains("Existing").contains("Received");
    }

    @Test
    void reject_longReason_truncatesToMaxLength() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.REQUESTED);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);

        String reason = "R".repeat(2000);
        OrderReturn result = orderReturnService.reject("RET-1", reason, null);

        assertThat(result.getRejectionReason()).hasSize(1000);
    }

    @Test
    void cancelByCustomer_wrongUser_throwsNotFound() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.REQUESTED);
        orderReturn.setUserLogin("other");
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));

        assertThatThrownBy(() -> orderReturnService.cancelByCustomer(USER, "RET-1"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("not found");
    }

    @Test
    void assertReturnEligible_expiredWindow_throwsBadRequest() {
        CustomerOrder order = eligibleOrder().shipmentDeliveredAt(Instant.now().minus(30, ChronoUnit.DAYS));

        assertThatThrownBy(() -> orderReturnService.assertReturnEligible(order))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Return window has expired");
    }

    @Test
    void assertReturnEligible_cancelledOrder_throwsBadRequest() {
        CustomerOrder order = eligibleOrder().status(OrderStatus.CANCELLED);

        assertThatThrownBy(() -> orderReturnService.assertReturnEligible(order))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Cancelled orders");
    }

    @Test
    void requestReturn_veryLongReason_truncatesReason() {
        CustomerOrder order = eligibleOrder();
        when(customerOrderRepository.findOneByOrderNumberAndUserLogin(ORDER_NUMBER, USER)).thenReturn(Optional.of(order));
        when(orderReturnRepository.existsByOrderIdAndStatusIn(eq(order.getId()), any())).thenReturn(false);
        when(orderReturnRepository.save(any(OrderReturn.class))).thenAnswer(inv -> inv.getArgument(0));

        String longReason = "x".repeat(5000);
        OrderReturn result = orderReturnService.requestReturn(USER, ORDER_NUMBER, longReason);

        assertThat(result.getReason()).hasSize(4000);
    }

    @Test
    void acceptWithRestockable_variantProduct_doesNotIncrementStock() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.INSPECTED);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);

        orderReturnService.acceptAfterInspection("RET-1", ReturnDisposition.RESTOCKABLE, null);

        verify(productRepository, never()).findOneForInventoryUpdate(any());
        verify(zohoBooksIntegrationClient, never()).syncProductStockOnly(any());
    }

    @Test
    void processRefund_restockableVariantProduct_incrementsVariantStock() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.ACCEPTED);
        orderReturn.setDisposition(ReturnDisposition.RESTOCKABLE);
        String variantsJson =
            "[{\"sku\":\"TEE-S-RED\",\"size\":\"S\",\"color\":\"Red\",\"rate\":500,\"stock\":1,\"active\":true}]";
        OrderItem item = new OrderItem().productId("2").qty(1).variantSku("TEE-S-RED").itemSize("S").color("Red");
        Product product = new Product().id(2L).stock(1).itemStructure("VARIANTS").variantsJson(variantsJson);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(orderItemRepository.findAllByOrderId(10L)).thenReturn(List.of(item));
        when(productRepository.findOneForInventoryUpdate(2L)).thenReturn(Optional.of(product));
        when(productRepository.save(product)).thenReturn(product);
        when(razorpayRefundService.refundPayment(any(), any(), any())).thenReturn("rfnd_demo");
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);
        when(customerOrderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        orderReturnService.processRefund("RET-1", null);

        verify(productRepository).save(product);
        assertThat(product.getStock()).isEqualTo(2);
        verify(zohoBooksIntegrationClient, never()).syncProductStockOnly(any());
    }

    @Test
    void processRefund_restockableVariantWithoutSku_usesSizeColorForRestock() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.ACCEPTED);
        orderReturn.setDisposition(ReturnDisposition.RESTOCKABLE);
        String variantsJson =
            "[{\"sku\":\"TEE-S-RED\",\"size\":\"S\",\"color\":\"Red\",\"rate\":500,\"stock\":1,\"active\":true}]";
        OrderItem item = new OrderItem().productId("2").qty(1).itemSize("S").color("Red");
        Product product = new Product().id(2L).stock(1).itemStructure("VARIANTS").variantsJson(variantsJson);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(orderItemRepository.findAllByOrderId(10L)).thenReturn(List.of(item));
        when(productRepository.findOneForInventoryUpdate(2L)).thenReturn(Optional.of(product));
        when(productRepository.save(product)).thenReturn(product);
        when(razorpayRefundService.refundPayment(any(), any(), any())).thenReturn("rfnd_demo");
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);
        when(customerOrderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        orderReturnService.processRefund("RET-1", null);

        assertThat(product.getStock()).isEqualTo(2);
        verify(zohoBooksIntegrationClient, never()).syncProductStockOnly(any());
    }

    @Test
    void resolveDeliveryAnchor_doesNotTreatDispatchDateAsDelivery() {
        CustomerOrder order = eligibleOrder()
            .shipmentDate(LocalDate.now().minusDays(1).toString())
            .shipmentDeliveredAt(null)
            .status(OrderStatus.CONFIRMED)
            .trackingNumber("AWB-123");

        Instant anchor = orderReturnService.resolveDeliveryAnchor(order);

        assertThat(anchor).isNull();
    }

    @Test
    void resolveDeliveryAnchor_fulfilledDoesNotUseAccountingSyncTime() {
        CustomerOrder order = eligibleOrder()
            .shipmentDate(null)
            .shipmentDeliveredAt(null)
            .status(OrderStatus.FULFILLED)
            .ledgerlyLastSyncedAt(Instant.parse("2026-01-10T00:00:00Z"));

        Instant anchor = orderReturnService.resolveDeliveryAnchor(order);

        assertThat(anchor).isNull();
    }

    @Test
    void processRefund_ledgerlyFailure_recordsAdminNote() {
        enableLedgerlyReturnsSync();
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.ACCEPTED);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(razorpayRefundService.refundPayment(any(), any(), any())).thenReturn("rfnd_live_1");
        when(ledgerlyIntegrationClient.cancelOrder(any(), any(), any())).thenThrow(new RuntimeException("ledgerly 500"));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);
        when(customerOrderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderReturn result = orderReturnService.processRefund("RET-1", "done");

        assertThat(result.getStatus()).isEqualTo(OrderReturnStatus.REFUNDED);
        assertThat(result.getAdminNotes()).contains("Ledgerly order cancel failed");
    }

    @Test
    void approve_returnNotFound_throwsNotFound() {
        when(orderReturnRepository.findOneByReturnNumber("MISSING")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderReturnService.approve("MISSING", null))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("not found");
    }

    // ---------------------------------------------------------------------
    // Exchange request creation
    // ---------------------------------------------------------------------

    @Test
    void requestExchange_unavailableReplacement_throwsBadRequest() {
        CustomerOrder order = eligibleOrder();
        OrderItem item = new OrderItem().id(5L).productId("2").qty(1).itemSize("S").color("Red");
        Product product = new Product()
            .id(2L)
            .itemStructure("VARIANTS")
            .variantsJson("[{\"sku\":\"TEE-S-RED\",\"size\":\"S\",\"color\":\"Red\",\"rate\":500,\"stock\":0,\"active\":false}]");
        when(customerOrderRepository.findOneByOrderNumberAndUserLogin(ORDER_NUMBER, USER)).thenReturn(Optional.of(order));
        when(orderItemRepository.findByIdAndOrderId(5L, 10L)).thenReturn(Optional.of(item));
        when(productRepository.findById(2L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() ->
            orderReturnService.requestExchange(USER, ORDER_NUMBER, 5L, "Wrong size ordered by mistake", "M", "Blue")
        )
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("unavailable for exchange");
    }

    @Test
    void requestExchange_sellableReplacement_persistsExchange() {
        CustomerOrder order = eligibleOrder();
        OrderItem item = new OrderItem().id(5L).productId("2").qty(1).itemSize("S").color("Red");
        Product product = new Product()
            .id(2L)
            .itemStructure("VARIANTS")
            .variantsJson("[{\"sku\":\"TEE-M-BLUE\",\"size\":\"M\",\"color\":\"Blue\",\"rate\":500,\"stock\":3,\"active\":true}]");
        when(customerOrderRepository.findOneByOrderNumberAndUserLogin(ORDER_NUMBER, USER)).thenReturn(Optional.of(order));
        when(orderItemRepository.findByIdAndOrderId(5L, 10L)).thenReturn(Optional.of(item));
        when(productRepository.findById(2L)).thenReturn(Optional.of(product));
        when(orderReturnRepository.save(any(OrderReturn.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderReturn result = orderReturnService.requestExchange(USER, ORDER_NUMBER, 5L, "Wrong size ordered by mistake", "M", "Blue");

        assertThat(result.getRequestType()).isEqualTo(OrderRequestType.EXCHANGE);
        assertThat(result.getReplacementVariantSku()).isEqualTo("TEE-M-BLUE");
        assertThat(result.getOrderItem()).isSameAs(item);
        verify(orderMailService).sendReturnUpdate(eq(order), any(OrderReturn.class));
    }

    @Test
    void requestExchange_withImages_persistsImageUrls() {
        CustomerOrder order = eligibleOrder();
        OrderItem item = new OrderItem().id(5L).productId("2").qty(1).itemSize("S").color("Red");
        Product product = new Product()
            .id(2L)
            .itemStructure("VARIANTS")
            .variantsJson("[{\"sku\":\"TEE-M-BLUE\",\"size\":\"M\",\"color\":\"Blue\",\"rate\":500,\"stock\":3,\"active\":true}]");
        when(customerOrderRepository.findOneByOrderNumberAndUserLogin(ORDER_NUMBER, USER)).thenReturn(Optional.of(order));
        when(orderItemRepository.findByIdAndOrderId(5L, 10L)).thenReturn(Optional.of(item));
        when(productRepository.findById(2L)).thenReturn(Optional.of(product));
        when(orderReturnRepository.save(any(OrderReturn.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderReturn result = orderReturnService.requestExchange(
            USER,
            ORDER_NUMBER,
            5L,
            "Wrong size ordered by mistake",
            "M",
            "Blue",
            List.of("https://cdn.example/returns/fit.jpg")
        );

        assertThat(orderReturnService.imageUrls(result)).containsExactly("https://cdn.example/returns/fit.jpg");
    }

    @Test
    void requestExchange_openReturnOnOrder_throwsConflict() {
        CustomerOrder order = eligibleOrder();
        OrderItem item = new OrderItem().id(5L).productId("2").qty(1).itemSize("S").color("Red");
        Product product = new Product()
            .id(2L)
            .itemStructure("VARIANTS")
            .variantsJson("[{\"sku\":\"TEE-M-BLUE\",\"size\":\"M\",\"color\":\"Blue\",\"rate\":500,\"stock\":3,\"active\":true}]");
        when(customerOrderRepository.findOneByOrderNumberAndUserLogin(ORDER_NUMBER, USER)).thenReturn(Optional.of(order));
        when(orderItemRepository.findByIdAndOrderId(5L, 10L)).thenReturn(Optional.of(item));
        when(productRepository.findById(2L)).thenReturn(Optional.of(product));
        when(orderReturnRepository.existsByOrderIdAndRequestTypeAndStatusIn(eq(10L), eq(OrderRequestType.RETURN), any())).thenReturn(true);

        assertThatThrownBy(() ->
            orderReturnService.requestExchange(USER, ORDER_NUMBER, 5L, "Wrong size ordered by mistake", "M", "Blue")
        )
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("open return already exists");
    }

    @Test
    void requestExchange_openExchangeOnSameItem_throwsConflict() {
        CustomerOrder order = eligibleOrder();
        OrderItem item = new OrderItem().id(5L).productId("2").qty(1).itemSize("S").color("Red");
        Product product = new Product()
            .id(2L)
            .itemStructure("VARIANTS")
            .variantsJson("[{\"sku\":\"TEE-M-BLUE\",\"size\":\"M\",\"color\":\"Blue\",\"rate\":500,\"stock\":3,\"active\":true}]");
        when(customerOrderRepository.findOneByOrderNumberAndUserLogin(ORDER_NUMBER, USER)).thenReturn(Optional.of(order));
        when(orderItemRepository.findByIdAndOrderId(5L, 10L)).thenReturn(Optional.of(item));
        when(productRepository.findById(2L)).thenReturn(Optional.of(product));
        when(orderReturnRepository.existsByOrderIdAndOrderItemIdAndStatusIn(eq(10L), eq(5L), any())).thenReturn(true);

        assertThatThrownBy(() ->
            orderReturnService.requestExchange(USER, ORDER_NUMBER, 5L, "Wrong size ordered by mistake", "M", "Blue")
        )
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("already open for this item");
    }

    @Test
    void isReplacementSellable_matchingActiveVariant_returnsTrue() {
        CustomerOrder order = eligibleOrder();
        OrderItem item = new OrderItem().id(5L).productId("2");
        item.setOrder(order);
        Product product = new Product()
            .id(2L)
            .itemStructure("VARIANTS")
            .variantsJson("[{\"sku\":\"TEE-M-BLUE\",\"size\":\"M\",\"color\":\"Blue\",\"rate\":500,\"stock\":3,\"active\":true}]");
        when(orderItemRepository.findById(5L)).thenReturn(Optional.of(item));
        when(productRepository.findById(2L)).thenReturn(Optional.of(product));

        assertThat(orderReturnService.isReplacementSellable(USER, 5L, "M", "Blue")).isTrue();
    }

    // ---------------------------------------------------------------------
    // Granular fulfilment transitions
    // ---------------------------------------------------------------------

    @Test
    void markPickupRequested_approvedReturn_movesToPickupRequested() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.APPROVED);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);

        OrderReturn result = orderReturnService.markPickupRequested("RET-1", null);

        assertThat(result.getStatus()).isEqualTo(OrderReturnStatus.PICKUP_REQUESTED);
        verify(orderMailService).sendReturnUpdate(any(), eq(orderReturn));
    }

    @Test
    void markPickedUp_pickupRequestedReturn_movesToPickedUp() {
        OrderReturn orderReturn = returnWithReverseShipment(OrderReturnStatus.PICKUP_REQUESTED);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);

        OrderReturn result = orderReturnService.markPickedUp("RET-1", null);

        assertThat(result.getStatus()).isEqualTo(OrderReturnStatus.PICKED_UP);
    }

    @Test
    void markPickedUp_approvedReturn_withoutShipment_throwsBadRequest() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.APPROVED);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));

        assertThatThrownBy(() -> orderReturnService.markPickedUp("RET-1", null))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Book a reverse pickup shipment before marking picked up");
    }

    @Test
    void markInspected_receivedReturn_movesToInspected() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.RECEIVED);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);

        OrderReturn result = orderReturnService.markInspected("RET-1", null);

        assertThat(result.getStatus()).isEqualTo(OrderReturnStatus.INSPECTED);
    }

    @Test
    void acceptAfterInspection_inspectedReturn_movesToAccepted() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.INSPECTED);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);

        OrderReturn result = orderReturnService.acceptAfterInspection("RET-1", ReturnDisposition.QUARANTINE, null);

        assertThat(result.getStatus()).isEqualTo(OrderReturnStatus.ACCEPTED);
    }

    @Test
    void rejectAtCurrentStage_requestedReturn_delegatesToReject() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.REQUESTED);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);

        OrderReturn result = orderReturnService.rejectAtCurrentStage("RET-1", "Not eligible", null);

        assertThat(result.getStatus()).isEqualTo(OrderReturnStatus.REJECTED);
    }

    @Test
    void rejectAtCurrentStage_wrongStatus_throwsBadRequest() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.APPROVED);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));

        assertThatThrownBy(() -> orderReturnService.rejectAtCurrentStage("RET-1", "Not eligible", null))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("cannot be rejected");
    }

    @Test
    void rejectAfterInspection_wrongStatus_throwsBadRequest() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.RECEIVED);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));

        assertThatThrownBy(() -> orderReturnService.rejectAfterInspection("RET-1", "Damaged beyond use", null))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("inspected");
    }

    @Test
    void rejectAfterInspection_inspectedReturn_rejectsWithoutStockChange() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.INSPECTED);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);

        OrderReturn result = orderReturnService.rejectAfterInspection("RET-1", "Item damaged by customer", null);

        assertThat(result.getStatus()).isEqualTo(OrderReturnStatus.REJECTED);
        verify(productRepository, never()).findOneForInventoryUpdate(any());
    }

    @Test
    void rejectAfterInspection_rejectsEvenWhenStockWasPreviouslyCredited() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.INSPECTED);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);

        OrderReturn result = orderReturnService.rejectAfterInspection("RET-1", "Item damaged by customer", null);

        assertThat(result.getStatus()).isEqualTo(OrderReturnStatus.REJECTED);
        assertThat(result.getAdminNotes()).isNull();
    }

    // ---------------------------------------------------------------------
    // Exchange-only fulfilment
    // ---------------------------------------------------------------------

    @Test
    void allocateReplacementItem_notAnExchange_throwsBadRequest() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.ACCEPTED);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));

        assertThatThrownBy(() -> orderReturnService.allocateReplacementItem("RET-1", null))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Not an exchange request");
    }

    @Test
    void allocateReplacementItem_insufficientStock_throwsConflictAndDoesNotTransition() {
        OrderReturn orderReturn = exchangeReturnWithStatus(OrderReturnStatus.ACCEPTED, "TEE-M-BLUE", "M", "Blue");
        Product product = new Product()
            .id(2L)
            .itemStructure("VARIANTS")
            .variantsJson("[{\"sku\":\"TEE-M-BLUE\",\"size\":\"M\",\"color\":\"Blue\",\"rate\":500,\"stock\":0,\"active\":true}]");
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(productRepository.findOneForInventoryUpdate(2L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> orderReturnService.allocateReplacementItem("RET-1", null))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("no longer in stock");

        assertThat(orderReturn.getStatus()).isEqualTo(OrderReturnStatus.ACCEPTED);
        verify(productRepository, never()).save(any());
    }

    @Test
    void allocateReplacementItem_success_decrementsStockAndTransitions() {
        OrderReturn orderReturn = exchangeReturnWithStatus(OrderReturnStatus.ACCEPTED, "TEE-M-BLUE", "M", "Blue");
        Product product = new Product()
            .id(2L)
            .itemStructure("VARIANTS")
            .variantsJson("[{\"sku\":\"TEE-M-BLUE\",\"size\":\"M\",\"color\":\"Blue\",\"rate\":500,\"stock\":3,\"active\":true}]");
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(productRepository.findOneForInventoryUpdate(2L)).thenReturn(Optional.of(product));
        when(productRepository.save(product)).thenReturn(product);
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);

        OrderReturn result = orderReturnService.allocateReplacementItem("RET-1", null);

        assertThat(result.getStatus()).isEqualTo(OrderReturnStatus.ITEM_ALLOCATED);
        assertThat(product.getStock()).isEqualTo(2);
        verify(zohoBooksIntegrationClient).syncProductStockOnly(2L);
        verify(zohoBooksIntegrationClient, never()).createCreditNote(any(), any(), anyMap());
    }

    @Test
    void allocateReplacementItem_alreadyAllocated_skipsStockAndCreditNote() {
        OrderReturn orderReturn = exchangeReturnWithStatus(OrderReturnStatus.ITEM_ALLOCATED, "TEE-M-BLUE", "M", "Blue");
        orderReturn.setReplacementReservedAt(Instant.now());
        orderReturn.setOriginalRestockedAt(Instant.now());
        orderReturn.setZohoCreditNoteId("cn-existing");
        orderReturn.setSettlementStatus(ExchangeSettlementStatus.REFUNDED);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(orderReturnRepository.findOneByReturnNumberForUpdate("RET-1")).thenReturn(Optional.of(orderReturn));

        OrderReturn result = orderReturnService.allocateReplacementItem("RET-1", null);

        assertThat(result.getStatus()).isEqualTo(OrderReturnStatus.ITEM_ALLOCATED);
        assertThat(result.getZohoCreditNoteId()).isEqualTo("cn-existing");
        verify(productRepository, never()).findOneForInventoryUpdate(any());
        verify(productRepository, never()).save(any());
        verify(zohoBooksIntegrationClient, never()).createCreditNote(any(), any(), anyMap());
        verify(zohoBooksIntegrationClient, never()).createCreditNote(any(), any(), anyMap(), eq(false));
        verify(orderReturnRepository, never()).save(any());
    }

    @Test
    void allocateReplacementItem_reservedRestockable_restocksRequestedItemOnceWithoutTouchingReplacement() {
        OrderReturn orderReturn = exchangeReturnWithStatus(OrderReturnStatus.ACCEPTED, "TEE-M-BLUE", "M", "Blue");
        orderReturn.setDisposition(ReturnDisposition.RESTOCKABLE);
        orderReturn.setReplacementReservedAt(Instant.now());
        orderReturn.setSettlementStatus(ExchangeSettlementStatus.NOT_REQUIRED);
        orderReturn.getOrderItem().variantSku("TEE-S-RED").itemSize("S").color("Red").qty(1);
        Product product = new Product()
            .id(2L)
            .itemStructure("VARIANTS")
            .variantsJson(
                "[{\"sku\":\"TEE-S-RED\",\"size\":\"S\",\"color\":\"Red\",\"rate\":500,\"stock\":4,\"active\":true}," +
                "{\"sku\":\"TEE-M-BLUE\",\"size\":\"M\",\"color\":\"Blue\",\"rate\":500,\"stock\":2,\"active\":true}]"
            );
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(productRepository.findOneForInventoryUpdate(2L)).thenReturn(Optional.of(product));
        when(productRepository.save(product)).thenReturn(product);
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);

        OrderReturn result = orderReturnService.allocateReplacementItem("RET-1", null);

        assertThat(result.getStatus()).isEqualTo(OrderReturnStatus.ITEM_ALLOCATED);
        assertThat(result.getOriginalRestockedAt()).isNotNull();
        assertThat(product.getStock()).isEqualTo(7);
        verify(zohoBooksIntegrationClient).syncProductStockOnly(2L);
        verify(zohoBooksIntegrationClient, never()).createCreditNote(any(), any(), anyMap());
    }

    @Test
    void allocateReplacementItem_originalAlreadyRestocked_skipsSecondIncrement() {
        OrderReturn orderReturn = exchangeReturnWithStatus(OrderReturnStatus.ACCEPTED, "TEE-M-BLUE", "M", "Blue");
        orderReturn.setDisposition(ReturnDisposition.RESTOCKABLE);
        orderReturn.setOriginalRestockedAt(Instant.now().minusSeconds(60));
        orderReturn.setReplacementReservedAt(Instant.now());
        orderReturn.setSettlementStatus(ExchangeSettlementStatus.NOT_REQUIRED);
        orderReturn.getOrderItem().variantSku("TEE-S-RED").itemSize("S").color("Red").qty(1);
        Product product = new Product()
            .id(2L)
            .stock(7)
            .itemStructure("VARIANTS")
            .variantsJson(
                "[{\"sku\":\"TEE-S-RED\",\"size\":\"S\",\"color\":\"Red\",\"rate\":500,\"stock\":5,\"active\":true}," +
                "{\"sku\":\"TEE-M-BLUE\",\"size\":\"M\",\"color\":\"Blue\",\"rate\":500,\"stock\":2,\"active\":true}]"
            );
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);

        OrderReturn result = orderReturnService.allocateReplacementItem("RET-1", null);

        assertThat(result.getStatus()).isEqualTo(OrderReturnStatus.ITEM_ALLOCATED);
        assertThat(result.getOriginalRestockedAt()).isEqualTo(orderReturn.getOriginalRestockedAt());
        assertThat(product.getStock()).isEqualTo(7);
        verify(productRepository, never()).findOneForInventoryUpdate(any());
        verify(productRepository, never()).save(any());
    }

    @Test
    void allocateReplacementItem_reservedDiscard_doesNotRestockRequestedItem() {
        OrderReturn orderReturn = exchangeReturnWithStatus(OrderReturnStatus.ACCEPTED, "TEE-M-BLUE", "M", "Blue");
        orderReturn.setDisposition(ReturnDisposition.DISCARD);
        orderReturn.setReplacementReservedAt(Instant.now());
        orderReturn.setSettlementStatus(ExchangeSettlementStatus.NOT_REQUIRED);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);

        OrderReturn result = orderReturnService.allocateReplacementItem("RET-1", null);

        assertThat(result.getStatus()).isEqualTo(OrderReturnStatus.ITEM_ALLOCATED);
        verify(productRepository, never()).findOneForInventoryUpdate(any());
        verify(zohoBooksIntegrationClient).syncProductStockOnly(2L);
        verify(zohoBooksIntegrationClient, never()).createCreditNote(any(), any(), anyMap());
    }

    @Test
    void acceptExchange_zohoSync_doesNotCreateCreditNote() {
        OrderReturn orderReturn = exchangeReturnWithStatus(OrderReturnStatus.INSPECTED, "TEE-M-BLUE", "M", "Blue");
        orderReturn.setOriginalItemAmount(new BigDecimal("10.00"));
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);

        OrderReturn result = orderReturnService.acceptAfterInspection("RET-1", ReturnDisposition.DISCARD, null);

        assertThat(result.getStatus()).isEqualTo(OrderReturnStatus.ACCEPTED);
        assertThat(result.getZohoCreditNoteId()).isNull();
        verify(zohoBooksIntegrationClient, never()).createCreditNote(any(), any(), anyMap());
        verify(zohoBooksIntegrationClient, never()).createCreditNote(any(), any(), anyMap(), eq(false));
    }

    @Test
    void allocateReplacementItem_samePrice_createsCreditNoteAndAppliesPastNotesToInvoice() {
        ApplicationProperties.Zoho zoho = new ApplicationProperties.Zoho();
        zoho.setSyncReturns(true);
        when(applicationProperties.getZoho()).thenReturn(zoho);
        OrderReturn orderReturn = exchangeReturnWithStatus(OrderReturnStatus.ACCEPTED, "TEE-M-BLUE", "M", "Blue");
        orderReturn.setReplacementItemAmount(new BigDecimal("15.00"));
        orderReturn.setReplacementReservedAt(Instant.now());
        orderReturn.setSettlementStatus(ExchangeSettlementStatus.NOT_REQUIRED);
        OrderReturn earlier = new OrderReturn();
        earlier.setRequestType(OrderRequestType.EXCHANGE);
        earlier.setZohoCreditNoteId("cn-old");
        earlier.setRequestedAt(Instant.now().minusSeconds(90));
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(orderReturnRepository.findAllByOrder_IdOrderByRequestedAtAsc(10L)).thenReturn(List.of(earlier, orderReturn));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);
        when(zohoBooksIntegrationClient.createCreditNote(eq(10L), eq("RET-1"), anyMap(), eq(false))).thenReturn(Optional.of("cn-this"));
        when(
            zohoBooksIntegrationClient.createExchangeReplacementInvoice(
                eq(10L),
                eq("RET-1"),
                eq(5L),
                eq("TEE-M-BLUE"),
                eq("M"),
                eq("Blue"),
                eq(new BigDecimal("15.00")),
                eq(1),
                anyList()
            )
        ).thenReturn(Optional.of("inv-repl"));

        OrderReturn result = orderReturnService.allocateReplacementItem("RET-1", null);

        assertThat(result.getStatus()).isEqualTo(OrderReturnStatus.ITEM_ALLOCATED);
        assertThat(result.getZohoCreditNoteId()).isEqualTo("cn-this");
        assertThat(result.getZohoReplacementInvoiceId()).isEqualTo("inv-repl");
        verify(zohoBooksIntegrationClient).createCreditNote(eq(10L), eq("RET-1"), anyMap(), eq(false));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> creditNotes = ArgumentCaptor.forClass(List.class);
        verify(zohoBooksIntegrationClient).createExchangeReplacementInvoice(
            eq(10L),
            eq("RET-1"),
            eq(5L),
            eq("TEE-M-BLUE"),
            eq("M"),
            eq("Blue"),
            eq(new BigDecimal("15.00")),
            eq(1),
            creditNotes.capture()
        );
        assertThat(creditNotes.getValue()).containsExactly("cn-old", "cn-this");
    }

    @Test
    void allocateReplacementItem_alreadyHasCreditNoteFromRazorpay_doesNotCreateAgain() {
        ApplicationProperties.Zoho zoho = new ApplicationProperties.Zoho();
        zoho.setSyncReturns(true);
        when(applicationProperties.getZoho()).thenReturn(zoho);
        OrderReturn orderReturn = exchangeReturnWithStatus(OrderReturnStatus.ACCEPTED, "TEE-M-BLUE", "M", "Blue");
        orderReturn.setReplacementItemAmount(new BigDecimal("12.00"));
        orderReturn.setReplacementReservedAt(Instant.now());
        orderReturn.setSettlementStatus(ExchangeSettlementStatus.REFUNDED);
        orderReturn.setPriceDifference(new BigDecimal("-3.00"));
        orderReturn.setZohoCreditNoteId("cn-rzp");
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(orderReturnRepository.findAllByOrder_IdOrderByRequestedAtAsc(10L)).thenReturn(List.of(orderReturn));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);
        when(
            zohoBooksIntegrationClient.createExchangeReplacementInvoice(
                eq(10L),
                eq("RET-1"),
                eq(5L),
                eq("TEE-M-BLUE"),
                eq("M"),
                eq("Blue"),
                eq(new BigDecimal("12.00")),
                eq(1),
                anyList()
            )
        ).thenReturn(Optional.of("inv-repl"));

        OrderReturn result = orderReturnService.allocateReplacementItem("RET-1", null);

        assertThat(result.getZohoCreditNoteId()).isEqualTo("cn-rzp");
        assertThat(result.getZohoReplacementInvoiceId()).isEqualTo("inv-repl");
        verify(zohoBooksIntegrationClient, never()).createCreditNote(any(), any(), anyMap(), eq(false));
        verify(zohoBooksIntegrationClient).refundOpenCreditNoteBalance("cn-rzp", "RET-1");
    }

    @Test
    void allocateReplacementItem_replacementInvoiceFails_skipsLeftoverCreditRefund() {
        ApplicationProperties.Zoho zoho = new ApplicationProperties.Zoho();
        zoho.setSyncReturns(true);
        when(applicationProperties.getZoho()).thenReturn(zoho);
        OrderReturn orderReturn = exchangeReturnWithStatus(OrderReturnStatus.ACCEPTED, "TEE-M-BLUE", "M", "Blue");
        orderReturn.setReplacementItemAmount(new BigDecimal("12.00"));
        orderReturn.setReplacementReservedAt(Instant.now());
        orderReturn.setSettlementStatus(ExchangeSettlementStatus.REFUNDED);
        orderReturn.setPriceDifference(new BigDecimal("-3.00"));
        orderReturn.setZohoCreditNoteId("cn-rzp");
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(orderReturnRepository.findAllByOrder_IdOrderByRequestedAtAsc(10L)).thenReturn(List.of(orderReturn));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);
        when(
            zohoBooksIntegrationClient.createExchangeReplacementInvoice(
                eq(10L),
                eq("RET-1"),
                eq(5L),
                eq("TEE-M-BLUE"),
                eq("M"),
                eq("Blue"),
                eq(new BigDecimal("12.00")),
                eq(1),
                anyList()
            )
        ).thenReturn(Optional.empty());

        orderReturnService.allocateReplacementItem("RET-1", null);

        verify(zohoBooksIntegrationClient, never()).refundOpenCreditNoteBalance(any(), any());
    }

    @Test
    void convertExchangeToRefund_refundsItemAmountNotWholeOrder() {
        OrderReturn orderReturn = exchangeReturnWithStatus(OrderReturnStatus.ACCEPTED, "TEE-M-BLUE", "M", "Blue");
        orderReturn.getOrderItem().price(new BigDecimal("250.00")).qty(2);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(razorpayRefundService.refundPayment(any(), eq(new BigDecimal("500.00")), any())).thenReturn("rfnd_demo");
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);

        OrderReturn result = orderReturnService.convertExchangeToRefund("RET-1", null);

        assertThat(result.getStatus()).isEqualTo(OrderReturnStatus.REFUNDED);
        assertThat(result.getRefundAmount()).isEqualByComparingTo("500.00");
        assertThat(orderReturn.getOrder().getPaymentStatus()).isEqualTo(PaymentStatus.REFUND_INITIATED);
        verify(customerOrderRepository).save(orderReturn.getOrder());
    }

    @Test
    void convertExchangeToRefund_alreadyRefunded_skipsStockAndCreditNote() {
        OrderReturn orderReturn = exchangeReturnWithStatus(OrderReturnStatus.REFUNDED, "TEE-M-BLUE", "M", "Blue");
        orderReturn.setReplacementReservedAt(Instant.now());
        orderReturn.setReplacementReservationReleasedAt(Instant.now());
        orderReturn.setOriginalRestockedAt(Instant.now());
        orderReturn.setZohoCreditNoteId("cn-existing");
        orderReturn.setRefundAmount(new BigDecimal("500.00"));
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(orderReturnRepository.findOneByReturnNumberForUpdate("RET-1")).thenReturn(Optional.of(orderReturn));

        OrderReturn result = orderReturnService.convertExchangeToRefund("RET-1", null);

        assertThat(result.getStatus()).isEqualTo(OrderReturnStatus.REFUNDED);
        assertThat(result.getZohoCreditNoteId()).isEqualTo("cn-existing");
        verify(razorpayRefundService, never()).refundPayment(any(), any(), any());
        verify(zohoBooksIntegrationClient, never()).createCreditNote(any(), any(), anyMap(), eq(true));
        verify(zohoBooksIntegrationClient, never()).applyCreditNoteToInvoice(any(), any());
        verify(zohoBooksIntegrationClient, never()).attachCreditNoteToSourceInvoice(any(), any(), any(), anyMap());
        verify(productRepository, never()).findOneForInventoryUpdate(any());
        verify(orderReturnRepository, never()).save(any());
    }

    @Test
    void convertExchangeToRefund_zohoSync_createsCreditNoteAfterRefundInitiated() {
        ApplicationProperties.Zoho zoho = new ApplicationProperties.Zoho();
        zoho.setSyncReturns(true);
        when(applicationProperties.getZoho()).thenReturn(zoho);
        OrderReturn orderReturn = exchangeReturnWithStatus(OrderReturnStatus.ACCEPTED, "TEE-M-BLUE", "M", "Blue");
        orderReturn.getOrderItem().price(new BigDecimal("250.00")).qty(2);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(razorpayRefundService.refundPayment(any(), eq(new BigDecimal("500.00")), any())).thenReturn("rfnd_demo");
        when(zohoBooksIntegrationClient.createCreditNote(eq(10L), eq("RET-1"), anyMap(), eq(true))).thenReturn(Optional.of("cn-zoho-ex"));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);
        when(customerOrderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderReturn result = orderReturnService.convertExchangeToRefund("RET-1", null);

        assertThat(result.getStatus()).isEqualTo(OrderReturnStatus.REFUNDED);
        assertThat(result.getZohoCreditNoteId()).isEqualTo("cn-zoho-ex");
        assertThat(orderReturn.getOrder().getPaymentStatus()).isEqualTo(PaymentStatus.REFUND_INITIATED);
        verify(zohoBooksIntegrationClient).createCreditNote(eq(10L), eq("RET-1"), anyMap(), eq(true));
        verify(zohoBooksIntegrationClient, never()).createCreditNote(any(), any(), anyMap());
    }

    @Test
    void convertExchangeToRefund_discountedMissingSnapshot_refundsNetAmount() {
        OrderReturn orderReturn = exchangeReturnWithStatus(OrderReturnStatus.ACCEPTED, "TEE-M-BLUE", "M", "Blue");
        orderReturn.setOriginalItemAmount(null);
        orderReturn.getOrderItem().price(new BigDecimal("250.00")).qty(2);
        orderReturn.getOrder().subtotal(new BigDecimal("500.00")).discount(new BigDecimal("100.00"));
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(orderItemRepository.findAllByOrderId(10L)).thenReturn(List.of(orderReturn.getOrderItem()));
        when(razorpayRefundService.refundPayment(any(), eq(new BigDecimal("400.00")), any())).thenReturn("rfnd_net");
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);

        OrderReturn result = orderReturnService.convertExchangeToRefund("RET-1", null);

        assertThat(result.getRefundAmount()).isEqualByComparingTo("400.00");
        verify(razorpayRefundService).refundPayment(orderReturn.getOrder(), new BigDecimal("400.00"), RazorpayRefundService.PURPOSE_RETURN_REFUND);
    }

    @Test
    void processRefund_exchangeType_throwsBadRequest() {
        OrderReturn orderReturn = exchangeReturnWithStatus(OrderReturnStatus.ACCEPTED, "TEE-M-BLUE", "M", "Blue");
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));

        assertThatThrownBy(() -> orderReturnService.processRefund("RET-1", null))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("convert-to-refund");
    }

    @Test
    void exchangeFulfilment_walksThroughRemainingStates() {
        OrderReturn orderReturn = exchangeReturnWithStatus(OrderReturnStatus.ITEM_ALLOCATED, "TEE-M-BLUE", "M", "Blue");
        stubOutboundPackageRates(orderReturn);

        assertThat(
            orderReturnService
                .completeOutboundPackage("RET-1", 500, 12, 10, 8, "1", "Surface Xpressbees 0.5 K.G", "box")
                .getStatus()
        ).isEqualTo(OrderReturnStatus.READY_TO_SHIP);
        orderReturn.setOutboundAwb("OUT-AWB-1");
        assertThat(orderReturnService.markShipped("RET-1", null).getStatus()).isEqualTo(OrderReturnStatus.SHIPPED);
        assertThat(orderReturnService.markDelivered("RET-1", null).getStatus()).isEqualTo(OrderReturnStatus.DELIVERED);
        assertThat(orderReturnService.completeExchange("RET-1", null).getStatus()).isEqualTo(OrderReturnStatus.COMPLETED);
        verify(zohoBooksIntegrationClient, never()).syncProductStockOnly(any());
        verify(zohoBooksIntegrationClient, never()).createCreditNote(any(), any(), anyMap());
    }

    @Test
    void markPickupFailed_notifiesCustomerAndAdmin() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.PICKUP_SCHEDULED);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);

        OrderReturn result = orderReturnService.markPickupFailed("RET-1", "Customer not available", "webhook");

        assertThat(result.getStatus()).isEqualTo(OrderReturnStatus.PICKUP_FAILED);
        verify(orderMailService).sendReturnUpdate(eq(orderReturn.getOrder()), eq(orderReturn));
        verify(orderMailService).sendPickupFailedAdminNotice(eq(orderReturn.getOrder()), eq(orderReturn));
    }

    @Test
    void publicConfig_exposesFeatureFlags() {
        Map<String, Object> config = orderReturnService.publicConfig();

        assertThat(config.get("enabled")).isEqualTo(true);
        assertThat(config.get("window_days")).isEqualTo(3);
        assertThat(config.get("refund_min_business_days")).isEqualTo(2);
        assertThat(config.get("refund_max_business_days")).isEqualTo(5);
    }

    @Test
    void rejectAtCurrentStage_inspectedReturn_delegatesToRejectAfterInspection() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.INSPECTED);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);

        OrderReturn result = orderReturnService.rejectAtCurrentStage("RET-1", "Damaged item", "note");

        assertThat(result.getStatus()).isEqualTo(OrderReturnStatus.REJECTED);
        verify(productRepository, never()).findOneForInventoryUpdate(any());
    }

    @Test
    void isReplacementSellable_wrongUser_returnsFalse() {
        CustomerOrder order = eligibleOrder();
        OrderItem item = new OrderItem().id(5L).productId("2");
        item.setOrder(order);
        when(orderItemRepository.findById(5L)).thenReturn(Optional.of(item));

        assertThat(orderReturnService.isReplacementSellable("other-user", 5L, "M", "Blue")).isFalse();
    }

    @Test
    void isReplacementSellable_nonVariantProduct_returnsFalse() {
        CustomerOrder order = eligibleOrder();
        OrderItem item = new OrderItem().id(5L).productId("2");
        item.setOrder(order);
        Product product = new Product().id(2L).itemStructure("SIMPLE");
        when(orderItemRepository.findById(5L)).thenReturn(Optional.of(item));
        when(productRepository.findById(2L)).thenReturn(Optional.of(product));

        assertThat(orderReturnService.isReplacementSellable(USER, 5L, "M", "Blue")).isFalse();
    }

    @Test
    void requestExchange_nonVariantProduct_throwsBadRequest() {
        CustomerOrder order = eligibleOrder();
        OrderItem item = new OrderItem().id(5L).productId("2").qty(1);
        Product product = new Product().id(2L).itemStructure("SIMPLE");
        when(customerOrderRepository.findOneByOrderNumberAndUserLogin(ORDER_NUMBER, USER)).thenReturn(Optional.of(order));
        when(orderItemRepository.findByIdAndOrderId(5L, 10L)).thenReturn(Optional.of(item));
        when(productRepository.findById(2L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() ->
            orderReturnService.requestExchange(USER, ORDER_NUMBER, 5L, "Wrong size ordered by mistake", "M", "Blue")
        )
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("does not support exchanges");
    }

    @Test
    void acceptWithRestockable_exchangeDoesNotChangeRequestedItemStock() {
        OrderReturn orderReturn = exchangeReturnWithStatus(OrderReturnStatus.INSPECTED, "TEE-M-BLUE", "M", "Blue");
        Product product = new Product().id(2L).stock(4);
        orderReturn.getOrderItem().productId("2").qty(1);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);

        OrderReturn result = orderReturnService.acceptAfterInspection("RET-1", ReturnDisposition.RESTOCKABLE, null);

        assertThat(result.getStatus()).isEqualTo(OrderReturnStatus.ACCEPTED);
        assertThat(result.getDisposition()).isEqualTo(ReturnDisposition.RESTOCKABLE);
        assertThat(product.getStock()).isEqualTo(4);
        verify(productRepository, never()).findOneForInventoryUpdate(any());
        verify(productRepository, never()).save(any());
        verify(zohoBooksIntegrationClient, never()).syncProductStockOnly(any());
        verify(zohoBooksIntegrationClient, never()).createCreditNote(any(), any(), anyMap());
    }

    @Test
    void acceptWithDiscard_exchangeDoesNotRestockOrSyncZoho() {
        OrderReturn orderReturn = exchangeReturnWithStatus(OrderReturnStatus.INSPECTED, "TEE-M-BLUE", "M", "Blue");
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);

        OrderReturn result = orderReturnService.acceptAfterInspection("RET-1", ReturnDisposition.DISCARD, null);

        assertThat(result.getStatus()).isEqualTo(OrderReturnStatus.ACCEPTED);
        verify(productRepository, never()).findOneForInventoryUpdate(any());
        verify(zohoBooksIntegrationClient, never()).syncProductStockOnly(any());
        verify(zohoBooksIntegrationClient, never()).createCreditNote(any(), any(), anyMap());
    }

    @Test
    void convertExchangeToRefund_fromFailedStatus_refundsItemAmount() {
        OrderReturn orderReturn = exchangeReturnWithStatus(OrderReturnStatus.FAILED, "TEE-M-BLUE", "M", "Blue");
        orderReturn.getOrderItem().price(new BigDecimal("150.00")).qty(1);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(razorpayRefundService.refundPayment(any(), eq(new BigDecimal("150.00")), any())).thenReturn("rfnd_demo");
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);

        OrderReturn result = orderReturnService.convertExchangeToRefund("RET-1", "Converted");

        assertThat(result.getStatus()).isEqualTo(OrderReturnStatus.REFUNDED);
    }

    @Test
    void processRefund_zohoSyncEnabled_createsCreditNote() {
        ApplicationProperties.Zoho zoho = new ApplicationProperties.Zoho();
        zoho.setSyncReturns(true);
        when(applicationProperties.getZoho()).thenReturn(zoho);
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.ACCEPTED);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(razorpayRefundService.refundPayment(any(), any(), any())).thenReturn("rfnd_demo_ORD-9001");
        when(zohoBooksIntegrationClient.createCreditNote(eq(10L), eq("RET-1"), anyMap())).thenReturn(Optional.of("cn-zoho-1"));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);
        when(customerOrderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderReturn result = orderReturnService.processRefund("RET-1", null);

        assertThat(result.getZohoCreditNoteId()).isEqualTo("cn-zoho-1");
    }

    @Test
    void processRefund_zohoSyncEnabled_passesAcceptedQuantitiesToCreditNote() {
        ApplicationProperties.Zoho zoho = new ApplicationProperties.Zoho();
        zoho.setSyncReturns(true);
        when(applicationProperties.getZoho()).thenReturn(zoho);
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.ACCEPTED);
        orderReturn.setId(50L);
        OrderItem orderItem = new OrderItem().id(200L).qty(2);
        OrderReturnItem line = new OrderReturnItem();
        line.setOrderItem(orderItem);
        line.setRequestedQty(2);
        line.setAcceptedQty(1);
        line.setRefundAmount(new BigDecimal("100.00"));
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(orderReturnItemRepository.findAllByOrderReturnIdOrderById(50L)).thenReturn(List.of(line));
        when(razorpayRefundService.refundPayment(any(), any(), any())).thenReturn("rfnd_demo_ORD-9001");
        when(zohoBooksIntegrationClient.createCreditNote(eq(10L), eq("RET-1"), eq(Map.of(200L, 1)))).thenReturn(Optional.of("cn-zoho-1"));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);
        when(customerOrderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        orderReturnService.processRefund("RET-1", null);

        verify(zohoBooksIntegrationClient).createCreditNote(10L, "RET-1", Map.of(200L, 1));
    }

    @Test
    void processRefund_zeroAcceptedQty_doesNotRestockOrCreditLine() {
        ApplicationProperties.Zoho zoho = new ApplicationProperties.Zoho();
        zoho.setSyncReturns(true);
        when(applicationProperties.getZoho()).thenReturn(zoho);
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.ACCEPTED);
        orderReturn.setId(50L);
        orderReturn.setDisposition(ReturnDisposition.RESTOCKABLE);
        OrderItem orderItem = new OrderItem().id(200L).productId("2").qty(2);
        OrderReturnItem line = new OrderReturnItem();
        line.setOrderItem(orderItem);
        line.setRequestedQty(2);
        line.setAcceptedQty(0);
        line.setDisposition(ReturnDisposition.RESTOCKABLE);
        line.setRefundAmount(new BigDecimal("200.00"));
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(orderReturnItemRepository.findAllByOrderReturnIdOrderById(50L)).thenReturn(List.of(line));
        when(razorpayRefundService.refundPayment(any(), any(), any())).thenReturn("rfnd_demo_ORD-9001");
        when(zohoBooksIntegrationClient.createCreditNote(eq(10L), eq("RET-1"), eq(Map.of()))).thenReturn(Optional.of("cn-empty"));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);
        when(customerOrderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        orderReturnService.processRefund("RET-1", null);

        verify(productRepository, never()).findOneForInventoryUpdate(any());
        verify(zohoBooksIntegrationClient).createCreditNote(10L, "RET-1", Map.of());
    }

    @Test
    void processRefund_nullAcceptedQty_doesNotTreatRequestedAsAccepted() {
        ApplicationProperties.Zoho zoho = new ApplicationProperties.Zoho();
        zoho.setSyncReturns(true);
        when(applicationProperties.getZoho()).thenReturn(zoho);
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.ACCEPTED);
        orderReturn.setId(50L);
        orderReturn.setDisposition(ReturnDisposition.RESTOCKABLE);
        OrderItem orderItem = new OrderItem().id(200L).productId("2").qty(2);
        OrderReturnItem line = new OrderReturnItem();
        line.setOrderItem(orderItem);
        line.setRequestedQty(2);
        line.setAcceptedQty(null);
        line.setDisposition(ReturnDisposition.RESTOCKABLE);
        line.setRefundAmount(new BigDecimal("200.00"));
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(orderReturnItemRepository.findAllByOrderReturnIdOrderById(50L)).thenReturn(List.of(line));
        when(razorpayRefundService.refundPayment(any(), any(), any())).thenReturn("rfnd_demo_ORD-9001");
        when(zohoBooksIntegrationClient.createCreditNote(eq(10L), eq("RET-1"), eq(Map.of()))).thenReturn(Optional.of("cn-empty"));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);
        when(customerOrderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        orderReturnService.processRefund("RET-1", null);

        verify(productRepository, never()).findOneForInventoryUpdate(any());
        verify(zohoBooksIntegrationClient).createCreditNote(10L, "RET-1", Map.of());
    }

    @Test
    void processRefund_zohoFailure_recordsAdminNote() {
        ApplicationProperties.Zoho zoho = new ApplicationProperties.Zoho();
        zoho.setSyncReturns(true);
        when(applicationProperties.getZoho()).thenReturn(zoho);
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.ACCEPTED);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(razorpayRefundService.refundPayment(any(), any(), any())).thenReturn("rfnd_live");
        when(zohoBooksIntegrationClient.createCreditNote(eq(10L), eq("RET-1"), anyMap())).thenThrow(new RuntimeException("zoho 500"));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);
        when(customerOrderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderReturn result = orderReturnService.processRefund("RET-1", null);

        assertThat(result.getStatus()).isEqualTo(OrderReturnStatus.REFUNDED);
        assertThat(result.getAdminNotes()).contains("Zoho credit note failed");
    }

    @Test
    void resolveDeliveryAnchor_deliveredTrackingStatus_requiresPhysicalTimestamp() {
        CustomerOrder order = eligibleOrder()
            .shipmentDate(null)
            .shipmentDeliveredAt(null)
            .status(OrderStatus.CONFIRMED)
            .ledgerlyOrderStatus("delivered")
            .ledgerlyTrackingLastPulledAt(Instant.parse("2026-02-01T00:00:00Z"));

        Instant anchor = orderReturnService.resolveDeliveryAnchor(order);

        assertThat(anchor).isEqualTo(Instant.parse("2026-02-01T00:00:00Z"));
    }

    @Test
    void resolveDeliveryAnchor_trackingNumberDoesNotImplyDelivery() {
        CustomerOrder order = eligibleOrder()
            .shipmentDate(null)
            .shipmentDeliveredAt(null)
            .status(OrderStatus.CONFIRMED)
            .trackingNumber("AWB-999")
            .ledgerlyTrackingLastPulledAt(Instant.parse("2026-02-15T00:00:00Z"));

        Instant anchor = orderReturnService.resolveDeliveryAnchor(order);

        assertThat(anchor).isNull();
    }

    @Test
    void markReceived_fromPickedUp_movesToReceived() {
        OrderReturn orderReturn = returnWithReverseShipment(OrderReturnStatus.PICKED_UP);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);

        OrderReturn result = orderReturnService.markReceived("RET-1", "At warehouse");

        assertThat(result.getStatus()).isEqualTo(OrderReturnStatus.RECEIVED);
    }

    @Test
    void markReceived_pickedUpWithoutShipment_throwsBadRequest() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.PICKED_UP);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));

        assertThatThrownBy(() -> orderReturnService.markReceived("RET-1", "At warehouse"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Book a reverse pickup shipment before marking received");
    }

    @Test
    void markReceived_exchangeBeforePickedUp_throwsBadRequest() {
        OrderReturn orderReturn = exchangeReturnWithStatus(OrderReturnStatus.PICKUP_SCHEDULED, "TEE-M-BLUE", "M", "Blue");
        orderReturn.setLogisticsMode(ReturnLogisticsMode.MANUAL);
        orderReturn.setReverseAwb("AWB-REV-1");
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));

        assertThatThrownBy(() -> orderReturnService.markReceived("RET-1", "At warehouse"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Exchange item must be picked up before marking received");
    }

    @Test
    void markReceived_exchangePickedUp_movesToReceived() {
        OrderReturn orderReturn = exchangeReturnWithStatus(OrderReturnStatus.PICKED_UP, "TEE-M-BLUE", "M", "Blue");
        orderReturn.setLogisticsMode(ReturnLogisticsMode.MANUAL);
        orderReturn.setReverseAwb("AWB-REV-1");
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);

        OrderReturn result = orderReturnService.markReceived("RET-1", "At warehouse");

        assertThat(result.getStatus()).isEqualTo(OrderReturnStatus.RECEIVED);
    }

    @Test
    void reject_wrongStatus_throwsBadRequest() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.APPROVED);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));

        assertThatThrownBy(() -> orderReturnService.reject("RET-1", "Not eligible", null))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("not awaiting approval");
    }

    @Test
    void rejectAfterInspection_exchangeRejectsWithoutStockChange() {
        OrderReturn orderReturn = exchangeReturnWithStatus(OrderReturnStatus.INSPECTED, "TEE-M-BLUE", "M", "Blue");
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);

        OrderReturn result = orderReturnService.rejectAfterInspection("RET-1", "Customer damaged item", null);

        assertThat(result.getStatus()).isEqualTo(OrderReturnStatus.REJECTED);
        verify(productRepository, never()).findOneForInventoryUpdate(any());
    }

    @Test
    void allocateReplacementItem_wrongStatus_throwsBadRequest() {
        OrderReturn orderReturn = exchangeReturnWithStatus(OrderReturnStatus.REQUESTED, "TEE-M-BLUE", "M", "Blue");
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));

        assertThatThrownBy(() -> orderReturnService.allocateReplacementItem("RET-1", null))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("accepted before allocating");
    }

    @Test
    void allocateReplacementItem_missingProduct_throwsBadRequest() {
        OrderReturn orderReturn = exchangeReturnWithStatus(OrderReturnStatus.ACCEPTED, "TEE-M-BLUE", "M", "Blue");
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(productRepository.findOneForInventoryUpdate(2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderReturnService.allocateReplacementItem("RET-1", null))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("could not be resolved");
    }

    @Test
    void convertExchangeToRefund_wrongStatus_throwsBadRequest() {
        OrderReturn orderReturn = exchangeReturnWithStatus(OrderReturnStatus.REQUESTED, "TEE-M-BLUE", "M", "Blue");
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));

        assertThatThrownBy(() -> orderReturnService.convertExchangeToRefund("RET-1", null))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("accepted");
    }

    @Test
    void processRefund_ledgerlyException_demoOrder_stubsCreditNote() {
        enableLedgerlyReturnsSync();
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.ACCEPTED);
        CustomerOrder order = orderReturn.getOrder();
        order.setRazorpayPaymentId("pay_demo_abc");
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(razorpayRefundService.refundPayment(any(), any(), any())).thenReturn("rfnd_demo");
        when(ledgerlyIntegrationClient.cancelOrder(any(), any(), any())).thenThrow(new RuntimeException("ledgerly down"));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);
        when(customerOrderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderReturn result = orderReturnService.processRefund("RET-1", null);

        assertThat(result.getLedgerlyCreditNoteId()).isEqualTo("cn_demo_" + ORDER_NUMBER);
    }

    @Test
    void processRefund_zohoEmptyResponse_demoOrder_stubsCreditNote() {
        ApplicationProperties.Zoho zoho = new ApplicationProperties.Zoho();
        zoho.setSyncReturns(true);
        when(applicationProperties.getZoho()).thenReturn(zoho);
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.ACCEPTED);
        orderReturn.getOrder().setRazorpayOrderId("order_demo_ORD-9001");
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(razorpayRefundService.isDemoMode()).thenReturn(true);
        when(razorpayRefundService.refundPayment(any(), any(), any())).thenReturn("rfnd_demo");
        when(zohoBooksIntegrationClient.createCreditNote(eq(10L), eq("RET-1"), anyMap())).thenReturn(Optional.empty());
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);
        when(customerOrderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderReturn result = orderReturnService.processRefund("RET-1", null);

        assertThat(result.getZohoCreditNoteId()).isEqualTo("cn_zoho_demo_" + ORDER_NUMBER);
    }

    @Test
    void processRefund_zohoException_demoOrder_stubsCreditNote() {
        ApplicationProperties.Zoho zoho = new ApplicationProperties.Zoho();
        zoho.setSyncReturns(true);
        when(applicationProperties.getZoho()).thenReturn(zoho);
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.ACCEPTED);
        orderReturn.getOrder().setRazorpayPaymentId("pay_demo_xyz");
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(razorpayRefundService.refundPayment(any(), any(), any())).thenReturn("rfnd_demo");
        when(zohoBooksIntegrationClient.createCreditNote(eq(10L), eq("RET-1"), anyMap())).thenThrow(new RuntimeException("zoho down"));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);
        when(customerOrderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderReturn result = orderReturnService.processRefund("RET-1", null);

        assertThat(result.getZohoCreditNoteId()).isEqualTo("cn_zoho_demo_" + ORDER_NUMBER);
    }

    @Test
    void acceptWithRestockable_doesNotTouchInventory() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.INSPECTED);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);

        orderReturnService.acceptAfterInspection("RET-1", ReturnDisposition.RESTOCKABLE, null);

        verify(productRepository, never()).findOneForInventoryUpdate(any());
        verify(productRepository, never()).save(any());
    }

    @Test
    void processRefund_invalidProductId_skipsInventoryRestore() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.ACCEPTED);
        orderReturn.setDisposition(ReturnDisposition.RESTOCKABLE);
        OrderItem item = new OrderItem().productId("not-a-number").qty(2);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(orderItemRepository.findAllByOrderId(10L)).thenReturn(List.of(item));
        when(razorpayRefundService.refundPayment(any(), any(), any())).thenReturn("rfnd_demo");
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);
        when(customerOrderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        orderReturnService.processRefund("RET-1", null);

        verify(productRepository, never()).findOneForInventoryUpdate(any());
    }

    @Test
    void processRefund_zeroQuantityItem_skipsStockChange() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.ACCEPTED);
        orderReturn.setDisposition(ReturnDisposition.RESTOCKABLE);
        OrderItem item = new OrderItem().productId("1").qty(0);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(orderItemRepository.findAllByOrderId(10L)).thenReturn(List.of(item));
        when(razorpayRefundService.refundPayment(any(), any(), any())).thenReturn("rfnd_demo");
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);
        when(customerOrderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        orderReturnService.processRefund("RET-1", null);

        verify(productRepository, never()).save(any());
    }

    @Test
    void processRefund_variantIncrementFails_fallsBackToProductStock() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.ACCEPTED);
        orderReturn.setDisposition(ReturnDisposition.RESTOCKABLE);
        OrderItem item = new OrderItem().productId("2").qty(1).variantSku("MISSING").itemSize("M").color("Blue");
        Product product = new Product().id(2L).stock(3).itemStructure("VARIANTS").variantsJson("[]");
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(orderItemRepository.findAllByOrderId(10L)).thenReturn(List.of(item));
        when(productRepository.findOneForInventoryUpdate(2L)).thenReturn(Optional.of(product));
        when(productRepository.save(product)).thenReturn(product);
        when(razorpayRefundService.refundPayment(any(), any(), any())).thenReturn("rfnd_demo");
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);
        when(customerOrderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        orderReturnService.processRefund("RET-1", null);

        assertThat(product.getStock()).isEqualTo(4);
    }

    @Test
    void rejectAfterInspection_nullItem_stillRejects() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.INSPECTED);
        orderReturn.setRequestType(OrderRequestType.EXCHANGE);
        orderReturn.setOrderItem(null);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);

        OrderReturn result = orderReturnService.rejectAfterInspection("RET-1", "Not acceptable", null);

        assertThat(result.getStatus()).isEqualTo(OrderReturnStatus.REJECTED);
    }

    @Test
    void requestReturn_longOrderNumber_truncatesReturnNumber() {
        CustomerOrder order = eligibleOrder().orderNumber("ORD-" + "X".repeat(60));
        when(customerOrderRepository.findOneByOrderNumberAndUserLogin(order.getOrderNumber(), USER)).thenReturn(Optional.of(order));
        when(orderReturnRepository.existsByOrderIdAndStatusIn(eq(order.getId()), any())).thenReturn(false);
        when(orderReturnRepository.save(any(OrderReturn.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderReturn result = orderReturnService.requestReturn(USER, order.getOrderNumber(), "Product arrived damaged in transit");

        assertThat(result.getReturnNumber().length()).isLessThanOrEqualTo(48);
    }

    @Test
    void resolveDeliveryAnchor_invalidShipmentDate_returnsNull() {
        CustomerOrder order = eligibleOrder().shipmentDate("31/08/2026").shipmentDeliveredAt(null).status(OrderStatus.CONFIRMED);

        Instant anchor = orderReturnService.resolveDeliveryAnchor(order);

        assertThat(anchor).isNull();
    }

    @Test
    void isReplacementSellable_missingItem_returnsFalse() {
        when(orderItemRepository.findById(99L)).thenReturn(Optional.empty());

        assertThat(orderReturnService.isReplacementSellable(USER, 99L, "M", "Blue")).isFalse();
    }

    @Test
    void requestExchange_missingOrderItem_throwsNotFound() {
        CustomerOrder order = eligibleOrder();
        when(customerOrderRepository.findOneByOrderNumberAndUserLogin(ORDER_NUMBER, USER)).thenReturn(Optional.of(order));
        when(orderItemRepository.findByIdAndOrderId(99L, 10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
            orderReturnService.requestExchange(USER, ORDER_NUMBER, 99L, "Wrong size ordered by mistake", "M", "Blue")
        )
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Order item not found");
    }

    @Test
    void acceptWithRestockable_exchangeWithZeroQtyItem_doesNotSaveProduct() {
        OrderReturn orderReturn = exchangeReturnWithStatus(OrderReturnStatus.INSPECTED, "TEE-M-BLUE", "M", "Blue");
        orderReturn.getOrderItem().productId("2").qty(0);
        Product product = new Product().id(2L).stock(4);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);

        orderReturnService.acceptAfterInspection("RET-1", ReturnDisposition.RESTOCKABLE, null);

        verify(productRepository, never()).findOneForInventoryUpdate(any());
        verify(productRepository, never()).save(any());
        assertThat(product.getStock()).isEqualTo(4);
    }

    @Test
    void rejectAfterInspection_missingProduct_stillRejects() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.INSPECTED);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);

        OrderReturn result = orderReturnService.rejectAfterInspection("RET-1", "Item damaged by customer", null);

        assertThat(result.getStatus()).isEqualTo(OrderReturnStatus.REJECTED);
        assertThat(result.getAdminNotes()).isNull();
    }

    @Test
    void rejectAfterInspection_zeroQuantityItem_stillRejects() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.INSPECTED);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);

        OrderReturn result = orderReturnService.rejectAfterInspection("RET-1", "Item damaged by customer", null);

        assertThat(result.getStatus()).isEqualTo(OrderReturnStatus.REJECTED);
        verify(productRepository, never()).save(any());
    }

    @Test
    void rejectAfterInspection_invalidProductId_stillRejects() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.INSPECTED);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);

        OrderReturn result = orderReturnService.rejectAfterInspection("RET-1", "Item damaged by customer", null);

        assertThat(result.getStatus()).isEqualTo(OrderReturnStatus.REJECTED);
    }

    @Test
    void allocateReplacementItem_invalidProductId_throwsBadRequest() {
        OrderReturn orderReturn = exchangeReturnWithStatus(OrderReturnStatus.ACCEPTED, "TEE-M-BLUE", "M", "Blue");
        orderReturn.getOrderItem().productId("bad-id");
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));

        assertThatThrownBy(() -> orderReturnService.allocateReplacementItem("RET-1", null))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("could not be resolved");
    }

    @Test
    void bookPickup_manualWithoutAwb_throwsBadRequest() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.APPROVED);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));

        assertThatThrownBy(() ->
            orderReturnService.bookPickup("RET-1", ReturnLogisticsMode.MANUAL, "Delhivery", "", null, null, null)
        )
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Reverse AWB");
    }

    @Test
    void bookPickup_xpressBeesNotConfigured_throwsServiceUnavailable() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.APPROVED);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(xpressBeesClient.isReversePickupConfigured()).thenReturn(false);

        assertThatThrownBy(() ->
            orderReturnService.bookPickup("RET-1", ReturnLogisticsMode.XPRESSBEES, null, null, null, null, null)
        )
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("not configured");
    }

    @Test
    void bookPickup_xpressBeesSuccess_persistsAwbAndTracking() throws Exception {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.APPROVED);
        orderReturn.getOrder().shippingAddressJson(
            "{\"full_name\":\"Jane Doe\",\"phone\":\"9876543210\",\"line1\":\"12 MG Road\",\"city\":\"Bengaluru\",\"state\":\"Karnataka\",\"pincode\":\"560001\"}"
        );
        OrderItem item = new OrderItem().productName("Tee").variantSku("TEE-1").qty(1).price(BigDecimal.TEN);
        orderReturn.setOrderItem(item);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(xpressBeesClient.isReversePickupConfigured()).thenReturn(true);
        when(xpressBeesClient.createReversePickup(any())).thenReturn(
            new XpressBeesClient.ReversePickupResult("AWB123", "https://track.example/AWB123", "Tomorrow", "REQ-1", "Success")
        );
        when(xpressBeesClient.buildTrackingUrl("AWB123")).thenReturn("https://track.example/AWB123");
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);

        OrderReturn result = orderReturnService.bookPickup("RET-1", ReturnLogisticsMode.XPRESSBEES, null, null, null, null, "Book now");

        assertThat(result.getStatus()).isEqualTo(OrderReturnStatus.PICKUP_SCHEDULED);
        assertThat(result.getLogisticsMode()).isEqualTo(ReturnLogisticsMode.XPRESSBEES);
        assertThat(result.getReverseAwb()).isEqualTo("AWB123");
        assertThat(result.getTrackingUrl()).isEqualTo("https://track.example/AWB123");
        assertThat(result.getProviderRequestId()).isEqualTo("REQ-1");
    }

    @Test
    void bookOutbound_manualMode_movesToShippedWithAwb() {
        OrderReturn orderReturn = packedExchange(OrderReturnStatus.READY_TO_SHIP);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);

        OrderReturn result = orderReturnService.bookOutbound(
            "RET-1",
            ReturnLogisticsMode.MANUAL,
            "Delhivery",
            "OUT-AWB-1",
            "https://track/out",
            "https://label/out",
            "Manual outbound"
        );

        assertThat(result.getStatus()).isEqualTo(OrderReturnStatus.SHIPPED);
        assertThat(result.getOutboundLogisticsMode()).isEqualTo(ReturnLogisticsMode.MANUAL);
        assertThat(result.getOutboundAwb()).isEqualTo("OUT-AWB-1");
        assertThat(result.getOutboundTrackingUrl()).isEqualTo("https://track/out");
        assertThat(result.getOutboundLabelUrl()).isEqualTo("https://label/out");
    }

    @Test
    void bookOutbound_xpressBeesSuccess_persistsOutboundAwb() throws Exception {
        OrderReturn orderReturn = packedExchange(OrderReturnStatus.READY_TO_SHIP);
        orderReturn
            .getOrder()
            .shippingAddressJson(
                "{\"full_name\":\"Jane Doe\",\"phone\":\"9876543210\",\"line1\":\"12 MG Road\",\"city\":\"Bengaluru\",\"state\":\"Karnataka\",\"pincode\":\"560001\"}"
            );
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(xpressBeesClient.isForwardShipmentConfigured()).thenReturn(true);
        when(xpressBeesClient.createForwardShipment(any())).thenReturn(
            new XpressBeesClient.ForwardShipmentResult("OUT123", "https://track.example/OUT123", "https://label/out", "OUT-REQ-1", "Success")
        );
        when(xpressBeesClient.buildTrackingUrl("OUT123")).thenReturn("https://track.example/OUT123");
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);

        OrderReturn result = orderReturnService.bookOutbound("RET-1", ReturnLogisticsMode.XPRESSBEES, null, null, null, null, "Book outbound");

        assertThat(result.getStatus()).isEqualTo(OrderReturnStatus.READY_TO_SHIP);
        assertThat(result.getOutboundCarrierStatus()).isEqualTo("awaiting_pickup");
        assertThat(result.getOutboundLogisticsMode()).isEqualTo(ReturnLogisticsMode.XPRESSBEES);
        assertThat(result.getOutboundAwb()).isEqualTo("OUT123");
        assertThat(result.getOutboundTrackingUrl()).isEqualTo("https://track.example/OUT123");
        assertThat(result.getOutboundProviderRequestId()).isEqualTo("OUT-REQ-1");
    }

    @Test
    void requestReturn_selectedQuantityCreatesProratedRefundSnapshot() {
        CustomerOrder order = eligibleOrder().subtotal(new BigDecimal("1000.00")).discount(new BigDecimal("100.00")).total(new BigDecimal("900.00"));
        OrderItem item = new OrderItem().id(77L).order(order).productId("2").productName("Romper").qty(2).price(new BigDecimal("500.00"));
        when(customerOrderRepository.findOneByOrderNumberAndUserLogin(ORDER_NUMBER, USER)).thenReturn(Optional.of(order));
        when(orderReturnRepository.existsByOrderIdAndStatusIn(eq(10L), any())).thenReturn(false);
        when(orderReturnRepository.save(any(OrderReturn.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderItemRepository.findAllByOrderId(10L)).thenReturn(List.of(item));
        when(orderReturnItemRepository.save(any(OrderReturnItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderReturn result = orderReturnService.requestReturn(
            USER,
            ORDER_NUMBER,
            "The selected item is the wrong size",
            List.of(new OrderReturnService.ReturnLineSelection(77L, 1))
        );

        assertThat(result.getRefundAmount()).isEqualByComparingTo("450.00");
        assertThat(result.getOrderItem()).isSameAs(item);
        verify(orderReturnItemRepository).save(
            org.mockito.ArgumentMatchers.argThat(line ->
                line.getRequestedQty() == 1 &&
                line.getGrossAmount().compareTo(new BigDecimal("500.00")) == 0 &&
                line.getDiscountAllocation().compareTo(new BigDecimal("50.00")) == 0 &&
                line.getRefundAmount().compareTo(new BigDecimal("450.00")) == 0
            )
        );
    }

    @Test
    void requestReturn_doesNotRefundShippingCodFeeOrDonation() {
        CustomerOrder order = eligibleOrder()
            .subtotal(new BigDecimal("1000.00"))
            .discount(BigDecimal.ZERO)
            .shipping(new BigDecimal("99.00"))
            .codFee(new BigDecimal("40.00"))
            .donation(new BigDecimal("10.00"))
            .total(new BigDecimal("1149.00"));
        OrderItem item = new OrderItem().id(77L).order(order).productId("2").productName("Romper").qty(1).price(new BigDecimal("1000.00"));
        when(customerOrderRepository.findOneByOrderNumberAndUserLogin(ORDER_NUMBER, USER)).thenReturn(Optional.of(order));
        when(orderReturnRepository.existsByOrderIdAndStatusIn(eq(10L), any())).thenReturn(false);
        when(orderReturnRepository.save(any(OrderReturn.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderItemRepository.findAllByOrderId(10L)).thenReturn(List.of(item));
        when(orderReturnItemRepository.save(any(OrderReturnItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderReturn result = orderReturnService.requestReturn(USER, ORDER_NUMBER, "The selected item is the wrong size");

        assertThat(result.getRefundAmount()).isEqualByComparingTo("1000.00");
        verify(razorpayRefundService, never()).refundPayment(any(), any(), any());
    }

    @Test
    void processRefund_doesNotRefundShippingWhenReturnHasNoLineItems() {
        CustomerOrder order = eligibleOrder()
            .subtotal(new BigDecimal("500.00"))
            .discount(BigDecimal.ZERO)
            .shipping(new BigDecimal("99.00"))
            .total(new BigDecimal("599.00"));
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.ACCEPTED);
        orderReturn.setOrder(order);
        orderReturn.setRefundAmount(null);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(orderReturnItemRepository.findAllByOrderReturnIdOrderById(any())).thenReturn(List.of());
        when(razorpayRefundService.refundPayment(any(), eq(new BigDecimal("500.00")), any())).thenReturn("rfnd_demo");
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);
        when(customerOrderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderReturn result = orderReturnService.processRefund("RET-1", "Refunded");

        assertThat(result.getRefundAmount()).isEqualByComparingTo("500.00");
        verify(razorpayRefundService).refundPayment(eq(order), eq(new BigDecimal("500.00")), any());
    }

    @Test
    void approveExchange_priceDifferenceUsesItemNetNotShipping() {
        CustomerOrder order = eligibleOrder()
            .subtotal(new BigDecimal("1000.00"))
            .shipping(new BigDecimal("99.00"))
            .codFee(new BigDecimal("40.00"))
            .total(new BigDecimal("1139.00"));
        OrderItem item = new OrderItem().id(5L).productId("2").qty(1).itemSize("S").color("Red").price(new BigDecimal("1000.00"));
        OrderReturn orderReturn = exchangeReturnWithStatus(OrderReturnStatus.REQUESTED, "TEE-M-BLUE", "M", "Blue");
        orderReturn.setOrder(order);
        orderReturn.setOrderItem(item);
        Product product = new Product()
            .id(2L)
            .itemStructure("VARIANTS")
            .variantsJson("[{\"sku\":\"TEE-M-BLUE\",\"size\":\"M\",\"color\":\"Blue\",\"rate\":700,\"stock\":2,\"active\":true}]");
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(orderItemRepository.findAllByOrderId(10L)).thenReturn(List.of(item));
        when(productRepository.findOneForInventoryUpdate(2L)).thenReturn(Optional.of(product));
        when(productRepository.save(product)).thenReturn(product);
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);

        OrderReturn result = orderReturnService.approve("RET-1", null);

        assertThat(result.getOriginalItemAmount()).isEqualByComparingTo("1000.00");
        assertThat(result.getReplacementItemAmount()).isPositive();
        assertThat(result.getPriceDifference()).isEqualByComparingTo(
            result.getReplacementItemAmount().subtract(new BigDecimal("1000.00"))
        );
    }

    @Test
    void requestReturn_usesStoredBogoLineAllocationInsteadOfGrossRatio() {
        CustomerOrder order = eligibleOrder().subtotal(new BigDecimal("1500.00")).discount(new BigDecimal("500.00")).total(new BigDecimal("1000.00"));
        OrderItem paid = new OrderItem()
            .id(77L)
            .order(order)
            .productId("1")
            .productName("Shirt")
            .qty(1)
            .price(new BigDecimal("1000.00"))
            .discountAllocation(BigDecimal.ZERO);
        OrderItem free = new OrderItem()
            .id(78L)
            .order(order)
            .productId("2")
            .productName("Tee")
            .qty(1)
            .price(new BigDecimal("500.00"))
            .discountAllocation(new BigDecimal("500.00"));
        when(customerOrderRepository.findOneByOrderNumberAndUserLogin(ORDER_NUMBER, USER)).thenReturn(Optional.of(order));
        when(orderReturnRepository.existsByOrderIdAndStatusIn(eq(10L), any())).thenReturn(false);
        when(orderReturnRepository.save(any(OrderReturn.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderItemRepository.findAllByOrderId(10L)).thenReturn(List.of(paid, free));
        when(orderReturnItemRepository.save(any(OrderReturnItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderReturn returningPaid = orderReturnService.requestReturn(
            USER,
            ORDER_NUMBER,
            "The selected item is the wrong size",
            List.of(new OrderReturnService.ReturnLineSelection(77L, 1))
        );
        assertThat(returningPaid.getRefundAmount()).isEqualByComparingTo("1000.00");

        OrderReturn returningFree = orderReturnService.requestReturn(
            USER,
            ORDER_NUMBER,
            "The selected item is the wrong size",
            List.of(new OrderReturnService.ReturnLineSelection(78L, 1))
        );
        assertThat(returningFree.getRefundAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    void approveExchange_paidBogoLineWithZeroAllocation_creditsFullGrossNotGrossRatio() {
        CustomerOrder order = eligibleOrder()
            .subtotal(new BigDecimal("1500.00"))
            .discount(new BigDecimal("500.00"))
            .total(new BigDecimal("1000.00"));
        OrderItem paid = new OrderItem()
            .id(77L)
            .order(order)
            .productId("2")
            .productName("Shirt")
            .qty(1)
            .price(new BigDecimal("1000.00"))
            .discountAllocation(BigDecimal.ZERO);
        OrderItem free = new OrderItem()
            .id(78L)
            .order(order)
            .productId("3")
            .productName("Tee")
            .qty(1)
            .price(new BigDecimal("500.00"))
            .discountAllocation(new BigDecimal("500.00"));
        OrderReturn orderReturn = exchangeReturnWithStatus(OrderReturnStatus.REQUESTED, "TEE-M-BLUE", "M", "Blue");
        orderReturn.setOrder(order);
        orderReturn.setOrderItem(paid);
        Product product = new Product()
            .id(2L)
            .itemStructure("VARIANTS")
            .variantsJson(
                "[{\"sku\":\"TEE-M-BLUE\",\"size\":\"M\",\"color\":\"Blue\",\"rate\":1000,\"price\":1000,\"stock\":2,\"active\":true}]"
            );
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(orderItemRepository.findAllByOrderId(10L)).thenReturn(List.of(paid, free));
        when(productRepository.findOneForInventoryUpdate(2L)).thenReturn(Optional.of(product));
        when(productRepository.save(product)).thenReturn(product);
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);

        OrderReturn result = orderReturnService.approve("RET-1", null);

        // Paid line had ₹0 BOGO allocation — credit full ₹1000, not ~₹666.67 gross-ratio.
        assertThat(result.getOriginalItemAmount()).isEqualByComparingTo("1000.00");
        assertThat(result.getReplacementItemAmount()).isEqualByComparingTo("1000.00");
        assertThat(result.getPriceDifference()).isEqualByComparingTo("0.00");
    }

    @Test
    void approveExchangeReservesReplacementAndSnapshotsPriceDifference() {
        OrderReturn orderReturn = exchangeReturnWithStatus(OrderReturnStatus.REQUESTED, "TEE-M-BLUE", "M", "Blue");
        orderReturn.getOrderItem().price(new BigDecimal("100.00"));
        Product product = new Product()
            .id(2L)
            .itemStructure("VARIANTS")
            .variantsJson("[{\"sku\":\"TEE-M-BLUE\",\"size\":\"M\",\"color\":\"Blue\",\"rate\":500,\"stock\":2,\"active\":true}]");
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(productRepository.findOneForInventoryUpdate(2L)).thenReturn(Optional.of(product));
        when(productRepository.save(product)).thenReturn(product);
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);

        OrderReturn result = orderReturnService.approve("RET-1", null);

        assertThat(result.getStatus()).isEqualTo(OrderReturnStatus.APPROVED);
        assertThat(result.getReplacementReservedAt()).isNotNull();
        assertThat(result.getPriceDifference()).isPositive();
        assertThat(result.getSettlementStatus()).isEqualTo(ExchangeSettlementStatus.PENDING);
        assertThat(product.getStock()).isEqualTo(1);
        verify(zohoBooksIntegrationClient).syncProductStockOnly(2L);
        verify(zohoBooksIntegrationClient, never()).createCreditNote(any(), any(), anyMap());
    }

    @Test
    void approveExchange_zohoStockSyncFails_stillApprovesAndNotesFailure() {
        OrderReturn orderReturn = exchangeReturnWithStatus(OrderReturnStatus.REQUESTED, "TEE-M-BLUE", "M", "Blue");
        orderReturn.getOrderItem().price(new BigDecimal("100.00"));
        Product product = new Product()
            .id(2L)
            .itemStructure("VARIANTS")
            .variantsJson("[{\"sku\":\"TEE-M-BLUE\",\"size\":\"M\",\"color\":\"Blue\",\"rate\":500,\"stock\":2,\"active\":true}]");
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(productRepository.findOneForInventoryUpdate(2L)).thenReturn(Optional.of(product));
        when(productRepository.save(product)).thenReturn(product);
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);
        doThrow(new RuntimeException("zoho 500")).when(zohoBooksIntegrationClient).syncProductStockOnly(2L);

        OrderReturn result = orderReturnService.approve("RET-1", null);

        assertThat(result.getStatus()).isEqualTo(OrderReturnStatus.APPROVED);
        assertThat(result.getReplacementReservedAt()).isNotNull();
        assertThat(product.getStock()).isEqualTo(1);
        assertThat(result.getAdminNotes()).contains("Zoho stock sync failed");
        verify(zohoBooksIntegrationClient, never()).createCreditNote(any(), any(), anyMap());
    }

    @Test
    void approveExchange_productStockSyncDisabled_skipsZoho() {
        ApplicationProperties.Zoho zoho = new ApplicationProperties.Zoho();
        zoho.setSyncProductStock(false);
        when(applicationProperties.getZoho()).thenReturn(zoho);
        OrderReturn orderReturn = exchangeReturnWithStatus(OrderReturnStatus.REQUESTED, "TEE-M-BLUE", "M", "Blue");
        orderReturn.getOrderItem().price(new BigDecimal("100.00"));
        Product product = new Product()
            .id(2L)
            .itemStructure("VARIANTS")
            .variantsJson("[{\"sku\":\"TEE-M-BLUE\",\"size\":\"M\",\"color\":\"Blue\",\"rate\":500,\"stock\":2,\"active\":true}]");
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(productRepository.findOneForInventoryUpdate(2L)).thenReturn(Optional.of(product));
        when(productRepository.save(product)).thenReturn(product);
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);

        OrderReturn result = orderReturnService.approve("RET-1", null);

        assertThat(result.getStatus()).isEqualTo(OrderReturnStatus.APPROVED);
        assertThat(product.getStock()).isEqualTo(1);
        verify(zohoBooksIntegrationClient, never()).syncProductStockOnly(any());
    }

    @Test
    void convertExchangeToRefund_releasesReservationAndSyncsZohoStock() {
        OrderReturn orderReturn = exchangeReturnWithStatus(OrderReturnStatus.ACCEPTED, "TEE-M-BLUE", "M", "Blue");
        orderReturn.setReplacementReservedAt(Instant.now());
        orderReturn.getOrderItem().price(new BigDecimal("100.00")).qty(1);
        Product product = new Product()
            .id(2L)
            .itemStructure("VARIANTS")
            .variantsJson("[{\"sku\":\"TEE-M-BLUE\",\"size\":\"M\",\"color\":\"Blue\",\"rate\":500,\"stock\":1,\"active\":true}]");
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(productRepository.findOneForInventoryUpdate(2L)).thenReturn(Optional.of(product));
        when(productRepository.save(product)).thenReturn(product);
        when(razorpayRefundService.refundPayment(any(), eq(new BigDecimal("100.00")), any())).thenReturn("rfnd_demo");
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);

        OrderReturn result = orderReturnService.convertExchangeToRefund("RET-1", null);

        assertThat(result.getStatus()).isEqualTo(OrderReturnStatus.REFUNDED);
        assertThat(result.getReplacementReservationReleasedAt()).isNotNull();
        assertThat(product.getStock()).isEqualTo(2);
        verify(zohoBooksIntegrationClient).syncProductStockOnly(2L);
        verify(zohoBooksIntegrationClient, never()).createCreditNote(any(), any(), anyMap());
    }

    @Test
    void convertExchangeToRefund_restockable_restocksRequestedItemOnce() {
        OrderReturn orderReturn = exchangeReturnWithStatus(OrderReturnStatus.ACCEPTED, "TEE-M-BLUE", "M", "Blue");
        orderReturn.setDisposition(ReturnDisposition.RESTOCKABLE);
        orderReturn.setReplacementReservedAt(Instant.now());
        orderReturn.getOrderItem().price(new BigDecimal("100.00")).qty(1).variantSku("TEE-S-RED").itemSize("S").color("Red");
        Product product = new Product()
            .id(2L)
            .itemStructure("VARIANTS")
            .variantsJson(
                "[{\"sku\":\"TEE-S-RED\",\"size\":\"S\",\"color\":\"Red\",\"rate\":500,\"stock\":4,\"active\":true}," +
                "{\"sku\":\"TEE-M-BLUE\",\"size\":\"M\",\"color\":\"Blue\",\"rate\":500,\"stock\":1,\"active\":true}]"
            );
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(productRepository.findOneForInventoryUpdate(2L)).thenReturn(Optional.of(product));
        when(productRepository.save(product)).thenReturn(product);
        when(razorpayRefundService.refundPayment(any(), eq(new BigDecimal("100.00")), any())).thenReturn("rfnd_demo");
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);

        OrderReturn result = orderReturnService.convertExchangeToRefund("RET-1", null);

        assertThat(result.getStatus()).isEqualTo(OrderReturnStatus.REFUNDED);
        assertThat(product.getStock()).isEqualTo(7);
        verify(zohoBooksIntegrationClient).syncProductStockOnly(2L);
    }

    @Test
    void settlePriceDifferenceRecordsAuditedCollection() {
        OrderReturn orderReturn = exchangeReturnWithStatus(OrderReturnStatus.APPROVED, "TEE-M-BLUE", "M", "Blue");
        orderReturn.setPriceDifference(new BigDecimal("100.00"));
        orderReturn.setSettlementStatus(ExchangeSettlementStatus.PENDING);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(orderReturnRepository.findOneByReturnNumberForUpdate("RET-1")).thenReturn(Optional.of(orderReturn));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);

        OrderReturn result = orderReturnService.settlePriceDifference(
            "RET-1",
            ExchangeSettlementStatus.COLLECTED,
            new BigDecimal("100.00"),
            "UPI",
            "UTR-123",
            "admin",
            null
        );

        assertThat(result.getSettlementStatus()).isEqualTo(ExchangeSettlementStatus.COLLECTED);
        assertThat(result.getSettlementReference()).isEqualTo("UTR-123");
        assertThat(result.getSettledBy()).isEqualTo("admin");
        assertThat(result.getSettledAt()).isNotNull();
        verify(razorpayRefundService, never()).refundPayment(any(), any(), any());
        verify(zohoBooksIntegrationClient, never()).createCreditNote(any(), any(), anyMap(), eq(false));
    }

    @Test
    void settlePriceDifference_collected_doesNotCreateCreditNote() {
        ApplicationProperties.Zoho zoho = new ApplicationProperties.Zoho();
        zoho.setSyncReturns(true);
        lenient().when(applicationProperties.getZoho()).thenReturn(zoho);
        OrderReturn orderReturn = exchangeReturnWithStatus(OrderReturnStatus.ACCEPTED, "TEE-M-BLUE", "M", "Blue");
        orderReturn.setPriceDifference(new BigDecimal("100.00"));
        orderReturn.setOriginalItemAmount(new BigDecimal("400.00"));
        orderReturn.setSettlementStatus(ExchangeSettlementStatus.PENDING);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(orderReturnRepository.findOneByReturnNumberForUpdate("RET-1")).thenReturn(Optional.of(orderReturn));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);

        OrderReturn result = orderReturnService.settlePriceDifference(
            "RET-1",
            ExchangeSettlementStatus.COLLECTED,
            new BigDecimal("100.00"),
            "UPI",
            "UTR-123",
            "admin",
            null
        );

        assertThat(result.getSettlementStatus()).isEqualTo(ExchangeSettlementStatus.COLLECTED);
        assertThat(result.getZohoCreditNoteId()).isNull();
        verify(zohoBooksIntegrationClient, never()).createCreditNote(any(), any(), anyMap(), eq(false));
        verify(exchangeRazorpaySettlementService, never()).recordRazorpayRefund(any(), any(), any(), any());
    }

    @Test
    void allocateReplacementItem_afterCollected_createsCreditNote() {
        ApplicationProperties.Zoho zoho = new ApplicationProperties.Zoho();
        zoho.setSyncReturns(true);
        when(applicationProperties.getZoho()).thenReturn(zoho);
        OrderReturn orderReturn = exchangeReturnWithStatus(OrderReturnStatus.ACCEPTED, "TEE-M-BLUE", "M", "Blue");
        orderReturn.setReplacementItemAmount(new BigDecimal("500.00"));
        orderReturn.setReplacementReservedAt(Instant.now());
        orderReturn.setSettlementStatus(ExchangeSettlementStatus.COLLECTED);
        orderReturn.setSettlementAmount(new BigDecimal("100.00"));
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(orderReturnRepository.findAllByOrder_IdOrderByRequestedAtAsc(10L)).thenReturn(List.of(orderReturn));
        when(zohoBooksIntegrationClient.createCreditNote(eq(10L), eq("RET-1"), eq(Map.of(5L, 1)), eq(false))).thenReturn(Optional.of("cn-higher"));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);
        when(
            zohoBooksIntegrationClient.createExchangeReplacementInvoice(
                eq(10L),
                eq("RET-1"),
                eq(5L),
                eq("TEE-M-BLUE"),
                eq("M"),
                eq("Blue"),
                eq(new BigDecimal("500.00")),
                eq(1),
                anyList()
            )
        ).thenReturn(Optional.of("inv-repl"));

        OrderReturn result = orderReturnService.allocateReplacementItem("RET-1", null);

        assertThat(result.getZohoCreditNoteId()).isEqualTo("cn-higher");
        verify(zohoBooksIntegrationClient).createCreditNote(eq(10L), eq("RET-1"), eq(Map.of(5L, 1)), eq(false));
    }

    @Test
    void allocateReplacementItem_ledgerlyCreditNoteUsesNetAmountWhenOriginalMissing() {
        enableLedgerlyReturnsSync();
        OrderReturn orderReturn = exchangeReturnWithStatus(OrderReturnStatus.ACCEPTED, "TEE-M-BLUE", "M", "Blue");
        orderReturn.setOriginalItemAmount(null);
        orderReturn.getOrderItem().price(new BigDecimal("250.00")).qty(2);
        orderReturn.getOrder().subtotal(new BigDecimal("500.00")).discount(new BigDecimal("100.00"));
        orderReturn.setReplacementReservedAt(Instant.now());
        orderReturn.setSettlementStatus(ExchangeSettlementStatus.NOT_REQUIRED);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);
        when(orderItemRepository.findAllByOrderId(10L)).thenReturn(List.of(orderReturn.getOrderItem()));
        when(ledgerlyIntegrationClient.createCreditNote(eq(ORDER_NUMBER), eq("RET-1"), eq(new BigDecimal("400.00"))))
            .thenReturn(Optional.of("cn-ledgerly"));

        OrderReturn result = orderReturnService.allocateReplacementItem("RET-1", null);

        assertThat(result.getLedgerlyCreditNoteId()).isEqualTo("cn-ledgerly");
        verify(ledgerlyIntegrationClient).createCreditNote(ORDER_NUMBER, "RET-1", new BigDecimal("400.00"));
    }

    @Test
    void allocateReplacementItem_ledgerlyCreditNoteDoesNotInventQtyWhenZero() {
        enableLedgerlyReturnsSync();
        OrderReturn orderReturn = exchangeReturnWithStatus(OrderReturnStatus.ACCEPTED, "TEE-M-BLUE", "M", "Blue");
        orderReturn.setOriginalItemAmount(null);
        orderReturn.getOrderItem().price(new BigDecimal("250.00")).qty(0);
        orderReturn.setReplacementReservedAt(Instant.now());
        orderReturn.setSettlementStatus(ExchangeSettlementStatus.NOT_REQUIRED);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);
        when(ledgerlyIntegrationClient.createCreditNote(eq(ORDER_NUMBER), eq("RET-1"), eq(new BigDecimal("0.00"))))
            .thenReturn(Optional.of("cn-zero"));

        OrderReturn result = orderReturnService.allocateReplacementItem("RET-1", null);

        assertThat(result.getLedgerlyCreditNoteId()).isEqualTo("cn-zero");
        verify(ledgerlyIntegrationClient).createCreditNote(ORDER_NUMBER, "RET-1", new BigDecimal("0.00"));
    }

    @Test
    void settlePriceDifference_negativePrepaid_refundsViaRazorpay() {
        OrderReturn orderReturn = exchangeReturnWithStatus(OrderReturnStatus.ACCEPTED, "TEE-M-BLUE", "M", "Blue");
        orderReturn.getOrder().paymentMethod("online").razorpayPaymentId("pay_live_1").total(new BigDecimal("1200.00"));
        orderReturn.setPriceDifference(new BigDecimal("-300.00"));
        orderReturn.setSettlementStatus(ExchangeSettlementStatus.PENDING);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(
            exchangeRazorpaySettlementService.recordRazorpayRefund(
                "RET-1",
                new BigDecimal("300.00"),
                "admin",
                null
            )
        ).thenReturn(orderReturn);
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);

        OrderReturn result = orderReturnService.settlePriceDifference(
            "RET-1",
            ExchangeSettlementStatus.REFUNDED,
            new BigDecimal("300.00"),
            null,
            null,
            "admin",
            null
        );

        assertThat(result).isSameAs(orderReturn);
        verify(exchangeRazorpaySettlementService).recordRazorpayRefund(
            "RET-1",
            new BigDecimal("300.00"),
            "admin",
            null
        );
        verify(zohoBooksIntegrationClient, never()).createCreditNote(any(), any(), anyMap(), eq(false));
        verify(orderReturnRepository).save(orderReturn);
    }

    @Test
    void settlePriceDifference_razorpay_createsCreditNoteAfterRefundInitiated() {
        ApplicationProperties.Zoho zoho = new ApplicationProperties.Zoho();
        zoho.setSyncReturns(true);
        when(applicationProperties.getZoho()).thenReturn(zoho);
        OrderReturn orderReturn = exchangeReturnWithStatus(OrderReturnStatus.ACCEPTED, "TEE-M-BLUE", "M", "Blue");
        orderReturn.getOrder().paymentMethod("online").razorpayPaymentId("pay_live_1").total(new BigDecimal("1200.00"));
        orderReturn.getOrder().setPaymentStatus(PaymentStatus.REFUND_INITIATED);
        orderReturn.setPriceDifference(new BigDecimal("-300.00"));
        orderReturn.setOriginalItemAmount(new BigDecimal("800.00"));
        orderReturn.setSettlementStatus(ExchangeSettlementStatus.PENDING);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(
            exchangeRazorpaySettlementService.recordRazorpayRefund(
                "RET-1",
                new BigDecimal("300.00"),
                "admin",
                null
            )
        ).thenReturn(orderReturn);
        when(zohoBooksIntegrationClient.createCreditNote(eq(10L), eq("RET-1"), eq(Map.of(5L, 1)), eq(false)))
            .thenReturn(Optional.of("cn-rzp"));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);

        OrderReturn result = orderReturnService.settlePriceDifference(
            "RET-1",
            ExchangeSettlementStatus.REFUNDED,
            new BigDecimal("300.00"),
            null,
            null,
            "admin",
            null
        );

        assertThat(result.getZohoCreditNoteId()).isEqualTo("cn-rzp");
        assertThat(orderReturn.getOrder().getPaymentStatus()).isEqualTo(PaymentStatus.REFUND_INITIATED);
        verify(zohoBooksIntegrationClient).createCreditNote(eq(10L), eq("RET-1"), eq(Map.of(5L, 1)), eq(false));
        verify(orderReturnRepository).save(orderReturn);
    }

    @Test
    void canRefundExchangeDifferenceViaRazorpay_requiresRazorpayPaymentId() {
        OrderReturn orderReturn = exchangeReturnWithStatus(OrderReturnStatus.ACCEPTED, "TEE-M-BLUE", "M", "Blue");
        orderReturn.getOrder().paymentMethod("online");
        orderReturn.setPriceDifference(new BigDecimal("-50.00"));
        orderReturn.setSettlementStatus(ExchangeSettlementStatus.PENDING);

        assertThat(orderReturnService.canRefundExchangeDifferenceViaRazorpay(orderReturn)).isFalse();

        orderReturn.getOrder().razorpayPaymentId("pay_live_1");
        assertThat(orderReturnService.canRefundExchangeDifferenceViaRazorpay(orderReturn)).isTrue();
    }

    @Test
    void settlePriceDifference_negativeCod_requiresManualReference() {
        OrderReturn orderReturn = exchangeReturnWithStatus(OrderReturnStatus.ACCEPTED, "TEE-M-BLUE", "M", "Blue");
        orderReturn.getOrder().paymentMethod("cod");
        orderReturn.setPriceDifference(new BigDecimal("-150.00"));
        orderReturn.setSettlementStatus(ExchangeSettlementStatus.PENDING);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));

        assertThatThrownBy(() ->
            orderReturnService.settlePriceDifference(
                "RET-1",
                ExchangeSettlementStatus.REFUNDED,
                new BigDecimal("150.00"),
                null,
                null,
                "admin",
                null
            )
        )
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Settlement method and transaction reference are required");
        verify(razorpayRefundService, never()).refundPayment(any(), any(), any());
    }

    @Test
    void settlePriceDifference_alreadySettled_throwsConflict() {
        OrderReturn orderReturn = exchangeReturnWithStatus(OrderReturnStatus.ACCEPTED, "TEE-M-BLUE", "M", "Blue");
        orderReturn.setPriceDifference(new BigDecimal("-100.00"));
        orderReturn.setSettlementStatus(ExchangeSettlementStatus.REFUNDED);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));

        assertThatThrownBy(() ->
            orderReturnService.settlePriceDifference(
                "RET-1",
                ExchangeSettlementStatus.REFUNDED,
                new BigDecimal("100.00"),
                "razorpay",
                "rfnd_old",
                "admin",
                null
            )
        )
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("already settled");
        verify(razorpayRefundService, never()).refundPayment(any(), any(), any());
    }

    @Test
    void settlePriceDifference_razorpayFailure_doesNotSettle() {
        OrderReturn orderReturn = exchangeReturnWithStatus(OrderReturnStatus.ACCEPTED, "TEE-M-BLUE", "M", "Blue");
        orderReturn.getOrder().paymentMethod("online").razorpayPaymentId("pay_live_1");
        orderReturn.setPriceDifference(new BigDecimal("-80.00"));
        orderReturn.setSettlementStatus(ExchangeSettlementStatus.PENDING);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(
            exchangeRazorpaySettlementService.recordRazorpayRefund(
                "RET-1",
                new BigDecimal("80.00"),
                "admin",
                null
            )
        ).thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.BAD_GATEWAY, "Refund could not be processed"));

        assertThatThrownBy(() ->
            orderReturnService.settlePriceDifference(
                "RET-1",
                ExchangeSettlementStatus.REFUNDED,
                new BigDecimal("80.00"),
                null,
                null,
                "admin",
                null
            )
        )
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Refund could not be processed");
        assertThat(orderReturn.getSettlementStatus()).isEqualTo(ExchangeSettlementStatus.PENDING);
        verify(orderReturnRepository, never()).save(any());
    }

    @Test
    void settlePriceDifference_forceManual_recordsReferenceWithoutRazorpay() {
        OrderReturn orderReturn = exchangeReturnWithStatus(OrderReturnStatus.ACCEPTED, "TEE-M-BLUE", "M", "Blue");
        orderReturn.getOrder().paymentMethod("online").razorpayPaymentId("pay_live_1").total(new BigDecimal("1200.00"));
        orderReturn.setPriceDifference(new BigDecimal("-300.00"));
        orderReturn.setSettlementStatus(ExchangeSettlementStatus.PENDING);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(orderReturnRepository.findOneByReturnNumberForUpdate("RET-1")).thenReturn(Optional.of(orderReturn));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);

        OrderReturn result = orderReturnService.settlePriceDifference(
            "RET-1",
            ExchangeSettlementStatus.REFUNDED,
            new BigDecimal("300.00"),
            "UPI",
            "UTR-OUTAGE-1",
            "admin",
            "Razorpay timeout",
            true
        );

        assertThat(result.getSettlementStatus()).isEqualTo(ExchangeSettlementStatus.REFUNDED);
        assertThat(result.getSettlementMethod()).isEqualTo("UPI");
        assertThat(result.getSettlementReference()).isEqualTo("UTR-OUTAGE-1");
        verify(exchangeRazorpaySettlementService, never()).recordRazorpayRefund(any(), any(), any(), any());
        verify(razorpayRefundService, never()).refundPayment(any(), any(), any());
        verify(zohoBooksIntegrationClient, never()).createCreditNote(any(), any(), anyMap(), eq(false));
    }

    @Test
    void settlePriceDifference_manualCheaper_createsUnlinkedCreditNote() {
        ApplicationProperties.Zoho zoho = new ApplicationProperties.Zoho();
        zoho.setSyncReturns(true);
        when(applicationProperties.getZoho()).thenReturn(zoho);
        OrderReturn orderReturn = exchangeReturnWithStatus(OrderReturnStatus.ACCEPTED, "TEE-M-BLUE", "M", "Blue");
        orderReturn.getOrder().paymentMethod("cod");
        orderReturn.setPriceDifference(new BigDecimal("-150.00"));
        orderReturn.setSettlementStatus(ExchangeSettlementStatus.PENDING);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(orderReturnRepository.findOneByReturnNumberForUpdate("RET-1")).thenReturn(Optional.of(orderReturn));
        when(zohoBooksIntegrationClient.createCreditNote(eq(10L), eq("RET-1"), anyMap(), eq(false))).thenReturn(Optional.of("cn-cod"));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);

        OrderReturn result = orderReturnService.settlePriceDifference(
            "RET-1",
            ExchangeSettlementStatus.REFUNDED,
            new BigDecimal("150.00"),
            "UPI",
            "UTR-COD-1",
            "admin",
            null
        );

        assertThat(result.getZohoCreditNoteId()).isEqualTo("cn-cod");
        verify(zohoBooksIntegrationClient).createCreditNote(eq(10L), eq("RET-1"), anyMap(), eq(false));
        verify(exchangeRazorpaySettlementService, never()).recordRazorpayRefund(any(), any(), any(), any());
    }

    @Test
    void settlePriceDifference_forceManualWithoutReference_throwsBadRequest() {
        OrderReturn orderReturn = exchangeReturnWithStatus(OrderReturnStatus.ACCEPTED, "TEE-M-BLUE", "M", "Blue");
        orderReturn.getOrder().paymentMethod("online").razorpayPaymentId("pay_live_1");
        orderReturn.setPriceDifference(new BigDecimal("-300.00"));
        orderReturn.setSettlementStatus(ExchangeSettlementStatus.PENDING);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));

        assertThatThrownBy(() ->
            orderReturnService.settlePriceDifference(
                "RET-1",
                ExchangeSettlementStatus.REFUNDED,
                new BigDecimal("300.00"),
                null,
                null,
                "admin",
                null,
                true
            )
        )
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Settlement method and transaction reference are required");
        verify(exchangeRazorpaySettlementService, never()).recordRazorpayRefund(any(), any(), any(), any());
    }

    @Test
    void settlePriceDifference_methodWithoutForceManual_stillUsesRazorpay() {
        OrderReturn orderReturn = exchangeReturnWithStatus(OrderReturnStatus.ACCEPTED, "TEE-M-BLUE", "M", "Blue");
        orderReturn.getOrder().paymentMethod("online").razorpayPaymentId("pay_live_1");
        orderReturn.setPriceDifference(new BigDecimal("-300.00"));
        orderReturn.setSettlementStatus(ExchangeSettlementStatus.PENDING);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(
            exchangeRazorpaySettlementService.recordRazorpayRefund(
                "RET-1",
                new BigDecimal("300.00"),
                "admin",
                null
            )
        ).thenReturn(orderReturn);
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);

        orderReturnService.settlePriceDifference(
            "RET-1",
            ExchangeSettlementStatus.REFUNDED,
            new BigDecimal("300.00"),
            "UPI",
            "UTR-1",
            "admin",
            null
        );

        verify(exchangeRazorpaySettlementService).recordRazorpayRefund(
            "RET-1",
            new BigDecimal("300.00"),
            "admin",
            null
        );
        verify(orderReturnRepository).save(orderReturn);
    }

    @Test
    void convertExchangeToRefund_subtractsAlreadyRefundedPriceDifference() {
        OrderReturn orderReturn = exchangeReturnWithStatus(OrderReturnStatus.ACCEPTED, "TEE-M-BLUE", "M", "Blue");
        orderReturn.setOriginalItemAmount(new BigDecimal("500.00"));
        orderReturn.setSettlementStatus(ExchangeSettlementStatus.REFUNDED);
        orderReturn.setSettlementAmount(new BigDecimal("120.00"));
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(razorpayRefundService.refundPayment(any(), eq(new BigDecimal("380.00")), eq(RazorpayRefundService.PURPOSE_RETURN_REFUND)))
            .thenReturn("rfnd_rest");
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);

        OrderReturn result = orderReturnService.convertExchangeToRefund("RET-1", null);

        assertThat(result.getStatus()).isEqualTo(OrderReturnStatus.REFUNDED);
        assertThat(result.getRefundAmount()).isEqualByComparingTo("380.00");
        verify(razorpayRefundService).refundPayment(
            orderReturn.getOrder(),
            new BigDecimal("380.00"),
            RazorpayRefundService.PURPOSE_RETURN_REFUND
        );
    }

    @Test
    void convertExchangeToRefund_afterPriceDifferenceRazorpay_createsLinkedCreditNote() {
        ApplicationProperties.Zoho zoho = new ApplicationProperties.Zoho();
        zoho.setSyncReturns(true);
        when(applicationProperties.getZoho()).thenReturn(zoho);
        OrderReturn orderReturn = exchangeReturnWithStatus(OrderReturnStatus.ACCEPTED, "TEE-M-BLUE", "M", "Blue");
        orderReturn.setOriginalItemAmount(new BigDecimal("500.00"));
        orderReturn.setSettlementStatus(ExchangeSettlementStatus.REFUNDED);
        orderReturn.setSettlementAmount(new BigDecimal("120.00"));
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(razorpayRefundService.refundPayment(any(), eq(new BigDecimal("380.00")), eq(RazorpayRefundService.PURPOSE_RETURN_REFUND)))
            .thenReturn("rfnd_rest");
        when(zohoBooksIntegrationClient.createCreditNote(eq(10L), eq("RET-1"), anyMap(), eq(true))).thenReturn(Optional.of("cn-linked"));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);
        when(customerOrderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderReturn result = orderReturnService.convertExchangeToRefund("RET-1", null);

        assertThat(result.getZohoCreditNoteId()).isEqualTo("cn-linked");
        verify(zohoBooksIntegrationClient).createCreditNote(eq(10L), eq("RET-1"), anyMap(), eq(true));
        verify(zohoBooksIntegrationClient, never()).createCreditNote(any(), any(), anyMap(), eq(false));
        verify(zohoBooksIntegrationClient).attachCreditNoteToSourceInvoice(eq(10L), eq("RET-1"), eq("cn-linked"), anyMap());
    }

    @Test
    void convertExchangeToRefund_afterCheaperSettleWithExistingNote_attachesToOriginalInvoice() {
        ApplicationProperties.Zoho zoho = new ApplicationProperties.Zoho();
        zoho.setSyncReturns(true);
        when(applicationProperties.getZoho()).thenReturn(zoho);
        OrderReturn orderReturn = exchangeReturnWithStatus(OrderReturnStatus.ACCEPTED, "TEE-M-BLUE", "M", "Blue");
        orderReturn.setOriginalItemAmount(new BigDecimal("500.00"));
        orderReturn.setSettlementStatus(ExchangeSettlementStatus.REFUNDED);
        orderReturn.setSettlementAmount(new BigDecimal("120.00"));
        orderReturn.setZohoCreditNoteId("cn-rzp");
        orderReturn.getOrder().setZohoInvoiceId("inv-orig");
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(razorpayRefundService.refundPayment(any(), eq(new BigDecimal("380.00")), eq(RazorpayRefundService.PURPOSE_RETURN_REFUND)))
            .thenReturn("rfnd_rest");
        when(zohoBooksIntegrationClient.attachCreditNoteToSourceInvoice(eq(10L), eq("RET-1"), eq("cn-rzp"), anyMap()))
            .thenReturn(Optional.of("cn-linked"));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);
        when(customerOrderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderReturn result = orderReturnService.convertExchangeToRefund("RET-1", null);

        assertThat(result.getZohoCreditNoteId()).isEqualTo("cn-linked");
        verify(zohoBooksIntegrationClient).attachCreditNoteToSourceInvoice(eq(10L), eq("RET-1"), eq("cn-rzp"), anyMap());
        verify(zohoBooksIntegrationClient, never()).createCreditNote(any(), any(), anyMap(), eq(true));
        verify(zohoBooksIntegrationClient, never()).createCreditNote(any(), any(), anyMap(), eq(false));
        verify(zohoBooksIntegrationClient, never()).applyCreditNoteToInvoice(any(), any());
    }

    @Test
    void convertExchangeToRefund_reusedUnlinkedNote_attachesToOriginalInvoice() {
        ApplicationProperties.Zoho zoho = new ApplicationProperties.Zoho();
        zoho.setSyncReturns(true);
        when(applicationProperties.getZoho()).thenReturn(zoho);
        OrderReturn orderReturn = exchangeReturnWithStatus(OrderReturnStatus.ACCEPTED, "TEE-M-BLUE", "M", "Blue");
        orderReturn.setOriginalItemAmount(new BigDecimal("500.00"));
        orderReturn.setSettlementStatus(ExchangeSettlementStatus.REFUNDED);
        orderReturn.setSettlementAmount(new BigDecimal("120.00"));
        orderReturn.getOrder().setZohoInvoiceId("inv-orig");
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(razorpayRefundService.refundPayment(any(), eq(new BigDecimal("380.00")), eq(RazorpayRefundService.PURPOSE_RETURN_REFUND)))
            .thenReturn("rfnd_rest");
        when(zohoBooksIntegrationClient.createCreditNote(eq(10L), eq("RET-1"), anyMap(), eq(true))).thenReturn(Optional.of("cn-reused"));
        when(zohoBooksIntegrationClient.attachCreditNoteToSourceInvoice(eq(10L), eq("RET-1"), eq("cn-reused"), anyMap()))
            .thenReturn(Optional.of("cn-linked"));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);
        when(customerOrderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderReturn result = orderReturnService.convertExchangeToRefund("RET-1", null);

        assertThat(result.getZohoCreditNoteId()).isEqualTo("cn-linked");
        verify(zohoBooksIntegrationClient).createCreditNote(eq(10L), eq("RET-1"), anyMap(), eq(true));
        verify(zohoBooksIntegrationClient).attachCreditNoteToSourceInvoice(eq(10L), eq("RET-1"), eq("cn-reused"), anyMap());
    }

    @Test
    void convertExchangeToRefund_equalRemainderDoesNotReuseSettlementRefund() {
        OrderReturn orderReturn = exchangeReturnWithStatus(OrderReturnStatus.ACCEPTED, "TEE-M-BLUE", "M", "Blue");
        orderReturn.setOriginalItemAmount(new BigDecimal("600.00"));
        orderReturn.setSettlementStatus(ExchangeSettlementStatus.REFUNDED);
        orderReturn.setSettlementAmount(new BigDecimal("300.00"));
        orderReturn.setSettlementReference("rfnd_settle");
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(
            razorpayRefundService.findExistingRefundForOrder(
                any(),
                eq(new BigDecimal("300.00")),
                eq(RazorpayRefundService.PURPOSE_RETURN_REFUND)
            )
        )
            .thenReturn(Optional.of("rfnd_settle"));
        when(
            razorpayRefundService.refundPayment(any(), eq(new BigDecimal("300.00")), eq(RazorpayRefundService.PURPOSE_RETURN_REFUND))
        )
            .thenReturn("rfnd_rest");
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);

        OrderReturn result = orderReturnService.convertExchangeToRefund("RET-1", null);

        assertThat(result.getRazorpayRefundId()).isEqualTo("rfnd_rest");
        assertThat(result.getRefundAmount()).isEqualByComparingTo("300.00");
        verify(razorpayRefundService).refundPayment(
            orderReturn.getOrder(),
            new BigDecimal("300.00"),
            RazorpayRefundService.PURPOSE_RETURN_REFUND
        );
    }

    @Test
    void convertExchangeToRefund_alreadyFullySettled_doesNotRefundAgain() {
        OrderReturn orderReturn = exchangeReturnWithStatus(OrderReturnStatus.ACCEPTED, "TEE-M-BLUE", "M", "Blue");
        orderReturn.setOriginalItemAmount(new BigDecimal("120.00"));
        orderReturn.setSettlementStatus(ExchangeSettlementStatus.REFUNDED);
        orderReturn.setSettlementAmount(new BigDecimal("120.00"));
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);

        OrderReturn result = orderReturnService.convertExchangeToRefund("RET-1", null);

        assertThat(result.getStatus()).isEqualTo(OrderReturnStatus.REFUNDED);
        assertThat(result.getRefundAmount()).isEqualByComparingTo("0.00");
        verify(razorpayRefundService, never()).refundPayment(any(), any(), any());
    }

    @Test
    void convertExchangeToRefund_uncollectedCod_createsCreditNote() {
        ApplicationProperties.Zoho zoho = new ApplicationProperties.Zoho();
        zoho.setSyncReturns(true);
        when(applicationProperties.getZoho()).thenReturn(zoho);
        OrderReturn orderReturn = exchangeReturnWithStatus(OrderReturnStatus.ACCEPTED, "TEE-M-BLUE", "M", "Blue");
        orderReturn.getOrder().paymentMethod("cod").paymentStatus(PaymentStatus.PENDING);
        orderReturn.getOrderItem().price(new BigDecimal("250.00")).qty(1);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(zohoBooksIntegrationClient.createCreditNote(eq(10L), eq("RET-1"), anyMap(), eq(true))).thenReturn(Optional.of("cn-cod"));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);

        OrderReturn result = orderReturnService.convertExchangeToRefund("RET-1", null);

        assertThat(result.getStatus()).isEqualTo(OrderReturnStatus.REFUNDED);
        assertThat(result.getZohoCreditNoteId()).isEqualTo("cn-cod");
        verify(razorpayRefundService, never()).refundPayment(any(), any(), any());
        verify(zohoBooksIntegrationClient).createCreditNote(eq(10L), eq("RET-1"), anyMap(), eq(true));
    }

    @Test
    void recordManualCodRefund_success() {
        CustomerOrder order = eligibleOrder();
        order.setPaymentMethod("cod");
        order.setPaymentStatus(PaymentStatus.PAID);
        order.setTotal(new BigDecimal("500.00"));

        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.ACCEPTED);
        orderReturn.setOrder(order);
        orderReturn.setRefundAmount(new BigDecimal("500.00"));

        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(orderReturnItemRepository.findAllByOrderReturnIdOrderById(orderReturn.getId())).thenReturn(List.of());
        when(orderReturnRepository.save(any(OrderReturn.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderReturn result = orderReturnService.recordManualCodRefund(
            "RET-1",
            ManualRefundMethod.BANK_TRANSFER,
            "TXN-BANK-1234",
            "Processed NEFT",
            "finance_admin"
        );

        assertThat(result.getStatus()).isEqualTo(OrderReturnStatus.REFUNDED);
        assertThat(result.getManualRefundMethod()).isEqualTo(ManualRefundMethod.BANK_TRANSFER);
        assertThat(result.getManualRefundReference()).isEqualTo("TXN-BANK-1234");
        assertThat(result.getManualRefundRecordedBy()).isEqualTo("finance_admin");
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    void recordManualCodRefund_exchange_createsCreditNote() {
        ApplicationProperties.Zoho zoho = new ApplicationProperties.Zoho();
        zoho.setSyncReturns(true);
        when(applicationProperties.getZoho()).thenReturn(zoho);
        CustomerOrder order = eligibleOrder();
        order.setPaymentMethod("cod");
        order.setPaymentStatus(PaymentStatus.PAID);
        order.setTotal(new BigDecimal("500.00"));
        OrderReturn orderReturn = exchangeReturnWithStatus(OrderReturnStatus.ACCEPTED, "TEE-M-BLUE", "M", "Blue");
        orderReturn.setOrder(order);
        orderReturn.setRefundAmount(new BigDecimal("400.00"));
        orderReturn.setOriginalItemAmount(new BigDecimal("400.00"));
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(orderReturnItemRepository.findAllByOrderReturnIdOrderById(orderReturn.getId())).thenReturn(List.of());
        when(zohoBooksIntegrationClient.createCreditNote(eq(10L), eq("RET-1"), anyMap(), eq(true))).thenReturn(Optional.of("cn-cod-ex"));
        when(orderReturnRepository.save(any(OrderReturn.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderReturn result = orderReturnService.recordManualCodRefund(
            "RET-1",
            ManualRefundMethod.UPI,
            "UPI-COD-9999",
            null,
            "admin"
        );

        assertThat(result.getStatus()).isEqualTo(OrderReturnStatus.REFUNDED);
        assertThat(result.getZohoCreditNoteId()).isEqualTo("cn-cod-ex");
        verify(zohoBooksIntegrationClient).createCreditNote(eq(10L), eq("RET-1"), anyMap(), eq(true));
        verify(zohoBooksIntegrationClient, never()).createCreditNote(any(), any(), anyMap());
    }

    @Test
    void recordManualCodRefund_nullMethod_throwsBadRequest() {
        assertThatThrownBy(() -> orderReturnService.recordManualCodRefund("RET-1", null, "REF123", "", "admin"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Refund method is required");
    }

    @Test
    void recordManualCodRefund_notCollectedCod_throwsBadRequest() {
        CustomerOrder order = eligibleOrder();
        order.setPaymentMethod("online");

        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.ACCEPTED);
        orderReturn.setOrder(order);

        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));

        assertThatThrownBy(() -> orderReturnService.recordManualCodRefund("RET-1", ManualRefundMethod.UPI, "REF123", "", "admin"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Manual COD refund recording applies only to collected COD orders");
    }

    @Test
    void recordManualCodRefund_alreadyRecorded_throwsConflict() {
        CustomerOrder order = eligibleOrder();
        order.setPaymentMethod("cod");
        order.setPaymentStatus(PaymentStatus.PAID);

        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.ACCEPTED);
        orderReturn.setOrder(order);
        orderReturn.setManualRefundRecordedAt(Instant.now());

        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));

        assertThatThrownBy(() -> orderReturnService.recordManualCodRefund("RET-1", ManualRefundMethod.UPI, "REF123", "", "admin"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("COD refund already recorded for this case");
    }

    @Test
    void recordManualCodRefund_invalidStatus_throwsBadRequest() {
        CustomerOrder order = eligibleOrder();
        order.setPaymentMethod("cod");
        order.setPaymentStatus(PaymentStatus.PAID);

        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.REQUESTED);
        orderReturn.setOrder(order);

        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));

        assertThatThrownBy(() -> orderReturnService.recordManualCodRefund("RET-1", ManualRefundMethod.UPI, "REF123", "", "admin"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Return must be accepted (or retry after a failed refund)");
    }

    @Test
    void closePickupFailed_success() {
        OrderReturn orderReturn = exchangeReturnWithStatus(OrderReturnStatus.PICKUP_FAILED, "TEE-M-BLUE", "M", "Blue");
        orderReturn.setPickupFailureReason("Customer unavailable");
        orderReturn.setReplacementReservedAt(Instant.now());

        Product product = new Product()
            .id(2L)
            .itemStructure("VARIANTS")
            .variantsJson("[{\"sku\":\"TEE-M-BLUE\",\"size\":\"M\",\"color\":\"Blue\",\"rate\":500,\"stock\":1,\"active\":true}]");

        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(productRepository.findOneForInventoryUpdate(2L)).thenReturn(Optional.of(product));
        when(productRepository.save(product)).thenReturn(product);
        when(orderReturnRepository.save(any(OrderReturn.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderReturn result = orderReturnService.closePickupFailed("RET-1", "Closing case");

        assertThat(result.getStatus()).isEqualTo(OrderReturnStatus.CANCELLED);
        assertThat(result.getReplacementReservationReleasedAt()).isNotNull();
        assertThat(result.getRejectionReason()).isEqualTo("Customer unavailable");
    }

    @Test
    void closePickupFailed_wrongStatus_throwsBadRequest() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.APPROVED);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));

        assertThatThrownBy(() -> orderReturnService.closePickupFailed("RET-1", "note"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Only a failed pickup can be closed");
    }

    @Test
    void reschedulePickup_success() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.PICKUP_FAILED);
        orderReturn.setPickupFailureReason("Door locked");

        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(orderReturnRepository.save(any(OrderReturn.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderReturn result = orderReturnService.reschedulePickup("RET-1", "Rescheduling next day");

        assertThat(result.getStatus()).isEqualTo(OrderReturnStatus.APPROVED);
        assertThat(result.getPickupFailureReason()).isNull();
    }

    @Test
    void reschedulePickup_wrongStatus_throwsBadRequest() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.REQUESTED);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));

        assertThatThrownBy(() -> orderReturnService.reschedulePickup("RET-1", "note"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Return is not in pickup failed state");
    }

    @Test
    void markPickupFailed_success() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.PICKUP_REQUESTED);

        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(orderReturnRepository.save(any(OrderReturn.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderReturn result = orderReturnService.markPickupFailed("RET-1", "Customer not reachable", "Admin note");

        assertThat(result.getStatus()).isEqualTo(OrderReturnStatus.PICKUP_FAILED);
        assertThat(result.getPickupFailureReason()).isEqualTo("Customer not reachable");
    }

    @Test
    void markPickupFailed_wrongStatus_throwsBadRequest() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.REQUESTED);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));

        assertThatThrownBy(() -> orderReturnService.markPickupFailed("RET-1", "Customer not reachable", "note"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Pickup must be scheduled before marking a failure");
    }

    @Test
    void acceptAfterInspection_withLineDecisions_calculatesRefundWithoutRestock() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.INSPECTED);
        orderReturn.setId(50L);

        OrderItem orderItem = new OrderItem().id(11L).productId("2").qty(2).price(new BigDecimal("200.00"));
        OrderReturnItem returnItem = new OrderReturnItem();
        returnItem.setId(101L);
        returnItem.setOrderItem(orderItem);
        returnItem.setRequestedQty(2);
        returnItem.setUnitAmount(new BigDecimal("200.00"));
        returnItem.setRefundAmount(new BigDecimal("400.00"));

        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(orderReturnItemRepository.findAllByOrderReturnIdOrderById(50L)).thenReturn(List.of(returnItem));
        when(orderReturnRepository.save(any(OrderReturn.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderReturnService.InspectionLineDecision decision = new OrderReturnService.InspectionLineDecision(
            101L,
            1,
            ReturnDisposition.RESTOCKABLE
        );

        OrderReturn result = orderReturnService.acceptAfterInspection(
            "RET-1",
            ReturnDisposition.RESTOCKABLE,
            List.of(decision),
            "Line inspection done"
        );

        assertThat(result.getStatus()).isEqualTo(OrderReturnStatus.ACCEPTED);
        assertThat(result.getRefundAmount()).isEqualByComparingTo("200.00");
        assertThat(returnItem.getAcceptedQty()).isEqualTo(1);
        verify(productRepository, never()).findOneForInventoryUpdate(any());
        verify(productRepository, never()).save(any());
        verify(zohoBooksIntegrationClient, never()).syncProductStockOnly(any());
    }

    @Test
    void processRefund_acceptedLineDecisions_restocksAcceptedQuantity() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.ACCEPTED);
        orderReturn.setId(50L);
        orderReturn.setDisposition(ReturnDisposition.RESTOCKABLE);
        OrderItem orderItem = new OrderItem().id(11L).productId("2").qty(2).price(new BigDecimal("200.00"));
        OrderReturnItem returnItem = new OrderReturnItem();
        returnItem.setId(101L);
        returnItem.setOrderItem(orderItem);
        returnItem.setRequestedQty(2);
        returnItem.setAcceptedQty(1);
        returnItem.setDisposition(ReturnDisposition.RESTOCKABLE);
        returnItem.setRefundAmount(new BigDecimal("200.00"));
        Product product = new Product().id(2L).stock(10);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(orderReturnItemRepository.findAllByOrderReturnIdOrderById(50L)).thenReturn(List.of(returnItem));
        when(productRepository.findOneForInventoryUpdate(2L)).thenReturn(Optional.of(product));
        when(productRepository.save(product)).thenReturn(product);
        when(razorpayRefundService.refundPayment(any(), any(), any())).thenReturn("rfnd_demo");
        when(orderReturnRepository.save(any(OrderReturn.class))).thenAnswer(inv -> inv.getArgument(0));
        when(customerOrderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        orderReturnService.processRefund("RET-1", null);

        assertThat(product.getStock()).isEqualTo(11);
    }

    @Test
    void acceptAfterInspection_invalidAcceptedQty_throwsBadRequest() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.INSPECTED);
        orderReturn.setId(50L);

        OrderReturnItem returnItem = new OrderReturnItem();
        returnItem.setId(101L);
        returnItem.setRequestedQty(2);

        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(orderReturnItemRepository.findAllByOrderReturnIdOrderById(50L)).thenReturn(List.of(returnItem));

        OrderReturnService.InspectionLineDecision decision = new OrderReturnService.InspectionLineDecision(
            101L,
            5,
            ReturnDisposition.RESTOCKABLE
        );

        List<OrderReturnService.InspectionLineDecision> decisions = List.of(decision);
        assertThatThrownBy(() -> orderReturnService.acceptAfterInspection("RET-1", ReturnDisposition.RESTOCKABLE, decisions, null))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Accepted quantity must be within the requested quantity");
    }

    @Test
    void acceptAfterInspection_noUnitsAccepted_throwsBadRequest() {
        OrderReturn orderReturn = returnWithStatus(OrderReturnStatus.INSPECTED);
        orderReturn.setId(50L);

        OrderReturnItem returnItem = new OrderReturnItem();
        returnItem.setId(101L);
        returnItem.setRequestedQty(2);
        returnItem.setRefundAmount(new BigDecimal("400.00"));

        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(orderReturnItemRepository.findAllByOrderReturnIdOrderById(50L)).thenReturn(List.of(returnItem));

        OrderReturnService.InspectionLineDecision decision = new OrderReturnService.InspectionLineDecision(
            101L,
            0,
            ReturnDisposition.DISCARD
        );

        List<OrderReturnService.InspectionLineDecision> decisions = List.of(decision);
        assertThatThrownBy(() -> orderReturnService.acceptAfterInspection("RET-1", ReturnDisposition.DISCARD, decisions, null))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("At least one returned unit must be accepted");
    }

    @Test
    void bookOutbound_manualCarrier_success() {
        OrderReturn orderReturn = packedExchange(OrderReturnStatus.READY_TO_SHIP);
        orderReturn.setSettlementStatus(ExchangeSettlementStatus.NOT_REQUIRED);

        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(orderReturnRepository.save(any(OrderReturn.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderReturn result = orderReturnService.bookOutbound(
            "RET-1",
            ReturnLogisticsMode.MANUAL,
            "BlueDart",
            "BD-AWB-12345",
            "https://bluedart.com/track",
            "https://bluedart.com/label",
            "Booked manual dispatch"
        );

        assertThat(result.getOutboundLogisticsMode()).isEqualTo(ReturnLogisticsMode.MANUAL);
        assertThat(result.getOutboundCarrierName()).isEqualTo("BlueDart");
        assertThat(result.getOutboundAwb()).isEqualTo("BD-AWB-12345");
        assertThat(result.getStatus()).isEqualTo(OrderReturnStatus.SHIPPED);
    }

    @Test
    void bookOutbound_missingAwb_throwsBadRequest() {
        OrderReturn orderReturn = packedExchange(OrderReturnStatus.READY_TO_SHIP);
        orderReturn.setSettlementStatus(ExchangeSettlementStatus.NOT_REQUIRED);

        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));

        assertThatThrownBy(() -> orderReturnService.bookOutbound(
            "RET-1",
            ReturnLogisticsMode.MANUAL,
            "BlueDart",
            "   ",
            null,
            null,
            null
        ))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Outbound AWB or tracking reference is required");
    }

    @Test
    void bookOutbound_pendingSettlement_throwsConflict() {
        OrderReturn orderReturn = exchangeReturnWithStatus(OrderReturnStatus.READY_TO_SHIP, "TEE-M-BLUE", "M", "Blue");
        orderReturn.setSettlementStatus(ExchangeSettlementStatus.PENDING);

        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));

        assertThatThrownBy(() -> orderReturnService.bookOutbound(
            "RET-1",
            ReturnLogisticsMode.MANUAL,
            "BlueDart",
            "BD-123",
            null,
            null,
            null
        ))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Settle the exchange price difference before booking outbound");
    }

    @Test
    void bookOutbound_xpressbees_unconfigured_throwsServiceUnavailable() {
        OrderReturn orderReturn = packedExchange(OrderReturnStatus.READY_TO_SHIP);
        orderReturn.setSettlementStatus(ExchangeSettlementStatus.NOT_REQUIRED);

        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(xpressBeesClient.isForwardShipmentConfigured()).thenReturn(false);

        assertThatThrownBy(() -> orderReturnService.bookOutbound(
            "RET-1",
            ReturnLogisticsMode.XPRESSBEES,
            null,
            "pre-awb",
            null,
            null,
            null
        ))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("ExpressBees forward shipment is not configured");
    }

    @Test
    void bookOutbound_xpressbees_configured_success() throws Exception {
        OrderReturn orderReturn = packedExchange(OrderReturnStatus.READY_TO_SHIP);
        orderReturn.setSettlementStatus(ExchangeSettlementStatus.NOT_REQUIRED);
        orderReturn.getOrder().setShippingAddressJson("{\"name\":\"Jane\",\"phone\":\"9999999999\",\"line1\":\"Street 1\",\"city\":\"City\",\"state\":\"State\",\"pincode\":\"560001\"}");

        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(xpressBeesClient.isForwardShipmentConfigured()).thenReturn(true);
        when(applicationProperties.getXpressbees()).thenReturn(new ApplicationProperties.XpressBees());

        XpressBeesClient.ForwardShipmentResult forwardResult = new XpressBeesClient.ForwardShipmentResult(
            "XB-FWD-123",
            "https://track.example.com",
            "https://label.example.com",
            "req-456",
            "success"
        );
        when(xpressBeesClient.createForwardShipment(any())).thenReturn(forwardResult);
        when(orderReturnRepository.save(any(OrderReturn.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderReturn result = orderReturnService.bookOutbound(
            "RET-1",
            ReturnLogisticsMode.XPRESSBEES,
            null,
            "pre-awb",
            null,
            null,
            "Outbound booked"
        );

        assertThat(result.getStatus()).isEqualTo(OrderReturnStatus.READY_TO_SHIP);
        assertThat(result.getOutboundCarrierStatus()).isEqualTo("awaiting_pickup");
        assertThat(result.getOutboundAwb()).isEqualTo("XB-FWD-123");
    }

    @Test
    void bookOutbound_rejectsWhenPackageIncomplete() {
        OrderReturn orderReturn = exchangeReturnWithStatus(OrderReturnStatus.READY_TO_SHIP, "TEE-M-BLUE", "M", "Blue");
        orderReturn.setSettlementStatus(ExchangeSettlementStatus.NOT_REQUIRED);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));

        assertThatThrownBy(() ->
            orderReturnService.bookOutbound(
                "RET-1",
                ReturnLogisticsMode.MANUAL,
                "Delhivery",
                "AWB-1",
                null,
                null,
                null
            )
        )
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Create a package before booking a shipment");
    }

    @ParameterizedTest
    @EnumSource(value = OrderReturnStatus.class, names = { "ITEM_ALLOCATED", "REPACKING" })
    void completeOutboundPackage_fromPackingStatuses_movesToReadyToShip(OrderReturnStatus fromStatus) {
        OrderReturn orderReturn = exchangeReturnWithStatus(fromStatus, "TEE-M-BLUE", "M", "Blue");
        stubOutboundPackageRates(orderReturn);

        OrderReturn result = orderReturnService.completeOutboundPackage(
            "RET-1",
            500,
            12,
            10,
            8,
            "1",
            "Surface Xpressbees 0.5 K.G",
            "box"
        );

        assertThat(result.getStatus()).isEqualTo(OrderReturnStatus.READY_TO_SHIP);
        assertThat(result.getPackedAt()).isNotNull();
        assertThat(result.getPackageWeightGrams()).isEqualTo(500);
        assertThat(result.getPackageLengthCm()).isEqualTo(12);
        assertThat(result.getPackageType()).isEqualTo(PackageType.BOX);
        assertThat(result.getPackageCourierId()).isEqualTo("1");
    }

    @Test
    void bookOutbound_xpressBees_usesSavedPackageDimensions() throws Exception {
        OrderReturn orderReturn = packedExchange(OrderReturnStatus.READY_TO_SHIP);
        orderReturn.setPackageCourierId("2");
        orderReturn.setPackageWeightGrams(750);
        orderReturn.setPackageLengthCm(20);
        orderReturn.setPackageBreadthCm(15);
        orderReturn.setPackageHeightCm(10);
        orderReturn
            .getOrder()
            .setShippingAddressJson(
                "{\"full_name\":\"Jane Doe\",\"phone\":\"9876543210\",\"line1\":\"12 MG Road\",\"city\":\"Bengaluru\",\"state\":\"Karnataka\",\"pincode\":\"560001\"}"
            );
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(xpressBeesClient.isForwardShipmentConfigured()).thenReturn(true);
        when(xpressBeesClient.createForwardShipment(any())).thenReturn(
            new XpressBeesClient.ForwardShipmentResult("OUT123", "https://track/out", "https://label/out", "REQ", "ok")
        );
        when(xpressBeesClient.buildTrackingUrl("OUT123")).thenReturn("https://track/out");
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);

        orderReturnService.bookOutbound("RET-1", ReturnLogisticsMode.XPRESSBEES, null, null, null, null, null);

        ArgumentCaptor<XpressBeesClient.ForwardShipmentRequest> captor = ArgumentCaptor.forClass(
            XpressBeesClient.ForwardShipmentRequest.class
        );
        verify(xpressBeesClient).createForwardShipment(captor.capture());
        assertThat(captor.getValue().packageWeightGrams()).isEqualTo(750);
        assertThat(captor.getValue().packageLengthCm()).isEqualTo(20);
        assertThat(captor.getValue().packageBreadthCm()).isEqualTo(15);
        assertThat(captor.getValue().packageHeightCm()).isEqualTo(10);
        assertThat(captor.getValue().courierId()).isEqualTo("2");
        assertThat(captor.getValue().paymentType()).isEqualTo("prepaid");
    }

    @Test
    void returnLineBreakdown_cases() {
        assertThat(orderReturnService.returnLineBreakdown(null)).isEmpty();
        assertThat(orderReturnService.returnLineBreakdown(new OrderReturn())).isEmpty();

        OrderReturn ret = new OrderReturn();
        ret.setId(99L);
        OrderReturnItem item = new OrderReturnItem();
        item.setId(1L);
        when(orderReturnItemRepository.findAllByOrderReturnIdOrderById(99L)).thenReturn(List.of(item));

        List<OrderReturnItem> items = orderReturnService.returnLineBreakdown(ret);
        assertThat(items).containsExactly(item);
    }

    @Test
    void bookPickup_xpressbees_returnRequestType_linesFromItems() throws Exception {
        OrderReturn approved = returnWithStatus(OrderReturnStatus.APPROVED);
        approved.setId(77L);
        approved.setRequestType(OrderRequestType.RETURN);
        CustomerOrder order = approved.getOrder();
        order.setShippingAddressJson("{\"name\":\"Jane\",\"phone\":\"9999999999\",\"line1\":\"Street 1\",\"city\":\"City\",\"state\":\"State\",\"pincode\":\"560001\"}");

        OrderItem orderItem = new OrderItem().id(1L).productName("Jeans").productSlug("jeans").price(BigDecimal.valueOf(1000)).qty(1);
        OrderReturnItem returnItem = new OrderReturnItem();
        returnItem.setId(10L);
        returnItem.setOrderItem(orderItem);
        returnItem.setRequestedQty(1);
        returnItem.setUnitAmount(BigDecimal.valueOf(1000));

        when(orderReturnRepository.findOneByReturnNumber("RET-77")).thenReturn(Optional.of(approved));
        when(orderReturnItemRepository.findAllByOrderReturnIdOrderById(77L)).thenReturn(List.of(returnItem));
        when(xpressBeesClient.isReversePickupConfigured()).thenReturn(true);
        when(applicationProperties.getXpressbees()).thenReturn(new ApplicationProperties.XpressBees());

        XpressBeesClient.ReversePickupResult pickupResult = new XpressBeesClient.ReversePickupResult(
            "XB-AWB-777",
            "https://track.example.com",
            "10:00-14:00",
            "req-777",
            "success"
        );
        when(xpressBeesClient.createReversePickup(any())).thenReturn(pickupResult);
        when(orderReturnRepository.save(any(OrderReturn.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderReturn result = orderReturnService.bookPickup("RET-77", ReturnLogisticsMode.XPRESSBEES, null, null, null, null, "booked");
        assertThat(result.getReverseAwb()).isEqualTo("XB-AWB-777");
    }

    @Test
    void markRepackingAndReadyToShip_transitionsCorrectly() {
        OrderReturn orderReturn = exchangeReturnWithStatus(OrderReturnStatus.ITEM_ALLOCATED, "TEE-M-BLUE", "M", "Blue");
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(orderReturnRepository.save(any(OrderReturn.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderReturn repacking = orderReturnService.markRepacking("RET-1", "Starting repack");
        assertThat(repacking.getStatus()).isEqualTo(OrderReturnStatus.REPACKING);

        stampExchangeOutboundPackage(orderReturn);
        OrderReturn ready = orderReturnService.markReadyToShip("RET-1", "Packaged");
        assertThat(ready.getStatus()).isEqualTo(OrderReturnStatus.READY_TO_SHIP);
    }

    @Test
    void ensurePickupScheduled_cases() {
        // already scheduled
        OrderReturn scheduled = returnWithStatus(OrderReturnStatus.PICKUP_SCHEDULED);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(scheduled));
        assertThat(orderReturnService.ensurePickupScheduled("RET-1", null).getStatus()).isEqualTo(OrderReturnStatus.PICKUP_SCHEDULED);

        // requested status (not approved or pickup_failed)
        OrderReturn requested = returnWithStatus(OrderReturnStatus.REQUESTED);
        when(orderReturnRepository.findOneByReturnNumber("RET-2")).thenReturn(Optional.of(requested));
        assertThat(orderReturnService.ensurePickupScheduled("RET-2", null).getStatus()).isEqualTo(OrderReturnStatus.REQUESTED);

        // approved status -> transitions to PICKUP_SCHEDULED
        OrderReturn approved = returnWithStatus(OrderReturnStatus.APPROVED);
        when(orderReturnRepository.findOneByReturnNumber("RET-3")).thenReturn(Optional.of(approved));
        when(orderReturnRepository.save(any(OrderReturn.class))).thenAnswer(inv -> inv.getArgument(0));
        OrderReturn res = orderReturnService.ensurePickupScheduled("RET-3", "scheduled note");
        assertThat(res.getStatus()).isEqualTo(OrderReturnStatus.PICKUP_SCHEDULED);
    }

    @Test
    void bookPickup_xpressbees_unconfigured_throwsServiceUnavailable() {
        OrderReturn approved = returnWithStatus(OrderReturnStatus.APPROVED);
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(approved));
        when(xpressBeesClient.isReversePickupConfigured()).thenReturn(false);

        assertThatThrownBy(() -> orderReturnService.bookPickup("RET-1", ReturnLogisticsMode.XPRESSBEES, null, null, null, null, null))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("ExpressBees reverse pickup is not configured");
    }

    @Test
    void bookPickup_xpressbees_configured_success() throws Exception {
        OrderReturn approved = returnWithStatus(OrderReturnStatus.APPROVED);
        CustomerOrder order = approved.getOrder();
        order.setShippingAddressJson("{\"name\":\"Jane\",\"phone\":\"9999999999\",\"line1\":\"Street 1\",\"city\":\"City\",\"state\":\"State\",\"pincode\":\"560001\"}");

        OrderItem item = new OrderItem().id(50L).productName("T-Shirt").productSlug("t-shirt").price(BigDecimal.valueOf(500)).qty(2);
        approved.setOrderItem(item);

        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(approved));
        when(xpressBeesClient.isReversePickupConfigured()).thenReturn(true);
        when(applicationProperties.getXpressbees()).thenReturn(new ApplicationProperties.XpressBees());

        XpressBeesClient.ReversePickupResult pickupResult = new XpressBeesClient.ReversePickupResult(
            "XB-AWB-999",
            "https://track.example.com",
            "10:00-14:00",
            "req-123",
            "success"
        );
        when(xpressBeesClient.createReversePickup(any())).thenReturn(pickupResult);
        when(orderReturnRepository.save(any(OrderReturn.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderReturn result = orderReturnService.bookPickup(
            "RET-1",
            ReturnLogisticsMode.XPRESSBEES,
            null,
            "pre-awb",
            null,
            null,
            "Pickup booked"
        );

        assertThat(result.getStatus()).isEqualTo(OrderReturnStatus.PICKUP_SCHEDULED);
        assertThat(result.getReverseAwb()).isEqualTo("XB-AWB-999");
        assertThat(result.getProviderRequestId()).isEqualTo("req-123");
    }

    @Test
    void reverseItemStock_reflectionCoverage() {
        Product variantProduct = new Product()
            .id(2L)
            .itemStructure("VARIANTS")
            .variantsJson("[{\"sku\":\"SKU-M\",\"size\":\"M\",\"color\":\"Red\",\"rate\":500,\"stock\":5,\"active\":true}]");

        Product simpleProduct = new Product()
            .id(3L)
            .itemStructure("SIMPLE")
            .stock(10);

        OrderItem variantItem = new OrderItem().productId("2").variantSku("SKU-M").itemSize("M").color("Red").qty(2);
        OrderItem simpleItem = new OrderItem().productId("3").qty(3);
        OrderItem emptyItem = new OrderItem().productId("").qty(1);
        OrderItem invalidItem = new OrderItem().productId("invalid-id").qty(1);

        when(productRepository.findOneForInventoryUpdate(2L)).thenReturn(Optional.of(variantProduct));
        when(productRepository.findOneForInventoryUpdate(3L)).thenReturn(Optional.of(simpleProduct));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        Boolean variantOk = org.springframework.test.util.ReflectionTestUtils.invokeMethod(orderReturnService, "reverseItemStock", variantItem);
        Boolean simpleOk = org.springframework.test.util.ReflectionTestUtils.invokeMethod(orderReturnService, "reverseItemStock", simpleItem);
        Boolean emptyOk = org.springframework.test.util.ReflectionTestUtils.invokeMethod(orderReturnService, "reverseItemStock", emptyItem);
        Boolean invalidOk = org.springframework.test.util.ReflectionTestUtils.invokeMethod(orderReturnService, "reverseItemStock", invalidItem);

        assertThat(variantOk).isTrue();
        assertThat(simpleOk).isTrue();
        assertThat(emptyOk).isTrue();
        assertThat(invalidOk).isTrue();
    }

    private void stubOutboundPackageRates(OrderReturn orderReturn) {
        orderReturn
            .getOrder()
            .setShippingAddressJson(
                "{\"full_name\":\"Jane Doe\",\"phone\":\"9876543210\",\"line1\":\"12 MG Road\",\"city\":\"Bengaluru\",\"state\":\"Karnataka\",\"pincode\":\"560001\"}"
            );
        when(orderReturnRepository.findOneByReturnNumber("RET-1")).thenReturn(Optional.of(orderReturn));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);
        when(
            orderPackageService.previewRatesForDestination(eq("560001"), eq(500), eq(12), eq(10), eq(8), eq(false), any())
        ).thenReturn(
            Map.of(
                "chargeable_weight_grams",
                500,
                "preferred_courier_id",
                "1",
                "preferred_courier_name",
                "Surface Xpressbees 0.5 K.G",
                "couriers",
                List.of(Map.of("id", "1", "name", "Surface Xpressbees 0.5 K.G"))
            )
        );
        when(orderPackageService.selectCourier(any(), eq("1"), eq("Surface Xpressbees 0.5 K.G"))).thenReturn(
            new OrderPackageService.CourierSelection("1", "Surface Xpressbees 0.5 K.G", 500)
        );
    }

    private void stampExchangeOutboundPackage(OrderReturn orderReturn) {
        orderReturn.setPackedAt(Instant.now());
        orderReturn.setPackageWeightGrams(500);
        orderReturn.setPackageLengthCm(12);
        orderReturn.setPackageBreadthCm(10);
        orderReturn.setPackageHeightCm(8);
        orderReturn.setPackageType(PackageType.CUSTOM);
    }

    private OrderReturn packedExchange(OrderReturnStatus status) {
        OrderReturn orderReturn = exchangeReturnWithStatus(status, "TEE-M-BLUE", "M", "Blue");
        stampExchangeOutboundPackage(orderReturn);
        return orderReturn;
    }

    private OrderReturn exchangeReturnWithStatus(OrderReturnStatus status, String replacementSku, String size, String color) {
        OrderReturn orderReturn = returnWithStatus(status);
        orderReturn.setRequestType(OrderRequestType.EXCHANGE);
        OrderItem item = new OrderItem().id(5L).productId("2").qty(1).itemSize("S").color("Red").price(BigDecimal.TEN);
        orderReturn.setOrderItem(item);
        orderReturn.setReplacementVariantSku(replacementSku);
        orderReturn.setReplacementSize(size);
        orderReturn.setReplacementColor(color);
        return orderReturn;
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
        orderReturn.setReason("Product arrived damaged in transit");
        orderReturn.setRequestedAt(Instant.now());
        orderReturn.setOrder(eligibleOrder());
        return orderReturn;
    }

    private OrderReturn returnWithReverseShipment(OrderReturnStatus status) {
        OrderReturn orderReturn = returnWithStatus(status);
        orderReturn.setLogisticsMode(ReturnLogisticsMode.MANUAL);
        orderReturn.setReverseAwb("AWB-REV-1");
        return orderReturn;
    }
}
