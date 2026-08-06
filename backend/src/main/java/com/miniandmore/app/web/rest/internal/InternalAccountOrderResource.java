package com.miniandmore.app.web.rest.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.domain.*;
import com.miniandmore.app.domain.enumeration.DiscountType;
import com.miniandmore.app.domain.enumeration.OrderStatus;
import com.miniandmore.app.domain.enumeration.PaymentStatus;
import com.miniandmore.app.domain.enumeration.LedgerlySyncStatus;
import com.miniandmore.app.security.SecurityUtils;
import com.miniandmore.app.service.LedgerlySyncService;
import com.miniandmore.app.service.OrderTrackingStatusResolver;
import com.miniandmore.app.service.ProductVariantSupport;
import com.miniandmore.app.service.ProductVariantSupport.VariantRow;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/internal/v1")
public class InternalAccountOrderResource {

    private static final Logger LOG = LoggerFactory.getLogger(InternalAccountOrderResource.class);
    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");
    private static final Duration INVENTORY_RESERVATION_TTL = Duration.ofMinutes(30);
    private static final Duration TRACKING_REFRESH_TTL = Duration.ofMinutes(5);
    private static final String FIELD_PHONE = "phone";
    private static final String FIELD_ERROR = "error";
    private static final String FIELD_ORDER_NUMBER = "orderNumber";
    private static final String FIELD_ORDER_NUMBER_SNAKE = "order_number";
    private static final String FIELD_DISCOUNT = "discount";
    private static final BigDecimal FIRST_ORDER_DISCOUNT_PERCENT = BigDecimal.TEN;
    private static final String FIELD_TOTAL = "total";

    private final AccountOrderDataAccess dataAccess;
    private final ObjectMapper objectMapper;
    private final LedgerlySyncService ledgerlySyncService;
    private final ProductVariantSupport variantSupport;

    @Value("${razorpay.key-id:DEMO}")
    private String razorpayKeyId;

    @Value("${razorpay.key-secret:}")
    private String razorpayKeySecret;

    @Value("${razorpay.payment-demo:false}")
    private boolean razorpayPaymentDemo;

    public InternalAccountOrderResource(
        AccountOrderDataAccess dataAccess,
        ObjectMapper objectMapper,
        LedgerlySyncService ledgerlySyncService,
        ProductVariantSupport variantSupport
    ) {
        this.dataAccess = dataAccess;
        this.objectMapper = objectMapper;
        this.ledgerlySyncService = ledgerlySyncService;
        this.variantSupport = variantSupport;
    }

    @PostMapping("/coupons/validate")
    public ApiResponse<Map<String, Object>> validateCoupon(@Valid @RequestBody CouponValidationRequest request) {
        Optional<Map<String, Object>> discount = calculateCouponDiscount(request.code(), request.subtotal());
        if (discount.isEmpty()) {
            return ApiResponse.ok(Map.of("ok", false, FIELD_ERROR, "Invalid or expired coupon"));
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ok", true);
        data.put("code", request.code().trim().toUpperCase(Locale.ROOT));
        data.put(FIELD_DISCOUNT, discount.orElseThrow().get(FIELD_DISCOUNT));
        return ApiResponse.ok(data);
    }

    @GetMapping("/account/profile")
    public ApiResponse<Map<String, String>> profile() {
        String login = requireLogin();
        Optional<CustomerProfile> profile = dataAccess.customerProfileRepository().findOneByUserLogin(login);
        Map<String, String> data = new LinkedHashMap<>();
        data.put("full_name", profile.map(CustomerProfile::getFullName).map(StringUtils::defaultString).orElse(""));
        data.put(FIELD_PHONE, profile.map(CustomerProfile::getPhone).map(StringUtils::defaultString).orElse(""));
        return ApiResponse.ok(data);
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
        dataAccess.customerProfileRepository().save(profile);
        ledgerlySyncService.syncCustomerProfileAsync(login);
        return ApiResponse.ok(Map.of("ok", true));
    }

    @PostMapping("/orders")
    @Transactional
    public ApiResponse<Map<String, Object>> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        String login = requireLogin();
        List<ReservedOrderItem> reserved;
        try {
            reserved = reserveInventory(request.items());
        } catch (ResponseStatusException ex) {
            throw ex;
        }
        CommerceSettings commerce = commerceSettings();
        Totals totals = calculateTotals(
            reserved,
            blankToNull(request.coupon_code()),
            request.donation(),
            request.shipping_mode(),
            login
        );
        String orderNumber = generateOrderNumber();
        CustomerOrder order = new CustomerOrder()
            .userLogin(login)
            .orderNumber(orderNumber)
            .status(OrderStatus.PENDING)
            .paymentStatus(PaymentStatus.PENDING)
            .subtotal(totals.subtotal())
            .discount(totals.discount())
            .shipping(totals.shipping())
            .tax(totals.tax())
            .donation(request.donation())
            .total(totals.total())
            .couponCode(blankToNull(request.coupon_code()))
            .shippingAddressJson(toJson(request.shipping_address()))
            .notes(blankToNull(request.notes()))
            .inventoryReserved(true)
            .inventoryReservedUntil(Instant.now().plus(INVENTORY_RESERVATION_TTL))
            .placedAt(Instant.now());
        CustomerOrder saved = dataAccess.customerOrderRepository().save(order);
        List<OrderItem> orderItems = new ArrayList<>();
        for (ReservedOrderItem reservedItem : reserved) {
            OrderItemRequest itemRequest = reservedItem.request();
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
                .price(reservedItem.unitPrice());
            orderItems.add(item);
        }
        dataAccess.orderItemRepository().saveAll(orderItems);

        String razorpayOrderId;
        if (isDemoMode() || totals.total().compareTo(BigDecimal.ZERO) <= 0) {
            razorpayOrderId = "order_demo_" + orderNumber;
        } else {
            razorpayOrderId = createRazorpayOrder(orderNumber, totals.total(), login);
        }
        saved.setRazorpayOrderId(razorpayOrderId);
        dataAccess.customerOrderRepository().save(saved);

        Map<String, Object> totalsMap = new LinkedHashMap<>();
        totalsMap.put("subtotal", totals.subtotal());
        totalsMap.put(FIELD_DISCOUNT, totals.discount());
        totalsMap.put("shipping", totals.shipping());
        totalsMap.put("tax", totals.tax());
        totalsMap.put(FIELD_TOTAL, totals.total());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("orderId", saved.getId());
        data.put(FIELD_ORDER_NUMBER, saved.getOrderNumber());
        data.put("razorpayOrderId", razorpayOrderId);
        data.put("keyId", razorpayKeyId);
        data.put("amount", toPaise(totals.total()));
        data.put("currency", "INR");
        data.put("demoMode", isDemoMode());
        data.put("gatewayMode", gatewayMode());
        data.put("totals", totalsMap);
        return ApiResponse.ok(data);
    }

