package com.miniandmore.app.service;

import com.miniandmore.app.client.LedgerlyIntegrationClient;
import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.OrderItem;
import com.miniandmore.app.domain.Product;
import com.miniandmore.app.domain.enumeration.ZohoSyncStatus;
import com.miniandmore.app.repository.CustomerOrderRepository;
import com.miniandmore.app.repository.ProductRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class LedgerlySyncService {

    private static final Logger LOG = LoggerFactory.getLogger(LedgerlySyncService.class);

    private final LedgerlyIntegrationClient ledgerlyClient;
    private final ProductRepository productRepository;
    private final CustomerOrderRepository customerOrderRepository;

    public LedgerlySyncService(
        LedgerlyIntegrationClient ledgerlyClient,
        ProductRepository productRepository,
        CustomerOrderRepository customerOrderRepository
    ) {
        this.ledgerlyClient = ledgerlyClient;
        this.productRepository = productRepository;
        this.customerOrderRepository = customerOrderRepository;
    }

    @Async
    public void syncProductAsync(Long productId) {
        syncProduct(productId);
    }

    public void syncProduct(Long productId) {
        productRepository
            .findById(productId)
            .ifPresent(product -> {
                try {
                    Map<String, Object> response = ledgerlyClient.syncProductPayload(buildProductPayload(product));
                    product.setZohoItemId(String.valueOf(response.getOrDefault("ledgerlyItemId", "")));
                    product.setZohoSyncStatus(ZohoSyncStatus.SYNCED);
                    product.setZohoLastSyncedAt(Instant.now());
                    product.setZohoSyncError(null);
                    productRepository.save(product);
                } catch (Exception e) {
                    LOG.warn("Ledgerly product sync failed for {}: {}", productId, e.getMessage());
                    product.setZohoSyncStatus(ZohoSyncStatus.FAILED);
                    product.setZohoSyncError(e.getMessage());
                    productRepository.save(product);
                }
            });
    }

    public void syncOrder(Long orderId) {
        customerOrderRepository
            .findById(orderId)
            .ifPresent(order -> {
                try {
                    Map<String, Object> response = ledgerlyClient.syncOrderPayload(
                        buildOrderPayload(order),
                        order.getRazorpayPaymentId()
                    );
                    order.setZohoInvoiceId(String.valueOf(response.getOrDefault("ledgerlyInvoiceId", "")));
                    order.setZohoSalesOrderId(String.valueOf(response.getOrDefault("ledgerlySalesOrderId", order.getOrderNumber())));
                    order.setZohoSyncStatus(ZohoSyncStatus.SYNCED);
                    order.setZohoLastSyncedAt(Instant.now());
                    order.setZohoSyncError(null);
                    customerOrderRepository.save(order);
                } catch (Exception e) {
                    LOG.warn("Ledgerly order sync failed for {}: {}", orderId, e.getMessage());
                    order.setZohoSyncStatus(ZohoSyncStatus.FAILED);
                    order.setZohoSyncError(e.getMessage());
                    customerOrderRepository.save(order);
                }
            });
    }

    public void pullOrderTracking(Long orderId) {
        customerOrderRepository
            .findById(orderId)
            .ifPresent(order -> {
                try {
                    Map<String, Object> response = ledgerlyClient.pullOrderTracking(order.getOrderNumber());
                    if (Boolean.TRUE.equals(response.get("found"))) {
                        order.setZohoOrderStatus(String.valueOf(response.getOrDefault("status", "")));
                        order.setZohoTrackingLastPulledAt(Instant.now());
                        customerOrderRepository.save(order);
                    }
                } catch (Exception e) {
                    LOG.warn("Ledgerly tracking pull failed for {}: {}", orderId, e.getMessage());
                }
            });
    }

    public Map<String, Object> syncAllProducts() {
        List<Map<String, Object>> failures = new ArrayList<>();
        int ok = 0;
        for (Product product : productRepository.findAll()) {
            try {
                syncProduct(product.getId());
                ok++;
            } catch (Exception e) {
                failures.add(Map.of("id", product.getId(), "message", e.getMessage()));
            }
        }
        return Map.of("successCount", ok, "failureCount", failures.size(), "failures", failures);
    }

    public Map<String, Object> pullProducts() {
        return ledgerlyClient.pullProducts();
    }

    public Map<String, Object> pullInventory() {
        return ledgerlyClient.pullInventory();
    }

    public Map<String, Object> retryFailedOrders() {
        List<CustomerOrder> orders = customerOrderRepository.findAll()
            .stream()
            .filter(o -> o.getZohoSyncStatus() == ZohoSyncStatus.FAILED || o.getZohoSyncStatus() == ZohoSyncStatus.PENDING)
            .toList();
        int ok = 0;
        List<Map<String, Object>> failures = new ArrayList<>();
        for (CustomerOrder order : orders) {
            try {
                syncOrder(order.getId());
                ok++;
            } catch (Exception e) {
                failures.add(Map.of("orderNumber", order.getOrderNumber(), "message", e.getMessage()));
            }
        }
        return Map.of("successCount", ok, "failureCount", failures.size(), "failures", failures);
    }

    public Map<String, Object> pullAllTracking() {
        int ok = 0;
        List<Map<String, Object>> failures = new ArrayList<>();
        for (CustomerOrder order : customerOrderRepository.findAll()) {
            try {
                pullOrderTracking(order.getId());
                ok++;
            } catch (Exception e) {
                failures.add(Map.of("orderNumber", order.getOrderNumber(), "message", e.getMessage()));
            }
        }
        return Map.of("successCount", ok, "failureCount", failures.size(), "failures", failures);
    }

    private Map<String, Object> buildProductPayload(Product product) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sku", product.getSku() != null ? product.getSku() : "P-" + product.getId());
        payload.put("name", product.getName());
        payload.put("price", product.getPrice());
        payload.put("stock", product.getStock());
        payload.put("taxPct", BigDecimal.valueOf(5));
        if (product.getDescription() != null) {
            payload.put("description", product.getDescription());
        }
        return payload;
    }

    private Map<String, Object> buildOrderPayload(CustomerOrder order) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("externalRef", order.getOrderNumber());
        payload.put("total", order.getTotal());
        payload.put("paymentId", order.getRazorpayPaymentId());
        payload.put("currency", "INR");
        Map<String, Object> customer = new LinkedHashMap<>();
        customer.put("name", order.getUserLogin());
        customer.put("email", order.getUserLogin());
        customer.put("shippingAddress", order.getShippingAddressJson());
        payload.put("customer", customer);
        List<Map<String, Object>> lines = new ArrayList<>();
        for (OrderItem item : order.getItemses()) {
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("sku", item.getProductSlug() != null ? item.getProductSlug() : item.getProductId());
            line.put("qty", item.getQty());
            line.put("rate", item.getPrice());
            line.put("taxPct", BigDecimal.valueOf(5));
            line.put("description", item.getProductName());
            lines.add(line);
        }
        payload.put("lines", lines);
        return payload;
    }
}
