package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.client.ZohoBooksClient;
import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.OrderItem;
import com.miniandmore.app.repository.CustomerOrderRepository;
import com.miniandmore.app.repository.OrderItemRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.ResourceAccessException;

@ExtendWith(MockitoExtension.class)
class ZohoCreditNoteServiceTest {

    @Mock
    private ZohoBooksClient booksClient;

    @Mock
    private CustomerOrderRepository customerOrderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private ZohoBooksAccountResolver accountResolver;

    @Mock
    private ZohoCreditNoteOrderSupport orderSupport;

    @Mock
    private ZohoReturnCreditNotePersistence returnCreditNotePersistence;

    private ZohoCreditNoteService service;

    @BeforeEach
    void setUp() {
        service = new ZohoCreditNoteService(
            booksClient,
            new ObjectMapper(),
            customerOrderRepository,
            orderItemRepository,
            accountResolver,
            orderSupport,
            returnCreditNotePersistence
        );
    }

    @Test
    void createCreditNote_reusesExistingAndPersistsId() {
        CustomerOrder order = new CustomerOrder().id(10L).orderNumber("ORD-1");
        when(customerOrderRepository.findById(10L)).thenReturn(Optional.of(order));
        when(booksClient.findCreditNoteByReferenceNumber("RET-1")).thenReturn(
            Map.of("creditnote_id", "cn-existing", "reference_number", "RET-1", "status", "open")
        );

        Optional<String> creditNoteId = service.createCreditNote(10L, "RET-1", Map.of(1L, 1), false);

        assertThat(creditNoteId).contains("cn-existing");
        verify(booksClient, never()).createCreditNote(any());
        verify(returnCreditNotePersistence, never()).persistIfAbsent(any(), any());
    }

    @Test
    void createCreditNote_persistsIdAfterSuccessfulCreate() {
        stubCreatePath();
        when(booksClient.findCreditNoteByReferenceNumber("RET-1")).thenReturn(null);
        when(booksClient.createCreditNote(any())).thenReturn(Map.of("creditnote", Map.of("creditnote_id", "cn-new")));
        when(accountResolver.isStockAdjustmentEnabled()).thenReturn(false);

        Optional<String> creditNoteId = service.createCreditNote(10L, "RET-1", Map.of(1L, 1), false);

        assertThat(creditNoteId).contains("cn-new");
        verify(returnCreditNotePersistence, never()).persistIfAbsent(any(), any());
    }

