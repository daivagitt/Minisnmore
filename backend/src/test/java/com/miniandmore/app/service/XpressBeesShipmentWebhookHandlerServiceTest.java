package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
    private OrderEventService orderEventService;

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
        when(orderRepository.save(order)).thenReturn(order);
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
        verify(orderEventService).syncLifecycleEvents(order);
    }
}
