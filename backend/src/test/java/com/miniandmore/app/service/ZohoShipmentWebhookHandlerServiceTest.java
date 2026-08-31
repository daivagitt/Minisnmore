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

        Map<String, Object> result = handler.handle(body, null, SECRET);

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

        Map<String, Object> result = handler.handle(body, null, SECRET);

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

        Map<String, Object> result = handler.handle(body, null, SECRET);

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

        handler.handle(body, null, SECRET);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.getTrackingNumber()).isEqualTo("AWB-4");
        assertThat(order.getTrackingCarrier()).isEqualTo("DTDC");
        verify(orderEventService, never()).syncLifecycleEvents(any());
    }

    @Test
    void handle_topLevelOrderReferenceWithNestedShipment_preservesTopLevelContext() {
        String body =
            "{\"eventId\":\"evt-ship-7\",\"zohoSalesOrderId\":\"so-7\",\"data\":{\"shipment\":{\"shipment_id\":\"ship-7\"," +
            "\"status\":\"delivered\",\"tracking_number\":\"AWB-7\"}}}";
        CustomerOrder order = order("MNM-7", "so-7", OrderStatus.HAND_WRAPPED);
        when(webhookEventRepository.existsByEventId("evt-ship-7")).thenReturn(false);
        when(customerOrderRepository.findOneByZohoSalesOrderId("so-7")).thenReturn(Optional.of(order));
        when(customerOrderRepository.save(order)).thenReturn(order);

        handler.handle(body, null, SECRET);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.FULFILLED);
        assertThat(order.getTrackingNumber()).isEqualTo("AWB-7");
    }

    @Test
    void handle_unknownZohoSalesOrderId_fallsBackToSalesOrderNumber() {
        String body =
            "{\"eventId\":\"evt-ship-8\",\"zohoSalesOrderId\":\"so-not-yet-stored\",\"salesorder_number\":\"MNM-8\"," +
            "\"package_id\":\"pkg-8\"}";
        CustomerOrder order = order("MNM-8", null, OrderStatus.CONFIRMED);
        when(webhookEventRepository.existsByEventId("evt-ship-8")).thenReturn(false);
        when(customerOrderRepository.findOneByZohoSalesOrderId("so-not-yet-stored")).thenReturn(Optional.empty());
        when(customerOrderRepository.findOneByOrderNumber("MNM-8")).thenReturn(Optional.of(order));
        when(customerOrderRepository.save(order)).thenReturn(order);

        handler.handle(body, null, SECRET);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.HAND_WRAPPED);
    }

    @Test
    void handle_missingSignature_throwsUnauthorized() {
        String body = "{\"eventId\":\"evt-ship-5\",\"zohoSalesOrderId\":\"so-5\"}";

        assertThatThrownBy(() -> handler.handle(body, null))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Missing shipment webhook authentication");
    }

    @Test
    void handle_defaultZohoShipmentOrderPayload_derivesEventIdAndUsesSharedSecretHeader() {
        String body =
            "{\"shipment_order\":{\"shipment_order_id\":\"ship-default-1\",\"salesorder_id\":\"so-default-1\"," +
            "\"salesorder_number\":\"MNM-DEFAULT-1\",\"status\":\"delivered\",\"last_modified_time\":\"2026-08-31T10:00:00Z\"}}";
        CustomerOrder order = order("MNM-DEFAULT-1", "so-default-1", OrderStatus.HAND_WRAPPED);
        String eventId = "zoho-shipment:ship-default-1:delivered:2026-08-31T10:00:00Z";
        when(webhookEventRepository.existsByEventId(eventId)).thenReturn(false);
        when(customerOrderRepository.findOneByZohoSalesOrderId("so-default-1")).thenReturn(Optional.of(order));
        when(customerOrderRepository.save(order)).thenReturn(order);

        Map<String, Object> result = handler.handle(body, null, SECRET);

        assertThat(result).containsEntry("eventId", eventId).containsEntry("status", "FULFILLED");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.FULFILLED);
        verifySavedEvent(eventId, "so-default-1", null, "ship-default-1", "delivered");
    }

    @Test
    void handle_hmacSignature_stillWorks() {
        String body = "{\"eventId\":\"evt-ship-hmac\",\"zohoSalesOrderId\":\"so-hmac\",\"package_id\":\"pkg-hmac\"}";
        CustomerOrder order = order("MNM-HMAC", "so-hmac", OrderStatus.CONFIRMED);
        when(webhookEventRepository.existsByEventId("evt-ship-hmac")).thenReturn(false);
        when(customerOrderRepository.findOneByZohoSalesOrderId("so-hmac")).thenReturn(Optional.of(order));
        when(customerOrderRepository.save(order)).thenReturn(order);

        handler.handle(body, sign(body));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.HAND_WRAPPED);
    }

    @Test
    void handle_missingOrderReference_throwsBadRequest() {
        String body = "{\"eventId\":\"evt-ship-6\",\"status\":\"delivered\"}";
        when(webhookEventRepository.existsByEventId("evt-ship-6")).thenReturn(false);

        assertThatThrownBy(() -> handler.handle(body, null, SECRET))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Provide zohoSalesOrderId or salesorder_number");
    }

    @Test
    void handle_orderReferenceWithoutShipmentSignal_throwsBadRequest() {
        String body = "{\"eventId\":\"evt-ship-9\",\"zohoSalesOrderId\":\"so-9\"}";
        when(webhookEventRepository.existsByEventId("evt-ship-9")).thenReturn(false);

        assertThatThrownBy(() -> handler.handle(body, null, SECRET))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Missing shipment/package fields");

        verify(customerOrderRepository, never()).findOneByZohoSalesOrderId(any());
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
