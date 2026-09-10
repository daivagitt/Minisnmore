package com.miniandmore.app.web.rest.internal;

import com.miniandmore.app.domain.OrderCancellationRequest;
import com.miniandmore.app.domain.enumeration.CancellationRequestStatus;
import com.miniandmore.app.service.OrderCancellationService;
import com.miniandmore.app.web.rest.v1.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/internal/v1/admin/cancellation-requests")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class InternalAdminCancellationRequestResource {

    private final OrderCancellationService orderCancellationService;

    public InternalAdminCancellationRequestResource(OrderCancellationService orderCancellationService) {
        this.orderCancellationService = orderCancellationService;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list(@RequestParam(required = false) String status) {
        CancellationRequestStatus filter = null;
        if (StringUtils.isNotBlank(status)) {
            try {
                filter = CancellationRequestStatus.valueOf(status.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status: " + status);
            }
        }
        return ApiResponse.ok(
            orderCancellationService.listForAdmin(filter).stream().map(orderCancellationService::toDto).toList()
        );
    }

    @PostMapping("/{requestNumber}/approve")
    public ApiResponse<Map<String, Object>> approve(
        @PathVariable String requestNumber,
        @RequestBody(required = false) AdminNotesRequest body
    ) {
        OrderCancellationRequest approved = orderCancellationService.approve(requestNumber, notes(body));
        return ApiResponse.ok(orderCancellationService.toDto(approved));
    }

    @PostMapping("/{requestNumber}/reject")
    public ApiResponse<Map<String, Object>> reject(
        @PathVariable String requestNumber,
        @Valid @RequestBody RejectCancellationRequest body
    ) {
        OrderCancellationRequest rejected = orderCancellationService.reject(
            requestNumber,
            body.rejection_reason(),
            body.admin_notes()
        );
        return ApiResponse.ok(orderCancellationService.toDto(rejected));
    }

    private static String notes(AdminNotesRequest body) {
        return body != null ? body.admin_notes() : null;
    }

    public record AdminNotesRequest(@Size(max = 2000) String admin_notes) {}

    public record RejectCancellationRequest(
        @NotBlank @Size(min = 5, max = 1000) String rejection_reason,
        @Size(max = 2000) String admin_notes
    ) {}
}
