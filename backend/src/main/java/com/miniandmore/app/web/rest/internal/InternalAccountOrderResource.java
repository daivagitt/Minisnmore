package com.miniandmore.app.web.rest.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.config.ApplicationProperties;
import com.miniandmore.app.domain.*;
import com.miniandmore.app.domain.enumeration.CouponEligibilityScope;
import com.miniandmore.app.domain.enumeration.DiscountType;
import com.miniandmore.app.domain.enumeration.OrderStatus;
import com.miniandmore.app.domain.enumeration.ReturnLogisticsMode;
import com.miniandmore.app.domain.enumeration.PaymentStatus;
import com.miniandmore.app.domain.enumeration.LedgerlySyncStatus;
import com.miniandmore.app.domain.enumeration.ZohoSyncStatus;
import com.miniandmore.app.security.AuthoritiesConstants;
import com.miniandmore.app.security.SecurityUtils;
import com.miniandmore.app.service.LedgerlySyncService;
import com.miniandmore.app.service.OrderShipmentService;
import com.miniandmore.app.service.OrderEventService;
import com.miniandmore.app.service.ZohoSyncService;
import com.miniandmore.app.service.BogoDiscountCalculator;
import com.miniandmore.app.service.CouponDiscountOutcome;
import com.miniandmore.app.service.DiscountLineAllocator;
import com.miniandmore.app.service.OrderCancellationService;
import com.miniandmore.app.service.OrderTrackingStatusResolver;
import com.miniandmore.app.service.ProductVariantSupport;
import com.miniandmore.app.service.ProductVariantSupport.VariantRow;
import com.miniandmore.app.service.RazorpayRefundService;
import com.miniandmore.app.service.StorePricingService;
import com.miniandmore.app.web.rest.v1.AccountOrderDataAccess;
import com.miniandmore.app.web.rest.v1.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/internal/v1")
public class InternalAccountOrderResource {

    private static final Logger LOG = LoggerFactory.getLogger(InternalAccountOrderResource.class);
    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");
    private static final Duration INVENTORY_RESERVATION_TTL = Duration.ofMinutes(30);
    private static final BigDecimal DEFAULT_COD_FEE = BigDecimal.valueOf(99);
    private static final int MAX_COD_FEE_INR = 10_000;
    private static final Duration TRACKING_REFRESH_TTL = Duration.ofMinutes(5);
    private static final String FIELD_PHONE = "phone";
    private static final String FIELD_ERROR = "error";
    private static final String FIELD_ORDER_NUMBER = "orderNumber";
    private static final String FIELD_ORDER_NUMBER_SNAKE = "order_number";
    private static final String FIELD_DISCOUNT = "discount";
    private static final String FIELD_DISCOUNT_VALUE = "discount_value";
    private static final String FIELD_TOTAL = "total";
    private static final String WELCOME10_COUPON = "WELCOME10";

    private final AccountOrderDataAccess dataAccess;
    private final ObjectMapper objectMapper;
    private final ApplicationProperties applicationProperties;
    private final LedgerlySyncService ledgerlySyncService;
    private final ZohoSyncService zohoSyncService;
    private final ProductVariantSupport variantSupport;
    private final StorePricingService storePricingService;
    private final OrderEventService orderEventService;
    private final RazorpayRefundService razorpayRefundService;
    private final OrderCancellationService orderCancellationService;
    private final OrderShipmentService orderShipmentService;
    private final TransactionTemplate inventoryRestoreTransaction;

    @Value("${razorpay.key-id:DEMO}")
    private String razorpayKeyId;

    @Value("${razorpay.key-secret:}")
    private String razorpayKeySecret;

    @Value("${razorpay.payment-demo:false}")
    private boolean razorpayPaymentDemo;

    public InternalAccountOrderResource(
        AccountOrderDataAccess dataAccess,
        ObjectMapper objectMapper,
        ApplicationProperties applicationProperties,
        LedgerlySyncService ledgerlySyncService,
        ZohoSyncService zohoSyncService,
        ProductVariantSupport variantSupport,
        StorePricingService storePricingService,
        OrderEventService orderEventService,
        RazorpayRefundService razorpayRefundService,
        OrderCancellationService orderCancellationService,
        OrderShipmentService orderShipmentService,
        PlatformTransactionManager transactionManager
    ) {
        this.dataAccess = dataAccess;
        this.objectMapper = objectMapper;
        this.applicationProperties = applicationProperties;
        this.ledgerlySyncService = ledgerlySyncService;
        this.zohoSyncService = zohoSyncService;
        this.variantSupport = variantSupport;
        this.storePricingService = storePricingService;
        this.orderEventService = orderEventService;
        this.razorpayRefundService = razorpayRefundService;
        this.orderCancellationService = orderCancellationService;
        this.orderShipmentService = orderShipmentService;
        this.inventoryRestoreTransaction = new TransactionTemplate(transactionManager);
    }

    @PostMapping("/coupons/validate")
    public ApiResponse<Map<String, Object>> validateCoupon(@Valid @RequestBody CouponValidationRequest request) {
        Optional<String> login = SecurityUtils.getCurrentUserLogin();
        Optional<CouponDiscountOutcome> discount = calculateCouponDiscount(
            request.code(),
            request.subtotal(),
            login,
            couponLines(request.items())
        );
        if (discount.isEmpty()) {
            return ApiResponse.ok(Map.of("ok", false, FIELD_ERROR, "Invalid or expired coupon"));
        }
        CouponDiscountOutcome result = discount.orElseThrow();
        if (result.hasError()) {
            return ApiResponse.ok(Map.of("ok", false, FIELD_ERROR, result.error()));
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ok", true);
        data.put("code", request.code().trim().toUpperCase(Locale.ROOT));
        data.put(FIELD_DISCOUNT, result.discount());
        if (result.nudge() != null) {
            data.put("nudge", result.nudge());
        }
        if (result.bogoDetails() != null) {
            data.put("bogo_details", result.bogoDetails());
        }
        return ApiResponse.ok(data);
    }

    /**
     * Public listing of currently redeemable coupons, for an "available offers" widget on cart/checkout.
     * No login required — mirrors the /coupons/validate endpoint's public visibility.
     */
    @GetMapping("/coupons/active")
    public ApiResponse<Map<String, Object>> activeCoupons() {
        Instant now = Instant.now();
        Optional<String> login = SecurityUtils.getCurrentUserLogin();
        boolean welcome10Eligible = login.map(this::isFirstOrderEligible).orElse(true);
        List<Map<String, Object>> coupons = dataAccess
            .couponRepository()
            .findAllByOrderByIdDesc()
            .stream()
            .filter(c -> Boolean.TRUE.equals(c.getActive()))
            .filter(c -> c.getExpiresAt() == null || c.getExpiresAt().isAfter(now))
            .filter(c -> welcome10Eligible || !isWelcome10Coupon(c.getCode()))
            .map(this::couponSummary)
            .toList();
        return ApiResponse.ok(Map.of("coupons", coupons));
    }

    private Map<String, Object> couponSummary(Coupon coupon) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("code", coupon.getCode());
        data.put("discount_type", lower(coupon.getDiscountType()));
        data.put(FIELD_DISCOUNT_VALUE, coupon.getDiscountValue());
        data.put("min_order", coupon.getMinOrder());
        data.put("max_discount", coupon.getMaxDiscount());
        data.put("bogo_buy_qty", BogoDiscountCalculator.clampQty(coupon.getBogoBuyQty()));
        data.put("bogo_get_qty", BogoDiscountCalculator.clampQty(coupon.getBogoGetQty()));
        data.put(
            "eligibility_scope",
            BogoDiscountCalculator.resolveScope(coupon.getEligibilityScope()).name().toLowerCase(Locale.ROOT)
        );
        data.put("eligibility_category_id", coupon.getEligibilityCategoryId());
        return data;
    }

    @GetMapping("/account/profile")
    public ApiResponse<Map<String, Object>> profile() {
        String login = requireLogin();
        Optional<CustomerProfile> profile = dataAccess.customerProfileRepository().findOneByUserLogin(login);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("full_name", profile.map(CustomerProfile::getFullName).map(StringUtils::defaultString).orElse(""));
        data.put(FIELD_PHONE, profile.map(CustomerProfile::getPhone).map(StringUtils::defaultString).orElse(""));
        data.put("subscribed", profile.map(CustomerProfile::getSubscribed).map(Boolean.TRUE::equals).orElse(false));
        return ApiResponse.ok(data);
    }

    @GetMapping("/account/notifications")
    public ApiResponse<Map<String, Object>> notifications() {
        String login = requireLogin();
        List<CustomerOrder> orders = dataAccess.customerOrderRepository().findAllByUserLoginOrderByIdDesc(login);
        for (CustomerOrder order : orders) {
            orderEventService.syncLifecycleEvents(order);
        }
        return ApiResponse.ok(Map.of("events", orderEventService.listForUser(login)));
    }

