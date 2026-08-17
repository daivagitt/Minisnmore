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

    private OrderEventService service;

    @BeforeEach
    void setUp() {
        service = new OrderEventService(orderEventRepository);
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
    }

    @Test
    void onOrderPaid_recordsPaymentAndShippedWhenDemoShipmentStamped() {
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
    }

    @Test
    void recordIfAbsent_skipsDuplicates() {
        CustomerOrder order = baseOrder().paymentStatus(PaymentStatus.PAID);
        when(orderEventRepository.existsByOrderIdAndEventType(42L, OrderEventService.TYPE_ORDER_PLACED)).thenReturn(true);
        when(orderEventRepository.existsByOrderIdAndEventType(42L, OrderEventService.TYPE_PAYMENT_RECEIVED)).thenReturn(true);

        service.syncLifecycleEvents(order);

        verify(orderEventRepository, never()).save(any());
    }

    private CustomerOrder baseOrder() {
        return new CustomerOrder()
            .id(42L)
            .userLogin("buyer@example.com")
            .orderNumber("MNM-TEST-001")
            .total(BigDecimal.valueOf(299))
            .placedAt(Instant.parse("2026-08-14T10:00:00Z"));
    }
}
