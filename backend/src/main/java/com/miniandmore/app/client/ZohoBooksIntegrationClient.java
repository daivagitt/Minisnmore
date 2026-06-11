package com.miniandmore.app.client;

import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class ZohoBooksIntegrationClient {

    private final RestClient restClient;
    private final String internalApiKey;

    public ZohoBooksIntegrationClient(
        RestClient.Builder builder,
        @Value("${application.zoho-integration.base-url:http://127.0.0.1:8083}") String baseUrl,
        @Value("${application.internal-api-key:}") String internalApiKey
    ) {
        this.restClient = builder.baseUrl(baseUrl).build();
        this.internalApiKey = internalApiKey;
    }

    public void syncOrder(Long orderId) {
        post("/internal/v1/zoho/triggers/sync-orders/" + orderId, Map.of());
    }

    public void syncProductAsync(Long productId) {
        post("/internal/v1/zoho/triggers/sync-products/" + productId + "/async", Map.of());
    }

    public void syncProduct(Long productId) {
        post("/internal/v1/zoho/triggers/sync-products/" + productId, Map.of());
    }

    public void deactivateProductInZoho(Long productId) {
        post("/internal/v1/zoho/triggers/deactivate-products/" + productId, Map.of());
    }

    public void pullOrderTracking(Long orderId) {
        post("/internal/v1/zoho/triggers/pull-order-tracking/" + orderId, Map.of());
    }

    public Optional<String> createCreditNote(Long orderId, String returnNumber) {
        @SuppressWarnings("unchecked")
        Map<String, String> response = post(
            "/internal/v1/zoho/triggers/credit-notes",
            Map.of("orderId", orderId, "returnNumber", returnNumber)
        );
        if (response == null) {
            return Optional.empty();
        }
        String id = response.get("zohoCreditNoteId");
        return Optional.ofNullable(id).filter(s -> !s.isBlank());
    }

    @SuppressWarnings("unchecked")
    private <T> T post(String path, Object body) {
        RestClient.RequestBodySpec spec = restClient.post().uri(path).contentType(MediaType.APPLICATION_JSON);
        if (internalApiKey != null && !internalApiKey.isBlank()) {
            spec = spec.header("X-Internal-Api-Key", internalApiKey);
        }
        return spec.body(body).retrieve().body((Class<T>) Map.class);
    }
}
