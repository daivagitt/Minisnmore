package com.miniandmore.commerce.web;

import com.miniandmore.commerce.client.CatalogInternalClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/order-returns")
public class OrderReturnsProxyResource {

    private final CatalogInternalClient catalog;

    public OrderReturnsProxyResource(CatalogInternalClient catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    public ResponseEntity<String> listMine() {
        return catalog.get("/internal/v1/order-returns");
    }

    @PostMapping
    public ResponseEntity<String> requestReturn(@RequestBody Object body) {
        return catalog.post("/internal/v1/order-returns", body);
    }

    @PostMapping("/{returnNumber}/cancel")
    public ResponseEntity<String> cancel(@PathVariable String returnNumber) {
        return catalog.postEmpty(CatalogInternalClient.encodedPath("/internal/v1/order-returns/{returnNumber}/cancel", returnNumber));
    }

    @GetMapping("/eligibility/{orderNumber}")
    public ResponseEntity<String> checkEligibility(@PathVariable String orderNumber) {
        return catalog.get(CatalogInternalClient.encodedPath("/internal/v1/order-returns/eligibility/{orderNumber}", orderNumber));
    }
}
