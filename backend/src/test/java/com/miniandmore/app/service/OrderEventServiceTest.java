package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
    void onOrderCreated_recordsPlacedEvent() {
        CustomerOrder order = baseOrder().status(OrderStatus.PENDING).paymentStatus(PaymentStatus.PENDING);
        when(orderEventRepository.existsByOrderIdAndEventType(42L, OrderEventService.TYPE_ORDER_PLACED)).thenReturn(false);

        service.onOrderCreated(order);

        ArgumentCaptor<OrderEvent> captor = ArgumentCaptor.forClass(OrderEvent.class);
        verify(orderEventRepository).save(captor.capture());
        OrderEvent event = captor.getValue();
        assertThat(event.getEventType()).isEqualTo(OrderEventService.TYPE_ORDER_PLACED);
        assertThat(event.getTitle()).isEqualTo("Order placed");
        assertThat(event.getCreatedAt()).isEqualTo(order.getPlacedAt());
        verify(orderMailService, never()).sendOrderConfirmation(any());
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
