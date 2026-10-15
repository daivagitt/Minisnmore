package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.miniandmore.app.client.LedgerlyIntegrationClient;
import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.OrderItem;
import com.miniandmore.app.domain.Product;
import com.miniandmore.app.domain.enumeration.OrderStatus;
import com.miniandmore.app.domain.enumeration.PaymentStatus;
import com.miniandmore.app.domain.OrderReturn;
import com.miniandmore.app.domain.enumeration.OrderRequestType;
import com.miniandmore.app.repository.CustomerOrderRepository;
import com.miniandmore.app.repository.OrderItemRepository;
import com.miniandmore.app.repository.OrderReturnRepository;
import com.miniandmore.app.repository.ProductRepository;
import com.miniandmore.app.web.rest.v1.AccountOrderDataAccess;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class OrderCancellationExecutorTest {

    @Mock
    private CustomerOrderRepository customerOrderRepository;

    @Mock
    private OrderReturnRepository orderReturnRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private LedgerlyIntegrationClient ledgerlyIntegrationClient;

    @Mock
    private RazorpayRefundService razorpayRefundService;

    @Mock
    private ZohoSyncService zohoSyncService;

    @Mock
    private OrderEventService orderEventService;

    @Mock
    private ProductVariantSupport variantSupport;

    private PlatformTransactionManager transactionManager;
    private OrderCancellationExecutor executor;

    @BeforeEach
    void setUp() {
        transactionManager = new PlatformTransactionManager() {
            @Override
            public TransactionStatus getTransaction(TransactionDefinition definition) {
                return mock(TransactionStatus.class);
            }

            @Override
            public void commit(TransactionStatus status) {
                // Test dummy transaction manager; no-op
            }

            @Override
            public void rollback(TransactionStatus status) {
                // Test dummy transaction manager; no-op
            }
        };

        AccountOrderDataAccess dataAccess = new AccountOrderDataAccess(
            null,
            null,
            customerOrderRepository,
            orderItemRepository,
            productRepository,
            null,
            ledgerlyIntegrationClient
        );

        lenient().when(orderReturnRepository.findAllByOrder_IdOrderByRequestedAtAsc(any())).thenReturn(List.of());

        executor = new OrderCancellationExecutor(
            dataAccess,
            orderReturnRepository,
            razorpayRefundService,
            zohoSyncService,
            orderEventService,
            variantSupport,
            transactionManager
        );
    }

    private CustomerOrder createOrder(String orderNumber) {
        CustomerOrder order = new CustomerOrder();
        order.setId(10L);
        order.setOrderNumber(orderNumber);
        order.setUserLogin("user1");
        order.setStatus(OrderStatus.CONFIRMED);
        order.setPaymentStatus(PaymentStatus.PAID);
        order.setPaymentMethod("online");
        order.setRazorpayPaymentId("pay_12345");
        order.setTotal(new BigDecimal("1000.00"));
        order.setInventoryReserved(true);
        return order;
    }

    @Test
    void performCancellation_alreadyCancelled_returnsNull() {
        CustomerOrder order = createOrder("ORD-CANCELLED");
        order.setStatus(OrderStatus.CANCELLED);

        String refundId = executor.performCancellation(order, "reason");
        assertThat(refundId).isNull();
        verifyNoInteractions(razorpayRefundService);
    }

    @Test
    void performCancellation_onlinePaid_restoresInventoryAndSyncs() {
        CustomerOrder order = createOrder("ORD-ONLINE");
        order.setLedgerlySalesOrderId("LEDGER-01");
        order.setZohoSalesOrderId("ZOHO-01");

        OrderItem simpleItem = new OrderItem();
        simpleItem.setProductId("101");
        simpleItem.setQty(2);

        OrderItem variantItem = new OrderItem();
        variantItem.setProductId("102");
        variantItem.setVariantSku("SKU-VAR-M");
        variantItem.setItemSize("M");
        variantItem.setColor("Blue");
        variantItem.setQty(1);

        OrderItem badItem = new OrderItem();
        badItem.setProductId("not-a-number");
        badItem.setQty(1);

        OrderItem zeroQtyItem = new OrderItem();
        zeroQtyItem.setProductId("103");
        zeroQtyItem.setQty(0);

        Product p101 = new Product();
        p101.setId(101L);
        p101.setStock(5);

        Product p102 = new Product();
        p102.setId(102L);

        when(razorpayRefundService.refundPayment(order, order.getTotal())).thenReturn("rfnd_999");
        when(customerOrderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderItemRepository.findAllByOrderId(10L)).thenReturn(List.of(simpleItem, variantItem, badItem, zeroQtyItem));
        when(productRepository.findOneForInventoryUpdate(101L)).thenReturn(Optional.of(p101));
        when(productRepository.findOneForInventoryUpdate(102L)).thenReturn(Optional.of(p102));
        when(variantSupport.isVariants(p102)).thenReturn(true);
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        String refundId = executor.performCancellation(order, "Customer requested cancellation");

        assertThat(refundId).isEqualTo("rfnd_999");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getCancellationReason()).isEqualTo("Customer requested cancellation");
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.REFUND_INITIATED);
        assertThat(order.getInventoryReserved()).isFalse();
        assertThat(order.getInventoryReleasedAt()).isNotNull();
        assertThat(p101.getStock()).isEqualTo(7);

        verify(variantSupport).incrementVariantStock(p102, "SKU-VAR-M", "M", "Blue", 1);
        verify(ledgerlyIntegrationClient).cancelOrder("ORD-ONLINE", null, "rfnd_999");
        verify(zohoSyncService).cancelOrder(10L);
        verify(orderEventService).syncLifecycleEvents(order, false);
    }

    @Test
    void performCancellation_codOrder_doesNotRefundRazorpay() {
        CustomerOrder order = createOrder("ORD-COD");
        order.setPaymentMethod("cod");
        order.setPaymentStatus(PaymentStatus.PENDING);
        order.setRazorpayPaymentId(null);

        when(customerOrderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderItemRepository.findAllByOrderId(10L)).thenReturn(List.of());

        String refundId = executor.performCancellation(order, "COD cancellation");

        assertThat(refundId).isNull();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verifyNoInteractions(razorpayRefundService);
        verify(orderEventService).syncLifecycleEvents(order, false);
    }

    @Test
    void performCancellation_inventoryAlreadyReleased_skipsRelease() {
        CustomerOrder order = createOrder("ORD-REL");
        order.setInventoryReleasedAt(Instant.now());
        order.setPaymentMethod("cod");
        order.setPaymentStatus(PaymentStatus.PENDING);

        when(customerOrderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        executor.performCancellation(order, "reason");

        verifyNoInteractions(orderItemRepository);
    }

    @Test
    void performCancellation_inventoryNotReservedAndNotRefunded_skipsRelease() {
        CustomerOrder order = createOrder("ORD-NOT-RES");
        order.setInventoryReserved(false);
        order.setPaymentStatus(PaymentStatus.PENDING);
        order.setPaymentMethod("cod");

        when(customerOrderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        executor.performCancellation(order, "reason");

        verifyNoInteractions(orderItemRepository);
    }

    @Test
    void performCancellation_syncExceptionsCaught_doesNotFailCancellation() {
        CustomerOrder order = createOrder("ORD-ERR");
        order.setLedgerlyInvoiceId("INV-1");
        order.setZohoInvoiceId("ZINV-1");
        order.setPaymentMethod("cod");
        order.setPaymentStatus(PaymentStatus.PENDING);

        when(customerOrderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderItemRepository.findAllByOrderId(10L)).thenReturn(List.of());
        doThrow(new RuntimeException("Ledgerly API error")).when(ledgerlyIntegrationClient).cancelOrder(any(), any(), any());
        doThrow(new RuntimeException("Zoho API error")).when(zohoSyncService).cancelOrder(any());

        executor.performCancellation(order, "Reason");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(orderEventService).syncLifecycleEvents(order, false);
    }

    @Test
    void performCancellation_productNotFoundForUpdate_handlesGracefully() {
        CustomerOrder order = createOrder("ORD-NOPROD");
        order.setPaymentMethod("cod");
        order.setPaymentStatus(PaymentStatus.PENDING);

        OrderItem item = new OrderItem();
        item.setProductId("999");
        item.setQty(2);

        when(customerOrderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderItemRepository.findAllByOrderId(10L)).thenReturn(List.of(item));
        when(productRepository.findOneForInventoryUpdate(999L)).thenReturn(Optional.empty());

        executor.performCancellation(order, "Reason");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void assertCancelEligible_eligibleOrder_passes() {
        CustomerOrder order = createOrder("ORD-OK");
        order.setStatus(OrderStatus.CONFIRMED);

        executor.assertCancelEligible(order);
    }

    @Test
    void assertCancelEligible_cancelledOrder_throwsConflict() {
        CustomerOrder order = createOrder("ORD-CANC");
        order.setStatus(OrderStatus.CANCELLED);

        assertThatThrownBy(() -> executor.assertCancelEligible(order))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("This order is already cancelled");
    }

    @Test
    void assertCancelEligible_fulfilledOrder_throwsConflict() {
        CustomerOrder order = createOrder("ORD-FULF");
        order.setStatus(OrderStatus.FULFILLED);

        assertThatThrownBy(() -> executor.assertCancelEligible(order))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("This order has already been dispatched and can no longer be cancelled");
    }

    @Test
    void assertCancelEligible_dispatchedOrder_throwsConflict() {
        CustomerOrder order = createOrder("ORD-DISP");
        order.setStatus(OrderStatus.CONFIRMED);
        order.setTrackingNumber("TRK-12345");
        order.setShipmentDate("2026-09-28");

        assertThatThrownBy(() -> executor.assertCancelEligible(order))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("This order has already been dispatched and can no longer be cancelled");
    }

    @Test
    void isCancelEligible_falseOnceShipmentBookedOrDispatched() {
        CustomerOrder confirmed = createOrder("ORD-OK");
        confirmed.setStatus(OrderStatus.CONFIRMED);
        assertThat(executor.isCancelEligible(confirmed)).isTrue();

        CustomerOrder handWrapped = createOrder("ORD-WRAP");
        handWrapped.setStatus(OrderStatus.HAND_WRAPPED);
        assertThat(executor.isCancelEligible(handWrapped)).isTrue();

        CustomerOrder cancelled = createOrder("ORD-CANC");
        cancelled.setStatus(OrderStatus.CANCELLED);
        assertThat(executor.isCancelEligible(cancelled)).isFalse();

        CustomerOrder awaitingPickup = createOrder("ORD-AWB");
        awaitingPickup.setStatus(OrderStatus.HAND_WRAPPED);
        awaitingPickup.setShipmentLogisticsMode(com.miniandmore.app.domain.enumeration.ReturnLogisticsMode.XPRESSBEES);
        awaitingPickup.setTrackingNumber("XB-1");
        assertThat(executor.isCancelEligible(awaitingPickup)).isFalse();

        CustomerOrder shipped = createOrder("ORD-SHIP");
        shipped.setStatus(OrderStatus.CONFIRMED);
        shipped.setTrackingNumber("TRK-9");
        shipped.setShipmentDate("2026-09-28");
        assertThat(executor.isCancelEligible(shipped)).isFalse();
    }

    @Test
    void assertCancelEligible_awaitingPickup_throwsConflict() {
        CustomerOrder order = createOrder("ORD-AWB");
        order.setStatus(OrderStatus.HAND_WRAPPED);
        order.setShipmentLogisticsMode(com.miniandmore.app.domain.enumeration.ReturnLogisticsMode.XPRESSBEES);
        order.setTrackingNumber("XB-1");

        assertThatThrownBy(() -> executor.assertCancelEligible(order))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("already has a shipment booked");
    }

    @Test
    void assertCancelEligible_carrierCancelledAwb_allowsCancellation() {
        CustomerOrder order = createOrder("ORD-REBOOK");
        order.setStatus(OrderStatus.HAND_WRAPPED);
        order.setShipmentLogisticsMode(com.miniandmore.app.domain.enumeration.ReturnLogisticsMode.XPRESSBEES);
        order.setTrackingNumber("XB-1");
        order.setShipmentCarrierStatus("carrier_cancelled");

        executor.assertCancelEligible(order);
        assertThat(executor.isCancelEligible(order)).isTrue();
    }

    @Test
    void performCancellation_syncFailures_areCaughtAndLogged() {
        CustomerOrder order = createOrder("ORD-SYNC-FAIL");
        order.setStatus(OrderStatus.CONFIRMED);
        order.setPaymentStatus(PaymentStatus.PAID);
        order.setPaymentMethod("cod");
        order.setLedgerlySalesOrderId("SO-1");
        order.setZohoSalesOrderId("ZSO-1");

        when(customerOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(orderItemRepository.findAllByOrderId(order.getId())).thenReturn(List.of());
        doThrow(new RuntimeException("ledgerly down")).when(ledgerlyIntegrationClient).cancelOrder(any(), any(), any());
        doThrow(new RuntimeException("zoho down")).when(zohoSyncService).cancelOrder(any());

        String refundId = executor.performCancellation(order, "Cancel reason");
        assertThat(refundId).isNull();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void releaseInventoryReservation_edgeCases() {
        CustomerOrder order = createOrder("ORD-INV-EDGE");
        order.setStatus(OrderStatus.CONFIRMED);
        order.setPaymentStatus(PaymentStatus.PAID);
        order.setPaymentMethod("cod");
        order.setInventoryReserved(true);

        OrderItem badIdItem = new OrderItem();
        badIdItem.setProductId("NOT-A-NUMBER");
        badIdItem.setQty(2);

        OrderItem zeroQtyItem = new OrderItem();
        zeroQtyItem.setProductId("101");
        zeroQtyItem.setQty(0);

        OrderItem notFoundProductItem = new OrderItem();
        notFoundProductItem.setProductId("999");
        notFoundProductItem.setQty(2);

        when(orderItemRepository.findAllByOrderId(order.getId()))
            .thenReturn(List.of(badIdItem, zeroQtyItem, notFoundProductItem));
        when(productRepository.findOneForInventoryUpdate(101L)).thenReturn(Optional.of(new Product()));
        when(productRepository.findOneForInventoryUpdate(999L)).thenReturn(Optional.empty());
        when(customerOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        executor.performCancellation(order, "Reason");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void performCancellation_afterPackageShortShip_refundsRemainderWhenRefundInitiated() {
        CustomerOrder order = createOrder("ORD-SHORT");
        order.setPaymentStatus(PaymentStatus.REFUND_INITIATED);
        order.setPackageShortRefundAmount(new BigDecimal("300.00"));
        order.setPackageShortRazorpayRefundId("rfnd_short");
        order.setTotal(new BigDecimal("1000.00"));

        when(razorpayRefundService.findExistingRefundForOrder(order, new BigDecimal("700.00"))).thenReturn(Optional.empty());
        when(razorpayRefundService.refundPayment(order, new BigDecimal("700.00"))).thenReturn("rfnd_cancel");
        when(customerOrderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderItemRepository.findAllByOrderId(10L)).thenReturn(List.of());

        String refundId = executor.performCancellation(order, "Cancel after short-ship");

        assertThat(refundId).isEqualTo("rfnd_cancel");
        verify(razorpayRefundService).refundPayment(order, new BigDecimal("700.00"));
        verify(razorpayRefundService, never()).refundPayment(order, new BigDecimal("1000.00"));
    }

    @Test
    void performCancellation_afterExchangePackageShortShip_refundsRemainder() {
        CustomerOrder order = createOrder("ORD-EX-SHORT");
        order.setPaymentStatus(PaymentStatus.REFUND_INITIATED);
        order.setTotal(new BigDecimal("1000.00"));

        OrderReturn exchange = new OrderReturn();
        exchange.setRequestType(OrderRequestType.EXCHANGE);
        exchange.setPackageShortRefundAmount(new BigDecimal("200.00"));
        exchange.setPackageShortRazorpayRefundId("rfnd_ex_short");
        exchange.setOrder(order);
        when(orderReturnRepository.findAllByOrder_IdOrderByRequestedAtAsc(10L)).thenReturn(List.of(exchange));
        when(razorpayRefundService.findExistingRefundForOrder(order, new BigDecimal("800.00"))).thenReturn(Optional.empty());
        when(razorpayRefundService.refundPayment(order, new BigDecimal("800.00"))).thenReturn("rfnd_cancel");
        when(customerOrderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderItemRepository.findAllByOrderId(10L)).thenReturn(List.of());

        String refundId = executor.performCancellation(order, "Cancel");

        assertThat(refundId).isEqualTo("rfnd_cancel");
        verify(razorpayRefundService).refundPayment(order, new BigDecimal("800.00"));
    }

    @Test
    void performCancellation_afterClearedFailedPackageShortShip_refundsFullTotal() {
        CustomerOrder order = createOrder("ORD-FAIL-SHORT");
        order.setPaymentStatus(PaymentStatus.REFUND_INITIATED);
        order.setPackageShortRefundAmount(null);
        order.setPackageShortRazorpayRefundId(null);
        order.setTotal(new BigDecimal("1000.00"));

        when(razorpayRefundService.findExistingRefundForOrder(order, new BigDecimal("1000.00"))).thenReturn(Optional.empty());
        when(razorpayRefundService.refundPayment(order, new BigDecimal("1000.00"))).thenReturn("rfnd_full");
        when(customerOrderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderItemRepository.findAllByOrderId(10L)).thenReturn(List.of());

        executor.performCancellation(order, "Cancel after failed short refund");

        verify(razorpayRefundService).refundPayment(order, new BigDecimal("1000.00"));
    }

    @Test
    void performCancellation_afterPackageShortShip_restoresPackedQtyOnly() {
        CustomerOrder order = createOrder("ORD-PACK");
        order.setPaymentMethod("cod");
        order.setPaymentStatus(PaymentStatus.PENDING);

        OrderItem item = new OrderItem();
        item.setProductId("101");
        item.setQty(2);
        item.setPackedQty(1);

        Product product = new Product();
        product.setId(101L);
        product.setStock(10);

        when(customerOrderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderItemRepository.findAllByOrderId(10L)).thenReturn(List.of(item));
        when(productRepository.findOneForInventoryUpdate(101L)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        executor.performCancellation(order, "Cancel packed line");

        assertThat(product.getStock()).isEqualTo(11);
    }
}
