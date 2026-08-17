package com.miniandmore.app.web.rest.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.enumeration.LedgerlySyncStatus;
import com.miniandmore.app.domain.enumeration.PaymentStatus;
import com.miniandmore.app.domain.enumeration.ZohoSyncStatus;
import com.miniandmore.app.service.LedgerlySyncService;
import com.miniandmore.app.service.OrderEventService;
import com.miniandmore.app.service.ZohoSyncService;
import com.miniandmore.app.web.rest.v1.AccountOrderDataAccess;
import com.miniandmore.app.web.rest.v1.ApiResponse;
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
        data.put("placed_at", order.getPlacedAt() == null ? "" : order.getPlacedAt().toString());
        JsonNode address = readShippingAddress(order.getShippingAddressJson());
        data.put("full_name", address.path("full_name").asText(""));
        data.put("phone", address.path("phone").asText(""));
        data.put("city", address.path("city").asText(""));
        return data;
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
