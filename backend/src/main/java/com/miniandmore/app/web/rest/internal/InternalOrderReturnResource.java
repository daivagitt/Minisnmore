package com.miniandmore.app.web.rest.internal;

import com.miniandmore.app.domain.OrderReturn;
import com.miniandmore.app.security.SecurityUtils;
import com.miniandmore.app.service.OrderReturnService;
import com.miniandmore.app.service.OrderTrackingStatusResolver;
import com.miniandmore.app.service.S3StorageService;
import com.miniandmore.app.web.rest.v1.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/internal/v1/order-returns")
public class InternalOrderReturnResource {

    private final OrderReturnService orderReturnService;
    private final S3StorageService s3StorageService;

    public InternalOrderReturnResource(OrderReturnService orderReturnService, S3StorageService s3StorageService) {
        this.orderReturnService = orderReturnService;
        this.s3StorageService = s3StorageService;
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
        OrderReturn orderReturn = orderReturnService.requestReturn(
            login,
            request.order_number(),
            request.reason(),
            lines,
            request.image_urls()
        );
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
            request.replacement_color(),
            request.image_urls()
        );
        return ApiResponse.ok(toDto(orderReturn));
    }

    @PostMapping(value = "/uploads", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Map<String, Object>> uploadImage(@RequestPart("file") MultipartFile file) throws IOException {
        requireLogin();
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image file is required");
        }
        String publicUrl = s3StorageService.uploadObject(
            "returns",
            file.getOriginalFilename(),
            file.getBytes(),
            file.getContentType()
        );
        var result = new LinkedHashMap<String, Object>();
        result.put("publicUrl", publicUrl);
        return ApiResponse.ok(result);
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
        data.put("image_urls", orderReturnService.imageUrls(orderReturn));
        data.put("rejection_reason", orderReturn.getRejectionReason());
        data.put("refund_amount", orderReturn.getRefundAmount());
        data.put("requested_at", orderReturn.getRequestedAt());
        data.put("resolved_at", orderReturn.getResolvedAt());
        if (orderReturn.getOrder() != null) {
            var order = orderReturn.getOrder();
            data.put("order_number", order.getOrderNumber());
            data.put("order_total", order.getTotal());
            data.put("order_shipping", order.getShipping());
            data.put("order_cod_fee", order.getCodFee());
            data.put(
                "order_payment_status",
                order.getPaymentStatus() != null ? order.getPaymentStatus().name().toLowerCase() : null
            );
            data.put("tracking_status", OrderTrackingStatusResolver.resolve(order));
            data.put("shipment_delivered_at", order.getShipmentDeliveredAt());
        }
        data.put("carrier_name", orderReturn.getCarrierName());
        data.put("reverse_awb", orderReturn.getReverseAwb());
        data.put("tracking_url", orderReturn.getTrackingUrl());
        data.put("pickup_window", orderReturn.getPickupWindow());
        data.put("pickup_failure_reason", orderReturn.getPickupFailureReason());
        data.put("reverse_carrier_status", orderReturn.getReverseCarrierStatus());
        List<Map<String, Object>> lines = orderReturnService.returnLineBreakdown(orderReturn).stream().map(OrderReturnDtoSupport::toLineDto).toList();
        OrderReturnDtoSupport.putOriginalItemFields(data, orderReturn, lines);
        data.put("replacement_size", orderReturn.getReplacementSize());
        data.put("replacement_color", orderReturn.getReplacementColor());
        data.put("outbound_awb", orderReturn.getOutboundAwb());
        data.put("outbound_tracking_url", orderReturn.getOutboundTrackingUrl());
        data.put("outbound_carrier_name", orderReturn.getOutboundCarrierName());
        data.put("outbound_carrier_status", orderReturn.getOutboundCarrierStatus());
        data.put("original_item_amount", orderReturn.getOriginalItemAmount());
        data.put("replacement_item_amount", orderReturn.getReplacementItemAmount());
        data.put("price_difference", orderReturn.getPriceDifference());
        data.put(
            "settlement_status",
            orderReturn.getSettlementStatus() == null ? null : orderReturn.getSettlementStatus().name().toLowerCase()
        );
        data.put("items", lines);
        return data;
    }

    public record CreateReturnRequest(
        @NotBlank @Size(max = 40) String order_number,
        @NotBlank @Size(min = 10, max = 4000) String reason,
        @Valid List<ReturnLineRequest> items,
        @Size(max = 5) List<@NotBlank @Size(max = 600) String> image_urls
    ) {
        public CreateReturnRequest {
            items = items == null ? List.of() : items;
            image_urls = image_urls == null ? List.of() : image_urls;
        }

        public CreateReturnRequest(String order_number, String reason) {
            this(order_number, reason, List.of(), List.of());
        }
    }

    public record ReturnLineRequest(@NotNull Long order_item_id, @NotNull @Min(1) Integer quantity) {}

    public record CreateExchangeRequest(
        @NotBlank @Size(max = 40) String order_number,
        @NotNull Long order_item_id,
        @NotBlank @Size(min = 10, max = 4000) String reason,
        @NotBlank @Size(max = 40) String replacement_size,
        @NotBlank @Size(max = 40) String replacement_color,
        @Size(max = 5) List<@NotBlank @Size(max = 600) String> image_urls
    ) {
        public CreateExchangeRequest {
            image_urls = image_urls == null ? List.of() : image_urls;
        }
    }
}
