package com.miniandmore.app.service;

import com.miniandmore.app.client.ZohoBooksClient;
import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.CustomerProfile;
import com.miniandmore.app.domain.OrderItem;
import com.miniandmore.app.domain.Product;
import com.miniandmore.app.domain.enumeration.ZohoSyncStatus;
import com.miniandmore.app.domain.enumeration.OrderStatus;
import com.miniandmore.app.domain.enumeration.PaymentStatus;
import com.miniandmore.app.repository.CustomerOrderRepository;
import com.miniandmore.app.repository.CustomerProfileRepository;
import com.miniandmore.app.repository.ProductRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Orchestrates Zoho Books sync: product push/pull, inventory pull, order (sales order + invoice)
 * push, and order status pull. Talks to the real Zoho Books API v3 via {@link ZohoBooksClient}.
 */
@Service
@Transactional
public class ZohoSyncService {

    private static final Logger LOG = LoggerFactory.getLogger(ZohoSyncService.class);

    private final ZohoBooksClient booksClient;
    private final ProductRepository productRepository;
    private final CustomerOrderRepository customerOrderRepository;
    private final CustomerProfileRepository customerProfileRepository;

    public ZohoSyncService(
        ZohoBooksClient booksClient,
        ProductRepository productRepository,
        CustomerOrderRepository customerOrderRepository,
        CustomerProfileRepository customerProfileRepository
    ) {
        this.booksClient = booksClient;
        this.productRepository = productRepository;
        this.customerOrderRepository = customerOrderRepository;
        this.customerProfileRepository = customerProfileRepository;
    }

    public Map<String, Object> testConnection() {
        return booksClient.testConnection();
    }

    @Async
    public void syncProductAsync(Long productId) {
        syncProduct(productId);
    }

    public void syncProduct(Long productId) {
        productRepository
            .findOneWithEagerRelationships(productId)
            .ifPresent(product -> {
                try {
                    pushProductToZoho(product);
                } catch (Exception e) {
                    LOG.warn("Zoho product sync failed for {}: {}", productId, e.getMessage());
                    productRepository.updateZohoSyncFailure(productId, ZohoSyncStatus.FAILED, e.getMessage());
                }
            });
    }

    public void deactivateProduct(Long productId) {
        productRepository
            .findOneWithEagerRelationships(productId)
            .ifPresent(product -> {
                try {
                    if (isZohoLinked(product)) {
                        deactivateInZoho(product);
                    }
                } catch (Exception e) {
                    LOG.warn("Zoho deactivation failed for {}: {}", productId, e.getMessage());
                }
            });
    }

    public Map<String, Object> syncAllProducts() {
        ZohoSyncSummary.Builder builder = ZohoSyncSummary.builder();
        for (Product product : productRepository.findAllWithEagerRelationships()) {
            try {
                if (StringUtils.isBlank(product.getSku())) {
                    builder.notice(product.getName(), "Skipped — SKU required");
                    continue;
                }
                if (!Boolean.TRUE.equals(product.getActive())) {
                    if (isZohoLinked(product)) {
                        deactivateInZoho(product);
                        builder.notice(product.getSku(), "Deactivated in Zoho");
                    }
                    continue;
                }
                pushProductToZoho(product);
                builder.success();
            } catch (Exception e) {
                productRepository.updateZohoSyncFailure(product.getId(), ZohoSyncStatus.FAILED, e.getMessage());
                builder.failure(product.getId(), product.getName(), e.getMessage());
            }
        }
        return builder.build().toMap();
    }

