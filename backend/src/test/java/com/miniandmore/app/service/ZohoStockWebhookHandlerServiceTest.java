package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.config.ApplicationProperties;
import com.miniandmore.app.domain.ZohoStockWebhookEvent;
import com.miniandmore.app.repository.ZohoStockWebhookEventRepository;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ZohoStockWebhookHandlerServiceTest {

    private static final String SECRET = "zoho-stock-secret";

    @Mock
    private ZohoStockWebhookEventRepository webhookEventRepository;

    @Mock
    private ZohoStockReconcileService stockReconcileService;

    private ZohoStockWebhookHandlerService handler;

    @BeforeEach
    void setUp() {
        ApplicationProperties properties = new ApplicationProperties();
        properties.getZoho().setStockWebhookSecret(SECRET);
        handler = new ZohoStockWebhookHandlerService(
            properties,
            webhookEventRepository,
            stockReconcileService,
            new ObjectMapper()
        );
    }

    @Test
    void handle_duplicateEventId_isNoOp() {
        String body = "{\"eventId\":\"evt-1\",\"sku\":\"SKU-1\",\"stock\":8}";
        when(webhookEventRepository.existsByEventId("evt-1")).thenReturn(true);

        Map<String, Object> result = handler.handle(body, WebhookTestSupport.hmacSha256Hex(SECRET, body));

        assertThat(result).containsEntry("duplicate", true);
        verify(stockReconcileService, never()).reconcileStockFromWebhook(any(), any(), any(Integer.class));
        verify(webhookEventRepository, never()).saveAndFlush(any());
    }

    @Test
    void handle_validPayload_reconcilesAndRecordsEvent() {
        String body = "{\"eventId\":\"evt-2\",\"sku\":\"SKU-2\",\"stock\":15}";
        when(webhookEventRepository.existsByEventId("evt-2")).thenReturn(false);
        when(stockReconcileService.reconcileStockFromWebhook("SKU-2", null, 15)).thenReturn(Map.of("ok", true, "reconciledItems", 1));

        Map<String, Object> result = handler.handle(body, WebhookTestSupport.hmacSha256Hex(SECRET, body));

        assertThat(result).containsEntry("ok", true);
        ArgumentCaptor<ZohoStockWebhookEvent> captor = ArgumentCaptor.forClass(ZohoStockWebhookEvent.class);
        verify(webhookEventRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getEventId()).isEqualTo("evt-2");
        assertThat(captor.getValue().getSku()).isEqualTo("SKU-2");
        verify(webhookEventRepository, never()).delete(any());
    }

    @Test
    void handle_reconcileFailure_releasesClaimAndDoesNotLeaveEvent() {
        String body = "{\"eventId\":\"evt-5\",\"sku\":\"SKU-5\",\"stock\":3}";
        when(webhookEventRepository.existsByEventId("evt-5")).thenReturn(false);
        when(stockReconcileService.reconcileStockFromWebhook("SKU-5", null, 3)).thenReturn(Map.of("ok", false, "error", "Product not found"));

        assertThatThrownBy(() -> handler.handle(body, WebhookTestSupport.hmacSha256Hex(SECRET, body)))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
            .isEqualTo(org.springframework.http.HttpStatus.BAD_GATEWAY);

        verify(webhookEventRepository).saveAndFlush(any());
        verify(webhookEventRepository).delete(any());
    }

    @Test
    void handle_sha256PrefixedSignature_isAccepted() {
        String body = "{\"eventId\":\"evt-6\",\"sku\":\"SKU-6\",\"stock\":2}";
        when(webhookEventRepository.existsByEventId("evt-6")).thenReturn(false);
        when(stockReconcileService.reconcileStockFromWebhook("SKU-6", null, 2)).thenReturn(Map.of("ok", true));

        Map<String, Object> result = handler.handle(body, "sha256=" + WebhookTestSupport.hmacSha256Hex(SECRET, body));

        assertThat(result).containsEntry("ok", true);
    }

    @Test
    void handle_invalidSignature_throwsUnauthorized() {
        String body = "{\"eventId\":\"evt-3\",\"sku\":\"SKU-3\",\"stock\":1}";

        assertThatThrownBy(() -> handler.handle(body, "bad-signature"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Invalid stock webhook signature");
    }

    @Test
    void handle_missingStock_throwsBadRequest() {
        String body = "{\"eventId\":\"evt-4\",\"sku\":\"SKU-4\"}";

        assertThatThrownBy(() -> handler.handle(body, WebhookTestSupport.hmacSha256Hex(SECRET, body)))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Missing or invalid stock");
    }

    @Test
    void handle_negativeStock_throwsBadRequest() {
        String body = "{\"eventId\":\"evt-neg\",\"sku\":\"SKU-NEG\",\"stock\":-2}";

        assertThatThrownBy(() -> handler.handle(body, WebhookTestSupport.hmacSha256Hex(SECRET, body)))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Negative Zoho stock (-2)");

        verify(stockReconcileService, never()).reconcileStockFromWebhook(any(), any(), anyInt());
        verify(webhookEventRepository, never()).saveAndFlush(any());
    }

    @Test
    void handle_snakeCaseEventId_isAccepted() {
        String body = "{\"event_id\":\"evt-snake\",\"sku\":\"SKU-SNAKE\",\"stock\":4}";
        when(webhookEventRepository.existsByEventId("evt-snake")).thenReturn(false);
        when(stockReconcileService.reconcileStockFromWebhook("SKU-SNAKE", null, 4)).thenReturn(Map.of("ok", true));

        Map<String, Object> result = handler.handle(body, WebhookTestSupport.hmacSha256Hex(SECRET, body));

        assertThat(result).containsEntry("ok", true);
    }

    @Test
    void handle_missingEventId_throwsBadRequest() {
        String body = "{\"sku\":\"SKU-7\",\"stock\":1}";

        assertThatThrownBy(() -> handler.handle(body, WebhookTestSupport.hmacSha256Hex(SECRET, body)))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Missing eventId");
    }

    @Test
    void handle_missingSkuAndProductId_throwsBadRequest() {
        String body = "{\"eventId\":\"evt-8\",\"stock\":2}";

        assertThatThrownBy(() -> handler.handle(body, WebhookTestSupport.hmacSha256Hex(SECRET, body)))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Provide sku or productId");
    }

    @Test
    void handle_invalidProductId_throwsBadRequest() {
        String body = "{\"eventId\":\"evt-9\",\"productId\":\"not-a-number\",\"stock\":2}";

        assertThatThrownBy(() -> handler.handle(body, WebhookTestSupport.hmacSha256Hex(SECRET, body)))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Invalid productId");
    }

    @Test
    void handle_raceOnInsert_returnsDuplicateResult() {
        String body = "{\"eventId\":\"evt-race\",\"sku\":\"SKU-RACE\",\"stock\":1}";
        when(webhookEventRepository.existsByEventId("evt-race")).thenReturn(false);
        org.mockito.Mockito
            .doThrow(new org.springframework.dao.DataIntegrityViolationException("duplicate"))
            .when(webhookEventRepository)
            .saveAndFlush(any());

        Map<String, Object> result = handler.handle(body, WebhookTestSupport.hmacSha256Hex(SECRET, body));

        assertThat(result).containsEntry("duplicate", true);
        verify(stockReconcileService, never()).reconcileStockFromWebhook(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.nullable(Long.class), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void handle_reconcileByProductId_passesNumericId() {
        String body = "{\"eventId\":\"evt-pid\",\"product_id\":42,\"stock\":9}";
        when(webhookEventRepository.existsByEventId("evt-pid")).thenReturn(false);
        when(stockReconcileService.reconcileStockFromWebhook(null, 42L, 9)).thenReturn(Map.of("ok", true));

        handler.handle(body, WebhookTestSupport.hmacSha256Hex(SECRET, body));

        verify(stockReconcileService).reconcileStockFromWebhook(null, 42L, 9);
    }
}
