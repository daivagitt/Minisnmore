package com.miniandmore.app.client;

import java.util.Map;
import java.util.Optional;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class LedgerlyIntegrationClient {

    private final RestClient.Builder restClientBuilder;
    private final com.miniandmore.app.service.LedgerlyCredentialsProvider credentials;

    public LedgerlyIntegrationClient(
        RestClient.Builder builder,
        com.miniandmore.app.service.LedgerlyCredentialsProvider credentials
    ) {
        this.restClientBuilder = builder;
        this.credentials = credentials;
    }

    private RestClient client() {
        String base = credentials.baseUrl();
        String normalized = base != null && !base.isBlank() ? base.replaceAll("/$", "") : "http://127.0.0.1:8080/api";
        return restClientBuilder.baseUrl(normalized).build();
    }

    public void syncOrder(Long orderId) {
        post("/v1/integrations/commerce/orders/sync", Map.of("orderId", orderId));
    }

    public void syncProductAsync(Long productId) {
        post("/v1/integrations/commerce/products/sync", Map.of("productId", productId, "async", true));
    }

    public void syncProduct(Long productId) {
        post("/v1/integrations/commerce/products/sync", Map.of("productId", productId));
    }

    public void deactivateProduct(Long productId) {
        post("/v1/integrations/commerce/products/sync", Map.of("productId", productId, "active", false));
    }

    public Map<String, Object> pullOrderTracking(String externalRef) {
        return post("/v1/integrations/commerce/orders/" + externalRef + "/tracking/pull", Map.of());
    }

    public Optional<String> createCreditNote(String externalRef, String returnNumber) {
        @SuppressWarnings("unchecked")
        Map<String, String> response = post(
            "/v1/integrations/commerce/orders/" + externalRef + "/credit-notes",
            Map.of("returnNumber", returnNumber)
        );
        if (response == null) {
            return Optional.empty();
        }
        String id = response.get("ledgerlyCreditNoteId");
        return Optional.ofNullable(id).filter(s -> !s.isBlank());
    }

    public Map<String, Object> testConnection() {
        return get("/v1/platform/integration/status");
    }

    public Map<String, Object> syncProductPayload(Map<String, Object> payload) {
        return post("/v1/integrations/commerce/products/sync", payload);
    }

    public Map<String, Object> pullProducts() {
        return post("/v1/integrations/commerce/products/pull", Map.of());
    }

    public Map<String, Object> pullInventory() {
        return post("/v1/integrations/commerce/inventory/pull", Map.of());
    }

    public Map<String, Object> syncOrderPayload(Map<String, Object> payload, String idempotencyKey) {
        RestClient.RequestBodySpec spec = authorized(client().post().uri("/v1/integrations/commerce/orders/sync"))
            .contentType(MediaType.APPLICATION_JSON);
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            spec = spec.header("Idempotency-Key", idempotencyKey);
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> body = spec.body(payload).retrieve().body(Map.class);
        return body;
    }

    @SuppressWarnings("unchecked")
    private <T> T post(String path, Object body) {
        return authorized(client().post().uri(path).contentType(MediaType.APPLICATION_JSON))
            .body(body)
            .retrieve()
            .body((Class<T>) Map.class);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> get(String path) {
        return authorized(client().get().uri(path)).retrieve().body(Map.class);
    }

    private RestClient.RequestHeadersSpec<?> authorized(RestClient.RequestHeadersUriSpec<?> spec) {
        String token = credentials.token();
        if (token != null && !token.isBlank()) {
            return spec.header("Authorization", "Bearer " + token);
        }
        return spec;
    }
}
