package com.miniandmore.app.web.rest.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.config.ApplicationProperties;
import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.enumeration.LedgerlySyncStatus;
import com.miniandmore.app.domain.enumeration.OrderStatus;
import com.miniandmore.app.domain.enumeration.PaymentStatus;
import com.miniandmore.app.domain.enumeration.ReturnLogisticsMode;
import com.miniandmore.app.domain.enumeration.ZohoSyncStatus;
import com.miniandmore.app.security.SecurityUtils;
import com.miniandmore.app.service.LedgerlySyncService;
import com.miniandmore.app.service.OrderCancellationService;
import com.miniandmore.app.service.OrderEventService;
import com.miniandmore.app.service.OrderPackageService;
import com.miniandmore.app.domain.enumeration.StoreAddressUsage;
import com.miniandmore.app.repository.StoreAddressRepository;
import com.miniandmore.app.service.OrderShipmentService;
import com.miniandmore.app.service.OrderTrackingStatusResolver;
import com.miniandmore.app.service.StoreAddressService;
import com.miniandmore.app.service.ZohoSyncService;
import com.miniandmore.app.web.rest.v1.AccountOrderDataAccess;
import com.miniandmore.app.web.rest.v1.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * Admin-only actions for Cash on Delivery orders, which have no online payment step and so need a
 * manual "collected" confirmation once the courier hands over the cash/UPI payment at the doorstep.
 * Zoho Books receives the sales order when the COD order is placed; marking collected records the cash payment.
 */
@RestController
@RequestMapping("/internal/v1/admin/orders")
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_SUPER_ADMIN','ROLE_OPERATIONS','ROLE_CUSTOMER_SUPPORT')")
public class InternalAdminOrderResource {

    private static final Duration MAX_ORDER_RANGE = Duration.ofDays(31);
    private static final Duration TRACKING_REFRESH_TTL = Duration.ofMinutes(5);
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 50;

    private final AccountOrderDataAccess dataAccess;
    private final ApplicationProperties applicationProperties;
    private final LedgerlySyncService ledgerlySyncService;
    private final ZohoSyncService zohoSyncService;
    private final ObjectMapper objectMapper;
    private final OrderEventService orderEventService;
    private final InternalAccountOrderResource accountOrders;
    private final OrderShipmentService orderShipmentService;
    private final OrderPackageService orderPackageService;
    private final OrderCancellationService orderCancellationService;
    private final StoreAddressRepository storeAddressRepository;

