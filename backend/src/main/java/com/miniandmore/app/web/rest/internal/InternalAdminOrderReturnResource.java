package com.miniandmore.app.web.rest.internal;

import com.miniandmore.app.domain.OrderItem;
import com.miniandmore.app.domain.OrderReturn;
import com.miniandmore.app.domain.enumeration.OrderReturnStatus;
import com.miniandmore.app.service.OrderReturnService;
import com.miniandmore.app.web.rest.v1.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/internal/v1/admin/order-returns")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class InternalAdminOrderReturnResource {

    private final OrderReturnService orderReturnService;

    public InternalAdminOrderReturnResource(OrderReturnService orderReturnService) {
        this.orderReturnService = orderReturnService;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list(@RequestParam(required = false) String status) {
        OrderReturnStatus filter = null;
        if (StringUtils.isNotBlank(status)) {
            try {
                filter = OrderReturnStatus.valueOf(status.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status: " + status);
            }
        }
        return ApiResponse.ok(orderReturnService.listForAdmin(filter).stream().map(this::toDto).toList());
    }

    @PostMapping("/{returnNumber}/approve")
    public ApiResponse<Map<String, Object>> approve(
        @PathVariable String returnNumber,
        @RequestBody(required = false) AdminNotesRequest body
    ) {
        return ApiResponse.ok(toDto(orderReturnService.approve(returnNumber, notes(body))));
    }

    @PostMapping("/{returnNumber}/reject")
    public ApiResponse<Map<String, Object>> reject(@PathVariable String returnNumber, @Valid @RequestBody RejectReturnRequest body) {
        return ApiResponse.ok(
            toDto(orderReturnService.rejectAtCurrentStage(returnNumber, body.rejection_reason(), body.admin_notes()))
        );
    }

    @PostMapping("/{returnNumber}/pickup-requested")
    public ApiResponse<Map<String, Object>> pickupRequested(
        @PathVariable String returnNumber,
        @RequestBody(required = false) AdminNotesRequest body
    ) {
        return ApiResponse.ok(toDto(orderReturnService.markPickupRequested(returnNumber, notes(body))));
    }

    @PostMapping("/{returnNumber}/picked-up")
    public ApiResponse<Map<String, Object>> pickedUp(
        @PathVariable String returnNumber,
        @RequestBody(required = false) AdminNotesRequest body
    ) {
        return ApiResponse.ok(toDto(orderReturnService.markPickedUp(returnNumber, notes(body))));
    }

    @PostMapping("/{returnNumber}/mark-received")
    public ApiResponse<Map<String, Object>> markReceived(
        @PathVariable String returnNumber,
        @RequestBody(required = false) AdminNotesRequest body
    ) {
        return ApiResponse.ok(toDto(orderReturnService.markReceived(returnNumber, notes(body))));
    }

    @PostMapping("/{returnNumber}/inspect")
    public ApiResponse<Map<String, Object>> inspect(
        @PathVariable String returnNumber,
        @RequestBody(required = false) AdminNotesRequest body
    ) {
        return ApiResponse.ok(toDto(orderReturnService.markInspected(returnNumber, notes(body))));
    }

    @PostMapping("/{returnNumber}/accept")
    public ApiResponse<Map<String, Object>> accept(
        @PathVariable String returnNumber,
        @RequestBody(required = false) AdminNotesRequest body
    ) {
        return ApiResponse.ok(toDto(orderReturnService.acceptAfterInspection(returnNumber, notes(body))));
    }

    @PostMapping("/{returnNumber}/allocate-replacement")
    public ApiResponse<Map<String, Object>> allocateReplacement(
        @PathVariable String returnNumber,
        @RequestBody(required = false) AdminNotesRequest body
    ) {
        return ApiResponse.ok(toDto(orderReturnService.allocateReplacementItem(returnNumber, notes(body))));
    }

    @PostMapping("/{returnNumber}/convert-to-refund")
    public ApiResponse<Map<String, Object>> convertToRefund(
        @PathVariable String returnNumber,
        @RequestBody(required = false) AdminNotesRequest body
    ) {
        return ApiResponse.ok(toDto(orderReturnService.convertExchangeToRefund(returnNumber, notes(body))));
    }

    @PostMapping("/{returnNumber}/repacking")
    public ApiResponse<Map<String, Object>> repacking(
        @PathVariable String returnNumber,
        @RequestBody(required = false) AdminNotesRequest body
    ) {
        return ApiResponse.ok(toDto(orderReturnService.markRepacking(returnNumber, notes(body))));
    }

    @PostMapping("/{returnNumber}/ready-to-ship")
    public ApiResponse<Map<String, Object>> readyToShip(
        @PathVariable String returnNumber,
        @RequestBody(required = false) AdminNotesRequest body
    ) {
        return ApiResponse.ok(toDto(orderReturnService.markReadyToShip(returnNumber, notes(body))));
    }

    @PostMapping("/{returnNumber}/shipped")
    public ApiResponse<Map<String, Object>> shipped(
        @PathVariable String returnNumber,
        @RequestBody(required = false) AdminNotesRequest body
    ) {
        return ApiResponse.ok(toDto(orderReturnService.markShipped(returnNumber, notes(body))));
    }

    @PostMapping("/{returnNumber}/delivered")
    public ApiResponse<Map<String, Object>> delivered(
        @PathVariable String returnNumber,
        @RequestBody(required = false) AdminNotesRequest body
    ) {
        return ApiResponse.ok(toDto(orderReturnService.markDelivered(returnNumber, notes(body))));
    }

    @PostMapping("/{returnNumber}/complete")
    public ApiResponse<Map<String, Object>> complete(
        @PathVariable String returnNumber,
        @RequestBody(required = false) AdminNotesRequest body
    ) {
        return ApiResponse.ok(toDto(orderReturnService.completeExchange(returnNumber, notes(body))));
    }

    @PostMapping("/{returnNumber}/refund")
    public ApiResponse<Map<String, Object>> refund(
        @PathVariable String returnNumber,
        @RequestBody(required = false) AdminNotesRequest body
    ) {
        return ApiResponse.ok(toDto(orderReturnService.processRefund(returnNumber, notes(body))));
    }

    private static String notes(AdminNotesRequest body) {
        return body != null ? body.admin_notes() : null;
    }

    private Map<String, Object> toDto(OrderReturn orderReturn) {
        var data = new LinkedHashMap<String, Object>();
        data.put("id", orderReturn.getId());
        data.put("return_number", orderReturn.getReturnNumber());
        data.put("status", orderReturn.getStatus().name().toLowerCase());
        data.put("request_type", orderReturn.getRequestType().name().toLowerCase());
        data.put("reason", orderReturn.getReason());
        data.put("admin_notes", orderReturn.getAdminNotes());
        data.put("rejection_reason", orderReturn.getRejectionReason());
        data.put("refund_amount", orderReturn.getRefundAmount());
        data.put("razorpay_refund_id", orderReturn.getRazorpayRefundId());
        data.put("ledgerly_credit_note_id", orderReturn.getLedgerlyCreditNoteId());
        data.put("requested_at", orderReturn.getRequestedAt());
        data.put("resolved_at", orderReturn.getResolvedAt());
        if (orderReturn.getOrder() != null) {
            data.put("order_id", orderReturn.getOrder().getId());
            data.put("order_number", orderReturn.getOrder().getOrderNumber());
            data.put("order_total", orderReturn.getOrder().getTotal());
            data.put("customer_login", orderReturn.getUserLogin());
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
        return data;
    }

    public record AdminNotesRequest(@Size(max = 2000) String admin_notes) {}

    public record RejectReturnRequest(
        @NotBlank @Size(min = 5, max = 1000) String rejection_reason,
        @Size(max = 2000) String admin_notes
    ) {}
}