    @PutMapping("/account/profile")
    @Transactional
    public ApiResponse<Map<String, Boolean>> updateProfile(@Valid @RequestBody ProfileUpdateRequest request) {
        String login = requireLogin();
        CustomerProfile profile = dataAccess
            .customerProfileRepository()
            .findOneByUserLogin(login)
            .orElseGet(() -> new CustomerProfile().userLogin(login));
        profile.setFullName(StringUtils.trimToNull(request.full_name()));
        profile.setPhone(StringUtils.trimToNull(request.phone()));
        if (request.subscribed() != null) {
            profile.setSubscribed(request.subscribed());
        }
        dataAccess.customerProfileRepository().save(profile);
        ledgerlySyncService.syncCustomerProfileAsync(login);
        return ApiResponse.ok(Map.of("ok", true));
    }

    /** Seed profile name from signup when the customer has not set one yet (phone optional). */
    @PostMapping("/account/profile/bootstrap")
    @Transactional
    public ApiResponse<Map<String, Boolean>> bootstrapProfile(@Valid @RequestBody ProfileBootstrapRequest request) {
        String login = requireLogin();
        if (SecurityUtils.hasCurrentUserAnyOfAuthorities(AuthoritiesConstants.ADMIN)) {
            return ApiResponse.ok(Map.of("ok", true));
        }
        String email = StringUtils.trimToEmpty(request.email()).toLowerCase(Locale.ROOT);
        if (!login.equalsIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email does not match signed-in account");
        }
        Optional<CustomerProfile> existing = dataAccess.customerProfileRepository().findOneByUserLogin(login);
        boolean isNew = existing.isEmpty();
        CustomerProfile profile = existing.orElseGet(() -> new CustomerProfile().userLogin(login));
        boolean changed = false;
        if (StringUtils.isBlank(profile.getFullName())) {
            profile.setFullName(StringUtils.trimToNull(request.full_name()));
            changed = true;
        }
        // Signup opt-in only seeds a new profile; later opt-outs from the profile page must not be undone.
        if (isNew && Boolean.TRUE.equals(request.subscribed())) {
            profile.setSubscribed(true);
            changed = true;
        }
        if (!changed) {
            return ApiResponse.ok(Map.of("ok", true));
        }
        dataAccess.customerProfileRepository().save(profile);
        ledgerlySyncService.syncCustomerProfileAsync(login);
        return ApiResponse.ok(Map.of("ok", true));
    }

    /** Keep account profile in sync with checkout contact details used on the order. */
    private void syncProfileFromShippingAddress(String login, AddressRequest shippingAddress) {
        if (shippingAddress == null) {
            return;
        }
        String fullName = StringUtils.trimToNull(shippingAddress.full_name());
        String phone = StringUtils.trimToNull(shippingAddress.phone());
        if (fullName == null && phone == null) {
            return;
        }
        CustomerProfile profile = dataAccess
            .customerProfileRepository()
            .findOneByUserLogin(login)
            .orElseGet(() -> new CustomerProfile().userLogin(login));
        if (fullName != null) {
            profile.setFullName(fullName);
        }
        if (phone != null) {
            profile.setPhone(phone);
        }
        dataAccess.customerProfileRepository().save(profile);
        ledgerlySyncService.syncCustomerProfileAsync(login);
    }

    @PostMapping("/orders")
    @Transactional
    public ApiResponse<Map<String, Object>> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        String login = requireLogin();
        boolean cod = "cod".equalsIgnoreCase(StringUtils.trimToEmpty(request.payment_method()));
        List<ReservedOrderItem> reserved;
        try {
            reserved = reserveInventory(request.items());
        } catch (ResponseStatusException ex) {
            throw ex;
        }
        Totals totals = calculateTotals(
            login,
            reserved,
            blankToNull(request.coupon_code()),
            request.donation(),
            request.shipping_mode(),
            cod
        );
        if (totals.subtotal().compareTo(BigDecimal.ZERO) <= 0 || totals.total().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cart amount must be greater than zero to place an order");
        }
        AddressRequest shippingAddress = lockShippingEmailToLogin(request.shipping_address(), login);
        String orderNumber = generateOrderNumber();
        CustomerOrder order = new CustomerOrder()
            .userLogin(login)
            .orderNumber(orderNumber)
            // COD has no online payment step to wait on — the order is confirmed for fulfillment
            // immediately; paymentStatus stays PENDING until cash/UPI is collected on delivery.
            .status(cod ? OrderStatus.CONFIRMED : OrderStatus.PENDING)
            .paymentStatus(PaymentStatus.PENDING)
            .subtotal(totals.subtotal())
            .discount(totals.discount())
            .shipping(totals.shipping())
            .codFee(totals.codFee())
            .paymentMethod(cod ? "cod" : "online")
            .tax(totals.tax())
            .donation(roundInr(request.donation()))
            .total(totals.total())
            .couponCode(blankToNull(request.coupon_code()))
            .shippingAddressJson(toJson(shippingAddress))
            .notes(blankToNull(request.notes()))
            .inventoryReserved(true)
            // Online orders get a 30-minute hold that the reaper releases if payment never completes.
            // COD orders commit the stock immediately with no expiry (inventoryReservedUntil left
            // null, which the reaper's "<=  :now" comparison never matches).
            .inventoryReservedUntil(cod ? null : Instant.now().plus(INVENTORY_RESERVATION_TTL))
            .placedAt(Instant.now());
        CustomerOrder saved = dataAccess.customerOrderRepository().save(order);
        List<OrderItem> orderItems = new ArrayList<>();
        List<BigDecimal> lineDiscounts = totals.lineDiscounts();
        for (int i = 0; i < reserved.size(); i++) {
            ReservedOrderItem reservedItem = reserved.get(i);
            OrderItemRequest itemRequest = reservedItem.request();
            BigDecimal lineDiscount = i < lineDiscounts.size() ? lineDiscounts.get(i) : BigDecimal.ZERO;
            OrderItem item = new OrderItem()
                .order(saved)
                .productId(itemRequest.product_id())
                .productSlug(blankToNull(itemRequest.product_slug()))
                .productName(itemRequest.product_name())
                .productImage(blankToNull(itemRequest.product_image()))
                .itemSize(blankToNull(itemRequest.size()))
                .color(blankToNull(itemRequest.color()))
                .variantSku(blankToNull(reservedItem.variantSku()))
                .qty(itemRequest.qty())
                .price(reservedItem.unitPrice())
                .mrp(reservedItem.mrp())
                .hsn(StringUtils.trimToNull(reservedItem.product().getHsn()))
                .taxPct(reservedItem.product().getTaxPct())
                .discountAllocation(roundInr(lineDiscount));
            orderItems.add(item);
        }
        dataAccess.orderItemRepository().saveAll(orderItems);
        orderEventService.onOrderCreated(saved);
        syncProfileFromShippingAddress(login, shippingAddress);

        Map<String, Object> totalsMap = new LinkedHashMap<>();
        totalsMap.put("subtotal", totals.subtotal());
        totalsMap.put(FIELD_DISCOUNT, totals.discount());
        totalsMap.put("shipping", totals.shipping());
        totalsMap.put("cod_fee", totals.codFee());
        totalsMap.put("tax", totals.tax());
        totalsMap.put(FIELD_TOTAL, totals.total());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("orderId", saved.getId());
        data.put(FIELD_ORDER_NUMBER, saved.getOrderNumber());
        data.put("paymentMethod", order.getPaymentMethod());
        data.put("cod", cod);
        data.put("amount", toPaise(totals.total()));
        data.put("currency", "INR");
        data.put("totals", totalsMap);

        if (cod) {
            // No gateway involved — the frontend treats this response as an immediate success.
            attemptZohoOrderSync(saved);
            return ApiResponse.ok(data);
        }

        String razorpayOrderId;
        if (isDemoMode()) {
            razorpayOrderId = "order_demo_" + orderNumber;
        } else {
            razorpayOrderId = createRazorpayOrder(orderNumber, totals.total(), login);
        }
        saved.setRazorpayOrderId(razorpayOrderId);
        dataAccess.customerOrderRepository().save(saved);

