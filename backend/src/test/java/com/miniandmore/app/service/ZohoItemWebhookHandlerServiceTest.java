package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.config.ApplicationProperties;
import com.miniandmore.app.domain.ZohoItemWebhookEvent;
import com.miniandmore.app.repository.ZohoItemWebhookEventRepository;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;
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
class ZohoItemWebhookHandlerServiceTest {

    private static final String SECRET = "zoho-item-secret";

    @Mock
    private ZohoItemWebhookEventRepository webhookEventRepository;

    @Mock
    private ZohoSyncService syncService;

    private ZohoItemWebhookHandlerService handler;

    @BeforeEach
    void setUp() {
        ApplicationProperties properties = new ApplicationProperties();
        properties.getZoho().setItemWebhookSecret(SECRET);
        handler = new ZohoItemWebhookHandlerService(properties, webhookEventRepository, syncService, new ObjectMapper());
    }

    @Test
    void handle_duplicateEventId_isNoOp() {
        String body = "{\"eventId\":\"evt-item-1\",\"item_id\":\"item-1\",\"sku\":\"SKU-1\"}";
        when(webhookEventRepository.existsByEventId("evt-item-1")).thenReturn(true);

        Map<String, Object> result = handler.handle(body, null, SECRET);

        assertThat(result).containsEntry("duplicate", true);
        verify(syncService, never()).pullExternalProduct(any(), any());
        verify(webhookEventRepository, never()).saveAndFlush(any());
    }

    @Test
    void handle_nestedItemPayload_pullsOneExternalProductAndRecordsEvent() {
        String body = "{\"event_id\":\"evt-item-2\",\"data\":{\"item\":{\"item_id\":\"item-2\",\"sku\":\"SKU-2\",\"status\":\"active\"}}}";
        when(webhookEventRepository.existsByEventId("evt-item-2")).thenReturn(false);
        when(syncService.pullExternalProduct("SKU-2", "item-2")).thenReturn(Map.of("success", 1, "failed", 0));

        Map<String, Object> result = handler.handle(body, null, SECRET);

        assertThat(result).containsEntry("ok", true).containsEntry("eventId", "evt-item-2");
        ArgumentCaptor<ZohoItemWebhookEvent> captor = ArgumentCaptor.forClass(ZohoItemWebhookEvent.class);
        verify(webhookEventRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getZohoItemId()).isEqualTo("item-2");
        assertThat(captor.getValue().getSku()).isEqualTo("SKU-2");
        assertThat(captor.getValue().getStatus()).isEqualTo("active");
    }

    @Test
    void handle_defaultPayload_derivesEventIdFromIdentityStatusAndTimestamp() {
        String body = "{\"item\":{\"item_id\":\"item-3\",\"sku\":\"SKU-3\",\"status\":\"inactive\",\"last_modified_time\":\"2026-08-31T12:00:00Z\"}}";
        String eventId = "zoho-item:item-3:inactive:2026-08-31T12:00:00Z";
        when(webhookEventRepository.existsByEventId(eventId)).thenReturn(false);
        when(syncService.pullExternalProduct("SKU-3", "item-3")).thenReturn(Map.of("success", 0, "failed", 0));

        Map<String, Object> result = handler.handle(body, null, SECRET);

        assertThat(result).containsEntry("eventId", eventId);
    }

    @Test
    void handle_hmacSignature_isAccepted() {
        String body = "{\"eventId\":\"evt-item-hmac\",\"sku\":\"SKU-H\"}";
        when(webhookEventRepository.existsByEventId("evt-item-hmac")).thenReturn(false);
        when(syncService.pullExternalProduct("SKU-H", null)).thenReturn(Map.of("success", 1, "failed", 0));

        Map<String, Object> result = handler.handle(body, sign(body), null);

        assertThat(result).containsEntry("ok", true);
    }

    @Test
    void handle_singleItemPullFailure_releasesClaim() {
        String body = "{\"eventId\":\"evt-item-4\",\"sku\":\"SKU-4\"}";
        when(webhookEventRepository.existsByEventId("evt-item-4")).thenReturn(false);
        when(syncService.pullExternalProduct("SKU-4", null)).thenReturn(Map.of("success", 0, "failed", 1));

        assertThatThrownBy(() -> handler.handle(body, null, SECRET)).isInstanceOf(ResponseStatusException.class);

        verify(webhookEventRepository).saveAndFlush(any());
        verify(webhookEventRepository).delete(any());
    }

    @Test
    void handle_missingIdentity_throwsBadRequest() {
        String body = "{\"eventId\":\"evt-item-5\"}";

        assertThatThrownBy(() -> handler.handle(body, null, SECRET))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Provide item_id or sku");
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