    private void pushProductToZoho(Product product) {
        if (StringUtils.isBlank(product.getSku())) {
            throw new IllegalStateException("SKU is required for Zoho sync");
        }
        if (!Boolean.TRUE.equals(product.getActive())) {
            if (isZohoLinked(product)) {
                deactivateInZoho(product);
            }
            return;
        }
        Map<String, Object> existing = StringUtils.isNotBlank(product.getZohoItemId())
            ? null
            : booksClient.findItemBySku(product.getSku());
        String itemId = StringUtils.isNotBlank(product.getZohoItemId())
            ? product.getZohoItemId()
            : existing != null
                ? stringValue(existing.get("item_id"))
                : null;

        Map<String, Object> payload = buildItemPayload(product);
        Map<String, Object> response;
        if (StringUtils.isNotBlank(itemId)) {
            response = booksClient.updateItem(itemId, payload);
        } else {
            response = booksClient.createItem(payload);
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> item = (Map<String, Object>) response.getOrDefault("item", response);
        String newItemId = stringValue(item.get("item_id"));
        if (StringUtils.isBlank(newItemId)) {
            newItemId = itemId;
        }
        if (StringUtils.isBlank(newItemId)) {
            throw new IllegalStateException("Zoho did not return an item id");
        }
        productRepository.updateZohoSyncSuccess(product.getId(), newItemId, ZohoSyncStatus.SYNCED, Instant.now());
    }

    private void deactivateInZoho(Product product) {
        if (StringUtils.isNotBlank(product.getZohoItemId())) {
            booksClient.markItemInactive(product.getZohoItemId());
        }
        productRepository.updateZohoSyncFailure(product.getId(), ZohoSyncStatus.SYNCED, null);
    }

    private static boolean isZohoLinked(Product product) {
        return StringUtils.isNotBlank(product.getZohoItemId()) || product.getZohoSyncStatus() == ZohoSyncStatus.SYNCED;
    }

    private Map<String, Object> buildItemPayload(Product product) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", product.getName());
        payload.put("sku", product.getSku());
        BigDecimal sellingPrice = product.getPrice() != null && product.getPrice().compareTo(BigDecimal.ZERO) > 0
            ? product.getPrice()
            : BigDecimal.ZERO;
        payload.put("rate", sellingPrice);
        payload.put("item_type", "inventory");
        payload.put("product_type", "goods");
        payload.put("unit", "pcs");
        if (StringUtils.isNotBlank(product.getDescription())) {
            payload.put("description", product.getDescription().trim());
        }
        if (product.getStock() != null) {
            payload.put("initial_stock", product.getStock());
            payload.put("initial_stock_rate", sellingPrice);
        }
        return payload;
    }

    public Map<String, Object> pullProducts() {
        ZohoSyncSummary.Builder builder = ZohoSyncSummary.builder();
        Set<String> pulledSkus = new HashSet<>();
        int page = 1;
        List<Map<String, Object>> items;
        do {
            items = booksClient.listItems(page);
            for (Map<String, Object> item : items) {
                String sku = stringValue(item.get("sku"));
                if (StringUtils.isBlank(sku)) {
                    builder.notice("missing-sku", "Zoho item has no SKU — skipped");
                    continue;
                }
                pulledSkus.add(sku.trim());
                try {
                    upsertPulledProduct(item, builder);
                } catch (Exception e) {
                    builder.failure(sku, e.getMessage());
                }
            }
            page++;
        } while (!items.isEmpty());
        if (!pulledSkus.isEmpty()) {
            deactivateStaleZohoProducts(pulledSkus, builder);
        }
        return builder.build().toMap();
    }

