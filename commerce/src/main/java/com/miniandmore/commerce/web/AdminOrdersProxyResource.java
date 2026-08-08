package com.miniandmore.commerce.web;

import com.miniandmore.commerce.client.CatalogInternalClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/orders")
public class AdminOrdersProxyResource {

    private final CatalogInternalClient catalog;

    public AdminOrdersProxyResource(CatalogInternalClient catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/cod-pending")
    public ResponseEntity<String> codPending() {
        return catalog.get("/internal/v1/admin/orders/cod-pending");
    }

    @PostMapping("/{orderNumber}/mark-cod-collected")
    public ResponseEntity<String> markCodCollected(@PathVariable String orderNumber) {
        return catalog.post(
            CatalogInternalClient.encodedPath("/internal/v1/admin/orders/{orderNumber}/mark-cod-collected", orderNumber),
            null
        );
    }
}
