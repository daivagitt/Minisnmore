package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.config.ApplicationProperties;
import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.ZohoInvoiceWebhookEvent;
import com.miniandmore.app.domain.enumeration.OrderStatus;
import com.miniandmore.app.domain.enumeration.PaymentStatus;
import com.miniandmore.app.domain.enumeration.ZohoSyncStatus;
import com.miniandmore.app.repository.CustomerOrderRepository;
import com.miniandmore.app.repository.ZohoInvoiceWebhookEventRepository;
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
class ZohoInvoiceWebhookHandlerServiceTest {

    private static final String SECRET = "zohoinvoicesecret";

    @Mock
    private ZohoInvoiceWebhookEventRepository webhookEventRepository;

    @Mock
    private CustomerOrderRepository customerOrderRepository;

    @Mock
    private OrderEventService orderEventService;

    @Mock
    private ZohoSyncService zohoSyncService;

    private ZohoInvoiceWebhookHandlerService handler;

    @BeforeEach
    void setUp() {
        ApplicationProperties properties = new ApplicationProperties();
        properties.getZoho().setInvoiceWebhookSecret(SECRET);
        handler = new ZohoInvoiceWebhookHandlerService(
            properties,
            webhookEventRepository,
            customerOrderRepository,
            orderEventService,
            zohoSyncService,
            new ObjectMapper()
        );
        lenient().when(customerOrderRepository.findOneByZohoInvoiceId(any())).thenReturn(Optional.empty());
        lenient().when(customerOrderRepository.findOneByZohoSalesOrderId(any())).thenReturn(Optional.empty());
        lenient().when(customerOrderRepository.findOneByOrderNumber(any())).thenReturn(Optional.empty());
    }

    @Test
    void handle_invoiceSent_linksInvoiceAndClearsSyncError() {
        String body =
            "{\"eventId\":\"evt-inv-1\",\"invoice\":{\"invoice_id\":\"inv-1\",\"salesorder_id\":\"so-1\",\"status\":\"sent\"}}";
        CustomerOrder order = order("MNM-INV-1", "so-1", ZohoSyncStatus.FAILED, "36026");
        when(webhookEventRepository.existsByEventId("evt-inv-1")).thenReturn(false);
        when(customerOrderRepository.findOneByZohoSalesOrderId("so-1")).thenReturn(Optional.of(order));
        when(customerOrderRepository.save(order)).thenReturn(order);

        Map<String, Object> result = handler.handle(body, null, SECRET);

        assertThat(order.getZohoInvoiceId()).isEqualTo("inv-1");
        assertThat(order.getZohoSyncStatus()).isEqualTo(ZohoSyncStatus.SYNCED);
        assertThat(order.getZohoSyncError()).isNull();
        assertThat(result).containsEntry("orderNumber", "MNM-INV-1").containsEntry("status", "sent");
        verify(zohoSyncService).ensureInvoicePdfCached(501L);
        verifySavedEvent("evt-inv-1", "inv-1", "so-1", "sent");
    }

    @Test
    void handle_duplicateEventId_isNoOp() {
        String body = "{\"eventId\":\"evt-inv-dup\",\"invoice\":{\"invoice_id\":\"inv-dup\",\"salesorder_id\":\"so-dup\",\"status\":\"sent\"}}";
        when(webhookEventRepository.existsByEventId("evt-inv-dup")).thenReturn(true);

        Map<String, Object> result = handler.handle(body, null, SECRET);

        assertThat(result).containsEntry("duplicate", true);
        verify(customerOrderRepository, never()).save(any());
        verify(webhookEventRepository, never()).saveAndFlush(any());
    }

    @Test
    void handle_unknownSalesOrderWithStoreReference_matchesExistingOrder() {
        String body =
            "{\"eventId\":\"evt-inv-race\",\"invoice\":{\"invoice_id\":\"inv-race\",\"salesorder_id\":\"so-not-stored\",\"reference_number\":\"MNM-INV-1\",\"status\":\"sent\"}}";
        CustomerOrder order = order("MNM-INV-1", null, ZohoSyncStatus.PENDING, null);
        when(webhookEventRepository.existsByEventId("evt-inv-race")).thenReturn(false);
        when(customerOrderRepository.findOneByOrderNumber("MNM-INV-1")).thenReturn(Optional.of(order));
        when(customerOrderRepository.save(order)).thenReturn(order);

        Map<String, Object> result = handler.handle(body, null, SECRET);

        assertThat(result).containsEntry("orderNumber", "MNM-INV-1").containsEntry("status", "sent");
        assertThat(order.getZohoInvoiceId()).isEqualTo("inv-race");
        verify(customerOrderRepository).save(order);
    }

    @Test
    void handle_knownInvoiceId_updatesOrderWithoutSalesOrderId() {
        String body = "{\"eventId\":\"evt-inv-id\",\"invoice\":{\"invoice_id\":\"inv-1\",\"status\":\"void\"}}";
        CustomerOrder order = order("MNM-INV-1", "so-1", ZohoSyncStatus.SYNCED, null);
        order.setZohoInvoiceId("inv-1");
        when(webhookEventRepository.existsByEventId("evt-inv-id")).thenReturn(false);
        when(customerOrderRepository.findOneByZohoInvoiceId("inv-1")).thenReturn(Optional.of(order));
        when(customerOrderRepository.save(order)).thenReturn(order);

        Map<String, Object> result = handler.handle(body, null, SECRET);

        assertThat(result).containsEntry("orderNumber", "MNM-INV-1").containsEntry("status", "void");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(customerOrderRepository, never()).findOneByZohoSalesOrderId(any());
    }

