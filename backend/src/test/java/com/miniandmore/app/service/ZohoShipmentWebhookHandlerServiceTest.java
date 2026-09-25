package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.client.ZohoBooksClient;
import com.miniandmore.app.config.ApplicationProperties;
import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.ZohoShipmentWebhookEvent;
import com.miniandmore.app.domain.enumeration.OrderStatus;
import com.miniandmore.app.domain.enumeration.PaymentStatus;
import com.miniandmore.app.repository.CustomerOrderRepository;
import com.miniandmore.app.repository.ZohoShipmentWebhookEventRepository;
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
class ZohoShipmentWebhookHandlerServiceTest {

    private static final String SECRET = "zoho-shipment-secret";

    @Mock
    private ZohoShipmentWebhookEventRepository webhookEventRepository;

    @Mock
    private CustomerOrderRepository customerOrderRepository;

    @Mock
    private OrderEventService orderEventService;

    @Mock
    private ZohoBooksClient booksClient;

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
            booksClient,
            new ObjectMapper()
        );
    }

    @Test
    void handle_duplicateEventId_isNoOp() {
        String body = "{\"eventId\":\"evt-ship-1\",\"zohoSalesOrderId\":\"so-1\",\"status\":\"shipped\"}";
        CustomerOrder order = order("MNM-1", "so-1", OrderStatus.CONFIRMED);
        when(webhookEventRepository.existsByEventId("evt-ship-1")).thenReturn(true);
        when(customerOrderRepository.findOneByZohoSalesOrderId("so-1")).thenReturn(Optional.of(order));

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
    void handle_localForwardShipment_doesNotApplyZohoStatus() {
        String body =
            "{\"eventId\":\"evt-ship-local\",\"zohoSalesOrderId\":\"so-local\",\"shipment_id\":\"ship-local\",\"status\":\"delivered\"," +
            "\"tracking_number\":\"ZOHO-AWB\",\"carrier\":\"DTDC\"}";
        CustomerOrder order = order("MNM-LOCAL", "so-local", OrderStatus.CONFIRMED);
        order.setTrackingNumber("XB-LOCAL");
        order.setTrackingCarrier("XpressBees");
        order.setShipmentLogisticsMode(com.miniandmore.app.domain.enumeration.ReturnLogisticsMode.XPRESSBEES);
        order.setShipmentCarrierStatus("booked");
        when(webhookEventRepository.existsByEventId("evt-ship-local")).thenReturn(false);
        when(customerOrderRepository.findOneByZohoSalesOrderId("so-local")).thenReturn(Optional.of(order));
        when(customerOrderRepository.save(order)).thenReturn(order);

        Map<String, Object> result = handler.handle(body, null, SECRET);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.getTrackingNumber()).isEqualTo("XB-LOCAL");
        assertThat(order.getTrackingCarrier()).isEqualTo("XpressBees");
        assertThat(result).containsEntry("status", "CONFIRMED");
        verify(orderEventService, never()).syncLifecycleEvents(any());
        verifySavedEvent("evt-ship-local", "so-local", null, "ship-local", "delivered");
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
        assertThat(order.getZohoOrderStatus()).isEqualTo("shipped");
        assertThat(order.getTrackingNumber()).isEqualTo("AWB-4");
        assertThat(order.getTrackingCarrier()).isEqualTo("DTDC");
        verify(orderEventService).syncLifecycleEvents(order);
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

        handler.handle(body, WebhookTestSupport.hmacSha256Hex(SECRET, body));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.HAND_WRAPPED);
    }

    @Test
    void handle_missingOrderReference_throwsBadRequest() {
        String body = "{\"eventId\":\"evt-ship-6\",\"status\":\"delivered\"}";

        assertThatThrownBy(() -> handler.handle(body, null, SECRET))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Provide zohoSalesOrderId or salesorder_number");
    }

    @Test
    void handle_orderReferenceWithoutShipmentSignal_throwsBadRequest() {
        String body = "{\"eventId\":\"evt-ship-9\",\"zohoSalesOrderId\":\"so-9\"}";
        CustomerOrder order = order("MNM-9", "so-9", OrderStatus.HAND_WRAPPED);
        when(webhookEventRepository.existsByEventId("evt-ship-9")).thenReturn(false);
        when(customerOrderRepository.findOneByZohoSalesOrderId("so-9")).thenReturn(Optional.of(order));
        when(customerOrderRepository.save(order)).thenReturn(order);

        Map<String, Object> result = handler.handle(body, null, SECRET);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.FULFILLED);
        assertThat(result).containsEntry("status", "FULFILLED");
        verifySavedEvent("evt-ship-9", "so-9", null, null, "delivered");
    }

    @Test
    void handle_minimalShipmentWebhookWithoutEventId_derivesEventIdAndMarksDelivered() {
        String body = "{\"shipment_order\":{\"salesorder_id\":\"so-minimal\"}}";
        CustomerOrder order = order("MNM-MINIMAL", "so-minimal", OrderStatus.HAND_WRAPPED);
        String eventId = "zoho-shipment:so-minimal:delivered:unknown";
        when(webhookEventRepository.existsByEventId(eventId)).thenReturn(false);
        when(customerOrderRepository.findOneByZohoSalesOrderId("so-minimal")).thenReturn(Optional.of(order));
        when(customerOrderRepository.save(order)).thenReturn(order);

        Map<String, Object> result = handler.handle(body, null, SECRET);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.FULFILLED);
        assertThat(result).containsEntry("eventId", eventId).containsEntry("status", "FULFILLED");
        verifySavedEvent(eventId, "so-minimal", null, null, "delivered");
    }

    @Test
    void handle_missingStatus_infersDeliveredFromEventId() {
        String body =
            "{\"eventId\":\"4066570000000369183delivered2026-09-01T12:31:00+0530\",\"shipment_order\":{\"shipment_order_id\":\"4066570000000369183\",\"salesorder_id\":\"so-delivered-infer\",\"salesorder_number\":\"MNM-DELIVERED-INFER\",\"shipment_date\":\"2026-09-01\"}}";
        CustomerOrder order = order("MNM-DELIVERED-INFER", "so-delivered-infer", OrderStatus.HAND_WRAPPED);
        when(webhookEventRepository.existsByEventId("4066570000000369183delivered2026-09-01T12:31:00+0530")).thenReturn(false);
        when(customerOrderRepository.findOneByZohoSalesOrderId("so-delivered-infer")).thenReturn(Optional.of(order));
        when(customerOrderRepository.save(order)).thenReturn(order);

        Map<String, Object> result = handler.handle(body, null, SECRET);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.FULFILLED);
        assertThat(result).containsEntry("status", "FULFILLED");
    }

    @Test
    void handle_missingStatus_infersFulfilledFromEventIdAndMarksDeliveredForShipmentOrder() {
        String body =
            "{\"eventId\":\"4066570000000369183fulfilled2026-09-01T12:31:00+0530\",\"shipment_order\":{\"shipment_order_id\":\"4066570000000369183\",\"salesorder_id\":\"so-fulfilled-infer\",\"salesorder_number\":\"MNM-FULFILLED-INFER\",\"shipment_date\":\"2026-09-01\"}}";
        CustomerOrder order = order("MNM-FULFILLED-INFER", "so-fulfilled-infer", OrderStatus.HAND_WRAPPED);
        when(webhookEventRepository.existsByEventId("4066570000000369183fulfilled2026-09-01T12:31:00+0530")).thenReturn(false);
        when(customerOrderRepository.findOneByZohoSalesOrderId("so-fulfilled-infer")).thenReturn(Optional.of(order));
        when(customerOrderRepository.save(order)).thenReturn(order);

        Map<String, Object> result = handler.handle(body, null, SECRET);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.FULFILLED);
        assertThat(order.getZohoOrderStatus()).isEqualTo("fulfilled");
        assertThat(result).containsEntry("status", "FULFILLED");
    }

    @Test
    void handle_fulfilledShipmentOrderWithoutShipmentId_marksDelivered() {
        String body =
            "{\"shipment_order\":{\"salesorder_id\":\"so-live-shape\",\"salesorder_number\":\"SO-00069\"," +
            "\"status\":\"fulfilled\",\"shipment_date\":\"01/09/2026\"}}";
        CustomerOrder order = order("MNM-F722FB08B10B", "so-live-shape", OrderStatus.HAND_WRAPPED);
        String eventId = "zoho-shipment:so-live-shape:fulfilled:01/09/2026";
        when(webhookEventRepository.existsByEventId(eventId)).thenReturn(false);
        when(customerOrderRepository.findOneByZohoSalesOrderId("so-live-shape")).thenReturn(Optional.of(order));
        when(customerOrderRepository.save(order)).thenReturn(order);

        Map<String, Object> result = handler.handle(body, null, SECRET);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.FULFILLED);
        assertThat(order.getZohoOrderStatus()).isEqualTo("fulfilled");
        assertThat(OrderTrackingStatusResolver.resolve(order)).isEqualTo("delivered");
        assertThat(result).containsEntry("eventId", eventId).containsEntry("status", "FULFILLED");
        verifySavedEvent(eventId, "so-live-shape", null, null, "fulfilled");
    }

    @Test
    void handle_fulfilledShipmentOrderWithDeliveredDeliveryStatus_marksOrderFulfilled() {
        String body =
            "{\"shipment_order\":{\"salesorder_id\":\"so-live-delivered\",\"salesorder_number\":\"SO-00070\"," +
            "\"status\":\"fulfilled\",\"deliveryStatus\":\"delivered\",\"shipment_date\":\"2026-09-01\"}}";
        CustomerOrder order = order("MNM-DELIVERED", "so-live-delivered", OrderStatus.HAND_WRAPPED);
        String eventId = "zoho-shipment:so-live-delivered:fulfilled:2026-09-01";
        when(webhookEventRepository.existsByEventId(eventId)).thenReturn(false);
        when(customerOrderRepository.findOneByZohoSalesOrderId("so-live-delivered")).thenReturn(Optional.of(order));
        when(customerOrderRepository.save(order)).thenReturn(order);

        Map<String, Object> result = handler.handle(body, null, SECRET);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.FULFILLED);
        assertThat(OrderTrackingStatusResolver.resolve(order)).isEqualTo("delivered");
        assertThat(result).containsEntry("eventId", eventId).containsEntry("status", "FULFILLED");
        verifySavedEvent(eventId, "so-live-delivered", null, null, "fulfilled");
    }

    @Test
    void handle_duplicateEventId_reappliesWhenOrderStillNotDelivered() {
        String eventId = "zoho-shipment:ship-dup:delivered:2026-09-01T12:31:00Z";
        String body =
            "{\"eventId\":\"" +
            eventId +
            "\",\"shipment_order\":{\"shipment_order_id\":\"ship-dup\",\"salesorder_id\":\"so-dup\",\"status\":\"delivered\"}}";
        CustomerOrder order = order("MNM-DUP", "so-dup", OrderStatus.HAND_WRAPPED);
        when(webhookEventRepository.existsByEventId(eventId)).thenReturn(true);
        when(customerOrderRepository.findOneByZohoSalesOrderId("so-dup")).thenReturn(Optional.of(order));
        when(customerOrderRepository.save(order)).thenReturn(order);

        Map<String, Object> result = handler.handle(body, null, SECRET);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.FULFILLED);
        assertThat(result).containsEntry("status", "FULFILLED");
        verify(webhookEventRepository, never()).saveAndFlush(any());
    }

    @Test
    void handle_livePayload_packageSlipWithoutShippedStatusMarksHandWrappedWithoutShipmentDate() {
        String body =
            "{\"eventId\":\"ship-pkg2026-09-02T10:00:00+0530\",\"shipment_order\":{" +
            "\"shipment_order_id\":\"ship-pkg\",\"salesorder_id\":\"so-pkg\",\"salesorder_number\":\"SO-00071\"," +
            "\"tracking_number\":\"\",\"reference_number\":\"SO-00071\",\"carrier\":\"\",\"delivery_method\":\"\"," +
            "\"shipment_date\":\"\",\"status\":\"\",\"detailed_status\":\"\",\"status_message\":\"\"," +
            "\"delivery_status\":\"\",\"cf_minims_delivery_status\":\"\",\"last_modified_time\":\"2026-09-02T10:00:00+0530\"}}";
        CustomerOrder order = order("MNM-PKG", "so-pkg", OrderStatus.CONFIRMED);
        when(webhookEventRepository.existsByEventId("ship-pkg2026-09-02T10:00:00+0530")).thenReturn(false);
        when(customerOrderRepository.findOneByZohoSalesOrderId("so-pkg")).thenReturn(Optional.of(order));
        when(customerOrderRepository.save(order)).thenReturn(order);

        Map<String, Object> result = handler.handle(body, null, SECRET);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.HAND_WRAPPED);
        assertThat(order.getShipmentDate()).isNull();
        assertThat(order.getTrackingNumber()).isNull();
        assertThat(OrderTrackingStatusResolver.resolve(order)).isEqualTo("hand_wrapped");
        assertThat(result).containsEntry("status", "HAND_WRAPPED");
        verifySavedEvent("ship-pkg2026-09-02T10:00:00+0530", "so-pkg", null, "ship-pkg", null);
    }

    @Test
    void handle_livePayload_onTheWayShippedMarksTrackingShippedNotDelivered() {
        String body =
            "{\"eventId\":\"ship-way2026-09-02T11:00:00+0530\",\"shipment_order\":{" +
            "\"shipment_order_id\":\"ship-way\",\"salesorder_id\":\"so-way\",\"salesorder_number\":\"SO-00072\"," +
            "\"tracking_number\":\"\",\"reference_number\":\"SO-00072\",\"carrier\":\"DTDC\",\"delivery_method\":\"DTDC\"," +
            "\"shipment_date\":\"2026-09-02\",\"status\":\"Shipped\",\"detailed_status\":\"\",\"status_message\":\"\"," +
            "\"delivery_status\":\"\",\"cf_minims_delivery_status\":\"\",\"last_modified_time\":\"2026-09-02T11:00:00+0530\"}}";
        CustomerOrder order = order("MNM-WAY", "so-way", OrderStatus.CONFIRMED);
        when(webhookEventRepository.existsByEventId("ship-way2026-09-02T11:00:00+0530")).thenReturn(false);
        when(customerOrderRepository.findOneByZohoSalesOrderId("so-way")).thenReturn(Optional.of(order));
        when(customerOrderRepository.save(order)).thenReturn(order);

        Map<String, Object> result = handler.handle(body, null, SECRET);

        assertThat(order.getStatus()).isNotEqualTo(OrderStatus.FULFILLED);
        assertThat(order.getShipmentDate()).isEqualTo("2026-09-02");
        assertThat(order.getTrackingNumber()).isNull();
        assertThat(OrderTrackingStatusResolver.resolve(order)).isEqualTo("shipped");
        assertThat(result).containsEntry("eventId", "ship-way2026-09-02T11:00:00+0530");
        verifySavedEvent("ship-way2026-09-02T11:00:00+0530", "so-way", null, "ship-way", "Shipped");
    }

    @Test
    void handle_livePayload_miniMsDeliveryStatusDeliveredMarksFulfilledWhileStatusStaysShipped() {
        String body =
            "{\"eventId\":\"ship-del2026-09-02T12:00:00+0530\",\"shipment_order\":{" +
            "\"shipment_order_id\":\"ship-del\",\"salesorder_id\":\"so-del\",\"salesorder_number\":\"SO-00073\"," +
            "\"tracking_number\":\"AWB-73\",\"reference_number\":\"SO-00073\",\"carrier\":\"DTDC\",\"delivery_method\":\"DTDC\"," +
            "\"shipment_date\":\"2026-09-02\",\"status\":\"Shipped\",\"detailed_status\":\"\",\"status_message\":\"\"," +
            "\"delivery_status\":\"delivered\",\"cf_minims_delivery_status\":\"delivered\",\"last_modified_time\":\"2026-09-02T12:00:00+0530\"}}";
        CustomerOrder order = order("MNM-DEL", "so-del", OrderStatus.HAND_WRAPPED);
        when(webhookEventRepository.existsByEventId("ship-del2026-09-02T12:00:00+0530")).thenReturn(false);
        when(customerOrderRepository.findOneByZohoSalesOrderId("so-del")).thenReturn(Optional.of(order));
        when(customerOrderRepository.save(order)).thenReturn(order);

        Map<String, Object> result = handler.handle(body, null, SECRET);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.FULFILLED);
        assertThat(order.getTrackingNumber()).isEqualTo("AWB-73");
        assertThat(OrderTrackingStatusResolver.resolve(order)).isEqualTo("delivered");
        assertThat(result).containsEntry("status", "FULFILLED");
        verifySavedEvent("ship-del2026-09-02T12:00:00+0530", "so-del", null, "ship-del", "Shipped");
    }

    @Test
    void handle_livePayload_nativeDeliveredStatusMarksFulfilledWithoutCustomField() {
        String body =
            "{\"eventId\":\"ship-natdel2026-09-02T14:00:00+0530\",\"shipment_order\":{" +
            "\"shipment_order_id\":\"ship-natdel\",\"salesorder_id\":\"so-natdel\",\"salesorder_number\":\"SO-00075\"," +
            "\"tracking_number\":\"AWB-75\",\"reference_number\":\"SO-00075\",\"carrier\":\"DTDC\",\"delivery_method\":\"DTDC\"," +
            "\"shipment_date\":\"2026-09-02\",\"status\":\"Delivered\",\"detailed_status\":\"\",\"status_message\":\"\"," +
            "\"delivery_status\":\"\",\"cf_minims_delivery_status\":\"\",\"last_modified_time\":\"2026-09-02T14:00:00+0530\"}}";
        CustomerOrder order = order("MNM-NATDEL", "so-natdel", OrderStatus.HAND_WRAPPED);
        when(webhookEventRepository.existsByEventId("ship-natdel2026-09-02T14:00:00+0530")).thenReturn(false);
        when(customerOrderRepository.findOneByZohoSalesOrderId("so-natdel")).thenReturn(Optional.of(order));
        when(customerOrderRepository.save(order)).thenReturn(order);

        Map<String, Object> result = handler.handle(body, null, SECRET);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.FULFILLED);
        assertThat(order.getTrackingNumber()).isEqualTo("AWB-75");
        assertThat(OrderTrackingStatusResolver.resolve(order)).isEqualTo("delivered");
        assertThat(result).containsEntry("status", "FULFILLED");
    }

    @Test
    void handle_livePayload_nativeFulfilledShipmentOrderMarksFulfilledWithoutCustomField() {
        String body =
            "{\"eventId\":\"ship-natful2026-09-02T15:00:00+0530\",\"shipment_order\":{" +
            "\"shipment_order_id\":\"ship-natful\",\"salesorder_id\":\"so-natful\",\"salesorder_number\":\"SO-00076\"," +
            "\"tracking_number\":\"\",\"reference_number\":\"SO-00076\",\"carrier\":\"\",\"delivery_method\":\"\"," +
            "\"shipment_date\":\"2026-09-02\",\"status\":\"Fulfilled\",\"detailed_status\":\"\",\"status_message\":\"\"," +
            "\"delivery_status\":\"\",\"cf_minims_delivery_status\":\"\",\"last_modified_time\":\"2026-09-02T15:00:00+0530\"}}";
        CustomerOrder order = order("MNM-NATFUL", "so-natful", OrderStatus.HAND_WRAPPED);
        when(webhookEventRepository.existsByEventId("ship-natful2026-09-02T15:00:00+0530")).thenReturn(false);
        when(customerOrderRepository.findOneByZohoSalesOrderId("so-natful")).thenReturn(Optional.of(order));
        when(customerOrderRepository.save(order)).thenReturn(order);

        handler.handle(body, null, SECRET);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.FULFILLED);
        assertThat(order.getTrackingNumber()).isNull();
        assertThat(OrderTrackingStatusResolver.resolve(order)).isEqualTo("delivered");
    }

    @Test
    void handle_livePayload_onTheWayWithoutStatusStaysShippedNotDelivered() {
        String body =
            "{\"shipment_order\":{\"salesorder_id\":\"so-blank-status\",\"salesorder_number\":\"SO-00088\"," +
            "\"carrier\":\"XpressBees\",\"delivery_method\":\"XpressBees\",\"shipment_date\":\"02/09/2026\"}}";
        CustomerOrder order = order("MNM-BLANK-STATUS", "so-blank-status", OrderStatus.HAND_WRAPPED);
        String eventId = "zoho-shipment:so-blank-status:unknown:02/09/2026";
        when(webhookEventRepository.existsByEventId(eventId)).thenReturn(false);
        when(customerOrderRepository.findOneByZohoSalesOrderId("so-blank-status")).thenReturn(Optional.of(order));
        when(customerOrderRepository.save(order)).thenReturn(order);

        handler.handle(body, null, SECRET);

        assertThat(order.getStatus()).isNotEqualTo(OrderStatus.FULFILLED);
        assertThat(order.getShipmentDate()).isEqualTo("02/09/2026");
        assertThat(order.getTrackingCarrier()).isEqualTo("XpressBees");
        assertThat(OrderTrackingStatusResolver.resolve(order)).isEqualTo("shipped");
        verifySavedEvent(eventId, "so-blank-status", null, null, null);
    }

    @Test
    void handle_blankWebhookStatus_usesZohoPackageDeliveredStatus() {
        String body =
            "{\"eventId\":\"\",\"shipment_order\":{\"shipment_order_id\":\"\",\"salesorder_id\":\"so-live-so89\"," +
            "\"salesorder_number\":\"SO-00089\",\"carrier\":\"XpressBees\",\"delivery_method\":\"\"," +
            "\"shipment_date\":\"02/09/2026\",\"status\":\"\",\"last_modified_time\":\"\"}}";
        CustomerOrder order = order("MNM-41D247A7CCC8", "so-live-so89", OrderStatus.HAND_WRAPPED);
        order.setShipmentDate("02/09/2026");
        String eventId = "zoho-shipment:ship-89:delivered:02/09/2026";
        when(webhookEventRepository.existsByEventId(eventId)).thenReturn(false);
        when(customerOrderRepository.findOneByZohoSalesOrderId("so-live-so89")).thenReturn(Optional.of(order));
        when(customerOrderRepository.save(order)).thenReturn(order);
        when(booksClient.getSalesOrder("so-live-so89"))
            .thenReturn(
                Map.of(
                    "salesorder",
                    Map.of(
                        "salesorder_id",
                        "so-live-so89",
                        "status",
                        "invoiced",
                        "shipped_status",
                        "fulfilled",
                        "packages",
                        java.util.List.of(
                            Map.of(
                                "package_id",
                                "pkg-89",
                                "status",
                                "delivered",
                                "shipment_status",
                                "delivered",
                                "shipment_id",
                                "ship-89",
                                "carrier",
                                "XpressBees",
                                "shipment_order",
                                Map.of("shipment_delivered_date", "2026-09-02 18:00", "shipment_id", "ship-89")
                            )
                        )
                    )
                )
            );

        Map<String, Object> result = handler.handle(body, null, SECRET);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.FULFILLED);
        assertThat(OrderTrackingStatusResolver.resolve(order)).isEqualTo("delivered");
        assertThat(result).containsEntry("status", "FULFILLED");
        verifySavedEvent(eventId, "so-live-so89", "pkg-89", "ship-89", "delivered");
    }

    @Test
    void handle_blankWebhookStatus_usesZohoPackageShippedStatusWithoutDelivering() {
        String body =
            "{\"shipment_order\":{\"salesorder_id\":\"so-way-api\",\"salesorder_number\":\"SO-00090\"," +
            "\"carrier\":\"XpressBees\",\"shipment_date\":\"02/09/2026\",\"status\":\"\"}}";
        CustomerOrder order = order("MNM-WAY-API", "so-way-api", OrderStatus.CONFIRMED);
        when(webhookEventRepository.existsByEventId("zoho-shipment:ship-way:shipped:02/09/2026")).thenReturn(false);
        when(customerOrderRepository.findOneByZohoSalesOrderId("so-way-api")).thenReturn(Optional.of(order));
        when(customerOrderRepository.save(order)).thenReturn(order);
        when(booksClient.getSalesOrder("so-way-api"))
            .thenReturn(
                Map.of(
                    "salesorder",
                    Map.of(
                        "packages",
                        java.util.List.of(Map.of("status", "shipped", "shipment_status", "shipped", "shipment_id", "ship-way"))
                    )
                )
            );

        handler.handle(body, null, SECRET);

        assertThat(order.getStatus()).isNotEqualTo(OrderStatus.FULFILLED);
        assertThat(OrderTrackingStatusResolver.resolve(order)).isEqualTo("shipped");
    }

    @Test
    void handle_unresolvedPlaceholders_areIgnoredAndDoNotInventTracking() {
        String body =
            "{\"eventId\":\"ship-ph2026-09-02T13:00:00+0530\",\"shipment_order\":{" +
            "\"shipment_order_id\":\"ship-ph\",\"salesorder_id\":\"so-ph\",\"salesorder_number\":\"SO-00074\"," +
            "\"tracking_number\":\"${SHIPMENT_ORDER.TRACKING_NUMBER}\",\"reference_number\":\"${SHIPMENT_ORDER.REFERENCE_NUMBER}\"," +
            "\"carrier\":\"${SHIPMENT_ORDER.CARRIER}\",\"delivery_method\":\"${SHIPMENT_ORDER.DELIVERY_METHOD}\"," +
            "\"shipment_date\":\"${SHIPMENT_ORDER.DATE}\",\"status\":\"${SHIPMENT_ORDER.STATUS}\"," +
            "\"detailed_status\":\"${SHIPMENT_ORDER.DETAILED_STATUS}\",\"status_message\":\"${SHIPMENT_ORDER.STATUS_MESSAGE}\"," +
            "\"delivery_status\":\"${SHIPMENT_ORDER.CF_MINIMS_DELIVERY_STATUS}\"," +
            "\"cf_minims_delivery_status\":\"${SHIPMENT_ORDER.CF_MINIMS_DELIVERY_STATUS}\"," +
            "\"last_modified_time\":\"2026-09-02T13:00:00+0530\"}}";
        CustomerOrder order = order("MNM-PH", "so-ph", OrderStatus.CONFIRMED);
        when(webhookEventRepository.existsByEventId("ship-ph2026-09-02T13:00:00+0530")).thenReturn(false);
        when(customerOrderRepository.findOneByZohoSalesOrderId("so-ph")).thenReturn(Optional.of(order));
        when(customerOrderRepository.save(order)).thenReturn(order);

        handler.handle(body, null, SECRET);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.HAND_WRAPPED);
        assertThat(order.getShipmentDate()).isNull();
        assertThat(order.getTrackingNumber()).isNull();
        assertThat(order.getTrackingCarrier()).isNull();
        assertThat(OrderTrackingStatusResolver.resolve(order)).isEqualTo("hand_wrapped");
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
}
