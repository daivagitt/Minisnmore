package com.miniandmore.app.web.rest.internal;

import com.miniandmore.app.domain.OrderReturn;
import com.miniandmore.app.domain.enumeration.ExchangeSettlementStatus;
import com.miniandmore.app.domain.enumeration.OrderReturnStatus;
import com.miniandmore.app.domain.enumeration.ManualRefundMethod;
import com.miniandmore.app.domain.enumeration.ReturnDisposition;
import com.miniandmore.app.domain.enumeration.ReturnLogisticsMode;
import com.miniandmore.app.service.CaseSlaSupport;
import com.miniandmore.app.service.OrderReturnService;
import com.miniandmore.app.service.OrderTrackingStatusResolver;
import com.miniandmore.app.security.SecurityUtils;
import com.miniandmore.app.web.rest.v1.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
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
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_SUPER_ADMIN')")
public class InternalAdminOrderReturnResource {

    private final OrderReturnService orderReturnService;
    private final CaseSlaSupport caseSlaSupport;

    public InternalAdminOrderReturnResource(OrderReturnService orderReturnService, CaseSlaSupport caseSlaSupport) {
        this.orderReturnService = orderReturnService;
        this.caseSlaSupport = caseSlaSupport;
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

    @GetMapping("/summary")
    public ApiResponse<Map<String, Object>> summary() {
        return ApiResponse.ok(orderReturnService.adminSummary()); // counts by type, stage, and SLA
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

    @PostMapping("/{returnNumber}/book-pickup")
    public ApiResponse<Map<String, Object>> bookPickup(
        @PathVariable String returnNumber,
        @Valid @RequestBody BookPickupRequest body
    ) {
        ReturnLogisticsMode mode = parseLogisticsMode(body.mode());
        return ApiResponse.ok(
            toDto(
                orderReturnService.bookPickup(
                    returnNumber,
                    mode,
                    body.carrier_name(),
                    body.reverse_awb(),
                    body.tracking_url(),
                    body.pickup_window(),
                    body.admin_notes()
                )
            )
        );
    }

    @PostMapping("/{returnNumber}/pickup-failed")
    public ApiResponse<Map<String, Object>> pickupFailed(
        @PathVariable String returnNumber,
        @Valid @RequestBody PickupFailedRequest body
    ) {
        return ApiResponse.ok(
            toDto(orderReturnService.markPickupFailed(returnNumber, body.failure_reason(), body.admin_notes()))
        );
    }

    @PostMapping("/{returnNumber}/reschedule-pickup")
    public ApiResponse<Map<String, Object>> reschedulePickup(
        @PathVariable String returnNumber,
        @RequestBody(required = false) AdminNotesRequest body
    ) {
        return ApiResponse.ok(toDto(orderReturnService.reschedulePickup(returnNumber, notes(body))));
    }

    @PostMapping("/{returnNumber}/close-pickup-failed")
    public ApiResponse<Map<String, Object>> closePickupFailed(
        @PathVariable String returnNumber,
        @RequestBody(required = false) AdminNotesRequest body
    ) {
        return ApiResponse.ok(toDto(orderReturnService.closePickupFailed(returnNumber, notes(body))));
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
        @Valid @RequestBody AcceptReturnRequest body
    ) {
        ReturnDisposition disposition = parseDisposition(body.disposition());
        List<OrderReturnService.InspectionLineDecision> decisions = body.items() == null
            ? List.of()
            : body
                .items()
                .stream()
                .map(item ->
                    new OrderReturnService.InspectionLineDecision(
                        item.return_item_id(),
                        item.accepted_quantity(),
                        item.disposition() == null ? null : parseDisposition(item.disposition())
                    )
                )
                .toList();
        OrderReturn accepted = decisions.isEmpty()
            ? orderReturnService.acceptAfterInspection(returnNumber, disposition, body.admin_notes())
            : orderReturnService.acceptAfterInspection(returnNumber, disposition, decisions, body.admin_notes());
        return ApiResponse.ok(toDto(accepted));
    }

    @PostMapping("/{returnNumber}/settle-price-difference")
    public ApiResponse<Map<String, Object>> settlePriceDifference(
        @PathVariable String returnNumber,
        @Valid @RequestBody SettlePriceDifferenceRequest body
    ) {
        ExchangeSettlementStatus status;
        try {
            status = ExchangeSettlementStatus.valueOf(body.status().trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid settlement status");
        }
        return ApiResponse.ok(
            toDto(
                orderReturnService.settlePriceDifference(
                    returnNumber,
                    status,
                    body.amount(),
                    body.method(),
                    body.reference(),
                    SecurityUtils.getCurrentUserLogin().orElse("system"),
                    body.admin_notes()
                )
            )
        );
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

    @PostMapping("/{returnNumber}/book-outbound")
    public ApiResponse<Map<String, Object>> bookOutbound(
        @PathVariable String returnNumber,
        @Valid @RequestBody BookOutboundRequest body
    ) {
        ReturnLogisticsMode mode = parseLogisticsMode(body.mode());
        return ApiResponse.ok(
            toDto(
                orderReturnService.bookOutbound(
                    returnNumber,
                    mode,
                    body.carrier_name(),
                    body.outbound_awb(),
                    body.tracking_url(),
                    body.label_url(),
                    body.admin_notes()
                )
            )
        );
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

    @PostMapping("/{returnNumber}/record-cod-refund")
    public ApiResponse<Map<String, Object>> recordCodRefund(
        @PathVariable String returnNumber,
        @Valid @RequestBody RecordCodRefundRequest body
    ) {
        return ApiResponse.ok(
            toDto(
                orderReturnService.recordManualCodRefund(
                    returnNumber,
                    parseManualRefundMethod(body.refund_method()),
                    body.refund_reference(),
                    body.admin_notes(),
                    SecurityUtils.getCurrentUserLogin().orElse("system")
                )
            )
        );
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
        data.put("image_urls", orderReturnService.imageUrls(orderReturn));
        data.put("admin_notes", orderReturn.getAdminNotes());
        data.put("rejection_reason", orderReturn.getRejectionReason());
        data.put("refund_amount", orderReturn.getRefundAmount());
        data.put("razorpay_refund_id", orderReturn.getRazorpayRefundId());
        data.put("ledgerly_credit_note_id", orderReturn.getLedgerlyCreditNoteId());
        data.put("requested_at", orderReturn.getRequestedAt());
        data.put("resolved_at", orderReturn.getResolvedAt());
        if (orderReturn.getOrder() != null) {
            var order = orderReturn.getOrder();
            data.put("order_id", order.getId());
            data.put("order_number", order.getOrderNumber());
            data.put("order_total", order.getTotal());
            data.put("customer_login", orderReturn.getUserLogin());
            data.put("payment_method", order.getPaymentMethod());
            data.put("payment_status", order.getPaymentStatus() != null ? order.getPaymentStatus().name().toLowerCase() : null);
            data.put("tracking_status", OrderTrackingStatusResolver.resolve(order));
            data.put("shipment_date", StringUtils.defaultString(order.getShipmentDate()));
            data.put("shipment_delivered_at", order.getShipmentDeliveredAt());
            data.put(
                "cod_refund_pending",
                OrderReturnService.isCollectedCod(order) &&
                orderReturn.getManualRefundRecordedAt() == null &&
                (orderReturn.getStatus() == OrderReturnStatus.ACCEPTED || orderReturn.getStatus() == OrderReturnStatus.FAILED)
            );
        }
        data.put(
            "manual_refund_method",
            orderReturn.getManualRefundMethod() != null ? orderReturn.getManualRefundMethod().name().toLowerCase() : null
        );
        data.put("manual_refund_reference", orderReturn.getManualRefundReference());
        data.put("manual_refund_recorded_at", orderReturn.getManualRefundRecordedAt());
        data.put("manual_refund_recorded_by", orderReturn.getManualRefundRecordedBy());
        List<Map<String, Object>> lines = orderReturnService.returnLineBreakdown(orderReturn).stream().map(OrderReturnDtoSupport::toLineDto).toList();
        OrderReturnDtoSupport.putOriginalItemFields(data, orderReturn, lines);
        data.put("replacement_size", orderReturn.getReplacementSize());
        data.put("replacement_color", orderReturn.getReplacementColor());
        data.put("disposition", orderReturn.getDisposition() != null ? orderReturn.getDisposition().name().toLowerCase() : null);
        data.put("logistics_mode", orderReturn.getLogisticsMode() != null ? orderReturn.getLogisticsMode().name().toLowerCase() : null);
        data.put("carrier_name", orderReturn.getCarrierName());
        data.put("reverse_awb", orderReturn.getReverseAwb());
        data.put("tracking_url", orderReturn.getTrackingUrl());
        data.put("pickup_window", orderReturn.getPickupWindow());
        data.put("provider_request_id", orderReturn.getProviderRequestId());
        data.put("pickup_failure_reason", orderReturn.getPickupFailureReason());
        data.put(
            "outbound_logistics_mode",
            orderReturn.getOutboundLogisticsMode() != null ? orderReturn.getOutboundLogisticsMode().name().toLowerCase() : null
        );
        data.put("outbound_carrier_name", orderReturn.getOutboundCarrierName());
        data.put("outbound_awb", orderReturn.getOutboundAwb());
        data.put("outbound_tracking_url", orderReturn.getOutboundTrackingUrl());
        data.put("outbound_label_url", orderReturn.getOutboundLabelUrl());
        data.put("outbound_provider_request_id", orderReturn.getOutboundProviderRequestId());
        data.put("original_item_amount", orderReturn.getOriginalItemAmount());
        data.put("replacement_item_amount", orderReturn.getReplacementItemAmount());
        data.put("price_difference", orderReturn.getPriceDifference());
        data.put(
            "settlement_status",
            orderReturn.getSettlementStatus() == null ? null : orderReturn.getSettlementStatus().name().toLowerCase()
        );
        data.put("settlement_method", orderReturn.getSettlementMethod());
        data.put("settlement_reference", orderReturn.getSettlementReference());
        data.put("settlement_amount", orderReturn.getSettlementAmount());
        data.put("settled_at", orderReturn.getSettledAt());
        data.put("settled_by", orderReturn.getSettledBy());
        data.put("replacement_reserved_at", orderReturn.getReplacementReservedAt());
        data.put("items", lines);
        caseSlaSupport.enrichReturnCase(data);
        return data;
    }

    private static ManualRefundMethod parseManualRefundMethod(String method) {
        if (StringUtils.isBlank(method)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Refund method is required");
        }
        try {
            return ManualRefundMethod.valueOf(method.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid refund method: " + method);
        }
    }

    private static ReturnLogisticsMode parseLogisticsMode(String mode) {
        if (StringUtils.isBlank(mode)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Logistics mode is required");
        }
        try {
            return ReturnLogisticsMode.valueOf(mode.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid logistics mode: " + mode);
        }
    }

    private static ReturnDisposition parseDisposition(String disposition) {
        if (StringUtils.isBlank(disposition)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Disposition is required");
        }
        try {
            return ReturnDisposition.valueOf(disposition.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid disposition: " + disposition);
        }
    }

    public record AdminNotesRequest(@Size(max = 2000) String admin_notes) {}

    public record RecordCodRefundRequest(
        @NotBlank String refund_method,
        @NotBlank @Size(min = 4, max = 160) String refund_reference,
        @Size(max = 2000) String admin_notes
    ) {}

    public record RejectReturnRequest(
        @NotBlank @Size(min = 5, max = 1000) String rejection_reason,
        @Size(max = 2000) String admin_notes
    ) {}

    public record BookPickupRequest(
        @NotBlank String mode,
        @Size(max = 80) String carrier_name,
        @Size(max = 80) String reverse_awb,
        @Size(max = 500) String tracking_url,
        @Size(max = 200) String pickup_window,
        @Size(max = 2000) String admin_notes
    ) {}

    public record BookOutboundRequest(
        @NotBlank String mode,
        @Size(max = 80) String carrier_name,
        @Size(max = 80) String outbound_awb,
        @Size(max = 500) String tracking_url,
        @Size(max = 500) String label_url,
        @Size(max = 2000) String admin_notes
    ) {}

    public record PickupFailedRequest(
        @NotBlank @Size(min = 5, max = 500) String failure_reason,
        @Size(max = 2000) String admin_notes
    ) {}

    public record AcceptReturnRequest(
        @NotBlank String disposition,
        @Size(max = 2000) String admin_notes,
        @Valid List<InspectionLineRequest> items
    ) {
        public AcceptReturnRequest(String disposition, String admin_notes) {
            this(disposition, admin_notes, List.of());
        }
    }

    public record InspectionLineRequest(
        @NotNull Long return_item_id,
        @NotNull @Min(0) Integer accepted_quantity,
        String disposition
    ) {}

    public record SettlePriceDifferenceRequest(
        @NotBlank String status,
        BigDecimal amount,
        @Size(max = 40) String method,
        @Size(max = 160) String reference,
        @Size(max = 2000) String admin_notes
    ) {}
}