    private void upsertPulledProduct(Map<String, Object> item, ZohoSyncSummary.Builder builder) {
        String sku = stringValue(item.get("sku"));
        String itemId = stringValue(item.get("item_id"));
        Product product = productRepository
            .findOneBySku(sku)
            .or(() -> StringUtils.isNotBlank(itemId) ? productRepository.findOneByZohoItemId(itemId) : Optional.empty())
            .orElseGet(Product::new);

        boolean created = product.getId() == null;
        if (created) {
            product.setSlug(uniqueSlug(sku, stringValue(item.get("name"))));
            product.setName(stringValue(item.get("name")) != null ? stringValue(item.get("name")) : sku);
            product.setSku(sku);
            product.setCurrency("INR");
            product.setSortOrder(productRepository.findMaxSortOrder() + 1);
            product.setFeatured(false);
            product.setTrending(false);
            product.setNewArrival(false);
            product.setBestSeller(false);
            product.setStock(0);
            product.setPrice(BigDecimal.ZERO);
        }
        product.setActive(true);
        if (StringUtils.isNotBlank(itemId)) {
            product.setZohoItemId(itemId);
        }
        String name = stringValue(item.get("name"));
        if (StringUtils.isNotBlank(name)) {
            product.setName(name.trim());
        }
        String description = stringValue(item.get("description"));
        if (StringUtils.isNotBlank(description)) {
            product.setDescription(description.trim());
        }
        Object rate = item.get("rate");
        if (rate != null) {
            product.setPrice(toBigDecimal(rate));
        }
        Object stockOnHand = item.get("stock_on_hand");
        if (stockOnHand != null) {
            product.setStock(toInteger(stockOnHand));
        }
        product.setZohoSyncStatus(ZohoSyncStatus.SYNCED);
        product.setZohoLastSyncedAt(Instant.now());
        product.setZohoSyncError(null);
        productRepository.save(product);
        if (created) {
            builder.notice(sku, "Created from Zoho pull");
        }
        builder.success();
    }

    private void deactivateStaleZohoProducts(Set<String> pulledSkus, ZohoSyncSummary.Builder builder) {
        for (Product product : productRepository.findAllByZohoItemIdIsNotNull()) {
            String sku = StringUtils.trimToNull(product.getSku());
            if (sku != null && !pulledSkus.contains(sku) && Boolean.TRUE.equals(product.getActive())) {
                product.setActive(false);
                product.setZohoSyncError("No longer in Zoho catalog (deactivated on pull)");
                productRepository.save(product);
                builder.notice(sku, "Deactivated — removed from Zoho");
            }
        }
    }

    public Map<String, Object> pullInventory() {
        ZohoSyncSummary.Builder builder = ZohoSyncSummary.builder();
        try {
            int page = 1;
            List<Map<String, Object>> items;
            do {
                items = booksClient.listItems(page);
                for (Map<String, Object> item : items) {
                    String sku = stringValue(item.get("sku"));
                    String itemId = stringValue(item.get("item_id"));
                    try {
                        Optional<Product> product = StringUtils.isNotBlank(itemId)
                            ? productRepository.findOneByZohoItemId(itemId)
                            : Optional.empty();
                        if (product.isEmpty() && StringUtils.isNotBlank(sku)) {
                            product = productRepository.findOneBySku(sku);
                        }
                        if (product.isPresent()) {
                            Product p = product.orElseThrow();
                            p.setStock(toInteger(item.get("stock_on_hand")));
                            productRepository.save(p);
                            builder.success();
                        } else {
                            builder.skipped(sku != null ? sku : itemId, "No linked store product for SKU or Zoho item id");
                        }
                    } catch (Exception e) {
                        builder.failure(sku != null ? sku : itemId, e.getMessage());
                    }
                }
                page++;
            } while (!items.isEmpty());
        } catch (RestClientResponseException ex) {
            String message = "Zoho inventory pull failed (HTTP " + ex.getStatusCode().value() + "): " + StringUtils.defaultString(ex.getStatusText());
            LOG.warn(message);
            return ZohoSyncSummary.builder().failure(message).build().toMap();
        } catch (ResourceAccessException ex) {
            String message = "Zoho unreachable: " + ex.getMessage();
            LOG.warn(message);
            return ZohoSyncSummary.builder().failure(message).build().toMap();
        }
        return builder.build().toMap();
    }

