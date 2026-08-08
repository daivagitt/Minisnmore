package com.miniandmore.commerce.web;

import com.miniandmore.commerce.client.CatalogInternalClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class CommerceOrdersProxyResource {

    private final CatalogInternalClient catalog;

    public CommerceOrdersProxyResource(CatalogInternalClient catalog) {
        this.catalog = catalog;
    }

    @PostMapping("/coupons/validate")
    public ResponseEntity<String> validateCoupon(@RequestBody Object body) {
        return catalog.postPublic("/internal/v1/coupons/validate", body);
    }

    @GetMapping("/account/profile")
    public ResponseEntity<String> profile() {
        return catalog.get("/internal/v1/account/profile");
    }

    @PutMapping("/account/profile")
    public ResponseEntity<String> updateProfile(@RequestBody Object body) {
        return catalog.put("/internal/v1/account/profile", body);
    }

    @PostMapping("/orders")
    public ResponseEntity<String> createOrder(@RequestBody Object body) {
        return catalog.post("/internal/v1/orders", body);
    }

    @PostMapping("/orders/verify-payment")
    public ResponseEntity<String> verifyPayment(@RequestBody Object body) {
        return catalog.post("/internal/v1/orders/verify-payment", body);
    }

    @PostMapping("/orders/sync-payment")
    public ResponseEntity<String> syncPayment(@RequestBody Object body) {
        return catalog.post("/internal/v1/orders/sync-payment", body);
    }

    @PostMapping("/orders/retry-payment")
    public ResponseEntity<String> retryPayment(@RequestBody Object body) {
        return catalog.post("/internal/v1/orders/retry-payment", body);
    }

    @GetMapping("/orders")
    public ResponseEntity<String> listOrders() {
        return catalog.get("/internal/v1/orders");
    }

    @GetMapping("/orders/track/{orderNumber}")
    public ResponseEntity<String> trackOrder(@PathVariable String orderNumber) {
        return catalog.getPublic(CatalogInternalClient.encodedPath("/internal/v1/orders/track/{orderNumber}", orderNumber));
    }

    @GetMapping("/orders/{orderNumber}")
    public ResponseEntity<String> getOrder(@PathVariable String orderNumber) {
        return catalog.get(CatalogInternalClient.encodedPath("/internal/v1/orders/{orderNumber}", orderNumber));
    }

    @GetMapping("/payments")
    public ResponseEntity<String> listPayments() {
        return catalog.get("/internal/v1/payments");
    }
}
