package com.miniandmore.app.client;

import com.miniandmore.app.service.ZohoOAuthService;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Thin REST client for the Zoho Books API v3 (https://www.zoho.com/books/api/v3/).
 */
@Component
public class ZohoBooksClient {

    private final ZohoOAuthService oauthService;
    private final RestClient.Builder restClientBuilder;

    public ZohoBooksClient(ZohoOAuthService oauthService, RestClient.Builder restClientBuilder) {
        this.oauthService = oauthService;
        this.restClientBuilder = restClientBuilder;
    }

    private RestClient client() {
        return restClientBuilder.baseUrl(oauthService.apiHost(oauthService.dataCenter()) + "/books/v3").build();
    }

    private RestClient.RequestHeadersSpec<?> authorize(RestClient.RequestHeadersSpec<?> spec) {
        return spec.header("Authorization", "Zoho-oauthtoken " + oauthService.accessToken());
    }

    private String orgIdParam() {
        return "organization_id=" + oauthService.orgId();
    }

    public Map<String, Object> testConnection() {
        return get("/organizations?" + orgIdParam());
    }

    // --- Items ---

    @SuppressWarnings("unchecked")
    public Map<String, Object> findItemBySku(String sku) {
        Map<String, Object> response = get("/items?" + orgIdParam() + "&sku=" + encode(sku));
        List<Map<String, Object>> items = (List<Map<String, Object>>) response.getOrDefault("items", List.of());
        return items.isEmpty() ? null : items.get(0);
    }

    public Map<String, Object> createItem(Map<String, Object> payload) {
        return post("/items?" + orgIdParam(), payload);
    }

    public Map<String, Object> updateItem(String itemId, Map<String, Object> payload) {
        return put("/items/" + itemId + "?" + orgIdParam(), payload);
    }

    public Map<String, Object> markItemActive(String itemId) {
        return post("/items/" + itemId + "/active?" + orgIdParam(), Map.of());
    }

    public Map<String, Object> markItemInactive(String itemId) {
        return post("/items/" + itemId + "/inactive?" + orgIdParam(), Map.of());
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listItems(int page) {
        Map<String, Object> response = get("/items?" + orgIdParam() + "&page=" + page + "&per_page=200");
        return (List<Map<String, Object>>) response.getOrDefault("items", List.of());
    }

    // --- Contacts ---

    @SuppressWarnings("unchecked")
    public Map<String, Object> findContactByEmail(String email) {
        Map<String, Object> response = get("/contacts?" + orgIdParam() + "&email=" + encode(email));
        List<Map<String, Object>> contacts = (List<Map<String, Object>>) response.getOrDefault("contacts", List.of());
        return contacts.isEmpty() ? null : contacts.get(0);
    }

    public Map<String, Object> createContact(Map<String, Object> payload) {
        return post("/contacts?" + orgIdParam(), payload);
    }

    // --- Sales orders / invoices / credit notes ---

    public Map<String, Object> createSalesOrder(Map<String, Object> payload) {
        return post("/salesorders?" + orgIdParam(), payload);
    }

    public Map<String, Object> createInvoiceFromSalesOrder(String salesOrderId) {
        return post("/invoices/fromsalesorder?" + orgIdParam() + "&salesorder_id=" + salesOrderId, Map.of());
    }

    public Map<String, Object> getInvoice(String invoiceId) {
        return get("/invoices/" + invoiceId + "?" + orgIdParam());
    }

    public Map<String, Object> createCreditNote(Map<String, Object> payload) {
        return post("/creditnotes?" + orgIdParam(), payload);
    }

    // --- HTTP plumbing ---

    @SuppressWarnings("unchecked")
    private Map<String, Object> get(String path) {
        return (Map<String, Object>) authorize(client().get().uri(path)).retrieve().body(Map.class);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> post(String path, Object body) {
        RestClient.RequestBodySpec spec = client().post().uri(path).contentType(MediaType.APPLICATION_JSON);
        return (Map<String, Object>) authorize(spec.body(body)).retrieve().body(Map.class);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> put(String path, Object body) {
        RestClient.RequestBodySpec spec = client().put().uri(path).contentType(MediaType.APPLICATION_JSON);
        return (Map<String, Object>) authorize(spec.body(body)).retrieve().body(Map.class);
    }

    private String encode(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }
}
