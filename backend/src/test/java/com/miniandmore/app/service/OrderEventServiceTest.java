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
        when(orderEventRepository.existsByOrderIdAndEventType(42L, OrderEventService.TYPE_PAYMENT_PENDING)).thenReturn(false);

        service.onOrderCreated(order);

        ArgumentCaptor<OrderEvent> captor = ArgumentCaptor.forClass(OrderEvent.class);
        verify(orderEventRepository).save(captor.capture());
        OrderEvent event = captor.getValue();
        assertThat(event.getEventType()).isEqualTo(OrderEventService.TYPE_PAYMENT_PENDING);
        assertThat(event.getTitle()).isEqualTo("Payment pending");
        assertThat(event.getCreatedAt()).isEqualTo(order.getPlacedAt());
        verify(orderMailService, never()).sendOrderConfirmation(any());
    }

    @Test
    void syncLifecycleEvents_doesNotDuplicateLegacyPendingOrderPlacedEvent() {
        CustomerOrder order = baseOrder().status(OrderStatus.PENDING).paymentStatus(PaymentStatus.PENDING);
        when(orderEventRepository.existsByOrderIdAndEventType(42L, OrderEventService.TYPE_ORDER_PLACED)).thenReturn(true);

        service.syncLifecycleEvents(order);

        verify(orderEventRepository, never()).save(any());
        verify(orderEventRepository, never())
            .existsByOrderIdAndEventType(42L, OrderEventService.TYPE_PAYMENT_PENDING);
    }

    @Test
    void onOrderCreated_cod_sendsConfirmation() {
        CustomerOrder order = baseOrder().paymentMethod("cod").status(OrderStatus.CONFIRMED).paymentStatus(PaymentStatus.PENDING);
        when(orderEventRepository.existsByOrderIdAndEventType(42L, OrderEventService.TYPE_ORDER_PLACED)).thenReturn(false);

        service.onOrderCreated(order);

        verify(orderMailService).sendOrderConfirmation(order);
    }

    @Test
    void onOrderPaid_online_sendsConfirmationAndOutForDeliveryWhenShipped() {
        CustomerOrder order = baseOrder()
            .status(OrderStatus.CONFIRMED)
            .paymentStatus(PaymentStatus.PAID)
            .shipmentDate("2026-08-14");
        when(orderEventRepository.existsByOrderIdAndEventType(any(), any())).thenReturn(false);

        service.onOrderPaid(order);

        ArgumentCaptor<OrderEvent> captor = ArgumentCaptor.forClass(OrderEvent.class);
        verify(orderEventRepository, org.mockito.Mockito.atLeast(2)).save(captor.capture());
        List<String> types = captor.getAllValues().stream().map(OrderEvent::getEventType).toList();
        assertThat(types).contains(OrderEventService.TYPE_PAYMENT_RECEIVED, OrderEventService.TYPE_SHIPPED);
        verify(orderMailService, org.mockito.Mockito.atLeastOnce()).sendOrderConfirmation(order);
        verify(orderMailService).sendOutForDelivery(order);
    }

    @Test
    void syncLifecycleEvents_cancelled_sendsCancelEmail() {
        CustomerOrder order = baseOrder().status(OrderStatus.CANCELLED).paymentStatus(PaymentStatus.PAID);
        when(orderEventRepository.existsByOrderIdAndEventType(any(), any())).thenReturn(false);

        service.syncLifecycleEvents(order);

        verify(orderMailService).sendOrderCancelled(order);
    }

    @Test
    void syncLifecycleEvents_delivered_sendsEmailAndSmsOnce() {
        CustomerOrder order = baseOrder().paymentStatus(PaymentStatus.PAID).ledgerlyOrderStatus("delivered");
        when(orderEventRepository.existsByOrderIdAndEventType(any(), any())).thenReturn(true);
        when(orderEventRepository.existsByOrderIdAndEventType(42L, OrderEventService.TYPE_DELIVERED)).thenReturn(false);

        service.syncLifecycleEvents(order);

        verify(orderMailService).sendOrderDelivered(order);
        verify(orderSmsService).sendOrderDelivered(order);
    }

    @Test
    void syncLifecycleEvents_delivered_smsStillRunsWhenEmailFails() {
        CustomerOrder order = baseOrder().paymentStatus(PaymentStatus.PAID).ledgerlyOrderStatus("delivered");
        when(orderEventRepository.existsByOrderIdAndEventType(any(), any())).thenReturn(true);
        when(orderEventRepository.existsByOrderIdAndEventType(42L, OrderEventService.TYPE_DELIVERED)).thenReturn(false);
        org.mockito.Mockito.doThrow(new RuntimeException("smtp down")).when(orderMailService).sendOrderDelivered(order);

        service.syncLifecycleEvents(order);

        verify(orderSmsService).sendOrderDelivered(order);
    }

    @Test
    void syncLifecycleEvents_cancelled_smsStillRunsWhenEmailFails() {
        CustomerOrder order = baseOrder().status(OrderStatus.CANCELLED).paymentStatus(PaymentStatus.PAID);
        when(orderEventRepository.existsByOrderIdAndEventType(any(), any())).thenReturn(false);
        org.mockito.Mockito.doThrow(new RuntimeException("smtp down")).when(orderMailService).sendOrderCancelled(order);

        service.syncLifecycleEvents(order);

        verify(orderSmsService).sendOrderCancelled(order);
    }

    @Test
    void syncLifecycleEvents_handWrapped_recordsCustomerVisibleEvent() {
        CustomerOrder order = baseOrder().status(OrderStatus.HAND_WRAPPED).paymentStatus(PaymentStatus.PAID);
        when(orderEventRepository.existsByOrderIdAndEventType(any(), any())).thenReturn(true);
        when(orderEventRepository.existsByOrderIdAndEventType(42L, OrderEventService.TYPE_HAND_WRAPPED)).thenReturn(false);

        service.syncLifecycleEvents(order);

        ArgumentCaptor<OrderEvent> captor = ArgumentCaptor.forClass(OrderEvent.class);
        verify(orderEventRepository).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo(OrderEventService.TYPE_HAND_WRAPPED);
        assertThat(captor.getValue().getTitle()).isEqualTo("Hand-wrapped");
    }

    @Test
    void onOrderPaid_cod_skipsPaidConfirmation() {
        CustomerOrder order = baseOrder().paymentMethod("cod").status(OrderStatus.CONFIRMED).paymentStatus(PaymentStatus.PAID);
        when(orderEventRepository.existsByOrderIdAndEventType(42L, OrderEventService.TYPE_ORDER_PLACED)).thenReturn(true);
        when(orderEventRepository.existsByOrderIdAndEventType(42L, OrderEventService.TYPE_PAYMENT_RECEIVED)).thenReturn(false);

        service.onOrderPaid(order);

        verify(orderMailService, never()).sendOrderConfirmation(any());
    }

    @Test
    void queueLifecycleEmail_swallowsMailFailures() {
        CustomerOrder order = baseOrder().paymentMethod("cod");
        when(orderEventRepository.existsByOrderIdAndEventType(42L, OrderEventService.TYPE_ORDER_PLACED)).thenReturn(false);
        org.mockito.Mockito.doThrow(new RuntimeException("smtp down")).when(orderMailService).sendOrderConfirmation(order);

        service.onOrderCreated(order);

        verify(orderEventRepository).save(any());
        verify(orderSmsService).sendOrderConfirmation(order);
    }

    @Test
    void recordIfAbsent_skipsDuplicates() {
        CustomerOrder order = baseOrder().paymentStatus(PaymentStatus.PAID);
        when(orderEventRepository.existsByOrderIdAndEventType(42L, OrderEventService.TYPE_ORDER_PLACED)).thenReturn(true);
        when(orderEventRepository.existsByOrderIdAndEventType(42L, OrderEventService.TYPE_PAYMENT_RECEIVED)).thenReturn(true);

        service.syncLifecycleEvents(order);

        verify(orderEventRepository, never()).save(any());
        verify(orderMailService, never()).sendOrderConfirmation(any());
    }

    @Test
    void syncLifecycleEvents_nullOrderId_isNoOp() {
        CustomerOrder order = baseOrder().id(null);

        service.syncLifecycleEvents(order);

        verify(orderEventRepository, never()).save(any());
    }

    @Test
    void syncLifecycleEvents_refunded_recordsRefundEvent() {
        CustomerOrder order = baseOrder().paymentStatus(PaymentStatus.REFUNDED);
        when(orderEventRepository.existsByOrderIdAndEventType(any(), any())).thenReturn(false);

        service.syncLifecycleEvents(order);

        ArgumentCaptor<OrderEvent> captor = ArgumentCaptor.forClass(OrderEvent.class);
        verify(orderEventRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues().stream().map(OrderEvent::getEventType))
            .contains(OrderEventService.TYPE_REFUNDED);
    }

    @Test
    void syncLifecycleEvents_shipped_includesTrackingInBody() {
        CustomerOrder order = baseOrder()
            .paymentStatus(PaymentStatus.PAID)
            .shipmentDate("2026-08-14")
            .trackingNumber(" AWB-123 ");
        when(orderEventRepository.existsByOrderIdAndEventType(any(), any())).thenReturn(false);

        service.syncLifecycleEvents(order);

        ArgumentCaptor<OrderEvent> captor = ArgumentCaptor.forClass(OrderEvent.class);
        verify(orderEventRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        OrderEvent shipped = captor
            .getAllValues()
            .stream()
            .filter(e -> OrderEventService.TYPE_SHIPPED.equals(e.getEventType()))
            .findFirst()
            .orElseThrow();
        assertThat(shipped.getBody()).contains("Tracking AWB-123");
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
        when(orderEventRepository.existsByOrderIdAndEventType(any(), any())).thenReturn(false);

        service.syncLifecycleEvents(order);

        ArgumentCaptor<OrderEvent> captor = ArgumentCaptor.forClass(OrderEvent.class);
        verify(orderEventRepository, org.mockito.Mockito.atLeast(2)).save(captor.capture());
        OrderEvent delivered = captor
            .getAllValues()
            .stream()
            .filter(e -> OrderEventService.TYPE_DELIVERED.equals(e.getEventType()))
            .findFirst()
            .orElseThrow();
        assertThat(delivered.getCreatedAt()).isEqualTo(Instant.parse("2026-08-14T18:30:00Z"));
    }

    @Test
    void syncLifecycleEvents_shipped_unparseableShipmentDate_usesNow() {
        CustomerOrder order = baseOrder()
            .paymentStatus(PaymentStatus.PAID)
            .shipmentDate("not-a-date")
            .trackingNumber("AWB-1");
        when(orderEventRepository.existsByOrderIdAndEventType(any(), any())).thenReturn(false);

        service.syncLifecycleEvents(order);

        ArgumentCaptor<OrderEvent> captor = ArgumentCaptor.forClass(OrderEvent.class);
        verify(orderEventRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        OrderEvent shipped = captor
            .getAllValues()
            .stream()
            .filter(e -> OrderEventService.TYPE_SHIPPED.equals(e.getEventType()))
            .findFirst()
            .orElseThrow();
        assertThat(shipped.getCreatedAt()).isNotNull();
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