    @Test
    void createCreditNote_timeoutRecoversExistingAndPersists() {
        stubCreatePath();
        when(booksClient.findCreditNoteByReferenceNumber("RET-1"))
            .thenReturn(null)
            .thenReturn(Map.of("creditnote_id", "cn-recovered", "reference_number", "RET-1", "status", "open"));
        when(booksClient.createCreditNote(any())).thenThrow(new ResourceAccessException("timed out"));

        Optional<String> creditNoteId = service.createCreditNote(10L, "RET-1", Map.of(1L, 1), false);

        assertThat(creditNoteId).contains("cn-recovered");
        verify(returnCreditNotePersistence, never()).persistIfAbsent(any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void createCreditNote_returnOmitsInvoiceShippingCharge() {
        stubCreatePath(BigDecimal.valueOf(99), 80);
        when(booksClient.findCreditNoteByReferenceNumber("RET-1")).thenReturn(null);
        when(booksClient.createCreditNote(any())).thenReturn(Map.of("creditnote", Map.of("creditnote_id", "cn-ret")));
        when(accountResolver.isStockAdjustmentEnabled()).thenReturn(false);

        service.createCreditNote(10L, "RET-1", Map.of(1L, 1));

        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(booksClient).createCreditNote(payloadCaptor.capture());
        Map<String, Object> payload = payloadCaptor.getValue();
        assertThat(new BigDecimal(String.valueOf(payload.get("shipping_charge")))).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(payload).doesNotContainKey("notes");
    }

    @Test
    @SuppressWarnings("unchecked")
    void applyCreditNoteToInvoice_appliesOpenBalance() {
        when(booksClient.getCreditNote("cn-1")).thenReturn(
            Map.of("creditnote", Map.of("creditnote_id", "cn-1", "status", "open", "balance", "120.00"))
        );

        service.applyCreditNoteToInvoice("cn-1", "inv-orig");

        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(booksClient).applyCreditNoteToInvoices(eq("cn-1"), payloadCaptor.capture());
        Map<String, Object> payload = payloadCaptor.getValue();
        List<Map<String, Object>> invoices = (List<Map<String, Object>>) payload.get("invoices");
        assertThat(invoices).hasSize(1);
        assertThat(invoices.get(0).get("invoice_id")).isEqualTo("inv-orig");
        assertThat(new BigDecimal(String.valueOf(invoices.get(0).get("amount_applied")))).isEqualByComparingTo("120.00");
    }

    @Test
    void applyCreditNoteToInvoice_skipsVoidedNote() {
        when(booksClient.getCreditNote("cn-void")).thenReturn(
            Map.of("creditnote", Map.of("creditnote_id", "cn-void", "status", "void", "balance", "50.00"))
        );

        service.applyCreditNoteToInvoice("cn-void", "inv-orig");

        verify(booksClient, never()).applyCreditNoteToInvoices(any(), any());
    }

    @Test
    void applyCreditNoteToInvoice_blankIds_isNoOp() {
        service.applyCreditNoteToInvoice(" ", null);

        verify(booksClient, never()).getCreditNote(any());
        verify(booksClient, never()).applyCreditNoteToInvoices(any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void attachCreditNoteToSourceInvoice_appliesWhenInvoiceHasOutstanding() {
        CustomerOrder order = new CustomerOrder().id(10L).orderNumber("ORD-1").zohoInvoiceId("inv-1");
        when(customerOrderRepository.findById(10L)).thenReturn(Optional.of(order));
        when(booksClient.getCreditNote("cn-open")).thenReturn(
            Map.of("creditnote", Map.of("creditnote_id", "cn-open", "status", "open", "balance", "80.00"))
        );
        when(booksClient.getInvoice("inv-1")).thenReturn(Map.of("invoice", Map.of("balance", "100.00")));

        Optional<String> attached = service.attachCreditNoteToSourceInvoice(10L, "RET-1", "cn-open", Map.of(1L, 1));

        assertThat(attached).contains("cn-open");
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(booksClient).applyCreditNoteToInvoices(eq("cn-open"), payloadCaptor.capture());
        List<Map<String, Object>> invoices = (List<Map<String, Object>>) payloadCaptor.getValue().get("invoices");
        assertThat(new BigDecimal(String.valueOf(invoices.get(0).get("amount_applied")))).isEqualByComparingTo("80.00");
        verify(booksClient, never()).voidCreditNote(any());
    }

    @Test
    void attachCreditNoteToSourceInvoice_afterVoidRecreateFails_returnsEmpty() {
        stubCreatePath();
        when(booksClient.getCreditNote("cn-unlinked")).thenReturn(
            Map.of("creditnote", Map.of("creditnote_id", "cn-unlinked", "status", "open", "balance", "800.00"))
        );
        when(booksClient.getInvoice("inv-1")).thenReturn(Map.of("invoice", Map.of("customer_id", "cust-1", "balance", "0.00", "line_items", List.of())));
        when(booksClient.findCreditNoteByReferenceNumber("RET-1")).thenReturn(null);
        when(booksClient.createCreditNote(any())).thenThrow(new RuntimeException("zoho down"));

        Optional<String> attached = service.attachCreditNoteToSourceInvoice(10L, "RET-1", "cn-unlinked", Map.of(1L, 1));

        assertThat(attached).isEmpty();
        verify(booksClient).voidCreditNote("cn-unlinked");
    }

    @Test
    void attachCreditNoteToSourceInvoice_replacesWhenInvoiceIsPaid() {
        stubCreatePath();
        when(booksClient.getCreditNote("cn-unlinked")).thenReturn(
            Map.of("creditnote", Map.of("creditnote_id", "cn-unlinked", "status", "open", "balance", "800.00"))
        );
        when(booksClient.getInvoice("inv-1")).thenReturn(Map.of("invoice", Map.of("customer_id", "cust-1", "balance", "0.00", "line_items", List.of())));
        when(booksClient.findCreditNoteByReferenceNumber("RET-1")).thenReturn(null);
        when(booksClient.createCreditNote(any())).thenReturn(Map.of("creditnote", Map.of("creditnote_id", "cn-linked")));
        when(accountResolver.isStockAdjustmentEnabled()).thenReturn(false);

        Optional<String> attached = service.attachCreditNoteToSourceInvoice(10L, "RET-1", "cn-unlinked", Map.of(1L, 1));

        assertThat(attached).contains("cn-linked");
        verify(booksClient).voidCreditNote("cn-unlinked");
        verify(booksClient).createCreditNote(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void refundOpenCreditNoteBalance_refundsLeftoverThroughBankAccount() {
        when(booksClient.getCreditNote("cn-1")).thenReturn(
            Map.of("creditnote", Map.of("creditnote_id", "cn-1", "status", "open", "balance", "300.00"))
        );
        when(booksClient.listBankAccounts()).thenReturn(List.of(Map.of("account_id", "bank-1")));

        service.refundOpenCreditNoteBalance("cn-1", "rfnd_diff_1");

        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(booksClient).refundCreditNote(eq("cn-1"), payloadCaptor.capture());
        Map<String, Object> payload = payloadCaptor.getValue();
        assertThat(new BigDecimal(String.valueOf(payload.get("amount")))).isEqualByComparingTo("300.00");
        assertThat(payload.get("from_account_id")).isEqualTo("bank-1");
        assertThat(payload.get("reference_number")).isEqualTo("rfnd_diff_1");
    }

    @Test
    void refundOpenCreditNoteBalance_skipsWhenBalanceIsZero() {
        when(booksClient.getCreditNote("cn-1")).thenReturn(
            Map.of("creditnote", Map.of("creditnote_id", "cn-1", "status", "open", "balance", "0.00"))
        );

        service.refundOpenCreditNoteBalance("cn-1", "RET-1");

        verify(booksClient, never()).refundCreditNote(any(), any());
    }

    private void stubCreatePath() {
        stubCreatePath(null, null);
    }

    private void stubCreatePath(BigDecimal orderShipping, Integer invoiceShippingCharge) {
        CustomerOrder order = new CustomerOrder().id(10L).orderNumber("ORD-1").zohoInvoiceId("inv-1");
        if (orderShipping != null) {
            order.shipping(orderShipping);
        }
        OrderItem item = new OrderItem().id(1L).productName("Tee").qty(1).price(BigDecimal.TEN);
        when(customerOrderRepository.findById(10L)).thenReturn(Optional.of(order));
        when(orderItemRepository.findAllByOrderId(10L)).thenReturn(List.of(item));
        when(orderSupport.resolveZohoItemId(item)).thenReturn(Optional.of("item-1"));
        Map<String, Object> invoice = new java.util.LinkedHashMap<>();
        invoice.put("customer_id", "cust-1");
        invoice.put("line_items", List.of());
        if (invoiceShippingCharge != null) {
            invoice.put("shipping_charge", invoiceShippingCharge);
        }
        when(booksClient.getInvoice("inv-1")).thenReturn(Map.of("invoice", invoice));
    }
}
