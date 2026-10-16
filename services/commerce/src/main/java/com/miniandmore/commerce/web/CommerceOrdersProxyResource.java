package com.miniandmore.commerce.web;

import com.miniandmore.commerce.client.CatalogInternalClient;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class CommerceOrdersProxyResource {

    private static final String ACCOUNT_ADDRESSES_PATH = "/internal/v1/account/addresses/";

    private final CatalogInternalClient catalog;

    public CommerceOrdersProxyResource(CatalogInternalClient catalog) {
        this.catalog = catalog;
    }

    @PostMapping("/coupons/validate")
    public ResponseEntity<String> validateCoupon(@RequestBody Object body) {
        return catalog.post("/internal/v1/coupons/validate", body);
    }

    @GetMapping("/coupons/active")
    public ResponseEntity<String> activeCoupons() {
        return catalog.get("/internal/v1/coupons/active");
    }

    @GetMapping("/account/profile")
    public ResponseEntity<String> profile() {
        return catalog.get("/internal/v1/account/profile");
    }

    @PutMapping("/account/profile")
    public ResponseEntity<String> updateProfile(@RequestBody Object body) {
        return catalog.put("/internal/v1/account/profile", body);
    }

    @PostMapping("/account/profile/bootstrap")
    public ResponseEntity<String> bootstrapProfile(@RequestBody Object body) {
        return catalog.post("/internal/v1/account/profile/bootstrap", body);
    }

    @GetMapping("/account/addresses")
    public ResponseEntity<String> listAddresses() {
        return catalog.get("/internal/v1/account/addresses");
    }

    @PostMapping("/account/addresses")
    public ResponseEntity<String> createAddress(@RequestBody Object body) {
        return catalog.post("/internal/v1/account/addresses", body);
    }

    @PutMapping("/account/addresses/{id}")
    public ResponseEntity<String> updateAddress(@PathVariable Long id, @RequestBody Object body) {
        return catalog.put(ACCOUNT_ADDRESSES_PATH + id, body);
    }

    @DeleteMapping("/account/addresses/{id}")
    public ResponseEntity<String> deleteAddress(@PathVariable Long id) {
        return catalog.delete(ACCOUNT_ADDRESSES_PATH + id);
    }

    @PutMapping("/account/addresses/{id}/default")
    public ResponseEntity<String> setDefaultAddress(@PathVariable Long id) {
        return catalog.put(ACCOUNT_ADDRESSES_PATH + id + "/default", Map.of());
    }

    @PostMapping("/account/addresses/sync")
    public ResponseEntity<String> syncAddresses(@RequestBody Object body) {
        return catalog.post("/internal/v1/account/addresses/sync", body);
    }

    @GetMapping("/account/wishlist")
    public ResponseEntity<String> listWishlist() {
        return catalog.get("/internal/v1/account/wishlist");
    }

    @GetMapping("/account/notifications")
    public ResponseEntity<String> listNotifications() {
        return catalog.get("/internal/v1/account/notifications");
    }

    @PostMapping("/account/wishlist/items")
    public ResponseEntity<String> addWishlistItem(@RequestBody Object body) {
        return catalog.post("/internal/v1/account/wishlist/items", body);
    }

    @DeleteMapping("/account/wishlist/items/{productId}")
    public ResponseEntity<String> removeWishlistItem(@PathVariable String productId) {
        return catalog.delete(CatalogInternalClient.encodedPath("/internal/v1/account/wishlist/items/{productId}", productId));
    }

    @PostMapping("/account/wishlist/sync")
    public ResponseEntity<String> syncWishlist(@RequestBody Object body) {
        return catalog.post("/internal/v1/account/wishlist/sync", body);
    }

    @PutMapping("/account/wishlist")
    public ResponseEntity<String> replaceWishlist(@RequestBody Object body) {
        return catalog.put("/internal/v1/account/wishlist", body);
    }

    @GetMapping("/account/cart")
    public ResponseEntity<String> getCart() {
        return catalog.get("/internal/v1/account/cart");
    }

    @PutMapping("/account/cart")
    public ResponseEntity<String> replaceCart(@RequestBody Object body) {
        return catalog.put("/internal/v1/account/cart", body);
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

    @PostMapping("/orders/{orderNumber}/cancel")
    public ResponseEntity<String> cancelOrder(@PathVariable String orderNumber, @RequestBody Object body) {
        return catalog.post(CatalogInternalClient.encodedPath("/internal/v1/orders/{orderNumber}/cancel", orderNumber), body);
    }

    @PostMapping("/orders/{orderNumber}/cancel-request")
    public ResponseEntity<String> submitCancelRequest(@PathVariable String orderNumber, @RequestBody Object body) {
        return catalog.post(
            CatalogInternalClient.encodedPath("/internal/v1/orders/{orderNumber}/cancel-request", orderNumber),
            body
        );
    }

    @GetMapping("/orders/{orderNumber}/cancel-request")
    public ResponseEntity<String> getCancelRequest(@PathVariable String orderNumber) {
        return catalog.get(CatalogInternalClient.encodedPath("/internal/v1/orders/{orderNumber}/cancel-request", orderNumber));
    }

    @PostMapping("/orders/{orderNumber}/cancel-request/withdraw")
    public ResponseEntity<String> withdrawCancelRequest(@PathVariable String orderNumber) {
        return catalog.post(
            CatalogInternalClient.encodedPath("/internal/v1/orders/{orderNumber}/cancel-request/withdraw", orderNumber),
            Map.of()
        );
    }

    @GetMapping("/payments")
    public ResponseEntity<String> listPayments() {
        return catalog.get("/internal/v1/payments");
    }
}
