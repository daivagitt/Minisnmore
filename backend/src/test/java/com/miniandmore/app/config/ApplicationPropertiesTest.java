package com.miniandmore.app.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ApplicationPropertiesTest {

    @Test
    void nestedProperties_haveDefaultsAndSetters() {
        ApplicationProperties properties = new ApplicationProperties();

        assertThat(properties.getLiquibase().getAsyncStart()).isTrue();
        properties.getLiquibase().setAsyncStart(false);
        assertThat(properties.getLiquibase().getAsyncStart()).isFalse();

        assertThat(properties.getStorage().getS3().getRegion()).isEqualTo("us-east-1");
        properties.getStorage().getS3().setBucket("bucket");
        assertThat(properties.getStorage().getS3().getBucket()).isEqualTo("bucket");

        assertThat(properties.getInventory()).isNotNull();
        properties.getInventory().getReservationRelease().setFixedDelayMs(120_000L);
        assertThat(properties.getInventory().getReservationRelease().getFixedDelayMs()).isEqualTo(120_000L);

        assertThat(properties.getMail().getConfigChangeTo()).isEqualTo("info@stoltglobal.com,saikedar2003@gmail.com");
        properties.getMail().setConfigChangeTo("ops@example.com");
        assertThat(properties.getMail().getConfigChangeTo()).isEqualTo("ops@example.com");
    }

    @Test
    void fastSmsTemplates_andZohoProductFields_roundTrip() {
        ApplicationProperties properties = new ApplicationProperties();
        ApplicationProperties.FastSms.Templates templates = properties.getFastsms().getTemplates();
        assertThat(templates.getOrderConfirmed()).contains("Minis n More");
        templates.setReturnApproved("approved body");
        templates.setReturnApprovedTemplateId("tmpl-approved");
        templates.setReturnRejected("rejected body");
        templates.setReturnRejectedTemplateId("tmpl-rejected");
        templates.setReturnPickupScheduled("pickup body");
        templates.setReturnPickupScheduledTemplateId("tmpl-pickup");
        templates.setReturnRefunded("refunded body");
        templates.setReturnRefundedTemplateId("tmpl-refund");
        templates.setReturnFailed("failed body");
        templates.setReturnFailedTemplateId("tmpl-fail");
        assertThat(templates.getReturnApproved()).isEqualTo("approved body");
        assertThat(templates.getReturnFailedTemplateId()).isEqualTo("tmpl-fail");

        ApplicationProperties.Zoho.ProductFields fields = properties.getZoho().getProductFields();
        assertThat(fields.getFabricTypeLabel()).isEqualTo("Fabric Type");
        fields.setFabricTypeLabel("Fabric");
        fields.setSizesLabel("Size");
        fields.setAgeMinLabel("Age Min");
        fields.setAgeMaxLabel("Age Max");
        fields.setCategoryLabel("Cat");
        fields.setMrpApiName("rate");
        assertThat(fields.fabricTypeLabels()).containsExactly("Fabric");
        assertThat(fields.sizesLabels()).containsExactly("Size", "Sizes");
        assertThat(fields.ageMinLabels()).contains("Age Min");
        assertThat(fields.ageMaxLabels()).contains("Age Max");
        assertThat(fields.categoryLabels()).containsExactly("Cat", "Category");
    }
}