    @Test
    void handle_unknownOfflineSalesOrder_skipsWithoutUpdating() {
        String body =
            "{\"eventId\":\"evt-inv-offline\",\"invoice\":{\"invoice_id\":\"inv-offline\",\"salesorder_id\":\"so-offline\",\"reference_number\":\"MnM/26-27/0044\",\"status\":\"void\"}}";
        when(webhookEventRepository.existsByEventId("evt-inv-offline")).thenReturn(false);
        when(customerOrderRepository.findOneByZohoSalesOrderId("so-offline")).thenReturn(Optional.empty());

        Map<String, Object> result = handler.handle(body, null, SECRET);

        assertThat(result)
            .containsEntry("ok", true)
            .containsEntry("skipped", true)
            .containsEntry("reason", "no matching store order")
            .containsEntry("eventId", "evt-inv-offline");
        verify(customerOrderRepository, never()).save(any());
        verify(webhookEventRepository, never()).saveAndFlush(any());
        verify(zohoSyncService, never()).ensureInvoicePdfCached(any());
    }

    @Test
    void handle_unknownSalesOrderWithoutStoreReference_skips() {
        String body =
            "{\"eventId\":\"evt-inv-so-only\",\"invoice\":{\"invoice_id\":\"inv-so-only\",\"salesorder_id\":\"so-offline\",\"status\":\"sent\"}}";
        when(webhookEventRepository.existsByEventId("evt-inv-so-only")).thenReturn(false);
        when(customerOrderRepository.findOneByZohoSalesOrderId("so-offline")).thenReturn(Optional.empty());

        Map<String, Object> result = handler.handle(body, null, SECRET);

        assertThat(result).containsEntry("skipped", true).containsEntry("eventId", "evt-inv-so-only");
        verify(customerOrderRepository, never()).findOneByOrderNumber(any());
        verify(webhookEventRepository, never()).saveAndFlush(any());
    }

    @Test
    void handle_invoiceIdOnly_skips() {
        String body = "{\"eventId\":\"evt-inv-only\",\"invoice\":{\"invoice_id\":\"inv-only\",\"status\":\"void\"}}";
        when(webhookEventRepository.existsByEventId("evt-inv-only")).thenReturn(false);

        Map<String, Object> result = handler.handle(body, null, SECRET);

        assertThat(result).containsEntry("skipped", true).containsEntry("eventId", "evt-inv-only");
        verify(customerOrderRepository, never()).findOneByZohoSalesOrderId(any());
        verify(customerOrderRepository, never()).save(any());
    }

    @Test
    void handle_unknownStoreOrderNumber_returnsNotFound() {
        String body =
            "{\"eventId\":\"evt-inv-missing\",\"invoice\":{\"invoice_id\":\"inv-missing\",\"salesorder_id\":\"so-missing\",\"reference_number\":\"MNM-MISSING\",\"status\":\"sent\"}}";
        when(webhookEventRepository.existsByEventId("evt-inv-missing")).thenReturn(false);
        when(customerOrderRepository.findOneByZohoSalesOrderId("so-missing")).thenReturn(Optional.empty());
        when(customerOrderRepository.findOneByOrderNumber("MNM-MISSING")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(body, null, SECRET))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("No order for MNM-MISSING");
        verify(webhookEventRepository, never()).saveAndFlush(any());
    }

    @Test
    void handle_unknownStoreOrderNumberWithoutSalesOrderId_returnsNotFound() {
        String body =
            "{\"eventId\":\"evt-inv-ref-only\",\"invoice\":{\"invoice_id\":\"inv-ref\",\"reference_number\":\"MNM-REF-ONLY\",\"status\":\"paid\"}}";
        when(webhookEventRepository.existsByEventId("evt-inv-ref-only")).thenReturn(false);
        when(customerOrderRepository.findOneByOrderNumber("MNM-REF-ONLY")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(body, null, SECRET))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("No order for MNM-REF-ONLY");
        verify(customerOrderRepository, never()).findOneByZohoSalesOrderId(any());
    }

    private static CustomerOrder order(String orderNumber, String zohoSalesOrderId, ZohoSyncStatus syncStatus, String syncError) {
        CustomerOrder order = new CustomerOrder()
            .id(501L)
            .orderNumber(orderNumber)
            .userLogin("buyer@example.com")
            .paymentStatus(PaymentStatus.PAID)
            .status(OrderStatus.CONFIRMED)
            .zohoSalesOrderId(zohoSalesOrderId)
            .zohoSyncStatus(syncStatus)
            .zohoSyncError(syncError);
        return order;
    }

    private void verifySavedEvent(String eventId, String invoiceId, String salesOrderId, String status) {
        ArgumentCaptor<ZohoInvoiceWebhookEvent> captor = ArgumentCaptor.forClass(ZohoInvoiceWebhookEvent.class);
        verify(webhookEventRepository).saveAndFlush(captor.capture());
        ZohoInvoiceWebhookEvent saved = captor.getValue();
        assertThat(saved.getEventId()).isEqualTo(eventId);
        assertThat(saved.getZohoInvoiceId()).isEqualTo(invoiceId);
        assertThat(saved.getZohoSalesOrderId()).isEqualTo(salesOrderId);
        assertThat(saved.getStatus()).isEqualTo(status);
    }
}
