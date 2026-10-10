package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
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
import org.springframework.dao.DataIntegrityViolationException;
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

    @Test
    void handle_missingEventId_returnsBadRequest() {
        String body = "{\"salesorder\":{\"reference_number\":\"MNM-1\",\"status\":\"open\"}}";

        assertThatThrownBy(() -> handler.handle(body, null, SECRET))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Missing eventId");
    }

    @Test
    void handle_missingSalesOrderIdentifiers_returnsBadRequest() {
        String body = "{\"eventId\":\"evt-no-ids\",\"salesorder\":{\"status\":\"open\"}}";

        assertThatThrownBy(() -> handler.handle(body, null, SECRET))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Provide salesorder_id");
    }

    @Test
    void handle_findsOrderByReferenceNumberWithoutZohoId() {
        String body =
            "{\"eventId\":\"evt-ref-only\",\"salesorder\":{\"reference_number\":\"MNM-REF-ONLY\",\"status\":\"invoiced\"}}";
        CustomerOrder order = order("MNM-REF-ONLY", null, OrderStatus.CONFIRMED);
        when(webhookEventRepository.existsByEventId("evt-ref-only")).thenReturn(false);
        when(customerOrderRepository.findOneByOrderNumber("MNM-REF-ONLY")).thenReturn(Optional.of(order));
        when(customerOrderRepository.save(order)).thenReturn(order);

        Map<String, Object> result = handler.handle(body, null, SECRET);

        assertThat(result).containsEntry("orderNumber", "MNM-REF-ONLY");
        verify(webhookEventRepository).saveAndFlush(any());
    }

    @Test
    void handle_nonStoreReferenceNumber_isNotFound() {
        String body = "{\"eventId\":\"evt-ext\",\"salesorder\":{\"reference_number\":\"EXT-999\",\"status\":\"open\"}}";

        assertThatThrownBy(() -> handler.handle(body, null, SECRET))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("No order for");
    }

    @Test
    void handle_nestedSalesOrderKey_isNormalized() {
        String body =
            "{\"eventId\":\"evt-nested\",\"sales_order\":{\"salesorder_id\":\"so-nested\",\"reference_number\":\"MNM-NEST\",\"status\":\"invoiced\"}}";
        CustomerOrder order = order("MNM-NEST", "so-nested", OrderStatus.CONFIRMED);
        when(webhookEventRepository.existsByEventId("evt-nested")).thenReturn(false);
        when(customerOrderRepository.findOneByZohoSalesOrderId("so-nested")).thenReturn(Optional.of(order));
        when(customerOrderRepository.save(order)).thenReturn(order);

        handler.handle(body, null, SECRET);

        assertThat(order.getZohoOrderStatus()).isEqualTo("invoiced");
    }

    @Test
    void handle_syntheticEventId_whenExplicitIdMissing() {
        String body =
            "{\"salesorder\":{\"salesorder_id\":\"so-synth\",\"reference_number\":\"MNM-SYNTH\",\"status\":\"shipped\",\"last_modified_time\":\"2026-01-01T00:00:00Z\"}}";
        CustomerOrder order = order("MNM-SYNTH", "so-synth", OrderStatus.CONFIRMED);
        when(webhookEventRepository.existsByEventId("zoho-salesorder:so-synth:shipped:2026-01-01T00:00:00Z")).thenReturn(false);
        when(customerOrderRepository.findOneByZohoSalesOrderId("so-synth")).thenReturn(Optional.of(order));
        when(customerOrderRepository.save(order)).thenReturn(order);

        Map<String, Object> result = handler.handle(body, null, SECRET);

        assertThat(result).containsEntry("ok", true);
    }

    @Test
    void handle_duplicateEventId_reappliesDeliveryWhenPending() {
        String body =
            "{\"eventId\":\"evt-dup-deliver\",\"salesorder\":{\"salesorder_id\":\"so-dup-del\",\"reference_number\":\"MNM-DUP-DEL\",\"status\":\"delivered\"}}";
        CustomerOrder order = order("MNM-DUP-DEL", "so-dup-del", OrderStatus.CONFIRMED);
        when(webhookEventRepository.existsByEventId("evt-dup-deliver")).thenReturn(true);
        when(customerOrderRepository.findOneByZohoSalesOrderId("so-dup-del")).thenReturn(Optional.of(order));
        when(customerOrderRepository.save(order)).thenReturn(order);

        Map<String, Object> result = handler.handle(body, null, SECRET);

        assertThat(result).containsEntry("ok", true);
        verify(webhookEventRepository, never()).saveAndFlush(any());
    }

    @Test
    void handle_raceOnEventInsert_returnsDuplicateWhenNotDeliveryReapply() {
        String body =
            "{\"eventId\":\"evt-race\",\"salesorder\":{\"salesorder_id\":\"so-race\",\"reference_number\":\"MNM-RACE\",\"status\":\"invoiced\"}}";
        CustomerOrder order = order("MNM-RACE", "so-race", OrderStatus.CONFIRMED);
        when(webhookEventRepository.existsByEventId("evt-race")).thenReturn(false);
        when(customerOrderRepository.findOneByZohoSalesOrderId("so-race")).thenReturn(Optional.of(order));
        doThrow(new DataIntegrityViolationException("duplicate")).when(webhookEventRepository).saveAndFlush(any());

        Map<String, Object> result = handler.handle(body, null, SECRET);

        assertThat(result).containsEntry("duplicate", true);
        verify(customerOrderRepository, never()).save(any());
    }

    @Test
    void handle_saveFailure_deletesInsertedEventRecord() {
        String body =
            "{\"eventId\":\"evt-fail\",\"salesorder\":{\"salesorder_id\":\"so-fail\",\"reference_number\":\"MNM-FAIL\",\"status\":\"invoiced\"}}";
        CustomerOrder order = order("MNM-FAIL", "so-fail", OrderStatus.CONFIRMED);
        when(webhookEventRepository.existsByEventId("evt-fail")).thenReturn(false);
        when(customerOrderRepository.findOneByZohoSalesOrderId("so-fail")).thenReturn(Optional.of(order));
        when(customerOrderRepository.save(order)).thenThrow(new IllegalStateException("db down"));

        assertThatThrownBy(() -> handler.handle(body, null, SECRET)).isInstanceOf(IllegalStateException.class);
        verify(webhookEventRepository).delete(any(ZohoOrderStatusWebhookEvent.class));
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
