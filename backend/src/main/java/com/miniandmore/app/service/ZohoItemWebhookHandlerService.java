package com.miniandmore.app.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.config.ApplicationProperties;
import com.miniandmore.app.domain.ZohoItemWebhookEvent;
import com.miniandmore.app.repository.ZohoItemWebhookEventRepository;
import java.time.Instant;
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
        Map<String, Object> item = ZohoWebhookSupport.mergeNestedPayload(ZohoWebhookSupport.eventData(payload), "item", "product");

        String zohoItemId = ZohoWebhookSupport.firstNonBlank(
            ZohoWebhookSupport.stringValue(item.get("item_id")),
            ZohoWebhookSupport.stringValue(item.get("itemId"))
        );
        String sku = ZohoWebhookSupport.firstNonBlank(
            ZohoWebhookSupport.stringValue(item.get("sku")),
            ZohoWebhookSupport.stringValue(item.get("SKU"))
        );
        if (zohoItemId == null && sku == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provide item_id or sku");
        }

        String eventId = eventId(payload, item, zohoItemId, sku);
        if (eventId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing eventId");
        }
        if (webhookEventRepository.existsByEventId(eventId)) {
            return ZohoWebhookSupport.duplicateEventResult(eventId);
        }

        ZohoItemWebhookEvent record = new ZohoItemWebhookEvent();
        record.setEventId(eventId);
        record.setZohoItemId(zohoItemId);
        record.setSku(sku);
        record.setStatus(
            ZohoWebhookSupport.firstNonBlank(
                ZohoWebhookSupport.stringValue(item.get("status")),
                ZohoWebhookSupport.stringValue(item.get("item_status"))
            )
        );
        record.setReceivedAt(Instant.now());
        try {
            webhookEventRepository.saveAndFlush(record);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            return ZohoWebhookSupport.duplicateEventResult(eventId);
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

    private void verifySignature(String rawBody, String signature, String sharedSecret) {
        ZohoWebhookSupport.verifySecretOrSignature(
            applicationProperties.getZoho().getItemWebhookSecret(),
            rawBody,
            signature,
            sharedSecret,
            "Item webhook secret is not configured",
            "Missing item webhook authentication",
            "Invalid item webhook signature"
        );
    }

    private static String eventId(Map<String, Object> payload, Map<String, Object> item, String zohoItemId, String sku) {
        String explicit = ZohoWebhookSupport.explicitEventIdIncludingLegacyId(payload, item);
        if (explicit != null) {
            return explicit;
        }
        String modified = ZohoWebhookSupport.firstNonBlank(
            ZohoWebhookSupport.stringValue(item.get("last_modified_time")),
            ZohoWebhookSupport.stringValue(item.get("updated_time")),
            ZohoWebhookSupport.stringValue(item.get("created_time"))
        );
        String status = ZohoWebhookSupport.firstNonBlank(
            ZohoWebhookSupport.stringValue(item.get("status")),
            ZohoWebhookSupport.stringValue(item.get("item_status"))
        );
        String identity = ZohoWebhookSupport.firstNonBlank(zohoItemId, sku);
        return identity == null || (modified == null && status == null)
            ? null
            : "zoho-item:" + identity + ":" + ZohoWebhookSupport.normalizeStatusToken(status) + ":" + ZohoWebhookSupport.firstNonBlank(modified, "unknown");
    }
}
