package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.Product;
import com.miniandmore.app.domain.enumeration.LedgerlySyncStatus;
import com.miniandmore.app.domain.enumeration.PaymentStatus;
import com.miniandmore.app.repository.CustomerOrderRepository;
import com.miniandmore.app.repository.LedgerlyWebhookEventRepository;
import com.miniandmore.app.repository.ProductRepository;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class LedgerlyWebhookHandlerServiceTest {

    private static final String SECRET = "webhook-secret";

    @Mock
    private LedgerlyCredentialsProvider credentialsProvider;

    @Mock
    private LedgerlyWebhookEventRepository webhookEventRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CustomerOrderRepository customerOrderRepository;

    @Mock
    private LedgerlyProductMergeService productMergeService;

    @Mock
    private LedgerlyCategoryResolver categoryResolver;

    private LedgerlyWebhookHandlerService handler;

    @BeforeEach
    void setUp() {
        when(credentialsProvider.webhookSecret()).thenReturn(SECRET);
        handler = new LedgerlyWebhookHandlerService(
            credentialsProvider,
            webhookEventRepository,
            productRepository,
            customerOrderRepository,
            new ObjectMapper(),
            productMergeService,
            categoryResolver
        );
    }

    @Test
    void handle_duplicateEvent_returnsOkWithoutReprocessing() throws Exception {
        String body = "{\"id\":\"evt-1\",\"event\":\"item.updated\",\"data\":{\"sku\":\"SKU-1\"}}";
        when(webhookEventRepository.existsByEventId("evt-1")).thenReturn(true);

        Map<String, Object> result = handler.handle(body, sign(body), "item.updated");

        assertThat(result).containsEntry("duplicate", true);
        verify(productRepository, never()).save(any());
    }

    @Test
    void handle_itemUpdated_mergesProduct() throws Exception {
        String body = "{\"id\":\"evt-2\",\"data\":{\"sku\":\"SKU-1\",\"price\":120}}";
        Product product = new Product().sku("SKU-1");
        when(webhookEventRepository.existsByEventId("evt-2")).thenReturn(false);
        when(productRepository.findOneBySku("SKU-1")).thenReturn(Optional.of(product));
        when(productRepository.save(product)).thenReturn(product);

        Map<String, Object> result = handler.handle(body, sign(body), "item.updated");

        assertThat(result).containsEntry("ok", true).containsEntry("event", "item.updated");
        verify(productMergeService).mergeLedgerlyItem(any(), any(), any(), any());
        verify(webhookEventRepository).save(any());
    }

    @Test
    void handle_invoicePaid_marksOrderSynced() throws Exception {
        String body = "{\"id\":\"evt-3\",\"data\":{\"externalRef\":\"MNM-100\",\"ledgerlyInvoiceId\":\"inv-100\"}}";
        CustomerOrder order = new CustomerOrder()
            .orderNumber("MNM-100")
            .paymentStatus(PaymentStatus.PENDING)
            .ledgerlySyncStatus(LedgerlySyncStatus.PENDING);
        when(webhookEventRepository.existsByEventId("evt-3")).thenReturn(false);
        when(customerOrderRepository.findOneByOrderNumber("MNM-100")).thenReturn(Optional.of(order));
        when(customerOrderRepository.save(order)).thenReturn(order);

        handler.handle(body, sign(body), "invoice.paid");

        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(order.getLedgerlyInvoiceId()).isEqualTo("inv-100");
        assertThat(order.getLedgerlySyncStatus()).isEqualTo(LedgerlySyncStatus.SYNCED);
        assertThat(order.getLedgerlyOrderStatus()).isEqualTo("Paid");
    }

    @Test
    void handle_invoicePaid_doesNotDowngradeDeliveredStatus() throws Exception {
        String body = "{\"id\":\"evt-6\",\"data\":{\"externalRef\":\"MNM-102\",\"ledgerlyInvoiceId\":\"inv-102\"}}";
        CustomerOrder order = new CustomerOrder()
            .orderNumber("MNM-102")
            .paymentStatus(PaymentStatus.PAID)
            .ledgerlyOrderStatus("delivered")
            .ledgerlySyncStatus(LedgerlySyncStatus.SYNCED);
        when(webhookEventRepository.existsByEventId("evt-6")).thenReturn(false);
        when(customerOrderRepository.findOneByOrderNumber("MNM-102")).thenReturn(Optional.of(order));
        when(customerOrderRepository.save(order)).thenReturn(order);

        handler.handle(body, sign(body), "invoice.paid");

        assertThat(order.getLedgerlyOrderStatus()).isEqualTo("delivered");
        assertThat(order.getLedgerlyInvoiceId()).isEqualTo("inv-102");
    }

    @Test
    void handle_orderDelivered_updatesTrackingFields() throws Exception {
        String body =
            "{\"id\":\"evt-5\",\"data\":{\"externalRef\":\"MNM-101\",\"status\":\"delivered\",\"trackingNumber\":\"AWB-9\",\"shipmentDate\":\"2026-07-13\"}}";
        CustomerOrder order = new CustomerOrder().orderNumber("MNM-101").paymentStatus(PaymentStatus.PAID);
        when(webhookEventRepository.existsByEventId("evt-5")).thenReturn(false);
        when(customerOrderRepository.findOneByOrderNumber("MNM-101")).thenReturn(Optional.of(order));
        when(customerOrderRepository.save(order)).thenReturn(order);

        handler.handle(body, sign(body), "order.delivered");

        assertThat(order.getLedgerlyOrderStatus()).isEqualTo("delivered");
        assertThat(order.getTrackingNumber()).isEqualTo("AWB-9");
        assertThat(order.getShipmentDate()).isEqualTo("2026-07-13");
    }

    @Test
    void handle_invalidSignature_throwsUnauthorized() {
        String body = "{\"id\":\"evt-4\",\"event\":\"item.updated\",\"data\":{}}";

        assertThatThrownBy(() -> handler.handle(body, "bad-signature", "item.updated"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Invalid webhook signature");
    }

    @Test
    void handle_missingEventId_throwsBadRequest() {
        String body = "{\"event\":\"item.updated\",\"data\":{}}";

        assertThatThrownBy(() -> handler.handle(body, sign(body), "item.updated"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Missing event id");
    }

    private static String sign(String payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }
}
