package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.miniandmore.app.client.ZohoBooksClient;
import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.OrderItem;
import com.miniandmore.app.repository.CustomerOrderRepository;
import com.miniandmore.app.repository.OrderItemRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ZohoExchangeInvoiceServiceTest {

    @Mock
    private ZohoBooksClient booksClient;

    @Mock
    private CustomerOrderRepository customerOrderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private ZohoCreditNoteOrderSupport orderSupport;

    @InjectMocks
    private ZohoExchangeInvoiceService service;

    @Test
    void createReplacementInvoice_appliesOpenCreditNotesUpToInvoiceBalance() {
        CustomerOrder order = new CustomerOrder().id(10L).orderNumber("ORD-1").zohoInvoiceId("inv-orig");
        OrderItem item = new OrderItem().id(5L).productId("2").productName("Tee").price(new BigDecimal("10.00"));
        when(customerOrderRepository.findById(10L)).thenReturn(Optional.of(order));
        when(orderItemRepository.findById(5L)).thenReturn(Optional.of(item));
        when(booksClient.findInvoiceByReferenceNumber("EXINV-RET-1")).thenReturn(null);
        when(booksClient.getInvoice("inv-orig")).thenReturn(Map.of("invoice", Map.of("customer_id", "cust-1", "is_inclusive_tax", true)));
        when(orderSupport.resolveZohoItemId(any())).thenReturn(Optional.of("item-9"));
        when(booksClient.createInvoice(any())).thenReturn(
            Map.of("invoice", Map.of("invoice_id", "inv-new", "balance", new BigDecimal("12.00")))
        );
        when(booksClient.getCreditNote("cn-old")).thenReturn(Map.of("creditnote", Map.of("balance", new BigDecimal("10.00"), "status", "open")));
        when(booksClient.getCreditNote("cn-small")).thenReturn(Map.of("creditnote", Map.of("balance", new BigDecimal("5.00"), "status", "open")));

        Optional<String> invoiceId = service.createReplacementInvoice(
            10L,
            "RET-1",
            5L,
            "TEE-M",
            "M",
            "Blue",
            new BigDecimal("12.00"),
            1,
            List.of("cn-old", "cn-small")
        );

        assertThat(invoiceId).contains("inv-new");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> firstApply = ArgumentCaptor.forClass(Map.class);
        verify(booksClient).applyCreditNoteToInvoices(eq("cn-old"), firstApply.capture());
        assertThat(firstApply.getValue()).containsEntry("invoices", List.of(Map.of("invoice_id", "inv-new", "amount_applied", new BigDecimal("10.00"))));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> secondApply = ArgumentCaptor.forClass(Map.class);
        verify(booksClient).applyCreditNoteToInvoices(eq("cn-small"), secondApply.capture());
        assertThat(secondApply.getValue()).containsEntry(
            "invoices",
            List.of(Map.of("invoice_id", "inv-new", "amount_applied", new BigDecimal("2.00")))
        );
    }

    @Test
    void createReplacementInvoice_timeoutAfterCreate_reusesExistingByReference() {
        CustomerOrder order = new CustomerOrder().id(10L).orderNumber("ORD-1").zohoInvoiceId("inv-orig");
        OrderItem item = new OrderItem().id(5L).productId("2").productName("Tee").price(new BigDecimal("10.00"));
        when(customerOrderRepository.findById(10L)).thenReturn(Optional.of(order));
        when(orderItemRepository.findById(5L)).thenReturn(Optional.of(item));
        when(booksClient.findInvoiceByReferenceNumber("EXINV-RET-1"))
            .thenReturn(null)
            .thenReturn(Map.of("invoice_id", "inv-recovered", "reference_number", "EXINV-RET-1"));
        when(booksClient.getInvoice("inv-orig")).thenReturn(Map.of("invoice", Map.of("customer_id", "cust-1", "is_inclusive_tax", true)));
        when(orderSupport.resolveZohoItemId(any())).thenReturn(Optional.of("item-9"));
        when(booksClient.createInvoice(any())).thenThrow(new RuntimeException("connection timed out"));
        when(booksClient.getInvoice("inv-recovered")).thenReturn(
            Map.of("invoice", Map.of("invoice_id", "inv-recovered", "balance", new BigDecimal("12.00")))
        );

        Optional<String> invoiceId = service.createReplacementInvoice(
            10L,
            "RET-1",
            5L,
            "TEE-M",
            "M",
            "Blue",
            new BigDecimal("12.00"),
            1,
            List.of()
        );

        assertThat(invoiceId).contains("inv-recovered");
    }

    @Test
    void returnNumberFromInvoiceReference_stripsExinvPrefix() {
        assertThat(ZohoExchangeInvoiceService.returnNumberFromInvoiceReference("EXINV-RET-MNM-10AF633829D6-1749F0"))
            .isEqualTo("RET-MNM-10AF633829D6-1749F0");
        assertThat(ZohoExchangeInvoiceService.returnNumberFromInvoiceReference("exinv-RET-1")).isEqualTo("RET-1");
        assertThat(ZohoExchangeInvoiceService.returnNumberFromInvoiceReference("MNM-10AF633829D6")).isNull();
        assertThat(ZohoExchangeInvoiceService.returnNumberFromInvoiceReference("EXINV-")).isNull();
        assertThat(ZohoExchangeInvoiceService.returnNumberFromInvoiceReference(null)).isNull();
    }
}