    public InternalAdminOrderResource(
        AccountOrderDataAccess dataAccess,
        ApplicationProperties applicationProperties,
        LedgerlySyncService ledgerlySyncService,
        ZohoSyncService zohoSyncService,
        ObjectMapper objectMapper,
        OrderEventService orderEventService,
        InternalAccountOrderResource accountOrders,
        OrderShipmentService orderShipmentService,
        OrderPackageService orderPackageService,
        OrderCancellationService orderCancellationService,
        StoreAddressRepository storeAddressRepository
    ) {
        this.dataAccess = dataAccess;
        this.applicationProperties = applicationProperties;
        this.ledgerlySyncService = ledgerlySyncService;
        this.zohoSyncService = zohoSyncService;
        this.objectMapper = objectMapper;
        this.orderEventService = orderEventService;
        this.accountOrders = accountOrders;
        this.orderShipmentService = orderShipmentService;
        this.orderPackageService = orderPackageService;
        this.orderCancellationService = orderCancellationService;
        this.storeAddressRepository = storeAddressRepository;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> listOrders(
        @RequestParam(required = false) Instant from,
        @RequestParam(required = false) Instant to,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int size,
        @RequestParam(required = false) String q,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String paymentStatus,
        @RequestParam(required = false) String paymentMethod,
        @RequestParam(required = false) String zohoSyncStatus,
        @RequestParam(required = false) String ledgerlySyncStatus,
        @RequestParam(required = false) String trackingStatus
    ) {
        boolean hasRange = from != null || to != null;
        if (hasRange) {
            if (from == null || to == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Both from and to are required when filtering by date");
            }
            if (!from.isBefore(to)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The order date range is invalid");
            }
            if (Duration.between(from, to).compareTo(MAX_ORDER_RANGE) > 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order date range cannot exceed 31 days");
            }
        }
        int pageSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int pageNumber = Math.max(page, 1);
        Pageable pageable = PageRequest.of(
            pageNumber - 1,
            pageSize,
            Sort.by(Sort.Direction.DESC, "placedAt").and(Sort.by(Sort.Direction.DESC, "id"))
        );
        Page<CustomerOrder> result = dataAccess
            .customerOrderRepository()
            .findAll(
                AdminOrderListSpec.create(
                    from,
                    to,
                    q,
                    status,
                    paymentStatus,
                    paymentMethod,
                    zohoSyncStatus,
                    ledgerlySyncStatus,
                    trackingStatus
                ),
                pageable
            );
        if (result.getTotalPages() > 0 && pageNumber > result.getTotalPages()) {
            pageNumber = result.getTotalPages();
            pageable = PageRequest.of(
                pageNumber - 1,
                pageSize,
                Sort.by(Sort.Direction.DESC, "placedAt").and(Sort.by(Sort.Direction.DESC, "id"))
            );
            result = dataAccess
                .customerOrderRepository()
                .findAll(
                    AdminOrderListSpec.create(
                        from,
                        to,
                        q,
                        status,
                        paymentStatus,
                        paymentMethod,
                        zohoSyncStatus,
                        ledgerlySyncStatus,
                        trackingStatus
                    ),
                    pageable
                );
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orders", result.getContent().stream().map(this::summary).toList());
        payload.put("page", pageNumber);
        payload.put("pageSize", pageSize);
        payload.put("totalItems", result.getTotalElements());
        payload.put("totalPages", Math.max(result.getTotalPages(), 1));
        payload.put("express_cancellation_count", orderShipmentService.countExpressCancellations());
        return ApiResponse.ok(payload);
    }

    @GetMapping("/cod-pending")
    public ApiResponse<Map<String, Object>> codPending() {
        var orders = dataAccess
            .customerOrderRepository()
            .findCodOrdersByPaymentStatus(PaymentStatus.PENDING)
            .stream()
            .map(this::summary)
            .toList();
        return ApiResponse.ok(Map.of("orders", orders));
    }

    /**
     * Logins with at least one real placed order (excludes cancelled carts and unpaid PENDING checkouts).
     * Used by the admin users table Ordered column.
     */
    @GetMapping("/ordered-logins")
    public ApiResponse<Map<String, Object>> orderedLogins() {
        List<String> logins = dataAccess
            .customerOrderRepository()
            .findDistinctUserLoginsWithStatusNotIn(List.of(OrderStatus.CANCELLED, OrderStatus.PENDING));
        return ApiResponse.ok(Map.of("logins", logins));
    }

    @GetMapping("/{orderNumber}")
    public ApiResponse<Map<String, Object>> getOrder(@PathVariable String orderNumber) {
        CustomerOrder order = dataAccess
            .customerOrderRepository()
            .findOneByOrderNumber(orderNumber)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        if (
            applicationProperties.getZoho().getOrderTracking().isEnabled() &&
            order.getStatus() != OrderStatus.FULFILLED &&
            order.getStatus() != OrderStatus.CANCELLED &&
            StringUtils.isNotBlank(order.getZohoSalesOrderId()) &&
            (order.getZohoTrackingLastPulledAt() == null || order.getZohoTrackingLastPulledAt().isBefore(Instant.now().minus(TRACKING_REFRESH_TTL)))
        ) {
            zohoSyncService.pullOrderTracking(order.getId());
            order = dataAccess.customerOrderRepository().findOneByOrderNumber(orderNumber).orElse(order);
        }
        return ApiResponse.ok(Map.of("order", accountOrders.orderDetail(order)));
    }

    @PostMapping("/{orderNumber}/mark-cod-collected")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_SUPER_ADMIN','ROLE_OPERATIONS')")
    @Transactional
    public ApiResponse<Map<String, Object>> markCodCollected(@PathVariable String orderNumber) {
        CustomerOrder order = dataAccess
            .customerOrderRepository()
            .findOneByOrderNumber(orderNumber)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        if (!canMarkCodCollected(order)) {
            if (!"cod".equalsIgnoreCase(order.getPaymentMethod())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Not a Cash on Delivery order");
            }
            if (order.getStatus() == OrderStatus.CANCELLED) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot mark COD collected on a cancelled order");
            }
            if (order.getPaymentStatus() == PaymentStatus.PAID) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Already marked as collected");
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT, "COD can only be marked collected while payment is pending");
        }
        order.setPaymentStatus(PaymentStatus.PAID);
        order.setZohoSyncStatus(ZohoSyncStatus.PENDING);
        CustomerOrder saved = dataAccess.customerOrderRepository().save(order);
        orderEventService.onOrderPaid(saved);
        Long orderId = saved.getId();
        Runnable syncZohoAfterCollection = () -> {
            zohoSyncService.syncOrder(orderId);
            zohoSyncService.pullOrderTracking(orderId);
        };
        // Commit payment_status=PAID before Zoho sync so a Books API failure cannot roll back collection.
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        syncZohoAfterCollection.run();
                    }
                }
            );
        } else {
            syncZohoAfterCollection.run();
        }
        return ApiResponse.ok(Map.of("ok", true, "orderNumber", saved.getOrderNumber(), "paymentStatus", "paid"));
    }

    @PostMapping("/{orderNumber}/book-shipment")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_SUPER_ADMIN','ROLE_OPERATIONS')")
    public ApiResponse<Map<String, Object>> bookShipment(
        @PathVariable String orderNumber,
        @Valid @RequestBody BookShipmentRequest body
    ) {
        ReturnLogisticsMode mode = parseLogisticsMode(body.mode());
        CustomerOrder saved = orderShipmentService.bookShipment(
            orderNumber,
            mode,
            body.carrier_name(),
            body.awb(),
            body.tracking_url(),
            body.label_url(),
            Boolean.TRUE.equals(body.override_fulfillment_hold()),
            body.override_reason(),
            body.courier_id(),
            body.courier_name(),
            body.package_type(),
            body.package_box_id()
        );
        return ApiResponse.ok(Map.of("order", summary(saved)));
    }

    @PostMapping("/{orderNumber}/package-rates")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_SUPER_ADMIN','ROLE_OPERATIONS')")
    public ApiResponse<Map<String, Object>> packageRates(
        @PathVariable String orderNumber,
        @Valid @RequestBody PackageRatesRequest body
    ) {
        return ApiResponse.ok(
            orderPackageService.previewRates(
                orderNumber,
                body.weight_grams(),
                body.length_cm(),
                body.breadth_cm(),
                body.height_cm()
            )
        );
    }

    @PostMapping("/{orderNumber}/complete-package")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_SUPER_ADMIN','ROLE_OPERATIONS')")
    public ApiResponse<Map<String, Object>> completePackage(
        @PathVariable String orderNumber,
        @Valid @RequestBody CompletePackageRequest body
    ) {
        CustomerOrder saved = orderPackageService.completePackage(
            orderNumber,
            body.weight_grams(),
            body.length_cm(),
            body.breadth_cm(),
            body.height_cm(),
            body.courier_id(),
            body.courier_name(),
            body.package_type(),
            body.package_box_id(),
            AdminPackedItemLines.toPackedLines(body.items())
        );
        return ApiResponse.ok(Map.of("order", summary(saved)));
    }

    @PostMapping("/{orderNumber}/cancel-shipment")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_SUPER_ADMIN','ROLE_OPERATIONS')")
    public ApiResponse<Map<String, Object>> cancelShipment(
        @PathVariable String orderNumber,
        @Valid @RequestBody CancelShipmentRequest body
    ) {
        CustomerOrder saved = orderShipmentService.cancelShipment(
            orderNumber,
            parseLogisticsMode(body.mode()),
            body.reason(),
            SecurityUtils.getCurrentUserLogin().orElse("system")
        );
        return ApiResponse.ok(Map.of("order", summary(saved)));
    }

    @PostMapping("/{orderNumber}/cancel")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_SUPER_ADMIN','ROLE_OPERATIONS')")
    public ApiResponse<Map<String, Object>> cancelOrder(
        @PathVariable String orderNumber,
        @Valid @RequestBody CancelOrderRequest body
    ) {
        CustomerOrder saved = orderCancellationService.cancelOrderFromAdmin(
            orderNumber,
            body.reason(),
            SecurityUtils.getCurrentUserLogin().orElse("system"),
            body.shipment_mode()
        );
        return ApiResponse.ok(Map.of("order", summary(saved)));
    }

    private Map<String, Object> summary(CustomerOrder order) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", order.getId());
        data.put("order_number", order.getOrderNumber());
        data.put("total", order.getTotal());
        data.put("cod_fee", order.getCodFee());
        data.put("status", order.getStatus() == null ? "" : order.getStatus().name().toLowerCase());
        data.put("tracking_status", OrderTrackingStatusResolver.resolve(order));
        data.put("payment_status", order.getPaymentStatus() == null ? "" : order.getPaymentStatus().name().toLowerCase());
        data.put("payment_method", StringUtils.defaultString(order.getPaymentMethod()).toLowerCase());
        data.put("coupon_code", StringUtils.defaultString(order.getCouponCode()));
        data.put("placed_at", order.getPlacedAt() == null ? "" : order.getPlacedAt().toString());
        data.put("zoho_sync_status", lower(order.getZohoSyncStatus()));
        data.put("zoho_sync_error", StringUtils.defaultString(order.getZohoSyncError()));
        data.put("zoho_last_synced_at", instant(order.getZohoLastSyncedAt()));
        data.put("zoho_sales_order_id", StringUtils.defaultString(order.getZohoSalesOrderId()));
        data.put("zoho_invoice_id", StringUtils.defaultString(order.getZohoInvoiceId()));
        data.put("zoho_credit_note_id", StringUtils.defaultString(order.getZohoCreditNoteId()));
        data.put("zoho_sync_eligible", isZohoSyncEligible(order));
        data.put("ledgerly_sync_status", lower(order.getLedgerlySyncStatus()));
        data.put("ledgerly_sync_error", StringUtils.defaultString(order.getLedgerlySyncError()));
        data.put("ledgerly_last_synced_at", instant(order.getLedgerlyLastSyncedAt()));
        data.put("ledgerly_sales_order_id", StringUtils.defaultString(order.getLedgerlySalesOrderId()));
        data.put("ledgerly_invoice_id", StringUtils.defaultString(order.getLedgerlyInvoiceId()));
        data.put("ledgerly_sync_eligible", order.getPaymentStatus() == PaymentStatus.PAID);
        JsonNode address = readShippingAddress(order.getShippingAddressJson());
        data.put("full_name", address.path("full_name").asText(""));
        data.put("phone", address.path("phone").asText(""));
        data.put("city", address.path("city").asText(""));
        data.put("shipping_address", shippingAddressMap(address));
        data.put("dispatch_address", dispatchAddressMap());
        data.put("package_slip_number", documentNumber("PKG-", order));
        data.put("shipment_order_number", documentNumber("SHP-", order));
        data.put("tracking_number", StringUtils.defaultString(order.getTrackingNumber()));
        data.put("tracking_carrier", StringUtils.defaultString(order.getTrackingCarrier()));
        data.put("tracking_url", StringUtils.defaultString(order.getTrackingUrl()));
        data.put("shipment_date", StringUtils.defaultString(order.getShipmentDate()));
        data.put("shipment_label_url", StringUtils.defaultString(order.getShipmentLabelUrl()));
        data.put("shipment_carrier_status", StringUtils.defaultString(order.getShipmentCarrierStatus()));
        data.put("shipment_delivered_at", instant(order.getShipmentDeliveredAt()));
        data.put("shipment_cancelled_at", instant(order.getShipmentCancelledAt()));
        data.put("shipment_cancelled_by", StringUtils.defaultString(order.getShipmentCancelledBy()));
        data.put("shipment_cancellation_reason", StringUtils.defaultString(order.getShipmentCancellationReason()));
        data.put(
            "shipment_logistics_mode",
            order.getShipmentLogisticsMode() == null ? "" : order.getShipmentLogisticsMode().name().toLowerCase()
        );
        boolean pendingCancellation = order.getId() != null && orderCancellationService.hasPendingRequest(order.getId());
        data.put("pending_cancellation", pendingCancellation);
        data.put("can_admin_cancel", orderCancellationService.canCancel(order));
        data.put("can_cancel", orderCancellationService.canCancel(order));
        data.put("can_book_shipment", orderShipmentService.canBook(order));
        data.put("can_book_shipment_with_override", orderShipmentService.canBookWithOverride(order));
        data.put("can_book_expressbees_shipment", orderShipmentService.canBookExpressBees(order));
        data.put("can_book_expressbees_shipment_with_override", orderShipmentService.canBookExpressBeesWithOverride(order));
        data.put("can_rebook_carrier_cancelled_shipment", orderShipmentService.canRebookCarrierCancelled(order));
        data.put("can_complete_package", orderPackageService.canCompletePackage(order));
        data.put("can_mark_cod_collected", canMarkCodCollected(order));
        data.put("default_package_weight_grams", orderPackageService.defaultPackageWeightGrams(order));
        data.put("package_weight_grams", order.getPackageWeightGrams());
        data.put("package_length_cm", order.getPackageLengthCm());
        data.put("package_breadth_cm", order.getPackageBreadthCm());
        data.put("package_height_cm", order.getPackageHeightCm());
        data.put("package_chargeable_weight_grams", order.getPackageChargeableWeightGrams());
        data.put("package_courier_id", StringUtils.defaultString(order.getPackageCourierId()));
        data.put("package_courier_name", StringUtils.defaultString(order.getPackageCourierName()));
        data.put(
            "package_type",
            order.getPackageType() == null ? "custom" : order.getPackageType().toApi()
        );
        data.put("package_box_id", order.getPackageBoxId());
        data.put("packed_at", instant(order.getPackedAt()));
        if (order.getId() != null) {
            data.put(
                "order_items",
                dataAccess.orderItemRepository().findAllByOrderId(order.getId()).stream().map(accountOrders::orderItem).toList()
            );
        } else {
            data.put("order_items", List.of());
        }
        return data;
    }

    static boolean canMarkCodCollected(CustomerOrder order) {
        return (
            order != null &&
            "cod".equalsIgnoreCase(StringUtils.defaultString(order.getPaymentMethod())) &&
            order.getPaymentStatus() == PaymentStatus.PENDING &&
            order.getStatus() != OrderStatus.CANCELLED
        );
    }

    private static boolean isZohoSyncEligible(CustomerOrder order) {
        if (order.getStatus() == OrderStatus.CANCELLED) {
            return StringUtils.isNotBlank(order.getZohoSalesOrderId()) || StringUtils.isNotBlank(order.getZohoInvoiceId());
        }
        return order.getPaymentStatus() == PaymentStatus.PAID ||
        (ZohoSyncService.isCodOrder(order) &&
            (order.getStatus() == OrderStatus.CONFIRMED || order.getStatus() == OrderStatus.HAND_WRAPPED));
    }

    private static String lower(Enum<?> value) {
        return value == null ? "" : value.name().toLowerCase();
    }

    private static String instant(Instant value) {
        return value == null ? "" : value.toString();
    }

    private JsonNode readShippingAddress(String raw) {
        if (StringUtils.isBlank(raw)) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(raw);
        } catch (Exception ex) {
            return objectMapper.createObjectNode();
        }
    }

    private Map<String, Object> shippingAddressMap(JsonNode address) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", firstText(address, "full_name", "name"));
        data.put("full_name", firstText(address, "full_name", "name"));
        data.put("line1", firstText(address, "line1", "address_line1", "address"));
        data.put("line2", firstText(address, "line2", "address_line2"));
        data.put("city", firstText(address, "city"));
        data.put("state", firstText(address, "state"));
        data.put("pincode", firstText(address, "pincode", "postal_code", "zip"));
        data.put("phone", firstText(address, "phone", "mobile"));
        data.put("email", firstText(address, "email"));
        data.put("country", StringUtils.defaultIfBlank(firstText(address, "country"), "India"));
        return data;
    }

    private Map<String, Object> dispatchAddressMap() {
        return StoreAddressService.dispatchAddressPayload(
            storeAddressRepository.findFirstByUsagesContainingAndIsDefaultTrueOrderByIdDesc(StoreAddressUsage.SHIPMENT),
            applicationProperties.getXpressbees()
        );
    }

    private static String documentNumber(String prefix, CustomerOrder order) {
        long id = order.getId() == null ? 0L : order.getId();
        return prefix + String.format("%05d", id);
    }

    private static String firstText(JsonNode address, String... fields) {
        if (address == null || address.isMissingNode()) {
            return "";
        }
        for (String field : fields) {
            String value = address.path(field).asText("").trim();
            if (StringUtils.isNotBlank(value)) {
                return value;
            }
        }
        return "";
    }

    private static ReturnLogisticsMode parseLogisticsMode(String mode) {
        if (StringUtils.isBlank(mode)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Logistics mode is required");
        }
        try {
            return ReturnLogisticsMode.valueOf(mode.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid logistics mode: " + mode);
        }
    }

    public record BookShipmentRequest(
        @NotBlank String mode,
        @Size(max = 80) String carrier_name,
        @Size(max = 120) String awb,
        @Size(max = 500) String tracking_url,
        @Size(max = 500) String label_url,
        Boolean override_fulfillment_hold,
        @Size(max = 500) String override_reason,
        @Size(max = 40) String courier_id,
        @Size(max = 120) String courier_name,
        @Size(max = 20) String package_type,
        Long package_box_id
    ) {}

    public record CancelShipmentRequest(
        @NotBlank String mode,
        @NotBlank @Size(min = 5, max = 500) String reason
    ) {}

    public record CancelOrderRequest(
        @NotBlank @Size(min = 5, max = 2000) String reason,
        @Size(max = 20) String shipment_mode
    ) {}

    public record PackageRatesRequest(
        Integer weight_grams,
        @NotNull @Min(1) Integer length_cm,
        @NotNull @Min(1) Integer breadth_cm,
        @NotNull @Min(1) Integer height_cm
    ) {}

    public record CompletePackageRequest(
        Integer weight_grams,
        @NotNull @Min(1) Integer length_cm,
        @NotNull @Min(1) Integer breadth_cm,
        @NotNull @Min(1) Integer height_cm,
        @Size(max = 40) String courier_id,
        @Size(max = 120) String courier_name,
        @Size(max = 20) String package_type,
        Long package_box_id,
        @Valid List<AdminPackedItemLines.PackedItemRequest> items
    ) {}
}
