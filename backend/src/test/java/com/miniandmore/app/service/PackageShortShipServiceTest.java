package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.miniandmore.app.client.LedgerlyIntegrationClient;
import com.miniandmore.app.client.ZohoBooksIntegrationClient;
import com.miniandmore.app.config.ApplicationProperties;
import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.OrderItem;
import com.miniandmore.app.domain.OrderReturn;
import com.miniandmore.app.domain.Product;
import com.miniandmore.app.domain.enumeration.OrderRequestType;
import com.miniandmore.app.domain.enumeration.OrderReturnStatus;
import com.miniandmore.app.domain.enumeration.PaymentStatus;
import com.miniandmore.app.repository.CustomerOrderRepository;
import com.miniandmore.app.repository.OrderItemRepository;
import com.miniandmore.app.repository.ProductRepository;
import java.math.BigDecimal;
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
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class PackageShortShipServiceTest {

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private CustomerOrderRepository customerOrderRepository;

    @Mock
    private ProductRepository productRepository;

    @Spy
    private ProductVariantSupport variantSupport = StorePricingTestSupport.variantSupport(new com.fasterxml.jackson.databind.ObjectMapper());

    @Mock
    private RazorpayRefundService razorpayRefundService;

    @Mock
    private ZohoBooksIntegrationClient zohoBooksIntegrationClient;

    @Mock
    private LedgerlyIntegrationClient ledgerlyIntegrationClient;

    @Mock
    private OrderMailService orderMailService;

    @Mock
    private ApplicationProperties applicationProperties;

    @InjectMocks
    private PackageShortShipService service;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(applicationProperties.getZoho()).thenReturn(new ApplicationProperties.Zoho());
        org.mockito.Mockito.lenient().when(applicationProperties.getLedgerly()).thenReturn(new ApplicationProperties.Ledgerly());
        org.mockito.Mockito.lenient().when(customerOrderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));
        org.mockito.Mockito.lenient().when(orderItemRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        org.mockito.Mockito.lenient().when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void applyForwardShortShip_prepaid_refundsUnpackedUnitsAndRestocks() {
        CustomerOrder order = prepaidOrder();
        OrderItem item = line(11L, 2, "199.00");
        Product product = new Product().id(5L).stock(3);
        when(productRepository.findOneForInventoryUpdate(5L)).thenReturn(Optional.of(product));
        when(razorpayRefundService.findExistingRefundForOrder(eq(order), any(), eq(RazorpayRefundService.PURPOSE_PACKAGE_SHORT_SHIP)))
            .thenReturn(Optional.empty());
        when(razorpayRefundService.refundPayment(eq(order), any(), eq(RazorpayRefundService.PURPOSE_PACKAGE_SHORT_SHIP)))
            .thenReturn("rfnd_short");

        service.applyForwardShortShip(order, List.of(item), List.of(new PackageShortShipService.PackedLine(11L, 1)));

        assertThat(item.getPackedQty()).isEqualTo(1);
        assertThat(order.getPackageShortRefundAmount()).isEqualByComparingTo("199.00");
        assertThat(order.getPackageShortRazorpayRefundId()).isEqualTo("rfnd_short");
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.REFUND_INITIATED);
        assertThat(product.getStock()).isEqualTo(4);
        verify(razorpayRefundService).refundPayment(order, new BigDecimal("199.00"), RazorpayRefundService.PURPOSE_PACKAGE_SHORT_SHIP);
    }

    @Test
    void applyForwardShortShip_uncollectedCod_reducesCollectableTotal() {
        CustomerOrder order = prepaidOrder().paymentMethod("cod").paymentStatus(PaymentStatus.PENDING).total(new BigDecimal("398.00"));
        OrderItem item = line(11L, 2, "199.00");

        service.applyForwardShortShip(order, List.of(item), List.of(new PackageShortShipService.PackedLine(11L, 1)));

        assertThat(item.getPackedQty()).isEqualTo(1);
        assertThat(order.getTotal()).isEqualByComparingTo("199.00");
        verify(razorpayRefundService, never()).refundPayment(any(), any(), any());
    }

    @Test
    void applyForwardShortShip_rejectsPackingZeroUnits() {
        CustomerOrder order = prepaidOrder();
        OrderItem item = line(11L, 1, "199.00");

        assertThatThrownBy(
            () -> service.applyForwardShortShip(order, List.of(item), List.of(new PackageShortShipService.PackedLine(11L, 0)))
        )
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void applyForwardShortShip_fullPack_doesNotRefund() {
        CustomerOrder order = prepaidOrder();
        OrderItem item = line(11L, 2, "199.00");

        service.applyForwardShortShip(order, List.of(item), null);

        assertThat(item.getPackedQty()).isEqualTo(2);
        assertThat(order.getPackageShortRefundAmount()).isNull();
        verify(razorpayRefundService, never()).refundPayment(any(), any(), any());
    }

    @Test
    void applyExchangeShortShip_refundsUnpackedReplacementUnits() {
        CustomerOrder order = prepaidOrder();
        OrderItem item = line(11L, 2, "199.00");
        OrderReturn exchange = new OrderReturn();
        exchange.setRequestType(OrderRequestType.EXCHANGE);
        exchange.setStatus(OrderReturnStatus.ITEM_ALLOCATED);
        exchange.setReturnNumber("RET-1");
        exchange.setOrder(order);
        exchange.setOrderItem(item);
        exchange.setOriginalItemAmount(new BigDecimal("398.00"));
        exchange.setReplacementVariantSku("SKU-NEW");
        Product product = new Product().id(5L).stock(0);
        when(productRepository.findOneForInventoryUpdate(5L)).thenReturn(Optional.of(product));
        when(razorpayRefundService.findExistingRefundForOrder(eq(order), any(), eq(RazorpayRefundService.PURPOSE_PACKAGE_SHORT_SHIP)))
            .thenReturn(Optional.empty());
        when(razorpayRefundService.refundPayment(eq(order), any(), eq(RazorpayRefundService.PURPOSE_PACKAGE_SHORT_SHIP)))
            .thenReturn("rfnd_ex");

        service.applyExchangeShortShip(exchange, 1);

        assertThat(exchange.getOutboundPackedQty()).isEqualTo(1);
        assertThat(exchange.getPackageShortRefundAmount()).isEqualByComparingTo("199.00");
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.REFUND_INITIATED);
        assertThat(product.getStock()).isEqualTo(1);
    }

    @Test
    void applyForwardShortShip_secondSaveWithSamePackedQty_isIdempotent() {
        CustomerOrder order = prepaidOrder();
        OrderItem item = line(11L, 2, "199.00");
        item.setPackedQty(1);
        order.setPackageShortRefundAmount(new BigDecimal("199.00"));

        service.applyForwardShortShip(order, List.of(item), List.of(new PackageShortShipService.PackedLine(11L, 1)));

        assertThat(item.getPackedQty()).isEqualTo(1);
        verify(razorpayRefundService, never()).refundPayment(any(), any(), any());
    }

    @Test
    void applyForwardShortShip_changingPackedQtyAfterSave_throwsConflict() {
        CustomerOrder order = prepaidOrder();
        OrderItem item = line(11L, 2, "199.00");
        item.setPackedQty(1);

        assertThatThrownBy(
            () -> service.applyForwardShortShip(order, List.of(item), List.of(new PackageShortShipService.PackedLine(11L, 2)))
        )
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void applyForwardShortShip_duplicatePackedLine_throwsBadRequest() {
        CustomerOrder order = prepaidOrder();
        OrderItem item = line(11L, 2, "199.00");

        assertThatThrownBy(
            () ->
                service.applyForwardShortShip(
                    order,
                    List.of(item),
                    List.of(
                        new PackageShortShipService.PackedLine(11L, 1),
                        new PackageShortShipService.PackedLine(11L, 1)
                    )
                )
        )
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void applyExchangeShortShip_secondCallWithSameQty_isIdempotent() {
        CustomerOrder order = prepaidOrder();
        OrderItem item = line(11L, 2, "199.00");
        OrderReturn exchange = new OrderReturn();
        exchange.setOrder(order);
        exchange.setOrderItem(item);
        exchange.setOutboundPackedQty(1);
        exchange.setPackageShortRefundAmount(new BigDecimal("199.00"));

        service.applyExchangeShortShip(exchange, 1);

        assertThat(exchange.getOutboundPackedQty()).isEqualTo(1);
        verify(razorpayRefundService, never()).refundPayment(any(), any(), any());
    }

    @Test
    void applyForwardShortShip_multiLineWithOrderDiscount_refundsProratedNetAmount() {
        CustomerOrder order = prepaidOrder();
        order.setSubtotal(new BigDecimal("300.00"));
        order.setDiscount(new BigDecimal("30.00"));
        order.setTotal(new BigDecimal("270.00"));
        OrderItem lineA = line(11L, 2, "100.00");
        OrderItem lineB = line(12L, 1, "100.00");
        when(razorpayRefundService.findExistingRefundForOrder(eq(order), any(), eq(RazorpayRefundService.PURPOSE_PACKAGE_SHORT_SHIP)))
            .thenReturn(Optional.empty());
        when(razorpayRefundService.refundPayment(eq(order), eq(new BigDecimal("90.00")), eq(RazorpayRefundService.PURPOSE_PACKAGE_SHORT_SHIP)))
            .thenReturn("rfnd_multi");

        service.applyForwardShortShip(
            order,
            List.of(lineA, lineB),
            List.of(new PackageShortShipService.PackedLine(11L, 1), new PackageShortShipService.PackedLine(12L, 1))
        );

        assertThat(lineA.getPackedQty()).isEqualTo(1);
        assertThat(lineB.getPackedQty()).isEqualTo(1);
        assertThat(order.getPackageShortRefundAmount()).isEqualByComparingTo("90.00");
        verify(razorpayRefundService).refundPayment(order, new BigDecimal("90.00"), RazorpayRefundService.PURPOSE_PACKAGE_SHORT_SHIP);
    }

    @Test
    void applyForwardShortShip_multiLineWithPersistedDiscountAllocations_refundsNetPerShortQty() {
        CustomerOrder order = prepaidOrder();
        order.setSubtotal(new BigDecimal("300.00"));
        order.setDiscount(new BigDecimal("30.00"));
        order.setTotal(new BigDecimal("270.00"));
        OrderItem lineA = line(11L, 2, "100.00");
        lineA.setDiscountAllocation(new BigDecimal("20.00"));
        OrderItem lineB = line(12L, 1, "100.00");
        lineB.setDiscountAllocation(new BigDecimal("10.00"));
        when(razorpayRefundService.findExistingRefundForOrder(eq(order), any(), eq(RazorpayRefundService.PURPOSE_PACKAGE_SHORT_SHIP)))
            .thenReturn(Optional.empty());
        when(razorpayRefundService.refundPayment(eq(order), eq(new BigDecimal("90.00")), eq(RazorpayRefundService.PURPOSE_PACKAGE_SHORT_SHIP)))
            .thenReturn("rfnd_alloc");

        service.applyForwardShortShip(order, List.of(lineA, lineB), List.of(new PackageShortShipService.PackedLine(11L, 1)));

        assertThat(order.getPackageShortRefundAmount()).isEqualByComparingTo("90.00");
    }

    @Test
    void applyForwardShortShip_withZohoSync_createsLinkedCreditNoteForShortQuantities() {
        ApplicationProperties.Zoho zoho = new ApplicationProperties.Zoho();
        zoho.setSyncReturns(true);
        when(applicationProperties.getZoho()).thenReturn(zoho);
        CustomerOrder order = prepaidOrder();
        OrderItem item = line(11L, 2, "199.00");
        when(razorpayRefundService.findExistingRefundForOrder(eq(order), any(), eq(RazorpayRefundService.PURPOSE_PACKAGE_SHORT_SHIP)))
            .thenReturn(Optional.empty());
        when(razorpayRefundService.refundPayment(eq(order), any(), eq(RazorpayRefundService.PURPOSE_PACKAGE_SHORT_SHIP)))
            .thenReturn("rfnd_short");
        when(zohoBooksIntegrationClient.createCreditNote(eq(10L), eq("PKG-SHORT-MNM-1"), eq(Map.of(11L, 1)), eq(true)))
            .thenReturn(Optional.of("cn-pkg-short"));

        service.applyForwardShortShip(order, List.of(item), List.of(new PackageShortShipService.PackedLine(11L, 1)));

        verify(zohoBooksIntegrationClient).createCreditNote(10L, "PKG-SHORT-MNM-1", Map.of(11L, 1), true);
        verify(zohoBooksIntegrationClient, never()).applyCreditNoteToInvoice(any(), any());
    }

    @Test
    void applyExchangeShortShip_withZohoSync_createsCreditNoteAndAppliesToReplacementInvoice() {
        ApplicationProperties.Zoho zoho = new ApplicationProperties.Zoho();
        zoho.setSyncReturns(true);
        when(applicationProperties.getZoho()).thenReturn(zoho);
        CustomerOrder order = prepaidOrder();
        OrderItem item = line(11L, 2, "199.00");
        OrderReturn exchange = new OrderReturn();
        exchange.setRequestType(OrderRequestType.EXCHANGE);
        exchange.setReturnNumber("RET-9");
        exchange.setOrder(order);
        exchange.setOrderItem(item);
        exchange.setOriginalItemAmount(new BigDecimal("398.00"));
        exchange.setZohoReplacementInvoiceId("inv-repl");
        when(razorpayRefundService.findExistingRefundForOrder(eq(order), any(), eq(RazorpayRefundService.PURPOSE_PACKAGE_SHORT_SHIP)))
            .thenReturn(Optional.empty());
        when(razorpayRefundService.refundPayment(eq(order), any(), eq(RazorpayRefundService.PURPOSE_PACKAGE_SHORT_SHIP)))
            .thenReturn("rfnd_ex");
        when(zohoBooksIntegrationClient.createCreditNote(eq(10L), eq("PKG-SHORT-RET-9"), eq(Map.of(11L, 1)), eq(false)))
            .thenReturn(Optional.of("cn-ex-short"));

        service.applyExchangeShortShip(exchange, 1);

        verify(zohoBooksIntegrationClient).createCreditNote(10L, "PKG-SHORT-RET-9", Map.of(11L, 1), false);
        verify(zohoBooksIntegrationClient).applyCreditNoteToInvoice("cn-ex-short", "inv-repl");
    }

    @Test
    void applyForwardShortShip_zohoSyncDisabled_skipsZoho() {
        ApplicationProperties.Zoho zoho = new ApplicationProperties.Zoho();
        zoho.setSyncReturns(false);
        when(applicationProperties.getZoho()).thenReturn(zoho);
        CustomerOrder order = prepaidOrder();
        OrderItem item = line(11L, 2, "199.00");
        when(razorpayRefundService.findExistingRefundForOrder(eq(order), any(), eq(RazorpayRefundService.PURPOSE_PACKAGE_SHORT_SHIP)))
            .thenReturn(Optional.empty());
        when(razorpayRefundService.refundPayment(eq(order), any(), eq(RazorpayRefundService.PURPOSE_PACKAGE_SHORT_SHIP)))
            .thenReturn("rfnd_short");

        service.applyForwardShortShip(order, List.of(item), List.of(new PackageShortShipService.PackedLine(11L, 1)));

        verify(zohoBooksIntegrationClient, never()).createCreditNote(any(), any(), anyMap(), anyBoolean());
        verify(zohoBooksIntegrationClient, never()).createCreditNote(any(), any(), anyMap());
    }

    @Test
    void applyExchangeShortShip_zeroPacked_asksToConvertToRefund() {
        OrderReturn exchange = new OrderReturn();
        exchange.setOrderItem(line(11L, 1, "199.00"));

        assertThatThrownBy(() -> service.applyExchangeShortShip(exchange, 0))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("convert this exchange to a refund");
    }

    private static CustomerOrder prepaidOrder() {
        return new CustomerOrder()
            .id(10L)
            .orderNumber("MNM-1")
            .paymentMethod("online")
            .paymentStatus(PaymentStatus.PAID)
            .razorpayPaymentId("pay_1")
            .subtotal(new BigDecimal("398.00"))
            .discount(BigDecimal.ZERO)
            .total(new BigDecimal("398.00"));
    }

    private static OrderItem line(Long id, int qty, String price) {
        OrderItem item = new OrderItem().id(id).qty(qty).price(new BigDecimal(price)).productId("5").productName("Body Suit");
        item.setDiscountAllocation(BigDecimal.ZERO);
        return item;
    }
}
