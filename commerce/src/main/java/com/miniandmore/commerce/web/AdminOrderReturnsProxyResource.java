package com.miniandmore.commerce.web;

import com.miniandmore.commerce.client.CatalogInternalClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/admin/order-returns")
public class AdminOrderReturnsProxyResource {

    private final CatalogInternalClient catalog;

    public AdminOrderReturnsProxyResource(CatalogInternalClient catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    public ResponseEntity<String> list(@RequestParam(required = false) String status) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/internal/v1/admin/order-returns");
        if (status != null && !status.isBlank()) {
            builder.queryParam("status", status);
        }
        return catalog.get(builder.build(true).toUriString());
    }

    @PostMapping("/{returnNumber}/approve")
    public ResponseEntity<String> approve(@PathVariable String returnNumber, @RequestBody(required = false) Object body) {
        return catalog.post(CatalogInternalClient.encodedPath("/internal/v1/admin/order-returns/{returnNumber}/approve", returnNumber), body);
    }

    @PostMapping("/{returnNumber}/reject")
    public ResponseEntity<String> reject(@PathVariable String returnNumber, @RequestBody Object body) {
        return catalog.post(CatalogInternalClient.encodedPath("/internal/v1/admin/order-returns/{returnNumber}/reject", returnNumber), body);
    }

    @PostMapping("/{returnNumber}/mark-received")
    public ResponseEntity<String> markReceived(@PathVariable String returnNumber, @RequestBody(required = false) Object body) {
        return catalog.post(
            CatalogInternalClient.encodedPath("/internal/v1/admin/order-returns/{returnNumber}/mark-received", returnNumber),
            body
        );
    }

    @PostMapping("/{returnNumber}/refund")
    public ResponseEntity<String> refund(@PathVariable String returnNumber, @RequestBody(required = false) Object body) {
        return catalog.post(CatalogInternalClient.encodedPath("/internal/v1/admin/order-returns/{returnNumber}/refund", returnNumber), body);
    }
}
