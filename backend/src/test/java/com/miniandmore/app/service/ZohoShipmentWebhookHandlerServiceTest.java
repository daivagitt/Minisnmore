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
import com.miniandmore.app.domain.ZohoShipmentWebhookEvent;
import com.miniandmore.app.domain.enumeration.OrderStatus;
import com.miniandmore.app.domain.enumeration.PaymentStatus;
import com.miniandmore.app.repository.CustomerOrderRepository;
import com.miniandmore.app.repository.ZohoShipmentWebhookEventRepository;
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
class ZohoShipmentWebhookHandlerServiceTest {

    private static final String SECRET = "zoho-shipment-secret";

    @Mock
    private ZohoShipmentWebhookEventRepository webhookEventRepository;

    @Mock
    private CustomerOrderRepository customerOrderRepository;

    @Mock
    private OrderEventService orderEventService;

    private ZohoShipmentWebhookHandlerService handler;

    @BeforeEach
    void setUp() {
        ApplicationProperties properties = new ApplicationProperties();
        properties.getZoho().setShipmentWebhookSecret(SECRET);
        handler = new ZohoShipmentWebhookHandlerService(
            properties,
            webhookEventRepository,
            customerOrderRepository,
            orderEventService,
            new ObjectMapper()
        );
    }

    @Test
    void handle_duplicateEventId_isNoOp() {
        String body = "{\"eventId\":\"evt-ship-1\",\"zohoSalesOrderId\":\"so-1\",\"status\":\"shipped\"}";
        when(webhookEventRepository.existsByEventId("evt-ship-1")).thenReturn(true);

        Map<String, Object> result = handler.handle(body, sign(body));

        assertThat(result).containsEntry("duplicate", true);
        verify(customerOrderRepository, never()).save(any());
        verify(webhookEventRepository, never()).saveAndFlush(any());
    }

    @Test
    void handle_packageCreated_marksConfirmedOrderHandWrapped() {
        String body = "{\"eventId\":\"evt-ship-2\",\"zohoSalesOrderId\":\"so-2\",\"package_id\":\"pkg-2\",\"status\":\"not_shipped\"}";
        CustomerOrder order = order("MNM-2", "so-2", OrderStatus.CONFIRMED);
        when(webhookEventRepository.existsByEventId("evt-ship-2")).thenReturn(false);
        when(customerOrderRepository.findOneByZohoSalesOrderId("so-2")).thenReturn(Optional.of(order));
        when(customerOrderRepository.save(order)).thenReturn(order);

        Map<String, Object> result = handler.handle(body, sign(body));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.HAND_WRAPPED);
        assertThat(result).containsEntry("status", "HAND_WRAPPED");
        verify(orderEventService).syncLifecycleEvents(order);
        verifySavedEvent("evt-ship-2", "so-2", "pkg-2", null, "not_shipped");
    }

    @Test
    void handle_deliveredShipment_marksOrderFulfilledAndCopiesTracking() {
        String body =
            "{\"eventId\":\"evt-ship-3\",\"zohoSalesOrderId\":\"so-3\",\"shipment_id\":\"ship-3\",\"status\":\"delivered\"," +
            "\"tracking_number\":\"AWB-3\",\"carrier\":\"BlueDart\",\"shipment_date\":\"2026-08-31\"}";
        CustomerOrder order = order("MNM-3", "so-3", OrderStatus.HAND_WRAPPED);
        when(webhookEventRepository.existsByEventId("evt-ship-3")).thenReturn(false);
        when(customerOrderRepository.findOneByZohoSalesOrderId("so-3")).thenReturn(Optional.of(order));
        when(customerOrderRepository.save(order)).thenReturn(order);

        Map<String, Object> result = handler.handle(body, sign(body));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.FULFILLED);
        assertThat(order.getTrackingNumber()).isEqualTo("AWB-3");
        assertThat(order.getTrackingCarrier()).isEqualTo("BlueDart");
        assertThat(order.getShipmentDate()).isEqualTo("2026-08-31");
        assertThat(result).containsEntry("status", "FULFILLED");
        verify(orderEventService).syncLifecycleEvents(order);
    }

    @Test
    void handle_nestedShipmentOrderPayload_usesSalesOrderNumberFallback() {
        String body =
            "{\"event_id\":\"evt-ship-4\",\"data\":{\"shipment_order\":{\"salesorder_number\":\"MNM-4\"," +
            "\"shipment_id\":\"ship-4\",\"status\":\"shipped\",\"delivery_method\":\"DTDC\",\"trackingNumber\":\"AWB-4\"}}}";
        CustomerOrder order = order("MNM-4", null, OrderStatus.CONFIRMED);
        when(webhookEventRepository.existsByEventId("evt-ship-4")).thenReturn(false);
        when(customerOrderRepository.findOneByOrderNumber("MNM-4")).thenReturn(Optional.of(order));
        when(customerOrderRepository.save(order)).thenReturn(order);

        handler.handle(body, sign(body));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.getTrackingNumber()).isEqualTo("AWB-4");
        assertThat(order.getTrackingCarrier()).isEqualTo("DTDC");
        verify(orderEventService, never()).syncLifecycleEvents(any());
    }

    @Test
    void handle_missingSignature_throwsUnauthorized() {
        String body = "{\"eventId\":\"evt-ship-5\",\"zohoSalesOrderId\":\"so-5\"}";

        assertThatThrownBy(() -> handler.handle(body, null))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Missing X-Zoho-Shipment-Signature");
    }

    @Test
    void handle_missingOrderReference_throwsBadRequest() {
        String body = "{\"eventId\":\"evt-ship-6\",\"status\":\"delivered\"}";
        when(webhookEventRepository.existsByEventId("evt-ship-6")).thenReturn(false);

        assertThatThrownBy(() -> handler.handle(body, sign(body)))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Provide zohoSalesOrderId or salesorder_number");
    }

    private void verifySavedEvent(String eventId, String zohoSalesOrderId, String packageId, String shipmentId, String status) {
        ArgumentCaptor<ZohoShipmentWebhookEvent> captor = ArgumentCaptor.forClass(ZohoShipmentWebhookEvent.class);
        verify(webhookEventRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getEventId()).isEqualTo(eventId);
        assertThat(captor.getValue().getZohoSalesOrderId()).isEqualTo(zohoSalesOrderId);
        assertThat(captor.getValue().getPackageId()).isEqualTo(packageId);
        assertThat(captor.getValue().getShipmentId()).isEqualTo(shipmentId);
        assertThat(captor.getValue().getStatus()).isEqualTo(status);
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
