package com.miniandmore.commerce.web;

import com.miniandmore.commerce.client.CatalogInternalClient;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/admin/orders")
public class AdminOrdersProxyResource {

    private final CatalogInternalClient catalog;

    public AdminOrdersProxyResource(CatalogInternalClient catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    public ResponseEntity<String> listOrders(@RequestParam Map<String, String> query) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/internal/v1/admin/orders");
        query.forEach((key, value) -> {
            if (value != null && !value.isBlank()) {
                builder.queryParam(key, value);
            }
        });
        return catalog.get(builder.build().encode().toUriString());
    }

    @GetMapping("/cod-pending")
    public ResponseEntity<String> codPending() {
        return catalog.get("/internal/v1/admin/orders/cod-pending");
    }

    @GetMapping("/{orderNumber}")
    public ResponseEntity<String> getOrder(@PathVariable String orderNumber) {
        return catalog.get(CatalogInternalClient.encodedPath("/internal/v1/admin/orders/{orderNumber}", orderNumber));
    }

    @PostMapping("/{orderNumber}/mark-cod-collected")
    public ResponseEntity<String> markCodCollected(@PathVariable String orderNumber) {
        return catalog.post(
            CatalogInternalClient.encodedPath("/internal/v1/admin/orders/{orderNumber}/mark-cod-collected", orderNumber),
            null
        );
    }

    @PostMapping("/{orderNumber}/book-shipment")
    public ResponseEntity<String> bookShipment(@PathVariable String orderNumber, @RequestBody Object body) {
        return catalog.post(
            CatalogInternalClient.encodedPath("/internal/v1/admin/orders/{orderNumber}/book-shipment", orderNumber),
            body
        );
    }

    @PostMapping("/{orderNumber}/package-rates")
    public ResponseEntity<String> packageRates(@PathVariable String orderNumber, @RequestBody Object body) {
        return catalog.post(
            CatalogInternalClient.encodedPath("/internal/v1/admin/orders/{orderNumber}/package-rates", orderNumber),
            body
        );
    }

    @PostMapping("/{orderNumber}/complete-package")
    public ResponseEntity<String> completePackage(@PathVariable String orderNumber, @RequestBody Object body) {
        return catalog.post(
            CatalogInternalClient.encodedPath("/internal/v1/admin/orders/{orderNumber}/complete-package", orderNumber),
            body
        );
    }

    @PostMapping("/{orderNumber}/cancel-shipment")
    public ResponseEntity<String> cancelShipment(@PathVariable String orderNumber, @RequestBody Object body) {
        return catalog.post(
            CatalogInternalClient.encodedPath("/internal/v1/admin/orders/{orderNumber}/cancel-shipment", orderNumber),
            body
        );
    }
}
