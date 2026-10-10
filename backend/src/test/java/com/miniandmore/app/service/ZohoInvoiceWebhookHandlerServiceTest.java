package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.config.ApplicationProperties;
import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.OrderReturn;
import com.miniandmore.app.domain.ZohoInvoiceWebhookEvent;
import com.miniandmore.app.domain.enumeration.ExchangeSettlementStatus;
import com.miniandmore.app.domain.enumeration.OrderRequestType;
import com.miniandmore.app.domain.enumeration.OrderStatus;
import com.miniandmore.app.domain.enumeration.PaymentStatus;
import com.miniandmore.app.domain.enumeration.ZohoSyncStatus;
import com.miniandmore.app.repository.CustomerOrderRepository;
import com.miniandmore.app.repository.OrderReturnRepository;
import com.miniandmore.app.repository.ZohoInvoiceWebhookEventRepository;
import java.math.BigDecimal;
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
    private OrderReturnRepository orderReturnRepository;

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
            orderReturnRepository,
            orderEventService,
            zohoSyncService,
            new ObjectMapper()
        );
        lenient().when(orderReturnRepository.findFirstByZohoReplacementInvoiceIdOrderByIdDesc(any())).thenReturn(Optional.empty());
        lenient().when(orderReturnRepository.findOneByReturnNumber(any())).thenReturn(Optional.empty());
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
    void handle_unknownOfflineSalesOrder_skipsWithoutUpdating() {
        String body =
            "{\"eventId\":\"evt-inv-offline\",\"invoice\":{\"invoice_id\":\"inv-offline\",\"salesorder_id\":\"so-offline\",\"reference_number\":\"MnM/26-27/0044\",\"status\":\"void\"}}";
        when(webhookEventRepository.existsByEventId("evt-inv-offline")).thenReturn(false);

        Map<String, Object> result = handler.handle(body, null, SECRET);

        assertThat(result)
            .containsEntry("ok", true)
            .containsEntry("skipped", true)
            .containsEntry("reason", "no matching store order")
            .containsEntry("eventId", "evt-inv-offline");
        verify(customerOrderRepository, never()).save(any());
        verify(webhookEventRepository, never()).saveAndFlush(any());
        verify(zohoSyncService, never()).ensureInvoicePdfCached(any());
        verify(orderReturnRepository, never()).save(any());
    }

    @Test
    void handle_unknownSalesOrderWithoutStoreReference_skips() {
        String body =
            "{\"eventId\":\"evt-inv-so-only\",\"invoice\":{\"invoice_id\":\"inv-so-only\",\"salesorder_id\":\"so-offline\",\"status\":\"sent\"}}";
        when(webhookEventRepository.existsByEventId("evt-inv-so-only")).thenReturn(false);

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

        assertThatThrownBy(() -> handler.handle(body, null, SECRET))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("No order for MNM-REF-ONLY");
        verify(customerOrderRepository, never()).findOneByZohoSalesOrderId(any());
    }

    @Test
    void handle_exchangeInvoiceByReplacementId_acceptsWithoutTouchingOriginalInvoice() {
        String body =
            "{\"eventId\":\"evt-ex-paid\",\"invoice\":{\"invoice_id\":\"inv-ex-1\",\"invoice_number\":\"INV-000174\",\"reference_number\":\"EXINV-RET-MNM-10AF633829D6-1749F0\",\"status\":\"paid\"}}";
        CustomerOrder order = fulfilledOrder("MNM-10AF633829D6", "so-orig", "inv-orig");
        OrderReturn exchange = exchangeReturn("RET-MNM-10AF633829D6-1749F0", "inv-ex-1", order);
        when(webhookEventRepository.existsByEventId("evt-ex-paid")).thenReturn(false);
        when(orderReturnRepository.findFirstByZohoReplacementInvoiceIdOrderByIdDesc("inv-ex-1")).thenReturn(Optional.of(exchange));

        Map<String, Object> result = handler.handle(body, null, SECRET);

        assertThat(result)
            .containsEntry("ok", true)
            .containsEntry("orderNumber", "MNM-10AF633829D6")
            .containsEntry("status", "paid")
            .containsEntry("replacement", true);
        assertThat(order.getZohoInvoiceId()).isEqualTo("inv-orig");
        assertThat(order.getZohoOrderStatus()).isEqualTo("invoiced");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.FULFILLED);
        assertThat(exchange.getSettlementStatus()).isEqualTo(ExchangeSettlementStatus.PENDING);
        verify(orderReturnRepository, never()).save(any());
        verify(customerOrderRepository, never()).save(any());
        verify(zohoSyncService, never()).ensureInvoicePdfCached(any());
        verifySavedEvent("evt-ex-paid", "inv-ex-1", null, "paid");
    }

    @Test
    void handle_exchangeInvoiceByExinvReference_whenSalesOrderMissing() {
        String body =
            "{\"eventId\":\"evt-ex-overdue\",\"invoice\":{\"invoice_id\":\"inv-ex-2\",\"invoice_number\":\"INV-000170\",\"reference_number\":\"EXINV-RET-MNM-3E217E384EF5-608EB3\",\"status\":\"overdue\"}}";
        CustomerOrder order = fulfilledOrder("MNM-3E217E384EF5", "so-orig-2", "inv-orig-2");
        OrderReturn exchange = exchangeReturn("RET-MNM-3E217E384EF5-608EB3", "inv-ex-2", order);
        when(webhookEventRepository.existsByEventId("evt-ex-overdue")).thenReturn(false);
        when(orderReturnRepository.findOneByReturnNumber("RET-MNM-3E217E384EF5-608EB3")).thenReturn(Optional.of(exchange));

        Map<String, Object> result = handler.handle(body, null, SECRET);

        assertThat(result).containsEntry("orderNumber", "MNM-3E217E384EF5").containsEntry("status", "overdue").containsEntry("replacement", true);
        assertThat(exchange.getSettlementStatus()).isEqualTo(ExchangeSettlementStatus.PENDING);
        assertThat(order.getZohoInvoiceId()).isEqualTo("inv-orig-2");
        verify(orderReturnRepository, never()).save(any());
        verify(customerOrderRepository, never()).save(any());
    }

    @Test
    void handle_voidReplacementInvoice_doesNotCancelOriginalOrder() {
        String body =
            "{\"eventId\":\"evt-ex-void\",\"invoice\":{\"invoice_id\":\"inv-ex-3\",\"reference_number\":\"EXINV-RET-1\",\"status\":\"void\"}}";
        CustomerOrder order = order("MNM-HAND", "so-hand", ZohoSyncStatus.SYNCED, null);
        order.setStatus(OrderStatus.HAND_WRAPPED);
        order.setZohoInvoiceId("inv-orig-3");
        OrderReturn exchange = exchangeReturn("RET-1", "inv-ex-3", order);
        when(webhookEventRepository.existsByEventId("evt-ex-void")).thenReturn(false);
        when(orderReturnRepository.findFirstByZohoReplacementInvoiceIdOrderByIdDesc("inv-ex-3")).thenReturn(Optional.of(exchange));

        Map<String, Object> result = handler.handle(body, null, SECRET);

        assertThat(result).containsEntry("replacement", true);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.HAND_WRAPPED);
        assertThat(order.getZohoInvoiceId()).isEqualTo("inv-orig-3");
        verify(orderEventService, never()).syncLifecycleEvents(any());
    }

    @Test
    void handle_originalInvoiceByInvoiceId_whenSalesOrderMissing() {
        String body = "{\"eventId\":\"evt-by-inv\",\"invoice\":{\"invoice_id\":\"inv-known\",\"status\":\"paid\"}}";
        CustomerOrder order = order("MNM-BY-INV", "so-known", ZohoSyncStatus.SYNCED, null);
        order.setZohoInvoiceId("inv-known");
        when(webhookEventRepository.existsByEventId("evt-by-inv")).thenReturn(false);
        when(customerOrderRepository.findOneByZohoInvoiceId("inv-known")).thenReturn(Optional.of(order));
        when(customerOrderRepository.save(order)).thenReturn(order);

        Map<String, Object> result = handler.handle(body, null, SECRET);

        assertThat(result).containsEntry("orderNumber", "MNM-BY-INV").containsEntry("status", "paid");
        assertThat(result).doesNotContainKey("replacement");
        verify(zohoSyncService).ensureInvoicePdfCached(501L);
    }

    @Test
    void handle_salesordersArraySuppliesSalesOrderId() {
        String body =
            "{\"eventId\":\"evt-nested-so\",\"invoice\":{\"invoice_id\":\"inv-nested\",\"status\":\"sent\",\"salesorders\":[{\"salesorder_id\":\"so-nested\",\"reference_number\":\"MNM-NESTED\"}]}}";
        CustomerOrder order = order("MNM-NESTED", "so-nested", ZohoSyncStatus.SYNCED, null);
        when(webhookEventRepository.existsByEventId("evt-nested-so")).thenReturn(false);
        when(customerOrderRepository.findOneByZohoSalesOrderId("so-nested")).thenReturn(Optional.of(order));
        when(customerOrderRepository.save(order)).thenReturn(order);

        Map<String, Object> result = handler.handle(body, null, SECRET);

        assertThat(result).containsEntry("orderNumber", "MNM-NESTED");
        verifySavedEvent("evt-nested-so", "inv-nested", "so-nested", "sent");
    }

    @Test
    void handle_exinvReferenceWinsOverNestedOriginalSalesOrder() {
        String body =
            "{\"eventId\":\"evt-ex-nested\",\"invoice\":{\"invoice_id\":\"inv-ex-new\",\"invoice_number\":\"INV-000170\",\"reference_number\":\"EXINV-RET-MNM-3E217E384EF5-608EB3\",\"status\":\"paid\",\"salesorders\":[{\"salesorder_id\":\"so-orig-2\",\"reference_number\":\"MNM-3E217E384EF5\"}]}}";
        CustomerOrder order = fulfilledOrder("MNM-3E217E384EF5", "so-orig-2", "inv-orig-2");
        OrderReturn exchange = exchangeReturn("RET-MNM-3E217E384EF5-608EB3", null, order);
        when(webhookEventRepository.existsByEventId("evt-ex-nested")).thenReturn(false);
        when(orderReturnRepository.findOneByReturnNumber("RET-MNM-3E217E384EF5-608EB3")).thenReturn(Optional.of(exchange));
        when(orderReturnRepository.save(exchange)).thenReturn(exchange);

        Map<String, Object> result = handler.handle(body, null, SECRET);

        assertThat(result).containsEntry("replacement", true).containsEntry("orderNumber", "MNM-3E217E384EF5");
        assertThat(exchange.getZohoReplacementInvoiceId()).isEqualTo("inv-ex-new");
        assertThat(exchange.getSettlementStatus()).isEqualTo(ExchangeSettlementStatus.PENDING);
        assertThat(order.getZohoInvoiceId()).isEqualTo("inv-orig-2");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.FULFILLED);
        verify(customerOrderRepository, never()).findOneByZohoSalesOrderId(any());
        verify(customerOrderRepository, never()).save(any());
        verify(zohoSyncService, never()).ensureInvoicePdfCached(any());
    }

    private static CustomerOrder order(String orderNumber, String zohoSalesOrderId, ZohoSyncStatus syncStatus, String syncError) {
        return new CustomerOrder()
            .id(501L)
            .orderNumber(orderNumber)
            .userLogin("buyer@example.com")
            .paymentStatus(PaymentStatus.PAID)
            .status(OrderStatus.CONFIRMED)
            .zohoSalesOrderId(zohoSalesOrderId)
            .zohoSyncStatus(syncStatus)
            .zohoSyncError(syncError);
    }

    private static CustomerOrder fulfilledOrder(String orderNumber, String zohoSalesOrderId, String zohoInvoiceId) {
        CustomerOrder order = order(orderNumber, zohoSalesOrderId, ZohoSyncStatus.SYNCED, null);
        order.setStatus(OrderStatus.FULFILLED);
        order.setZohoInvoiceId(zohoInvoiceId);
        order.setZohoOrderStatus("invoiced");
        return order;
    }

    private static OrderReturn exchangeReturn(String returnNumber, String replacementInvoiceId, CustomerOrder order) {
        OrderReturn exchange = new OrderReturn();
        exchange.setReturnNumber(returnNumber);
        exchange.setRequestType(OrderRequestType.EXCHANGE);
        exchange.setZohoReplacementInvoiceId(replacementInvoiceId);
        exchange.setSettlementStatus(ExchangeSettlementStatus.PENDING);
        exchange.setPriceDifference(new BigDecimal("8.00"));
        exchange.setOrder(order);
        return exchange;
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