    @PostMapping("/orders/verify-payment")
    @Transactional
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
        order = refreshTrackingIfStale(order);
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
            stampDemoDeliveryAnchor(order);
            dataAccess.customerOrderRepository().save(order);
        } else {
            order
                .razorpayOrderId(razorpayOrderId)
                .razorpayPaymentId(razorpayPaymentId)
                .razorpaySignature(razorpaySignature);
            dataAccess.customerOrderRepository().save(order);
        }
        attemptLedgerlyOrderSync(order);
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

    private boolean isFirstOrderEligible(String login) {
        return !dataAccess.customerOrderRepository().existsByUserLoginAndPaymentStatus(login, PaymentStatus.PAID);
    }

    private Optional<Map<String, Object>> calculateCouponDiscount(String code, BigDecimal subtotal) {
        if (StringUtils.isBlank(code)) {
            return Optional.empty();
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
        if (subtotal.compareTo(coupon.getMinOrder()) < 0) {
            return Optional.empty();
        }
        BigDecimal discount;
        if (coupon.getDiscountType() == DiscountType.PERCENT) {
            discount = subtotal
                .multiply(coupon.getDiscountValue())
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        } else {
            discount = coupon.getDiscountValue();
        }
        if (coupon.getMaxDiscount() != null) {
            discount = discount.min(coupon.getMaxDiscount());
        }
        discount = discount.max(BigDecimal.ZERO).min(subtotal);
        return Optional.of(Map.of(FIELD_DISCOUNT, discount));
    }

    private Totals calculateTotals(
        List<ReservedOrderItem> items,
        String couponCode,
        BigDecimal donation,
        String shippingMode,
        String login
    ) {
        BigDecimal subtotal = items
            .stream()
            .map(item -> item.unitPrice().multiply(BigDecimal.valueOf(item.request().qty())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal couponDiscount = calculateCouponDiscount(couponCode, subtotal)
            .map(m -> (BigDecimal) m.get(FIELD_DISCOUNT))
            .orElse(BigDecimal.ZERO);
        BigDecimal firstOrderDiscount = isFirstOrderEligible(login)
            ? subtotal.multiply(FIRST_ORDER_DISCOUNT_PERCENT).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;
        BigDecimal discount = couponDiscount.max(firstOrderDiscount);
        BigDecimal afterDiscount = subtotal.subtract(discount).max(BigDecimal.ZERO);
        CommerceSettings commerce = commerceSettings();
        BigDecimal shipping = calculateShipping(afterDiscount, shippingMode, commerce);
        BigDecimal lineGst = calculateTax(afterDiscount, commerce);
        BigDecimal shippingGst = shipping.compareTo(BigDecimal.ZERO) > 0 ? calculateTax(shipping, commerce) : BigDecimal.ZERO;
        BigDecimal tax = lineGst.add(shippingGst);
        BigDecimal total = afterDiscount
            .add(shipping)
            .add(donation == null ? BigDecimal.ZERO : donation)
            .add(commerce.pricesIncludeGst() ? BigDecimal.ZERO : tax);
        return new Totals(subtotal, discount, shipping, tax, total);
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
                    BigDecimal.valueOf(Optional.ofNullable(settings.getFreeShippingMinInr()).orElse(2500))
                )
            )
            .orElse(
                new CommerceSettings(
                    BigDecimal.valueOf(5),
                    true,
                    BigDecimal.valueOf(99),
                    BigDecimal.valueOf(150),
                    BigDecimal.valueOf(2500)
                )
            );
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
                    unitPrice = Optional.ofNullable(product.getPrice()).orElse(BigDecimal.ZERO);
                }
                reserved.add(new ReservedOrderItem(product, item, variant.sku(), unitPrice));
            } else {
                int available = Optional.ofNullable(product.getStock()).orElse(0);
                if (available < item.qty()) {
                    // Check cumulative later via running stock on product
                }
                reserved.add(
                    new ReservedOrderItem(
                        product,
                        item,
                        null,
                        item.price() != null ? item.price() : Optional.ofNullable(product.getPrice()).orElse(BigDecimal.ZERO)
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
        data.put(FIELD_TOTAL, order.getTotal());
        data.put("created_at", orderCreatedAtIso(order));
        data.put("order_items", dataAccess.orderItemRepository().findAllByOrderId(order.getId()).stream().map(this::orderItem).toList());
        trackingFields(order).forEach(data::putIfAbsent);
        return data;
    }

    private Map<String, Object> orderDetail(CustomerOrder order) {
        Map<String, Object> data = orderSummary(order);
        data.put("subtotal", order.getSubtotal());
        data.put(FIELD_DISCOUNT, order.getDiscount());
        data.put("shipping", order.getShipping());
        data.put("tax", order.getTax());
        data.put("donation", order.getDonation());
        data.put("coupon_code", order.getCouponCode());
        data.put("shipping_address", readJson(order.getShippingAddressJson()));
        data.put("notes", order.getNotes());
        data.put("razorpay_order_id", order.getRazorpayOrderId());
        data.put("razorpay_payment_id", order.getRazorpayPaymentId());
        data.put("tracking_status", OrderTrackingStatusResolver.resolve(order));
        trackingFields(order).forEach(data::put);
        return data;
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
        limited.put("shipment_date", fields.get("shipment_date"));
        return limited;
    }

    private Map<String, String> trackingFields(CustomerOrder order) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put(FIELD_ORDER_NUMBER_SNAKE, order.getOrderNumber());
        fields.put("status", lower(order.getStatus()));
        fields.put("tracking_status", OrderTrackingStatusResolver.resolve(order));
        fields.put("tracking_number", StringUtils.defaultString(order.getTrackingNumber()));
        fields.put("tracking_carrier", StringUtils.defaultString(order.getTrackingCarrier()));
        fields.put("shipment_date", StringUtils.defaultString(order.getShipmentDate()));
        return fields;
    }

    private CustomerOrder refreshTrackingIfStale(CustomerOrder order) {
        if (order.getStatus() == OrderStatus.FULFILLED || order.getStatus() == OrderStatus.CANCELLED) {
            return order;
        }
        if (StringUtils.isBlank(order.getLedgerlySalesOrderId())) {
            return order;
        }
        Instant lastPulled = order.getLedgerlyTrackingLastPulledAt();
        if (lastPulled != null && lastPulled.isAfter(Instant.now().minus(TRACKING_REFRESH_TTL))) {
            return order;
        }
        try {
            ledgerlySyncService.pullOrderTracking(order.getId());
        } catch (Exception ex) {
            LOG.warn("Tracking refresh failed for {}: {}", order.getOrderNumber(), ex.getMessage());
        }
        return dataAccess.customerOrderRepository().findById(order.getId()).orElse(order);
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

    private Map<String, Object> orderItem(OrderItem item) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", item.getId());
        data.put("product_id", item.getProductId());
        data.put("product_slug", item.getProductSlug());
        data.put("product_name", item.getProductName());
        data.put("product_image", item.getProductImage());
        data.put("size", item.getItemSize());
        data.put("color", item.getColor());
        data.put("sku", item.getVariantSku());
        data.put("qty", item.getQty());
        data.put("price", item.getPrice());
        return data;
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

    /** Demo/UAT: mark paid orders as delivered so return + Ledgerly credit-note scenarios can run. */
    private void stampDemoDeliveryAnchor(CustomerOrder order) {
        if (!isDemoMode() || StringUtils.isNotBlank(order.getShipmentDate())) {
            return;
        }
        order.setShipmentDate(LocalDate.now(INDIA).toString());
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
        return placed == null ? "" : placed.toString();
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
        @NotNull @DecimalMin("0") BigDecimal subtotal
    ) {}

    public record ProfileUpdateRequest(@NotNull @Size(max = 120) String full_name, @NotNull @Size(max = 20) String phone) {}

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

    private record CommerceSettings(
        BigDecimal gstRate,
        boolean pricesIncludeGst,
        BigDecimal shippingStandard,
        BigDecimal shippingExpress,
        BigDecimal freeShippingMin
    ) {}

    private record Totals(BigDecimal subtotal, BigDecimal discount, BigDecimal shipping, BigDecimal tax, BigDecimal total) {}

    private record ReservedOrderItem(Product product, OrderItemRequest request, String variantSku, BigDecimal unitPrice) {}
}
