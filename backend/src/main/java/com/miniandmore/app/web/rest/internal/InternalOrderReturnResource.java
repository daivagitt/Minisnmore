package com.miniandmore.app.web.rest.internal;

import com.miniandmore.app.domain.OrderReturn;
import com.miniandmore.app.security.SecurityUtils;
import com.miniandmore.app.service.OrderReturnService;
import com.miniandmore.app.web.rest.v1.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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
        OrderReturn orderReturn = orderReturnService.requestReturn(login, request.order_number(), request.reason());
        return ApiResponse.ok(toDto(orderReturn));
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
        data.put("reason", orderReturn.getReason());
        data.put("rejection_reason", orderReturn.getRejectionReason());
        data.put("refund_amount", orderReturn.getRefundAmount());
        data.put("requested_at", orderReturn.getRequestedAt());
        data.put("resolved_at", orderReturn.getResolvedAt());
        if (orderReturn.getOrder() != null) {
            data.put("order_number", orderReturn.getOrder().getOrderNumber());
            data.put("order_total", orderReturn.getOrder().getTotal());
        }
        return data;
    }

    public record CreateReturnRequest(
        @NotBlank @Size(max = 40) String order_number,
        @NotBlank @Size(min = 10, max = 4000) String reason
    ) {}
}
