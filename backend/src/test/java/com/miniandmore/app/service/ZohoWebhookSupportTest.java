package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ZohoWebhookSupportTest {

    @Test
    void eventData_mergesNestedDataMap() {
        Map<String, Object> payload = Map.of(
            "event_id",
            "evt-1",
            "data",
            Map.of("invoice_id", "inv-1", "status", "sent")
        );

        Map<String, Object> merged = ZohoWebhookSupport.eventData(payload);

        assertThat(merged).containsEntry("event_id", "evt-1").containsEntry("invoice_id", "inv-1").containsEntry("status", "sent");
    }

    @Test
    void mergeNestedPayload_flattensInvoiceObject() {
        Map<String, Object> data = Map.of("invoice", Map.of("invoice_id", "inv-2", "status", "paid"));

        Map<String, Object> merged = ZohoWebhookSupport.mergeNestedPayload(data, "invoice");

        assertThat(merged).containsEntry("invoice_id", "inv-2").containsEntry("status", "paid");
    }

    @Test
    void explicitEventId_ignoresLegacyIdField() {
        Map<String, Object> payload = Map.of("id", "inv-zoho", "invoice_id", "inv-zoho", "status", "sent");

        assertThat(ZohoWebhookSupport.explicitEventId(payload)).isNull();
        assertThat(ZohoWebhookSupport.explicitEventIdIncludingLegacyId(payload)).isEqualTo("inv-zoho");
    }

    @Test
    void stringValue_treatsUnresolvedZohoPlaceholderAsBlank() {
        assertThat(ZohoWebhookSupport.stringValue("${SHIPMENT_ORDER.TRACKING_NUMBER}")).isNull();
        assertThat(ZohoWebhookSupport.stringValue("  ${SHIPMENT_ORDER.CARRIER}  ")).isNull();
        assertThat(ZohoWebhookSupport.stringValue("AWB-99")).isEqualTo("AWB-99");
        assertThat(ZohoWebhookSupport.isUnresolvedPlaceholder("${SHIPMENT_ORDER.DATE}")).isTrue();
    }

    @Test
    void blankUnresolvedPlaceholders_nullsTemplateValues() {
        java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("tracking_number", "${SHIPMENT_ORDER.TRACKING_NUMBER}");
        map.put("carrier", "DTDC");
        map.put("status", "");

        ZohoWebhookSupport.blankUnresolvedPlaceholders(map);

        assertThat(map.get("tracking_number")).isNull();
        assertThat(map.get("status")).isNull();
        assertThat(map.get("carrier")).isEqualTo("DTDC");
    }

    @Test
    void duplicateEventResult_hasExpectedShape() {
        assertThat(ZohoWebhookSupport.duplicateEventResult("evt-dup"))
            .containsEntry("ok", true)
            .containsEntry("duplicate", true)
            .containsEntry("eventId", "evt-dup");
    }

    @Test
    void disabledWebhookResult_hasExpectedShape() {
        assertThat(ZohoWebhookSupport.disabledWebhookResult())
            .containsEntry("ok", true)
            .containsEntry("skipped", true)
            .containsEntry("reason", "inbound webhook disabled");
    }

    @Test
    void skippedResult_includesEventIdWhenPresent() {
        assertThat(ZohoWebhookSupport.skippedResult("no matching store order", "evt-skip"))
            .containsEntry("ok", true)
            .containsEntry("skipped", true)
            .containsEntry("reason", "no matching store order")
            .containsEntry("eventId", "evt-skip");
        assertThat(ZohoWebhookSupport.skippedResult("inbound webhook disabled")).doesNotContainKey("eventId");
    }

    @Test
    void isStoreOrderNumber_requiresMnmPrefix() {
        assertThat(ZohoWebhookSupport.isStoreOrderNumber("MNM-443B4DB2DCE3")).isTrue();
        assertThat(ZohoWebhookSupport.isStoreOrderNumber("  MNM-443B4DB2DCE3  ")).isTrue();
        assertThat(ZohoWebhookSupport.isStoreOrderNumber("MnM/26-27/0044")).isFalse();
        assertThat(ZohoWebhookSupport.isStoreOrderNumber(null)).isFalse();
    }
}
