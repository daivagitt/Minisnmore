package com.miniandmore.app.client;

import com.miniandmore.app.service.ZohoOAuthService;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Thin REST client for the Zoho Books API v3 (https://www.zoho.com/books/api/v3/).
 */
@Component
public class ZohoBooksClient {

    private static final Duration BINARY_TIMEOUT = Duration.ofSeconds(60);
    private static final String MULTIPART_FIELD_IMAGE = "image";

    private final ZohoOAuthService oauthService;
    private final RestClient.Builder restClientBuilder;
    private final HttpClient httpClient;

    public ZohoBooksClient(ZohoOAuthService oauthService, RestClient.Builder restClientBuilder) {
        this.oauthService = oauthService;
        this.restClientBuilder = restClientBuilder;
        this.httpClient = HttpClient.newBuilder().connectTimeout(BINARY_TIMEOUT).followRedirects(HttpClient.Redirect.NORMAL).build();
    }

    private String apiBase() {
        return oauthService.apiHost(oauthService.dataCenter()) + "/books/v3";
    }

    private String inventoryApiBase() {
        return oauthService.apiHost(oauthService.dataCenter()) + "/inventory/v1";
    }

    private RestClient client() {
        return restClientBuilder.baseUrl(apiBase()).build();
    }

    private RestClient.RequestHeadersSpec<?> authorize(RestClient.RequestHeadersSpec<?> spec) {
        return spec.header("Authorization", "Zoho-oauthtoken " + oauthService.accessToken());
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

    private String orgIdParam() {
        return "organization_id=" + oauthService.orgId();
    }

    public Map<String, Object> getItem(String itemId) {
        return get("/items/" + itemId + "?" + orgIdParam());
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
        return listActiveItems(page);
    }

    /** Active catalogue / inventory pulls. */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listActiveItems(int page) {
        return listItemsByStatus(page, "Status.Active");
    }

    /**
     * Incremental product pulls — Zoho Books has no {@code last_modified_time} list filter, so callers
     * page every status and filter locally by each row's timestamp.
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listAllStatusItems(int page) {
        return listItemsByStatus(page, "Status.All");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listItemsByStatus(int page, String statusFilter) {
        String url = "/items?" + orgIdParam() + "&page=" + page + "&per_page=200&filter_by=" + statusFilter;
        Map<String, Object> response = get(url);
        return (List<Map<String, Object>>) response.getOrDefault("items", List.of());
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listLocations() {
        Map<String, Object> response = get("/locations?" + orgIdParam());
        return (List<Map<String, Object>>) response.getOrDefault("locations", List.of());
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listAssetAccounts() {
        Map<String, Object> response = get("/chartofaccounts?" + orgIdParam() + "&filter_by=AccountType.Asset");
        return (List<Map<String, Object>>) response.getOrDefault("chartofaccounts", List.of());
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listExpenseAccounts() {
        Map<String, Object> response = get("/chartofaccounts?" + orgIdParam() + "&filter_by=AccountType.Expense");
        return (List<Map<String, Object>>) response.getOrDefault("chartofaccounts", List.of());
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listItemFields() {
        Map<String, Object> response = get("/settings/fields?" + orgIdParam() + "&entity=item");
        return (List<Map<String, Object>>) response.getOrDefault("fields", List.of());
    }

    public void uploadItemImage(String itemId, byte[] imageBytes, String filename, String contentType) {
        if (imageBytes == null || imageBytes.length == 0) {
            throw new IllegalArgumentException("Item image bytes are empty");
        }
        try {
            executeImageUpload(itemId, imageBytes, filename, contentType, false);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Zoho item image upload interrupted", e);
        } catch (IOException e) {
            throw new IllegalStateException("Zoho item image upload failed", e);
        }
    }

    /**
     * Pushes every gallery photo onto the item's photo set via {@code POST /items/{id}/images}
     * (plural) rather than the single-photo {@code /items/{id}/image} endpoint. Not part of Zoho's
     * published Books v3 reference — confirmed only via community reports of the same
     * {@code books/v3} host. Confirmed live against a real UAT org: each call adds exactly ONE
     * document (its {@code attachment_order} increments per call) — sending multiple files in one
     * multipart request silently keeps only the last part, so images must be posted one at a time.
     * A failure is propagated so the product cannot be reported as fully synced with a partial gallery.
     */
    public void uploadItemImages(String itemId, List<ImageUpload> images) {
        if (images == null || images.isEmpty()) {
            throw new IllegalArgumentException("Item images are empty");
        }
        try {
            deleteItemImages(itemId);
            for (ImageUpload image : images) {
                executeImagesUpload(itemId, image, false);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Zoho item images upload interrupted", e);
        } catch (IOException e) {
            throw new IllegalStateException("Zoho item images upload failed", e);
        }
    }

    /**
     * Clears any photos already on the item (single-endpoint front image, plus any documents added
     * via the plural endpoint) before re-adding the current gallery — otherwise repeated syncs would
     * keep appending duplicate copies of the same photos forever. Zoho requires document ids on the
     * plural delete endpoint, so resolve them before clearing the front image whenever the response
     * exposes {@code documents}; older Books responses retain the post-delete fallback.
     */
    public void deleteItemImages(String itemId) {
        if (StringUtils.isBlank(itemId)) {
            return;
        }
        try {
            // Resolve the documented gallery list before mutating the item. A failed GET therefore
            // leaves the existing front image intact instead of creating a half-deleted gallery.
            Map<String, Object> itemResponse;
            try {
                itemResponse = getItem(itemId);
            } catch (RuntimeException e) {
                throw new IllegalStateException("Zoho item fetch failed while clearing images for " + itemId, e);
            }
            boolean documentsExposed = ZohoItemDocuments.exposesDocuments(itemResponse);
            List<String> documentIds = ZohoItemDocuments.pluralDocumentIdsBeforeFrontImageDelete(itemResponse);

            executeImageDelete(itemId, false);

            // Some Books responses omit `documents`. Preserve the existing UAT fallback: after the
            // front image is removed, Zoho may promote the next gallery id to image_document_id.
            if (!documentsExposed) {
                try {
                    itemResponse = getItem(itemId);
                } catch (RuntimeException e) {
                    throw new IllegalStateException("Zoho item fetch failed after clearing the front image for " + itemId, e);
                }
                documentIds = ZohoItemDocuments.documentIdsRemainingAfterFrontImageDelete(itemResponse);
            }
            if (!documentIds.isEmpty()) {
                executeImagesDelete(itemId, documentIds, false);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Zoho item images delete interrupted", e);
        } catch (IOException e) {
            throw new IllegalStateException("Zoho item images delete failed", e);
        }
    }

    public record ImageUpload(byte[] bytes, String filename, String contentType) {}

    public Optional<BinaryDownload> downloadItemImage(String itemId, long maxBytes) {
        if (StringUtils.isBlank(itemId)) {
            return Optional.empty();
        }
        try {
            return executeBinaryDownload("/items/" + itemId + "/image?" + orgIdParam(), maxBytes, false);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Zoho item image download interrupted", e);
        } catch (IOException e) {
            throw new IllegalStateException("Zoho item image download failed", e);
        }
    }

    public void deleteItemImage(String itemId) {
        if (StringUtils.isBlank(itemId)) {
            return;
        }
        try {
            executeImageDelete(itemId, false);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Zoho item image delete interrupted", e);
        } catch (IOException e) {
            throw new IllegalStateException("Zoho item image delete failed", e);
        }
    }

    public Optional<BinaryDownload> downloadDocument(String documentId, long maxBytes) {
        if (StringUtils.isBlank(documentId)) {
            return Optional.empty();
        }
        try {
            return executeBinaryDownload("/documents/" + documentId + "?" + orgIdParam(), maxBytes, false);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Zoho document download interrupted", e);
        } catch (IOException e) {
            throw new IllegalStateException("Zoho document download failed", e);
        }
    }

    public record BinaryDownload(byte[] bytes, String contentType) {}

    // --- Contacts ---

    @SuppressWarnings("unchecked")
    public Map<String, Object> findContactByEmail(String email) {
        Map<String, Object> response = get("/contacts?" + orgIdParam() + "&email=" + encode(email));
        List<Map<String, Object>> contacts = (List<Map<String, Object>>) response.getOrDefault("contacts", List.of());
        return contacts.isEmpty() ? null : contacts.get(0);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> findContactByName(String contactName) {
        Map<String, Object> response = get("/contacts?" + orgIdParam() + "&contact_name_contains=" + encode(contactName));
        List<Map<String, Object>> contacts = (List<Map<String, Object>>) response.getOrDefault("contacts", List.of());
        for (Map<String, Object> contact : contacts) {
            String name = stringValue(contact.get("contact_name"));
            if (contactName.equalsIgnoreCase(StringUtils.trimToEmpty(name))) {
                return contact;
            }
        }
        return null;
    }

    private static String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    public Map<String, Object> createContact(Map<String, Object> payload) {
        return post("/contacts?" + orgIdParam(), payload);
    }

    // --- Sales orders / invoices / credit notes ---

    public Map<String, Object> createSalesOrder(Map<String, Object> payload) {
        return post("/salesorders?" + orgIdParam(), payload);
    }

    public Map<String, Object> updateSalesOrder(String salesOrderId, Map<String, Object> payload) {
        return put("/salesorders/" + salesOrderId + "?" + orgIdParam(), payload);
    }

    public Map<String, Object> createInvoiceFromSalesOrder(String salesOrderId) {
        return post("/invoices/fromsalesorder?" + orgIdParam() + "&salesorder_id=" + salesOrderId, Map.of());
    }

    public Map<String, Object> markInvoiceAsSent(String invoiceId) {
        return post("/invoices/" + invoiceId + "/status/sent?" + orgIdParam(), Map.of());
    }

    public Map<String, Object> voidInvoice(String invoiceId) {
        return post("/invoices/" + invoiceId + "/status/void?" + orgIdParam(), Map.of());
    }

    public Map<String, Object> voidSalesOrder(String salesOrderId, String reason) {
        return post(
            "/salesorders/" + salesOrderId + "/status/void?" + orgIdParam(),
            Map.of("reason", StringUtils.defaultString(reason))
        );
    }

    public Map<String, Object> markSalesOrderAsOpen(String salesOrderId) {
        return post("/salesorders/" + salesOrderId + "/status/open?" + orgIdParam(), Map.of());
    }

    public Map<String, Object> markCreditNoteAsOpen(String creditNoteId) {
        return post("/creditnotes/" + creditNoteId + "/status/open?" + orgIdParam(), Map.of());
    }

    public Map<String, Object> getInvoice(String invoiceId) {
        return get("/invoices/" + invoiceId + "?" + orgIdParam());
    }

    /**
     * Downloads one invoice as a PDF via the bulk-export endpoint reused with a single id
     * ({@code invoice_ids}) — Zoho Books has no dedicated single-invoice PDF path.
     */
    public Optional<BinaryDownload> downloadInvoicePdf(String invoiceId, long maxBytes) {
        if (StringUtils.isBlank(invoiceId)) {
            return Optional.empty();
        }
        try {
            return executeBinaryDownload("/invoices/pdf?" + orgIdParam() + "&invoice_ids=" + encode(invoiceId), maxBytes, false);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Zoho invoice PDF download interrupted", e);
        } catch (IOException e) {
            throw new IllegalStateException("Zoho invoice PDF download failed", e);
        }
    }

    public Map<String, Object> getSalesOrder(String salesOrderId) {
        return get("/salesorders/" + salesOrderId + "?" + orgIdParam());
    }

    /**
     * Find a sales order MiniMS already created for this store order number. Used so a second
     * payment-confirm/retry does not open another Confirmed SO after the first was invoiced/closed.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> findSalesOrderByReferenceNumber(String referenceNumber) {
        String trimmed = StringUtils.trimToNull(referenceNumber);
        if (trimmed == null) {
            return null;
        }
        Map<String, Object> response = get("/salesorders?" + orgIdParam() + "&reference_number=" + encode(trimmed));
        List<Map<String, Object>> salesOrders = (List<Map<String, Object>>) response.getOrDefault("salesorders", List.of());
        Map<String, Object> best = null;
        int bestRank = -1;
        for (Map<String, Object> salesOrder : salesOrders) {
            String reference = stringValue(salesOrder.get("reference_number"));
            if (!trimmed.equalsIgnoreCase(StringUtils.trimToEmpty(reference))) {
                continue;
            }
            int rank = salesOrderReuseRank(stringValue(salesOrder.get("status")));
            if (rank > bestRank) {
                best = salesOrder;
                bestRank = rank;
            }
        }
        return best;
    }

    private static int salesOrderReuseRank(String status) {
        String normalized = StringUtils.lowerCase(StringUtils.trimToEmpty(status));
        return switch (normalized) {
            case "void", "voided" -> -1;
            case "draft" -> 1;
            case "pending", "open", "confirmed" -> 2;
            case "invoiced", "closed", "fulfilled" -> 3;
            default -> 0;
        };
    }

    /**
     * Zoho rejects inline {@code billing_address}/{@code shipping_address} on create when the
     * serialized object exceeds 100 characters. Apply one-off addresses after SO creation instead.
     */
    public Map<String, Object> updateSalesOrderBillingAddress(String salesOrderId, Map<String, Object> address) {
        return updateSalesOrderAddress(salesOrderId, "billing", address);
    }

    public Map<String, Object> updateSalesOrderShippingAddress(String salesOrderId, Map<String, Object> address) {
        return updateSalesOrderAddress(salesOrderId, "shipping", address);
    }

    private Map<String, Object> updateSalesOrderAddress(String salesOrderId, String type, Map<String, Object> address) {
        Map<String, Object> payload = new java.util.LinkedHashMap<>(address);
        payload.put("is_one_off_address", true);
        payload.put("is_update_customer", false);
        return put("/salesorders/" + salesOrderId + "/address/" + type + "?" + orgIdParam(), payload);
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listDeliveryChallans(String salesOrderId) {
        Map<String, Object> response = get("/deliverychallans?" + orgIdParam() + "&salesorder_id=" + encode(salesOrderId));
        return (List<Map<String, Object>>) response.getOrDefault("deliverychallans", List.of());
    }

    /**
     * Packages shown from a Books sales order are exposed by Zoho's Inventory API. Search by the
     * human-readable sales-order number to keep the response bounded, then verify the immutable id
     * so similarly numbered orders cannot advance the wrong store order.
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listPackagesForSalesOrder(String salesOrderId, String salesOrderNumber) {
        StringBuilder url = new StringBuilder(inventoryApiBase())
            .append("/packages?")
            .append(orgIdParam())
            .append("&per_page=200");
        if (StringUtils.isNotBlank(salesOrderNumber)) {
            url.append("&salesorder_number_contains=").append(encode(salesOrderNumber.trim()));
        }
        Map<String, Object> response = get(url.toString());
        Object rawPackages = response.get("packages");
        if (!(rawPackages instanceof List<?>)) {
            // Zoho's published example currently spells this response key `packagea`.
            rawPackages = response.get("packagea");
        }
        if (!(rawPackages instanceof List<?> packages)) {
            return List.of();
        }
        return packages
            .stream()
            .filter(Map.class::isInstance)
            .map(value -> (Map<String, Object>) value)
            .filter(value -> salesOrderId.equals(String.valueOf(value.get("salesorder_id"))))
            .toList();
    }

    public Map<String, Object> createCreditNote(Map<String, Object> payload) {
        return post("/creditnotes?" + orgIdParam(), payload);
    }

    public Map<String, Object> createCustomerPayment(Map<String, Object> payload) {
        return post("/customerpayments?" + orgIdParam(), payload);
    }

    public Map<String, Object> createInventoryAdjustment(Map<String, Object> payload) {
        return post("/inventoryadjustments?" + orgIdParam(), payload);
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

    private Optional<BinaryDownload> executeBinaryDownload(String pathWithQuery, long maxBytes, boolean retriedAfterUnauthorized)
        throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(apiBase() + pathWithQuery))
            .timeout(BINARY_TIMEOUT)
            .header("Authorization", "Zoho-oauthtoken " + oauthService.accessToken())
            .header("Accept", "*/*")
            .GET()
            .build();
        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        int statusCode = response.statusCode();
        if (statusCode == 401 && !retriedAfterUnauthorized) {
            response.body().close();
            oauthService.accessToken();
            return executeBinaryDownload(pathWithQuery, maxBytes, true);
        }
        if (statusCode == 404) {
            response.body().close();
            return Optional.empty();
        }
        if (statusCode < 200 || statusCode >= 300) {
            String errorBody;
            try (InputStream errorStream = response.body()) {
                errorBody = new String(errorStream.readAllBytes(), StandardCharsets.UTF_8);
            }
            throw new IOException("Zoho Books download failed: HTTP " + statusCode + " " + errorBody);
        }
        String contentType = response.headers().firstValue("Content-Type").orElse("application/octet-stream");
        if (contentType.toLowerCase(Locale.ROOT).startsWith("application/json")) {
            response.body().close();
            return Optional.empty();
        }
        try (InputStream body = response.body()) {
            byte[] bytes = readBounded(body, maxBytes);
            return bytes.length == 0 ? Optional.empty() : Optional.of(new BinaryDownload(bytes, contentType));
        }
    }

    private void executeImageUpload(String itemId, byte[] imageBytes, String filename, String contentType, boolean retriedAfterUnauthorized)
        throws IOException, InterruptedException {
        String boundary = "----ZohoBooksBoundary" + System.currentTimeMillis();
        byte[] body = multipartImageBody(boundary, filename, contentType, imageBytes);
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(apiBase() + "/items/" + itemId + "/image?" + orgIdParam()))
            .timeout(BINARY_TIMEOUT)
            .header("Authorization", "Zoho-oauthtoken " + oauthService.accessToken())
            .header("Content-Type", "multipart/form-data; boundary=" + boundary)
            .POST(HttpRequest.BodyPublishers.ofByteArray(body))
            .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        int statusCode = response.statusCode();
        if (statusCode == 401 && !retriedAfterUnauthorized) {
            oauthService.accessToken();
            executeImageUpload(itemId, imageBytes, filename, contentType, true);
            return;
        }
        if (statusCode < 200 || statusCode >= 300) {
            throw new IOException("Zoho item image upload failed: HTTP " + statusCode + " " + response.body());
        }
    }

    private void executeImagesUpload(String itemId, ImageUpload image, boolean retriedAfterUnauthorized)
        throws IOException, InterruptedException {
        String boundary = "----ZohoBooksBoundary" + System.currentTimeMillis();
        byte[] body = multipartImageBody(boundary, image.filename(), image.contentType(), image.bytes());
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(apiBase() + "/items/" + itemId + "/images?" + orgIdParam()))
            .timeout(BINARY_TIMEOUT)
            .header("Authorization", "Zoho-oauthtoken " + oauthService.accessToken())
            .header("Content-Type", "multipart/form-data; boundary=" + boundary)
            .POST(HttpRequest.BodyPublishers.ofByteArray(body))
            .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        int statusCode = response.statusCode();
        if (statusCode == 401 && !retriedAfterUnauthorized) {
            oauthService.accessToken();
            executeImagesUpload(itemId, image, true);
            return;
        }
        if (statusCode < 200 || statusCode >= 300) {
            throw new IOException("Zoho item images upload failed: HTTP " + statusCode + " " + response.body());
        }
    }

    private void executeImagesDelete(String itemId, List<String> documentIds, boolean retriedAfterUnauthorized)
        throws IOException, InterruptedException {
        if (documentIds == null || documentIds.isEmpty()) {
            return;
        }
        HttpRequest request = HttpRequest.newBuilder()
            .uri(
                URI.create(
                    apiBase() +
                    "/items/" +
                    itemId +
                    "/images?" +
                    orgIdParam() +
                    "&document_ids=" +
                    encode(String.join(",", documentIds))
                )
            )
            .timeout(BINARY_TIMEOUT)
            .header("Authorization", "Zoho-oauthtoken " + oauthService.accessToken())
            .DELETE()
            .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        int statusCode = response.statusCode();
        if (statusCode == 401 && !retriedAfterUnauthorized) {
            oauthService.accessToken();
            executeImagesDelete(itemId, documentIds, true);
            return;
        }
        if (statusCode == 404) {
            return;
        }
        if (statusCode < 200 || statusCode >= 300) {
            if (isBenignItemImagesDeleteFailure(statusCode, response.body())) {
                return;
            }
            throw new IOException("Zoho item images delete failed: HTTP " + statusCode + " " + response.body());
        }
    }

    private static boolean isBenignItemImagesDeleteFailure(int statusCode, String responseBody) {
        if (statusCode != 400) {
            return false;
        }
        String body = StringUtils.defaultString(responseBody).toLowerCase(Locale.ROOT);
        if (body.contains("\"code\":33008") || body.contains("\"code\": 33008")) {
            return true;
        }
        boolean referencesMissingAttachment = body.contains("document") || body.contains("file");
        return (
            referencesMissingAttachment &&
            (
                body.contains("not exist") ||
                body.contains("does not exist") ||
                body.contains("not found") ||
                body.contains("invalid") ||
                body.contains("already deleted")
            )
        );
    }

    private void executeImageDelete(String itemId, boolean retriedAfterUnauthorized) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(apiBase() + "/items/" + itemId + "/image?" + orgIdParam()))
            .timeout(BINARY_TIMEOUT)
            .header("Authorization", "Zoho-oauthtoken " + oauthService.accessToken())
            .DELETE()
            .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        int statusCode = response.statusCode();
        if (statusCode == 401 && !retriedAfterUnauthorized) {
            oauthService.accessToken();
            executeImageDelete(itemId, true);
            return;
        }
        if (statusCode == 404) {
            return;
        }
        if (statusCode < 200 || statusCode >= 300) {
            if (isBenignItemImagesDeleteFailure(statusCode, response.body())) {
                return;
            }
            throw new IOException("Zoho item image delete failed: HTTP " + statusCode + " " + response.body());
        }
    }

    private static byte[] multipartImageBody(String boundary, String filename, String contentType, byte[] imageBytes) throws IOException {
        String safeName = sanitizeMultipartFilename(filename);
        String safeType = StringUtils.defaultIfBlank(contentType, "image/jpeg");
        String crlf = "\r\n";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(("--" + boundary + crlf).getBytes(StandardCharsets.UTF_8));
        out.write(
            ("Content-Disposition: form-data; name=\"" + MULTIPART_FIELD_IMAGE + "\"; filename=\"" + safeName + "\"" + crlf)
                .getBytes(StandardCharsets.UTF_8)
        );
        out.write(("Content-Type: " + safeType + crlf + crlf).getBytes(StandardCharsets.UTF_8));
        out.write(imageBytes);
        out.write((crlf + "--" + boundary + "--" + crlf).getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    static String sanitizeMultipartFilename(String filename) {
        if (filename == null) {
            return "product.jpg";
        }
        StringBuilder sanitized = new StringBuilder(filename.length());
        for (int i = 0; i < filename.length(); i++) {
            char character = filename.charAt(i);
            if (character == '"' || character <= 0x1F || character == 0x7F || character == '\r' || character == '\n') {
                continue;
            }
            sanitized.append(character);
        }
        String result = sanitized.toString().trim();
        return StringUtils.isBlank(result) ? "product.jpg" : result;
    }

    private static byte[] readBounded(InputStream input, long maxBytes) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) {
                throw new IOException("Zoho download exceeds max size (" + maxBytes + " bytes)");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }
}