    public void syncOrder(Long orderId) {
        customerOrderRepository
            .findById(orderId)
            .ifPresent(order -> {
                try {
                    String customerId = resolveOrCreateContact(order);
                    String salesOrderId = order.getZohoSalesOrderId();
                    if (StringUtils.isBlank(salesOrderId)) {
                        Map<String, Object> salesOrderResponse = booksClient.createSalesOrder(buildSalesOrderPayload(order, customerId));
                        @SuppressWarnings("unchecked")
                        Map<String, Object> salesOrder = (Map<String, Object>) salesOrderResponse.getOrDefault(
                            "salesorder",
                            salesOrderResponse
                        );
                        salesOrderId = stringValue(salesOrder.get("salesorder_id"));
                        order.setZohoSalesOrderId(salesOrderId);
                    }
                    if (StringUtils.isBlank(order.getZohoInvoiceId()) && StringUtils.isNotBlank(salesOrderId)) {
                        Map<String, Object> invoiceResponse = booksClient.createInvoiceFromSalesOrder(salesOrderId);
                        @SuppressWarnings("unchecked")
                        Map<String, Object> invoice = (Map<String, Object>) invoiceResponse.getOrDefault("invoice", invoiceResponse);
                        String invoiceId = stringValue(invoice.get("invoice_id"));
                        if (StringUtils.isNotBlank(invoiceId)) {
                            order.setZohoInvoiceId(invoiceId);
                        }
                    }
                    order.setZohoSyncStatus(ZohoSyncStatus.SYNCED);
                    order.setZohoLastSyncedAt(Instant.now());
                    order.setZohoSyncError(null);
                    customerOrderRepository.save(order);
                } catch (Exception e) {
                    LOG.warn("Zoho order sync failed for {}: {}", orderId, e.getMessage());
                    order.setZohoSyncStatus(ZohoSyncStatus.FAILED);
                    order.setZohoSyncError(e.getMessage());
                    customerOrderRepository.save(order);
                }
            });
    }

    private String resolveOrCreateContact(CustomerOrder order) {
        String email = order.getUserLogin();
        Map<String, Object> existing = StringUtils.isNotBlank(email) ? booksClient.findContactByEmail(email) : null;
        if (existing != null) {
            return stringValue(existing.get("contact_id"));
        }
        CustomerProfile profile = customerProfileRepository.findOneByUserLogin(email).orElse(null);
        String name = profile != null && StringUtils.isNotBlank(profile.getFullName()) ? profile.getFullName() : email;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("contact_name", name);
        Map<String, Object> contactPerson = new LinkedHashMap<>();
        contactPerson.put("email", email);
        if (profile != null && StringUtils.isNotBlank(profile.getPhone())) {
            contactPerson.put("phone", profile.getPhone());
        }
        payload.put("contact_persons", List.of(contactPerson));
        Map<String, Object> response = booksClient.createContact(payload);
        @SuppressWarnings("unchecked")
        Map<String, Object> contact = (Map<String, Object>) response.getOrDefault("contact", response);
        return stringValue(contact.get("contact_id"));
    }

    private Map<String, Object> buildSalesOrderPayload(CustomerOrder order, String customerId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("customer_id", customerId);
        payload.put("reference_number", order.getOrderNumber());
        List<Map<String, Object>> lineItems = new ArrayList<>();
        for (OrderItem item : order.getItemses()) {
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("name", item.getProductName());
            line.put("rate", item.getPrice());
            line.put("quantity", item.getQty());
            lineItems.add(line);
        }
        payload.put("line_items", lineItems);
        return payload;
    }

    public Map<String, Object> retryFailedOrders() {
        Page<CustomerOrder> page = customerOrderRepository.findPaidOrdersForZohoRetry(
            PaymentStatus.PAID,
            List.of(ZohoSyncStatus.FAILED, ZohoSyncStatus.PENDING),
            PageRequest.of(0, 200)
        );
        ZohoSyncSummary.Builder builder = ZohoSyncSummary.builder();
        for (CustomerOrder order : page.getContent()) {
            try {
                syncOrder(order.getId());
                builder.success();
            } catch (Exception e) {
                builder.orderFailure(order.getOrderNumber(), e.getMessage());
            }
        }
        return builder.build().toMap();
    }

