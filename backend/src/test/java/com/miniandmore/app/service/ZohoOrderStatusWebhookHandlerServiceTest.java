package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.config.ApplicationProperties;
import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.ZohoOrderStatusWebhookEvent;
import com.miniandmore.app.domain.enumeration.OrderStatus;
import com.miniandmore.app.domain.enumeration.PaymentStatus;
import com.miniandmore.app.repository.CustomerOrderRepository;
import com.miniandmore.app.repository.ZohoOrderStatusWebhookEventRepository;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ZohoOrderStatusWebhookHandlerServiceTest {

    private static final String SECRET = "zohoorderstatussecret";

    @Mock
    private ZohoOrderStatusWebhookEventRepository webhookEventRepository;

    @Mock
    private CustomerOrderRepository customerOrderRepository;

    @Mock
    private OrderEventService orderEventService;

    private ZohoOrderStatusWebhookHandlerService handler;

    @BeforeEach
    void setUp() {
        ApplicationProperties properties = new ApplicationProperties();
        properties.getZoho().setOrderStatusWebhookSecret(SECRET);
        handler = new ZohoOrderStatusWebhookHandlerService(
            properties,
            webhookEventRepository,
            customerOrderRepository,
            orderEventService,
            new ObjectMapper()
        );
    }

    @Test
    void handle_invoicedStatus_updatesZohoOrderStatus() {
        String body =
            "{\"eventId\":\"evt-so-1\",\"salesorder\":{\"salesorder_id\":\"so-1\",\"reference_number\":\"MNM-SO-1\",\"status\":\"invoiced\"}}";
        CustomerOrder order = order("MNM-SO-1", "so-1", OrderStatus.CONFIRMED);
        when(webhookEventRepository.existsByEventId("evt-so-1")).thenReturn(false);
        when(customerOrderRepository.findOneByZohoSalesOrderId("so-1")).thenReturn(Optional.of(order));
        when(customerOrderRepository.save(order)).thenReturn(order);

        Map<String, Object> result = handler.handle(body, null, SECRET);

        assertThat(order.getZohoOrderStatus()).isEqualTo("invoiced");
        assertThat(result).containsEntry("orderNumber", "MNM-SO-1").containsEntry("status", "CONFIRMED");
        verifySavedEvent("evt-so-1", "so-1", "invoiced");
    }

    @Test
    void handle_voidStatus_cancelsOrder() {
        String body =
            "{\"eventId\":\"evt-so-void\",\"salesorder\":{\"salesorder_id\":\"so-void\",\"reference_number\":\"MNM-SO-VOID\",\"status\":\"void\"}}";
        CustomerOrder order = order("MNM-SO-VOID", "so-void", OrderStatus.CONFIRMED);
        when(webhookEventRepository.existsByEventId("evt-so-void")).thenReturn(false);
        when(customerOrderRepository.findOneByZohoSalesOrderId("so-void")).thenReturn(Optional.of(order));
        when(customerOrderRepository.save(order)).thenReturn(order);

        Map<String, Object> result = handler.handle(body, null, SECRET);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(result).containsEntry("status", "CANCELLED");
        verify(orderEventService).syncLifecycleEvents(order);
    }

    @Test
    void handle_localForwardShipment_ignoresZohoDeliveryStatus() {
        String body =
            "{\"eventId\":\"evt-so-local\",\"salesorder\":{\"salesorder_id\":\"so-local\",\"reference_number\":\"MNM-SO-LOCAL\",\"status\":\"delivered\",\"tracking_number\":\"ZOHO-AWB\"}}";
        CustomerOrder order = order("MNM-SO-LOCAL", "so-local", OrderStatus.CONFIRMED)
            .trackingNumber("XB-LOCAL")
            .trackingCarrier("XpressBees")
            .shipmentLogisticsMode(com.miniandmore.app.domain.enumeration.ReturnLogisticsMode.XPRESSBEES)
            .shipmentCarrierStatus("booked");
        when(webhookEventRepository.existsByEventId("evt-so-local")).thenReturn(false);
        when(customerOrderRepository.findOneByZohoSalesOrderId("so-local")).thenReturn(Optional.of(order));
        when(customerOrderRepository.save(order)).thenReturn(order);

        Map<String, Object> result = handler.handle(body, null, SECRET);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.getTrackingNumber()).isEqualTo("XB-LOCAL");
        assertThat(order.getZohoOrderStatus()).isEqualTo("delivered");
        assertThat(result).containsEntry("status", "CONFIRMED");
        verify(orderEventService, never()).syncLifecycleEvents(any());
    }

    @Test
    void handle_duplicateEventId_isNoOp() {
        String body =
            "{\"eventId\":\"evt-so-dup\",\"salesorder\":{\"salesorder_id\":\"so-dup\",\"reference_number\":\"MNM-SO-DUP\",\"status\":\"invoiced\"}}";
        CustomerOrder order = order("MNM-SO-DUP", "so-dup", OrderStatus.CONFIRMED);
        when(customerOrderRepository.findOneByZohoSalesOrderId("so-dup")).thenReturn(Optional.of(order));
        when(webhookEventRepository.existsByEventId("evt-so-dup")).thenReturn(true);

        Map<String, Object> result = handler.handle(body, null, SECRET);

        assertThat(result).containsEntry("duplicate", true);
        verify(customerOrderRepository, never()).save(any());
        verify(webhookEventRepository, never()).saveAndFlush(any());
    }

    @Test
    void handle_unknownSalesOrder_returnsNotFound() {
        String body =
            "{\"eventId\":\"evt-so-missing\",\"salesorder\":{\"salesorder_id\":\"so-missing\",\"reference_number\":\"MNM-MISSING\",\"status\":\"invoiced\"}}";
        when(customerOrderRepository.findOneByZohoSalesOrderId("so-missing")).thenReturn(Optional.empty());
        when(customerOrderRepository.findOneByOrderNumber("MNM-MISSING")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(body, null, SECRET))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("No order for Zoho sales order so-missing");
    }

    private static CustomerOrder order(String orderNumber, String zohoSalesOrderId, OrderStatus status) {
        return new CustomerOrder()
            .id(601L)
            .orderNumber(orderNumber)
            .userLogin("buyer@example.com")
            .paymentStatus(PaymentStatus.PAID)
            .status(status)
            .zohoSalesOrderId(zohoSalesOrderId);
    }

    private void verifySavedEvent(String eventId, String salesOrderId, String status) {
        ArgumentCaptor<ZohoOrderStatusWebhookEvent> captor = ArgumentCaptor.forClass(ZohoOrderStatusWebhookEvent.class);
        verify(webhookEventRepository).saveAndFlush(captor.capture());
        ZohoOrderStatusWebhookEvent saved = captor.getValue();
        assertThat(saved.getEventId()).isEqualTo(eventId);
        assertThat(saved.getZohoSalesOrderId()).isEqualTo(salesOrderId);
        assertThat(saved.getStatus()).isEqualTo(status);
    }
}
