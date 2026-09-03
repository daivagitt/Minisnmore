package com.miniandmore.app.web.rest.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.enumeration.LedgerlySyncStatus;
import com.miniandmore.app.domain.enumeration.OrderStatus;
import com.miniandmore.app.domain.enumeration.PaymentStatus;
import com.miniandmore.app.domain.enumeration.ZohoSyncStatus;
import com.miniandmore.app.service.LedgerlySyncService;
import com.miniandmore.app.service.OrderEventService;
import com.miniandmore.app.service.OrderTrackingStatusResolver;
import com.miniandmore.app.service.ZohoSyncService;
import com.miniandmore.app.web.rest.v1.AccountOrderDataAccess;
import com.miniandmore.app.web.rest.v1.ApiResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * Admin-only actions for Cash on Delivery orders, which have no online payment step and so need a
 * manual "collected" confirmation once the courier hands over the cash/UPI payment at the doorstep.
 * Zoho Books receives the sales order when the COD order is placed; marking collected records the cash payment.
 */
@RestController
@RequestMapping("/internal/v1/admin/orders")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class InternalAdminOrderResource {

    private static final Duration MAX_ORDER_RANGE = Duration.ofDays(31);
    private static final Duration TRACKING_REFRESH_TTL = Duration.ofMinutes(5);
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 50;

    private final AccountOrderDataAccess dataAccess;
    private final LedgerlySyncService ledgerlySyncService;
    private final ZohoSyncService zohoSyncService;
    private final ObjectMapper objectMapper;
    private final OrderEventService orderEventService;
    private final InternalAccountOrderResource accountOrders;

    public InternalAdminOrderResource(
        AccountOrderDataAccess dataAccess,
        LedgerlySyncService ledgerlySyncService,
        ZohoSyncService zohoSyncService,
        ObjectMapper objectMapper,
        OrderEventService orderEventService,
        InternalAccountOrderResource accountOrders
    ) {
        this.dataAccess = dataAccess;
        this.ledgerlySyncService = ledgerlySyncService;
        this.zohoSyncService = zohoSyncService;
        this.objectMapper = objectMapper;
        this.orderEventService = orderEventService;
        this.accountOrders = accountOrders;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> listOrders(
        @RequestParam(required = false) Instant from,
        @RequestParam(required = false) Instant to,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int size,
        @RequestParam(required = false) String q,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String paymentStatus,
        @RequestParam(required = false) String paymentMethod,
        @RequestParam(required = false) String zohoSyncStatus,
        @RequestParam(required = false) String ledgerlySyncStatus
    ) {
        boolean hasRange = from != null || to != null;
        if (hasRange) {
            if (from == null || to == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Both from and to are required when filtering by date");
            }
            if (!from.isBefore(to)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The order date range is invalid");
            }
            if (Duration.between(from, to).compareTo(MAX_ORDER_RANGE) > 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order date range cannot exceed 31 days");
            }
        }
        int pageSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int pageNumber = Math.max(page, 1);
        Pageable pageable = PageRequest.of(
            pageNumber - 1,
            pageSize,
            Sort.by(Sort.Direction.DESC, "placedAt").and(Sort.by(Sort.Direction.DESC, "id"))
        );
        Page<CustomerOrder> result = dataAccess
            .customerOrderRepository()
            .findAll(
                AdminOrderListSpec.create(from, to, q, status, paymentStatus, paymentMethod, zohoSyncStatus, ledgerlySyncStatus),
                pageable
            );
        if (result.getTotalPages() > 0 && pageNumber > result.getTotalPages()) {
            pageNumber = result.getTotalPages();
            pageable = PageRequest.of(
                pageNumber - 1,
                pageSize,
                Sort.by(Sort.Direction.DESC, "placedAt").and(Sort.by(Sort.Direction.DESC, "id"))
            );
            result = dataAccess
                .customerOrderRepository()
                .findAll(
                    AdminOrderListSpec.create(from, to, q, status, paymentStatus, paymentMethod, zohoSyncStatus, ledgerlySyncStatus),
                    pageable
                );
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orders", result.getContent().stream().map(this::summary).toList());
        payload.put("page", pageNumber);
        payload.put("pageSize", pageSize);
        payload.put("totalItems", result.getTotalElements());
        payload.put("totalPages", Math.max(result.getTotalPages(), 1));
        return ApiResponse.ok(payload);
    }

    @GetMapping("/cod-pending")
    public ApiResponse<Map<String, Object>> codPending() {
        var orders = dataAccess
            .customerOrderRepository()
            .findCodOrdersByPaymentStatus(PaymentStatus.PENDING)
            .stream()
            .map(this::summary)
            .toList();
        return ApiResponse.ok(Map.of("orders", orders));
    }

    @GetMapping("/{orderNumber}")
    public ApiResponse<Map<String, Object>> getOrder(@PathVariable String orderNumber) {
        CustomerOrder order = dataAccess
            .customerOrderRepository()
            .findOneByOrderNumber(orderNumber)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        if (
            order.getStatus() != OrderStatus.FULFILLED &&
            order.getStatus() != OrderStatus.CANCELLED &&
            StringUtils.isNotBlank(order.getZohoSalesOrderId()) &&
            (order.getZohoTrackingLastPulledAt() == null || order.getZohoTrackingLastPulledAt().isBefore(Instant.now().minus(TRACKING_REFRESH_TTL)))
        ) {
            zohoSyncService.pullOrderTracking(order.getId());
            order = dataAccess.customerOrderRepository().findOneByOrderNumber(orderNumber).orElse(order);
        }
        return ApiResponse.ok(Map.of("order", accountOrders.orderDetail(order)));
    }

    @PostMapping("/{orderNumber}/mark-cod-collected")
    @Transactional
    public ApiResponse<Map<String, Object>> markCodCollected(@PathVariable String orderNumber) {
        CustomerOrder order = dataAccess
            .customerOrderRepository()
            .findOneByOrderNumber(orderNumber)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        if (!"cod".equalsIgnoreCase(order.getPaymentMethod())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Not a Cash on Delivery order");
        }
        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Already marked as collected");
        }
        order.setPaymentStatus(PaymentStatus.PAID);
        order.setZohoSyncStatus(ZohoSyncStatus.PENDING);
        CustomerOrder saved = dataAccess.customerOrderRepository().save(order);
        orderEventService.onOrderPaid(saved);
        Long orderId = saved.getId();
        Runnable syncZohoAfterCollection = () -> {
            zohoSyncService.syncOrder(orderId);
            zohoSyncService.pullOrderTracking(orderId);
        };
        // Commit payment_status=PAID before Zoho sync so a Books API failure cannot roll back collection.
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        syncZohoAfterCollection.run();
                    }
                }
            );
        } else {
            syncZohoAfterCollection.run();
        }
        return ApiResponse.ok(Map.of("ok", true, "orderNumber", saved.getOrderNumber(), "paymentStatus", "paid"));
    }

    private Map<String, Object> summary(CustomerOrder order) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", order.getId());
        data.put("order_number", order.getOrderNumber());
        data.put("total", order.getTotal());
        data.put("cod_fee", order.getCodFee());
        data.put("status", order.getStatus() == null ? "" : order.getStatus().name().toLowerCase());
        data.put("tracking_status", OrderTrackingStatusResolver.resolve(order));
        data.put("payment_status", order.getPaymentStatus() == null ? "" : order.getPaymentStatus().name().toLowerCase());
        data.put("payment_method", StringUtils.defaultString(order.getPaymentMethod()).toLowerCase());
        data.put("placed_at", order.getPlacedAt() == null ? "" : order.getPlacedAt().toString());
        data.put("zoho_sync_status", lower(order.getZohoSyncStatus()));
        data.put("zoho_sync_error", StringUtils.defaultString(order.getZohoSyncError()));
        data.put("zoho_last_synced_at", instant(order.getZohoLastSyncedAt()));
        data.put("zoho_sales_order_id", StringUtils.defaultString(order.getZohoSalesOrderId()));
        data.put("zoho_invoice_id", StringUtils.defaultString(order.getZohoInvoiceId()));
        data.put("zoho_sync_eligible", isZohoSyncEligible(order));
        data.put("ledgerly_sync_status", lower(order.getLedgerlySyncStatus()));
        data.put("ledgerly_sync_error", StringUtils.defaultString(order.getLedgerlySyncError()));
        data.put("ledgerly_last_synced_at", instant(order.getLedgerlyLastSyncedAt()));
        data.put("ledgerly_sales_order_id", StringUtils.defaultString(order.getLedgerlySalesOrderId()));
        data.put("ledgerly_invoice_id", StringUtils.defaultString(order.getLedgerlyInvoiceId()));
        data.put("ledgerly_sync_eligible", order.getPaymentStatus() == PaymentStatus.PAID);
        JsonNode address = readShippingAddress(order.getShippingAddressJson());
        data.put("full_name", address.path("full_name").asText(""));
        data.put("phone", address.path("phone").asText(""));
        data.put("city", address.path("city").asText(""));
        return data;
    }

    private static boolean isZohoSyncEligible(CustomerOrder order) {
        return order.getPaymentStatus() == PaymentStatus.PAID ||
        (ZohoSyncService.isCodOrder(order) &&
            (order.getStatus() == OrderStatus.CONFIRMED || order.getStatus() == OrderStatus.HAND_WRAPPED));
    }

    private static String lower(Enum<?> value) {
        return value == null ? "" : value.name().toLowerCase();
    }

    private static String instant(Instant value) {
        return value == null ? "" : value.toString();
    }

    private JsonNode readShippingAddress(String raw) {
        if (StringUtils.isBlank(raw)) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(raw);
        } catch (Exception ex) {
            return objectMapper.createObjectNode();
        }
    }
}
