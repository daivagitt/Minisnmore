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
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ZohoOrderStatusWebhookHandlerServiceTest {

    private static final String SECRET = "zoho-order-status-secret";

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
    void handle_duplicateEventId_isNoOp() {
        String body = "{\"eventId\":\"evt-so-1\",\"salesorder_id\":\"so-1\",\"status\":\"void\"}";
        when(webhookEventRepository.existsByEventId("evt-so-1")).thenReturn(true);

        Map<String, Object> result = handler.handle(body, null, SECRET);

        assertThat(result).containsEntry("duplicate", true);
        verify(customerOrderRepository, never()).save(any());
        verify(webhookEventRepository, never()).saveAndFlush(any());
    }

    @Test
    void handle_voidStatus_cancelsLinkedOrderAndRecordsEvent() {
        String body = "{\"eventId\":\"evt-so-2\",\"salesorder_id\":\"so-2\",\"salesorder_number\":\"MNM-2\",\"status\":\"void\"}";
        CustomerOrder order = order("MNM-2", "so-2", OrderStatus.CONFIRMED);
        when(webhookEventRepository.existsByEventId("evt-so-2")).thenReturn(false);
        when(customerOrderRepository.findOneByZohoSalesOrderId("so-2")).thenReturn(Optional.of(order));
        when(customerOrderRepository.save(order)).thenReturn(order);

        Map<String, Object> result = handler.handle(body, null, SECRET);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getCancellationReason()).isEqualTo("Cancelled in Zoho");
        assertThat(result).containsEntry("status", "CANCELLED");
        verify(orderEventService).syncLifecycleEvents(order);
        ArgumentCaptor<ZohoOrderStatusWebhookEvent> captor = ArgumentCaptor.forClass(ZohoOrderStatusWebhookEvent.class);
        verify(webhookEventRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getZohoSalesOrderId()).isEqualTo("so-2");
        assertThat(captor.getValue().getSalesorderNumber()).isEqualTo("MNM-2");
    }

    @Test
    void handle_nestedSalesOrderPayload_usesOrderNumberFallbackAndTrackingFields() {
        String body =
            "{\"event_id\":\"evt-so-3\",\"data\":{\"salesorder\":{\"salesorder_number\":\"MNM-3\",\"status\":\"shipped\"," +
            "\"shipment_date\":\"2026-08-31\",\"delivery_method\":\"DTDC\",\"tracking_number\":\"AWB-3\"}}}";
        CustomerOrder order = order("MNM-3", null, OrderStatus.CONFIRMED);
        when(webhookEventRepository.existsByEventId("evt-so-3")).thenReturn(false);
        when(customerOrderRepository.findOneByOrderNumber("MNM-3")).thenReturn(Optional.of(order));
        when(customerOrderRepository.save(order)).thenReturn(order);

        handler.handle(body, null, SECRET);

        assertThat(order.getZohoOrderStatus()).isEqualTo("shipped");
        assertThat(order.getTrackingNumber()).isEqualTo("AWB-3");
        assertThat(order.getTrackingCarrier()).isEqualTo("DTDC");
        assertThat(order.getShipmentDate()).isEqualTo("2026-08-31");
        verify(orderEventService).syncLifecycleEvents(order);
    }

    @Test
    void handle_defaultPayload_derivesEventId() {
        String body =
            "{\"salesorder\":{\"salesorder_id\":\"so-4\",\"salesorder_number\":\"MNM-4\",\"status\":\"void\"," +
            "\"last_modified_time\":\"2026-08-31T12:00:00Z\"}}";
        CustomerOrder order = order("MNM-4", "so-4", OrderStatus.CONFIRMED);
        String eventId = "zoho-salesorder:so-4:void:2026-08-31T12:00:00Z";
        when(webhookEventRepository.existsByEventId(eventId)).thenReturn(false);
        when(customerOrderRepository.findOneByZohoSalesOrderId("so-4")).thenReturn(Optional.of(order));
        when(customerOrderRepository.save(order)).thenReturn(order);

        Map<String, Object> result = handler.handle(body, null, SECRET);

        assertThat(result).containsEntry("eventId", eventId);
    }

    @Test
    void handle_hmacSignature_isAccepted() {
        String body = "{\"eventId\":\"evt-so-hmac\",\"salesorder_id\":\"so-h\",\"status\":\"void\"}";
        CustomerOrder order = order("MNM-H", "so-h", OrderStatus.CONFIRMED);
        when(webhookEventRepository.existsByEventId("evt-so-hmac")).thenReturn(false);
        when(customerOrderRepository.findOneByZohoSalesOrderId("so-h")).thenReturn(Optional.of(order));
        when(customerOrderRepository.save(order)).thenReturn(order);

        Map<String, Object> result = handler.handle(body, sign(body), null);

        assertThat(result).containsEntry("ok", true);
    }

    @Test
    void handle_missingStatus_throwsBadRequest() {
        String body = "{\"eventId\":\"evt-so-5\",\"salesorder_id\":\"so-5\"}";

        assertThatThrownBy(() -> handler.handle(body, null, SECRET))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Missing sales order status");
    }

    private static CustomerOrder order(String orderNumber, String zohoSalesOrderId, OrderStatus status) {
        CustomerOrder order = new CustomerOrder();
        order.setOrderNumber(orderNumber);
        order.setZohoSalesOrderId(zohoSalesOrderId);
        order.setStatus(status);
        order.setPaymentStatus(PaymentStatus.PAID);
        return order;
    }

    private static String sign(String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
