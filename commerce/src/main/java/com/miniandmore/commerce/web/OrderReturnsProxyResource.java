package com.miniandmore.commerce.web;

import com.miniandmore.commerce.client.CatalogInternalClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

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

    @PostMapping("/exchange")
    public ResponseEntity<String> requestExchange(@RequestBody Object body) {
        return catalog.post("/internal/v1/order-returns/exchange", body);
    }

    @GetMapping("/exchange-availability/{orderItemId}")
    public ResponseEntity<String> checkExchangeAvailability(
        @PathVariable Long orderItemId,
        @RequestParam String size,
        @RequestParam String color
    ) {
        UriComponentsBuilder builder = UriComponentsBuilder
            .fromPath("/internal/v1/order-returns/exchange-availability/" + orderItemId)
            .queryParam("size", size)
            .queryParam("color", color);
        return catalog.get(builder.encode().build().toUriString());
    }

    @GetMapping("/config")
    public ResponseEntity<String> config() {
        return catalog.get("/internal/v1/order-returns/config");
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
