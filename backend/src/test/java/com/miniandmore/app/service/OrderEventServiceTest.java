package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.OrderEvent;
import com.miniandmore.app.domain.enumeration.OrderStatus;
import com.miniandmore.app.domain.enumeration.PaymentStatus;
import com.miniandmore.app.repository.OrderEventRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderEventServiceTest {

    @Mock
    private OrderEventRepository orderEventRepository;

    @Mock
    private OrderMailService orderMailService;

    @Mock
    private OrderSmsService orderSmsService;

    private OrderEventService service;

    @BeforeEach
    void setUp() {
        service = new OrderEventService(orderEventRepository, orderMailService, orderSmsService);
    }

    @Test
    void onOrderCreated_recordsPaymentPendingForUnpaidOnline() {
        CustomerOrder order = baseOrder().status(OrderStatus.PENDING).paymentStatus(PaymentStatus.PENDING);
        when(orderEventRepository.existsByOrderIdAndEventType(42L, OrderEventService.TYPE_ORDER_PLACED)).thenReturn(false);
        stubInsertSucceeds();

        service.onOrderCreated(order);

        assertThat(capturedEventTypes()).containsExactly(OrderEventService.TYPE_PAYMENT_PENDING);
        assertThat(capturedTitles()).containsExactly("Payment pending");
        assertThat(capturedCreatedAts()).containsExactly(order.getPlacedAt());
        verify(orderMailService, never()).sendOrderConfirmation(any());
    }

    @Test
    void syncLifecycleEvents_doesNotDuplicateLegacyPendingOrderPlacedEvent() {
        CustomerOrder order = baseOrder().status(OrderStatus.PENDING).paymentStatus(PaymentStatus.PENDING);
        when(orderEventRepository.existsByOrderIdAndEventType(42L, OrderEventService.TYPE_ORDER_PLACED)).thenReturn(true);

        service.syncLifecycleEvents(order);

        verify(orderEventRepository, never()).insertIfAbsent(any(), any(), any(), any(), any(), any(), any());
        verify(orderEventRepository, never())
            .existsByOrderIdAndEventType(42L, OrderEventService.TYPE_PAYMENT_PENDING);
    }

    @Test
    void onOrderCreated_cod_sendsConfirmation() {
        CustomerOrder order = baseOrder().paymentMethod("cod").status(OrderStatus.CONFIRMED).paymentStatus(PaymentStatus.PENDING);
        stubInsertSucceeds();

        service.onOrderCreated(order);

        verify(orderMailService).sendOrderConfirmation(order);
    }

    @Test
    void onOrderPaid_online_sendsConfirmationAndOutForDeliveryWhenShipped() {
        CustomerOrder order = baseOrder()
            .status(OrderStatus.CONFIRMED)
            .paymentStatus(PaymentStatus.PAID)
            .shipmentDate("2026-08-14");
        stubInsertSucceeds();

        service.onOrderPaid(order);

        assertThat(capturedEventTypes())
            .contains(OrderEventService.TYPE_PAYMENT_RECEIVED, OrderEventService.TYPE_SHIPPED);
        verify(orderMailService, org.mockito.Mockito.atLeastOnce()).sendOrderConfirmation(order);
        verify(orderMailService).sendOutForDelivery(order);
    }

    @Test
    void syncLifecycleEvents_expressBeesPickupDoesNotSendOutForDelivery() {
        CustomerOrder order = baseOrder()
            .status(OrderStatus.CONFIRMED)
            .paymentStatus(PaymentStatus.PAID)
            .trackingNumber("14344950061557")
            .shipmentLogisticsMode(com.miniandmore.app.domain.enumeration.ReturnLogisticsMode.XPRESSBEES)
            .shipmentCarrierStatus("in_transit");
        stubInsertSucceeds();

        service.syncLifecycleEvents(order);

        assertThat(capturedEventTypes()).contains(OrderEventService.TYPE_SHIPPED).doesNotContain(OrderEventService.TYPE_OUT_FOR_DELIVERY);
        verify(orderMailService, never()).sendOutForDelivery(any());
    }

    @Test
    void syncLifecycleEvents_expressBeesOutForDeliverySendsOutForDelivery() {
        CustomerOrder order = baseOrder()
            .status(OrderStatus.CONFIRMED)
            .paymentStatus(PaymentStatus.PAID)
            .trackingNumber("14344950061557")
            .shipmentLogisticsMode(com.miniandmore.app.domain.enumeration.ReturnLogisticsMode.XPRESSBEES)
            .shipmentCarrierStatus("out_for_delivery");
        stubInsertSucceeds();

        service.syncLifecycleEvents(order);

        assertThat(capturedEventTypes()).contains(OrderEventService.TYPE_SHIPPED, OrderEventService.TYPE_OUT_FOR_DELIVERY);
        assertThat(capturedTitles()).contains("Out for delivery");
        verify(orderMailService).sendOutForDelivery(order);
        verify(orderSmsService).sendOutForDelivery(order);
    }

    @Test
    void syncLifecycleEvents_deliveryFailed_sendsDeliveryFailedEmail() {
        CustomerOrder order = baseOrder()
            .status(OrderStatus.CONFIRMED)
            .paymentStatus(PaymentStatus.PAID)
            .trackingNumber("14344950061557")
            .shipmentLogisticsMode(com.miniandmore.app.domain.enumeration.ReturnLogisticsMode.XPRESSBEES)
            .shipmentCarrierStatus("delivery_failed");
        stubInsertSucceeds();

        service.syncLifecycleEvents(order);

        assertThat(capturedEventTypes()).contains(OrderEventService.TYPE_SHIPPED, OrderEventService.TYPE_DELIVERY_FAILED);
        assertThat(capturedTitles()).contains("Delivery attempt unsuccessful");
        verify(orderMailService).sendDeliveryFailed(order);
    }

    @Test
    void syncLifecycleEvents_cancelled_sendsCancelEmail() {
        CustomerOrder order = baseOrder().status(OrderStatus.CANCELLED).paymentStatus(PaymentStatus.PAID);
        stubInsertSucceeds();

        service.syncLifecycleEvents(order);

        verify(orderMailService).sendOrderCancelled(order);
    }

    @Test
    void syncLifecycleEvents_delivered_sendsEmailAndSmsOnce() {
        CustomerOrder order = baseOrder().paymentStatus(PaymentStatus.PAID).ledgerlyOrderStatus("delivered");
        stubInsertSucceeds();

        service.syncLifecycleEvents(order);

        verify(orderMailService).sendOrderDelivered(order);
        verify(orderSmsService).sendOrderDelivered(order);
    }

    @Test
    void syncLifecycleEvents_delivered_smsStillRunsWhenEmailFails() {
        CustomerOrder order = baseOrder().paymentStatus(PaymentStatus.PAID).ledgerlyOrderStatus("delivered");
        stubInsertSucceeds();
        org.mockito.Mockito.doThrow(new RuntimeException("smtp down")).when(orderMailService).sendOrderDelivered(order);

        service.syncLifecycleEvents(order);

        verify(orderSmsService).sendOrderDelivered(order);
    }

    @Test
    void syncLifecycleEvents_cancelled_smsStillRunsWhenEmailFails() {
        CustomerOrder order = baseOrder().status(OrderStatus.CANCELLED).paymentStatus(PaymentStatus.PAID);
        stubInsertSucceeds();
        org.mockito.Mockito.doThrow(new RuntimeException("smtp down")).when(orderMailService).sendOrderCancelled(order);

        service.syncLifecycleEvents(order);

        verify(orderSmsService).sendOrderCancelled(order);
    }

    @Test
    void syncLifecycleEvents_handWrapped_recordsCustomerVisibleEvent() {
        CustomerOrder order = baseOrder().status(OrderStatus.HAND_WRAPPED).paymentStatus(PaymentStatus.PAID);
        stubInsertSucceeds();

        service.syncLifecycleEvents(order);

        assertThat(capturedEventTypes()).contains(OrderEventService.TYPE_HAND_WRAPPED);
        assertThat(capturedTitles()).contains("Hand-wrapped");
    }

    @Test
    void onOrderPaid_cod_skipsPaidConfirmation() {
        CustomerOrder order = baseOrder().paymentMethod("cod").status(OrderStatus.CONFIRMED).paymentStatus(PaymentStatus.PAID);
        // ORDER_PLACED already recorded at create-time for COD; payment_received must not re-send confirmation.
        when(orderEventRepository.insertIfAbsent(any(), any(), any(), any(), any(), any(), any())).thenAnswer(invocation -> {
            String eventType = invocation.getArgument(3);
            return OrderEventService.TYPE_ORDER_PLACED.equals(eventType) ? 0 : 1;
        });

        service.onOrderPaid(order);

        verify(orderMailService, never()).sendOrderConfirmation(any());
    }

    @Test
    void queueLifecycleEmail_swallowsMailFailures() {
        CustomerOrder order = baseOrder().paymentMethod("cod");
        stubInsertSucceeds();
        org.mockito.Mockito.doThrow(new RuntimeException("smtp down")).when(orderMailService).sendOrderConfirmation(order);

        service.onOrderCreated(order);

        verify(orderEventRepository).insertIfAbsent(any(), any(), any(), any(), any(), any(), any());
        verify(orderSmsService).sendOrderConfirmation(order);
    }

    @Test
    void recordIfAbsent_skipsDuplicates() {
        CustomerOrder order = baseOrder().paymentStatus(PaymentStatus.PAID);
        when(orderEventRepository.insertIfAbsent(any(), any(), any(), any(), any(), any(), any())).thenReturn(0);

        service.syncLifecycleEvents(order);

        verify(orderMailService, never()).sendOrderConfirmation(any());
    }

    @Test
    void syncLifecycleEvents_nullOrderId_isNoOp() {
        CustomerOrder order = baseOrder().id(null);

        service.syncLifecycleEvents(order);

        verify(orderEventRepository, never()).insertIfAbsent(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void syncLifecycleEvents_refunded_recordsRefundEvent() {
        CustomerOrder order = baseOrder().paymentStatus(PaymentStatus.REFUNDED);
        stubInsertSucceeds();

        service.syncLifecycleEvents(order);

        assertThat(capturedEventTypes()).contains(OrderEventService.TYPE_REFUNDED);
    }

    @Test
    void syncLifecycleEvents_shipped_includesTrackingInBody() {
        CustomerOrder order = baseOrder()
            .paymentStatus(PaymentStatus.PAID)
            .shipmentDate("2026-08-14")
            .trackingNumber(" AWB-123 ");
        stubInsertSucceeds();

        service.syncLifecycleEvents(order);

        int shippedIndex = capturedEventTypes().indexOf(OrderEventService.TYPE_SHIPPED);
        assertThat(shippedIndex).isGreaterThanOrEqualTo(0);
        assertThat(capturedBodies().get(shippedIndex)).contains("Tracking AWB-123");
    }

    @Test
    void listForUser_mapsEventsToMaps() {
        OrderEvent event = new OrderEvent()
            .id(7L)
            .eventType(OrderEventService.TYPE_ORDER_PLACED)
            .title("Order placed")
            .body("MNM-TEST-001")
            .orderNumber("MNM-TEST-001")
            .createdAt(Instant.parse("2026-08-14T10:00:00Z"));
        when(orderEventRepository.findAllByUserLoginOrderByCreatedAtDesc(eq("buyer@example.com"), any()))
            .thenReturn(List.of(event));

        List<Map<String, Object>> result = service.listForUser("buyer@example.com");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).get("event_type")).isEqualTo(OrderEventService.TYPE_ORDER_PLACED);
        assertThat(result.get(0).get("created_at")).isEqualTo("2026-08-14T10:00:00Z");
    }

    @Test
    void syncLifecycleEvents_delivered_parsesIsoShipmentInstant() {
        CustomerOrder order = baseOrder()
            .paymentStatus(PaymentStatus.PAID)
            .ledgerlyOrderStatus("delivered")
            .shipmentDate("2026-08-14T18:30:00Z");
        stubInsertSucceeds();

        service.syncLifecycleEvents(order);

        int deliveredIndex = capturedEventTypes().indexOf(OrderEventService.TYPE_DELIVERED);
        assertThat(deliveredIndex).isGreaterThanOrEqualTo(0);
        assertThat(capturedCreatedAts().get(deliveredIndex)).isEqualTo(Instant.parse("2026-08-14T18:30:00Z"));
    }

    @Test
    void syncLifecycleEvents_shipped_unparseableShipmentDate_usesNow() {
        CustomerOrder order = baseOrder()
            .paymentStatus(PaymentStatus.PAID)
            .shipmentDate("not-a-date")
            .trackingNumber("AWB-1");
        stubInsertSucceeds();

        service.syncLifecycleEvents(order);

        int shippedIndex = capturedEventTypes().indexOf(OrderEventService.TYPE_SHIPPED);
        assertThat(shippedIndex).isGreaterThanOrEqualTo(0);
        assertThat(capturedCreatedAts().get(shippedIndex)).isNotNull();
    }

    @Test
    void listForUser_nullCreatedAt_mapsToEmptyString() {
        OrderEvent event = new OrderEvent()
            .id(8L)
            .eventType(OrderEventService.TYPE_PAYMENT_PENDING)
            .title("Payment pending")
            .body("MNM-TEST-001")
            .orderNumber("MNM-TEST-001")
            .createdAt(null);
        when(orderEventRepository.findAllByUserLoginOrderByCreatedAtDesc(eq("buyer@example.com"), any()))
            .thenReturn(List.of(event));

        List<Map<String, Object>> result = service.listForUser("buyer@example.com");

        assertThat(result.get(0).get("created_at")).isEqualTo("");
    }

    private void stubInsertSucceeds() {
        when(orderEventRepository.insertIfAbsent(any(), any(), any(), any(), any(), any(), any())).thenReturn(1);
    }

    private List<String> capturedEventTypes() {
        ArgumentCaptor<String> eventType = ArgumentCaptor.forClass(String.class);
        verify(orderEventRepository, org.mockito.Mockito.atLeastOnce())
            .insertIfAbsent(any(), any(), any(), eventType.capture(), any(), any(), any());
        return eventType.getAllValues();
    }

    private List<String> capturedTitles() {
        ArgumentCaptor<String> title = ArgumentCaptor.forClass(String.class);
        verify(orderEventRepository, org.mockito.Mockito.atLeastOnce())
            .insertIfAbsent(any(), any(), any(), any(), title.capture(), any(), any());
        return title.getAllValues();
    }

    private List<String> capturedBodies() {
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(orderEventRepository, org.mockito.Mockito.atLeastOnce())
            .insertIfAbsent(any(), any(), any(), any(), any(), body.capture(), any());
        return body.getAllValues();
    }

    private List<Instant> capturedCreatedAts() {
        ArgumentCaptor<Instant> createdAt = ArgumentCaptor.forClass(Instant.class);
        verify(orderEventRepository, org.mockito.Mockito.atLeastOnce())
            .insertIfAbsent(any(), any(), any(), any(), any(), any(), createdAt.capture());
        return createdAt.getAllValues();
    }

    private CustomerOrder baseOrder() {
        return new CustomerOrder()
            .id(42L)
            .userLogin("buyer@example.com")
            .orderNumber("MNM-TEST-001")
            .paymentMethod("online")
            .total(BigDecimal.valueOf(299))
            .placedAt(Instant.parse("2026-08-14T10:00:00Z"));
    }
}
