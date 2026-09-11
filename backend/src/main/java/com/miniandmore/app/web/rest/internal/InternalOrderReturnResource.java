package com.miniandmore.app.web.rest.internal;

import com.miniandmore.app.domain.OrderItem;
import com.miniandmore.app.domain.OrderReturn;
import com.miniandmore.app.domain.OrderReturnItem;
import com.miniandmore.app.security.SecurityUtils;
import com.miniandmore.app.service.OrderReturnService;
import com.miniandmore.app.web.rest.v1.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/internal/v1/order-returns")
public class InternalOrderReturnResource {

    private final OrderReturnService orderReturnService;

    public InternalOrderReturnResource(OrderReturnService orderReturnService) {
        this.orderReturnService = orderReturnService;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> listMine() {
        String login = requireLogin();
        return ApiResponse.ok(orderReturnService.listForUser(login).stream().map(this::toDto).toList());
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> requestReturn(@Valid @RequestBody CreateReturnRequest request) {
        String login = requireLogin();
        List<OrderReturnService.ReturnLineSelection> lines = request.items() == null
            ? List.of()
            : request.items().stream().map(item -> new OrderReturnService.ReturnLineSelection(item.order_item_id(), item.quantity())).toList();
        OrderReturn orderReturn = lines.isEmpty()
            ? orderReturnService.requestReturn(login, request.order_number(), request.reason())
            : orderReturnService.requestReturn(login, request.order_number(), request.reason(), lines);
        return ApiResponse.ok(toDto(orderReturn));
    }

    @PostMapping("/exchange")
    public ApiResponse<Map<String, Object>> requestExchange(@Valid @RequestBody CreateExchangeRequest request) {
        String login = requireLogin();
        OrderReturn orderReturn = orderReturnService.requestExchange(
            login,
            request.order_number(),
            request.order_item_id(),
            request.reason(),
            request.replacement_size(),
            request.replacement_color()
        );
        return ApiResponse.ok(toDto(orderReturn));
    }

    @GetMapping("/exchange-availability/{orderItemId}")
    public ApiResponse<Map<String, Object>> checkExchangeAvailability(
        @PathVariable Long orderItemId,
        @RequestParam String size,
        @RequestParam String color
    ) {
        String login = requireLogin();
        boolean sellable = orderReturnService.isReplacementSellable(login, orderItemId, size, color);
        var result = new LinkedHashMap<String, Object>();
        result.put("sellable", sellable);
        return ApiResponse.ok(result);
    }

    @GetMapping("/config")
    public ApiResponse<Map<String, Object>> config() {
        return ApiResponse.ok(orderReturnService.publicConfig());
    }

    @PostMapping("/{returnNumber}/cancel")
    public ApiResponse<Map<String, Object>> cancel(@PathVariable String returnNumber) {
        String login = requireLogin();
        return ApiResponse.ok(toDto(orderReturnService.cancelByCustomer(login, returnNumber)));
    }

    @GetMapping("/eligibility/{orderNumber}")
    public ApiResponse<Map<String, Object>> checkEligibility(@PathVariable String orderNumber) {
        String login = requireLogin();
        return ApiResponse.ok(orderReturnService.eligibilityForUser(login, orderNumber));
    }

    private String requireLogin() {
        return SecurityUtils.getCurrentUserLogin().orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    private Map<String, Object> toDto(OrderReturn orderReturn) {
        var data = new LinkedHashMap<String, Object>();
        data.put("id", orderReturn.getId());
        data.put("return_number", orderReturn.getReturnNumber());
        data.put("status", orderReturn.getStatus().name().toLowerCase());
        data.put("request_type", orderReturn.getRequestType().name().toLowerCase());
        data.put("reason", orderReturn.getReason());
        data.put("rejection_reason", orderReturn.getRejectionReason());
        data.put("refund_amount", orderReturn.getRefundAmount());
        data.put("requested_at", orderReturn.getRequestedAt());
        data.put("resolved_at", orderReturn.getResolvedAt());
        if (orderReturn.getOrder() != null) {
            data.put("order_number", orderReturn.getOrder().getOrderNumber());
            data.put("order_total", orderReturn.getOrder().getTotal());
        }
        OrderItem item = orderReturn.getOrderItem();
        if (item != null) {
            data.put("order_item_id", item.getId());
            data.put("product_name", item.getProductName());
            data.put("product_slug", item.getProductSlug());
            data.put("original_size", item.getItemSize());
            data.put("original_color", item.getColor());
        }
        data.put("replacement_size", orderReturn.getReplacementSize());
        data.put("replacement_color", orderReturn.getReplacementColor());
        data.put("outbound_awb", orderReturn.getOutboundAwb());
        data.put("outbound_tracking_url", orderReturn.getOutboundTrackingUrl());
        data.put("original_item_amount", orderReturn.getOriginalItemAmount());
        data.put("replacement_item_amount", orderReturn.getReplacementItemAmount());
        data.put("price_difference", orderReturn.getPriceDifference());
        data.put(
            "settlement_status",
            orderReturn.getSettlementStatus() == null ? null : orderReturn.getSettlementStatus().name().toLowerCase()
        );
        data.put("items", orderReturnService.returnLineBreakdown(orderReturn).stream().map(this::toLineDto).toList());
        return data;
    }

    private Map<String, Object> toLineDto(OrderReturnItem line) {
        var data = new LinkedHashMap<String, Object>();
        data.put("id", line.getId());
        data.put("order_item_id", line.getOrderItem().getId());
        data.put("product_name", line.getOrderItem().getProductName());
        data.put("size", line.getOrderItem().getItemSize());
        data.put("color", line.getOrderItem().getColor());
        data.put("requested_qty", line.getRequestedQty());
        data.put("accepted_qty", line.getAcceptedQty());
        data.put("unit_amount", line.getUnitAmount());
        data.put("gross_amount", line.getGrossAmount());
        data.put("discount_allocation", line.getDiscountAllocation());
        data.put("refund_amount", line.getRefundAmount());
        data.put("disposition", line.getDisposition() == null ? null : line.getDisposition().name().toLowerCase());
        return data;
    }

    public record CreateReturnRequest(
        @NotBlank @Size(max = 40) String order_number,
        @NotBlank @Size(min = 10, max = 4000) String reason,
        @Valid List<ReturnLineRequest> items
    ) {
        public CreateReturnRequest(String order_number, String reason) {
            this(order_number, reason, List.of());
        }
    }

    public record ReturnLineRequest(@NotNull Long order_item_id, @NotNull @Min(1) Integer quantity) {}

    public record CreateExchangeRequest(
        @NotBlank @Size(max = 40) String order_number,
        @NotNull Long order_item_id,
        @NotBlank @Size(min = 10, max = 4000) String reason,
        @NotBlank @Size(max = 40) String replacement_size,
        @NotBlank @Size(max = 40) String replacement_color
    ) {}
}
