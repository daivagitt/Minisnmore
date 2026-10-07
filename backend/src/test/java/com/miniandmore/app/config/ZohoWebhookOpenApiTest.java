package com.miniandmore.app.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import org.junit.jupiter.api.Test;
import org.springdoc.core.customizers.OpenApiCustomizer;

class ZohoWebhookOpenApiTest {

    private final OpenApiCustomizer publicApiPathCustomizer = new SpringDocConfiguration().publicApiPathCustomizer();

    @Test
    void publicApiPaths_includeZohoWebhookEndpoints() {
        OpenAPI openAPI = new OpenAPI();
        openAPI.path("/internal/v1/zoho/invoice-webhook", webhookPath("receiveInvoiceWebhook"));
        openAPI.path("/internal/v1/zoho/order-status-webhook", webhookPath("receiveOrderStatusWebhook"));
        openAPI.path("/internal/v1/zoho/shipment-webhook", webhookPath("receiveShipmentWebhook"));
        openAPI.path("/internal/v1/zoho/stock-webhook", webhookPath("receiveStockWebhook"));
        openAPI.path("/internal/v1/webhooks/razorpay", webhookPath("receive"));

        publicApiPathCustomizer.customise(openAPI);

        assertThat(openAPI.getPaths()).containsKeys(
            "/api/v1/zoho/invoice-webhook",
            "/api/v1/zoho/order-status-webhook",
            "/api/v1/zoho/shipment-webhook",
            "/api/v1/zoho/stock-webhook",
            "/api/v1/webhooks/razorpay"
        );
    }

    private static PathItem webhookPath(String operationId) {
        return new PathItem().post(new Operation().operationId(operationId));
    }
}
