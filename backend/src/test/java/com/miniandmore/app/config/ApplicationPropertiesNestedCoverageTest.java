package com.miniandmore.app.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Exercises nested ApplicationProperties accessors not covered elsewhere. */
class ApplicationPropertiesNestedCoverageTest {

    @Test
    void topLevelAndNestedBeans_roundTripAllMissedAccessors() {
        ApplicationProperties properties = new ApplicationProperties();

        assertThat(properties.getZoho()).isNotNull();
        assertThat(properties.getLedgerly()).isNotNull();
        assertThat(properties.getXpressbees()).isNotNull();
        assertThat(properties.getPostOrder()).isNotNull();

        ApplicationProperties.Zoho.ProductFields fields = properties.getZoho().getProductFields();
        assertThat(fields.getSizesLabel()).isEqualTo("sizes");
        fields.setSizesLabel("Size Column");
        assertThat(fields.getSizesLabel()).isEqualTo("Size Column");
        fields.setAgeMinLabel("Min Age");
        fields.setAgeMaxLabel("Max Age");
        fields.setCategoryLabel("Category Name");
        assertThat(fields.getAgeMinLabel()).isEqualTo("Min Age");
        assertThat(fields.getAgeMaxLabel()).isEqualTo("Max Age");
        assertThat(fields.getCategoryLabel()).isEqualTo("Category Name");

        ApplicationProperties.XpressBees xb = properties.getXpressbees();
        xb.setPaymentType("prepaid");
        assertThat(xb.getPaymentType()).isEqualTo("prepaid");
        xb.setWarehouseLine2("Suite 2");
        assertThat(xb.getWarehouseLine2()).isEqualTo("Suite 2");
        xb.setTrackingUrlTemplate("https://track.example/{awb}");
        assertThat(xb.getTrackingUrlTemplate()).contains("{awb}");
        xb.setAuthType("legacy");
        assertThat(xb.usesLegacyAuth()).isTrue();
        assertThat(xb.resolvedForwardManifestPath()).isEqualTo("/api/courier/order");
        xb.setForwardManifestPath("/custom/path");
        assertThat(xb.resolvedForwardManifestPath()).isEqualTo("/custom/path");

        ApplicationProperties.FastSms.Templates templates = properties.getFastsms().getTemplates();
        templates.setOrderConfirmed("confirmed");
        templates.setOrderConfirmedTemplateId("tmpl-oc");
        templates.setOrderCancelled("cancelled");
        templates.setOrderCancelledTemplateId("tmpl-can");
        assertThat(templates.getOrderConfirmed()).isEqualTo("confirmed");
        assertThat(templates.getOrderConfirmedTemplateId()).isEqualTo("tmpl-oc");
        assertThat(templates.getOrderCancelled()).isEqualTo("cancelled");
        assertThat(templates.getOrderCancelledTemplateId()).isEqualTo("tmpl-can");

        ApplicationProperties.Ledgerly ledgerly = properties.getLedgerly();
        ledgerly.setApiBaseUrl("https://ledgerly.test");
        ledgerly.setApiToken("token");
        ledgerly.setOrgId("org-1");
        ledgerly.setWebhookSecret("whsec");
        ledgerly.setSyncProductImages(true);
        ledgerly.setAutoSyncEnabled(true);
        ledgerly.setAutoPullEnabled(true);
        assertThat(ledgerly.getApiBaseUrl()).isEqualTo("https://ledgerly.test");
        assertThat(ledgerly.getApiToken()).isEqualTo("token");
        assertThat(ledgerly.getOrgId()).isEqualTo("org-1");
        assertThat(ledgerly.getWebhookSecret()).isEqualTo("whsec");
        assertThat(ledgerly.isSyncProductImages()).isTrue();
        assertThat(ledgerly.isAutoSyncEnabled()).isTrue();
        assertThat(ledgerly.isAutoPullEnabled()).isTrue();

        ApplicationProperties.Ledgerly.Schedulers schedulers = ledgerly.getSchedulers();
        schedulers.setRetryOrdersMs(60_000L);
        schedulers.setPullTrackingMs(120_000L);
        schedulers.setPullInventoryMs(180_000L);
        assertThat(schedulers.getRetryOrdersMs()).isEqualTo(60_000L);
        assertThat(schedulers.getPullTrackingMs()).isEqualTo(120_000L);
        assertThat(schedulers.getPullInventoryMs()).isEqualTo(180_000L);

        ApplicationProperties.PostOrderSla sla = properties.getPostOrder().getSla();
        sla.setCancellationRequestedHours(12);
        sla.setReturnRequestedHours(24);
        sla.setReturnPickupHours(36);
        sla.setReturnPickupFailedHours(40);
        sla.setReturnInspectionHours(44);
        sla.setReturnDefaultHours(48);
        sla.setCollectedCodRefundHours(52);
        assertThat(sla.getCancellationRequestedHours()).isEqualTo(12);
        assertThat(sla.getReturnRequestedHours()).isEqualTo(24);
        assertThat(sla.getReturnPickupHours()).isEqualTo(36);
        assertThat(sla.getReturnPickupFailedHours()).isEqualTo(40);
        assertThat(sla.getReturnInspectionHours()).isEqualTo(44);
        assertThat(sla.getReturnDefaultHours()).isEqualTo(48);
        assertThat(sla.getCollectedCodRefundHours()).isEqualTo(52);
    }
}
