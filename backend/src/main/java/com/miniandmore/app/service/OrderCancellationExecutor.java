package com.miniandmore.app.service;

import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.OrderItem;
import com.miniandmore.app.domain.Product;
import com.miniandmore.app.domain.enumeration.OrderStatus;
import com.miniandmore.app.domain.enumeration.PaymentStatus;
import com.miniandmore.app.web.rest.v1.AccountOrderDataAccess;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/**
 * Executes an approved order cancellation: refund (when applicable), inventory release, and accounting sync.
 */
@Service
public class OrderCancellationExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(OrderCancellationExecutor.class);

    private final AccountOrderDataAccess dataAccess;
    private final RazorpayRefundService razorpayRefundService;
    private final ZohoSyncService zohoSyncService;
    private final OrderEventService orderEventService;
    private final ProductVariantSupport variantSupport;
    private final TransactionTemplate inventoryRestoreTransaction;

    public OrderCancellationExecutor(
        AccountOrderDataAccess dataAccess,
        RazorpayRefundService razorpayRefundService,
        ZohoSyncService zohoSyncService,
        OrderEventService orderEventService,
        ProductVariantSupport variantSupport,
        PlatformTransactionManager transactionManager
    ) {
        this.dataAccess = dataAccess;
        this.razorpayRefundService = razorpayRefundService;
        this.zohoSyncService = zohoSyncService;
        this.orderEventService = orderEventService;
        this.variantSupport = variantSupport;
        this.inventoryRestoreTransaction = new TransactionTemplate(transactionManager);
    }

    public String performCancellation(CustomerOrder order, String reason) {
        if (order.getStatus() == OrderStatus.CANCELLED) {
            return null;
        }
        boolean onlinePayment =
            "online".equalsIgnoreCase(order.getPaymentMethod()) && StringUtils.isNotBlank(order.getRazorpayPaymentId());
        String refundId = null;
        if (order.getPaymentStatus() == PaymentStatus.PAID && onlinePayment) {
            refundId = razorpayRefundService.refundPayment(order, order.getTotal());
            order.setPaymentStatus(PaymentStatus.REFUNDED);
            order = dataAccess.customerOrderRepository().save(order);
        }
        CustomerOrder orderForInventoryRestore = order;
        try {
            inventoryRestoreTransaction.executeWithoutResult(status -> restoreInventoryForCancellation(orderForInventoryRestore));
        } catch (RuntimeException ex) {
            LOG.warn("Inventory restore failed while cancelling {}: {}", order.getOrderNumber(), ex.getMessage());
        }
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancellationReason(StringUtils.normalizeSpace(reason).trim());
        CustomerOrder cancelled = dataAccess.customerOrderRepository().save(order);
        syncCancellationToLedgerly(cancelled, refundId);
        syncCancellationToZoho(cancelled);
        orderEventService.syncLifecycleEvents(cancelled);
        return refundId;
    }

    public void assertCancelEligible(CustomerOrder order) {
        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This order is already cancelled");
        }
        if (order.getStatus() == OrderStatus.FULFILLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This order has already been dispatched and can no longer be cancelled");
        }
        String trackingStatus = OrderTrackingStatusResolver.resolve(order);
        if ("shipped".equals(trackingStatus) || "delivered".equals(trackingStatus)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This order has already been dispatched and can no longer be cancelled");
        }
    }

    private void restoreInventoryForCancellation(CustomerOrder order) {
        if (order.getInventoryReleasedAt() != null) {
            return;
        }
        if (!Boolean.TRUE.equals(order.getInventoryReserved()) && order.getPaymentStatus() != PaymentStatus.REFUNDED) {
            return;
        }
        order.setInventoryReserved(true);
        releaseInventoryReservation(order);
    }

    private void releaseInventoryReservation(CustomerOrder order) {
        if (!Boolean.TRUE.equals(order.getInventoryReserved()) || order.getInventoryReleasedAt() != null) {
            return;
        }
        List<OrderItem> items = dataAccess.orderItemRepository().findAllByOrderId(order.getId());
        Map<Long, Integer> simpleQtyByProduct = new LinkedHashMap<>();
        for (OrderItem item : items) {
            try {
                Long productId = Long.parseLong(StringUtils.trimToEmpty(item.getProductId()));
                Product product = dataAccess.productRepository().findOneForInventoryUpdate(productId).orElse(null);
                if (product == null) {
                    continue;
                }
                int qty = Optional.ofNullable(item.getQty()).orElse(0);
                if (qty <= 0) {
                    continue;
                }
                if (StringUtils.isNotBlank(item.getVariantSku()) && variantSupport.isVariants(product)) {
                    variantSupport.incrementVariantStock(
                        product,
                        item.getVariantSku(),
                        item.getItemSize(),
                        item.getColor(),
                        qty
                    );
                    dataAccess.productRepository().save(product);
                } else {
                    simpleQtyByProduct.merge(productId, qty, Integer::sum);
                }
            } catch (NumberFormatException ignored) {
                // skip invalid product id
            }
        }
        for (Map.Entry<Long, Integer> entry : simpleQtyByProduct.entrySet()) {
            dataAccess
                .productRepository()
                .findOneForInventoryUpdate(entry.getKey())
                .ifPresent(product -> {
                    product.setStock(Optional.ofNullable(product.getStock()).orElse(0) + entry.getValue());
                    dataAccess.productRepository().save(product);
                });
        }
        order.setInventoryReserved(false);
        order.setInventoryReleasedAt(Instant.now());
        dataAccess.customerOrderRepository().save(order);
    }

    private void syncCancellationToLedgerly(CustomerOrder order, String refundId) {
        if (
            StringUtils.isBlank(refundId) &&
            StringUtils.isBlank(order.getLedgerlySalesOrderId()) &&
            StringUtils.isBlank(order.getLedgerlyInvoiceId())
        ) {
            return;
        }
        try {
            dataAccess.ledgerlyIntegrationClient().cancelOrder(order.getOrderNumber(), null, refundId);
        } catch (RuntimeException ex) {
            LOG.warn("Ledgerly cancellation sync failed for {}: {}", order.getOrderNumber(), ex.getMessage());
        }
    }

    private void syncCancellationToZoho(CustomerOrder order) {
        if (StringUtils.isBlank(order.getZohoSalesOrderId()) && StringUtils.isBlank(order.getZohoInvoiceId())) {
            return;
        }
        try {
            zohoSyncService.cancelOrder(order.getId());
        } catch (RuntimeException ex) {
            LOG.warn("Zoho cancellation sync failed for {}: {}", order.getOrderNumber(), ex.getMessage());
        }
    }
}