        data.put("razorpayOrderId", razorpayOrderId);
        data.put("keyId", razorpayKeyId);
        data.put("demoMode", isDemoMode());
        data.put("gatewayMode", gatewayMode());
        return ApiResponse.ok(data);
    }

    /**
     * Resume payment on an order that was created but never paid (e.g. the Razorpay
     * checkout was closed or abandoned) — reopens the same Razorpay order rather than
     * minting a fresh one, since Razorpay orders don't expire between attempts.
     */
    @PostMapping("/orders/retry-payment")
    public ApiResponse<Map<String, Object>> retryPayment(@Valid @RequestBody RetryPaymentRequest request) {
        String login = requireLogin();
        CustomerOrder order = requireOwnedOrder(request.orderId(), login);
        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This order has been cancelled");
        }
        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This order is already paid");
        }
        if (order.getPaymentStatus() != PaymentStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This order can no longer be paid");
        }
        if (StringUtils.isBlank(order.getRazorpayOrderId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No payment session found for this order");
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("orderId", order.getId());
        data.put(FIELD_ORDER_NUMBER, order.getOrderNumber());
        data.put("razorpayOrderId", order.getRazorpayOrderId());
        data.put("keyId", razorpayKeyId);
        data.put("amount", toPaise(order.getTotal()));
        data.put("currency", "INR");
        data.put("demoMode", isDemoMode());
        data.put("gatewayMode", gatewayMode());
        return ApiResponse.ok(data);
    }

    @PostMapping("/orders/verify-payment")
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public ApiResponse<Map<String, Object>> verifyPayment(@Valid @RequestBody VerifyPaymentRequest request) {
        String login = requireLogin();
        CustomerOrder order = requireOwnedOrder(request.orderId(), login);
        if (!validRazorpaySignature(request)) {
            order.setPaymentStatus(PaymentStatus.FAILED);
            releaseInventoryReservation(order);
            dataAccess.customerOrderRepository().save(order);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid payment signature");
        }
        confirmOrderPayment(order, request.razorpay_order_id(), request.razorpay_payment_id(), request.razorpay_signature());
        return ApiResponse.ok(verifyPaymentResponse(order));
    }

    /**
     * Poll Razorpay for UPI QR / async payments where the checkout handler may not fire after the bank debits the customer.
     */
    @PostMapping("/orders/sync-payment")
    @Transactional
    public ApiResponse<Map<String, Object>> syncPayment(@Valid @RequestBody SyncPaymentRequest request) {
        String login = requireLogin();
        CustomerOrder order = requireOwnedOrder(request.orderId(), login);
        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            return ApiResponse.ok(syncPaymentResponse(order, true));
        }
        if (isDemoMode() || StringUtils.isBlank(order.getRazorpayOrderId())) {
            return ApiResponse.ok(syncPaymentResponse(order, false));
        }
        Optional<JsonNode> paidPayment = findSuccessfulRazorpayPayment(order.getRazorpayOrderId());
        if (paidPayment.isEmpty()) {
            return ApiResponse.ok(syncPaymentResponse(order, false));
        }
        JsonNode payment = paidPayment.orElseThrow();
        String razorpayPaymentId = payment.path("id").asText(null);
        if (StringUtils.isBlank(razorpayPaymentId)) {
            return ApiResponse.ok(syncPaymentResponse(order, false));
        }
        long amountPaise = payment.path("amount").asLong(0);
        if (amountPaise != toPaise(order.getTotal())) {
            LOG.warn(
                "Razorpay payment amount mismatch for order {}: expected {} paise, got {}",
                order.getOrderNumber(),
                toPaise(order.getTotal()),
                amountPaise
            );
            return ApiResponse.ok(syncPaymentResponse(order, false));
        }
        String razorpayOrderId = order.getRazorpayOrderId();
        String signature = computeRazorpaySignature(razorpayOrderId, razorpayPaymentId);
        confirmOrderPayment(order, razorpayOrderId, razorpayPaymentId, signature);
        return ApiResponse.ok(syncPaymentResponse(order, true));
    }

    @Scheduled(fixedDelayString = "${application.inventory.reservation-release.fixed-delay-ms:300000}")
    @Transactional
    public void releaseExpiredInventoryReservations() {
        dataAccess
            .customerOrderRepository()
            .findExpiredInventoryReservations(PaymentStatus.PENDING, Instant.now(), PageRequest.of(0, 50))
            .forEach(this::releaseInventoryReservation);
    }

    @GetMapping("/orders")
    public ApiResponse<Map<String, Object>> orders() {
        String login = requireLogin();
        List<Map<String, Object>> summaries = dataAccess
            .customerOrderRepository()
            .findAllByUserLoginOrderByIdDesc(login)
            .stream()
            .map(this::orderSummary)
            .toList();
        return ApiResponse.ok(Map.of("orders", summaries));
    }

    /**
     * Public tracking by order number (no login). Response is limited to delivery timeline fields only;
     * totals, line items, and internal ids are omitted. Order numbers are high-entropy (MNM-…).
     */
    @GetMapping("/orders/track/{orderNumber}")
    public ApiResponse<Map<String, Object>> trackOrder(@PathVariable String orderNumber) {
        CustomerOrder order = dataAccess
            .customerOrderRepository()
            .findOneByOrderNumber(orderNumber)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        // Public track must not block on Ledgerly; scheduler/admin endpoints refresh stale tracking.
        return ApiResponse.ok(publicTrackingSummary(order));
    }

    @GetMapping("/orders/{orderNumber}")
    public ApiResponse<Map<String, Object>> order(@PathVariable String orderNumber) {
        String login = requireLogin();
        CustomerOrder order = dataAccess
            .customerOrderRepository()
            .findOneByOrderNumberAndUserLogin(orderNumber, login)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        order = refreshTrackingIfStale(order);
        return ApiResponse.ok(Map.of("order", orderDetail(order)));
    }

    @PostMapping("/orders/{orderNumber}/cancel-request")
    public ApiResponse<Map<String, Object>> submitCancelRequest(
        @PathVariable String orderNumber,
        @Valid @RequestBody CancelOrderRequest request
    ) {
        String login = requireLogin();
        var cancellationRequest = orderCancellationService.submitRequest(login, orderNumber, request.reason());
        CustomerOrder order = dataAccess
            .customerOrderRepository()
            .findOneByOrderNumberAndUserLogin(orderNumber, login)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("order", orderDetail(order));
        data.put("cancellation_request", orderCancellationService.toCustomerDto(cancellationRequest));
        return ApiResponse.ok(data);
    }

    @GetMapping("/orders/{orderNumber}/cancel-request")
    public ApiResponse<Map<String, Object>> getCancelRequest(@PathVariable String orderNumber) {
        String login = requireLogin();
        var cancellationRequest = orderCancellationService.getVisibleForUser(login, orderNumber);
        if (cancellationRequest == null) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("cancellation_request", null);
            return ApiResponse.ok(data);
        }
        return ApiResponse.ok(Map.of("cancellation_request", orderCancellationService.toCustomerDto(cancellationRequest)));
    }

    @PostMapping("/orders/{orderNumber}/cancel-request/withdraw")
    public ApiResponse<Map<String, Object>> withdrawCancelRequest(@PathVariable String orderNumber) {
        String login = requireLogin();
        var cancellationRequest = orderCancellationService.withdrawByCustomer(login, orderNumber);
        CustomerOrder order = dataAccess
            .customerOrderRepository()
            .findOneByOrderNumberAndUserLogin(orderNumber, login)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("order", orderDetail(order));
        data.put("cancellation_request", orderCancellationService.toCustomerDto(cancellationRequest));
        return ApiResponse.ok(data);
    }

    @PostMapping("/orders/{orderNumber}/cancel")
    public ApiResponse<Map<String, Object>> cancelOrder(
        @PathVariable String orderNumber,
        @Valid @RequestBody CancelOrderRequest request
    ) {
        return submitCancelRequest(orderNumber, request);
    }

    @GetMapping("/payments")
    public ApiResponse<Map<String, Object>> payments() {
        String login = requireLogin();
        List<Map<String, Object>> summaries = dataAccess
            .customerOrderRepository()
            .findAllByUserLoginOrderByIdDesc(login)
            .stream()
            .map(this::paymentSummary)
            .toList();
        return ApiResponse.ok(Map.of("payments", summaries));
    }

    private Map<String, Object> verifyPaymentResponse(CustomerOrder order) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ok", true);
        data.put(FIELD_ORDER_NUMBER, order.getOrderNumber());
        data.put("orderId", order.getId());
        return data;
    }

    private Map<String, Object> syncPaymentResponse(CustomerOrder order, boolean paid) {
        Map<String, Object> data = verifyPaymentResponse(order);
        data.put("paid", paid);
        data.put("payment_status", lower(order.getPaymentStatus()));
        return data;
    }

    private CustomerOrder requireOwnedOrder(Long orderId, String login) {
        CustomerOrder order = dataAccess
            .customerOrderRepository()
            .findById(orderId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        if (!login.equals(order.getUserLogin())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
        }
        return order;
    }

    private void confirmOrderPayment(
        CustomerOrder order,
        String razorpayOrderId,
        String razorpayPaymentId,
        String razorpaySignature
    ) {
        if (order.getPaymentStatus() != PaymentStatus.PAID) {
            ensureInventoryStillReserved(order);
            order
                .paymentStatus(PaymentStatus.PAID)
                .status(OrderStatus.CONFIRMED)
                .razorpayOrderId(razorpayOrderId)
                .razorpayPaymentId(razorpayPaymentId)
                .razorpaySignature(razorpaySignature)
                .inventoryReserved(false)
                .inventoryReleasedAt(null);
            dataAccess.customerOrderRepository().save(order);
            orderEventService.onOrderPaid(order);
        } else {
            order
                .razorpayOrderId(razorpayOrderId)
                .razorpayPaymentId(razorpayPaymentId)
                .razorpaySignature(razorpaySignature);
            dataAccess.customerOrderRepository().save(order);
        }
        attemptLedgerlyOrderSync(order);
        attemptZohoOrderSync(order);
    }

    private void attemptZohoOrderSync(CustomerOrder order) {
        if (!isEligibleForZohoOrderSync(order)) {
            return;
        }
        ZohoSyncStatus syncStatus = order.getZohoSyncStatus();
        if (syncStatus == ZohoSyncStatus.SYNCED || syncStatus == ZohoSyncStatus.PENDING) {
            return;
        }
        order.setZohoSyncStatus(ZohoSyncStatus.PENDING);
        dataAccess.customerOrderRepository().save(order);
        zohoSyncService.syncOrder(order.getId());
    }

    private static boolean isEligibleForZohoOrderSync(CustomerOrder order) {
        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            return true;
        }
        return ZohoSyncService.isCodOrder(order) &&
        (order.getStatus() == OrderStatus.CONFIRMED || order.getStatus() == OrderStatus.HAND_WRAPPED);
    }

    private void attemptLedgerlyOrderSync(CustomerOrder order) {
        if (order.getPaymentStatus() != PaymentStatus.PAID) {
            return;
        }
        LedgerlySyncStatus syncStatus = order.getLedgerlySyncStatus();
        if (syncStatus != null && syncStatus != LedgerlySyncStatus.FAILED && syncStatus != LedgerlySyncStatus.PENDING) {
            return;
        }
        order.setLedgerlySyncStatus(LedgerlySyncStatus.PENDING);
        dataAccess.customerOrderRepository().save(order);
        ledgerlySyncService.syncOrder(order.getId());
    }

    private boolean isWelcome10Coupon(String code) {
        return WELCOME10_COUPON.equalsIgnoreCase(StringUtils.trimToEmpty(code));
    }

    /**
     * WELCOME10 is first-order only. Count any non-cancelled order — including COD
     * (payment stays PENDING until collection) and unpaid online checkouts — so the
     * coupon cannot be reused on a second confirmed order for the same login.
     */
    private boolean isFirstOrderEligible(String login) {
        return !dataAccess.customerOrderRepository().existsByUserLoginAndStatusNot(login, OrderStatus.CANCELLED);
    }

    private Optional<String> welcome10EligibilityError(String code, Optional<String> userLogin) {
        if (!isWelcome10Coupon(code)) {
            return Optional.empty();
        }
        if (userLogin == null || userLogin.isEmpty() || !SecurityUtils.isAuthenticated()) {
            return Optional.of("Sign in to use WELCOME10 on your first order");
        }
        if (!isFirstOrderEligible(userLogin.orElseThrow())) {
            return Optional.of("WELCOME10 is only valid on your first order");
        }
        return Optional.empty();
    }

    private Optional<CouponDiscountOutcome> calculateCouponDiscount(String code, BigDecimal subtotal, Optional<String> userLogin) {
        return calculateCouponDiscount(code, subtotal, userLogin, List.of());
    }

    private Optional<CouponDiscountOutcome> calculateCouponDiscount(
        String code,
        BigDecimal subtotal,
        Optional<String> userLogin,
        List<BogoDiscountCalculator.Line> lines
    ) {
        if (StringUtils.isBlank(code)) {
            return Optional.empty();
        }
        Optional<String> welcome10Error = welcome10EligibilityError(code, userLogin);
        if (welcome10Error.isPresent()) {
            return Optional.of(CouponDiscountOutcome.error(welcome10Error.orElseThrow()));
        }
        Optional<Coupon> couponOpt = dataAccess.couponRepository().findOneByCodeIgnoreCase(code.trim());
        if (couponOpt.isEmpty()) {
            return Optional.empty();
        }
        Coupon coupon = couponOpt.orElseThrow();
        if (!Boolean.TRUE.equals(coupon.getActive())) {
            return Optional.empty();
        }
        if (coupon.getExpiresAt() != null && coupon.getExpiresAt().isBefore(Instant.now())) {
            return Optional.empty();
        }
        BigDecimal effectiveSubtotal = couponSubtotal(
            subtotal,
            lines,
            coupon.getEligibilityScope(),
            coupon.getEligibilityCategoryId()
        );
        BigDecimal minOrder = coupon.getMinOrder() != null ? coupon.getMinOrder() : BigDecimal.ZERO;
        if (effectiveSubtotal.compareTo(minOrder) < 0) {
            return Optional.of(CouponDiscountOutcome.error(minOrderEligibilityMessage(minOrder, effectiveSubtotal)));
        }
        if (coupon.getDiscountType() == DiscountType.BOGO) {
            BogoDiscountCalculator.Result bogo = BogoDiscountCalculator.calculate(
                coupon.getBogoBuyQty(),
                coupon.getBogoGetQty(),
                coupon.getDiscountValue(),
                coupon.getMaxDiscount(),
                effectiveSubtotal,
                lines,
                coupon.getEligibilityScope(),
                coupon.getEligibilityCategoryId()
            );
            if (!bogo.eligible()) {
                return Optional.of(CouponDiscountOutcome.error(bogoEligibilityMessage(bogo.missingItems())));
            }
            BigDecimal discount = roundInr(bogo.discount().max(BigDecimal.ZERO).min(effectiveSubtotal));
            String nudge = bogo.nextTierMissing() > 0
                ? bogoNextTierMessage(bogo.nextTierMissing(), coupon.getBogoGetQty(), coupon.getDiscountValue())
                : null;
            String details = bogoSummaryMessage(
                bogo.appliedGroups(),
                coupon.getBogoBuyQty(),
                coupon.getBogoGetQty(),
                coupon.getDiscountValue()
            );
            return Optional.of(CouponDiscountOutcome.ok(discount, bogo.discountByLineIndex(), nudge, details));
        }
        BigDecimal discount;
        if (coupon.getDiscountType() == DiscountType.PERCENT) {
            discount = effectiveSubtotal
                .multiply(coupon.getDiscountValue())
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            if (coupon.getMaxDiscount() != null) {
                discount = discount.min(coupon.getMaxDiscount());
            }
        } else {
            discount = coupon.getDiscountValue();
            if (coupon.getMaxDiscount() != null) {
                discount = discount.min(coupon.getMaxDiscount());
            }
        }
        discount = discount.max(BigDecimal.ZERO).min(effectiveSubtotal);
        // Preserve paise: the invoice workbook applies percentage discounts to two decimals
        // (for example, 10% of ₹279.20 is ₹27.92).
        discount = roundInr(discount);
        Map<Integer, BigDecimal> lineAllocations = DiscountLineAllocator.allocateProportionally(
            discount,
            lines,
            coupon.getEligibilityScope(),
            coupon.getEligibilityCategoryId()
        );
        return Optional.of(CouponDiscountOutcome.ok(discount, lineAllocations, null, null));
    }

    private static String bogoEligibilityMessage(int missingItems) {
        if (missingItems <= 1) {
            return "Add 1 more item to use this BOGO offer";
        }
        return "Add " + missingItems + " more items to use this BOGO offer";
    }

    private static String bogoNextTierMessage(int nextTierMissing, Integer getQty, BigDecimal discountValue) {
        int get = BogoDiscountCalculator.clampQty(getQty);
        boolean isFree = discountValue == null || discountValue.compareTo(BigDecimal.valueOf(100)) >= 0;
        String reward = isFree
            ? (get == 1 ? "1 more free item" : get + " more free items")
            : (get == 1 ? "the next item at " + formatPercent(discountValue) + "% off" : get + " more items at " + formatPercent(discountValue) + "% off");
        if (nextTierMissing <= 1) {
            return "Add 1 more item to get " + reward + "!";
        }
        return "Add " + nextTierMissing + " more items to get " + reward + "!";
    }

    private static String bogoSummaryMessage(int appliedGroups, Integer buyQty, Integer getQty, BigDecimal discountValue) {
        int buy = BogoDiscountCalculator.clampQty(buyQty);
        int get = BogoDiscountCalculator.clampQty(getQty);
        boolean isFree = discountValue == null || discountValue.compareTo(BigDecimal.valueOf(100)) >= 0;
        int totalDiscountedItems = appliedGroups * get;
        if (isFree) {
            return totalDiscountedItems <= 1
                ? "Buy " + buy + " Get " + get + " Free applied (1 free item)"
                : "Buy " + buy + " Get " + get + " Free applied (" + totalDiscountedItems + " free items)";
        }
        return "Buy " + buy + " Get " + get + " at " + formatPercent(discountValue) + "% off applied (" + totalDiscountedItems + " items discounted)";
    }

    private static String formatPercent(BigDecimal value) {
        if (value == null) return "100";
        if (value.scale() <= 0 || value.remainder(BigDecimal.ONE).compareTo(BigDecimal.ZERO) == 0) {
            return value.setScale(0, RoundingMode.HALF_UP).toPlainString();
        }
        return value.stripTrailingZeros().toPlainString();
    }

    private static String minOrderEligibilityMessage(BigDecimal minOrder, BigDecimal subtotal) {
        BigDecimal shortfall = minOrder.subtract(subtotal).max(BigDecimal.ZERO);
        if (shortfall.compareTo(BigDecimal.ZERO) <= 0) {
            return "Order does not meet the minimum amount for this coupon";
        }
        return "Add ₹" + shortfall.setScale(0, RoundingMode.CEILING).toPlainString() + " more to unlock this offer";
    }

    private static BigDecimal couponSubtotal(
        BigDecimal requestSubtotal,
        List<BogoDiscountCalculator.Line> lines,
        CouponEligibilityScope scope,
        Long eligibilityCategoryId
    ) {
        if (lines == null || lines.isEmpty()) {
            return requestSubtotal == null ? BigDecimal.ZERO : requestSubtotal;
        }
        return BogoDiscountCalculator.qualifyingSubtotal(lines, scope, eligibilityCategoryId);
    }

    private List<BogoDiscountCalculator.Line> couponLines(List<CouponLineRequest> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        List<BogoDiscountCalculator.Line> lines = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            CouponLineRequest item = items.get(i);
            if (item == null || item.qty() == null || item.price() == null) {
                continue;
            }
            Long categoryId = null;
            if (StringUtils.isNotBlank(item.product_id())) {
                // Prefer server category so validate matches checkout (never trust client category alone).
                categoryId = lookupProductCategoryId(item.product_id());
            }
            if (categoryId == null) {
                categoryId = item.category_id();
            }
            lines.add(
                new BogoDiscountCalculator.Line(
                    item.qty(),
                    item.price(),
                    i,
                    blankToNull(item.product_id()),
                    blankToNull(item.sku()),
                    categoryId
                )
            );
        }
        return lines;
    }

    private Long lookupProductCategoryId(String productIdRaw) {
        try {
            Long productId = Long.parseLong(StringUtils.trimToEmpty(productIdRaw));
            return dataAccess
                .productRepository()
                .findById(productId)
                .map(Product::getCategory)
                .map(Category::getId)
                .orElse(null);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Totals calculateTotals(
        String login,
        List<ReservedOrderItem> items,
        String couponCode,
        BigDecimal donation,
        String shippingMode,
        boolean cod
    ) {
        BigDecimal subtotal = items
            .stream()
            .map(item -> item.unitPrice().multiply(BigDecimal.valueOf(item.request().qty())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<BogoDiscountCalculator.Line> lines = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            ReservedOrderItem item = items.get(i);
            Long categoryId = item.product().getCategory() != null ? item.product().getCategory().getId() : null;
            lines.add(
                new BogoDiscountCalculator.Line(
                    item.request().qty(),
                    item.unitPrice(),
                    i,
                    item.request().product_id(),
                    blankToNull(item.variantSku()),
                    categoryId
                )
            );
        }
        Optional<CouponDiscountOutcome> couponResult = calculateCouponDiscount(
            couponCode,
            subtotal,
            Optional.ofNullable(login),
            lines
        );
        if (couponResult.map(CouponDiscountOutcome::hasError).orElse(false)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, couponResult.orElseThrow().error());
        }
        BigDecimal discount = couponResult.map(CouponDiscountOutcome::discount).orElse(BigDecimal.ZERO);
        Map<Integer, BigDecimal> allocations = couponResult
            .map(CouponDiscountOutcome::lineAllocations)
            .orElse(Map.of());
        List<BigDecimal> lineDiscounts = new ArrayList<>(items.size());
        for (int i = 0; i < items.size(); i++) {
            lineDiscounts.add(roundInr(allocations.getOrDefault(i, BigDecimal.ZERO)));
        }
        BigDecimal afterDiscount = roundInr(subtotal.subtract(discount).max(BigDecimal.ZERO));
        CommerceSettings commerce = commerceSettings();
        BigDecimal shipping = calculateShipping(afterDiscount, shippingMode, commerce);
        BigDecimal codFee = cod ? commerce.codFee() : BigDecimal.ZERO;
        // Keep one aggregate GST amount in the store. The accounting/invoice layer can derive
        // CGST+SGST or IGST from place of supply; the commerce admin does not need tax-type fields.
        // Product GST is calculated after coupon discount. COD is always GST-inclusive and
        // shipping/donation are non-taxable in the workbook scenarios.
        BigDecimal productTax = calculateTax(afterDiscount, commerce);
        BigDecimal codTax = calculateIncludedTax(codFee, commerce.gstRate());
        BigDecimal tax = roundInr(productTax.add(codTax));
        BigDecimal roundedDonation = roundInr(donation == null ? BigDecimal.ZERO : donation);
        // The payable total is authoritative and independent of component-level tax rounding.
        // This is what removes the +/- ₹0.01 drift demonstrated by the workbook.
        BigDecimal total = roundInr(
            afterDiscount
                .add(shipping)
                .add(codFee)
                .add(roundedDonation)
                .add(commerce.pricesIncludeGst() ? BigDecimal.ZERO : productTax)
        );
        return new Totals(subtotal, discount, shipping, codFee, tax, total, List.copyOf(lineDiscounts));
    }

    /** INR rounded to paise — mirrors src/lib/money.ts#roundInr. */
    private static BigDecimal roundInr(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    private CommerceSettings commerceSettings() {
        return dataAccess
            .siteSettingRepository()
            .findFirstByOrderByIdAsc()
            .map(settings ->
                new CommerceSettings(
                    Optional.ofNullable(settings.getGstRate()).orElse(BigDecimal.valueOf(5)),
                    Boolean.TRUE.equals(settings.getPricesIncludeGst()),
                    BigDecimal.valueOf(Optional.ofNullable(settings.getShippingStandardInr()).orElse(99)),
                    BigDecimal.valueOf(Optional.ofNullable(settings.getShippingExpressInr()).orElse(150)),
                    BigDecimal.valueOf(Optional.ofNullable(settings.getFreeShippingMinInr()).orElse(2500)),
                    resolveCodFee(settings.getCodFeeInr())
                )
            )
            .orElse(
                new CommerceSettings(
                    BigDecimal.valueOf(5),
                    true,
                    BigDecimal.valueOf(99),
                    BigDecimal.valueOf(150),
                    BigDecimal.valueOf(2500),
                    DEFAULT_COD_FEE
                )
            );
    }

    private static BigDecimal resolveCodFee(Integer configuredFee) {
        if (configuredFee == null || configuredFee < 0 || configuredFee > MAX_COD_FEE_INR) {
            return DEFAULT_COD_FEE;
        }
        return BigDecimal.valueOf(configuredFee);
    }

    private BigDecimal calculateShipping(BigDecimal amountAfterDiscount, String shippingMode, CommerceSettings commerce) {
        if (amountAfterDiscount.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        if (isExpressShipping(shippingMode)) {
            return commerce.shippingExpress();
        }
        if (amountAfterDiscount.compareTo(commerce.freeShippingMin()) >= 0) {
            return BigDecimal.ZERO;
        }
        return commerce.shippingStandard();
    }

    private BigDecimal calculateTax(BigDecimal amountAfterDiscount, CommerceSettings commerce) {
        if (amountAfterDiscount.compareTo(BigDecimal.ZERO) <= 0 || commerce.gstRate().compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        if (commerce.pricesIncludeGst()) {
            return amountAfterDiscount
                .multiply(commerce.gstRate())
                .divide(commerce.gstRate().add(BigDecimal.valueOf(100)), 2, RoundingMode.HALF_UP);
        }
        return amountAfterDiscount.multiply(commerce.gstRate()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateIncludedTax(BigDecimal inclusiveAmount, BigDecimal gstRate) {
        if (inclusiveAmount.compareTo(BigDecimal.ZERO) <= 0 || gstRate.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return inclusiveAmount.multiply(gstRate).divide(gstRate.add(BigDecimal.valueOf(100)), 2, RoundingMode.HALF_UP);
    }

    private static boolean isExpressShipping(String shippingMode) {
        return "exp".equalsIgnoreCase(StringUtils.trimToEmpty(shippingMode));
    }

    private List<ReservedOrderItem> reserveInventory(List<OrderItemRequest> items) {
        // First pass: load products and validate / resolve variant lines (grouped by product for locking).
        Map<Long, List<OrderItemRequest>> itemsByProduct = new LinkedHashMap<>();
        for (OrderItemRequest item : items) {
            Long productId = parseProductId(item.product_id());
            itemsByProduct.computeIfAbsent(productId, id -> new ArrayList<>()).add(item);
        }

        Map<Long, Product> productsById = new LinkedHashMap<>();
        for (Long productId : itemsByProduct.keySet()) {
            Product product = dataAccess
                .productRepository()
                .findOneForInventoryUpdate(productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Product unavailable"));
            if (!Boolean.TRUE.equals(product.getActive())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Product unavailable");
            }
            productsById.put(productId, product);
        }

        List<ReservedOrderItem> reserved = new ArrayList<>();
        for (OrderItemRequest item : items) {
            Long productId = parseProductId(item.product_id());
            Product product = productsById.get(productId);
            if (variantSupport.isVariants(product)) {
                Optional<VariantRow> row = variantSupport.resolveSellable(product, item.sku(), item.size(), item.color());
                if (row.isEmpty()) {
                    throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Selected size/color is unavailable for " + product.getName()
                    );
                }
                VariantRow variant = row.orElseThrow();
                if (StringUtils.isBlank(variant.sku())) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Variant SKU missing for " + product.getName());
                }
                if (variant.stock() < item.qty()) {
                    throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        stockAvailabilityMessage(variant.stock()) + " for " + product.getName() + " (" + variant.sku() + ")"
                    );
                }
                if (!variantSupport.decrementVariantStock(product, variant.sku(), variant.size(), variant.color(), item.qty())) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Unable to reserve stock for " + product.getName());
                }
                BigDecimal unitPrice = variantSupport.variantSellingPrice(variant);
                if (unitPrice.compareTo(BigDecimal.ZERO) <= 0) {
                    unitPrice = storePricingService.simpleProductSellingPrice(product.getCompareAtPrice(), product.getPrice());
                }
                reserved.add(new ReservedOrderItem(product, item, variant.sku(), unitPrice, invoiceMrp(variant.mrp(), unitPrice)));
            } else {
                int available = Optional.ofNullable(product.getStock()).orElse(0);
                if (available < item.qty()) {
                    // Check cumulative later via running stock on product
                }
                BigDecimal unitPrice = storePricingService.simpleProductSellingPrice(product.getCompareAtPrice(), product.getPrice());
                reserved.add(
                    new ReservedOrderItem(
                        product,
                        item,
                        null,
                        unitPrice,
                        invoiceMrp(product.getCompareAtPrice(), unitPrice)
                    )
                );
            }
        }

        // Simple products: aggregate qty and decrement parent stock once.
        Map<Long, Integer> simpleQty = new LinkedHashMap<>();
        for (ReservedOrderItem reservedItem : reserved) {
            if (reservedItem.variantSku() == null) {
                Long id = reservedItem.product().getId();
                simpleQty.merge(id, reservedItem.request().qty(), Integer::sum);
            }
        }
        for (Map.Entry<Long, Integer> entry : simpleQty.entrySet()) {
            Product product = productsById.get(entry.getKey());
            int available = Optional.ofNullable(product.getStock()).orElse(0);
            int needed = entry.getValue();
            if (available < needed) {
                throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    stockAvailabilityMessage(available) + " for " + product.getName()
                );
            }
            product.setStock(available - needed);
        }

        for (Product product : productsById.values()) {
            dataAccess.productRepository().save(product);
        }
        return reserved;
    }

    private void releaseInventoryReservation(CustomerOrder order) {
        if (!Boolean.TRUE.equals(order.getInventoryReserved()) || order.getInventoryReleasedAt() != null) {
            return;
        }
        List<OrderItem> items = dataAccess.orderItemRepository().findAllByOrderId(order.getId());
        Map<Long, Integer> simpleQtyByProduct = new LinkedHashMap<>();
        for (OrderItem item : items) {
            try {
                Long productId = parseProductId(item.getProductId());
                Product product = dataAccess
                    .productRepository()
                    .findOneForInventoryUpdate(productId)
                    .orElse(null);
                if (product == null) {
                    continue;
                }
                int qty = Optional.ofNullable(item.getQty()).orElse(0);
                if (qty <= 0) {
                    continue;
                }
                if (StringUtils.isNotBlank(item.getVariantSku()) && variantSupport.isVariants(product)) {
                    variantSupport.incrementVariantStock(
                        product,
                        item.getVariantSku(),
                        item.getItemSize(),
                        item.getColor(),
                        qty
                    );
                    dataAccess.productRepository().save(product);
                } else {
                    simpleQtyByProduct.merge(productId, qty, Integer::sum);
                }
            } catch (ResponseStatusException ignored) {
                // skip invalid product id
            }
        }
        for (Map.Entry<Long, Integer> entry : simpleQtyByProduct.entrySet()) {
            dataAccess
                .productRepository()
                .findOneForInventoryUpdate(entry.getKey())
                .ifPresent(product -> {
                    product.setStock(Optional.ofNullable(product.getStock()).orElse(0) + entry.getValue());
                    dataAccess.productRepository().save(product);
                });
        }
        order.setInventoryReserved(false);
        order.setInventoryReleasedAt(Instant.now());
        dataAccess.customerOrderRepository().save(order);
    }

    private void restoreInventoryForCancellation(CustomerOrder order) {
        if (order.getInventoryReleasedAt() != null) {
            return;
        }
        // Paid orders no longer carry the temporary reservation flag, but their stock was still
        // deducted at checkout and must be restored when they are cancelled before dispatch.
        if (
            !Boolean.TRUE.equals(order.getInventoryReserved()) &&
            order.getPaymentStatus() != PaymentStatus.REFUNDED &&
            order.getPaymentStatus() != PaymentStatus.REFUND_INITIATED &&
            order.getPaymentStatus() != PaymentStatus.REFUND_FAILED
        ) {
            return;
        }
        order.setInventoryReserved(true);
        releaseInventoryReservation(order);
    }

    private void syncCancellationToLedgerly(CustomerOrder order, String refundId) {
        if (
            StringUtils.isBlank(refundId) &&
            StringUtils.isBlank(order.getLedgerlySalesOrderId()) &&
            StringUtils.isBlank(order.getLedgerlyInvoiceId())
        ) {
            return;
        }
        try {
            dataAccess.ledgerlyIntegrationClient().cancelOrder(order.getOrderNumber(), null, refundId);
        } catch (RuntimeException ex) {
            // Cancellation and any customer refund have already succeeded. Accounting sync is
            // best-effort here and can be reconciled without making the customer retry/refund twice.
            LOG.warn("Ledgerly cancellation sync failed for {}: {}", order.getOrderNumber(), ex.getMessage());
        }
    }

    private void syncCancellationToZoho(CustomerOrder order) {
        if (StringUtils.isBlank(order.getZohoSalesOrderId()) && StringUtils.isBlank(order.getZohoInvoiceId())) {
            return;
        }
        try {
            zohoSyncService.cancelOrder(order.getId());
        } catch (RuntimeException ex) {
            LOG.warn("Zoho cancellation sync failed for {}: {}", order.getOrderNumber(), ex.getMessage());
        }
    }

    private void ensureInventoryStillReserved(CustomerOrder order) {
        if (!Boolean.TRUE.equals(order.getInventoryReserved())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Inventory reservation expired — please place a new order");
        }
        if (order.getInventoryReservedUntil() != null && order.getInventoryReservedUntil().isBefore(Instant.now())) {
            releaseInventoryReservation(order);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Inventory reservation expired — please place a new order");
        }
    }

    private Long parseProductId(String productId) {
        try {
            return Long.parseLong(StringUtils.trimToEmpty(productId));
        } catch (NumberFormatException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid product id");
        }
    }

    private String createRazorpayOrder(String orderNumber, BigDecimal total, String login) {
        try {
            String body = objectMapper.writeValueAsString(
                Map.of(
                    "amount",
                    toPaise(total),
                    "currency",
                    "INR",
                    "receipt",
                    orderNumber,
                    "notes",
                    Map.of("user_login", login)
                )
            );
            JsonNode root = razorpayPost("/v1/orders", body);
            String id = root.path("id").asText(null);
            if (StringUtils.isBlank(id)) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Razorpay order response missing id");
            }
            return id;
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (JsonProcessingException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Razorpay order creation failed: " + ex.getMessage());
        }
    }

    private Optional<JsonNode> findSuccessfulRazorpayPayment(String razorpayOrderId) {
        try {
            JsonNode root = razorpayGet("/v1/orders/" + razorpayOrderId + "/payments");
            for (JsonNode payment : root.path("items")) {
                String status = payment.path("status").asText("");
                if ("captured".equalsIgnoreCase(status) || "authorized".equalsIgnoreCase(status)) {
                    return Optional.of(payment);
                }
            }
            return Optional.empty();
        } catch (ResponseStatusException ex) {
            if (ex.getStatusCode() == HttpStatus.BAD_GATEWAY) {
                LOG.warn("Could not fetch Razorpay payments for {}: {}", razorpayOrderId, ex.getReason());
                return Optional.empty();
            }
            throw ex;
        }
    }

    private JsonNode razorpayGet(String path) {
        return razorpayRequest("GET", path, null);
    }

    private JsonNode razorpayPost(String path, String body) throws JsonProcessingException {
        return razorpayRequest("POST", path, body);
    }

    private JsonNode razorpayRequest(String method, String path, String body) {
        try {
            String token = Base64.getEncoder().encodeToString((razorpayKeyId + ":" + razorpayKeySecret).getBytes(StandardCharsets.UTF_8));
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("https://api.razorpay.com" + path))
                .header(HttpHeaders.AUTHORIZATION, "Basic " + token)
                .timeout(Duration.ofSeconds(10));
            if ("POST".equals(method)) {
                builder
                    .header(HttpHeaders.CONTENT_TYPE, "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(StringUtils.defaultString(body)));
            } else {
                builder.GET();
            }
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Razorpay request failed: HTTP " + response.statusCode()
                );
            }
            return objectMapper.readTree(response.body());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Razorpay request interrupted");
        } catch (JsonProcessingException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Razorpay response parse failed: " + ex.getMessage());
        } catch (java.io.IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Razorpay request failed: " + ex.getMessage());
        }
    }

    private String computeRazorpaySignature(String razorpayOrderId, String razorpayPaymentId) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(razorpayKeySecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal((razorpayOrderId + "|" + razorpayPaymentId).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                hex.append(String.format(Locale.ROOT, "%02x", value));
            }
            return hex.toString();
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to compute payment signature");
        }
    }

    private boolean validRazorpaySignature(VerifyPaymentRequest request) {
        if (StringUtils.isBlank(StringUtils.trimToEmpty(razorpayKeySecret))) {
            return false;
        }
        if (isDemoMode()) {
            return true;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(razorpayKeySecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expected = mac.doFinal(
                (request.razorpay_order_id() + "|" + request.razorpay_payment_id()).getBytes(StandardCharsets.UTF_8)
            );
            byte[] provided = hexToBytes(request.razorpay_signature());
            return provided.length > 0 && MessageDigest.isEqual(expected, provided);
        } catch (Exception ex) {
            return false;
        }
    }

    private byte[] hexToBytes(String hex) {
        if (hex == null || hex.length() % 2 != 0) {
            return new byte[0];
        }
        try {
            byte[] out = new byte[hex.length() / 2];
            for (int i = 0; i < hex.length(); i += 2) {
                int value = Integer.parseInt(hex.substring(i, i + 2), 16);
                out[i / 2] = (byte) value;
            }
            return out;
        } catch (NumberFormatException ex) {
            return new byte[0];
        }
    }

    private Map<String, Object> orderSummary(CustomerOrder order) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", order.getId());
        data.put(FIELD_ORDER_NUMBER_SNAKE, order.getOrderNumber());
        data.put("status", lower(order.getStatus()));
        data.put("payment_status", lower(order.getPaymentStatus()));
        data.put("payment_method", order.getPaymentMethod());
        data.put(FIELD_TOTAL, order.getTotal());
        data.put("created_at", orderCreatedAtIso(order));
        data.put("can_cancel", orderCancellationService.canCustomerShowCancelAction(order));
        data.put("contact_care_to_cancel", orderCancellationService.shouldContactCareToCancel(order));
        data.put("packed_at", order.getPackedAt() == null ? "" : order.getPackedAt().toString());
        data.put("order_items", dataAccess.orderItemRepository().findAllByOrderId(order.getId()).stream().map(this::orderItem).toList());
        trackingFields(order).forEach(data::putIfAbsent);
        return data;
    }

    Map<String, Object> orderDetail(CustomerOrder order) {
        Map<String, Object> data = orderSummary(order);
        data.put("subtotal", order.getSubtotal());
        data.put(FIELD_DISCOUNT, order.getDiscount());
        data.put("shipping", order.getShipping());
        data.put("cod_fee", order.getCodFee());
        data.put("tax", order.getTax());
        data.put("donation", order.getDonation());
        data.put("coupon_code", order.getCouponCode());
        data.put("shipping_address", readJson(order.getShippingAddressJson()));
        data.put("notes", order.getNotes());
        data.put("cancellation_reason", order.getCancellationReason());
        var cancellationRequest = orderCancellationService.getVisibleForUser(order.getUserLogin(), order.getOrderNumber());
        data.put(
            "cancellation_request",
            cancellationRequest != null ? orderCancellationService.toCustomerDto(cancellationRequest) : null
        );
        data.put("pending_cancellation", orderCancellationService.hasPendingRequest(order.getId()));
        data.put("razorpay_order_id", order.getRazorpayOrderId());
        data.put("razorpay_payment_id", order.getRazorpayPaymentId());
        data.put("invoice_pdf_url", invoicePdfUrl(order));
        data.put("tracking_status", OrderTrackingStatusResolver.resolve(order));
        trackingFields(order).forEach(data::put);
        return data;
    }

    /**
     * Returns the cached invoice PDF URL, lazily backfilling it for orders that already have a
     * Zoho invoice but synced before this feature existed. Best-effort — a Zoho hiccup here must
     * not break the order-detail page.
     */
    private String invoicePdfUrl(CustomerOrder order) {
        if (StringUtils.isNotBlank(order.getZohoInvoicePdfUrl())) {
            return order.getZohoInvoicePdfUrl();
        }
        if (StringUtils.isBlank(order.getZohoInvoiceId())) {
            return null;
        }
        try {
            return zohoSyncService.ensureInvoicePdfCached(order.getId());
        } catch (Exception ex) {
            LOG.debug("Invoice PDF backfill failed for {}: {}", order.getOrderNumber(), ex.getMessage());
            return null;
        }
    }

    private Map<String, Object> publicTrackingSummary(CustomerOrder order) {
        Map<String, String> fields = publicTrackingFields(order);
        Map<String, Object> orderMap = new LinkedHashMap<>(fields);
        return Map.of("order", orderMap);
    }

    private Map<String, String> publicTrackingFields(CustomerOrder order) {
        Map<String, String> fields = trackingFields(order);
        Map<String, String> limited = new LinkedHashMap<>();
        limited.put(FIELD_ORDER_NUMBER_SNAKE, fields.get(FIELD_ORDER_NUMBER_SNAKE));
        limited.put("status", fields.get("status"));
        limited.put("tracking_status", fields.get("tracking_status"));
        limited.put("tracking_number", fields.get("tracking_number"));
        limited.put("tracking_carrier", fields.get("tracking_carrier"));
        limited.put("tracking_url", fields.get("tracking_url"));
        limited.put("shipment_date", fields.get("shipment_date"));
        limited.put("shipment_delivered_at", fields.get("shipment_delivered_at"));
        return limited;
    }

    private Map<String, String> trackingFields(CustomerOrder order) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put(FIELD_ORDER_NUMBER_SNAKE, order.getOrderNumber());
        fields.put("status", lower(order.getStatus()));
        fields.put("tracking_status", OrderTrackingStatusResolver.resolve(order));
        fields.put("tracking_number", StringUtils.defaultString(order.getTrackingNumber()));
        fields.put("tracking_carrier", StringUtils.defaultString(order.getTrackingCarrier()));
        fields.put("tracking_url", StringUtils.defaultString(order.getTrackingUrl()));
        fields.put("shipment_date", StringUtils.defaultString(order.getShipmentDate()));
        fields.put("shipment_delivered_at", instant(order.getShipmentDeliveredAt()));
        return fields;
    }

    private CustomerOrder refreshTrackingIfStale(CustomerOrder order) {
        if (order.getStatus() == OrderStatus.FULFILLED || order.getStatus() == OrderStatus.CANCELLED) {
            return order;
        }
        boolean refreshed = false;
        if (StringUtils.isNotBlank(order.getLedgerlySalesOrderId())) {
            Instant lastLedgerlyPull = order.getLedgerlyTrackingLastPulledAt();
            if (lastLedgerlyPull == null || lastLedgerlyPull.isBefore(Instant.now().minus(TRACKING_REFRESH_TTL))) {
                try {
                    ledgerlySyncService.pullOrderTracking(order.getId());
                    refreshed = true;
                } catch (Exception ex) {
                    LOG.warn("Ledgerly tracking refresh failed for {}: {}", order.getOrderNumber(), ex.getMessage());
                }
            }
        }
        if (
            applicationProperties.getZoho().getOrderTracking().isEnabled() &&
            (StringUtils.isNotBlank(order.getZohoInvoiceId()) || StringUtils.isNotBlank(order.getZohoSalesOrderId()))
        ) {
            Instant lastZohoPull = order.getZohoTrackingLastPulledAt();
            if (lastZohoPull == null || lastZohoPull.isBefore(Instant.now().minus(TRACKING_REFRESH_TTL))) {
                try {
                    zohoSyncService.pullOrderTracking(order.getId());
                    refreshed = true;
                } catch (Exception ex) {
                    LOG.warn("Zoho tracking refresh failed for {}: {}", order.getOrderNumber(), ex.getMessage());
                }
            }
        }
        if (order.getShipmentLogisticsMode() == ReturnLogisticsMode.XPRESSBEES && !orderShipmentService.isExpressBeesTrackingFresh(order)) {
            try {
                CustomerOrder pulled = orderShipmentService.refreshExpressBeesTracking(order);
                if (pulled != order) {
                    refreshed = true;
                    order = pulled;
                }
            } catch (Exception ex) {
                LOG.warn("ExpressBees tracking refresh failed for {}: {}", order.getOrderNumber(), ex.getMessage());
            }
        }
        if (refreshed) {
            return dataAccess.customerOrderRepository().findById(order.getId()).orElse(order);
        }
        return order;
    }

    private Map<String, Object> paymentSummary(CustomerOrder order) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", order.getId());
        data.put(FIELD_ORDER_NUMBER_SNAKE, order.getOrderNumber());
        data.put("payment_status", lower(order.getPaymentStatus()));
        data.put(FIELD_TOTAL, order.getTotal());
        data.put("created_at", orderCreatedAtIso(order));
        data.put("razorpay_payment_id", order.getRazorpayPaymentId());
        return data;
    }

    Map<String, Object> orderItem(OrderItem item) {
        Map<String, Object> data = new LinkedHashMap<>();
        Product product = orderItemProduct(item).orElse(null);
        data.put("id", item.getId());
        data.put("product_id", item.getProductId());
        data.put("product_slug", item.getProductSlug());
        data.put("product_name", item.getProductName());
        data.put("product_image", item.getProductImage());
        data.put("size", item.getItemSize());
        data.put("color", item.getColor());
        data.put("sku", item.getVariantSku());
        data.put("qty", item.getQty());
        data.put("packed_qty", item.getPackedQty());
        data.put("price", item.getPrice());
        data.put("mrp", invoiceItemMrp(item, product));
        data.put("hsn", StringUtils.defaultString(item.getHsn(), product == null ? "" : product.getHsn()));
        data.put("tax_pct", Optional.ofNullable(item.getTaxPct()).orElse(product == null ? null : product.getTaxPct()));
        return data;
    }

    private Optional<Product> orderItemProduct(OrderItem item) {
        try {
            return dataAccess.productRepository().findById(Long.valueOf(item.getProductId()));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    private BigDecimal invoiceItemMrp(OrderItem item, Product product) {
        if (item.getMrp() != null && item.getMrp().compareTo(BigDecimal.ZERO) > 0) {
            return item.getMrp();
        }
        if (product != null && StringUtils.isNotBlank(item.getVariantSku())) {
            Optional<VariantRow> variant = variantSupport.findBySku(variantSupport.parseProduct(product), item.getVariantSku());
            if (variant.isPresent()) {
                return invoiceMrp(variant.orElseThrow().mrp(), item.getPrice());
            }
        }
        return invoiceMrp(product == null ? null : product.getCompareAtPrice(), item.getPrice());
    }

    private static BigDecimal invoiceMrp(BigDecimal candidate, BigDecimal sellingPrice) {
        BigDecimal selling = Optional.ofNullable(sellingPrice).orElse(BigDecimal.ZERO);
        return candidate != null && candidate.compareTo(selling) > 0 ? candidate : selling;
    }

    private Object readJson(String raw) {
        if (StringUtils.isBlank(raw)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(raw, Object.class);
        } catch (JsonProcessingException ex) {
            return Map.of();
        }
    }

    private static AddressRequest lockShippingEmailToLogin(AddressRequest address, String login) {
        return new AddressRequest(
            address.full_name(),
            login,
            address.phone(),
            address.line1(),
            address.line2(),
            address.city(),
            address.state(),
            address.pincode(),
            address.baby_name()
        );
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid JSON payload");
        }
    }

    private boolean isDemoMode() {
        return razorpayPaymentDemo
            || "DEMO".equalsIgnoreCase(StringUtils.trimToEmpty(razorpayKeyId))
            || StringUtils.isBlank(StringUtils.trimToEmpty(razorpayKeySecret));
    }

    private String gatewayMode() {
        if (isDemoMode()) {
            return "demo";
        }
        String key = StringUtils.lowerCase(StringUtils.trimToEmpty(razorpayKeyId));
        if (key.startsWith("rzp_test_")) {
            return "test";
        }
        if (key.startsWith("rzp_live_")) {
            return "live";
        }
        return "unknown";
    }

    private String generateOrderNumber() {
        return "MNM-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
    }

    private String requireLogin() {
        return SecurityUtils.getCurrentUserLogin().orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    private String displayName(String login, Optional<CustomerProfile> profile) {
        return profile.map(CustomerProfile::getFullName).filter(StringUtils::isNotBlank).orElse(login);
    }

    private String blankToNull(String value) {
        return StringUtils.trimToNull(value);
    }

    private String lower(Enum<?> value) {
        return value == null ? "" : value.name().toLowerCase(Locale.ROOT);
    }

    private Instant resolveOrderPlacedAt(CustomerOrder order) {
        if (order.getPlacedAt() != null) {
            return order.getPlacedAt();
        }
        if (order.getInventoryReservedUntil() != null) {
            return order.getInventoryReservedUntil().minus(INVENTORY_RESERVATION_TTL);
        }
        return null;
    }

    private String orderCreatedAtIso(CustomerOrder order) {
        Instant placed = resolveOrderPlacedAt(order);
        return instant(placed);
    }

    private static String instant(Instant value) {
        return value == null ? "" : value.toString();
    }

    private static long toPaise(BigDecimal total) {
        return total.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    private static String stockAvailabilityMessage(int available) {
        if (available == 1) {
            return "Only 1 item available";
        }
        return "Only " + available + " items available";
    }

    public record CouponValidationRequest(
        @NotBlank @Size(max = 64) String code,
        @NotNull @DecimalMin("0") BigDecimal subtotal,
        @Size(max = 50) List<@Valid CouponLineRequest> items
    ) {}

    public record CouponLineRequest(
        @NotNull @Min(1) @Max(50) Integer qty,
        @NotNull @DecimalMin("0") BigDecimal price,
        @Size(max = 120) String product_id,
        @Size(max = 120) String sku,
        Long category_id
    ) {}

    public record ProfileUpdateRequest(
        @NotBlank
        @Size(min = 2, max = 120)
        @Pattern(regexp = "^\\p{L}[\\p{L} .'-]*$", message = "Full name may only contain letters, spaces, apostrophes, periods and hyphens")
        String full_name,
        @NotBlank
        @Size(max = 20)
        @Pattern(regexp = "^(\\+91[\\s-]?)?[6-9]\\d{4}[\\s-]?\\d{5}$", message = "Enter a valid 10-digit Indian mobile number")
        String phone,
        Boolean subscribed
    ) {}

    public record ProfileBootstrapRequest(
        @NotBlank
        @Size(min = 2, max = 120)
        @Pattern(regexp = "^\\p{L}[\\p{L} .'-]*$", message = "Full name may only contain letters, spaces, apostrophes, periods and hyphens")
        String full_name,
        @NotBlank @Email @Size(max = 160) String email,
        Boolean subscribed
    ) {}

    public record AddressRequest(
        @NotBlank @Size(max = 120) String full_name,
        @NotBlank @Email @Size(max = 160) String email,
        @NotBlank @Size(max = 20) String phone,
        @NotBlank @Size(max = 200) String line1,
        @Size(max = 200) String line2,
        @NotBlank @Size(max = 80) String city,
        @NotBlank @Size(max = 80) String state,
        @NotBlank @Pattern(regexp = "^\\d{6}$") String pincode,
        @Size(max = 80) String baby_name
    ) {}

    public record OrderItemRequest(
        @NotBlank @Size(max = 120) String product_id,
        @Size(max = 180) String product_slug,
        @NotBlank @Size(max = 200) String product_name,
        @Size(max = 600) String product_image,
        @Size(max = 40) String size,
        @Size(max = 40) String color,
        @Size(max = 120) String sku,
        @NotNull @Min(1) @Max(50) Integer qty,
        @NotNull @DecimalMin("0") BigDecimal price
    ) {}

    public record CreateOrderRequest(
        @NotEmpty @Size(max = 50) List<@Valid OrderItemRequest> items,
        @NotNull @Valid AddressRequest shipping_address,
        @Size(max = 12) String shipping_mode,
        @Pattern(regexp = "^(online|cod)?$") String payment_method,
        @Size(max = 64) String coupon_code,
        @NotNull @DecimalMin("0") BigDecimal donation,
        @Size(max = 500) String notes
    ) {}

    public record VerifyPaymentRequest(
        @NotNull Long orderId,
        @NotBlank @Size(max = 120) String razorpay_order_id,
        @NotBlank @Size(max = 120) String razorpay_payment_id,
        @NotBlank @Size(max = 500) String razorpay_signature
    ) {}

    public record SyncPaymentRequest(@NotNull Long orderId) {}

    public record RetryPaymentRequest(@NotNull Long orderId) {}

    public record CancelOrderRequest(@NotBlank @Size(min = 3, max = 500) String reason) {}

    private record CommerceSettings(
        BigDecimal gstRate,
        boolean pricesIncludeGst,
        BigDecimal shippingStandard,
        BigDecimal shippingExpress,
        BigDecimal freeShippingMin,
        BigDecimal codFee
    ) {}

    private record Totals(
        BigDecimal subtotal,
        BigDecimal discount,
        BigDecimal shipping,
        BigDecimal codFee,
        BigDecimal tax,
        BigDecimal total,
        List<BigDecimal> lineDiscounts
    ) {}

    private record ReservedOrderItem(Product product, OrderItemRequest request, String variantSku, BigDecimal unitPrice, BigDecimal mrp) {}
}