    /**
     * Zoho Books has no shipment-tracking API; "tracking pull" here means refreshing the invoice
     * payment status (paid/overdue/void) into {@code zohoOrderStatus}.
     */
    public void pullOrderTracking(Long orderId) {
        customerOrderRepository
            .findById(orderId)
            .ifPresent(order -> {
                if (StringUtils.isBlank(order.getZohoInvoiceId())) {
                    return;
                }
                try {
                    Map<String, Object> response = booksClient.getInvoice(order.getZohoInvoiceId());
                    @SuppressWarnings("unchecked")
                    Map<String, Object> invoice = (Map<String, Object>) response.getOrDefault("invoice", response);
                    String status = stringValue(invoice.get("status"));
                    if (StringUtils.isNotBlank(status)) {
                        order.setZohoOrderStatus(status);
                    }
                    order.setZohoTrackingLastPulledAt(Instant.now());
                    customerOrderRepository.save(order);
                } catch (Exception e) {
                    LOG.warn("Zoho invoice status pull failed for {}: {}", orderId, e.getMessage());
                }
            });
    }

    public Map<String, Object> pullAllTracking() {
        ZohoSyncSummary.Builder builder = ZohoSyncSummary.builder();
        Page<CustomerOrder> page = customerOrderRepository.findOrdersForZohoTrackingPull(
            PaymentStatus.PAID,
            List.of(OrderStatus.PENDING, OrderStatus.CONFIRMED),
            PageRequest.of(0, 3)
        );
        for (CustomerOrder order : page.getContent()) {
            try {
                pullOrderTracking(order.getId());
                builder.success();
            } catch (Exception e) {
                builder.orderFailure(order.getOrderNumber(), e.getMessage());
            }
        }
        builder.notice("batch", "Pulled Zoho invoice status for " + page.getNumberOfElements() + " open paid orders (max 3)");
        return builder.build().toMap();
    }

    public Optional<String> createCreditNote(Long orderId, String returnNumber) {
        Optional<CustomerOrder> orderOpt = customerOrderRepository.findById(orderId);
        if (orderOpt.isEmpty()) {
            return Optional.empty();
        }
        CustomerOrder order = orderOpt.orElseThrow();
        String customerId = resolveOrCreateContact(order);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("customer_id", customerId);
        payload.put("reference_number", returnNumber);
        List<Map<String, Object>> lineItems = new ArrayList<>();
        for (OrderItem item : order.getItemses()) {
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("name", item.getProductName());
            line.put("rate", item.getPrice());
            line.put("quantity", item.getQty());
            lineItems.add(line);
        }
        payload.put("line_items", lineItems);
        Map<String, Object> response = booksClient.createCreditNote(payload);
        @SuppressWarnings("unchecked")
        Map<String, Object> creditNote = (Map<String, Object>) response.getOrDefault("creditnote", response);
        String creditNoteId = stringValue(creditNote.get("creditnote_id"));
        return Optional.ofNullable(creditNoteId).filter(StringUtils::isNotBlank);
    }

    private static final java.util.regex.Pattern NON_SLUG = java.util.regex.Pattern.compile("[^a-z0-9]+");

    private String uniqueSlug(String sku, String name) {
        String base = slugify(name != null && !name.isBlank() ? name : sku);
        String candidate = base;
        int suffix = 1;
        while (productRepository.findOneBySlug(candidate).isPresent()) {
            candidate = base + "-" + suffix++;
        }
        return candidate;
    }

    private static String slugify(String value) {
        if (value == null || value.isBlank()) {
            return "item";
        }
        String normalized = NON_SLUG.matcher(value.toLowerCase(java.util.Locale.ROOT).trim()).replaceAll("-");
        normalized = normalized.replaceAll("^-+|-+$", "");
        return normalized.isBlank() ? "item" : normalized;
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        return new BigDecimal(value.toString());
    }

    private int toInteger(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return new BigDecimal(value.toString()).intValue();
    }
}
