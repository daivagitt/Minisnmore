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
import com.miniandmore.app.service.ZohoSyncService;
import com.miniandmore.app.web.rest.v1.AccountOrderDataAccess;
import com.miniandmore.app.web.rest.v1.ApiResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
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

    private final AccountOrderDataAccess dataAccess;
    private final LedgerlySyncService ledgerlySyncService;
    private final ZohoSyncService zohoSyncService;
    private final ObjectMapper objectMapper;
    private final OrderEventService orderEventService;

    public InternalAdminOrderResource(
        AccountOrderDataAccess dataAccess,
        LedgerlySyncService ledgerlySyncService,
        ZohoSyncService zohoSyncService,
        ObjectMapper objectMapper,
        OrderEventService orderEventService
    ) {
        this.dataAccess = dataAccess;
        this.ledgerlySyncService = ledgerlySyncService;
        this.zohoSyncService = zohoSyncService;
        this.objectMapper = objectMapper;
        this.orderEventService = orderEventService;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> listOrders(@RequestParam Instant from, @RequestParam Instant to) {
        if (!from.isBefore(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The order date range is invalid");
        }
        if (Duration.between(from, to).compareTo(MAX_ORDER_RANGE) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order date range cannot exceed 31 days");
        }
        var orders = dataAccess
            .customerOrderRepository()
            .findAllByPlacedAtGreaterThanEqualAndPlacedAtLessThanOrderByPlacedAtDesc(from, to)
            .stream()
            .map(this::summary)
            .toList();
        return ApiResponse.ok(Map.of("orders", orders));
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
        order.setLedgerlySyncStatus(LedgerlySyncStatus.PENDING);
        order.setZohoSyncStatus(ZohoSyncStatus.PENDING);
        CustomerOrder saved = dataAccess.customerOrderRepository().save(order);
        orderEventService.onOrderPaid(saved);
        ledgerlySyncService.syncOrder(saved.getId());
        zohoSyncService.syncOrder(saved.getId());
        return ApiResponse.ok(Map.of("ok", true, "orderNumber", saved.getOrderNumber(), "paymentStatus", "paid"));
    }

    private Map<String, Object> summary(CustomerOrder order) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", order.getId());
        data.put("order_number", order.getOrderNumber());
        data.put("total", order.getTotal());
        data.put("cod_fee", order.getCodFee());
        data.put("status", order.getStatus() == null ? "" : order.getStatus().name().toLowerCase());
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
        (ZohoSyncService.isCodOrder(order) && order.getStatus() == OrderStatus.CONFIRMED);
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
