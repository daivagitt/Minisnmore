package com.miniandmore.commerce.web;

import com.miniandmore.commerce.client.CatalogInternalClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/admin/cancellation-requests")
public class AdminCancellationRequestsProxyResource {

    private final CatalogInternalClient catalog;

    public AdminCancellationRequestsProxyResource(CatalogInternalClient catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    public ResponseEntity<String> list(@RequestParam(required = false) String status) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/internal/v1/admin/cancellation-requests");
        if (status != null && !status.isBlank()) {
            builder.queryParam("status", status);
        }
        return catalog.get(builder.build(true).toUriString());
    }

    @PostMapping("/{requestNumber}/approve")
    public ResponseEntity<String> approve(@PathVariable String requestNumber, @RequestBody(required = false) Object body) {
        return catalog.post(
            CatalogInternalClient.encodedPath("/internal/v1/admin/cancellation-requests/{requestNumber}/approve", requestNumber),
            body
        );
    }

    @PostMapping("/{requestNumber}/reject")
    public ResponseEntity<String> reject(@PathVariable String requestNumber, @RequestBody Object body) {
        return catalog.post(
            CatalogInternalClient.encodedPath("/internal/v1/admin/cancellation-requests/{requestNumber}/reject", requestNumber),
            body
        );
    }
}
