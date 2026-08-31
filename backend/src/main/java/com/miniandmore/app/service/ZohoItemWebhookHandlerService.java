package com.miniandmore.app.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.config.ApplicationProperties;
import com.miniandmore.app.domain.ZohoItemWebhookEvent;
import com.miniandmore.app.repository.ZohoItemWebhookEventRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class ZohoItemWebhookHandlerService {

    private final ApplicationProperties applicationProperties;
    private final ZohoItemWebhookEventRepository webhookEventRepository;
    private final ZohoSyncService syncService;
    private final ObjectMapper objectMapper;

    public ZohoItemWebhookHandlerService(
        ApplicationProperties applicationProperties,
        ZohoItemWebhookEventRepository webhookEventRepository,
        ZohoSyncService syncService,
        ObjectMapper objectMapper
    ) {
        this.applicationProperties = applicationProperties;
        this.webhookEventRepository = webhookEventRepository;
        this.syncService = syncService;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> handle(String rawBody, String signature, String sharedSecret) {
        verifySignature(rawBody, signature, sharedSecret);
        Map<String, Object> payload = WebhookPayloadSupport.parseJsonMap(objectMapper, rawBody);
        Map<String, Object> item = normalizeItemPayload(eventData(payload));

        String zohoItemId = firstNonBlank(stringValue(item.get("item_id")), stringValue(item.get("itemId")));
        String sku = firstNonBlank(stringValue(item.get("sku")), stringValue(item.get("SKU")));
        if (zohoItemId == null && sku == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provide item_id or sku");
        }

        String eventId = eventId(payload, item, zohoItemId, sku);
        if (eventId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing eventId");
        }
        if (webhookEventRepository.existsByEventId(eventId)) {
            return Map.of("ok", true, "duplicate", true, "eventId", eventId);
        }

        ZohoItemWebhookEvent record = new ZohoItemWebhookEvent();
        record.setEventId(eventId);
        record.setZohoItemId(zohoItemId);
        record.setSku(sku);
        record.setStatus(firstNonBlank(stringValue(item.get("status")), stringValue(item.get("item_status"))));
        record.setReceivedAt(Instant.now());
        try {
            webhookEventRepository.saveAndFlush(record);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            return Map.of("ok", true, "duplicate", true, "eventId", eventId);
        }

        try {
            Map<String, Object> summary = syncService.pullExternalProduct(sku, zohoItemId);
            Object failed = summary.get("failed");
            if (failed instanceof Number number && number.intValue() > 0) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Zoho item pull failed");
            }
            return Map.of("ok", true, "eventId", eventId, "sku", StringUtils.defaultString(sku), "summary", summary);
        } catch (RuntimeException e) {
            webhookEventRepository.delete(record);
            throw e;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> eventData(Map<String, Object> payload) {
        Object data = payload.get("data");
        if (data instanceof Map<?, ?> map) {
            Map<String, Object> merged = new LinkedHashMap<>(payload);
            merged.putAll((Map<String, Object>) map);
            return merged;
        }
        return payload;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> normalizeItemPayload(Map<String, Object> data) {
        for (String key : List.of("item", "product")) {
            Object nested = data.get(key);
            if (nested instanceof Map<?, ?> map) {
                Map<String, Object> merged = new LinkedHashMap<>(data);
                merged.putAll((Map<String, Object>) map);
                return merged;
            }
        }
        return data;
    }

    private void verifySignature(String rawBody, String signature, String sharedSecret) {
        String secret = StringUtils.trimToNull(applicationProperties.getZoho().getItemWebhookSecret());
        if (secret == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Item webhook secret is not configured");
        }
        if (StringUtils.equals(secret, StringUtils.trimToNull(sharedSecret))) {
            return;
        }
        if (StringUtils.isBlank(signature)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing item webhook authentication");
        }
        if (!WebhookHmacSupport.signaturesMatch(secret, rawBody, signature)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid item webhook signature");
        }
    }

    private static String eventId(Map<String, Object> payload, Map<String, Object> item, String zohoItemId, String sku) {
        String explicit = firstNonBlank(
            stringValue(payload.get("eventId")),
            stringValue(payload.get("event_id")),
            stringValue(payload.get("id")),
            stringValue(item.get("eventId")),
            stringValue(item.get("event_id"))
        );
        if (explicit != null) {
            return explicit;
        }
        String modified = firstNonBlank(
            stringValue(item.get("last_modified_time")),
            stringValue(item.get("updated_time")),
            stringValue(item.get("created_time"))
        );
        String status = firstNonBlank(stringValue(item.get("status")), stringValue(item.get("item_status")));
        String identity = firstNonBlank(zohoItemId, sku);
        return identity == null || (modified == null && status == null)
            ? null
            : "zoho-item:" + identity + ":" + firstNonBlank(status, "unknown") + ":" + firstNonBlank(modified, "unknown");
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            String trimmed = StringUtils.trimToNull(value);
            if (trimmed != null) {
                return trimmed;
            }
        }
        return null;
    }

    private static String stringValue(Object value) {
        return value == null ? null : value.toString();
    }
}
