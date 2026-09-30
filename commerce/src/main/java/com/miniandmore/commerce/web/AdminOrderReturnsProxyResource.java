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

    @GetMapping("/summary")
    public ResponseEntity<String> summary() {
        return catalog.get("/internal/v1/admin/order-returns/summary");
    }

    @PostMapping("/{returnNumber}/approve")
    public ResponseEntity<String> approve(@PathVariable String returnNumber, @RequestBody(required = false) Object body) {
        return catalog.post(CatalogInternalClient.encodedPath("/internal/v1/admin/order-returns/{returnNumber}/approve", returnNumber), body);
    }

    @PostMapping("/{returnNumber}/reject")
    public ResponseEntity<String> reject(@PathVariable String returnNumber, @RequestBody Object body) {
        return catalog.post(CatalogInternalClient.encodedPath("/internal/v1/admin/order-returns/{returnNumber}/reject", returnNumber), body);
    }

    @PostMapping("/{returnNumber}/pickup-requested")
    public ResponseEntity<String> pickupRequested(@PathVariable String returnNumber, @RequestBody(required = false) Object body) {
        return catalog.post(
            CatalogInternalClient.encodedPath("/internal/v1/admin/order-returns/{returnNumber}/pickup-requested", returnNumber),
            body
        );
    }

    @PostMapping("/{returnNumber}/picked-up")
    public ResponseEntity<String> pickedUp(@PathVariable String returnNumber, @RequestBody(required = false) Object body) {
        return catalog.post(
            CatalogInternalClient.encodedPath("/internal/v1/admin/order-returns/{returnNumber}/picked-up", returnNumber),
            body
        );
    }

    @PostMapping("/{returnNumber}/mark-received")
    public ResponseEntity<String> markReceived(@PathVariable String returnNumber, @RequestBody(required = false) Object body) {
        return catalog.post(
            CatalogInternalClient.encodedPath("/internal/v1/admin/order-returns/{returnNumber}/mark-received", returnNumber),
            body
        );
    }

    @PostMapping("/{returnNumber}/inspect")
    public ResponseEntity<String> inspect(@PathVariable String returnNumber, @RequestBody(required = false) Object body) {
        return catalog.post(CatalogInternalClient.encodedPath("/internal/v1/admin/order-returns/{returnNumber}/inspect", returnNumber), body);
    }

    @PostMapping("/{returnNumber}/accept")
    public ResponseEntity<String> accept(@PathVariable String returnNumber, @RequestBody(required = false) Object body) {
        return catalog.post(CatalogInternalClient.encodedPath("/internal/v1/admin/order-returns/{returnNumber}/accept", returnNumber), body);
    }

    @PostMapping("/{returnNumber}/allocate-replacement")
    public ResponseEntity<String> allocateReplacement(@PathVariable String returnNumber, @RequestBody(required = false) Object body) {
        return catalog.post(
            CatalogInternalClient.encodedPath("/internal/v1/admin/order-returns/{returnNumber}/allocate-replacement", returnNumber),
            body
        );
    }

    @PostMapping("/{returnNumber}/convert-to-refund")
    public ResponseEntity<String> convertToRefund(@PathVariable String returnNumber, @RequestBody(required = false) Object body) {
        return catalog.post(
            CatalogInternalClient.encodedPath("/internal/v1/admin/order-returns/{returnNumber}/convert-to-refund", returnNumber),
            body
        );
    }

    @PostMapping("/{returnNumber}/repacking")
    public ResponseEntity<String> repacking(@PathVariable String returnNumber, @RequestBody(required = false) Object body) {
        return catalog.post(
            CatalogInternalClient.encodedPath("/internal/v1/admin/order-returns/{returnNumber}/repacking", returnNumber),
            body
        );
    }

    @PostMapping("/{returnNumber}/ready-to-ship")
    public ResponseEntity<String> readyToShip(@PathVariable String returnNumber, @RequestBody(required = false) Object body) {
        return catalog.post(
            CatalogInternalClient.encodedPath("/internal/v1/admin/order-returns/{returnNumber}/ready-to-ship", returnNumber),
            body
        );
    }

    @PostMapping("/{returnNumber}/shipped")
    public ResponseEntity<String> shipped(@PathVariable String returnNumber, @RequestBody(required = false) Object body) {
        return catalog.post(CatalogInternalClient.encodedPath("/internal/v1/admin/order-returns/{returnNumber}/shipped", returnNumber), body);
    }

    @PostMapping("/{returnNumber}/delivered")
    public ResponseEntity<String> delivered(@PathVariable String returnNumber, @RequestBody(required = false) Object body) {
        return catalog.post(
            CatalogInternalClient.encodedPath("/internal/v1/admin/order-returns/{returnNumber}/delivered", returnNumber),
            body
        );
    }

    @PostMapping("/{returnNumber}/complete")
    public ResponseEntity<String> complete(@PathVariable String returnNumber, @RequestBody(required = false) Object body) {
        return catalog.post(CatalogInternalClient.encodedPath("/internal/v1/admin/order-returns/{returnNumber}/complete", returnNumber), body);
    }

    @PostMapping("/{returnNumber}/refund")
    public ResponseEntity<String> refund(@PathVariable String returnNumber, @RequestBody(required = false) Object body) {
        return catalog.post(CatalogInternalClient.encodedPath("/internal/v1/admin/order-returns/{returnNumber}/refund", returnNumber), body);
    }

    @PostMapping("/{returnNumber}/record-cod-refund")
    public ResponseEntity<String> recordCodRefund(@PathVariable String returnNumber, @RequestBody Object body) {
        return catalog.post(
            CatalogInternalClient.encodedPath("/internal/v1/admin/order-returns/{returnNumber}/record-cod-refund", returnNumber),
            body
        );
    }

    @PostMapping("/{returnNumber}/book-pickup")
    public ResponseEntity<String> bookPickup(@PathVariable String returnNumber, @RequestBody Object body) {
        return catalog.post(
            CatalogInternalClient.encodedPath("/internal/v1/admin/order-returns/{returnNumber}/book-pickup", returnNumber),
            body
        );
    }

    @PostMapping("/{returnNumber}/pickup-failed")
    public ResponseEntity<String> pickupFailed(@PathVariable String returnNumber, @RequestBody Object body) {
        return catalog.post(
            CatalogInternalClient.encodedPath("/internal/v1/admin/order-returns/{returnNumber}/pickup-failed", returnNumber),
            body
        );
    }

    @PostMapping("/{returnNumber}/reschedule-pickup")
    public ResponseEntity<String> reschedulePickup(@PathVariable String returnNumber, @RequestBody(required = false) Object body) {
        return catalog.post(
            CatalogInternalClient.encodedPath("/internal/v1/admin/order-returns/{returnNumber}/reschedule-pickup", returnNumber),
            body
        );
    }

    @PostMapping("/{returnNumber}/close-pickup-failed")
    public ResponseEntity<String> closePickupFailed(@PathVariable String returnNumber, @RequestBody(required = false) Object body) {
        return catalog.post(
            CatalogInternalClient.encodedPath("/internal/v1/admin/order-returns/{returnNumber}/close-pickup-failed", returnNumber),
            body
        );
    }

    @PostMapping("/{returnNumber}/settle-price-difference")
    public ResponseEntity<String> settlePriceDifference(@PathVariable String returnNumber, @RequestBody Object body) {
        return catalog.post(
            CatalogInternalClient.encodedPath("/internal/v1/admin/order-returns/{returnNumber}/settle-price-difference", returnNumber),
            body
        );
    }

    @PostMapping("/{returnNumber}/book-outbound")
    public ResponseEntity<String> bookOutbound(@PathVariable String returnNumber, @RequestBody Object body) {
        return catalog.post(
            CatalogInternalClient.encodedPath("/internal/v1/admin/order-returns/{returnNumber}/book-outbound", returnNumber),
            body
        );
    }
}
