package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.config.ApplicationProperties;
import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.enumeration.OrderStatus;
import com.miniandmore.app.domain.enumeration.PaymentStatus;
import com.miniandmore.app.repository.CustomerOrderRepository;
import com.miniandmore.app.repository.XpressBeesShipmentWebhookEventRepository;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class XpressBeesShipmentWebhookHandlerServiceTest {

    @Mock
    private ApplicationProperties applicationProperties;

    @Mock
    private XpressBeesShipmentWebhookEventRepository eventRepository;

    @Mock
    private CustomerOrderRepository orderRepository;

    @Mock
    private OrderShipmentService orderShipmentService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private XpressBeesShipmentWebhookHandlerService service;

    @BeforeEach
    void setUp() {
        ApplicationProperties.XpressBees config = new ApplicationProperties.XpressBees();
        config.setWebhookSecret("secret");
        when(applicationProperties.getXpressbees()).thenReturn(config);
    }

    @Test
    void deliveredEventIsIdempotentlyAppliedToForwardOrder() {
        CustomerOrder order = new CustomerOrder()
            .id(10L)
            .orderNumber("MNM-1")
            .status(OrderStatus.CONFIRMED)
            .paymentStatus(PaymentStatus.PAID)
            .trackingNumber("XB-1");
        when(orderRepository.findOneByOrderNumber("MNM-1")).thenReturn(Optional.of(order));
        when(orderShipmentService.applyProviderStatus(eq(order), eq("delivered"), any())).thenAnswer(invocation -> {
            order.setStatus(OrderStatus.FULFILLED);
            order.setShipmentDeliveredAt(java.time.Instant.now());
            order.setShipmentCarrierStatus("delivered");
            return order;
        });
        when(eventRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(eventRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Map<String, Object> result = service.handle(
            "{\"event_id\":\"evt-1\",\"order_number\":\"MNM-1\",\"awb\":\"XB-1\",\"status\":\"delivered\"}",
            null,
            "secret"
        );

        assertThat(result.get("local_status")).isEqualTo("delivered");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.FULFILLED);
        assertThat(order.getShipmentDeliveredAt()).isNotNull();
        verify(orderShipmentService).applyProviderStatus(eq(order), eq("delivered"), any());
    }

    @Test
    void deliveredEventDoesNotResurrectCancelledOrder() {
        CustomerOrder order = new CustomerOrder()
            .id(11L)
            .orderNumber("MNM-2")
            .status(OrderStatus.CANCELLED)
            .paymentStatus(PaymentStatus.REFUNDED)
            .trackingNumber("XB-2");
        when(orderRepository.findOneByOrderNumber("MNM-2")).thenReturn(Optional.of(order));
        when(orderShipmentService.applyProviderStatus(eq(order), eq("delivered"), any())).thenReturn(order);
        when(eventRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(eventRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.handle(
            "{\"event_id\":\"evt-2\",\"order_number\":\"MNM-2\",\"awb\":\"XB-2\",\"status\":\"delivered\"}",
            null,
            "secret"
        );

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getShipmentDeliveredAt()).isNull();
        verify(orderShipmentService).applyProviderStatus(eq(order), eq("delivered"), any());
    }

    @Test
    void sampleCarrierPayload_looksUpOrderByAwbNumber() {
        CustomerOrder order = new CustomerOrder()
            .id(12L)
            .orderNumber("MNM-3")
            .status(OrderStatus.CONFIRMED)
            .paymentStatus(PaymentStatus.PAID)
            .trackingNumber("4152912381315");
        when(orderRepository.findFirstByTrackingNumber("4152912381315")).thenReturn(Optional.of(order));
        when(orderShipmentService.applyProviderStatus(eq(order), eq("in transit"), any())).thenAnswer(invocation -> {
            order.setShipmentCarrierStatus("in_transit");
            return order;
        });
        when(eventRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(eventRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Map<String, Object> result = service.handle(
            "{\"awb_number\":\"4152912381315\",\"status\":\"in transit\",\"event_time\":\"2021-02-26 16:19:59\",\"location\":\"Delhi\",\"message\":\"Reached at nearest hub\",\"rto_awb\":\"\"}",
            null,
            "secret"
        );

        assertThat(result.get("ok")).isEqualTo(true);
        assertThat(result.get("order_number")).isEqualTo("MNM-3");
        assertThat(order.getShipmentCarrierStatus()).isEqualTo("in_transit");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void cancelledEventIsAppliedToForwardOrderWithoutFulfilling() {
        CustomerOrder order = new CustomerOrder()
            .id(13L)
            .orderNumber("MNM-4")
            .status(OrderStatus.CONFIRMED)
            .paymentStatus(PaymentStatus.PAID)
            .trackingNumber("XB-4");
        when(orderRepository.findOneByOrderNumber("MNM-4")).thenReturn(Optional.of(order));
        when(orderShipmentService.applyProviderStatus(eq(order), eq("Cancelled"), any())).thenAnswer(invocation -> {
            order.setShipmentCarrierStatus("carrier_cancelled");
            return order;
        });
        when(eventRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(eventRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Map<String, Object> result = service.handle(
            "{\"event_id\":\"evt-cancel\",\"order_number\":\"MNM-4\",\"awb\":\"XB-4\",\"status\":\"Cancelled\"}",
            null,
            "secret"
        );

        assertThat(result.get("ok")).isEqualTo(true);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.getShipmentCarrierStatus()).isEqualTo("carrier_cancelled");
        verify(orderShipmentService).applyProviderStatus(eq(order), eq("Cancelled"), any());
    }

    @Test
    void staleAwbAfterRebookDoesNotUpdateTheNewShipment() {
        CustomerOrder order = new CustomerOrder()
            .id(14L)
            .orderNumber("MNM-5")
            .status(OrderStatus.CONFIRMED)
            .paymentStatus(PaymentStatus.PAID)
            .trackingNumber("XB-NEW");
        when(orderRepository.findOneByOrderNumber("MNM-5")).thenReturn(Optional.of(order));

        Map<String, Object> result = service.handle(
            "{\"event_id\":\"evt-old-cancel\",\"order_number\":\"MNM-5\",\"awb\":\"XB-OLD\",\"status\":\"Cancelled\"}",
            null,
            "secret"
        );

        assertThat(result.get("ok")).isEqualTo(true);
        assertThat(result.get("skipped")).isEqualTo(true);
        assertThat(result.get("reason")).isEqualTo("stale_awb");
        assertThat(order.getTrackingNumber()).isEqualTo("XB-NEW");
        assertThat(order.getShipmentCarrierStatus()).isNull();
        verify(orderShipmentService, never()).applyProviderStatus(any(), any(), any());
        verify(eventRepository, never()).saveAndFlush(any());
    }
}
