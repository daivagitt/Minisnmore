package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
